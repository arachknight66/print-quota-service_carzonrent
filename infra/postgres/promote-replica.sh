#!/bin/bash
# =====================================================================
# Print Quota Management System - Standby Replica Database Promotion
# Role: Standby Replica VM (CentOS Stream 9)
# Objective: Scripted, idempotent promotion of replica database to primary
# Last Updated: 2026-07
# =====================================================================
set -euo pipefail

PG_VERSION="16"
PG_DATA="/var/lib/postgresql/${PG_VERSION}/main"

echo "1. Checking current database recovery state..."
# pg_is_in_recovery() returns 't' (true) if the database is in hot standby mode
IS_RECOVERY=$(sudo -u postgres psql -t -A -c "SELECT pg_is_in_recovery();" 2>/dev/null || echo "error")

if [ "${IS_RECOVERY}" = "f" ]; then
    echo "Database is already operating in Primary/Write mode. No promotion needed."
    exit 0
elif [ "${IS_RECOVERY}" = "error" ]; then
    echo "ERROR: PostgreSQL appears to be stopped or unreachable. Ensure service is running before promoting." >&2
    exit 1
fi

echo "Database is in Hot Standby. Initiating promotion..."

# Idempotent promotion command using pg_ctl promote
if sudo -u postgres pg_ctl promote -D "${PG_DATA}"; then
    echo "Promotion command dispatched. Verifying state..."
    sleep 3
    
    # Assert recovery is now false
    IS_RECOVERY_NOW=$(sudo -u postgres psql -t -A -c "SELECT pg_is_in_recovery();")
    if [ "${IS_RECOVERY_NOW}" = "f" ]; then
        echo "SUCCESS: Database successfully promoted to Primary. Write operations are enabled."
        exit 0
    else
        echo "ERROR: Database promotion was triggered but database remains in Standby mode." >&2
        exit 1
    fi
else
    echo "ERROR: Failed to execute pg_ctl promote command." >&2
    exit 1
fi
