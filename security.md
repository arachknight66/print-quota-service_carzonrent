# Security & Threat Model

This document outlines the security architecture, Active Directory communication paths, container hardening, and threat model.

---

## 1. Directory Communication (LDAPS)

To align with corporate enterprise security regulations:
- Production environments connect to Active Directory using **LDAPS** (LDAP over SSL/TLS) on port `636`.
- Certificates are injected inside container volume mounts (`/app/certs/`) and loaded into the Java keystore on startup.
- Production bind operations require valid domain service credentials mapping to read-only directories.

---

## 2. Container Hardening

The application container uses multiple protection layers:
- **No Root Execution**: The CentOS Stream 9 container drops root capabilities (`cap_drop: ALL`) and runs as an unprivileged system user (`UID/GID 10001`).
- **No Privilege Escalation**: Configured with `no-new-privileges:true` security options to prevent setuid binaries from gaining root.
- **Read-Only System Files**: The application jar is immutable within `/app/`. Volume mount storage areas (`/app/logs/`, `/app/spool/`) use strict folder ownership restrictions.

---

## 3. Database Permissions & soft-delete

- **Credential Isolation**: Database logins require custom credentials matching only the required print quota tables.
- **Soft Delete Pattern**: To protect historical audit data consistency:
  - Users are soft-deleted instead of dropped.
  - Hibernate maps soft delete calls: `@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ?")` and enforces filter restrictions: `@SQLRestriction("deleted = false")`.

---

## 4. Threat Matrix & Auditing Traceability

The system logs every request in PostgreSQL to ensure audit compliance:

| Threat Vector | Countermeasure Implemented | Status |
|---|---|---|
| Double-Spend Quota Deduction | Pessimistic locks (`SELECT ... FOR UPDATE`) serialize concurrent page deduction requests | Mitigated |
| Spoofed User Requests | Validation of `requesting-user-name` and user active directory active check | Mitigated |
| Untraceable Print Traffic | Audit logs written using `REQUIRES_NEW` transactions capturing correlation IDs | Mitigated |
| Man-in-the-Middle Sync Attacks | Secure TLS channel binding via LDAPS port `636` | Mitigated |
| Container Runtime Intrusion | Root capability drop and unprivileged container users | Mitigated |
