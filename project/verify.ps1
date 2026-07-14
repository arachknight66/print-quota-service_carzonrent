[CmdletBinding()]
param(
    [string]$ContainerName = "qa.carzonrent",
    [string]$FunctionalUrl = "http://127.0.0.1",
    [string]$PublicUrl = "http://qa.carzonrent.com",
    [int]$ExpectedHostPort = 80,
    [switch]$RequirePublicUrl
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

function Get-HttpCode([string]$Url, [string[]]$Headers = @()) {
    $arguments = @("-sS", "--connect-timeout", "4", "-o", "NUL", "-w", "%{http_code}")
    foreach ($header in $Headers) { $arguments += @("-H", $header) }
    $arguments += $Url
    & curl.exe @arguments 2>$null
}

Write-Host "Functional gateway: $FunctionalUrl"
Write-Host "Required public URL: $PublicUrl"

Test-Check "Docker container is running" { (docker inspect -f '{{.State.Running}}' $ContainerName) -eq "true" }
Test-Check "Docker health is healthy" { (docker inspect -f '{{.State.Health.Status}}' $ContainerName) -eq "healthy" }
Test-Check "Container hostname is qa.carzonrent" { (docker inspect -f '{{.Config.Hostname}}' $ContainerName) -eq "qa.carzonrent" }
Test-Check "Host port $ExpectedHostPort maps to Apache port 80" { (docker port $ContainerName 80/tcp) -match ":$ExpectedHostPort$" }
Test-Check "Backend port 8085 is not published" {
    (docker inspect -f '{{json .HostConfig.PortBindings}}' $ContainerName) -notmatch '8085/tcp'
}
Test-Check "Apache configuration syntax is valid" {
    $output = docker exec $ContainerName sh -lc "httpd -t >/tmp/httpd-syntax.out 2>&1; echo `$?; cat /tmp/httpd-syntax.out"
    (($output | Select-Object -First 1) -eq "0")
}
Test-Check "Required Apache modules are loaded" {
    $modules = (docker exec $ContainerName httpd -M 2>&1) -join "`n"
    @("proxy_module", "proxy_http_module", "proxy_connect_module", "ssl_module", "headers_module") |
        Where-Object { $modules -notmatch [regex]::Escape($_) } |
        Measure-Object | Select-Object -ExpandProperty Count | ForEach-Object { $_ -eq 0 }
}
Test-Check "Spring Boot listens only on 127.0.0.1:8085" {
    $sockets = (docker exec $ContainerName ss -lntp) -join "`n"
    (($sockets -match "127\.0\.0\.1:8085") -or ($sockets -match "\[::ffff:127\.0\.0\.1\]:8085")) -and
        ($sockets -notmatch "0\.0\.0\.0:8085") -and ($sockets -notmatch "\[::\]:8085")
}
Test-Check "Spring Boot process is running" { ((docker top $ContainerName) -join "`n") -match 'qa-dashboard\.jar' }
Test-Check "Backend is reachable inside the container" {
    (docker exec $ContainerName curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/health) -eq "200"
}
Test-Check "Apache reverse proxy returns HTTP 200" { (Get-HttpCode "$FunctionalUrl/" @("Host: qa.carzonrent.com")) -eq "200" }
Test-Check "Health endpoint returns HTTP 200" { (Get-HttpCode "$FunctionalUrl/health") -eq "200" }
Test-Check "Health endpoint reports UP" { (curl.exe -sS "$FunctionalUrl/health") -match '"status":"UP"' }
Test-Check "Info endpoint returns HTTP 200" { (Get-HttpCode "$FunctionalUrl/info") -eq "200" }
Test-Check "Info reports QA and active reverse proxy" {
    $info = curl.exe -sS "$FunctionalUrl/info"
    ($info -match '"environment":"QA"') -and ($info -match '"reverseProxyStatus":"ACTIVE"')
}
Test-Check "CentOS Stream runtime is reported" { (curl.exe -sS "$FunctionalUrl/info") -match 'CentOS Stream 9' }
Test-Check "Dashboard is pure HTML with no CSS or JavaScript references" {
    $html = (curl.exe -sS "$FunctionalUrl/") | Out-String
    ($LASTEXITCODE -eq 0) -and
        ($html -notmatch '<link[^>]+stylesheet') -and
        ($html -notmatch '<style') -and
        ($html -notmatch '<script') -and
        ($html -match '<table') -and
        ($html -match 'Legacy HTML Operations Page')
}
Test-Check "Unknown route returns HTTP 404" { (Get-HttpCode "$FunctionalUrl/does-not-exist") -eq "404" }
Test-Check "Backend marker proves Apache-to-Spring path" {
    ((curl.exe -sSI "$FunctionalUrl/") -join "`n") -match 'X-Carzonrent-Backend: qa-dashboard-springboot:8085'
}
Test-Check "Java 21 runtime is installed" {
    $version = (docker exec $ContainerName sh -lc "java -version 2>&1") -join "`n"
    ($LASTEXITCODE -eq 0) -and ($version -match 'version "21')
}

$publicCode = Get-HttpCode $PublicUrl
if ($publicCode -eq "200") {
    Write-Host "[PASS] Required public URL is reachable: $PublicUrl" -ForegroundColor Green
} else {
    $resolved = @(Resolve-DnsName qa.carzonrent.com -Type A -ErrorAction SilentlyContinue | Select-Object -ExpandProperty IPAddress -Unique)
    $mapping = (docker port $ContainerName 80/tcp 2>$null) -join ", "
    $port80Listener = Get-NetTCPConnection -LocalPort 80 -State Listen -ErrorAction SilentlyContinue
    Write-Warning "Required public URL is NOT reachable (HTTP code: $publicCode)."
    Write-Warning "qa.carzonrent.com resolves to: $($resolved -join ', '). Container port 80 is mapped as: $mapping."
    if (-not $port80Listener) {
        Write-Warning "Exact cause: no host process is listening on TCP port 80. A mapping such as -p 80:80 is required for a URL without a port suffix."
    }
    if ($RequirePublicUrl) { $failures.Add("Required public URL $PublicUrl is unreachable") }
}

Write-Host "`nContainer summary:"
docker ps --filter "name=$ContainerName" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'

if ($failures.Count -gt 0) {
    Write-Error ("Verification failed: " + ($failures -join "; "))
    exit 1
}

Write-Host "`nAll functional QA deployment checks passed." -ForegroundColor Green
