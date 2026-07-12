# Deployment Validation Guide

This document describes the testing scenarios and script commands used to validate a successful production deployment.

---

## 1. Startup & Probe Verification

Immediately after booting the application container, verify service state:

```bash
# 1. Verify Startup Status
curl -i http://localhost:8080/actuator/health/startup
# Expect: HTTP 200 {"status":"UP"}

# 2. Verify Readiness (Postgres and LDAP check)
curl -i http://localhost:8080/actuator/health/readiness
# Expect: HTTP 200 {"status":"UP"}

# 3. Verify Container Info (OS and CPU cores details)
curl -i http://localhost:8080/actuator/info
# Expect: HTTP 200 {"container":{"osName":"Linux"...}}
```

---

## 2. Quota Validation Integration Test

Validate balance deduction and audit logging end-to-end:

1. **Submit print request** using curl:
```bash
curl -i -X POST -H "Content-Type: application/ipp" \
     -H "X-Correlation-ID: val-test-101" \
     --data-binary @test_job.ipp \
     http://localhost:8080/printers/LaserJet_5
```
2. **Verify Response**: Expect HTTP 200 containing binary IPP response.
3. **Audit Verification**: Connect to database and verify audit tables:
```sql
SELECT status, page_count, correlation_id FROM print_logs WHERE correlation_id = 'val-test-101';
-- Expect: status = 'SUCCESS', page_count = 10 (or matching calculation)
```

---

## 3. Database Resilience Validation

1. Trigger database restart during container operations:
```bash
docker compose restart print-quota-db
```
2. Call readiness probe: `/actuator/health/readiness` should show `DOWN` during database restart and return `UP` automatically once Postgres is active again.
