# Operations Runbook

This document provides system administrators with instructions for operational monitoring, logging, and metrics.

---

## 1. Logs Location and Ingestion

- **Path**: `/var/log/print-quota/service.log`
- **Rotation Rules**:
  - Size Limit: Rolls logs to gzip files if they exceed `50MB`.
  - Max Files: Retains archived logs for `30 days`.
  - Disk Cap: Limits total archive size to `5GB`.
- **Structured Output**: production profile outputs logs in single-line JSON format:
```json
{"timestamp":"2026-07-12T10:30:00.123Z","level":"INFO","thread":"http-nio-8080-exec-1","logger":"com.printkeep.quota.core.proxy.service.PrinterProxyService","message":"Print job ALLOWED. Forwarding stream","context":"default","correlationId":"corr-123"}
```

---

## 2. Metrics & Observability

Monitor Prometheus metrics exposed at: `http://localhost:8080/actuator/prometheus`

### Critical Alerts Thresholds
- **Printer Failure Rate**: Alert if `proxy_printer_failures_total` increases by more than 5 in a 1-minute window.
- **Quota Rejections**: Monitor `proxy_jobs_rejected_total` to track abnormal user blockages.
- **Hikari Connections**: Alert if `hikaricp_connections_pending` is greater than 5 for over 30 seconds.

---

## 3. Scheduled Schedulers Maintenance

- **Manual AD sync trigger**: If users need immediate synchronization before the daily scheduled trigger runs:
```bash
curl -i -X POST http://localhost:8080/api/v1/admin/sync
```
- **Manual Quota Reset**: If quotas need resetting outside the first day of the month:
```bash
curl -i -X POST -H "Content-Type: application/json" \
     -d '{"defaultPages": 100}' \
     http://localhost:8080/api/v1/admin/quotas/reset
```
