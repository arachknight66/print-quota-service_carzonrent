# Disaster Recovery (DR) Drill Execution Log

**System**: Print Quota Management System  
**Drill Objective**: Validate manual promote-replica scripts, runbooks, and DNS failover processes under simulated master VM database failure.  
**Reference Document**: [DR_RUNBOOK.md](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/postgres/DR_RUNBOOK.md)

---

## 1. Execution Overview

- **Drill Date**: _[YYYY-MM-DD]_  
- **Lead Engineer / On-Call Operator**: _[Name]_  
- **Witness / Security Approver**: _[Name]_  
- **DR Scenario Simulated**: Complete failure of PostgreSQL primary database node (Master VM power off).

---

## 2. Timing and SLA Metrics

| Phase Milestone | Target SLA | Actual Time |
|---|---|---|
| **Incident Trigger** (Primary VM Shutdown) | 02:00:00 | _[Timestamp]_ |
| **Symptom Detection** (Alerts fired / logs Refused) | < 3 minutes | _[Timestamp]_ |
| **Isolation Confirmation** (Primary VM confirmed DOWN) | < 5 minutes | _[Timestamp]_ |
| **standby Promotion Execution** (`promote-replica.sh`) | < 2 minutes | _[Timestamp]_ |
| **Worker Redirection Complete** (Workers restarted/VIP swapped) | < 5 minutes | _[Timestamp]_ |
| **Recovery Verification** (First successful print job processed) | < 5 minutes | _[Timestamp]_ |
| **Total Recovery Time (RTO)** | **< 20 minutes** | **_Total Minutes_** |

---

## 3. Step-by-Step Drill Execution Log

Verify and initial each checkpoint:

- **[ ] Checkpoint 1: Isolation**
  - Confirmed the old primary master VM is powered off/network-isolated.
  - _Initials:_ ______
- **[ ] Checkpoint 2: Promotion**
  - Executed `./promote-replica.sh` on the standby database host.
  - Output verified: `pg_is_in_recovery()` returned `f`.
  - _Initials:_ ______
- **[ ] Checkpoint 3: Redirection**
  - Swapped connection destination (floating VIP verified or worker configs updated and restarted).
  - _Initials:_ ______
- **[ ] Checkpoint 4: Verification**
  - Submitted test print job via client workstation. IPP status returned 0x0000 (Successful).
  - Checked `/api/v1/me/quota` to verify balance is retrieved correctly.
  - _Initials:_ ______

---

## 4. Data Integrity Spot-Check

Perform random checks on 3 user accounts to confirm quota values persisted without drift:

| Employee Username | Expected Pages Used (Pre-Drill) | Actual Pages Used (Post-Failover) | Matching? |
|---|---|---|---|
| _[User 1]_ | _[e.g., 40]_ | _[e.g., 40]_ | `[ ]` Yes / `[ ]` No |
| _[User 2]_ | _[e.g., 12]_ | _[e.g., 12]_ | `[ ]` Yes / `[ ]` No |
| _[User 3]_ | _[e.g., 0]_ | _[e.g., 0]_ | `[ ]` Yes / `[ ]` No |

Database total row count audit:
- `SELECT COUNT(*) FROM print_logs;` (Post-Failover): ____________
- `SELECT COUNT(*) FROM quotas;` (Post-Failover): ____________

---

## 5. Post-Drill Recovery & Re-onboarding

Follow section 5 of the DR Runbook to wipe the old master database directory and convert it to a streaming standby replica of the *new* master:
- **[ ] Re-onboarding Complete**: Verified the old primary node successfully connects as `backup/replica` (checked `pg_stat_replication` on the new master).
- _Initials:_ ______

---

## 6. Lessons Learned & Action Items

Record any issues observed (e.g. script errors, config delays, documentation gaps):
- _Issue 1:_ ____________________
- _Issue 2:_ ____________________

**Action Items**:
- [ ] Update Keepalived weights or timeout limits if necessary.
- [ ] Train support staff on log queries during VM outage.
