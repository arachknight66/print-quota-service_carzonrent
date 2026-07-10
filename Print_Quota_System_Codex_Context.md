# System Architecture & Development Context: Print Quota Management System

**Target Audience:** Codex / AI Coding Assistant
**Purpose:** Provide comprehensive, zero-ambiguity context for generating the Java-based Print Quota Management System. Follow these constraints strictly.

---

## 1. Project Overview
Build a domain-wide print management middleware system in Java that intercepts print jobs via IPPS, validates user quotas against a database, and either forwards the job to a physical printer or rejects it. The system also generates and emails monthly usage reports.

## 2. Strict Technology Stack
* **Language:** Java 21
* **Framework:** Spring Boot 3.x
* **Web Server:** Jetty (Must explicitly exclude standard Spring Boot Tomcat and include `spring-boot-starter-jetty`)
* **Database:** PostgreSQL (or MySQL)
* **Logging:** Logback (Local rolling file only; NO centralized log aggregators like ELK/Splunk)
* **Build Tool:** Maven (or Gradle, but Maven preferred for standard enterprise Java)
* **Reporting:** Apache POI (for `.xlsx`) or JasperReports (for `.pdf`)
* **Email:** `spring-boot-starter-mail` (JavaMailSender)

## 3. Network & Security Constraints
* **No Load Balancer / Single Node:** System runs on a single CentOS VM. Do not over-engineer for distributed sessions or load balancing.
* **No Subnets:** Assume a flat network for firewall rules; all clients can reach the CentOS VM on port 443.
* **IPPS (Secure IPP):** * Clients submit print jobs to `ipps://<centos-vm-hostname>:443/printers/MainPrinter`.
    * Jetty must be configured for SSL/TLS (HTTPS/IPPS) using a local Keystore.
* **LDAPS (Secure LDAP):**
    * Connects to Active Directory via port 636.
    * Requires standard Java Truststore configuration to trust the AD Root CA.
    * Used for auto-provisioning users. No explicit user registration flow.

## 4. Core Business Logic: Print Interception & Quotas
The Java application acts as an IPP Proxy server listening for raw spool files.

**Quota Rules (Strict):**
1.  **Authentication:** Read `requesting-user-name` from IPP headers. Query DB. If not in DB, query LDAPS to fetch department and auto-create DB record with default quota.
2.  **Page Calculation:**
    * Read `job-impressions`.
    * Read `sides` attribute. **Rule:** If `sides` == `two-sided-long-edge` OR `two-sided-short-edge`, multiply `job-impressions` by 2.
    * Color print vs. B/W print cost is EQUAL (1x). Ignore `print-color-mode`.
    * File size does not affect quota. Ignore file size attributes.
3.  **Gatekeeper Action:**
    * **If user has sufficient quota:** Decrement used pages in DB, log the transaction as SUCCESS, and forward raw byte stream to the physical printer.
    * **If user lacks quota:** Drop byte stream, log as REJECTED_QUOTA, return IPP `client-error-not-possible` status to client.

## 5. Database Schema Requirements (Relational)
Maintain strict ACID compliance to prevent race conditions during concurrent print jobs.

* **`users` Table:**
    * `employee_id` (PK, UUID or Auto-inc)
    * `domain_username` (String, Unique)
    * `department` (String)
    * `is_active` (Boolean)
* **`quotas` Table:**
    * `quota_id` (PK)
    * `employee_id` (FK to users)
    * `month_year` (String/Date, e.g., "YYYY-MM")
    * `allocated_pages` (Integer)
    * `used_pages` (Integer)
* **`print_logs` Table:**
    * `log_id` (PK)
    * `employee_id` (FK to users)
    * `timestamp` (Timestamp)
    * `document_name` (String)
    * `page_count` (Integer - calculated based on duplex rules)
    * `status` (Enum/String: SUCCESS, REJECTED_QUOTA, ERROR)

## 6. Scheduled Tasks & Monthly Reporting
* **Scheduler:** Use Spring `@Scheduled(cron = "0 0 0 1 * ?")` (Midnight on the 1st of every month).
* **Tasks executed by Cron:**
    1.  Generate a tabular report (Excel or PDF) detailing usage per employee and department for the previous month.
    2.  Email the report to IT Admins/Heads using JavaMailSender.
    3.  Generate new rows in the `quotas` table for the new month for all active users, resetting `used_pages` to 0.

## 7. Logging & Disk Management
* Configure `logback-spring.xml` with a `RollingFileAppender`.
* **Constraint:** Must prevent CentOS disk space exhaustion.
* **Settings:** Max file size = 50MB, Max history = 30 days.

## 8. Deployment & CI/CD Context (Jenkins)
* **Target OS:** CentOS Linux.
* **Process Manager:** `systemd` (Standard background service).
* **Pipeline Flow:**
    * Jenkins builds the executable Jar/War using JDK 21.
    * Jenkins copies artifact to CentOS VM via SCP/SSH.
    * Jenkins runs `sudo systemctl restart print-quota.service`.
* **Codex Instruction:** Provide configuration templates for `systemd` service file and a basic Jenkinsfile to support this flow.

---
**END OF CONTEXT.** When generating code, prioritize error handling around IPP socket timeouts, LDAP connection drops, and SSL Handshake exceptions.
