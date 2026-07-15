# Phase 1 Pilot Rollout Gate — Exit Checklist

**System**: Print Quota Management System  
**Rollout Target**: 300–400 employees on the single master CentOS Stream 9 VM  
**Status**: Complete all items before promoting from pilot (≤ 20 users) to full rollout.

---

## Instructions

This checklist must be completed and signed off by IT Operations and a developer representative before the full employee rollout begins. Items marked **CRITICAL** are mandatory blockers — no rollout proceeds until they pass. All other items are important but may be tracked as follow-up tasks if documented.

| Symbol | Meaning |
|---|---|
| `[ ]` | Not done |
| `[x]` | Done and verified |
| `[N/A]` | Not applicable (document reason) |

---

## 1. Security — mTLS Identity Binding

| # | Check | Category | Owner |
|---|---|---|---|
| 1.1 | `application-prod.yml` has `server.ssl.client-auth: need` (verify with `grep client-auth`) | CRITICAL | Dev |
| 1.2 | At least 2 pilot employee workstations have issued client certificates with CN = sAMAccountName | CRITICAL | IT PKI |
| 1.3 | A test print job from a valid cert (correct CN) is ALLOWED with IPP status 0x0000 | CRITICAL | Dev/IT |
| 1.4 | A test print job from a cert where CN ≠ requesting-user-name is REJECTED with IPP status 0x0401 | CRITICAL | Dev |
| 1.5 | A connection without any client certificate is dropped at the TLS layer (no application-level response) | CRITICAL | IT/Network |
| 1.6 | `docs/CERTIFICATE_ISSUANCE.md` has been reviewed and approved by IT PKI team | Important | IT PKI |

---

## 2. Security — Admin Endpoint Network Lockdown

| # | Check | Category | Owner |
|---|---|---|---|
| 2.1 | `app.admin.ip-filter-enabled: true` is confirmed in production environment variables | CRITICAL | DevOps |
| 2.2 | `ADMIN_ALLOWED_NETWORKS` env var is set to the actual IT management subnet CIDR (not the default `10.0.0.0/8`) | CRITICAL | IT |
| 2.3 | Accessing `GET /api/v1/admin/users` from a general WiFi/WLAN client returns HTTP 403 | CRITICAL | IT |
| 2.4 | Accessing `GET /api/v1/admin/users` from the IT management VLAN returns the expected response (or 401 if unauthenticated) | CRITICAL | IT |
| 2.5 | DEPLOYMENT.md admin network lockdown section has been reviewed | Important | DevOps |

---

## 3. Quota Refund on Forwarding Failure

| # | Check | Category | Owner |
|---|---|---|---|
| 3.1 | `quota.refunds.total` counter appears in `/actuator/prometheus` output | Important | Dev |
| 3.2 | Simulate a printer failure (e.g. stop the test printer, then submit a job) and verify: (a) the quota is refunded, (b) a PrintLog ERROR row exists in the DB, (c) the `quota_refunds_total` counter increments | CRITICAL | Dev |
| 3.3 | Check that the original PrintLog SUCCESS row is not mutated by the refund (audit trail integrity) | CRITICAL | Dev |

---

## 4. Self-Service Employee Quota Balance

| # | Check | Category | Owner |
|---|---|---|---|
| 4.1 | `GET /api/v1/me/quota` from a valid cert-authenticated workstation returns 200 with correct balance JSON | CRITICAL | Dev/IT |
| 4.2 | `GET /api/v1/me/quota` from a workstation with no client cert returns connection error (TLS layer) | Important | IT |
| 4.3 | `GET /api/v1/me/quota` with a valid cert but a CN that has no DB record returns 404 JSON | Important | Dev |
| 4.4 | The endpoint response includes `quotaUtilizationPercentage` computed correctly | Important | Dev |

---

## 5. Baseline Monitoring

| # | Check | Category | Owner |
|---|---|---|---|
| 5.1 | Prometheus is scraping the print quota service (verify in Prometheus UI: `up{job="printkeep"} == 1`) | CRITICAL | DevOps |
| 5.2 | The Grafana dashboard `testing/grafana-dashboard.json` is imported and showing live data | Important | DevOps |
| 5.3 | All 5 panels have data (including the `quota_refunds_total` panel, which should be 0 at baseline) | Important | DevOps |
| 5.4 | Prometheus alert rules (from `docs/MONITORING_SETUP.md`) are configured and test-firing correctly | Important | DevOps |
| 5.5 | The `HighIdentityFailureRate` alert fires correctly when more than 1 identity failure/5s occurs | Important | Dev/DevOps |
| 5.6 | `/actuator/health/liveness` returns 200 UP | CRITICAL | DevOps |
| 5.7 | `/actuator/health/readiness` returns 200 UP (database + LDAP healthy) | CRITICAL | DevOps |

---

## 6. Pilot User Group Validation

| # | Check | Category | Owner |
|---|---|---|---|
| 6.1 | 15–20 pilot employees are onboarded (certs issued, DNS/routing configured for their workstations) | CRITICAL | IT |
| 6.2 | Each pilot employee can successfully print a test document (end-to-end) | CRITICAL | IT |
| 6.3 | Each pilot employee can check their own balance via `GET /api/v1/me/quota` (or via the employee web portal if built) | Important | Dev/IT |
| 6.4 | No quota calculation errors observed over a 3-business-day pilot window | CRITICAL | IT |
| 6.5 | No unhandled exceptions in the application log during the pilot window | CRITICAL | Dev |
| 6.6 | `quota_refunds_total` remains at 0 during the pilot window (no printer failures) | Important | IT |

---

## 7. Operational Readiness

| # | Check | Category | Owner |
|---|---|---|---|
| 7.1 | A tested backup and restore procedure exists for the PostgreSQL database | CRITICAL | DevOps |
| 7.2 | The LDAP sync cron job is running and correctly updating `is_active` flags | CRITICAL | IT |
| 7.3 | The application log file is being rotated (logrotate or journald) and not filling the disk | Important | DevOps |
| 7.4 | A runbook for common failure scenarios (LDAP down, printer unreachable, disk full) has been drafted | Important | Dev/IT |
| 7.5 | IT has been trained on the admin API endpoints and basic troubleshooting | Important | IT |

---

## Sign-Off

| Role | Name | Date | Signature |
|---|---|---|---|
| Developer Representative | | | |
| IT Operations Lead | | | |
| IT PKI Administrator | | | |
| DevOps / Monitoring Lead | | | |

**Decision**:  
`[ ]` Approved for full rollout (all CRITICAL items checked)  
`[ ]` Conditional approval (document exceptions below)  
`[ ]` Not approved — return to development

**Exceptions / Notes**:

> _(Document any conditional approvals or deferred items here)_

---

*Once all CRITICAL items are checked and the sign-off section is complete, this document should be archived in the project's documentation repository with the date and rollout version noted.*
