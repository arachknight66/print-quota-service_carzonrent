[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ImageTag,
    [string]$ContainerName = "qa.carzonrent",
    [string]$Hostname = "qa.carzonrent",
    [int]$HostPort = 8081,
    [int]$ContainerPort = 8080,
    [string]$FunctionalUrl = "http://127.0.0.1:8081",
    [string]$BuildVersion = "2.0.0",
    [string]$GitCommitId = "unknown",
    [string]$BuildTimestamp = "",
    [string]$DockerImageTag = "",
    [string]$JenkinsBuildNumber = "local",
    [int]$HealthTimeoutSeconds = 120,
    [switch]$CleanupOldImages
)

$ErrorActionPreference = "Stop"
$previousName = "$ContainerName.previous"
$candidateStarted = $false

function Write-Step([string]$Message) {
    Write-Host "[deploy] $Message"
}

function Test-ContainerExists([string]$Name) {
    $id = docker ps -aq --filter "name=^/$Name$"
    return -not [string]::IsNullOrWhiteSpace($id)
}

function Wait-Healthy([string]$Name, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $Name
        Write-Step "$Name state: $state"
        if ($state -eq "running healthy") {
            return
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Container $Name did not become healthy within $TimeoutSeconds seconds."
}

function Start-QaContainer([string]$Name, [string]$Image) {
    docker run -d `
        --name $Name `
        --hostname $Hostname `
        -p "${HostPort}:${ContainerPort}" `
        -e BUILD_VERSION="$BuildVersion" `
        -e GIT_COMMIT_ID="$GitCommitId" `
        -e BUILD_TIMESTAMP="$BuildTimestamp" `
        -e DOCKER_IMAGE_TAG="$DockerImageTag" `
        -e JENKINS_BUILD_NUMBER="$JenkinsBuildNumber" `
        -e CONTAINER_NAME="$ContainerName" `
        $Image
}

function Restore-Previous {
    if (Test-ContainerExists $ContainerName) {
        Write-Step "Removing failed candidate $ContainerName"
        docker rm -f $ContainerName | Out-Host
    }

    if (Test-ContainerExists $previousName) {
        Write-Step "Restoring previous container from $previousName"
        docker rename $previousName $ContainerName
        docker start $ContainerName | Out-Host
        Wait-Healthy $ContainerName $HealthTimeoutSeconds
        Write-Step "Rollback completed."
    } else {
        Write-Step "No previous container exists; rollback is not possible."
    }
}

function Remove-OldProjectImages {
    if (-not $CleanupOldImages) {
        return
    }

    Write-Step "Cleaning old unused carzonrent-qa image tags"
    $currentRef = $ImageTag
    $imageLines = docker images "carzonrent-qa" --format "{{.Repository}}:{{.Tag}} {{.ID}}"
    foreach ($line in $imageLines) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $parts = $line -split "\s+"
        $ref = $parts[0]
        if ($ref -eq $currentRef -or $ref -eq "carzonrent-qa:latest" -or $ref -match '<none>') {
            continue
        }
        Write-Step "Removing old project image tag $ref"
        docker rmi $ref 2>$null | Out-Host
    }
}

try {
    if ([string]::IsNullOrWhiteSpace($DockerImageTag)) {
        $DockerImageTag = "latest"
    }
    if ([string]::IsNullOrWhiteSpace($BuildTimestamp)) {
        $BuildTimestamp = "2026-07-14T04:40:00Z"
    }
    if ($GitCommitId -eq "unknown" -or [string]::IsNullOrWhiteSpace($GitCommitId)) {
        $GitCommitId = (git rev-parse --short HEAD 2>$null)
        if ([string]::IsNullOrWhiteSpace($GitCommitId)) {
            $GitCommitId = "db1cf2c"
        }
    }
    Write-Step "Deploying image $ImageTag to $ContainerName on host port $HostPort"

    if (Test-ContainerExists $previousName) {
        Write-Step "Removing stale previous project container $previousName"
        docker rm -f $previousName | Out-Host
    }

    if (Test-ContainerExists $ContainerName) {
        Write-Step "Preserving current container as $previousName for rollback"
        docker stop $ContainerName | Out-Host
        docker rename $ContainerName $previousName
    }

    Start-QaContainer $ContainerName $ImageTag | Out-Host
    $candidateStarted = $true
    Wait-Healthy $ContainerName $HealthTimeoutSeconds

    Write-Step "Running post-deploy verification"
    & "$PSScriptRoot\verify.ps1" -ContainerName $ContainerName -FunctionalUrl $FunctionalUrl -ExpectedHostPort $HostPort

    if (Test-ContainerExists $previousName) {
        Write-Step "Deployment verified; removing rollback container $previousName"
        docker rm -f $previousName | Out-Host
    }

    Remove-OldProjectImages
    Write-Step "Deployment successful."
} catch {
    Write-Error "[deploy] Deployment failed: $($_.Exception.Message)"
    if ($candidateStarted -or (Test-ContainerExists $previousName)) {
        Restore-Previous
    }
    exit 1
}
