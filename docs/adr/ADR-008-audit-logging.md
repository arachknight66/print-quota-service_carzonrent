# ADR-008: Isolation of Audit Log Persistence

## Context
When a print job is rejected (e.g. insufficient quota), the database transaction rolls back. If the audit log insertion runs in the same transaction, the audit record is also rolled back, losing the record of the rejection.

## Decision
We configure `AuditServiceImpl.logAudit` with transaction propagation set to `Propagation.REQUIRES_NEW`. This executes the audit save inside a separate database transaction, ensuring it commits even if the parent quota deduction transaction is rolled back.

## Consequences
- **Pros**: Complete audit history for all print requests (allowed, rejected, or system failures).
- **Cons**: Overhead of managing a second database connection during pipeline processing.
