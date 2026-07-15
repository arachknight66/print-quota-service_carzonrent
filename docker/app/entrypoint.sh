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

# 2. Trap signals to ensure clean shutdown of background Apache and foreground Java
cleanup() {
    echo "[SYSTEM] Received shutdown signal. Propagating shutdown..."
    apachectl stop || true
    if [ -n "${SPRING_PID:-}" ]; then
        kill -TERM "$SPRING_PID" 2>/dev/null || true
        wait "$SPRING_PID" 2>/dev/null || true
    fi
    exit 0
}
trap cleanup SIGTERM SIGINT

# 3. Start Apache HTTP Server first
echo "[APACHE] Starting reverse proxy gateway..."
rm -f /run/httpd/httpd.pid
/usr/sbin/httpd

# 4. Start Spring Boot second
echo "[SPRING BOOT] Spawning backend instance on 127.0.0.1:8085..."
if [ "$(id -u)" = "0" ]; then
    # Start Spring Boot in foreground, replacing the shell process
    exec runuser -u printuser -- java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-qa}"
else
    exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-qa}"
fi
