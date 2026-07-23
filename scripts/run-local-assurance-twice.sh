#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LOG_DIR="$ROOT/receipts/local/twice-logs"
mkdir -p "$LOG_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
LOG1="$LOG_DIR/$STAMP-run-1.log"
LOG2="$LOG_DIR/$STAMP-run-2.log"

extract_run_root() {
  local log="$1"
  awk '/^LOCAL_ASSURANCE_PASS / {sub(/^LOCAL_ASSURANCE_PASS /, ""); value=$0} END {print value}' "$log"
}

run_and_capture() {
  local log="$1"
  bash "$ROOT/scripts/run-local-assurance.sh" | tee "$log"
  local run_root
  run_root="$(extract_run_root "$log")"
  [[ -n "$run_root" && -d "$run_root" ]] || {
    echo "LOCAL_ASSURANCE_TWICE_FAIL RUN_ROOT_NOT_REPORTED" >&2
    exit 86
  }
  printf '%s\n' "$run_root"
}

RUN1="$(run_and_capture "$LOG1" | tail -n 1)"
bash "$ROOT/scripts/verify-local-assurance.sh" "$RUN1"

RUN2="$(run_and_capture "$LOG2" | tail -n 1)"

# The shared append-only ledger changes after run 2. Both historical and current
# final receipts must remain valid against their own per-run ledger binding.
bash "$ROOT/scripts/verify-local-assurance.sh" "$RUN1"
bash "$ROOT/scripts/verify-local-assurance.sh" "$RUN2"

cmp "$RUN1/source-lock.json" "$RUN2/source-lock.json"
cmp "$RUN1/adversarial-transition-fixtures.snapshot.json" "$RUN2/adversarial-transition-fixtures.snapshot.json"
cmp "$RUN1/security-findings.snapshot.json" "$RUN2/security-findings.snapshot.json"
cmp "$RUN1/regression-1/test-summary.txt" "$RUN2/regression-1/test-summary.txt"
cmp "$RUN1/regression-2/test-summary.txt" "$RUN2/regression-2/test-summary.txt"
cmp "$RUN1/regression-1/classes.sha256" "$RUN2/regression-1/classes.sha256"
cmp "$RUN1/regression-2/classes.sha256" "$RUN2/regression-2/classes.sha256"
cmp "$RUN1/regression-1/adversarial-fixtures.tsv" "$RUN2/regression-1/adversarial-fixtures.tsv"
cmp "$RUN1/regression-2/adversarial-fixtures.tsv" "$RUN2/regression-2/adversarial-fixtures.tsv"

# evidence.sha256 manifests contain absolute run-root paths by design and are
# validated independently inside each run. Their underlying files are compared above.
SOURCE_COMMIT_1="$(sed -n 's/.*"commit_sha"[[:space:]]*:[[:space:]]*"\([0-9a-f]*\)".*/\1/p' "$RUN1/source-lock.json" | head -n 1)"
SOURCE_COMMIT_2="$(sed -n 's/.*"commit_sha"[[:space:]]*:[[:space:]]*"\([0-9a-f]*\)".*/\1/p' "$RUN2/source-lock.json" | head -n 1)"
[[ -n "$SOURCE_COMMIT_1" && "$SOURCE_COMMIT_1" == "$SOURCE_COMMIT_2" ]] || {
  echo "LOCAL_ASSURANCE_TWICE_FAIL SOURCE_COMMIT_DIVERGENCE" >&2
  exit 87
}

printf 'LOCAL_ASSURANCE_TWICE_PASS %s %s\n' "$RUN1" "$RUN2"
