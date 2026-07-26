#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT="$ROOT/receipts/development-gate/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$OUT"

bash "$ROOT/scripts/preflight-local-assurance.sh" --profile core | tee "$OUT/preflight.log"
python3 "$ROOT/scripts/validate-core-isolation.py" \
  --output "$OUT/core-isolation.json" | tee "$OUT/core-isolation.log"

bash "$ROOT/scripts/run-core-validator-fixture-e2e.sh" | tee "$OUT/core-fixture-e2e.log"
CORE_ROOT="$(awk '/^ONSURE_CORE_FIXTURE_TWO_RUN_PASS_NONFINAL / {print $2}' \
  "$OUT/core-fixture-e2e.log" | tail -n 1)"
[[ -n "$CORE_ROOT" && -d "$CORE_ROOT" ]] || {
  echo "ONSURE_DEVELOPMENT_GATE_FAIL CORE_FIXTURE_E2E_EVIDENCE_MISSING" >&2
  exit 90
}

bash "$ROOT/scripts/run-universal-harness-twice.sh" \
  internal-operator-1 internal-operator-2 local-jdk17-self-validation \
  | tee "$OUT/universal-harness-twice.log"
UNIVERSAL_ROOT="$(awk '/^ONSURE_UNIVERSAL_TWO_RUN_PASS_NONFINAL / {print $2}' \
  "$OUT/universal-harness-twice.log" | tail -n 1)"
[[ -n "$UNIVERSAL_ROOT" && -d "$UNIVERSAL_ROOT" ]] || {
  echo "ONSURE_DEVELOPMENT_GATE_FAIL UNIVERSAL_HARNESS_EVIDENCE_MISSING" >&2
  exit 91
}

bash "$ROOT/scripts/run-local-assurance-twice.sh" | tee "$OUT/local-self-assurance-twice.log"
LOCAL_ROOTS="$(awk '/^LOCAL_ASSURANCE_TWICE_PASS / {print $2, $3}' \
  "$OUT/local-self-assurance-twice.log" | tail -n 1)"
[[ -n "$LOCAL_ROOTS" ]] || {
  echo "ONSURE_DEVELOPMENT_GATE_FAIL LOCAL_SELF_ASSURANCE_EVIDENCE_MISSING" >&2
  exit 92
}

cat > "$OUT/development-gate-result.txt" <<EOF
contract=ONSURE_DEVELOPMENT_GATE_V4
core_module_isolation_static=PASS
core_fixture_e2e_two_run=PASS_NONFINAL
core_fixture_evidence_root=$CORE_ROOT
universal_harness_twice=PASS_NONFINAL
universal_harness_evidence_root=$UNIVERSAL_ROOT
local_self_assurance_twice=PASS_NONFINAL
local_self_assurance_roots=$LOCAL_ROOTS
authority_class=INTERNAL_SELF_VALIDATION
assurance_class=SELF_VALIDATION_NONFINAL
atomic_traceability=SEPARATE_GATE
vscode_product_full_chain=NOT_RUN
web_product_full_chain=NOT_RUN
independent_otester=NOT_RUN
independent_oaudit=NOT_RUN
final_lock_allowed=false
production_go=false
commercial_go=false
final_claim_allowed=false
EOF
sha256sum \
  "$OUT/preflight.log" "$OUT/core-isolation.json" "$OUT/core-isolation.log" \
  "$OUT/core-fixture-e2e.log" "$OUT/universal-harness-twice.log" \
  "$OUT/local-self-assurance-twice.log" "$OUT/development-gate-result.txt" \
  "$CORE_ROOT/core-two-run-evidence.sha256" \
  "$UNIVERSAL_ROOT/two-run-evidence.sha256" \
  > "$OUT/development-gate-lock.sha256"

printf 'ONSURE_DEVELOPMENT_GATE_SELF_VALIDATION_NONFINAL %s\n' "$OUT"
