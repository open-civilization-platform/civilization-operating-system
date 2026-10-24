#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOST="${1:-localhost}"
CONNECT_BASE_URL="${CONNECT_BASE_URL:-http://${HOST}:8083}"
if [[ "$CONNECT_BASE_URL" == */connectors ]]; then
  CONNECTOR_URL="$CONNECT_BASE_URL"
else
  CONNECTOR_URL="${CONNECT_BASE_URL%/}/connectors"
fi

printf 'Registering Debezium PostgreSQL connector at %s...\n' "$CONNECTOR_URL"
curl --fail --silent --show-error --request POST "$CONNECTOR_URL" \
  --header 'Content-Type: application/json' \
  --data-binary "@$REPO_ROOT/debezium/connector.json"
printf '\nWaiting for connector status...\n'
STATUS_URL="$CONNECTOR_URL/civos-postgres-connector/status"
for attempt in {1..30}; do
  status_code="$(curl --silent --output /dev/null --write-out '%{http_code}' "$STATUS_URL" || true)"
  if [[ "$status_code" == 200 ]]; then
    printf 'Connector status:\n'
    curl --fail --silent --show-error "$STATUS_URL"
    printf '\n'
    exit 0
  fi
  sleep 1
done

echo "Timed out waiting for connector status at $STATUS_URL (last HTTP status: ${status_code:-no response})" >&2
exit 1
