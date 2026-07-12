# Release Candidate Checklist

This checklist outlines the mandatory quality gates and validation steps required before declaring a production release candidate (RC) build.

---

## 1. Quality Gates (Pre-Build)

- [ ] **Static Code Analysis**: Run Checkstyle, PMD, and SpotBugs. Build must contain zero errors.
  - Command: `.\mvnw.cmd clean compile spotbugs:check pmd:check checkstyle:check`
- [ ] **Test Coverage**: JaCoCo line coverage must be verified (minimum 80% on business classes).
- [ ] **Dependency Audit**: Run OWASP dependency-check to inspect library CVEs.
  - Command: `.\mvnw.cmd dependency-check:check`

---

## 2. Artifact Integrity (Post-Build)

- [ ] **SBOM Verification**: Verify CycloneDX SBOM files (`target/cyclonedx/bom.json`) exist and list all third-party coordinates.
- [ ] **Jar Execution**: Validate that the packaged jar compiles and starts correctly.
- [ ] **Docker Assembly**: Verify the multi-stage CentOS Stream 9 image builds without warnings.
  - Command: `docker compose build`

---

## 3. Deployment Validation

- [ ] **Health Probes Check**: Verify `/actuator/health/readiness` and `/actuator/health/liveness` return `UP` under mock environments.
- [ ] **Pessimistic Locking Verification**: Verify concurrent test classes pass successfully without deadlock timeouts.
- [ ] **Admin Authentication**: Confirm endpoints under `/api/v1/admin/` are restricted at network level.
