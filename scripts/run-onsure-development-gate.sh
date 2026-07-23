#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUT="$ROOT/receipts/development-gate/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$OUT"

bash "$ROOT/scripts/preflight-local-assurance.sh" | tee "$OUT/preflight.log"
bash "$ROOT/scripts/preflight-universal-harness.sh" | tee "$OUT/universal-preflight.log"

bash "$ROOT/scripts/run-product-platform-e2e.sh" | tee "$OUT/product-platform-e2e.log"
PRODUCT_ROOT="$(awk '/^ONSURE_PRODUCT_PLATFORM_E2E_PASS / {print $2}' "$OUT/product-platform-e2e.log" | tail -n 1)"
[[ -n "$PRODUCT_ROOT" && -d "$PRODUCT_ROOT" ]] || {
  echo "ONSURE_DEVELOPMENT_GATE_FAIL PRODUCT_E2E_EVIDENCE_MISSING" >&2
  exit 90
}

bash "$ROOT/scripts/run-universal-harness-twice.sh" \
  operator-independent-1 operator-independent-2 local-jdk17 \
  | tee "$OUT/universal-harness-twice.log"
UNIVERSAL_ROOT="$(awk '/^ONSURE_UNIVERSAL_TWO_RUN_PASS / {print $2}' "$OUT/universal-harness-twice.log" | tail -n 1)"
[[ -n "$UNIVERSAL_ROOT" && -d "$UNIVERSAL_ROOT" ]] || {
  echo "ONSURE_DEVELOPMENT_GATE_FAIL UNIVERSAL_HARNESS_EVIDENCE_MISSING" >&2
  exit 91
}
[[ -s "$UNIVERSAL_ROOT/final-candidate.json" && -s "$UNIVERSAL_ROOT/two-run-evidence.sha256" ]] || {
  echo "ONSURE_DEVELOPMENT_GATE_FAIL UNIVERSAL_HARNESS_CANDIDATE_MISSING" >&2
  exit 92
}

bash "$ROOT/scripts/execute-issue-4-final-gate.sh" | tee "$OUT/self-assurance.log"
ASSURANCE_ROOT="$(awk '/^ISSUE4_FINAL_GATE_EVIDENCE_READY / {print $2}' "$OUT/self-assurance.log" | tail -n 1)"
[[ -n "$ASSURANCE_ROOT" && -d "$ASSURANCE_ROOT" ]] || {
  echo "ONSURE_DEVELOPMENT_GATE_FAIL SELF_ASSURANCE_EVIDENCE_MISSING" >&2
  exit 93
}

cat > "$OUT/development-gate-result.txt" <<EOF
contract=ONSURE_DEVELOPMENT_GATE_V2
product_platform_e2e=PASS
product_evidence_root=$PRODUCT_ROOT
universal_harness_twice=PASS
universal_harness_evidence_root=$UNIVERSAL_ROOT
universal_final_candidate=$UNIVERSAL_ROOT/final-candidate.json
universal_final_lock_allowed=false
self_assurance=PASS
assurance_evidence_root=$ASSURANCE_ROOT
gate=PASS
EOF
sha256sum \
  "$OUT/preflight.log" "$OUT/universal-preflight.log" \
  "$OUT/product-platform-e2e.log" "$OUT/universal-harness-twice.log" "$OUT/self-assurance.log" \
  "$PRODUCT_ROOT/product-e2e-lock.sha256" \
  "$UNIVERSAL_ROOT/two-run-evidence.sha256" "$UNIVERSAL_ROOT/final-candidate.json" \
  "$ASSURANCE_ROOT/evidence.sha256" "$OUT/development-gate-result.txt" \
  > "$OUT/development-gate-lock.sha256"

printf 'ONSURE_DEVELOPMENT_GATE_PASS %s\n' "$OUT"
