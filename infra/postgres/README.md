# PostgreSQL Streaming Replication and Standby Setup

**System**: Print Quota Management System  
**Topology**: PostgreSQL 16 (Master VM) + Standby Database (Worker VM)  
**Target OS**: CentOS Stream 9  
**Last Updated**: 2026-07

---

## 1. Streaming Replication Architecture

To prevent data loss and support disaster recovery, PostgreSQL streaming replication is established between the Master VM (Primary) and a Standby VM. Write operations (quota reservation deductions, audit logging) execute on the Primary, which stream WAL files to the replica.

```
 [Application Node 1]      [Application Node 2]
        |                         |
        +------------+------------+
                     |  (Reads/Writes)
                     v
             [Primary Database]
                     |
                     |  (Streaming WAL)
                     v
             [Standby Database]
```

---

## 2. Master VM (Primary) Setup

Execute the configuration script on the Primary server to define replication slots, HBA client configurations, and restart the DB:

```bash
# 1. Edit primary-setup.sh and replace <REPLICA_IP> with the Standby VM IP.
# 2. Make script executable and run:
chmod +x primary-setup.sh
sudo ./primary-setup.sh
```

---

## 3. Worker VM (Standby/Replica) Setup

Pull the base backup directory from the Primary database and initiate replication:

```bash
# 1. Edit replica-setup.sh and replace <PRIMARY_IP> with the Primary VM IP.
# 2. Make script executable and run:
chmod +x replica-setup.sh
sudo ./replica-setup.sh
```

---

## 4. Verification and Runbooks

### Verify Replication Lag (Run on Primary)
Execute this query inside PostgreSQL on the master VM to track active replicas and their lag state:

```sql
SELECT
    client_addr AS replica_ip,
    application_name,
    state,
    sync_state,
    pg_wal_lsn_diff(pg_current_wal_lsn(), sent_lsn) AS sent_lag_bytes,
    pg_wal_lsn_diff(sent_lsn, write_lsn) AS write_lag_bytes,
    pg_wal_lsn_diff(write_lsn, flush_lsn) AS flush_lag_bytes,
    pg_wal_lsn_diff(flush_lsn, replay_lsn) AS replay_lag_bytes
FROM pg_stat_replication;
```

---

## 5. Manual Failover / Promotion Runbook

> [!WARNING]
> In Phase 2, database failover is **MANUAL**. Automatic routing or switchover is out of scope. If the primary database fails, follow these steps immediately to promote the replica to write mode:

**Step 1** — Promote the replica database on the Standby node:
```bash
# Run pg_ctl promote or use postgresql service commands
sudo -u postgres pg_ctl promote -D /var/lib/postgresql/16/main
```

**Step 2** — Update the application configuration on the worker nodes:
Update the production environment variable `SPRING_DATASOURCE_URL` or configuration parameter in `application-prod.yml` to point to the newly promoted Standby database's IP address.

**Step 3** — Restart both print-quota-core instances to establish connection pools to the new master:
```bash
sudo systemctl restart print-quota
```

---

## 6. Database Backups (Cron configuration)

The [backup.sh](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/infra/postgres/backup.sh) script is scheduled daily on the master VM.

To schedule the backup via cron, run:
```bash
sudo crontab -e
```

And add this entry to run the backup daily at 02:00 AM (UTC):
```cron
0 2 * * * /bin/bash /opt/printkeep/infra/postgres/backup.sh >> /var/log/printkeep/backup.log 2>&1
```
