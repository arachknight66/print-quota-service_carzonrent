[CmdletBinding()]
param(
    [string]$Namespace = "carzonrent-qa",
    [string]$ReleaseName = "qa",
    [string]$IngressHost = "qa.carzonrent.com",
    [string]$ChartName = "carzonrent-qa"
)

$ErrorActionPreference = "Stop"
$failures = [System.Collections.Generic.List[string]]::new()
$deploymentName = "$ReleaseName-$ChartName"

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

function Invoke-Kubectl {
    param([string[]]$Arguments)
    & kubectl @Arguments
}

Write-Host "Kubernetes namespace: $Namespace"
Write-Host "Helm release: $ReleaseName"
Write-Host "Expected ingress host: $IngressHost"

Test-Check "kubectl is available" {
    kubectl version --client | Out-Null
    $LASTEXITCODE -eq 0
}
Test-Check "Namespace exists" {
    (Invoke-Kubectl @("get", "namespace", $Namespace, "-o", "jsonpath={.metadata.name}")) -eq $Namespace
}
Test-Check "Deployment exists" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "deployment", $deploymentName, "-o", "jsonpath={.metadata.name}")) -eq $deploymentName
}
Test-Check "Deployment rollout is complete" {
    kubectl -n $Namespace rollout status deployment/$deploymentName --timeout=120s | Out-Null
    $LASTEXITCODE -eq 0
}
Test-Check "ReplicaSet exists" {
    $replicasets = Invoke-Kubectl @("-n", $Namespace, "get", "rs", "-l", "app.kubernetes.io/instance=$ReleaseName", "-o", "name")
    -not [string]::IsNullOrWhiteSpace(($replicasets | Out-String))
}
Test-Check "Pods are ready" {
    $ready = Invoke-Kubectl @("-n", $Namespace, "get", "pods", "-l", "app.kubernetes.io/instance=$ReleaseName", "-o", "jsonpath={range .items[*]}{.status.containerStatuses[0].ready}{'\n'}{end}")
    ($ready | Where-Object { $_ -ne "true" } | Measure-Object).Count -eq 0
}
Test-Check "Service is ClusterIP" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "svc", $deploymentName, "-o", "jsonpath={.spec.type}")) -eq "ClusterIP"
}
Test-Check "Ingress host is configured" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "ingress", $deploymentName, "-o", "jsonpath={.spec.rules[0].host}")) -eq $IngressHost
}
Test-Check "HPA exists" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "hpa", $deploymentName, "-o", "jsonpath={.metadata.name}")) -eq $deploymentName
}
Test-Check "PDB exists" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "pdb", $deploymentName, "-o", "jsonpath={.metadata.name}")) -eq $deploymentName
}
Test-Check "NetworkPolicy exists" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "networkpolicy", $deploymentName, "-o", "jsonpath={.metadata.name}")) -eq $deploymentName
}
Test-Check "ConfigMap exists" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "configmap", "$deploymentName-config", "-o", "jsonpath={.metadata.name}")) -eq "$deploymentName-config"
}
Test-Check "Secret exists" {
    (Invoke-Kubectl @("-n", $Namespace, "get", "secret", "$deploymentName-secret", "-o", "jsonpath={.metadata.name}")) -eq "$deploymentName-secret"
}
Test-Check "Readiness endpoint works through service" {
    $pod = Invoke-Kubectl @("-n", $Namespace, "get", "pod", "-l", "app.kubernetes.io/instance=$ReleaseName", "-o", "jsonpath={.items[0].metadata.name}")
    $body = Invoke-Kubectl @("-n", $Namespace, "exec", $pod, "--", "curl", "-sS", "http://127.0.0.1:8080/health/readiness")
    $body -match '"status":"UP"'
}
Test-Check "Liveness endpoint works through service" {
    $pod = Invoke-Kubectl @("-n", $Namespace, "get", "pod", "-l", "app.kubernetes.io/instance=$ReleaseName", "-o", "jsonpath={.items[0].metadata.name}")
    $body = Invoke-Kubectl @("-n", $Namespace, "exec", $pod, "--", "curl", "-sS", "http://127.0.0.1:8080/health/liveness")
    $body -match '"status":"UP"'
}
Test-Check "Prometheus endpoint is available" {
    $pod = Invoke-Kubectl @("-n", $Namespace, "get", "pod", "-l", "app.kubernetes.io/instance=$ReleaseName", "-o", "jsonpath={.items[0].metadata.name}")
    $body = Invoke-Kubectl @("-n", $Namespace, "exec", $pod, "--", "curl", "-sS", "http://127.0.0.1:8080/prometheus")
    $body -match "jvm_memory_used_bytes"
}

if ($failures.Count -gt 0) {
    Write-Error ("Kubernetes verification failed: " + ($failures -join "; "))
    exit 1
}

Write-Host "Kubernetes verification passed." -ForegroundColor Green
