# Testing Blueprint & Matrix

This document outlines the testing levels, mock architectures, database testcontainers, and build validation commands.

---

## 1. Testing Framework Levels

1. **Unit Tests**:
   - Standalone logic testing with JUnit 5 and AssertJ.
   - Core parser/encoder check (`IppCodecTests.java`) testing bit conversions.
   - Metadata extraction (`IppMetadataExtractorTests.java`) checking rounding and multipliers.
   - MDC logging trace validation (`MdcCorrelationFilterTests.java`).
2. **Integration Tests**:
   - Spring Boot Test context checks.
   - Sliced controller layer tests (`LivenessControllerTests.java`).
3. **Database Concurrency Integration Tests**:
   - `QuotaConcurrencyTests.java` verifying pessimistic locks.
   - Spin up a real PostgreSQL 16 container via **Testcontainers** dynamically.
4. **Embedded LDAP Testing**:
   - Configures an in-memory **UnboundID Directory Server** mapping custom schema properties to mock Active Directory.

---

## 2. Test execution Command Guide

```bash
# Execute the complete testing suite (Runs Unit + Integration tests)
./mvnw clean test "-Dnet.bytebuddy.experimental=true"

# Run tests for a specific module
./mvnw test -pl print-quota-core "-Dnet.bytebuddy.experimental=true"

# Disable JaCoCo coverage rules checking
./mvnw clean test "-Djacoco.skip=true" "-Dnet.bytebuddy.experimental=true"
```
*Note: Java 26 inline mocking via ByteBuddy requires setting JVM property `"-Dnet.bytebuddy.experimental=true"` to prevent JVM compilation warnings.*

---

## 3. Mock Environments

### A. Testcontainers Integration Setup
`AbstractIntegrationTest` starts a static database instance:
- Image: `postgres:16-alpine`
- Startup: Instantiated before class loading, sharing a single container across all repository tests to minimize context startup time.
- Fallback: Uses `DockerCondition` extension to skip executing test classes if no Docker daemon is running on the host system.

### B. In-Memory LDAP Integration Setup
`LdapTestConfiguration` initializes:
- Embedded server: `InMemoryDirectoryServer`
- Port: Randomly assigned available system port.
- User schema: Prefills mock users matching domain accounts (`jdoe`, `asmith`, and disabled users).
