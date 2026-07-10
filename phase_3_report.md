# Phase 3 Implementation Report: JPA Repositories & Concurrency Control (ACID Lock)
* **Status**: Completed
* **Date Completed**: 2026-07-10
* **Mentor Sign-off Status**: Pending Review

---

### 1. Deliverables Completed
* **[UserRepository.java](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/java/com/printkeep/quota/repository/UserRepository.java)**: User domain database operations.
* **[QuotaRepository.java](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/java/com/printkeep/quota/repository/QuotaRepository.java)**: Quota domain database operations featuring pessimistic locking support.
* **[PrintLogRepository.java](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/java/com/printkeep/quota/repository/PrintLogRepository.java)**: Transaction history database operations.

---

### 2. Design & Technical Summary
* **Concurrency Locking Strategy**:
  * Added a pessimistic write lock (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the `Quota` retrieval query in `QuotaRepository`.
  * When a print job begins checking the quota, the database locks the specific user's quota record for the current month (`SELECT ... FOR UPDATE`).
  * Any parallel printing requests for the *same* employee are queued at the database level until the current transaction commits or rolls back. This completely prevents race conditions (e.g. "double-spending" pages when a user sends multiple print jobs at the exact same millisecond).
* **Transaction Lifecycle**: Configured service methods with `@Transactional`. If a network timeout or physical printer error occurs after deducting the quota, the transaction rolls back cleanly, reverting the deducted pages in the database.

---

### 3. Verification & Test Metrics
* Concurrency logic verified under simulated parallel unit tests in the JUnit framework.
* Verified that parallel threads wait for the lock to release, and quota updates write sequentially without data loss or phantom reads.

---

### 4. Code Health & Maintainability
* Implemented clean repositories extending Spring Data JPA's `JpaRepository` interface, giving standard CRUD support without writing custom SQL.
* Query arguments are parameterized using `@Param` tags, mitigating any risk of SQL injection.
