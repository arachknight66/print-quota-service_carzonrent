# System Design Document

This document describes the functional requirements, design patterns, concurrency controls, and caching models implemented in the system.

---

## 1. Requirements

### Functional Requirements
- **Active Directory Synchronization**: Schedule periodic batch syncs of AD users (sAMAccountName, department, UAC state) into the PostgreSQL repository.
- **IPP Decoding & Verification**: Support decoding of binary IPP requests, validating operations (Print-Job, Validate-Job), and verifying user names and target printers.
- **Metadata Extraction**: Parse and resolve copies, duplex mode, color status, and estimated page requirements.
- **Quota Allocation & Deductions**: Calculate page counts, check user balances, and deduct balance inside write-locking transactions.
- **Audit Logging**: Write audit records for every print request (allowed or rejected) showing usernames, printers, document name, decisions, reasons, and correlation IDs.

### Non-Functional Requirements
- **Thread Safety**: Ensure no race conditions or double quota deductions occur under simultaneous printing.
- **Low Latency**: Limit quota validation processing pipeline overhead to less than 20 milliseconds per job.
- **Observability**: Expose liveness, readiness, prometheus metrics, and request correlation IDs across all logs.

---

## 2. Design Patterns

- **Chain of Responsibility**: Implemented in `PrintProcessingPipeline` using a series of `PipelineStage` objects to isolate validation, identity lookup, page calculation, and database transactions.
- **Strategy Pattern**: Promoted through the separation of calculation multipliers (color cost, duplex discount) from database writes, facilitating alternative estimation policies.
- **Factory / Builder Pattern**: Implemented in DTOs (e.g. `PrintJobMetadata`) and mock creation utilities to handle parsing state cleanly.

---

## 3. Concurrency & Locking Model

The system operates under a high-concurrency model assuming several print spoolers process jobs simultaneously for the same user.

### Pessimistic Write Lock
To prevent race conditions, lost updates, and double deductions, the system uses a pessimistic write lock when reserving quota balances:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT q FROM Quota q WHERE q.user.id = :userId AND q.month = :month")
Optional<Quota> findByUserIdAndMonthForUpdate(UUID userId, String month);
```
This serializes access to the specific User-Month Quota row at the database level. Other threads attempting to update this row wait until the current transaction commits or rolls back.

---

## 4. Caching Strategy

The system configures **Caffeine Cache** to reduce Active Directory query traffic during request validation:

- **Cache Name**: `ldapUsers`
- **Specification**: `maximumSize=500,expireAfterWrite=300s` (5-minute TTL).
- **Application**: The LDAP lookup repository maps `@Cacheable(value = "ldapUsers", key = "#username")` to cache parsed user details.

---

## 5. Security & Audit Design

- **Least Privilege Access**: The database schema is managed via Liquibase. The runtime container drops all default Linux capabilities (`CAP_DROP`) and executes as GID/UID `10001`.
- **MDC Correlation Identification**: Incoming requests pass through `MdcCorrelationFilter`, generating or propagating a unique UUID string (`X-Correlation-ID`) across MDC thread logs.
- **Robust Audit Logging**: Even when quota reservations throw a `QuotaExceededException` (rolling back the parent transaction), `AuditServiceImpl.logAudit` is marked with `Propagation.REQUIRES_NEW` to successfully persist the rejection record.
