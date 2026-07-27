#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/fixtures/sandbox-boundary"
LAUNCHER="$ROOT/scripts/fixture-sandbox-launcher.sh"
BASELINE_SHA="$(sha256sum "$TARGET/protected.txt" | awk '{print $1}')"
ESCAPE_LINK="$TARGET/escape-link"

cleanup() {
  rm -f "$ESCAPE_LINK" /tmp/onsure-sandbox-timeout.out \
    /tmp/onsure-sandbox-cpu.out /tmp/onsure-sandbox-memory.out
}
trap cleanup EXIT

run_probe() {
  local probe="$1"
  local expected="$2"
  local output
  output="$(
    ONSURE_FIXTURE_TEST_MARKER=ALLOWED_MARKER \
    UNRELATED_SECRET_FOR_ONSURE_TEST=SHOULD_NOT_ESCAPE \
    bash "$LAUNCHER" "$TARGET" 10 bash sandbox-boundary-runner.sh "$probe"
  )"
  [[ "$output" == "$expected" || "$output" == "$expected"* ]] || {
    echo "SANDBOX_BOUNDARY_PROBE_FAIL $probe expected=$expected observed=$output" >&2
    exit 1
  }
  echo "SANDBOX_BOUNDARY_PROBE_PASS $probe $output"
}

run_probe source-read-only SOURCE_WRITE_BLOCKED
[[ "$(sha256sum "$TARGET/protected.txt" | awk '{print $1}')" == "$BASELINE_SHA" ]] || {
  echo 'SANDBOX_SOURCE_MUTATED' >&2
  exit 1
}
run_probe tmp-writable TMP_WRITE_ALLOWED
run_probe network-egress NETWORK_EGRESS_BLOCKED
run_probe filesystem-escape FILESYSTEM_ESCAPE_BLOCKED
ln -s /etc/passwd "$ESCAPE_LINK"
run_probe symlink-escape SYMLINK_ESCAPE_BLOCKED
rm -f "$ESCAPE_LINK"
run_probe capabilities CAPABILITIES_DROPPED
run_probe environment ENVIRONMENT_FILTERED
run_probe limits RESOURCE_LIMITS_ENFORCED

start_seconds="$(date +%s)"
child_output="$(bash "$LAUNCHER" "$TARGET" 10 bash sandbox-boundary-runner.sh child-process)"
elapsed_seconds=$(( $(date +%s) - start_seconds ))
[[ "$child_output" == 'CHILD_STARTED' && "$elapsed_seconds" -lt 5 ]] || {
  echo "SANDBOX_CHILD_PROCESS_NOT_TERMINATED output=$child_output elapsed=$elapsed_seconds" >&2
  exit 1
}
sleep 1
if pgrep -f 'onsure-sandbox-child-probe' >/dev/null 2>&1; then
  echo 'SANDBOX_CHILD_PROCESS_SURVIVED' >&2
  pkill -f 'onsure-sandbox-child-probe' || true
  exit 1
fi
echo 'SANDBOX_BOUNDARY_PROBE_PASS child-process CHILD_PROCESS_TERMINATED'

set +e
bash "$LAUNCHER" "$TARGET" 1 bash sandbox-boundary-runner.sh cpu-exhaustion \
  > /tmp/onsure-sandbox-cpu.out 2>&1
cpu_status=$?
set -e
[[ $cpu_status -eq 124 || $cpu_status -eq 137 ]] || {
  echo "SANDBOX_CPU_LIMIT_NOT_ENFORCED status=$cpu_status output=$(cat /tmp/onsure-sandbox-cpu.out)" >&2
  exit 1
}
echo 'SANDBOX_BOUNDARY_PROBE_PASS cpu-exhaustion CPU_LIMIT_ENFORCED'

set +e
bash "$LAUNCHER" "$TARGET" 10 bash sandbox-boundary-runner.sh memory-exhaustion \
  > /tmp/onsure-sandbox-memory.out 2>&1
memory_status=$?
set -e
[[ $memory_status -ne 0 ]] || {
  echo 'SANDBOX_MEMORY_LIMIT_NOT_ENFORCED' >&2
  exit 1
}
! grep -q MEMORY_LIMIT_NOT_ENFORCED /tmp/onsure-sandbox-memory.out || {
  echo 'SANDBOX_MEMORY_LIMIT_BYPASSED' >&2
  exit 1
}
echo "SANDBOX_BOUNDARY_PROBE_PASS memory-exhaustion MEMORY_LIMIT_ENFORCED status=$memory_status"

set +e
ONSURE_FIXTURE_TEST_MARKER=ALLOWED_MARKER \
  bash "$LAUNCHER" "$TARGET" 1 bash sandbox-boundary-runner.sh timeout \
  > /tmp/onsure-sandbox-timeout.out 2>&1
timeout_status=$?
set -e
[[ $timeout_status -eq 124 || $timeout_status -eq 137 ]] || {
  echo "SANDBOX_TIMEOUT_NOT_ENFORCED status=$timeout_status output=$(cat /tmp/onsure-sandbox-timeout.out)" >&2
  exit 1
}
! grep -q TIMEOUT_NOT_ENFORCED /tmp/onsure-sandbox-timeout.out || {
  echo 'SANDBOX_TIMEOUT_OUTPUT_LEAKED' >&2
  exit 1
}
echo 'SANDBOX_BOUNDARY_PROBE_PASS timeout WALL_CLOCK_TIMEOUT_ENFORCED'

echo 'ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12'
