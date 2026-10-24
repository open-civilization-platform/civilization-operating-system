#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT
cat > "$temp_dir/curl" <<'CURL'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CURL_CALLS"
exit 22
CURL
chmod +x "$temp_dir/curl"
export CURL_CALLS="$temp_dir/curl-calls.log"
: > "$CURL_CALLS"
set +e
PATH="$temp_dir:$PATH" bash "$repo_root/debezium/setup-connector.sh" localhost > "$temp_dir/stdout.log" 2>&1
status=$?
set -e
if [[ $status -eq 0 ]]; then
  echo 'FAIL: connector setup must fail when the registration HTTP request fails' >&2
  exit 1
fi
if ! grep -q -- '--fail' "$CURL_CALLS"; then
  echo 'FAIL: connector setup must request curl HTTP failure handling' >&2
  cat "$CURL_CALLS" >&2
  exit 1
fi
echo 'PASS: connector setup rejected the mocked non-success HTTP response'