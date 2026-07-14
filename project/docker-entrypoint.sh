#!/usr/bin/bash
set -Eeuo pipefail

spring_pid=""
apache_pid=""

log() {
    printf '[entrypoint] %s\n' "$*"
}

shutdown() {
    log "Shutdown requested; stopping Apache and Spring Boot"
    if [[ -n "${apache_pid}" ]] && kill -0 "${apache_pid}" 2>/dev/null; then
        kill -TERM "${apache_pid}" 2>/dev/null || true
    fi
    if [[ -n "${spring_pid}" ]] && kill -0 "${spring_pid}" 2>/dev/null; then
        kill -TERM "${spring_pid}" 2>/dev/null || true
    fi
    wait 2>/dev/null || true
}

trap shutdown EXIT INT TERM

log "Starting Spring Boot on ${SERVER_ADDRESS:-127.0.0.1}:${SERVER_PORT:-8085}"
java ${JAVA_OPTS:-} -jar /opt/carzonrent/runtime/qa-dashboard.jar &
spring_pid=$!

for attempt in {1..120}; do
    if curl --fail --silent http://127.0.0.1:8085/health >/dev/null; then
        log "Spring Boot is ready; validating Apache configuration"
        httpd -t
        log "Starting Apache in foreground mode"
        "$@" &
        apache_pid=$!
        wait -n "${spring_pid}" "${apache_pid}"
        exit_code=$?
        log "A managed process exited with status ${exit_code}; stopping remaining process"
        exit "${exit_code}"
    fi
    if ! kill -0 "${spring_pid}" 2>/dev/null; then
        echo "Spring Boot exited before becoming ready" >&2
        exit 1
    fi
    sleep 0.5
done

echo "Spring Boot failed to become ready on 127.0.0.1:8085" >&2
exit 1
