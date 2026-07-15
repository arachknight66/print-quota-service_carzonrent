#!/bin/bash
# =====================================================================
# Print Quota Management System - Apache Balancer Monitor Script
# Role: Alert on worker node status change (DOWN)
# Usage: Run via cron every 1 minute.
# Last Updated: 2026-07
# =====================================================================
set -euo pipefail

# Configurable alerting hook endpoint (Slack, Discord, MS Teams, SMTP Relay, etc.)
ALERT_WEBHOOK_URL="${PRINTKEEP_ALERT_WEBHOOK:-https://hooks.slack.com/services/YOUR/WEBHOOK/URL}"

# Apache Status URL (configured on localhost:9000/server-status in httpd.conf)
STATUS_URL="http://127.0.0.1:9000/server-status"

echo "Checking Apache Balancer members status..."

# Fetch HTML and look for balancer states.
# Active check failures (mod_proxy_hcheck) will mark BalancerMembers as 'Err' or 'Init Err'.
FAILS=$(curl -s -L --max-time 10 "${STATUS_URL}" | grep -Ei "worker|balancer" | grep -Ei "Err|Down" || true)

if [ -n "${FAILS}" ]; then
    MESSAGE="CRITICAL ALERT: Print Quota Apache Balancer Failures Detected!\\nFailed Members: ${FAILS}"
    echo "${MESSAGE}" >&2

    # Send payload to the configured alert channel
    if [[ "${ALERT_WEBHOOK_URL}" =~ ^https:// ]]; then
        curl -s -X POST -H 'Content-type: application/json' \
          --data "{\"text\":\"${MESSAGE}\"}" \
          "${ALERT_WEBHOOK_URL}" > /dev/null || echo "WARNING: Webhook alert dispatch failed." >&2
    else
        echo "Alert channel not configured (default webhook placeholder detected). Logging alert only." >&2
    fi
    exit 1
else
    echo "All Apache Balancer members are healthy."
fi
