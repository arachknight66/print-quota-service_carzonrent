# Carzonrent Enterprise QA Staging Infrastructure Documentation

This document describes the design decisions, architectural components, request lifecycles, and deployment procedures for the Phase 1 QA environment.

---

## 1. Architectural Decisions

### Why CentOS Stream 9?
- **Enterprise Alignment**: CentOS Stream 9 is built on the same foundation as Red Hat Enterprise Linux 9 (RHEL 9). Carzonrent's production servers utilize RHEL/CentOS-like systems; utilizing CentOS Stream 9 for staging guarantees high environmental parity (similar system libraries, package managers, network stacks, and security configurations).
- **Stability and Package Lifecycle**: It provides stable, enterprise-tested core libraries (glibc, systemd, openssl) suitable for hosting high-performance HTTP reverse proxies and Java runtime environments.

### Why Docker?
- **Isolation & Portability**: Docker containerizes the application and its server environment (Apache, Java, dependencies) into a single artifact, ensuring the environment is identical across developer workstations, QA hosts, and staging environments.
- **Resource Efficiency**: Instead of full virtualization (like virtual machines which duplicate guest OS kernels), Docker containers share the host OS kernel and run as isolated processes, starting up in seconds and using minimal memory.

### Image vs. Container
- **Docker Image**: A read-only template that contains the operating system, application files, packages, libraries, and configurations required to run. It represents the "executable package" (like a class in OOP).
- **Docker Container**: A runtime instance of a Docker image. It is an isolated, active execution space where the application runs (like an object instance of a class in OOP).

---

## 2. Server Architecture & Reverse Proxy Design

```
             [ User Browser ]
                     │
         http://qa.carzonrent.com
                     │ (Host Port 80)
                     ▼
        [ CentOS Stream 9 Container ]
                     │ (Container Port 80)
                     ▼
          [ Apache HTTP Server ]
                     │ (PID 1)
        ProxyPass & ProxyPassReverse
                     │
                     ▼
        [ Spring Boot App (Jetty) ]
            (Bound to 127.0.0.1:8085)
```

### Why Apache HTTP Server?
- **Production Parity**: Carzonrent utilizes Apache for SSL termination, request routing, header injection, security hardening, and serving static assets in production.
- **Security Hardening**: Apache operates as a resilient front-line gateway, handling slowloris attacks, enforcing request rate limits, removing debug headers, and screening traffic before it touches the Spring Boot application container.

### What is a Reverse Proxy?
- A reverse proxy is an intermediary gateway server that sits in front of backend servers. Clients connect to the proxy (Apache), which evaluates the request, forwards it to the appropriate backend server (Spring Boot), receives the response, and forwards it back to the client. The client never directly communicates with or knows the address of the backend.

### Why Spring Boot runs on 127.0.0.1
- **Security Isolation**: By binding Spring Boot exclusively to `127.0.0.1` (loopback interface), it cannot be reached from any network interface card (NIC) connected to the container. The host system and external networks cannot connect directly to port `8085`.
- **Enforced Pathing**: All traffic is forced to flow through the Apache reverse proxy. This prevents attackers or unauthorized clients from bypassing Apache's security configurations, filters, or logs.

### Why Apache listens on Port 80
- **Standard HTTP Port**: Port 80 is the default port for non-secure web requests. It allows QA users and developers to access the application by simply typing `http://qa.carzonrent.com` without appending a port number (e.g. `:8085`) to the URL.

---

## 3. Proxy Directives & Mechanics

### How ProxyPass Works
- `ProxyPass / http://127.0.0.1:8085/` maps incoming URLs to the backend server. When a client requests `http://qa.carzonrent.com/api/v1/status`, Apache intercepts it and issues a request to `http://127.0.0.1:8085/api/v1/status`.

### How ProxyPassReverse Works
- `ProxyPassReverse / http://127.0.0.1:8085/` dynamically rewrites the response headers (such as `Location`, `Content-Location`, and `URI`) returned by Spring Boot. If Spring Boot issues a redirect to `http://127.0.0.1:8085/login`, Apache rewrites this header to `http://qa.carzonrent.com/login` before sending it to the client. This prevents the backend's internal loopback URL from being exposed to the browser.

---

## 4. Request Lifecycle: End-to-End

1. **DNS Resolution**: The user enters `http://qa.carzonrent.com/` in the browser. The browser checks the local `hosts` file, resolving `qa.carzonrent.com` to `127.0.0.1`.
2. **TCP Connection**: The browser opens a TCP connection on port `80` to the host.
3. **Docker Port Mapping**: The Docker engine receives the packet on host port `80` and routes it into the virtual bridge network interface mapped to container `qa.carzonrent` on port `80`.
4. **Apache Ingress**: Inside the container, the Apache HTTP Server listening on port `80` receives the HTTP request.
5. **Proxy Execution**: Apache evaluates the `ProxyPass` rules and forwards the request over the loopback interface (`127.0.0.1:8085`) to the Spring Boot instance. It appends proxy headers (`X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto`).
6. **Spring Boot (Jetty)**: The embedded Jetty server accepts the connection from `127.0.0.1`. The Spring dispatcher servlet maps the request `/` to `DashboardController.showDashboard()`.
7. **Controller Processing**: The controller runs, detects the proxy headers, compiles the dynamic template model, and renders the `dashboard.html` view using Thymeleaf.
8. **Egress Response**: 
   - Spring Boot returns the rendered HTML payload (HTTP 200) to Apache.
   - Apache intercepts the response, applies `ProxyPassReverse` rewriting, and streams the HTML back to the Docker interface.
   - Docker forwards the packet to the host NIC.
   - The user browser receives the HTML and renders the corporate dashboard.

---

## 5. Docker Networking, Health, and Verification

### Docker Networking & Port Mapping
- The container runs on Docker's default `bridge` driver.
- A host port mapping `-p 80:80` maps port 80 of the host machine to port 80 inside the container.
- Spring Boot is bound inside the container to `127.0.0.1:8085`. Because it binds only to the loopback interface *inside* the container, it cannot be reached via the container's bridge IP or the host network, maintaining complete backend isolation.

### Health Checks
- The Dockerfile contains:
  ```dockerfile
  HEALTHCHECK --interval=10s --timeout=5s --retries=3 CMD curl -f http://127.0.0.1/health || exit 1
  ```
- This healthcheck queries Apache on port 80. Since Apache proxying is active, Apache forwards the request to `/health` on Spring Boot. 
- If Apache is down, curl fails (exit code 7).
- If Spring Boot is down, Apache returns HTTP 503 (exit code 22 under curl `-f`).
- Therefore, the container only reports `healthy` when **both** services are running and connected.

### Verification Process
A PowerShell test suite `verify-deployment.ps1` verifies the following:
1. Container runtime state.
2. Container health status.
3. Apache configuration syntax.
4. Active Apache proxy modules.
5. External backend isolation (confirming host cannot query port 8085 directly).
6. HTTP response status codes, header parsing, static asset availability, and correct 404 response routing.
