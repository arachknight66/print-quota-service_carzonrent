#!/bin/bash
# =====================================================================
# Print Quota Management System - Rolling App Deployment Script
# Role: Graceful rolling update of application containers across workers
# Usage: ./rolling-deploy.sh <image_tag>
# Last Updated: 2026-07
# =====================================================================
set -euo pipefail

IMAGE_TAG="${1:-}"
if [ -z "${IMAGE_TAG}" ]; then
    echo "ERROR: Please specify target image tag (e.g. print-quota-core:v1.1.0)" >&2
    exit 1
fi

APACHE_MANAGER="http://127.0.0.1:9000/balancer-manager"
WORKERS=("worker1" "worker2")
WORKER_IPS=("10.10.5.11" "10.10.5.12") # Replace with actual worker private IPs

# Helper to change status of balancer members in Apache httpd
apache_balancer_cmd() {
    local balancer="$1"
    local worker_url="$2"
    local status="$3" # "Disable" or "Enable"

    echo "Setting Apache balancer '${balancer}' member '${worker_url}' status to '${status}'..."
    # Sends GET query to balancer-manager on localhost:9000 to dynamically toggle member state
    if curl -s -f -G "${APACHE_MANAGER}" \
         --data-urlencode "b=${balancer}" \
         --data-urlencode "w=${worker_url}" \
         --data-urlencode "dw=${status}" > /dev/null; then
        echo "Successfully updated Apache balancer member status."
    else
        echo "Warning: Could not contact Apache balancer-manager. Confirm httpd service is active on localhost:9000." >&2
    fi
}

for i in "${!WORKERS[@]}"; do
    WORKER="${WORKERS[$i]}"
    IP="${WORKER_IPS[$i]}"
    echo "--------------------------------------------------"
    echo "Starting upgrade for ${WORKER} (${IP})..."
    echo "--------------------------------------------------"

    # 1. Drain node from Apache load balancer
    echo "Draining ${WORKER} from Apache load balancer..."
    apache_balancer_cmd "tcp_cluster" "tcp://${IP}:8080" "Disable"
    apache_balancer_cmd "http_cluster" "http://${IP}:8080" "Disable"
    sleep 5 # Wait for active connections to finish draining

    # 2. Rebuild/Restart container on worker node (via SSH)
    # Preservation pattern: keep old container name running on port 8082 as a fallback
    echo "Connecting to ${WORKER} via SSH to deploy image..."
    ssh -o ConnectTimeout=10 "admin@${IP}" <<EOF
      set -e
      # Pull/load new image
      # docker pull ${IMAGE_TAG}
      
      # Rename current container as backup
      if docker ps -a --format '{{.Names}}' | grep -Eq '^print-quota-app$'; then
          echo "Backing up active container..."
          docker rename print-quota-app print-quota-app-backup
          docker stop print-quota-app-backup
      fi

      # Start new container
      docker run -d --name print-quota-app \
        -p 8080:8080 \
        -v /var/log/printkeep:/var/log/printkeep \
        "${IMAGE_TAG}"
EOF

    # 3. Poll Actuator Health readiness endpoint on the worker node
    echo "Waiting for ${WORKER} to report readiness..."
    HEALTH_URL="http://${IP}:8081/actuator/health/readiness" # Assuming actuator runs on separate port/http port 8081
    MAX_ATTEMPTS=20
    ATTEMPT=1
    UP=false

    while [ ${ATTEMPT} -le ${MAX_ATTEMPTS} ]; do
        STATUS_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "${HEALTH_URL}" || echo "000")
        if [ "${STATUS_CODE}" = "200" ]; then
            echo "${WORKER} is READY."
            UP=true
            break
        fi
        echo "Attempt ${ATTEMPT}/${MAX_ATTEMPTS}: Status code ${STATUS_CODE}. Waiting 5 seconds..."
        sleep 5
        ATTEMPT=$((ATTEMPT + 1))
    done

    # 4. Rollback or Enable node
    if [ "${UP}" = "false" ]; then
        echo "ERROR: ${WORKER} failed health checks. Reverting node..." >&2
        ssh -o ConnectTimeout=10 "admin@${IP}" <<EOF
          docker stop print-quota-app || true
          docker rm print-quota-app || true
          if docker ps -a --format '{{.Names}}' | grep -Eq '^print-quota-app-backup$'; then
              docker rename print-quota-app-backup print-quota-app
              docker start print-quota-app
          fi
EOF
        echo "Rollback completed on ${WORKER}. Re-enabling in Apache..."
        apache_balancer_cmd "tcp_cluster" "tcp://${IP}:8080" "Enable"
        apache_balancer_cmd "http_cluster" "http://${IP}:8080" "Enable"
        exit 1
    else
        # Success — delete backup container and enable node in Apache
        echo "Deployment successful on ${WORKER}. Cleaning up backup..."
        ssh -o ConnectTimeout=10 "admin@${IP}" "docker rm print-quota-app-backup || true"
        
        echo "Re-enabling ${WORKER} in load balancer..."
        apache_balancer_cmd "tcp_cluster" "tcp://${IP}:8080" "Enable"
        apache_balancer_cmd "http_cluster" "http://${IP}:8080" "Enable"
    fi
done

echo "Rolling deployment completed successfully."
