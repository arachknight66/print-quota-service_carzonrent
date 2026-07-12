# Backup & Disaster Recovery Guide

This document describes the backup scheduling, data preservation, and recovery validation procedures.

---

## 1. Backup Execution Procedures

Backups are executed on host machines using the provided PowerShell scripts:

```powershell
# Run the backup script to archive database, certs, and logs
./scripts/backup.ps1
```

### Generated Artifacts
- **Database Dump**: SQL file `backup_archive/printquota_db_YYYYMMDD_HHMMSS.sql` containing logical Postgres schemas, user records, and print audit histories.
- **Certificates Backup**: Directory `backup_archive/certs_YYYYMMDD_HHMMSS/` archiving the active SSL certificates.

---

## 2. Restoration Procedures

To restore services from an archived state:

```powershell
# Run the restore script passing the path to the backup file
./scripts/restore.ps1 -DbBackupFile "./backup_archive/printquota_db_20260712_120000.sql"
```

### Verification Checks
1. Validate that the Postgres container is active: `docker ps`.
2. Inspect log tables size to confirm data was re-imported successfully:
```sql
SELECT COUNT(*) FROM print_logs;
```

---

## 3. Disaster Recovery Objectives

- **Recovery Point Objective (RPO)**: 24 hours (supported by daily automated database logical dumps).
- **Recovery Time Objective (RTO)**: 30 minutes (supported by Docker Compose orchestration and scripted Postgres schema restore commands).
