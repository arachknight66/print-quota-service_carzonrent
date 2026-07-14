# Carzonrent QA Deployment

This project packages a production-style QA deployment for `http://qa.carzonrent.com`.

## Architecture

The container runs CentOS Stream 9 with Apache HTTP Server as the public entry point and a Spring Boot 3.3.x application bound only to loopback.

```text
Browser
  |
  | http://qa.carzonrent.com:80
  v
Docker host port 80
  |
  v
Apache HTTP Server inside qa.carzonrent
  |
  | ProxyPass / http://127.0.0.1:8085/
  v
Spring Boot QA dashboard on 127.0.0.1:8085
```

Apache is the only externally exposed service. The backend port `8085` must never be published with Docker.

## Docker Image

The image is built from `quay.io/centos/centos:stream9`.

CentOS Stream 9 was chosen because it matches a common enterprise Linux family, has current Apache HTTP Server and OpenJDK 21 packages, and keeps the QA environment close to Red Hat-style production operations without adding Kubernetes or a multi-container platform in Phase 1.

Docker is used to make the QA runtime reproducible. The image defines the operating system, packages, Apache configuration, Java runtime, application artifact, startup behavior, and health checks in one buildable unit.

An image is the immutable template. A container is a running instance of that image with its own process tree, hostname, network namespace, health status, and port mappings.

## Image Lifecycle

The Dockerfile uses a multi-stage lifecycle:

1. The build stage installs Maven and the Java 21 JDK, resolves dependencies, runs tests, generates Spring Boot build metadata, and creates the executable jar.
2. The runtime stage starts from a clean CentOS Stream 9 image and installs only runtime software.
3. The built jar is copied from the build stage into `/opt/carzonrent/runtime/qa-dashboard.jar`.
4. Package caches and default Apache welcome/autoindex/SSL sample files are removed.

Docker layers are ordered for cache efficiency: the POM is copied before source code so dependency resolution can be reused when only Java or template files change. BuildKit cache mounts are used for Maven’s `/root/.m2` cache during image builds.

Runtime packages:

- OpenJDK 21
- Apache HTTP Server
- curl

The Dockerfile uses a build stage to compile and verify the Spring Boot application, then copies the runnable jar into the runtime image.

The runtime uses `java-21-openjdk-headless` instead of a full desktop-capable Java package. That reduces image size and attack surface while preserving everything needed to run Spring Boot.

## Container

Expected container identity:

- Container name: `qa.carzonrent`
- Hostname: `qa.carzonrent`
- Public service: Apache on container port `8080`
- Private backend: Spring Boot on `127.0.0.1:8085`

Recommended exact-domain run command:

```powershell
docker run -d --name qa.carzonrent --hostname qa.carzonrent --restart unless-stopped -p 80:8080 carzonrent-qa:latest
```

Fallback non-privileged run command:

```powershell
docker run -d --name qa.carzonrent --hostname qa.carzonrent --restart unless-stopped -p 8081:8080 carzonrent-qa:latest
```

The fallback is useful for local testing, but it does not satisfy `http://qa.carzonrent.com` because a URL without a port uses TCP port `80`.

## ENTRYPOINT, CMD, and PID 1

`ENTRYPOINT` is `/usr/local/bin/docker-entrypoint.sh`. It prepares and supervises the runtime.

`CMD` is `/usr/sbin/httpd -DFOREGROUND`. It remains overridable while keeping Apache as the foreground web server command.

PID 1 inside a container must handle signals and reap child processes. The entrypoint now starts Spring Boot as the `apache` user, waits for it to become healthy, validates Apache with `httpd -t`, starts Apache in foreground mode, traps `SIGTERM`, and shuts both managed processes down. This avoids orphaned child processes and reduces zombie risk without adding a heavier supervisor.

## Apache Reverse Proxy

Apache is configured with:

```apache
SSLProxyEngine On
Timeout 2400
ProxyTimeout 2400
ProxyBadHeader Ignore
ProxyPass / http://127.0.0.1:8085/
ProxyPassReverse / http://127.0.0.1:8085/
```

Apache terminates the public HTTP request and forwards it to the private backend over container loopback.

Apache exists in Phase 1 as the enterprise HTTP edge inside the container. It is the only public web server, owns port `80`, loads the reverse proxy modules, applies HTTP headers, and provides the operational boundary between browser traffic and the Java process.

A reverse proxy receives a client request and forwards it to a backend service on behalf of that client. The browser talks only to Apache; Spring Boot is hidden behind Apache on `127.0.0.1:8085`.

`ProxyPass / http://127.0.0.1:8085/` maps every incoming Apache path to the private Spring Boot backend. `ProxyPassReverse / http://127.0.0.1:8085/` rewrites backend redirect headers so clients continue to see the Apache-facing URL instead of the private backend address.

Apache logs go to `/proc/self/fd/1` and `/proc/self/fd/2`, so access and error logs are visible through `docker logs`. KeepAlive is enabled with bounded request and timeout values. Security headers are applied without changing the required reverse proxy directives.

## Spring Boot

The backend provides:

- `GET /`
- `GET /health`
- `GET /info`

The dashboard is rendered with Thymeleaf and reports environment, hostname, Java version, Spring Boot version, Apache status, reverse proxy status, backend address, deployment status, current time, build version, and container name.

Spring Boot configuration is environment-driven:

- `SERVER_ADDRESS`, default `127.0.0.1`
- `SERVER_PORT`, default `8085`
- `SPRING_PROFILES_ACTIVE`, default `qa`
- `SPRING_APPLICATION_NAME`, default `qa-dashboard`
- `CARZONRENT_ENVIRONMENT`, default `QA`
- `BUILD_VERSION`, default `2.0.0`
- `LOG_LEVEL_ROOT`, default `INFO`
- `LOG_LEVEL_APP`, default `INFO`

`GET /health` is served by Spring Boot Actuator. `GET /info` remains the enterprise deployment information endpoint used by the dashboard and verification suite.

## Why Backend Uses 127.0.0.1

Binding Spring Boot to `127.0.0.1:8085` ensures it can only be reached from inside the container. This keeps Apache as the single public control point for routing, headers, timeouts, and hardening.

## Why Apache Is Exposed

Apache is the externally exposed service because it provides a stable enterprise entry point, reverse proxy behavior, standard HTTP operational controls, and a clean boundary between public traffic and the Java application.

Apache listens on container port `8080` so the image can run rootless. Docker publishes host port `80` to container port `8080` so HTTP clients can still use `http://qa.carzonrent.com` without a port suffix. During local fallback verification, host port `8081` can be mapped to container port `8080`.

## Request Lifecycle

1. The browser requests `http://qa.carzonrent.com`.
2. Windows resolves the hostname through the hosts file or DNS.
3. Docker forwards host port `80` to Apache port `80` inside the container.
4. Apache forwards the request to `http://127.0.0.1:8085`.
5. Spring Boot returns the response to Apache.
6. Apache returns the response to the browser.

## Docker Networking

The container has an isolated network namespace. Spring Boot binds to container loopback only, so it is reachable by Apache inside the same container but not published on the Docker host. Docker port publishing maps a host port to a container port; the verified fallback local mapping is `8081:8080`, which exposes only Apache.

Bridge networking gives the container its own virtual interface. Docker forwards packets from the published host port to Apache inside the container. Apache then opens a separate loopback connection to Spring Boot on `127.0.0.1:8085`. Container loopback is private to that container, so the Docker host cannot reach the backend unless port `8085` is explicitly published, which this deployment does not do.

To make `http://qa.carzonrent.com` work without a port suffix, two host-level actions are normally required: map the hostname to the local Docker host in the Windows hosts file or DNS, and publish host port `80` to container port `80`. Both require explicit confirmation.

## Health Checks

The Docker `HEALTHCHECK` calls `http://127.0.0.1/health` through Apache, calls Spring Boot Actuator directly on the private backend port, and calls `/info` through Apache. The container only becomes healthy when Apache, Spring Boot, the reverse proxy path, and the application endpoints are functioning.

## Verification

Run the verification suite:

```powershell
.\verify.ps1 -RequirePublicUrl
```

For fallback testing on `8081`:

```powershell
.\verify.ps1 -FunctionalUrl http://127.0.0.1:8081 -ExpectedHostPort 8081
```

The suite checks Docker state, health, Apache syntax and modules, reverse proxy behavior, backend isolation, HTTP endpoints, CSS, 404 handling, Java runtime, and public URL reachability.

## CI/CD

Phase 3 adds an enterprise Jenkins pipeline in the repository root `Jenkinsfile`. The pipeline performs checkout, environment validation, compile, unit tests, integration-test phase, static analysis, packaging, Docker image build, QA deployment, and post-deployment verification.

Deployment automation is provided by `deploy-qa.ps1`. It replaces the QA container, waits for health, runs the verification suite, and restores the previous container if deployment fails. Full CI/CD details are documented in `CI_CD.md`.

## Operations and Hardening

Phase 4 adds production-style QA hardening: readiness and liveness health groups, Prometheus-ready metrics, structured logs, correlation IDs, sanitized error handling, validated configuration, richer Apache logging, response hardening headers, and restart verification. Operational details are documented in `OPERATIONS.md`.

## Kubernetes Readiness

Phase 5 adds Kubernetes-ready enterprise deployment assets without changing the application architecture. The Helm chart under `helm/carzonrent-qa` creates namespace, deployment, service, ingress, config, secret, HPA, PDB, network policy, quota, and limit range resources. Plain `kubectl` manifests are available under `kubernetes`. Kubernetes deployment details are documented in `KUBERNETES.md`.

## Troubleshooting

If `http://qa.carzonrent.com` does not load:

- Check hosts/DNS: `Resolve-DnsName qa.carzonrent.com`
- Check port mapping: `docker port qa.carzonrent 80/tcp`
- Check container health: `docker inspect -f '{{.State.Health.Status}}' qa.carzonrent`
- Check Apache syntax: `docker exec qa.carzonrent httpd -t`
- Check listeners: `docker exec qa.carzonrent ss -lntp`
- Check backend internally: `docker exec qa.carzonrent curl http://127.0.0.1:8085/health`

Observed on 2026-07-13: the Windows hosts file contains duplicate entries mapping `qa.carzonrent.com` to `192.168.2.67`. Duplicate identical entries are noisy but not functionally harmful. Editing the hosts file is a host-level change and requires explicit confirmation.
