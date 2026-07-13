#!/usr/bin/bash
set -Eeuo pipefail

python3 /opt/carzonrent/backend/server.py &
backend_pid=$!

cleanup() {
    kill "${backend_pid}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for attempt in {1..20}; do
    if curl --fail --silent http://127.0.0.1:8085/health >/dev/null; then
        exec "$@"
    fi
    sleep 0.25
done

echo "Backend failed to become ready on 127.0.0.1:8085" >&2
exit 1
