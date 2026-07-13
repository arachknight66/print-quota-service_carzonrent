#!/usr/bin/bash
set -Eeuo pipefail

java ${JAVA_OPTS:-} -jar /opt/carzonrent/runtime/qa-dashboard.jar &
spring_pid=$!

cleanup() {
    kill "${spring_pid}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for attempt in {1..120}; do
    if curl --fail --silent http://127.0.0.1:8085/health >/dev/null; then
        exec "$@"
    fi
    if ! kill -0 "${spring_pid}" 2>/dev/null; then
        echo "Spring Boot exited before becoming ready" >&2
        exit 1
    fi
    sleep 0.5
done

echo "Spring Boot failed to become ready on 127.0.0.1:8085" >&2
exit 1
