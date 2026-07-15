# Monthly OS/App Patching and Rollout Procedure

**System**: Print Quota Management System  
**Topology**: 1 Master VM + 2 Worker VMs  
**Last Updated**: 2026-07

---

## 1. Rolling Upgrade Strategy

To maintain high availability and prevent downtime, patching and app rollouts are executed in a **rolling** fashion across the 2 worker VMs. One worker is drained from the HAProxy load balancer, patched, and smoke-tested before repeating on the next worker.

The master VM (hosting PostgreSQL and LDAP synchronization scheduler) is patched during a scheduled **maintenance window** (e.g. Sunday 02:00 AM) since database write paths are single-master.

---

## 2. Phase-by-Phase Execution Runbook

### Phase A: Staging Validation
1. Deploy the patch or application update onto a staging/QA environment.
2. Confirm the database migrations run cleanly.
3. Validate printer routing, mTLS client certification extraction, and audit log writes.

### Phase B: Rebuilding Application Image (CI/CD / Rebuild Node)
Compile and build the updated production package:
```bash
./mvnw clean package -pl print-quota-core -Djacoco.skip=true
```
Create the new Docker container image tagged with the release version:
```bash
docker build -t print-quota-core:v1.1.0 .
```

### Phase C: Worker Rollout Sequence
Utilise the automated rolling deployment script on the master VM to update the workers:

```bash
cd /opt/printkeep/infra/patching
chmod +x rolling-deploy.sh
# Execute rolling deployment passing the target image tag
./rolling-deploy.sh print-quota-core:v1.1.0
```

### Phase D: Master VM OS Patching (PostgreSQL & OS Updates)
1. Schedule a low-traffic window.
2. Trigger database backup:
   ```bash
   /bin/bash /opt/printkeep/infra/postgres/backup.sh
   ```
3. Run OS patches:
   ```bash
   sudo dnf update -y
   ```
4. Restart PostgreSQL and verify stream replication status on the standby node.
