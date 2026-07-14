# Print Quota Management System - Backup Script
# Powershell script to backup database schema, named volumes, and certificates.

$backupDir = "./backup_archive"
if (!(Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$dbBackupPath = "$backupDir/printquota_db_$timestamp.sql"

Write-Host "1. Starting PostgreSQL database dump..."
docker exec print-quota-db pg_dumpall -c -U printuser > $dbBackupPath
if ($LASTEXITCODE -eq 0) {
    Write-Host "Database backup complete: $dbBackupPath"
} else {
    Write-Warning "PostgreSQL backup failed."
}

Write-Host "2. Copying SSL certificates..."
$certDest = "$backupDir/certs_$timestamp"
if (Test-Path "./docker/certs") {
    Copy-Item -Path "./docker/certs" -Destination $certDest -Recurse -Force
    Write-Host "Certificates backup complete: $certDest"
} else {
    Write-Host "No local certs folder found. Skipping certificates backup."
}

Write-Host "3. Archiving logs directory..."
$logDest = "$backupDir/logs_$timestamp"
if (Test-Path "./logs") {
    Copy-Item -Path "./logs" -Destination $logDest -Recurse -Force
    Write-Host "Logs backup complete: $logDest"
} else {
    Write-Host "No local logs folder found."
}

Write-Host "Backup procedure complete."
