#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
runner="$repo_root/scripts/smoke-test.sh"
if [[ ! -f "$runner" ]]; then
  echo 'FAIL: scripts/smoke-test.sh is missing; it cannot reject the fixed container collision' >&2
  exit 1
fi
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
cat > "$temp_dir/docker" <<'DOCKER'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALLS"
case "$1 $2" in
  'ps --all') if [[ "${MOCK_FIXED:-0}" == 1 ]]; then printf '%s\n' 'civos-app'; fi ;;
  'volume ls')
    if [[ "${MOCK_VOLUME:-0}" == 1 ]]; then
      for argument in "$@"; do
        if [[ "$argument" == label=com.docker.compose.project=* ]]; then
          printf '%s\n' "${argument##*=}_pgdata"
        fi
      done
    fi
    ;;
  compose*)
    for argument in "$@"; do
      case "$argument" in
        version) exit 0 ;;
        up) exit 7 ;;
      esac
    done
    ;;
esac
DOCKER
chmod +x "$temp_dir/docker"
export DOCKER_CALLS="$temp_dir/docker-calls.log"
: > "$DOCKER_CALLS"
set +e
DOCKER_BIN="$temp_dir/docker" MOCK_FIXED=1 PATH="$temp_dir:$PATH" bash "$runner" > "$temp_dir/stdout.log" 2>&1
status=$?
set -e
if [[ $status -eq 0 ]]; then
  echo 'FAIL: smoke runner must reject an existing fixed container name' >&2
  exit 1
fi
if grep -Eq '^compose .* (up|down)( |$)|^volume (rm|prune)' "$DOCKER_CALLS"; then
  echo 'FAIL: smoke runner modified Compose resources after detecting a collision' >&2
  cat "$DOCKER_CALLS" >&2
  exit 1
fi
if ! grep -qi 'civos-app' "$temp_dir/stdout.log"; then
  echo 'FAIL: collision error did not identify the existing container' >&2
  cat "$temp_dir/stdout.log" >&2
  exit 1
fi
echo 'PASS: existing fixed container was rejected without Compose changes'

: > "$DOCKER_CALLS"
set +e
DOCKER_BIN="$temp_dir/docker" MOCK_VOLUME=1 SMOKE_TEST_PROJECT='civos-reused' \
  PATH="$temp_dir:$PATH" bash "$runner" > "$temp_dir/volume-stdout.log" 2>&1
status=$?
set -e
if [[ $status -eq 0 ]] || grep -Eq '^compose .* (up|down)( |$)' "$DOCKER_CALLS"; then
  echo 'FAIL: smoke runner must reject a project whose persistent volume already exists' >&2
  cat "$DOCKER_CALLS" >&2
  exit 1
fi
if ! grep -qi 'civos-reused' "$temp_dir/volume-stdout.log"; then
  echo 'FAIL: stale-volume error did not identify the existing project volume' >&2
  cat "$temp_dir/volume-stdout.log" >&2
  cat "$DOCKER_CALLS" >&2
  exit 1
fi
echo 'PASS: existing project volume was rejected without Compose changes'

DOCKER_BIN="$temp_dir/docker" SMOKE_TEST_PROJECT='civos-concurrent' \
  PATH="$temp_dir:$PATH" bash "$runner" > "$temp_dir/concurrent-one.log" 2>&1 || true
DOCKER_BIN="$temp_dir/docker" SMOKE_TEST_PROJECT='civos-concurrent' \
  PATH="$temp_dir:$PATH" bash "$runner" > "$temp_dir/concurrent-two.log" 2>&1 || true
project_one="$(sed -n "s/^Starting disposable Compose project '\([^']*\)'.*/\1/p" "$temp_dir/concurrent-one.log")"
project_two="$(sed -n "s/^Starting disposable Compose project '\([^']*\)'.*/\1/p" "$temp_dir/concurrent-two.log")"
if [[ -z "$project_one" || -z "$project_two" || "$project_one" == "$project_two" \
      || "$project_one" != civos-concurrent-* || "$project_two" != civos-concurrent-* ]]; then
  echo 'FAIL: SMOKE_TEST_PROJECT must be a prefix while each run keeps a unique Compose project name' >&2
  cat "$temp_dir/concurrent-one.log" "$temp_dir/concurrent-two.log" >&2
  exit 1
fi
echo 'PASS: concurrent override prefixes produce unique Compose project names'
