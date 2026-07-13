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

Runtime packages:

- OpenJDK 21
- Apache HTTP Server
- curl
- vim
- iproute
- net-tools

The Dockerfile uses a build stage to compile and verify the Spring Boot application, then copies the runnable jar into the runtime image.

## Container

Expected container identity:

- Container name: `qa.carzonrent`
- Hostname: `qa.carzonrent`
- Public service: Apache on container port `80`
- Private backend: Spring Boot on `127.0.0.1:8085`

Recommended exact-domain run command:

```powershell
docker run -d --name qa.carzonrent --hostname qa.carzonrent --restart unless-stopped -p 80:80 carzonrent-qa:latest
```

Fallback non-privileged run command:

```powershell
docker run -d --name qa.carzonrent --hostname qa.carzonrent --restart unless-stopped -p 8081:80 carzonrent-qa:latest
```

The fallback is useful for local testing, but it does not satisfy `http://qa.carzonrent.com` because a URL without a port uses TCP port `80`.

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

## Spring Boot

The backend provides:

- `GET /`
- `GET /health`
- `GET /info`

The dashboard is rendered with Thymeleaf and reports environment, hostname, Java version, Spring Boot version, Apache status, reverse proxy status, backend address, deployment status, current time, build version, and container name.

## Why Backend Uses 127.0.0.1

Binding Spring Boot to `127.0.0.1:8085` ensures it can only be reached from inside the container. This keeps Apache as the single public control point for routing, headers, timeouts, and hardening.

## Why Apache Is Exposed

Apache is the externally exposed service because it provides a stable enterprise entry point, reverse proxy behavior, standard HTTP operational controls, and a clean boundary between public traffic and the Java application.

## Request Lifecycle

1. The browser requests `http://qa.carzonrent.com`.
2. Windows resolves the hostname through the hosts file or DNS.
3. Docker forwards host port `80` to Apache port `80` inside the container.
4. Apache forwards the request to `http://127.0.0.1:8085`.
5. Spring Boot returns the response to Apache.
6. Apache returns the response to the browser.

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

## Troubleshooting

If `http://qa.carzonrent.com` does not load:

- Check hosts/DNS: `Resolve-DnsName qa.carzonrent.com`
- Check port mapping: `docker port qa.carzonrent 80/tcp`
- Check container health: `docker inspect -f '{{.State.Health.Status}}' qa.carzonrent`
- Check Apache syntax: `docker exec qa.carzonrent httpd -t`
- Check listeners: `docker exec qa.carzonrent ss -lntp`
- Check backend internally: `docker exec qa.carzonrent curl http://127.0.0.1:8085/health`

Observed on 2026-07-13: the Windows hosts file contains duplicate entries mapping `qa.carzonrent.com` to `192.168.2.67`. Duplicate identical entries are noisy but not functionally harmful. Editing the hosts file is a host-level change and requires explicit confirmation.
