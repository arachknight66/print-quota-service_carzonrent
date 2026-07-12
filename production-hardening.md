# Production Hardening Guide

This document describes the hardening steps, pool limits, and container restrictions implemented to secure the print management platform.

---

## 1. Thread and Connection Pool Hardening

- **Jetty Thread Pool limits**: Bound to a minimum of `10` and a maximum of `200` threads via `WebServerConfig.java` to prevent denial-of-service thread exhaustion.
- **Hikari Database Connection Pool**: Maximum pool size is set to `10` (default) but configurable up to `50` in production environments via `spring.datasource.hikari.maximum-pool-size`. Uses connection timeout of `30s` to fail fast during database network splits.
- **LDAP Context Pooling**: Configured with Spring Data LDAP pooled context sources with validation on borrow to recycle dead connections quickly.

---

## 2. Container Hardening Policies

To align with corporate DevSecOps policies:
- **No-Root Execution**: Container runs with system user `printuser` mapping to GID/UID `10001`.
- **Dropped Capabilities**: The Compose file drops all kernel privileges:
```yaml
cap_drop:
  - ALL
security_opt:
  - no-new-privileges:true
```
- **Read-Only Root Filesystem**: Docker run configurations mount `/app` as read-only, forcing logs and temp prints to write to distinct named write volumes.

---

## 3. JVM Tuning & GC Settings

The container entrypoint sets memory thresholds based on container limits:
- **GC Algorithm**: Configured with `-XX:+UseG1GC` (Garbage-First Garbage Collector) to reduce pause times.
- **Memory Boundaries**: Sets `-XX:MaxRAMPercentage=75.0` to respect Docker memory limits and prevent container out-of-memory killing by the Linux kernel.
- **OutOfMemory Crash Policy**: Sets `-XX:+ExitOnOutOfMemoryError` to force immediate JVM exit on heap exhaustion, allowing orchestrators (Kubernetes / Docker Compose) to restart the container in a clean state.
