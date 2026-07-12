# Operations Handover Checklist (v1.0 GA)

This checklist provides IT Operations staff with daily, weekly, and monthly maintenance checks to keep the print management system healthy.

---

## 1. Daily Operations Checklist

- [ ] **Verify Readiness Probes**:
  Check that the local endpoints return successful statuses:
  - URL: `http://localhost:8080/actuator/health/readiness`
  - Expect: HTTP 200 `{"status":"UP"}`
- [ ] **Inspect Log Errors**:
  Review `/var/log/print-quota/service.log` for high volume database timeouts (`CannotAcquireLockException`) or LDAP connection warning messages.
- [ ] **Track Memory Utilizations**:
  Confirm that JVM heap usage stays within bounds (maximum 75% of container RAM limits).

---

## 2. Weekly Maintenance Checklist

- [ ] **Run Retention Cleanup**:
  Confirm that the weekly retention cron executes successfully on Sundays at midnight to purge logs older than 30 days.
- [ ] **Execute Backup Drills**:
  Verify backup file is generated inside `./backup_archive/`. Perform a validation dry run:
  - Command: `Powershell -File ./scripts/backup.ps1`
- [ ] **Inspect Disk Space**:
  Check host partition storage capacity to prevent log growth disk exhaustion.

---

## 3. Monthly Maintenance Checklist

- [ ] **Monitor Quota Allocation Reset**:
  Confirm that on the 1st of the month, `ReportScheduler` logs report successful generation of quota allocations for the new month.
- [ ] **Verify SMTP Email Dispatches**:
  Check administrative mailboxes for the monthly/daily operations summary emails containing Excel logs attachments.
- [ ] **System Compliance Audit**:
  Verify the system running state is secure and no new non-compliant services are running under root namespaces.
