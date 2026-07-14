# PowerShell Staging Verification Script for Carzonrent QA Environment (Phase 2)
# Ensures all architectural constraints, process boundaries, security controls, and health endpoints are fully verified.

$ErrorActionPreference = "Stop"

Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "CARZONRENT QA ENVIRONMENT VERIFICATION (PHASE 2)" -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan

$allPassed = $true
$report = @()

function Log-Result($testName, $passed, $message) {
    if ($passed) {
        Write-Host "[PASS] $testName - $message" -ForegroundColor Green
        $global:report += [PSCustomObject]@{ Test = $testName; Status = "PASS"; Details = $message }
    } else {
        Write-Host "[FAIL] $testName - $message" -ForegroundColor Red
        $global:report += [PSCustomObject]@{ Test = $testName; Status = "FAIL"; Details = $message }
        $global:allPassed = $false
    }
}

# 1. Verify Container running
try {
    $containerState = docker inspect --format='{{.State.Status}}' qa.carzonrent
    if ($containerState -eq "running") {
        Log-Result "Container Running" $true "Container 'qa.carzonrent' is in running state."
    } else {
        Log-Result "Container Running" $false "Container 'qa.carzonrent' is in state: $containerState"
    }
} catch {
    Log-Result "Container Running" $false "Failed to inspect container 'qa.carzonrent'. Is it created?"
}

# 2. Verify Container healthy
try {
    $containerHealth = docker inspect --format='{{.State.Health.Status}}' qa.carzonrent
    if ($containerHealth -eq "healthy") {
        Log-Result "Container Health" $true "Container 'qa.carzonrent' is healthy."
    } else {
        Log-Result "Container Health" $false "Container 'qa.carzonrent' health status is: $containerHealth"
    }
} catch {
    Log-Result "Container Health" $false "Could not query container health status."
}

# 3. Verify Apache configuration syntax
try {
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $apacheCheck = docker exec qa.carzonrent httpd -t 2>&1 | Out-String
    $ErrorActionPreference = $oldEap
    if ($LASTEXITCODE -eq 0 -and $apacheCheck -like "*Syntax OK*") {
        Log-Result "Apache Config Syntax" $true "Apache configuration syntax is OK."
    } else {
        Log-Result "Apache Config Syntax" $false "Apache config syntax error: $apacheCheck"
    }
} catch {
    Log-Result "Apache Config Syntax" $false "Failed to run httpd -t inside the container: $_"
}

# 4. Verify Proxy modules loaded
try {
    $modules = docker exec qa.carzonrent httpd -M 2>&1 | Out-String
    $proxyLoaded = $modules -like "*proxy_module*"
    $proxyHttpLoaded = $modules -like "*proxy_http_module*"
    
    if ($proxyLoaded -and $proxyHttpLoaded) {
        Log-Result "Apache Proxy Modules" $true "mod_proxy and mod_proxy_http are successfully loaded."
    } else {
        Log-Result "Apache Proxy Modules" $false "Loaded modules check failed. mod_proxy: $proxyLoaded, mod_proxy_http: $proxyHttpLoaded"
    }
} catch {
    Log-Result "Apache Proxy Modules" $false "Failed to list Apache modules."
}

# 5. Verify Port mapping (Host 80 -> Container 8080)
try {
    $portMap = docker port qa.carzonrent 8080
    if ($portMap -like "*:80*") {
        Log-Result "Port Mapping" $true "Container port 8080 is mapped to host port ($portMap)."
    } else {
        Log-Result "Port Mapping" $false "Container port 8080 mapping details: $portMap"
    }
} catch {
    Log-Result "Port Mapping" $false "Failed to check container port mapping."
}

# 6. Verify Spring Boot is reachable internally (inside container)
try {
    $internalCurl = docker exec qa.carzonrent curl -s http://127.0.0.1:8085/actuator/health
    if ($internalCurl -like "*UP*") {
        Log-Result "Internal Spring Boot Access" $true "Spring Boot is reachable on 127.0.0.1:8085/actuator/health inside the container."
    } else {
        Log-Result "Internal Spring Boot Access" $false "Spring Boot returned unexpected response: $internalCurl"
    }
} catch {
    Log-Result "Internal Spring Boot Access" $false "Failed to curl Spring Boot internally."
}

# 7. Verify Spring Boot is NOT reachable externally (from host)
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8085/actuator/health" -TimeoutSec 3 -ErrorAction Stop
    Log-Result "External Backend Isolation" $false "ERROR: Spring Boot was reachable externally on port 8085!"
} catch [System.Net.WebException] {
    Log-Result "External Backend Isolation" $true "Spring Boot port 8085 is not accessible from host (Connection Refused/Blocked)."
} catch {
    Log-Result "External Backend Isolation" $true "Spring Boot port 8085 is not accessible: $_"
}

# 8. Verify Apache responds on localhost (Port 80)
try {
    $resDashboard = Invoke-WebRequest -Uri "http://localhost/" -UseBasicParsing
    if ($resDashboard.StatusCode -eq 200 -and $resDashboard.Content -like "*Carzonrent*") {
        Log-Result "Apache HTTP Gateway" $true "HTTP 200 OK received from Apache frontend gateway."
    } else {
        Log-Result "Apache HTTP Gateway" $false "Failed to load dashboard. Status: $($resDashboard.StatusCode)"
    }
} catch {
    Log-Result "Apache HTTP Gateway" $false "Failed to reach Apache frontend on port 80: $_"
}

# 9. Verify Reverse Proxy Working (Forwarding headers & content)
try {
    $resDashboard = Invoke-WebRequest -Uri "http://localhost/" -UseBasicParsing
    if ($resDashboard.Content -like "*ACTIVE (Routed via Apache)*") {
        Log-Result "Reverse Proxy Functionality" $true "Reverse proxy headers correctly identified. Request verified as routed through Apache."
    } else {
        Log-Result "Reverse Proxy Functionality" $false "Reverse proxy status display was not verified as ACTIVE."
    }
} catch {
    Log-Result "Reverse Proxy Functionality" $false "Failed to verify reverse proxy routing."
}

# 10. Verify Health endpoint (/health)
try {
    $resHealth = Invoke-WebRequest -Uri "http://localhost/health" -UseBasicParsing
    $healthJson = $resHealth.Content | ConvertFrom-Json
    if ($healthJson.status -eq "UP" -and $healthJson.components.db.status -eq "UP") {
        Log-Result "Health Endpoint" $true "Health endpoint returned status UP with valid Database component."
    } else {
        Log-Result "Health Endpoint" $false "Health response structure invalid: $($resHealth.Content)"
    }
} catch {
    Log-Result "Health Endpoint" $false "Failed to query /health endpoint: $_"
}

# 11. Verify Info endpoint (/info)
try {
    $resInfo = Invoke-WebRequest -Uri "http://localhost/info" -UseBasicParsing
    $infoJson = $resInfo.Content | ConvertFrom-Json
    if ($infoJson.app.name -eq "print-quota-service" -and $infoJson.app.environment -eq "QA-Phase2") {
        Log-Result "Info Endpoint" $true "Info endpoint returned valid app name and environment (QA-Phase2)."
    } else {
        Log-Result "Info Endpoint" $false "Info response structure invalid: $($resInfo.Content)"
    }
} catch {
    Log-Result "Info Endpoint" $false "Failed to query /info endpoint: $_"
}

# 12. Verify CSS file loads
try {
    $resCss = Invoke-WebRequest -Uri "http://localhost/css/dashboard.css" -UseBasicParsing
    if ($resCss.StatusCode -eq 200 -and $resCss.Content -like "*Modern Corporate Staging Styles*") {
        Log-Result "Static CSS Resource" $true "dashboard.css loaded successfully (HTTP 200)."
    } else {
        Log-Result "Static CSS Resource" $false "Failed to load dashboard.css. Content check failed."
    }
} catch {
    Log-Result "Static CSS Resource" $false "Failed to fetch CSS file: $_"
}

# 13. Verify 404 handling
try {
    $res404 = Invoke-WebRequest -Uri "http://localhost/non-existent-page-test-123" -UseBasicParsing
    Log-Result "Error 404 Handling" $false "Expected HTTP 404 but got HTTP $($res404.StatusCode)"
} catch [System.Net.WebException] {
    $webResponse = $_.Exception.Response
    if ($webResponse -and $webResponse.StatusCode -eq 404) {
        Log-Result "Error 404 Handling" $true "Server correctly returned HTTP 404 for non-existent page."
    } else {
        Log-Result "Error 404 Handling" $false "Expected HTTP 404, but got exception status: $($webResponse.StatusCode)"
    }
} catch {
    Log-Result "Error 404 Handling" $false "Unexpected error during 404 check: $_"
}

# 14. Verify PID 1 is Tini (Process Parenting)
try {
    $pid1 = docker exec qa.carzonrent ps -p 1 -o comm=
    $pid1Clean = $pid1.Trim()
    if ($pid1Clean -match "tini") {
        Log-Result "Tini Process Parenting (PID 1)" $true "Verified Tini is running as PID 1 inside the container ($pid1Clean)."
    } else {
        Log-Result "Tini Process Parenting (PID 1)" $false "Process running as PID 1 is not Tini: $pid1Clean"
    }
} catch {
    Log-Result "Tini Process Parenting (PID 1)" $false "Failed to query PID 1 command: $_"
}

# 15. Verify Java Process Owner is non-root printuser
try {
    $javaPid = docker exec qa.carzonrent pgrep -f java
    $javaPidClean = $javaPid.Trim()
    if ($javaPidClean -match "^\d+$") {
        $javaUser = docker exec qa.carzonrent ps -o user= -p $javaPidClean
        $javaUserClean = $javaUser.Trim()
        if ($javaUserClean -eq "printuser" -or $javaUserClean -eq "printus+") {
            Log-Result "Non-Root Java Execution" $true "Verified Java runs as non-root user: $javaUserClean (PID $javaPidClean)"
        } else {
            Log-Result "Non-Root Java Execution" $false "Java process is running as root/unexpected user: $javaUserClean"
        }
    } else {
        Log-Result "Non-Root Java Execution" $false "Could not locate Java process PID: $javaPid"
    }
} catch {
    Log-Result "Non-Root Java Execution" $false "Failed to verify process owner: $_"
}

# 16. Verify Security Headers (Clickjacking, MIME Sniffing, XSS, CSP)
try {
    $headers = Invoke-WebRequest -Uri "http://localhost/" -Method Head
    
    $xFrame = $headers.Headers["X-Frame-Options"]
    $xContentType = $headers.Headers["X-Content-Type-Options"]
    $xXss = $headers.Headers["X-XSS-Protection"]
    $csp = $headers.Headers["Content-Security-Policy"]
    
    $headersPassed = $true
    $headerDetails = @()
    
    if ($xFrame -eq "SAMEORIGIN") {
        $headerDetails += "X-Frame-Options: SAMEORIGIN"
    } else {
        $headersPassed = $false
        $headerDetails += "X-Frame-Options missing/invalid ($xFrame)"
    }
    
    if ($xContentType -eq "nosniff") {
        $headerDetails += "X-Content-Type-Options: nosniff"
    } else {
        $headersPassed = $false
        $headerDetails += "X-Content-Type-Options missing/invalid ($xContentType)"
    }
    
    if ($xXss -like "*1;*") {
        $headerDetails += "X-XSS-Protection: active"
    } else {
        $headersPassed = $false
        $headerDetails += "X-XSS-Protection missing/invalid ($xXss)"
    }
    
    if ($csp -like "*default-src 'self'*") {
        $headerDetails += "CSP: Active ('self')"
    } else {
        $headersPassed = $false
        $headerDetails += "CSP missing/invalid ($csp)"
    }
    
    $detailsString = $headerDetails -join ", "
    Log-Result "Security Headers Check" $headersPassed "Headers status: $detailsString"
} catch {
    Log-Result "Security Headers Check" $false "Failed to read headers from server: $_"
}

# 17. Verify Gzip Compression (mod_deflate)
try {
    # Send custom headers requesting gzip compression
    $req = [System.Net.HttpWebRequest]::Create("http://localhost/")
    $req.Headers.Add("Accept-Encoding", "gzip")
    $res = $req.GetResponse()
    $contentEncoding = $res.Headers["Content-Encoding"]
    $res.Close()
    
    if ($contentEncoding -eq "gzip") {
        Log-Result "HTTP Compression (Gzip)" $true "Gzip compression is active (Content-Encoding: gzip)."
    } else {
        Log-Result "HTTP Compression (Gzip)" $false "Server did not compress response. Content-Encoding is: $contentEncoding"
    }
} catch {
    Log-Result "HTTP Compression (Gzip)" $false "Failed to verify response compression: $_"
}

# 18. Verify Environment variables propagation
try {
    $envStage = docker exec qa.carzonrent printenv STAGE_ENV
    $envStageClean = $envStage.Trim()
    if ($envStageClean -eq "QA-Phase2") {
        Log-Result "Environment Variable STAGE_ENV" $true "STAGE_ENV is correctly propagated as '$envStageClean'."
    } else {
        Log-Result "Environment Variable STAGE_ENV" $false "STAGE_ENV value is unexpected: '$envStageClean'"
    }
} catch {
    Log-Result "Environment Variable STAGE_ENV" $false "Failed to query container environment variables."
}

Write-Host "====================================================================" -ForegroundColor Cyan
if ($allPassed) {
    Write-Host "ALL PHASE 2 VERIFICATION TESTS PASSED SUCCESSFULLY!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SOME VERIFICATION TESTS FAILED!" -ForegroundColor Red
    exit 1
}
