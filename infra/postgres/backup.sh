#!/bin/bash
# =====================================================================
# Print Quota Management System - Database & Directory Backup Script
# Platform: CentOS Stream 9 (Master VM)
# Last Updated: 2026-07
# =====================================================================
set -euo pipefail

BACKUP_DIR="./backup_archive"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DB_BACKUP_PATH="${BACKUP_DIR}/printquota_db_${TIMESTAMP}.sql"
CERT_DEST="${BACKUP_DIR}/certs_${TIMESTAMP}"
LOG_DEST="${BACKUP_DIR}/logs_${TIMESTAMP}"

# Create backup archive directory if missing
mkdir -p "${BACKUP_DIR}"

echo "1. Starting PostgreSQL database dump..."
# Perform database schema and data dump (assuming local pg_dumpall access)
if pg_dumpall -c -U printuser -h localhost > "${DB_BACKUP_PATH}" 2>/dev/null; then
    echo "Database backup complete: ${DB_BACKUP_PATH}"
else
    echo "WARNING: Local pg_dumpall command failed, attempting via Docker container fallback..."
    if docker exec print-quota-db pg_dumpall -c -U printuser > "${DB_BACKUP_PATH}" 2>/dev/null; then
        echo "Database backup complete (via Docker): ${DB_BACKUP_PATH}"
    else
        echo "ERROR: PostgreSQL backup failed." >&2
        exit 1
    fi
fi

echo "2. Copying SSL certificates..."
if [ -d "./docker/certs" ]; then
    cp -r "./docker/certs" "${CERT_DEST}"
    echo "Certificates backup complete: ${CERT_DEST}"
else
    echo "No local certs folder found. Skipping certificates backup."
fi

echo "3. Archiving logs directory..."
if [ -d "./logs" ]; then
    cp -r "./logs" "${LOG_DEST}"
    echo "Logs backup complete: ${LOG_DEST}"
else
    echo "No local logs folder found."
fi

echo "Backup procedure complete."
EOF
