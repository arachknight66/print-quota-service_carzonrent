# Security Audit Report

This report summarizes the final security audit, covering the implementation of OWASP regulations, HTTP header filters, container isolation, and least-privilege configurations.

---

## 1. OWASP Compliance Matrix

- **HTTP Security Headers**: Enforced via `SecurityHeadersFilter.java` adding `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and strict XSS policies.
- **Input and Protocol Validation**:
  - `ProtocolValidationStage.java` rejects payloads that lack mandatory user properties or operation codes.
  - Reject actions return binary RFC 8011 IPP responses rather than exposing verbose raw HTTP trace exceptions.
- **Vulnerability Isolation**:
  - CycloneDX Maven plugin produces a Software Bill of Materials (SBOM) listing coordinates to verify supply chain security.

---

## 2. LDAP and Database Access Controls

- **Database Credentials**: Mapped to a dedicated schema user (`printuser`) rather than using administrative accounts.
- **LDAPS Support**: Configured to connect over SSL (`port 636`) in production environments to secure Active Directory sync queries.
- **Audit Logs Protection**: All audit log writes are executed under isolated `REQUIRES_NEW` transactions, preventing trace suppression during system failures.

---

## 3. Container Security Hardening

- **Non-Root Execution**: Replaced CentOS root defaults by mapping UID/GID `10001` and system user `printuser`.
- **Dropped Capabilities**:
  - Container runs with dropped Linux kernel privileges (`cap_drop: ALL`).
  - Flag `no-new-privileges:true` is set to block setuid elevation attacks.
- **Read-Only Runtime**: Application JAR files are stored on a read-only root system partition.
