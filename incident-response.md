# Incident Response Manual

This document details the triage and recovery procedures for common operational failure scenarios.

---

## 1. Scenario A: Physical Printer Offline / Unreachable

### Indicators
- Logs report: `Error forwarding print stream to printer... ConnectException: Connection refused`.
- Metric `proxy_printer_failures_total` spikes.

### Triage Steps
1. Verify target printer network reachability:
```bash
ping <printer-ip>
```
2. Call health status endpoint: `http://localhost:8080/actuator/health/readiness`. Under `details.printers`, verify status shows `OFFLINE` or `UNREACHABLE`.
3. Check configured port mapping inside `application.yml` or container environment overrides.

---

## 2. Scenario B: Active Directory / LDAP Outage

### Indicators
- Logs report: `LDAP read failure during synchronization`.
- Daily identity synchronizations report errors.
- New domain users are unable to print because they are missing from Postgres.

### Triage & Remediation
1. Verify LDAP container status: `docker compose ps`.
2. Inspect LDAP template reachability:
```bash
curl -i http://localhost:8080/actuator/health/readiness
# Look for "ldap": {"status":"DOWN"} in details
```
3. Restart LDAP sync triggers manually once network connection resolves:
```bash
curl -i -X POST http://localhost:8080/api/v1/admin/sync
```

---

## 3. Scenario C: Database Corruption or Lock Timeouts

### Indicators
- Logs report: `CannotAcquireLockException` or `TransactionTimedOutException`.
- Actuator health endpoints report `DOWN`.

### Triage Steps
1. Inspect PostgreSQL container state: `docker ps`.
2. Review locked rows using administrative SQL queries:
```sql
SELECT pid, query, state, age(clock_timestamp(), query_start) FROM pg_stat_activity WHERE state != 'idle';
```
3. If deadlocks persist due to orphaned container connections, restart the database:
```bash
docker compose restart print-quota-db
```
