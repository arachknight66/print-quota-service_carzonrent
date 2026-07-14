# Phase 4 Operations and Hardening Guide

Phase 4 prepares the existing QA Docker deployment for production-style operations without changing the architecture.

## Architecture

```text
Browser
  |
  v
Docker host port 8081 or approved host port 80
  |
  v
Apache HTTP Server :80
  |
  | ProxyPass / http://127.0.0.1:8085/
  v
Spring Boot 127.0.0.1:8085
```

The backend remains private. Apache remains the only public entry point.

## Observability Architecture

```text
Apache access/error logs -> stdout/stderr -> docker logs
Spring Boot structured logs -> stdout -> docker logs
Actuator health/readiness/liveness -> Apache reverse proxy
Actuator metrics -> /metrics
Prometheus scrape format -> /prometheus
Build/git/runtime metadata -> /info and /actuator/info
```

No Prometheus or Grafana server is installed in Phase 4. The application is ready for those systems to scrape later.

## Spring Boot Operations

Spring Boot now provides:

- `/health`
- `/health/readiness`
- `/health/liveness`
- `/metrics`
- `/prometheus`
- `/info`
- `/actuator/info`

Readiness indicates the app is ready to serve traffic. Liveness indicates the JVM process should remain running. Docker health uses readiness, liveness, and `/info`.

## Structured Logging

Spring Boot logs are emitted as JSON-style lines with:

- timestamp
- level
- thread
- logger
- correlationId
- message

Request logging records method, path, status, duration, and remote address.

Apache logs include:

- request line
- status
- byte count
- request duration
- correlation ID
- forwarded-for
- user agent

All logs go to stdout/stderr for Docker compatibility.

## Correlation IDs

Every request receives an `X-Correlation-ID` response header. If a client supplies one, the app preserves it. Otherwise, the app generates a UUID. The value is added to the logging MDC and appears in application logs. Apache also logs the incoming correlation header.

## Security Review

Implemented controls:

- `ServerTokens Prod`
- `ServerSignature Off`
- `TraceEnable Off`
- `FileETag None`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: SAMEORIGIN`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy` denying camera, microphone, and geolocation
- `X-Permitted-Cross-Domain-Policies: none`
- `X-Download-Options: noopen`
- No Docker publication for backend port `8085`
- Spring Boot binds to `127.0.0.1`
- Runtime artifact owned by `apache`
- Spring Boot process runs as `apache`
- Management endpoints expose health, metrics, and Prometheus readiness only
- Error responses are sanitized and include correlation IDs
- Configuration validation enforces loopback backend address

Secrets are not stored in the image or source. Future secret-bearing configuration should be passed as environment variables or a secret manager integration in a later phase.

## Reliability

Reliability controls:

- Graceful Spring Boot shutdown
- Entrypoint traps shutdown signals
- Apache configuration validation before Apache starts
- Docker `HEALTHCHECK` requires readiness, liveness, and `/info`
- Deployment script preserves the previous container for rollback
- Verification checks restart behavior and container health after restart

## Operational Monitoring

Recommended later integrations:

- Prometheus scrape target: `http://qa.carzonrent.com/prometheus`
- Dashboard panels for JVM memory, CPU, thread count, HTTP status rates, request latency, process uptime, and Docker health
- Alert on readiness failure, liveness failure, repeated 5xx responses, high latency, and restart loops

These are prepared but not installed in Phase 4.
