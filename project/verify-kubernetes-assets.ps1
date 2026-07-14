[CmdletBinding()]
param(
    [string]$ChartPath = ""
)

$ErrorActionPreference = "Stop"
$failures = [System.Collections.Generic.List[string]]::new()
if ([string]::IsNullOrWhiteSpace($ChartPath)) {
    $ChartPath = Join-Path $PSScriptRoot "helm\carzonrent-qa"
}

function Test-Check {
    param([string]$Name, [scriptblock]$Check)
    try {
        if (& $Check) {
            Write-Host "[PASS] $Name" -ForegroundColor Green
        } else {
            $failures.Add($Name)
            Write-Host "[FAIL] $Name" -ForegroundColor Red
        }
    } catch {
        $failures.Add("$Name`: $($_.Exception.Message)")
        Write-Host "[FAIL] $Name - $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Read-Text([string]$Path) {
    Get-Content $Path -Raw
}

$templates = Join-Path $ChartPath "templates"

Test-Check "Chart.yaml exists" { Test-Path (Join-Path $ChartPath "Chart.yaml") }
Test-Check "values.yaml exists" { Test-Path (Join-Path $ChartPath "values.yaml") }
Test-Check "Namespace template exists" { Test-Path (Join-Path $templates "namespace.yaml") }
Test-Check "Deployment template exists" { Test-Path (Join-Path $templates "deployment.yaml") }
Test-Check "Service template exists" { Test-Path (Join-Path $templates "service.yaml") }
Test-Check "Ingress template exists" { Test-Path (Join-Path $templates "ingress.yaml") }
Test-Check "ConfigMap template exists" { Test-Path (Join-Path $templates "configmap.yaml") }
Test-Check "Secret template exists" { Test-Path (Join-Path $templates "secret.yaml") }
Test-Check "HPA template exists" { Test-Path (Join-Path $templates "hpa.yaml") }
Test-Check "PDB template exists" { Test-Path (Join-Path $templates "pdb.yaml") }
Test-Check "NetworkPolicy template exists" { Test-Path (Join-Path $templates "networkpolicy.yaml") }
Test-Check "ResourceQuota template exists" { Test-Path (Join-Path $templates "resourcequota.yaml") }
Test-Check "LimitRange template exists" { Test-Path (Join-Path $templates "limitrange.yaml") }

$deployment = Read-Text (Join-Path $templates "deployment.yaml")
$values = Read-Text (Join-Path $ChartPath "values.yaml")
$ingress = Read-Text (Join-Path $templates "ingress.yaml")
$service = Read-Text (Join-Path $templates "service.yaml")
$networkPolicy = Read-Text (Join-Path $templates "networkpolicy.yaml")

Test-Check "Deployment uses rolling update" { $deployment -match "RollingUpdate" -and $deployment -match "maxUnavailable" -and $deployment -match "maxSurge" }
Test-Check "Deployment has startup readiness and liveness probes" { $deployment -match "startupProbe" -and $deployment -match "readinessProbe" -and $deployment -match "livenessProbe" }
Test-Check "Probes use Spring Boot Actuator health groups" { $values -match "/health/readiness" -and $values -match "/health/liveness" }
Test-Check "Service is configurable as ClusterIP" { $values -match "type: ClusterIP" -and $service -match "targetPort" }
Test-Check "Ingress host is parameterized" { $values -match "qa.carzonrent.com" -and $ingress -match "ingress.host" }
Test-Check "Prometheus annotations are present" { $values -match "prometheus.io/scrape" -and $values -match "/prometheus" }
Test-Check "Resources requests and limits are configured" { $values -match "requests:" -and $values -match "limits:" }
Test-Check "Autoscaling is configurable" { $values -match "autoscaling:" -and $values -match "targetCPUUtilizationPercentage" -and $values -match "targetMemoryUtilizationPercentage" }
Test-Check "NetworkPolicy restricts ingress and egress" { $networkPolicy -match "policyTypes" -and $networkPolicy -match "Ingress" -and $networkPolicy -match "Egress" }
Test-Check "ServiceAccount token is disabled by default" { $values -match "automountServiceAccountToken: false" }
Test-Check "No PVC template is present because application is stateless" { -not (Test-Path (Join-Path $templates "pvc.yaml")) }

if ($failures.Count -gt 0) {
    Write-Error ("Kubernetes asset verification failed: " + ($failures -join "; "))
    exit 1
}

Write-Host "Kubernetes asset verification passed." -ForegroundColor Green
