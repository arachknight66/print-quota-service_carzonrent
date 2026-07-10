# Print Quota Management System

An enterprise-grade, high-performance middleware service designed to manage user print quotas, authenticate active directory sessions, and audit print traffic across corporate printer networks.

This repository contains the complete implementation of Phases 1 to 5. Phase 6 (Printer Proxy forwarding) is planned.

---

## 1. Project Implementation Status

- **Phase 1: Enterprise Foundation** — Completed. Configured Maven multi-module structure, thread pool limits, and static analysis gates.
- **Phase 2: Persistence Layer** — Completed. JPA entity mapping, Liquibase migrations, and optimistic/pessimistic locking.
- **Phase 3: Identity Integration** — Completed. Spring LDAP connection to Active Directory, batch synchronization, and caching.
- **Phase 4: Docker Runtime & IPP Codec** — Completed. Multi-stage CentOS Stream 9 containerization and low-level IPP (RFC 8010/8011) codec.
- **Phase 5: Print Processing Engine** — Completed. Chain of Responsibility validation, quota deduction under pessimistic locks, and transactional audits.
- **Phase 6: Print Job Routing & Proxy** — **Planned / Future Work**.

---

## 2. Directory Structure

```
print-quota-parent/ (Root)
├── docker/
│   ├── app/
│   │   ├── Dockerfile       (Production multi-stage CentOS Stream 9 runtime)
│   │   └── Dockerfile.dev   (Development container with host workspace mounting)
│   └── ldap/
│       └── bootstrap.ldif   (Local directory bootstrap mock database)
├── ipp-codec/               (Low-level binary IPP parser/encoder library module)
└── print-quota-core/        (Main Spring Boot middleware core module)
    ├── src/main/java/com/printkeep/quota/core/
    │   ├── config/          (Thread-pools, Cache, Actuator, and JSON Logger settings)
    │   ├── controller/      (Actuator REST endpoints and Exception handlers)
    │   ├── filter/          (MdcCorrelationFilter tracing HTTP requests)
    │   ├── identity/        (LDAP directory synchronization and caching service)
    │   ├── model/           (User, Quota, PrintLog JPA entities)
    │   ├── processing/      (PrintProcessingPipeline & quota deduction engine)
    │   └── repository/      (JPA PostgreSQL repositories)
    └── src/main/resources/
        ├── db/changelog/    (Liquibase database schema migration files)
        └── application.yml  (Configuration binder)
```

---

## 3. Technology Stack

- **Runtime**: Java 21 (Headless OpenJDK), Spring Boot 3.3.0
- **Database**: PostgreSQL 16
- **Migrations**: Liquibase
- **Directory Services**: Active Directory / Spring LDAP
- **Logging**: Logback with MDC Correlation trace binding
- **Metrics**: Micrometer / Actuator / Prometheus
- **Containerization**: Docker / Docker Compose (CentOS Stream 9 base)

---

## 4. Build Instructions

The build features static analysis enforcement (Checkstyle, SpotBugs, PMD) and JaCoCo code coverage requirements (minimum 80% coverage).

```bash
# Execute compiling, static checks, and unit tests
./mvnw clean verify

# Skip coverage checks and testing when packaging
./mvnw clean package -Djacoco.skip=true -DskipTests
```

---

## 5. Deployment and Runtime Guide

### A. Run Database & LDAP placeholders
Run Docker Compose in the root directory to spin up PostgreSQL, LDAP mock server, and pgAdmin:
```bash
docker compose up -d
```

### B. Run the Application Container
Build the CentOS Stream 9 production-grade image:
```bash
docker compose build
docker compose up print-quota-app -d
```

### C. Run Locally (Dev profile)
```bash
./mvnw spring-boot:run -pl print-quota-core -Dspring-boot.run.profiles=dev
```

---

## 6. Project Documentation Index

Detailed architectural mappings and system administration operations guides are available in the repository root:

1. [Architecture Blueprint](architecture.md) — Multi-module dependency layout, layered structure, data flows.
2. [System Design Document](system-design.md) — Functional constraints, pattern templates, caching, and concurrency rules.
3. [Database Documentation](database.md) — ER diagrams, schema definition, Liquibase migrations catalog, locking decisions.
4. [API Catalog](api.md) — Actuator monitoring, health checks, JSON logging specifications.
5. [Print Processing Logic](processing_documentation.md) — Pipeline stages, calculations, and multipliers.
6. [Containerization & Docker Setup](docker.md) — Deployment layers, image security, Compose service networks.
7. [Configuration Blueprint](configuration.md) — Global parameters, LDAP values, profiles.
8. [Security & Threat Model](security.md) — Session tracking, directory permissions, TLS details.
9. [Testing Matrix](testing.md) — Coverage specs, Testcontainers PostgreSQL setups, LDAP mocks.
10. [Operations Guide](operations.md) — Maintenance procedures, logs locations, monitoring counters, backup strategies.
11. [Project Roadmap](ROADMAP.md) — Completed milestones, Phase 6 goals, architectural debt mapping.
