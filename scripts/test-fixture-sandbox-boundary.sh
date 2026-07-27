#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/fixtures/sandbox-boundary"
LAUNCHER="$ROOT/scripts/fixture-sandbox-launcher.sh"
BASELINE_SHA="$(sha256sum "$TARGET/protected.txt" | awk '{print $1}')"

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
run_probe capabilities CAPABILITIES_DROPPED
run_probe environment ENVIRONMENT_FILTERED
run_probe limits RESOURCE_LIMITS_ENFORCED

set +e
ONSURE_FIXTURE_TEST_MARKER=ALLOWED_MARKER \
  bash "$LAUNCHER" "$TARGET" 1 bash sandbox-boundary-runner.sh timeout \
  > /tmp/onsure-sandbox-timeout.out 2>&1
status=$?
set -e
[[ $status -eq 124 || $status -eq 137 ]] || {
  echo "SANDBOX_TIMEOUT_NOT_ENFORCED status=$status output=$(cat /tmp/onsure-sandbox-timeout.out)" >&2
  exit 1
}
! grep -q TIMEOUT_NOT_ENFORCED /tmp/onsure-sandbox-timeout.out || {
  echo 'SANDBOX_TIMEOUT_OUTPUT_LEAKED' >&2
  exit 1
}
rm -f /tmp/onsure-sandbox-timeout.out

echo 'ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 7'
