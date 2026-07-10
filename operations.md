# Operations & Maintenance Guide

This document describes deployment, maintenance, log rotation, prometheus monitoring, and backup procedures.

---

## 1. Deployment Procedures

### Build & Package
Run clean packaging inside the host machine:
```bash
./mvnw clean package -Djacoco.skip=true -DskipTests
```

### Compose Orchestration Build
Build and pull all services in detached mode:
```bash
docker compose build
docker compose up -d
```

### Verify Container Status
Check running services status:
```bash
docker compose ps
```

---

## 2. Health Monitoring & Alerting

### Health Endpoint Check
Verify Actuator status:
- Liveness check path: `http://localhost:8080/actuator/health/liveness`
- Readiness check path: `http://localhost:8080/actuator/health/readiness`

### Prometheus Metrics Scrape
- URL: `http://localhost:8080/actuator/prometheus`
- Exposes: JVM heap allocations, GC pauses, database Hikari connection pool usage, active thread counts, and print processing metrics (`print.requests.received`, `print.evaluation.latency`).

---

## 3. Log Maintenance & Rotation

- **Log Path**: `/var/log/print-quota/service.log` (Mapped from named volume `printquota_app_logs` under Docker).
- **Log Format**: JSON formatted logs in production for ingestion by log collectors (Splunk, ELK).
- **Rotation Rules**:
  - Size Limit: Rolls logs to gzip files if they exceed `50MB`.
  - Max Files: Retains archived logs for `30 days`.
  - Disk Cap: Limits total archive size to `5GB` to prevent disk exhaustion.

---

## 4. Backups and Database Recovery

### Backup Postgres Schema & Data
Create logical dumps from the PostgreSQL container:
```bash
docker exec -t print-quota-db pg_dumpall -c -U printuser > printquota_dump.sql
```

### Database Restore Procedure
Re-import backups to clean databases:
```bash
cat printquota_dump.sql | docker exec -i print-quota-db psql -U printuser -d printquota
```
*Note: Liquibase will check metadata tables (`databasechangelog`) and skip applying migrations that have already run, preventing database duplication anomalies.*
