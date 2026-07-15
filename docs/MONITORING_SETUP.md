# Monitoring Setup Guide

**Audience**: IT/DevOps administrators responsible for operating the Print Quota Management System.  
**System**: Print Quota Management System — `print-quota-core`  
**Last Updated**: 2026-07

---

## Overview

The Print Quota Management System exposes metrics via Spring Boot Actuator and Micrometer's Prometheus registry. Metrics are scraped by a Prometheus instance and visualized in Grafana.

```
  [printkeep service]
        |
        |  GET /actuator/prometheus  (scrape interval: 15s)
        v
  [Prometheus server]
        |
        |  PromQL queries
        v
  [Grafana dashboard]
        |
        v
  [Alerts → IT team email/Slack]
```

---

## 1. Actuator Configuration (Already in application.yml)

The following is already configured in `print-quota-core/src/main/resources/application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
```

The Prometheus endpoint is exposed at: `http://localhost:8080/actuator/prometheus`

> **IMPORTANT** — The `/actuator/prometheus` endpoint is restricted to the `PRINTKEEP_ADMIN` role in production (see `SecurityConfig`). Configure Prometheus to use HTTP Basic Auth credentials for its scrape target.

---

## 2. Prometheus Scrape Configuration

Add the following job to your Prometheus `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'printkeep'
    scrape_interval: 15s
    static_configs:
      - targets: ['<PRINTKEEP_HOST>:8080']
    metrics_path: /actuator/prometheus
    basic_auth:
      username: '${PROMETHEUS_SCRAPE_USER}'
      password: '${PROMETHEUS_SCRAPE_PASSWORD}'
```

Replace `<PRINTKEEP_HOST>` with the CentOS VM's hostname or IP (e.g. `192.168.10.50`).

Reload Prometheus after editing:
```bash
curl -X POST http://localhost:9090/-/reload
```

---

## 3. Key Metrics Reference

### Print Pipeline

| Metric | Type | Description |
|---|---|---|
| `print_requests_received_total` | Counter | Total IPP requests received |
| `print_requests_processed_total{decision="ALLOW"}` | Counter | Requests allowed |
| `print_requests_processed_total{decision="REJECT_*"}` | Counter | Requests rejected (by decision type) |
| `print_requests_invalid_total` | Counter | Requests with malformed IPP packets |
| `print_requests_identity_failures_total` | Counter | CN mismatch / unknown user rejections |
| `print_requests_quota_failures_total` | Counter | Insufficient quota rejections |
| `print_evaluation_latency_seconds` | Timer | Per-request pipeline evaluation time |

### Proxy Forwarding

| Metric | Type | Description |
|---|---|---|
| `proxy_requests_proxied_total` | Counter | Total jobs forwarded to printers |
| `proxy_printer_failures_total` | Counter | Printer connectivity / server errors |
| `proxy_jobs_rejected_total` | Counter | Jobs rejected before forwarding |
| `proxy_forwarding_latency_seconds{printer="*"}` | Timer | Per-printer forwarding time |
| `quota_refunds_total` | Counter | **Automatic quota refunds** after printer failure |

### Health

| Metric | Endpoint | Description |
|---|---|---|
| Liveness | `/actuator/health/liveness` | Returns UP when the JVM is alive |
| Readiness | `/actuator/health/readiness` | Returns UP when all datasources are reachable |
| LDAP health | embedded in `/actuator/health` | Shows `ldap: UP` when AD is reachable |

---

## 4. Grafana Dashboard Import

A pre-built Grafana dashboard is provided at `testing/grafana-dashboard.json`.

**Import steps**:

1. Open Grafana → **Dashboards** → **+ Import**
2. Click **Upload JSON file** and select `testing/grafana-dashboard.json`
3. Set the data source to your Prometheus instance
4. Click **Import**

The dashboard includes:

| Panel | PromQL | Description |
|---|---|---|
| Print Requests (1m rate) | `rate(print_requests_received_total[1m])` | Requests per second arriving |
| Allow vs Reject (1m rate) | `rate(print_requests_processed_total[1m])` by `decision` | Decision breakdown |
| Pipeline Latency (P95) | `histogram_quantile(0.95, rate(print_evaluation_latency_seconds_bucket[5m]))` | 95th-percentile latency |
| Proxy Forwarding Failures | `rate(proxy_printer_failures_total[1m])` | Printer connectivity failures |
| **Quota Refunds (1m rate)** | `rate(quota_refunds_total[1m])` | **Automatic refunds after printer failure** |

---

## 5. Recommended Alerts

Configure these alerts in Grafana Alert Rules or in the Prometheus `rules.yml`:

```yaml
groups:
  - name: printkeep_alerts
    rules:
      # Alert if printer failures spike (> 5/min sustained for 2 minutes)
      - alert: PrinterForwardingFailureSpike
        expr: rate(proxy_printer_failures_total[1m]) > 0.083
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "PrintKeep: printer forwarding failures are spiking"
          description: "More than 5 forwarding failures per minute sustained for 2+ minutes."

      # Alert if quota refunds are occurring (any refund = something is failing)
      - alert: QuotaRefundsOccurring
        expr: rate(quota_refunds_total[1m]) > 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "PrintKeep: automatic quota refunds are being issued"
          description: "Quota refunds indicate that accepted print jobs are failing at the printer level."

      # Alert if the identity failure rate exceeds threshold (possible mTLS attack or misconfiguration)
      - alert: HighIdentityFailureRate
        expr: rate(print_requests_identity_failures_total[5m]) > 0.2
        for: 3m
        labels:
          severity: critical
        annotations:
          summary: "PrintKeep: high rate of identity/mTLS failures"
          description: "Possible certificate misconfiguration or unauthorized access attempts."

      # Alert if the quota system goes down (no metrics received for 5 minutes)
      - alert: PrintQuotaSystemDown
        expr: absent(print_requests_received_total)
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "PrintKeep: system appears to be down"
          description: "No print_requests_received_total metric received in 5 minutes."
```

---

## 6. Log-Based Monitoring (Complementary)

Key log patterns that security operations should monitor in the application log (`/var/log/printkeep/app.log`):

| Pattern | Severity | Meaning |
|---|---|---|
| `Client certificate identity mismatch` | WARN | CN ≠ requesting-user-name (spoofing attempt or bad cert) |
| `Admin API access denied. sourceIp=` | WARN | Admin endpoint accessed from an unapproved network |
| `Quota refund issued for` | INFO | A job was accepted but the printer failed — refund triggered |
| `Error forwarding print stream` | ERROR | Printer-level connectivity failure |
| `Failed to process automatic quota refund` | ERROR | Refund transaction itself failed — requires manual intervention |

Configure your log aggregation tool (ELK, Loki, etc.) to alert on the WARN/ERROR patterns above.

---

## 7. Adding New Panels

To add a new panel to the Grafana dashboard:

1. Open `testing/grafana-dashboard.json`
2. Add a new panel object to the `"panels"` array following the existing pattern
3. Assign a unique `"id"` integer (currently max is 5 — use 6 for the next)
4. Set `"gridPos"` (x, y, w, h) to position the panel
5. Re-import in Grafana (or use the Grafana API to update)
