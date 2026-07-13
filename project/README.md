# Carzonrent Spring Boot QA Environment

Production-style local QA gateway for Carzonrent, built with CentOS Stream 9, OpenJDK 21, Spring Boot 3.3.x, Maven, Apache HTTP Server, and Docker. The public endpoint is [http://localhost:8081](http://localhost:8081).

Apache is the only externally reachable web server. It never serves the dashboard from its document root; every request is reverse-proxied to Spring Boot on `127.0.0.1:8085` inside the container.

## Architecture

```text
Browser
        |
        v
localhost:8081
        |
        v
Docker Port Mapping
8081 -> 80
        |
        v
Apache HTTP Server
        |
Reverse Proxy
        |
        v
Spring Boot
127.0.0.1:8085
        |
Business Logic
        |
        v
HTTP Response
```

## Folder structure

```text
project/
|-- .dockerignore
|-- Dockerfile
|-- docker-entrypoint.sh
|-- verify.ps1
|-- README.md
|-- apache/
|   `-- qa.carzonrent.conf
`-- app/
    |-- pom.xml
    `-- src/main/
        |-- java/com/carzonrent/platform/qadashboard/
        |-- resources/application.yml
        |-- resources/templates/dashboard.html
        `-- resources/static/css/dashboard.css
```

## Build and run

Run from the `project` directory in PowerShell:

```powershell
docker build --pull --build-arg BUILD_VERSION=2.0.0 -t carzonrent-qa:2.0.0 -t carzonrent-qa:latest .

docker run -d `
  --name qa.carzonrent `
  --hostname qa.carzonrent `
  --restart unless-stopped `
  -p 8081:80 `
  carzonrent-qa:2.0.0
```

To rebuild an existing local deployment:

```powershell
docker rm -f qa.carzonrent
docker build --pull --build-arg BUILD_VERSION=2.0.1 -t carzonrent-qa:2.0.1 -t carzonrent-qa:latest .
docker run -d --name qa.carzonrent --hostname qa.carzonrent --restart unless-stopped -p 8081:80 carzonrent-qa:2.0.1
```

## Spring Boot application

The application name is `qa-dashboard`. Maven builds the executable JAR during image creation, and the entrypoint starts it before Apache.

Exposed routes:

- `GET /` renders the enterprise QA dashboard using Spring MVC and Thymeleaf.
- `GET /health` returns `{"status":"UP"}`.
- `GET /info` returns hostname, Java version, Spring Boot version, current time, operating system, and `environment=QA`.

`application.yml` binds Tomcat to `127.0.0.1:8085`, not `0.0.0.0`. Docker does not publish port 8085, so the backend is reachable only from inside the container.

## Apache reverse proxy

The Apache virtual host applies the requested enterprise proxy settings:

```apache
SSLProxyEngine On
Timeout 2400
ProxyTimeout 2400
ProxyBadHeader Ignore
ProxyPass / http://127.0.0.1:8085/
ProxyPassReverse / http://127.0.0.1:8085/
```

Additional choices:

- `ProxyRequests Off` prevents open-forward-proxy behavior.
- `ProxyPreserveHost On` keeps the QA host header visible to Spring Boot.
- `mod_ssl`, `mod_proxy`, `mod_proxy_http`, `mod_proxy_connect`, and `mod_headers` are loaded by the CentOS Apache module configuration.
- Packaged inbound `ssl.conf` is removed because this local QA gateway exposes HTTP port 80 only; `SSLProxyEngine` remains available for upstream proxy behavior.
- Apache access and error logs go to stdout/stderr for `docker logs`.

## Container startup

`docker-entrypoint.sh` starts Spring Boot from `/opt/carzonrent/runtime/qa-dashboard.jar`, waits for `http://127.0.0.1:8085/health`, and then executes:

```text
/usr/sbin/httpd -DFOREGROUND
```

Apache remains the foreground process. If Spring Boot fails during startup, the container exits instead of accepting broken traffic.

## Healthcheck

Docker health only becomes healthy when both layers work:

- Apache-proxied health: `http://127.0.0.1/health`
- Direct loopback Spring Boot health: `http://127.0.0.1:8085/health`

## Verification

Run:

```powershell
.\verify.ps1
```

The script checks container running state, health status, hostname, `8081 -> 80` mapping, that 8085 is not published, `httpd -t`, Apache modules, Spring Boot loopback binding, internal backend connectivity, proxied `/`, `/health`, `/info`, CSS delivery, 404 handling, Java 21, Spring Boot process state, Docker logs, and application logs.

Useful manual commands:

```powershell
docker ps --filter "name=qa.carzonrent"
docker logs --tail 50 qa.carzonrent
docker inspect -f '{{.State.Health.Status}}' qa.carzonrent
docker port qa.carzonrent
docker exec qa.carzonrent httpd -t
docker exec qa.carzonrent httpd -M
docker exec qa.carzonrent ss -lntp
docker exec qa.carzonrent curl -i http://127.0.0.1:8085/health
curl.exe -i http://localhost:8081/
curl.exe -i http://localhost:8081/health
curl.exe -i http://localhost:8081/info
```

Expected results are container health `healthy`, Apache `Syntax OK`, HTTP `200` for `/`, `/health`, and `/info`, HTTP `404` for `/not-found`, Apache on published port `8081`, and Spring Boot only on `127.0.0.1:8085` inside the container.

## Windows domain simulation

The hosts file is not modified automatically. To add the QA name:

1. Start Notepad as Administrator.
2. Open `C:\Windows\System32\drivers\etc\hosts` using the **All Files** file type.
3. Add:

   ```text
   127.0.0.1 qa.carzonrent.com
   ```

4. Save the file and optionally run `ipconfig /flushdns`.
5. Open [http://qa.carzonrent.com:8081](http://qa.carzonrent.com:8081).

The `:8081` suffix is required because hosts-file entries map names to IP addresses, not ports.

## Troubleshooting

- Build fails on `curl-minimal`: the Dockerfile uses `dnf --allowerasing` so full `curl` can replace the minimal package.
- Apache complains about missing TLS certificates: the Dockerfile removes the packaged inbound `ssl.conf` while keeping `mod_ssl` installed for `SSLProxyEngine`.
- Container stays unhealthy: inspect `docker logs qa.carzonrent`, then run `docker exec qa.carzonrent curl -i http://127.0.0.1:8085/health` and `docker exec qa.carzonrent curl -i http://127.0.0.1/health`.
- Backend appears externally reachable: confirm `docker port qa.carzonrent` only lists `80/tcp`; port 8085 must not appear.
