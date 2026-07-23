#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_MODE=0
if [[ "${1:-}" == "--verify" ]]; then
  VERIFY_MODE=1
  shift
fi
RUN_ROOT="${1:-}"
if [[ -z "$RUN_ROOT" ]]; then
  echo "usage: $0 [--verify] <run-root>" >&2
  exit 64
fi

command -v sha256sum >/dev/null 2>&1 || { echo "missing command: sha256sum" >&2; exit 69; }
command -v cmp >/dev/null 2>&1 || { echo "missing command: cmp" >&2; exit 69; }
[[ -d "$RUN_ROOT" ]] || { echo "run root not found: $RUN_ROOT" >&2; exit 71; }
RUN_ROOT="$(cd "$RUN_ROOT" && pwd)"
LEDGER="$(dirname "$RUN_ROOT")/receipt-ledger.jsonl"

status() {
  local file="$1"
  [[ -s "$file" ]] && printf 'PRESENT' || printf 'MISSING'
}

digest() {
  local file="$1"
  [[ -s "$file" ]] && sha256sum "$file" | awk '{print $1}' || printf 'NOT_AVAILABLE'
}

json_string() {
  local file="$1" key="$2"
  [[ -s "$file" ]] || { printf 'NOT_AVAILABLE'; return; }
  local value
  value="$(sed -n 's/.*"'"$key"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$file" | head -n 1)"
  [[ -n "$value" ]] && printf '%s' "$value" || printf 'NOT_AVAILABLE'
}

regression_status() {
  local summary="$1"
  [[ -s "$summary" ]] || { printf 'NOT_RUN'; return; }
  if grep -Eq 'Failures: [1-9][0-9]*|Errors: [1-9][0-9]*' "$summary"; then
    printf 'FAIL'
  else
    printf 'PASS'
  fi
}

fixture_status() {
  local report="$1"
  [[ -s "$report" ]] || { printf 'NOT_RUN'; return; }
  local rows failures
  rows="$(tail -n +2 "$report" | wc -l | tr -d ' ')"
  failures="$(tail -n +2 "$report" | awk -F '\t' '$7 != "PASS" {count++} END {print count+0}')"
  [[ "$rows" == "20" && "$failures" == "0" ]] && printf 'PASS' || printf 'FAIL'
}

RUN_CONTEXT="$RUN_ROOT/run-context.json"
SOURCE_LOCK="$RUN_ROOT/source-lock.json"
FIXTURE_SNAPSHOT="$RUN_ROOT/adversarial-transition-fixtures.snapshot.json"
SECURITY_SNAPSHOT="$RUN_ROOT/security-findings.snapshot.json"
FINAL_RECEIPT="$RUN_ROOT/final-receipt.json"
OTESTER="$RUN_ROOT/otester/receipt.json"
OAUDIT="$RUN_ROOT/oaudit/receipt.json"
FIXTURE_REPORT="$RUN_ROOT/regression-2/adversarial-fixtures.tsv"

VERIFY_STATUS="NOT_RUN"
VERIFY_DETAIL="not requested"
if [[ "$VERIFY_MODE" == "1" ]]; then
  VERIFY_LOG="$(mktemp)"
  trap 'rm -f "$VERIFY_LOG"' EXIT
  if bash "$ROOT/scripts/verify-local-assurance.sh" "$RUN_ROOT" >"$VERIFY_LOG" 2>&1; then
    VERIFY_STATUS="PASS"
  else
    VERIFY_STATUS="FAIL"
  fi
  VERIFY_DETAIL="$(tr '\n' ' ' < "$VERIFY_LOG" | sed 's/[[:space:]]\+/ /g')"
fi

SUMMARY_EQUAL="FAIL"
CLASSES_EQUAL="FAIL"
FIXTURES_EQUAL="FAIL"
cmp -s "$RUN_ROOT/regression-1/test-summary.txt" "$RUN_ROOT/regression-2/test-summary.txt" && SUMMARY_EQUAL="PASS"
cmp -s "$RUN_ROOT/regression-1/classes.sha256" "$RUN_ROOT/regression-2/classes.sha256" && CLASSES_EQUAL="PASS"
cmp -s "$RUN_ROOT/regression-1/adversarial-fixtures.tsv" "$RUN_ROOT/regression-2/adversarial-fixtures.tsv" && FIXTURES_EQUAL="PASS"

SECURITY_STATUS="NOT_VERIFIED"
[[ "$VERIFY_STATUS" == "PASS" ]] && SECURITY_STATUS="ZERO_OPEN_BLOCKING_CONFIRMED"

CURRENT_LEDGER_HEAD="NOT_AVAILABLE"
if [[ -s "$LEDGER" ]]; then
  CURRENT_LEDGER_HEAD="$(tail -n 1 "$LEDGER" | sed -n 's/.*"entry_hash"[[:space:]]*:[[:space:]]*"\([0-9a-f]*\)".*/\1/p')"
  [[ -n "$CURRENT_LEDGER_HEAD" ]] || CURRENT_LEDGER_HEAD="NOT_AVAILABLE"
fi

cat <<EOF
# ONSURE Local Assurance Execution Summary

## 1. 실행 식별

- Run root: $RUN_ROOT
- Assurance run ID: $(json_string "$RUN_CONTEXT" run_id)
- Run started at: $(json_string "$RUN_CONTEXT" started_at)
- Source commit SHA: $(json_string "$SOURCE_LOCK" commit_sha)
- Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)
- OS: $(uname -srm 2>/dev/null || echo UNKNOWN)
- JDK: $(java -version 2>&1 | head -n 1 || echo UNKNOWN)
- Maven: $(mvn -version 2>/dev/null | head -n 1 || echo UNKNOWN)

## 2. 결과 요약

- Preflight: $([[ -s "$SOURCE_LOCK" ]] && echo EVIDENCED_BY_RUNNER || echo NOT_RUN)
- Maven compile: $([[ -s "$RUN_ROOT/regression-2/classes.sha256" ]] && echo PASS || echo NOT_RUN)
- JUnit regression-1: $(regression_status "$RUN_ROOT/regression-1/test-summary.txt")
- JUnit regression-2: $(regression_status "$RUN_ROOT/regression-2/test-summary.txt")
- A01~A20 Fixture: $(fixture_status "$FIXTURE_REPORT")
- Regression summary identical: $SUMMARY_EQUAL
- Compiled class hash identical: $CLASSES_EQUAL
- Fixture report identical: $FIXTURES_EQUAL
- OTester decision: $(json_string "$OTESTER" decision)
- OAudit decision: $(json_string "$OAUDIT" decision)
- Final Receipt decision: $(json_string "$FINAL_RECEIPT" decision)
- Read-only verifier: $VERIFY_STATUS
- Critical/High: $SECURITY_STATUS
- Gate: HOLD until full two-run execution and verification evidence is recorded

## 3. Evidence inventory

| Evidence | Status | SHA-256 |
|---|---|---|
| run-context.json | $(status "$RUN_CONTEXT") | $(digest "$RUN_CONTEXT") |
| source-lock.json | $(status "$SOURCE_LOCK") | $(digest "$SOURCE_LOCK") |
| adversarial-transition-fixtures.snapshot.json | $(status "$FIXTURE_SNAPSHOT") | $(digest "$FIXTURE_SNAPSHOT") |
| security-findings.snapshot.json | $(status "$SECURITY_SNAPSHOT") | $(digest "$SECURITY_SNAPSHOT") |
| regression-1/test-summary.txt | $(status "$RUN_ROOT/regression-1/test-summary.txt") | $(digest "$RUN_ROOT/regression-1/test-summary.txt") |
| regression-1/classes.sha256 | $(status "$RUN_ROOT/regression-1/classes.sha256") | $(digest "$RUN_ROOT/regression-1/classes.sha256") |
| regression-1/adversarial-fixtures.tsv | $(status "$RUN_ROOT/regression-1/adversarial-fixtures.tsv") | $(digest "$RUN_ROOT/regression-1/adversarial-fixtures.tsv") |
| regression-1/evidence.sha256 | $(status "$RUN_ROOT/regression-1/evidence.sha256") | $(digest "$RUN_ROOT/regression-1/evidence.sha256") |
| regression-2/test-summary.txt | $(status "$RUN_ROOT/regression-2/test-summary.txt") | $(digest "$RUN_ROOT/regression-2/test-summary.txt") |
| regression-2/classes.sha256 | $(status "$RUN_ROOT/regression-2/classes.sha256") | $(digest "$RUN_ROOT/regression-2/classes.sha256") |
| regression-2/adversarial-fixtures.tsv | $(status "$FIXTURE_REPORT") | $(digest "$FIXTURE_REPORT") |
| regression-2/evidence.sha256 | $(status "$RUN_ROOT/regression-2/evidence.sha256") | $(digest "$RUN_ROOT/regression-2/evidence.sha256") |
| otester/receipt.json | $(status "$OTESTER") | $(digest "$OTESTER") |
| oaudit/receipt.json | $(status "$OAUDIT") | $(digest "$OAUDIT") |
| key-registry.snapshot.json | $(status "$RUN_ROOT/key-registry.snapshot.json") | $(digest "$RUN_ROOT/key-registry.snapshot.json") |
| final-lock.sha256 | $(status "$RUN_ROOT/final-lock.sha256") | $(digest "$RUN_ROOT/final-lock.sha256") |
| final-receipt.json | $(status "$FINAL_RECEIPT") | $(digest "$FINAL_RECEIPT") |
| receipt-ledger.jsonl | $(status "$LEDGER") | $(digest "$LEDGER") |

## 4. Hash와 결속

- Source tree SHA-256: $(json_string "$SOURCE_LOCK" tree_sha256)
- Policy SHA-256: $(json_string "$SOURCE_LOCK" policy_sha256)
- Fixture contract snapshot SHA-256: $(digest "$FIXTURE_SNAPSHOT")
- Security findings snapshot SHA-256: $(digest "$SECURITY_SNAPSHOT")
- OTester input digest: $(json_string "$OTESTER" input_digest)
- OTester receipt SHA-256: $(digest "$OTESTER")
- OAudit input digest: $(json_string "$OAUDIT" input_digest)
- OAudit receipt SHA-256: $(digest "$OAUDIT")
- Registry snapshot SHA-256: $(digest "$RUN_ROOT/key-registry.snapshot.json")
- Final lock SHA-256: $(digest "$RUN_ROOT/final-lock.sha256")
- Final Receipt per-run Ledger head: $(json_string "$FINAL_RECEIPT" ledger_chain_head)
- Current global Ledger head: $CURRENT_LEDGER_HEAD
- Final Receipt verified at: $(json_string "$FINAL_RECEIPT" verified_at)

## 5. Fixture 결과

| Fixture | Expected Decision | Expected Reason | Actual Decision | Actual Reasons | Result |
|---|---|---|---|---|---|
EOF

if [[ -s "$FIXTURE_REPORT" ]]; then
  tail -n +2 "$FIXTURE_REPORT" | while IFS=$'\t' read -r contract fixture expected expected_reason actual actual_reasons result; do
    printf '| %s | %s | %s | %s | %s | %s |\n' \
      "$fixture" "$expected" "$expected_reason" "$actual" "$actual_reasons" "$result"
  done
else
  echo '| A01~A20 |  |  |  |  | NOT_RUN |'
fi

cat <<EOF

## 6. Read-only verifier detail

- Status: $VERIFY_STATUS
- Output: $VERIFY_DETAIL

## 7. 최종 판정

- Issue #4: OPEN until full two-run evidence is recorded
- PR #2: DRAFT until Issue #4 completion conditions are met
- Merge: NOT_ELIGIBLE
- Gate: HOLD
EOF
