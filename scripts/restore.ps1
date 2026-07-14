# Print Quota Management System - Restore Script
# Powershell script to restore PostgreSQL database and certificates from an archive.

param (
    [Parameter(Mandatory=$true)]
    [string]$DbBackupFile
)

if (!(Test-Path $DbBackupFile)) {
    Write-Error "Backup file not found: $DbBackupFile"
    exit 1
}

Write-Host "1. Validating PostgreSQL database container is active..."
$dbStatus = docker inspect --format='{{.State.Running}}' print-quota-db 2>$null
if ($dbStatus -ne "true") {
    Write-Error "PostgreSQL container 'print-quota-db' is not running. Please start it using 'docker compose up print-quota-db -d'"
    exit 1
}

Write-Host "2. Restoring database schema and data..."
Get-Content $DbBackupFile | docker exec -i print-quota-db psql -U printuser -d printquota
if ($LASTEXITCODE -eq 0) {
    Write-Host "Database restoration complete."
} else {
    Write-Error "Database restoration failed."
}

Write-Host "Restoration procedure complete."
