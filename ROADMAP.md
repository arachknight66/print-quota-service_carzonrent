# Project Status & Roadmap

This document outlines the current project maturity, completed phases, identified technical debt, and next milestones.

---

## 1. Completed Milestones

### Phase 1: Enterprise Foundation
- Established multi-module Maven design.
- Enforced PMD, SpotBugs, Checkstyle compiler validations.
- Configured Jetty server thread pool limits.

### Phase 2: Persistence Layer
- Mapped JPA entities (`User`, `Quota`, `PrintLog`) with audits.
- Implemented database migrations via Liquibase.
- Setup optimistic and pessimistic locks.

### Phase 3: Identity Integration
- Built LDAP/Active Directory synchronization schedulers.
- Configured Caffeine cache lookup buffers.

### Phase 4: Docker & IPP Codec
- Multi-stage CentOS Stream 9 container compilation.
- Standalone `ipp-codec` binary parser and encoder.

### Phase 5: Print Processing Engine
- Built Chain of Responsibility evaluation stages.
- Implemented transactional quota deductions and metrics logging.

---

## 2. Next Milestones & Phase 6 Blueprint

### Phase 6: Print Job Routing & Proxy (Planned)
- Implement a spooler interceptor proxy.
- Allow the application to forward jobs to the physical printers:
  - If a print job is **Accepted (ALLOW)**, decode the destination URI, build the IPP output packet, and route it to the target printer over standard print channels.
  - If **Rejected (REJECT)**, cancel the job, delete print spooler files, and send an IPP status failure packet back to the client.

---

## 3. Technical Debt & Known Limitations

- **AD Sync Lockups**: LDAP directory listings use paged search results. Large user sets (10,000+) can block execution threads if the connection pool is saturated.
- **Transactional Latency**: Under extremely high concurrent print volume (100+ requests/sec for the same user), the pessimistic write lock on the `quotas` table row will serialize requests, causing short wait latencies.
- **Single Point of Failure**: Active Directory connectivity checks are monitored in Actuator readiness groups. If AD drops, the readiness probe fails, marking the container offline even though local cached users can still print. A cached verification fallback is planned.
