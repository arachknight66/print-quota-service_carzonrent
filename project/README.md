# Carzonrent Local QA Environment

Production-aligned local QA gateway built on CentOS Stream 9, Apache HTTP Server, and Docker. The public endpoint is [http://localhost:8081](http://localhost:8081). Apache never serves the dashboard from its document root: every request is reverse-proxied to a loopback-only backend at `127.0.0.1:8085`.

## Architecture

```text
Windows browser / curl
        |
        | HTTP :8081
        v
Docker port publish 8081 -> 80
        |
        v
Apache httpd (PID 1, :80)
        |
        | ProxyPass /, ProxyPassReverse /
        v
Python QA backend (127.0.0.1:8085)
        |
        +-- dynamic enterprise dashboard
        +-- /style.css
        +-- /health
```

The backend is intentionally bound to container loopback and port 8085 is not published. This makes Apache the only ingress tier and accurately demonstrates the reverse-proxy boundary.

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
`-- backend/
    |-- server.py
    |-- static/
    |   `-- style.css
    `-- templates/
        `-- index.html
```

## Build and run

Run from this `project` directory in PowerShell:

```powershell
docker build --pull --build-arg BUILD_VERSION=1.0.0 -t carzonrent-qa:1.0.0 -t carzonrent-qa:latest .

docker run -d `
  --name qa.carzonrent `
  --hostname qa.carzonrent `
  --restart unless-stopped `
  -p 8081:80 `
  carzonrent-qa:1.0.0
```

Open [http://localhost:8081](http://localhost:8081). Apache and the backend start automatically; no post-start command is needed.

To rebuild an existing deployment:

```powershell
docker rm -f qa.carzonrent
docker build --pull --build-arg BUILD_VERSION=1.0.1 -t carzonrent-qa:1.0.1 .
docker run -d --name qa.carzonrent --hostname qa.carzonrent --restart unless-stopped -p 8081:80 carzonrent-qa:1.0.1
```

## Automated verification

Run the supplied verification suite:

```powershell
.\verify.ps1
```

It verifies Docker runtime state, Docker health, hostname, port mapping, that 8085 is not published, `httpd -t`, required Apache modules, the loopback socket, direct in-container backend connectivity, proxied root/health/CSS responses, 404 behavior, and the backend response marker.

Equivalent individual operations commands are:

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
```

Expected results are `Syntax OK`, container health `healthy`, HTTP `200` for `/` and `/health`, Apache on `0.0.0.0:80`, and the backend only on `127.0.0.1:8085`.

## Apache configuration choices

- `ProxyRequests Off` prevents this server from becoming an open forward proxy.
- `ProxyPass` and `ProxyPassReverse` cover `/`, so HTML, CSS, health checks, and error routes all reach the backend through Apache.
- `ProxyPreserveHost On` retains the original QA host header for applications that use virtual-host routing.
- `SSLProxyEngine On` prepares Apache for a future HTTPS upstream. `mod_ssl` remains installed and loaded; its packaged inbound TLS virtual host is removed because this local gateway listens only on HTTP port 80 and has no server certificate.
- `Timeout 2400` and `ProxyTimeout 2400` match the requested long-running enterprise request profile. `connectiontimeout=5` fails quickly when the backend cannot be reached.
- `ProxyBadHeader Ignore` tolerates imperfect upstream response headers as requested.
- `X-Forwarded-Proto` and `X-Forwarded-Port` provide upstream routing context.
- Security response headers reduce content sniffing, clickjacking, referrer leakage, and browser device-permission exposure.
- Access and error logs are directed to stdout/stderr for `docker logs` compatibility.
- `ServerTokens Prod` and `ServerSignature Off` reduce version disclosure.

The CentOS packages load `proxy`, `proxy_http`, `proxy_connect`, `headers`, and `ssl` from `/etc/httpd/conf.modules.d`; the site configuration does not load them twice.

## Backend and process model

The temporary backend uses Python's standard library, so there are no downloaded application dependencies. It renders the current server time and build version for each dashboard request and provides `/health` as JSON.

`docker-entrypoint.sh` starts the loopback backend, waits until it is ready, and then uses `exec` to start the exact required command:

```text
/usr/sbin/httpd -DFOREGROUND
```

Apache therefore remains the container's foreground PID. The Docker health check tests both the proxied health URL and the backend directly; either tier failing makes the container unhealthy.

## Windows domain simulation

The hosts file is not modified automatically. To add the local QA name:

1. Start Notepad as Administrator.
2. Open `C:\Windows\System32\drivers\etc\hosts` (select **All Files** in the file dialog).
3. Add this line:

   ```text
   127.0.0.1 qa.carzonrent.com
   ```

4. Save the file and optionally run `ipconfig /flushdns` in an elevated terminal.
5. Open [http://qa.carzonrent.com:8081](http://qa.carzonrent.com:8081).

The `:8081` suffix is still required because a hosts-file entry changes name resolution only; it does not remap ports. Publishing `-p 80:80` would allow the portless URL, but the supplied deployment intentionally follows the required `localhost:8081` endpoint.

## Operations

```powershell
docker stop qa.carzonrent
docker start qa.carzonrent
docker restart qa.carzonrent
docker logs -f qa.carzonrent
docker rm -f qa.carzonrent
```

The `unless-stopped` restart policy brings the environment back after Docker Desktop restarts while respecting an intentional manual stop.

## Build issues diagnosed during setup

Two CentOS Stream 9 image defaults required explicit handling:

1. The base image contains `curl-minimal`, which conflicts with the requested full `curl` package. DNF's `--allowerasing` safely replaces it during the image build.
2. Installing `mod_ssl` adds an inbound TLS virtual host referencing a localhost certificate that minimal containers do not generate. Because this deployment needs `SSLProxyEngine` but does not expose HTTPS, the unused packaged `ssl.conf` is removed while the SSL module remains installed and active.

These are image-build/runtime configuration issues, not application defects.
