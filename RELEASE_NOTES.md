# Version 1.0 Release Notes (General Availability)

This document contains the release notes, changelog, migration steps, and known limitations for Version 1.0 GA of the Print Quota Management System.

---

## 1. Executive Summary

Version 1.0 GA establishes a production-grade Print Quota Management System and IPP Proxy. The system secures print routing, synchronizes directory structures, calculates monthly allocations under strict concurrency controls, and provides dashboards and Excel exports.

---

## 2. Changelog & Milestones

- **Phase 1-3 (Foundation & Identity)**: Built core Spring Boot configuration, configured PostgreSQL schema migrations via Liquibase, and added Active Directory LDAP synchronization.
- **Phase 4-5 (Codec & Engines)**: Added low-level RFC 8010 binary parsing module (`ipp-codec`) and validation pipelines.
- **Phase 6 (IPP Proxy)**: Introduced transparent streaming IPP proxy, connection pooling, and target printer routing.
- **Phase 7 (Reporting & Admin)**: Integrated SXSSFWorkbook streaming Excel exporters, daily mail alerts, and versioned admin REST APIs.
- **Phase 8 (Production Hardening)**: Hardened container privileges, added CycloneDX SBOM configurations, and wrote PowerShell backup scripts.

---

## 3. Migration Guide

To upgrade from development/beta setups to V1.0 GA:
1. **Apply Liquibase Schema Updates**: Migrations execute automatically during container start. Alternatively, run:
```bash
./mvnw liquibase:update -pl print-quota-core
```
2. **Import SSL Certificates**: Place active TLS certificate files in `./docker/certs` to enable secure print proxy communications.
3. **Seed Database Default Quotas**: Run a manual sync to pull current employees and seed default quotas:
```bash
curl -X POST http://localhost:8080/api/v1/admin/sync
```

---

## 4. Known Issues & Future Roadmap

- **Known Issues**:
  - Direct print job cancel operations are validated but forwarding cancellation streams are not optimized yet.
- **Future Roadmap (v2.0)**:
  - Centralized distributed lock management using Redis or Hazelcast.
  - Multi-node clustering configurations.
