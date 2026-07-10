# Phase 2 Implementation Report: Relational Database Schema Design & JPA Mapping
* **Status**: Completed
* **Date Completed**: 2026-07-10
* **Mentor Sign-off Status**: Pending Review

---

### 1. Deliverables Completed
* **[User.java](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/java/com/printkeep/quota/model/User.java)**: ORM entity mapping the `users` table.
* **[Quota.java](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/java/com/printkeep/quota/model/Quota.java)**: ORM entity mapping the `quotas` table.
* **[PrintLog.java](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/src/main/java/com/printkeep/quota/model/PrintLog.java)**: ORM entity mapping the `print_logs` transaction history table.

---

### 2. Design & Technical Summary
* **Schema Topology**:
  * **`users`**: Uses a UUID primary key for maximum security and domain isolation. Contains a unique indexed column over `domain_username` (`idx_users_domain_username`) to support sub-millisecond authentication checks when print jobs arrive.
  * **`quotas`**: Contains a composite unique constraint over `employee_id` and `month_year` (`uk_user_month_year`). This structure restricts users to having exactly one quota record per billing/calendar cycle (format: `YYYY-MM`). The default quota is hardcoded to **106 pages** per cycle.
  * **`print_logs`**: Designed with an index on print timestamps and user mappings. Tracks print details (filenames, calculated duplex pages, execution status).
* **State Enumeration**: The `print_logs.status` is mapped to a JPA String-Based Enum to guarantee strict database readability and auditing capability (`SUCCESS`, `REJECTED_QUOTA`, `ERROR`).

---

### 3. Verification & Test Metrics
* Verified Hibernate auto-DDL configurations. The SQL schemas generated successfully against the relational mapper specifications.
* Verified composite and unique indexes are correctly created on the primary domain fields.

---

### 4. Code Health & Maintainability
* **Lombok Integration**: Used `@Data`, `@Builder`, `@NoArgsConstructor`, and `@AllArgsConstructor` annotations to eliminate boilerplate getter/setter code, reducing file sizes and improving maintainability.
* **JPA Conventions**: Configured lazy loading (`FetchType.LAZY`) on relational properties in `Quota` and `PrintLog` to avoid N+1 select queries and reduce JVM heap pressure.
