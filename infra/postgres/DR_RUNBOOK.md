# Disaster Recovery (DR) Runbook — PostgreSQL Standby Promotion

**System**: Print Quota Management System  
**Role**: Manual failover instructions for tier-2/3 operations on database loss  
**Last Updated**: 2026-07

---

> [!IMPORTANT]
> **READ BEFORE PROCEEDING**: Database failover and standby replica promotion is a **MANUAL** operation. Under no circumstances does this system automatically promote the standby replica. This is designed to prevent split-brain anomalies under transient network partitions.

---

## 1. Symptoms Indicating Primary Database Failure

The primary PostgreSQL database node is considered down if:
- Actuator endpoint `/actuator/health/readiness` on the workers returns `DOWN` with `db` or `mail` health indicators showing failure.
- Worker log files (`/var/log/printkeep/app.log`) are saturated with connection timeouts:
  ```
  org.postgresql.util.PSQLException: Connection to 10.10.5.10:5432 refused.
  ```
- Prometheus alerts trigger `PrintQuotaSystemDown` or `PrinterForwardingFailureSpike` continuously for > 5 minutes.

---

## 2. Decision Criteria for Standby Promotion (Avoiding Split-Brain)

> [!CAUTION]
> **Split-Brain Hazard**: If both database nodes operate in Primary (Write) mode simultaneously, data corruption and quota tracking drift will occur.

Do not trigger standby promotion unless:
1. **The Old Primary VM is Fully Down / Isolated**: Confirmed via hypervisor status (powered off) or network link shutdown (NIC disabled or port shut). You must guarantee the old primary cannot write to disks.
2. **Connectivity is Verified**: Standby node has stable network routing to the Active Directory domain and both application workers.

---

## 3. Step-by-Step Scripted Promotion Procedure

Once the old primary is confirmed down/isolated, run the following steps:

**Step 1** — Log into the Standby database VM (Replica) via SSH.

**Step 2** — Run the promotion script:
```bash
cd /opt/printkeep/infra/postgres
chmod +x promote-replica.sh
sudo ./promote-replica.sh
```

**Step 3** — Verify write mode:
Confirm that SQL queries containing writes can run:
```bash
sudo -u postgres psql -d printquota -c "SELECT pg_is_in_recovery();"
# Response MUST be 'f'.
```

---

## 4. Redirecting Worker Nodes (Application Setup)

Post-promotion, the worker application nodes must redirect their write traffic to the new Primary database IP.

### Option A: Manual IP Re-configuration (Standard Setup)
1. Edit `/etc/printkeep/application-prod.yml` (or set environment variables) on **both** worker nodes.
2. Update the JDBC URL:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://<NEW_PRIMARY_IP>:5432/printquota
   ```
3. Restart both services:
   ```bash
   sudo systemctl restart printkeep
   ```

### Option B: Keepalived Floating VIP (Advanced Setup)
If [keepalived.conf](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/postgres/keepalived.conf) was deployed prior to the incident:
- The floating IP (`<FLOATING_DB_VIP>`) automatically binds to the Standby node when `check_postgres` detects it is active.
- No config modification is required on worker nodes, as their JDBC URLs are pre-configured to point to `<FLOATING_DB_VIP>`.

---

## 5. Reintroducing the Old Primary

When the failed master server is recovered, **DO NOT** simply start the PostgreSQL service. You must wipe its data directory and re-onboard it as a standby replica of the *new* master:

```bash
# 1. Stop postgresql on recovered node
sudo systemctl stop postgresql

# 2. Re-run pg_basebackup pointing to the NEW primary to align LSN history
# (Follow the exact steps in /infra/postgres/replica-setup.sh)
```
