#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT_DIR="${ONSURE_DD_VERIFY_OUT:-.onsure/manual-dd-verification}"
mkdir -p "$OUT_DIR"
RUN_ID="dd-manual-$(date -u +%Y%m%dT%H%M%SZ)"
RECEIPT="$OUT_DIR/${RUN_ID}.json"
HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || printf 'UNKNOWN')"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

run_capture() {
  local name="$1"; shift
  set +e
  "$@" >"$OUT_DIR/${RUN_ID}.${name}.stdout" 2>"$OUT_DIR/${RUN_ID}.${name}.stderr"
  local rc=$?
  set -e
  printf '%s' "$rc"
}

set -e
STATIC_RC="$(run_capture static python3 scripts/validate-dd-machine-definitions.py)"
QUAL_STATUS_RC="$(run_capture qualification_status python3 scripts/validate-dd-semantic-evaluator-qualifications.py)"
MAVEN_RC="$(run_capture maven mvn -B -Dtest=BuiltInDdSemanticEvaluatorsTest test)"
set +e
python3 scripts/materialize-dd-manual-verification-receipt.py \
  --run-id "$RUN_ID" \
  --source-tree-sha "$HEAD_SHA" \
  --started-at "$STARTED_AT" \
  --static-rc "$STATIC_RC" \
  --qualification-status-rc "$QUAL_STATUS_RC" \
  --maven-rc "$MAVEN_RC" \
  --output "$RECEIPT"
RECEIPT_RC=$?
set -e

cat "$RECEIPT"
if [[ "$STATIC_RC" != "0" || "$QUAL_STATUS_RC" != "0" || "$MAVEN_RC" != "0" || "$RECEIPT_RC" != "0" ]]; then
  exit 42
fi
exit 0
