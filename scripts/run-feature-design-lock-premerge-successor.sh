#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-PREPARE_EXTERNAL}"
OUT_DIR="${ONSURE_FEATURE_PREMERGE_OUT:-.onsure/feature-design-lock-premerge-successor}"
mkdir -p "$OUT_DIR"
HEAD_SHA="$(git rev-parse HEAD)"
TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
BRANCH="$(git branch --show-current)"
if [[ "$BRANCH" == "main" || -z "$BRANCH" ]]; then echo "ONSURE_SUCCESSOR_PREMERGE_HOLD INVALID_FEATURE_REF" >&2; exit 83; fi

if [[ "$MODE" == "PREPARE_EXTERNAL" ]]; then
  [[ -z "$(git status --porcelain --untracked-files=no)" ]] || { echo "ONSURE_SUCCESSOR_PREPARE_HOLD TRACKED_WORKTREE_NOT_CLEAN" >&2; exit 85; }
  echo "[SUCCESSOR] 1/5 execute successor Java/JUnit mechanics"
  export ONSURE_DD_VERIFY_OUT="$OUT_DIR/dd-manual"
  bash scripts/run-dd-semantic-evaluator-manual-verification-successor.sh
  DD_RECEIPT="$(ls -1t "$OUT_DIR"/dd-manual/dd-manual-successor-*.json | head -1)"
  export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"
  echo "$DD_RECEIPT" > "$OUT_DIR/current-dd-manual-receipt.path"
  echo "[SUCCESSOR] 2/5 require exact 42-DD design/runtime/fixture denominator"
  python3 scripts/validate-dd-denominator-42.py || true
  echo "[SUCCESSOR] 3/5 freeze 42-DD qualification subject"
  python3 scripts/prepare-dd-independent-qualification-bundle-successor.py
  rm -rf "$OUT_DIR/qualification-bundle"; cp -R .onsure/dd-independent-qualification/frozen-bundle-successor "$OUT_DIR/qualification-bundle"
  echo "[SUCCESSOR] 4/5 freeze new uncontaminated Discovery subject"
  python3 scripts/freeze-independent-design-discovery-baseline.py
  rm -rf "$OUT_DIR/discovery-bundle"; mkdir -p "$OUT_DIR/discovery-bundle"
  cp .onsure/design-discovery/frozen-baseline/freeze-receipt.json "$OUT_DIR/discovery-bundle/frozen-baseline-receipt.json"
  cp -R .onsure/design-discovery/frozen-baseline/bundle "$OUT_DIR/discovery-bundle/bundle"
  echo "[SUCCESSOR] 5/5 STOP_FOR_EXTERNAL_ASSURANCE"
  echo "Required: base qualification 40 + DD-041/042 qualification 2; fresh structural-blind Discovery A/B V2; Reconciler; HDA successor decisions; target runtime evidence 42."
  exit 86
fi

if [[ "$MODE" == "PRE_CLEAN" ]]; then
  : "${ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE:?required}"
  : "${ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR:?required}"
  : "${ONSURE_HDA_RECEIPTS_DIR:?required}"
  : "${ONSURE_DD_EVIDENCE_INDEX_SOURCE:?required}"
  python3 scripts/stage-dd-qualification-receipts.py --source "$ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE"
  export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$ROOT/.onsure/dd-independent-qualification/receipts"
  python3 scripts/validate-dd-semantic-evaluator-qualifications-successor.py
  python3 scripts/validate-dd-denominator-42.py
  python3 scripts/reconcile-design-discovery-waves-v2.py
  python3 scripts/validate-human-design-authority-successor.py
  bash scripts/run-dd-semantic-runtime-evidence-successor.sh
  echo "ONSURE_SUCCESSOR_PRE_CLEAN_HOLD closure/preclean successor integration still required" >&2
  exit 84
fi

echo "ONSURE_SUCCESSOR_PREMERGE_HOLD FINALIZE_PREMERGE_NOT_ENABLED_UNTIL_SUCCESSOR_PRECLEAN_AND_CLEAN_GATES_MATERIALIZED" >&2
exit 82
