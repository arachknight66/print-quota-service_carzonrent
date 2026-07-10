# Print Processing Engine: Technical Documentation

This document describes the design, execution flow, transaction rules, and error mappings of the Print Processing Engine implemented in Phase 5.

---

## 1. Pipeline Architecture (Mermaid Diagram)

The pipeline is modeled as a Chain of Responsibility where request payloads are enriched and verified sequentially.

```mermaid
graph TD
    A[IPP Request Bytes] --> B[ProtocolValidationStage]
    B -->|Passed| C[IdentityResolutionStage]
    C -->|Passed| D[MetadataExtractionStage]
    D -->|Passed| E[QuotaEvaluationStage]
    E -->|Passed| F[PrintTransactionStage]
    F -->|Allowed & Reserved| G[ALLOW]
    
    B -.->|Reject| H[REJECT_INVALID_REQUEST]
    C -.->|Reject| I[REJECT_UNKNOWN_USER / REJECT_DISABLED_USER]
    D -.->|Reject| H
    E -.->|Reject| J[REJECT_INSUFFICIENT_QUOTA]
    F -.->|Locked Out / DB Fail| K[SYSTEM_ERROR]
```

---

## 2. Sequence Diagram

Below is the transactional sequence showing interaction between the caller, pipeline, database locking context, and audit log commits.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Pipeline as PrintProcessingPipeline
    participant Identity as IdentityService
    participant Repo as QuotaRepository
    participant Tx as PrintTransactionService
    participant Audit as AuditService

    Client->>Pipeline: process(ippPacket, correlationId)
    Pipeline->>Identity: findUser(username)
    Identity-->>Pipeline: User Entity (Active)
    
    Pipeline->>Repo: findByUserIdAndMonth() (Pre-flight check)
    Repo-->>Pipeline: Quota row
    
    Pipeline->>Tx: reserveQuota(PipelineContext)
    note over Tx, Repo: Starts Transaction
    Tx->>Repo: findByUserIdAndMonthForUpdate() (Pessimistic write lock)
    Repo-->>Tx: Locked Quota row
    
    alt Balance is Sufficient
        Tx->>Repo: save(quota) (Deduct page balance)
        Tx->>Audit: logAudit(PipelineContext)
        note over Audit: Commits new audit log row (REQUIRES_NEW)
        Tx-->>Pipeline: SUCCESS
        note over Tx: Commit Transaction (Release Lock)
    else Balance is Insufficient
        Tx->>Audit: logAudit(PipelineContext)
        note over Audit: Commits rejected log row
        Tx-->>Pipeline: Throws QuotaExceededException
        note over Tx: Rollback Transaction (Release Lock)
    end
    
    Pipeline-->>Client: PipelineResult (Decision outcome)
```

---

## 3. Quota Calculations Reference

Deductions are calculated using double-precision float math before rounding up to the nearest whole page to prevent fractional leakages:

\[
\text{Calculated Pages} = \text{basePages} \times \text{copies} \times (\text{duplex} ? \text{duplexMultiplier} : 1.0) \times (\text{color} ? \text{colorMultiplier} : 1.0)
\]

\[
\text{Deducted Pages} = \lceil \text{Calculated Pages} \rceil
\]

### Configuration Parameters
- `app.quota.multipliers.duplex`: 0.8 (representing a 20% discount on double-sided print jobs).
- `app.quota.multipliers.color`: 2.0 (representing double cost per colored sheet).

---

## 4. Error Handling & Decision Matrix

| Stage | Input Condition | Pipeline Exception / Log | Final Decision | Response Message |
|---|---|---|---|---|
| Protocol Validation | Invalid Version / Unknown Operation | `REJECT_INVALID_REQUEST` | `REJECT_INVALID_REQUEST` | Unsupported IPP version or action code. |
| Protocol Validation | Missing requesting-user-name | `REJECT_INVALID_REQUEST` | `REJECT_INVALID_REQUEST` | Missing mandatory user attribute. |
| Identity Resolution | User not found in Postgres | `REJECT_UNKNOWN_USER` | `REJECT_UNKNOWN_USER` | Unknown user. |
| Identity Resolution | User is marked inactive (`is_active = false`) | `REJECT_DISABLED_USER` | `REJECT_DISABLED_USER` | User account is disabled. |
| Metadata Extraction | Non-integer copies or page values | `REJECT_INVALID_REQUEST` | `REJECT_INVALID_REQUEST` | Metadata extraction failed: Invalid format. |
| Quota Evaluation | Month Quota row does not exist | `REJECT_INSUFFICIENT_QUOTA` | `REJECT_INSUFFICIENT_QUOTA` | No print quota allocated for month. |
| Quota Reservation | Balance < Estimated Pages | `QuotaExceededException` | `REJECT_INSUFFICIENT_QUOTA` | Insufficient quota. |
| DB locking | Lock timeout / Timeout waiting | `CannotAcquireLockException` | `SYSTEM_ERROR` | Database transaction error. |
