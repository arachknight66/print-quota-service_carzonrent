# ADR-007: Pessimistic Write Locking for Quota Reservations

## Context
When several spoolers print concurrently for the same user, simultaneous updates to the same `Quota` row can cause race conditions or double quota deductions. Optimistic locking throws exceptions, triggering client retries and degrading the print experience.

## Decision
We use a pessimistic write lock (`SELECT ... FOR UPDATE` via `LockModeType.PESSIMISTIC_WRITE`) during the quota deduction phase in `PrintTransactionServiceImpl`.

## Consequences
- **Pros**: Guarantees consistency. Prevents double-spending. Eliminates retry loops.
- **Cons**: Serializes database updates for the same user, causing small processing delays under high concurrency.
