#!/bin/bash
set -e

echo "===================================================================="
echo "      CARZONRENT STAGING CONTAINER INGRESS INITIALIZATION (PHASE 2)  "
echo "===================================================================="
echo "[SYSTEM] Hostname: $(hostname)"
echo "[SYSTEM] Active User: $(whoami)"
echo "[SYSTEM] Environment Variables:"
echo "         - APP_NAME                  : ${APP_NAME:-print-quota-service}"
echo "         - STAGE_ENV                 : ${STAGE_ENV:-QA-Phase2}"
echo "         - SERVER_PORT               : ${SERVER_PORT:-8085}"
echo "         - JAVA_OPTS                 : ${JAVA_OPTS}"
echo "         - LOG_LEVEL_APP             : ${LOG_LEVEL_APP:-DEBUG}"
echo "===================================================================="

# 1. Align Permissions and Group Sharing for printuser and apache
echo "[LINUX] Applying group memberships and filesystem permissions..."
# Ensure printuser is added to apache group dynamically if not already
usermod -a -G apache printuser 2>/dev/null || true

# Re-create directories and ensure group-writable permissions (775)
mkdir -p /app/logs /app/reports /app/spool /app/certs
chown -R printuser:apache /app /var/spool/print-quota 2>/dev/null || true
chmod -R 775 /app /var/spool/print-quota 2>/dev/null || true

# 2. Trap SIGTERM and pass down to Spring Boot in case Tini process group propagation is bypassed
# When tini terminates, it sends SIGTERM to the process group. We add a trap here as defense-in-depth.
cleanup() {
    echo "[SYSTEM] Received SIGTERM signal. Propagating shutdown..."
    if [ -n "$SPRING_PID" ]; then
        echo "[SPRING BOOT] Sending SIGTERM to PID $SPRING_PID..."
        kill -TERM "$SPRING_PID" 2>/dev/null || true
        wait "$SPRING_PID" 2>/dev/null || true
        echo "[SPRING BOOT] Clean exit completed."
    fi
    exit 0
}
trap cleanup SIGTERM SIGINT

# 3. Start Spring Boot in the background
echo "[SPRING BOOT] Spawning backend instance on 127.0.0.1:8085..."
if [ "$(id -u)" = "0" ]; then
    runuser -u printuser -- java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-qa}" > /dev/stdout 2>&1 &
else
    java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-qa}" > /dev/stdout 2>&1 &
fi
SPRING_PID=$!

# 4. Wait for Spring Boot backend to be fully responsive
echo "[HEALTH CHECK] Waiting for backend to start reporting health state..."
HEALTH_URL="http://127.0.0.1:8085/actuator/health/readiness"
MAX_ATTEMPTS=45
ATTEMPT=0
SUCCESS=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    if curl -s -f "$HEALTH_URL" > /dev/null; then
        echo "[HEALTH CHECK] Backend verified as UP."
        SUCCESS=true
        break
    fi
    # Check if process is still running
    if ! kill -0 $SPRING_PID 2>/dev/null; then
        echo "[ERROR] Spring Boot backend crashed during startup initialization."
        exit 1
    fi
    ATTEMPT=$((ATTEMPT + 1))
    sleep 1
done

if [ "$SUCCESS" = false ]; then
    echo "[ERROR] Spring Boot backend failed to become healthy within $MAX_ATTEMPTS seconds."
    exit 1
fi

# 5. Hand over to Apache HTTP Server in the foreground
# Clean up stale Apache pid files to ensure clean startup
rm -f /run/httpd/httpd.pid

echo "[APACHE] Starting reverse proxy gateway in foreground..."
# Exec replaces the shell process, making httpd a direct child of tini (PID 1)
exec httpd -DFOREGROUND
