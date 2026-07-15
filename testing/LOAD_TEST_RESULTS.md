# Load Test Execution & Verification Report

**System**: Print Quota Management System  
**Test Objective**: Validate active-active multi-instance load distribution, failover resilience, and database locking contention prior to production cutover.  
**Configuration Plan**: [load-test-plan.jmx](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/testing/load-test-plan.jmx) (300 concurrent users / thread spoolers)

---

## 1. Execution Metadata

| Parameter | Value / Template |
|---|---|
| **Date Run** | _[YYYY-MM-DD]_ |
| **HAProxy Version** | _[e.g., HAProxy 2.4.x]_ |
| **Worker App Instances** | 2 (`worker1`, `worker2`) |
| **Database Topology** | PostgreSQL 16 (Primary + Streaming Standby) |
| **Total Requests Submitted** | 1,500 (300 threads * 5 loops) |

---

## 2. Workload Assumptions

The test target of **300 concurrent threads** is established based on the following enterprise load projections:
- **Total Registered Employees**: 300–400 users
- **Peak Concurrency Ratio**: 20% active users submitting jobs simultaneously (e.g. morning rush hours) = ~70 concurrent print spoolers.
- **Load Test Scale**: 300 concurrent threads (represents a **4x safety factor** over peak concurrency to verify server resources, connection pool capacities, and locking safety).

---

## 3. Key Performance Indicators (KPIs)

| Metric | Target Goal | Observed Result (Template) | Status |
|---|---|---|---|
| **p50 Latency** | < 100ms | _[e.g., 18ms]_ | _[Pass/Fail]_ |
| **p95 Latency** | < 250ms | _[e.g., 45ms]_ | _[Pass/Fail]_ |
| **p99 Latency** | < 500ms | _[e.g., 110ms]_ | _[Pass/Fail]_ |
| **Error Rate** | 0.00% (No network drops) | _[e.g., 0.00%]_ | _[Pass/Fail]_ |
| **DB Pool Saturation** | Max 80% (Hikari pool) | _[e.g., 40%]_ | _[Pass/Fail]_ |

---

## 4. Failover Resilience Tests (Chaos Engineering)

To satisfy the Phase 2 exit criteria, one worker app node is rebooted while under load:

| Test Action | Expected Behavior | Observed Result | Status |
|---|---|---|---|
| **Reboot `worker1`** | HAProxy health check detects failure (Actuator `/health/readiness` fails). HAProxy routes all new TCP streams to `worker2` within 5s. Zero failed print transactions returned to the client. | _[Describe behavior during run]_ | _[Pass/Fail]_ |

---

## 5. Quota Reservation Safety Verification

Verify that pessimistic locks prevented double deductions under heavy thread contention:

- **Expected**: Out of 1,500 requests, exactly the allocated amount of pages were deducted. No database deadlocks or transaction timeouts were thrown.
- **Verification Query**:
  ```sql
  -- Run on PostgreSQL Primary to confirm final quota state
  SELECT allocated_pages, used_pages FROM quotas WHERE month = '2026-07';
  ```
- **Observations**: _[Record final DB values and trace audit logs]_

---

## 6. Exit Criteria Status

- **[ ] Exit Criteria 1**: Successful load distribution across both worker instances under 300 concurrent threads.
- **[ ] Exit Criteria 2**: Failover checklist: rebooting of one worker node causes no visible spooler or client connection failure.
- **[ ] Exit Criteria 3**: Pessimistic write locks successfully prevented over-allocation of quota under concurrency.

**Approval Status**:  
`[ ]` Approved for Cutover  
`[ ]` Rejected (Explain corrective actions required below)

**Sign-off**:  
_DevOps Engineer Signature:_ ____________________  
_QA Lead Signature:_ ____________________
