[CmdletBinding()]
param(
    [string]$ContainerName = "qa.carzonrent",
    [string]$BaseUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"
$failures = [System.Collections.Generic.List[string]]::new()

function Test-Check {
    param([string]$Name, [scriptblock]$Check)
    try {
        if (& $Check) { Write-Host "[PASS] $Name" -ForegroundColor Green }
        else { $failures.Add($Name); Write-Host "[FAIL] $Name" -ForegroundColor Red }
    } catch {
        $failures.Add("$Name`: $($_.Exception.Message)")
        Write-Host "[FAIL] $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

Test-Check "Container is running" { (docker inspect -f '{{.State.Running}}' $ContainerName) -eq "true" }
Test-Check "Container health is healthy" { (docker inspect -f '{{.State.Health.Status}}' $ContainerName) -eq "healthy" }
Test-Check "Hostname is qa.carzonrent" { (docker inspect -f '{{.Config.Hostname}}' $ContainerName) -eq "qa.carzonrent" }
Test-Check "Host port 8081 maps to container port 80" { (docker port $ContainerName 80/tcp) -match ":8081$" }
Test-Check "Backend port 8085 is not published" {
    $bindings = docker inspect -f '{{json .HostConfig.PortBindings}}' $ContainerName
    $bindings -notmatch '8085/tcp'
}
Test-Check "Apache configuration is valid" {
    cmd.exe /d /c "docker exec $ContainerName httpd -t >NUL 2>&1"
    $LASTEXITCODE -eq 0
}
Test-Check "Required proxy modules are loaded" {
    $modules = docker exec $ContainerName httpd -M 2>&1
    @("proxy_module", "proxy_http_module", "proxy_connect_module", "ssl_module", "headers_module") |
        ForEach-Object { $modules -match [regex]::Escape($_) } |
        Where-Object { -not $_ } | Measure-Object | Select-Object -ExpandProperty Count | ForEach-Object { $_ -eq 0 }
}
Test-Check "Backend listens only on 127.0.0.1:8085" { (docker exec $ContainerName ss -lnt) -match "127\.0\.0\.1:8085" }
Test-Check "Backend is reachable inside the container" { (docker exec $ContainerName curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/health) -eq "200" }
Test-Check "Apache proxy returns HTTP 200" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/") -eq "200" }
Test-Check "Proxied health endpoint returns HTTP 200" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/health") -eq "200" }
Test-Check "CSS is served through the proxy" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/style.css") -eq "200" }
Test-Check "Unknown routes return HTTP 404" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/not-found") -eq "404" }
Test-Check "Backend marker proves reverse proxy path" { (curl.exe -sSI "$BaseUrl/") -match "X-Carzonrent-Backend: qa-dashboard:8085" }

Write-Host "`nContainer summary:"
docker ps --filter "name=$ContainerName" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
Write-Host "`nRecent logs:"
docker logs --tail 15 $ContainerName

if ($failures.Count -gt 0) {
    Write-Error ("Verification failed: " + ($failures -join "; "))
    exit 1
}

Write-Host "`nAll Carzonrent QA checks passed." -ForegroundColor Green
