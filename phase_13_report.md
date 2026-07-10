# Phase 13 Implementation Report: Service Hardening, systemd, & CI/CD Pipelines
* **Status**: Completed
* **Date Completed**: 2026-07-10
* **Mentor Sign-off Status**: Pending Review

---

### 1. Deliverables Completed
* **[print-quota.service](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/deployment/print-quota.service)**: systemd service descriptor configuration.
* **[Jenkinsfile](file:///c:/Users/arach/Documents/Projects/Carzonrent_Project/Printkeep/Jenkinsfile)**: Automated pipeline script for Maven builds and SSH deployments.

---

### 2. Design & Technical Summary
* **Privileged Port Binding Security**:
  * The system must listen on standard SSL Port 443 for IPPS connections. On Linux/CentOS systems, binding to ports below 1024 typically requires root privileges.
  * Running web applications as root is a major security risk.
  * Resolved this by adding capability rules inside `print-quota.service`:
    ```ini
    AmbientCapabilities=CAP_NET_BIND_SERVICE
    CapabilityBoundingSet=CAP_NET_BIND_SERVICE
    NoNewPrivileges=true
    ```
  * This allows the Java service (running under a lightweight, unprivileged service account `printquota`) to bind directly to port 443.
* **CI/CD Pipeline Flow**:
  * Checks out source code.
  * Executes Maven package compilations and runs JUnit test suites.
  * Opens secure SSH connections via Jenkins credentials mapping tools.
  * Securely copies the executable jar and systemd descriptor onto the target CentOS VM and restarts the systemd process.

---

### 3. Verification & Test Metrics
* Service configuration conforms to standard systemd file configurations.
* Jenkinsfile syntax validated against declarative pipeline mapping guidelines.

---

### 4. Code Health & Maintainability
* Hardened service process controls by enabling `Restart=always` with a `RestartSec=10` backup interval. This ensures the system automatically restarts if it encounters critical errors like database drops or JVM crashes.
* Configured log collection patterns (`StandardOutput=syslog`) to send service outputs directly to host logs.
