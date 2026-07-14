[CmdletBinding()]
param(
    [string]$ContainerName = "qa.carzonrent",
    [string]$FunctionalUrl = "http://127.0.0.1",
    [string]$PublicUrl = "http://qa.carzonrent.com",
    [int]$ExpectedHostPort = 80,
    [int]$ExpectedContainerPort = 8080,
    [switch]$RequirePublicUrl
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
    $Global:PSNativeCommandUseErrorActionPreference = $false
}
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
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $code = & curl.exe @arguments 2>$null
    $ErrorActionPreference = $previousErrorActionPreference
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($code)) {
        return "000"
    }
    return $code
}

Write-Host "Functional gateway: $FunctionalUrl"
Write-Host "Required public URL: $PublicUrl"

Test-Check "Docker container is running" { (docker inspect -f '{{.State.Running}}' $ContainerName) -eq "true" }
Test-Check "Docker health is healthy" { (docker inspect -f '{{.State.Health.Status}}' $ContainerName) -eq "healthy" }
Test-Check "Container identity is qa.carzonrent" {
    $identity = (docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $ContainerName) -join "`n"
    ($identity -match 'CONTAINER_NAME=qa\.carzonrent')
}
Test-Check "Host port $ExpectedHostPort maps to Apache port $ExpectedContainerPort" {
    (docker port $ContainerName "$ExpectedContainerPort/tcp") -match ":$ExpectedHostPort$"
}
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
Test-Check "Spring Boot binds to loopback and not wildcard" {
    $addresses = (docker exec $ContainerName sh -lc "awk 'NR>1 && `$2 ~ /:1F95/ {print `$2}' /proc/net/tcp /proc/net/tcp6") -join "`n"
    ($addresses -match "0100007F:1F95") -and ($addresses -notmatch "00000000:1F95") -and ($addresses -notmatch "00000000000000000000000000000000:1F95")
}
Test-Check "Spring Boot process is running" { ((docker top $ContainerName) -join "`n") -match 'java .* -jar /opt/carzonrent/runtime/qa-dashboard\.jar' }
Test-Check "Entrypoint supervises Apache and Spring Boot" {
    $top = (docker top $ContainerName -eo pid,ppid,user,args) -join "`n"
    ($top -match 'entrypoint\.sh') -and ($top -match 'httpd -DFOREGROUND') -and ($top -match 'java .* -jar /opt/carzonrent/runtime/qa-dashboard\.jar')
}
Test-Check "Spring Boot runs as printuser user" {
    $result = docker exec $ContainerName sh -lc "printuser_uid=`$(id -u printuser); for cmdline in /proc/[0-9]*/cmdline; do tr '\\0' ' ' < `$cmdline | grep -q '/opt/carzonrent/runtime/qa-dashboard.jar' || continue; pid=`$(basename `$(dirname `$cmdline)); uid=`$(awk '/^Uid:/ {print `$2}' /proc/`$pid/status); test `$uid = `$printuser_uid && exit 0; done; exit 1"
    $LASTEXITCODE -eq 0
}
Test-Check "Runtime environment is configured explicitly" {
    $env = (docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' $ContainerName) -join "`n"
    ($env -match 'GIT_COMMIT_ID=') -and
        ($env -match 'DOCKER_IMAGE_TAG=') -and
        ($env -match 'JENKINS_BUILD_NUMBER=')
}
Test-Check "Apache logs to container stdout and stderr" {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $logs = (docker logs --tail 80 $ContainerName 2>&1) -join "`n"
    $ErrorActionPreference = $previousErrorActionPreference
    ($logs -match 'GET /health/readiness') -and ($logs -match 'duration_us=')
}
Test-Check "Entrypoint emitted useful startup logs" {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $logs = (docker logs $ContainerName 2>&1) -join "`n"
    $ErrorActionPreference = $previousErrorActionPreference
    ($logs -match 'Starting Spring Boot') -and ($logs -match 'Starting Apache in foreground mode')
}
Test-Check "Backend is reachable inside the container" {
    (docker exec $ContainerName curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/health) -eq "200"
}
Test-Check "Apache reverse proxy returns HTTP 200" { (Get-HttpCode "$FunctionalUrl/" @("Host: qa.carzonrent.com")) -eq "200" }
Test-Check "Health endpoint returns HTTP 200" { (Get-HttpCode "$FunctionalUrl/health") -eq "200" }
Test-Check "Health endpoint reports UP" { (curl.exe -sS "$FunctionalUrl/health") -match '"status":"UP"' }
Test-Check "Readiness endpoint returns UP" {
    ((Get-HttpCode "$FunctionalUrl/health/readiness") -eq "200") -and
        ((curl.exe -sS "$FunctionalUrl/health/readiness") -match '"status":"UP"')
}
Test-Check "Liveness endpoint returns UP" {
    ((Get-HttpCode "$FunctionalUrl/health/liveness") -eq "200") -and
        ((curl.exe -sS "$FunctionalUrl/health/liveness") -match '"status":"UP"')
}
Test-Check "Info endpoint returns HTTP 200" { (Get-HttpCode "$FunctionalUrl/info") -eq "200" }
Test-Check "Info reports QA and active reverse proxy" {
    $info = curl.exe -sS "$FunctionalUrl/info"
    ($info -match '"environment":"QA"') -and ($info -match '"reverseProxyStatus":"ACTIVE"')
}
Test-Check "Info exposes CI/CD build metadata" {
    $info = curl.exe -sS "$FunctionalUrl/info"
    ($info -match '"buildVersion"') -and
        ($info -match '"gitCommitId"') -and
        ($info -match '"buildTimestamp"') -and
        ($info -match '"dockerImageTag"') -and
        ($info -match '"jenkinsBuildNumber"') -and
        ($info -notmatch '"gitCommitId":"unknown"')
}
Test-Check "Actuator info exposes app and git sections" {
    $actuatorInfo = curl.exe -sS "$FunctionalUrl/actuator/info"
    ($actuatorInfo -match '"app"') -and ($actuatorInfo -match '"git"')
}
Test-Check "Metrics endpoint exposes JVM and HTTP metrics" {
    $metrics = curl.exe -sS "$FunctionalUrl/metrics"
    ($metrics -match 'jvm\.memory\.used') -and ($metrics -match 'http\.server\.requests')
}
Test-Check "CentOS Stream runtime is reported" { (curl.exe -sS "$FunctionalUrl/info") -match 'CentOS Stream 9' }
Test-Check "Dashboard references external CSS" {
    $html = (curl.exe -sS "$FunctionalUrl/") | Out-String
    ($LASTEXITCODE -eq 0) -and ($html -match '<link[^>]+stylesheet') -and ($html -match '/style.css') -and ($html -match '<section')
}
Test-Check "CSS loads through Apache reverse proxy" {
    $code = Get-HttpCode "$FunctionalUrl/style.css"
    $css = (curl.exe -sS "$FunctionalUrl/style.css") | Out-String
    ($code -eq "200") -and ($css -match "font-family") -and ($css -match "card")
}
Test-Check "Unknown route returns HTTP 404" { (Get-HttpCode "$FunctionalUrl/does-not-exist") -eq "404" }
Test-Check "404 response includes correlation ID and no stack trace" {
    $body = curl.exe -sS "$FunctionalUrl/does-not-exist"
    ($body -match '"correlationId"') -and ($body -notmatch 'Exception') -and ($body -notmatch 'trace')
}
Test-Check "Security headers are present" {
    $headers = (curl.exe -sSI "$FunctionalUrl/") -join "`n"
    ($headers -match 'X-Content-Type-Options:\s*nosniff') -and
        ($headers -match 'X-Frame-Options:\s*SAMEORIGIN') -and
        ($headers -match 'Referrer-Policy:\s*strict-origin-when-cross-origin') -and
        ($headers -match 'Permissions-Policy:')
}
Test-Check "Correlation ID is returned and logged" {
    $correlationId = "verify-$([guid]::NewGuid().ToString('N'))"
    $headers = (curl.exe -sSI -H "X-Correlation-ID: $correlationId" "$FunctionalUrl/info") -join "`n"
    Start-Sleep -Seconds 1
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $logs = (docker logs --tail 160 $ContainerName 2>&1) -join "`n"
    $ErrorActionPreference = $previousErrorActionPreference
    ($headers -match "X-Correlation-ID:\s*$correlationId") -and ($logs -match [regex]::Escape($correlationId))
}
Test-Check "Java 21 runtime is installed" {
    $version = (docker exec $ContainerName sh -lc "java -version 2>&1") -join "`n"
    ($LASTEXITCODE -eq 0) -and ($version -match 'version "21')
}
Test-Check "Container stop signal is SIGTERM" {
    (docker inspect -f '{{.Config.StopSignal}}' $ContainerName) -eq "SIGTERM"
}
Test-Check "Container runs without root" {
    (docker inspect -f '{{.Config.User}}' $ContainerName) -match '^10001(:10001)?$'
}
Test-Check "No obvious zombie processes are present" {
    $states = (docker exec $ContainerName sh -lc "for stat in /proc/[0-9]*/stat; do awk '{print `$3}' `$stat; done") -join "`n"
    $states -notmatch "(^|`n)Z($|`n)"
}
Test-Check "Container restart preserves health" {
    docker restart $ContainerName | Out-Null
    Start-Sleep -Seconds 20
    (docker inspect -f '{{.State.Status}} {{.State.Health.Status}}' $ContainerName) -eq "running healthy"
}

$publicCode = Get-HttpCode $PublicUrl
if ($publicCode -eq "200") {
    Write-Host "[PASS] Required public URL is reachable: $PublicUrl" -ForegroundColor Green
} else {
    $resolved = @(Resolve-DnsName qa.carzonrent.com -Type A -ErrorAction SilentlyContinue | Select-Object -ExpandProperty IPAddress -Unique)
    $mapping = (docker port $ContainerName "$ExpectedContainerPort/tcp" 2>$null) -join ", "
    $port80Listener = Get-NetTCPConnection -LocalPort 80 -State Listen -ErrorAction SilentlyContinue
    Write-Warning "Required public URL is NOT reachable (HTTP code: $publicCode)."
    Write-Warning "qa.carzonrent.com resolves to: $($resolved -join ', '). Container port $ExpectedContainerPort is mapped as: $mapping."
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
