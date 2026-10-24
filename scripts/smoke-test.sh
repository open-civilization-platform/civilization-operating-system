#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

APP_BASE_URL="${APP_BASE_URL:-http://localhost:8080}"
WORKER_BASE_URL="${WORKER_BASE_URL:-http://localhost:9090}"
FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:80}"
CONNECT_BASE_URL="${CONNECT_BASE_URL:-http://localhost:8083}"
READINESS_TIMEOUT_SECONDS="${READINESS_TIMEOUT_SECONDS:-180}"
PROJECT_PREFIX="${SMOKE_TEST_PROJECT:-civos-smoke}"
PROJECT_NAME="${PROJECT_PREFIX}-$(date +%s)-$$"
LOG_FILE="$REPO_ROOT/artifacts/smoke-test/compose.log"
to_windows_path() {
  if command -v wslpath >/dev/null 2>&1; then wslpath -w "$1"
  elif command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"
  else echo 'No WSL/Git Bash path converter is available for the Windows Docker/Node executable' >&2; return 1; fi
}
DOCKER_IS_WINDOWS=0
if [[ -n "${DOCKER_BIN:-}" ]]; then
  DOCKER=("$DOCKER_BIN")
  if [[ "$DOCKER_BIN" == *.exe ]]; then DOCKER_IS_WINDOWS=1; fi
elif command -v docker.exe >/dev/null 2>&1 && { [[ -n "${WSL_DISTRO_NAME:-}" ]] || grep -qi microsoft /proc/version 2>/dev/null || [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* || "$(uname -s)" == CYGWIN* ]]; }; then
  DOCKER=(docker.exe)
  DOCKER_IS_WINDOWS=1
else
  DOCKER=(docker)
fi
DOCKER_PROJECT_DIR="$REPO_ROOT"
DOCKER_COMPOSE_FILE="$REPO_ROOT/docker-compose.yml"
if [[ "$DOCKER_IS_WINDOWS" -eq 1 ]]; then
  DOCKER_PROJECT_DIR="$(to_windows_path "$REPO_ROOT")"
  DOCKER_COMPOSE_FILE="$(to_windows_path "$REPO_ROOT/docker-compose.yml")"
fi
COMPOSE=("${DOCKER[@]}" compose --project-name "$PROJECT_NAME" --file "$DOCKER_COMPOSE_FILE" --project-directory "$DOCKER_PROJECT_DIR")
COMPOSE_STARTED=0
WORK_DIR=""

if [[ ! "$PROJECT_NAME" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
  echo 'SMOKE_TEST_PROJECT must contain only lowercase letters, digits, underscores, and hyphens' >&2
  exit 2
fi
if ! [[ "$READINESS_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo 'READINESS_TIMEOUT_SECONDS must be a positive integer' >&2
  exit 2
fi

fixed_containers=(
  civos-zookeeper civos-kafka civos-debezium civos-postgres civos-redis
  civos-app civos-app-primary civos-cortex-worker civos-frontend civos-nginx
)
existing_containers="$("${DOCKER[@]}" ps --all --format '{{.Names}}')"
for fixed_name in "${fixed_containers[@]}"; do
  if grep -Fxq "$fixed_name" <<< "$existing_containers"; then
    echo "Smoke test cannot start: fixed Compose container '$fixed_name' already exists. Stop or remove it yourself, then retry." >&2
    exit 1
  fi
done
existing_project_volumes="$("${DOCKER[@]}" volume ls \
  --filter "label=com.docker.compose.project=$PROJECT_NAME" --format '{{.Name}}')"
if [[ -n "$existing_project_volumes" ]]; then
  echo "Smoke test cannot start: Compose project '$PROJECT_NAME' already owns persistent volume(s): $existing_project_volumes. Choose a new SMOKE_TEST_PROJECT; existing data was left untouched." >&2
  exit 1
fi

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ "$COMPOSE_STARTED" -eq 1 ]]; then
    if [[ "$status" -ne 0 ]]; then
      mkdir -p "$(dirname "$LOG_FILE")"
      "${COMPOSE[@]}" logs --no-color > "$LOG_FILE" 2>&1 || true
      echo "Smoke test failed; Compose logs saved to $LOG_FILE" >&2
    fi
    "${COMPOSE[@]}" down --volumes --remove-orphans || status=1
  fi
  if [[ -n "$WORK_DIR" ]]; then rm -rf "$WORK_DIR"; fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! "${DOCKER[@]}" compose version >/dev/null 2>&1; then
  echo 'Docker Compose v2 is required' >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo 'curl is required' >&2
  exit 1
fi
if [[ -n "${NODE_BIN:-}" ]]; then
  NODE=("$NODE_BIN")
  [[ "$NODE_BIN" == *.exe ]] && NODE_IS_WINDOWS=1 || NODE_IS_WINDOWS=0
elif command -v node >/dev/null 2>&1; then
  NODE=(node)
  NODE_IS_WINDOWS=0
elif command -v node.exe >/dev/null 2>&1; then
  NODE=(node.exe)
  NODE_IS_WINDOWS=1
else
  echo 'Node.js 24 or newer is required' >&2
  exit 1
fi
node_path() {
  if [[ "$NODE_IS_WINDOWS" -eq 1 ]]; then to_windows_path "$1"; else printf '%s' "$1"; fi
}
"${NODE[@]}" -e 'if (Number(process.versions.node.split(".")[0]) < 24) process.exit(1)' || {
  echo 'Node.js 24 or newer is required for the built-in WebSocket client' >&2
  exit 1
}

WORK_DIR="$(mktemp -d "$REPO_ROOT/.smoke-test.XXXXXX")"
echo "Starting disposable Compose project '$PROJECT_NAME'..."
COMPOSE_STARTED=1
"${COMPOSE[@]}" up --build --detach

echo 'Waiting for backend, worker, frontend, and Debezium readiness...'
wait_for_http() {
  local name="$1" url="$2" deadline=$((SECONDS + READINESS_TIMEOUT_SECONDS)) response='no response yet'
  while (( SECONDS < deadline )); do
    if response="$(curl --silent --show-error --max-time 5 --fail --output /dev/null --write-out 'HTTP %{http_code}' "$url" 2>&1)"; then
      printf 'PASS: %s ready (%s)\n' "$name" "$url"
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for $name at $url after ${READINESS_TIMEOUT_SECONDS}s; last response/error: $response" >&2
  return 1
}

wait_for_http 'backend actuator health' "$APP_BASE_URL/actuator/health"
wait_for_http 'worker health' "$WORKER_BASE_URL/api/v1/worker/health"
wait_for_http 'frontend through Nginx' "$FRONTEND_BASE_URL/"
wait_for_http 'Debezium Connect registry' "$CONNECT_BASE_URL/connectors"

printf 'Authenticating smoke-test client...\n'
curl --silent --show-error --fail --max-time 10 --request POST \
  --output "$(node_path "$WORK_DIR/auth.json")" "$APP_BASE_URL/api/v1/auth/connect"
SMOKE_TEST_JWT="$("${NODE[@]}" --input-type=module -e 'import fs from "node:fs"; const body = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); if (!body.token) throw new Error("auth response did not include token"); process.stdout.write(body.token);' "$(node_path "$WORK_DIR/auth.json")")"
export SMOKE_TEST_JWT APP_BASE_URL

CIVILIZATION_NAME="CivOS-Smoke-$(date -u +%Y%m%dT%H%M%SZ)-$$-${RANDOM}"
export CIVILIZATION_NAME
"${NODE[@]}" --input-type=module -e 'import fs from "node:fs"; fs.writeFileSync(process.argv[1], JSON.stringify({ name: process.env.CIVILIZATION_NAME, scale: "LOCAL", region: "smoke-test" }));' "$(node_path "$WORK_DIR/civilization-request.json")"
curl --silent --show-error --fail --max-time 15 --request POST \
  --header "Authorization: Bearer $SMOKE_TEST_JWT" \
  --header 'Content-Type: application/json' \
  --data-binary "@$WORK_DIR/civilization-request.json" \
  --output "$(node_path "$WORK_DIR/civilization.json")" "$APP_BASE_URL/api/v1/civilizations"
"${NODE[@]}" --input-type=module -e 'import fs from "node:fs"; const body = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); if (!body.id || body.name !== process.env.CIVILIZATION_NAME) throw new Error("REST did not return the created civilization ID and name"); process.env.CIVILIZATION_ID = String(body.id);' "$(node_path "$WORK_DIR/civilization.json")"
CIVILIZATION_ID="$("${NODE[@]}" --input-type=module -e 'import fs from "node:fs"; process.stdout.write(String(JSON.parse(fs.readFileSync(process.argv[1], "utf8")).id));' "$(node_path "$WORK_DIR/civilization.json")")"
export CIVILIZATION_ID
"${NODE[@]}" --input-type=module -e 'import fs from "node:fs"; fs.writeFileSync(process.argv[1], JSON.stringify({query: "query { civilizations(page: 0, size: 100) { content { id name } } }"}));' "$(node_path "$WORK_DIR/graphql-request.json")"
curl --silent --show-error --fail --max-time 15 --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$WORK_DIR/graphql-request.json" \
  --output "$(node_path "$WORK_DIR/graphql-response.json")" "$APP_BASE_URL/graphql"
"${NODE[@]}" --input-type=module -e 'import fs from "node:fs"; const body = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); if (body.errors?.length) throw new Error("GraphQL returned errors"); const found = body.data?.civilizations?.content?.some((civ) => String(civ.id) === process.env.CIVILIZATION_ID && civ.name === process.env.CIVILIZATION_NAME); if (!found) throw new Error("GraphQL did not return the exact civilization created through REST");' "$(node_path "$WORK_DIR/graphql-response.json")"
printf 'PASS: REST-created civilization %s is visible through GraphQL\n' "$CIVILIZATION_NAME"

"${NODE[@]}" --input-type=module -e 'import { connectNexusWebSocket } from "./scripts/smoke-test-websocket.mjs"; await connectNexusWebSocket(process.env.APP_BASE_URL, process.env.SMOKE_TEST_JWT);'
echo 'PASS: authenticated Nexus WebSocket handshake'

CONNECT_BASE_URL="$CONNECT_BASE_URL" bash "$REPO_ROOT/debezium/setup-connector.sh"
curl --silent --show-error --fail --max-time 10 \
  --output "$(node_path "$WORK_DIR/connectors.json")" "$CONNECT_BASE_URL/connectors"
"${NODE[@]}" --input-type=module -e 'import fs from "node:fs"; const names = JSON.parse(fs.readFileSync(process.argv[1], "utf8")); if (!names.includes("civos-postgres-connector")) throw new Error("Debezium connector registry is missing civos-postgres-connector");' "$(node_path "$WORK_DIR/connectors.json")"
echo 'PASS: civos-postgres-connector is registered'
echo 'Full-stack smoke test passed.'
