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
Test-Check "Required Apache modules are loaded" {
    $modules = docker exec $ContainerName httpd -M 2>&1
    @("proxy_module", "proxy_http_module", "proxy_connect_module", "ssl_module", "headers_module") |
        ForEach-Object { $modules -match [regex]::Escape($_) } |
        Where-Object { -not $_ } | Measure-Object | Select-Object -ExpandProperty Count | ForEach-Object { $_ -eq 0 }
}
Test-Check "Spring Boot listens only on 127.0.0.1:8085" {
    $sockets = docker exec $ContainerName ss -lntp
    (($sockets -match "127\.0\.0\.1]:8085") -or ($sockets -match "127\.0\.0\.1:8085")) -and
        ($sockets -notmatch "0\.0\.0\.0:8085") -and
        ($sockets -notmatch "\[::\]:8085")
}
Test-Check "Spring Boot is reachable inside the container" {
    (docker exec $ContainerName curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/health) -eq "200"
}
Test-Check "Apache proxy returns HTTP 200" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/") -eq "200" }
Test-Check "Proxied health endpoint returns HTTP 200" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/health") -eq "200" }
Test-Check "Proxied info endpoint returns HTTP 200" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/info") -eq "200" }
Test-Check "Spring Boot static CSS is served through Apache" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/css/dashboard.css") -eq "200" }
Test-Check "Unknown routes return HTTP 404" { (curl.exe -sS -o NUL -w '%{http_code}' "$BaseUrl/not-found") -eq "404" }
Test-Check "Backend marker proves reverse proxy path" { (curl.exe -sSI "$BaseUrl/") -match "X-Carzonrent-Backend: qa-dashboard-springboot:8085" }
Test-Check "Info endpoint reports QA environment" { (curl.exe -sS "$BaseUrl/info") -match '"environment":"QA"' }
Test-Check "Java 21 is installed" {
    $version = cmd.exe /d /c "docker exec $ContainerName java -version 2>&1"
    ($LASTEXITCODE -eq 0) -and ($version -match 'version "21')
}
Test-Check "Spring Boot process is running" { (docker top $ContainerName) -match 'qa-dashboard\.jar' }

Write-Host "`nContainer summary:"
docker ps --filter "name=$ContainerName" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
Write-Host "`nRecent Docker and application logs:"
docker logs --tail 40 $ContainerName

if ($failures.Count -gt 0) {
    Write-Error ("Verification failed: " + ($failures -join "; "))
    exit 1
}

Write-Host "`nAll Carzonrent Spring Boot QA checks passed." -ForegroundColor Green
