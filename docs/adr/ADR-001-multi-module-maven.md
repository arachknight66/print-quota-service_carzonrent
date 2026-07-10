# ADR-001: Multi-Module Maven Project Structure

## Context
The project needs to isolate binary protocol decoding logic from application framework settings. Sharing dependencies or embedding codec parsing directly into Spring Boot would increase classpath bloat and make code testing harder.

## Decision
We implement a multi-module Maven structure:
1. **`print-quota-parent` (Root)**: Manages plugin declarations, compiler versions, static analysis configurations (SpotBugs, Checkstyle, PMD), and dependencies.
2. **`ipp-codec`**: Sub-module for low-level IPP parsing. Contains zero framework dependencies (only Lombok and JUnit).
3. **`print-quota-core`**: Sub-module containing the Spring Boot web middleware, database repositories, Active Directory integrations, and processing engines.

## Consequences
- **Pros**: Clean isolation of protocol logic. Fast local compilation. Codecs can be reused in future standalone proxies.
- **Cons**: Requires building multiple artifacts.
