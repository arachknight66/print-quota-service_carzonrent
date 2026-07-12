# PrintKeep Administrator Handbook (v1.0 GA)

This document provides system administrators with setup, management, troubleshooting, and restoration guidelines.

---

## 1. System Overview

PrintKeep is an enterprise-grade print quota enforcement system and IPP proxy. It acts as a transparent filter between office client workstations and network printers:

- **Identity Sync**: Automated nightly synchronizations fetch user metadata and departments from Active Directory.
- **Quota Validation**: Compares print job page requests against the current month's remaining balance using pessimistic database write locking.
- **Proxy Routing**: Forwards allowed print streams to destination printers in low-memory, chunked streams.

---

## 2. Global Configurations

All properties are configured inside `application.yml` or overridden via environment variables:

| Environment Variable | Description | Default Value |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/printquota` |
| `SPRING_LDAP_URLS` | Active Directory LDAP URLs | `ldap://localhost:10389` |
| `APP_QUOTA_DEFAULT_LIMIT` | Monthly default user page allocation | `100` |
| `APP_PRINTERS_ROUTING_MAP` | Logical name to target printer URI mapping | `LaserJet_5=http://printer.local/ipp` |
| `APP_ADMIN_EMAILS` | Target comma-separated admin emails | `admin@company.local` |

---

## 3. Printer Registration and Routing

To register new logical network printers, update the routing map key-values in `application.yml` or container variables:
```yaml
app:
  printers:
    routing-map:
      LaserJet_5: "http://printer1.company.local:631/ipp/print"
      Finance_Printer: "http://printer2.company.local:631/ipp/print"
```
During operations, client print queries targeting `http://proxy:8080/printers/LaserJet_5` will automatically route to the corresponding destination URI.

---

## 4. Quota Adjustments & Resets

Admin operations are exposed via administrative REST endpoints.

### Adjust User Quota
- **POST** `/api/v1/admin/quotas/{userId}/adjust`
- Request: `{"adjustment": 50}` (increases quota limit by 50 pages; negative values decrease limit).

### Manual Quota Reset
- **POST** `/api/v1/admin/quotas/reset`
- Request: `{"defaultPages": 150}` (resets all monthly balances to 150 pages).

---

## 5. Active Directory / LDAP Synchronization

Active Directory sync matches sAMAccountName records to PostgreSQL. The scheduler triggers daily.
To trigger an immediate manual synchronization:
- **POST** `/api/v1/admin/sync`
- Returns: `{"status":"SUCCESS","message":"Active Directory sync completed"}`

---

## 6. Logs Interpretation

Logs are formatted in single-line JSON structure for simple search integration (Splunk, Elasticsearch):
- **Core fields**: `timestamp`, `level`, `logger`, `message`, `correlationId`.
- **Example Log**:
```json
{"timestamp":"2026-07-12T10:45:00.102Z","level":"INFO","message":"Print job ALLOWED. Consumed pages: 5","correlationId":"uuid-999"}
```

---

## 7. Backup, Recovery & Disaster Recovery

Run backup/restore operations on host machines:
- **Execute Backup**: `Powershell -File ./scripts/backup.ps1`
- **Execute Restoration**: `Powershell -File ./scripts/restore.ps1 -DbBackupFile ./backup_archive/printquota_db_20260712_120000.sql`
- **RTO**: 30 minutes.
- **RPO**: 24 hours.
