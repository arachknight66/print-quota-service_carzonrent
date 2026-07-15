#!/bin/bash
# =====================================================================
# Print Quota Management System - PostgreSQL Streaming Replication Setup
# Host Role: REPLICA (Standby worker VM)
# Last Updated: 2026-07
# =====================================================================
set -euo pipefail

PRIMARY_IP="<PRIMARY_IP>" # Replace with actual Primary database VM IP
PG_VERSION="16"
PG_DATA="/var/lib/postgresql/${PG_VERSION}/main"

echo "1. Stopping local PostgreSQL service..."
sudo systemctl stop postgresql

echo "2. Backing up existing cluster directory..."
sudo mv "${PG_DATA}" "${PG_DATA}.bak_$(date +%Y%m%d_%H%m%s)"

echo "3. Pulling base backup from primary database host..."
# Prompts for 'replicator' role password ('ReplicaSecurePassword123' configured in primary-setup.sh)
# Passes -R parameter to automatically write standby.signal and primary connection parameters (recovery.conf equivalent in PG16)
sudo -u postgres pg_basebackup \
  -h "${PRIMARY_IP}" \
  -D "${PG_DATA}" \
  -U replicator \
  -P \
  -v \
  -R \
  -X stream \
  -Fp

echo "4. Restoring directory ownership and permissions..."
sudo chown -R postgres:postgres "${PG_DATA}"
sudo chmod 700 "${PG_DATA}"

echo "5. Starting replication standby cluster..."
sudo systemctl start postgresql

echo "Standby replication initialization completed. Verify process via pg_stat_wal_receiver."
