#!/bin/bash
# =====================================================================
# Print Quota Management System - PostgreSQL Streaming Replication Setup
# Host Role: PRIMARY (Master CentOS VM)
# Last Updated: 2026-07
# =====================================================================
set -euo pipefail

REPLICA_IP="<REPLICA_IP>" # Replace with actual Standby VM IP
PG_VERSION="16"
PG_DATA="/var/lib/postgresql/${PG_VERSION}/main"
PG_CONF_DIR="/etc/postgresql/${PG_VERSION}/main"

echo "1. Configuring postgresql.conf parameters for streaming replication..."
sudo tee -a "${PG_CONF_DIR}/postgresql.conf" <<EOF

# --- Streaming Replication Configuration ---
listen_addresses = '*'
wal_level = replica
max_wal_senders = 10
max_replication_slots = 10
wal_keep_size = 1024MB # Reserve WAL logs to prevent replica falling behind
hot_standby = on
EOF

echo "2. Setting up replication user in database..."
# Creates a dedicated replication role with REPLICATION privilege
sudo -u postgres psql -c "CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD 'ReplicaSecurePassword123';"

echo "3. Updating pg_hba.conf to authorize replication connection from Standby VM..."
# Append HBA entry permitting replica IP to connect via replication protocol
sudo tee -a "${PG_CONF_DIR}/pg_hba.conf" <<EOF

# Standby replication connections
host    replication     replicator      ${REPLICA_IP}/32         scram-sha-256
EOF

echo "4. Restarting PostgreSQL service to apply replication parameters..."
sudo systemctl restart postgresql

echo "Primary configuration completed. Verify listening status and replication permissions."
