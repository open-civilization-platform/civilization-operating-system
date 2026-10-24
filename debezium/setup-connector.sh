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
printf '\nConnector status:\n'
curl --fail --silent --show-error "$CONNECTOR_URL/civos-postgres-connector/status"
printf '\n'
