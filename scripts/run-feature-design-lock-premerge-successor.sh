#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-PREPARE_EXTERNAL}"
OUT_DIR="${ONSURE_FEATURE_PREMERGE_OUT:-.onsure/feature-design-lock-premerge-successor}"
mkdir -p "$OUT_DIR"
HEAD_SHA="$(git rev-parse HEAD)"; TREE_SHA="$(git rev-parse 'HEAD^{tree}')"; BRANCH="$(git branch --show-current)"
if [[ "$BRANCH" == "main" || -z "$BRANCH" ]]; then echo "ONSURE_SUCCESSOR_PREMERGE_HOLD INVALID_FEATURE_REF" >&2; exit 83; fi

if [[ "$MODE" == "PREPARE_EXTERNAL" ]]; then
  [[ -z "$(git status --porcelain --untracked-files=no)" ]] || { echo "ONSURE_SUCCESSOR_PREPARE_HOLD TRACKED_WORKTREE_NOT_CLEAN" >&2; exit 85; }
  echo "[SUCCESSOR] 1/8 execute successor Java/JUnit mechanics"
  export ONSURE_DD_VERIFY_OUT="$OUT_DIR/dd-manual"
  bash scripts/run-dd-semantic-evaluator-manual-verification-successor.sh
  DD_RECEIPT="$(ls -1t "$OUT_DIR"/dd-manual/dd-manual-successor-*.json | head -1)"
  export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"; echo "$DD_RECEIPT" > "$OUT_DIR/current-dd-manual-receipt.path"
  echo "[SUCCESSOR] 2/8 require exact static DD42 / fixture173 denominator"
  python3 scripts/validate-dd-denominator-42.py
  echo "[SUCCESSOR] 3/8 prove structural-blindness fail-closed mechanics"
  python3 scripts/run-design-discovery-structural-blindness-fixture-tests.py > "$OUT_DIR/structural-blindness-fixture-tests.json"
  echo "[SUCCESSOR] 4/8 verify closure-runner materialization is fail-closed"
  python3 scripts/validate-successor-closure-runner-materialization.py > "$OUT_DIR/closure-runner-materialization.json"
  echo "[SUCCESSOR] 5/8 freeze DD42/fixture173 qualification subject"
  python3 scripts/prepare-dd-independent-qualification-bundle-successor.py
  rm -rf "$OUT_DIR/qualification-bundle"; cp -R .onsure/dd-independent-qualification/frozen-bundle-successor "$OUT_DIR/qualification-bundle"
  echo "$OUT_DIR/qualification-bundle" > "$OUT_DIR/current-qualification-bundle.path"
  echo "[SUCCESSOR] 6/8 freeze uncontaminated Discovery baseline"
  python3 scripts/freeze-independent-design-discovery-baseline.py
  rm -rf "$OUT_DIR/discovery-bundle"; mkdir -p "$OUT_DIR/discovery-bundle"
  cp .onsure/design-discovery/frozen-baseline/freeze-receipt.json "$OUT_DIR/discovery-bundle/frozen-baseline-receipt.json"
  cp -R .onsure/design-discovery/frozen-baseline/bundle "$OUT_DIR/discovery-bundle/bundle"
  echo "[SUCCESSOR] 7/8 prepare paired sealed Wave A/B input envelopes before results"
  rm -rf .onsure/design-discovery-v3/sealed-results
  python3 scripts/prepare-design-discovery-wave-envelopes-v3.py > "$OUT_DIR/discovery-bundle/envelope-preparation.json"
  cp .onsure/design-discovery-v3/envelopes/envelope-pair-receipt.json "$OUT_DIR/discovery-bundle/envelope-pair-receipt.json"
  mkdir -p "$OUT_DIR/discovery-bundle/envelopes"; cp -R .onsure/design-discovery-v3/envelopes/INDEPENDENT-SATURATION-A "$OUT_DIR/discovery-bundle/envelopes/"; cp -R .onsure/design-discovery-v3/envelopes/INDEPENDENT-SATURATION-B "$OUT_DIR/discovery-bundle/envelopes/"
  echo "[SUCCESSOR] 8/8 STOP_FOR_EXTERNAL_ASSURANCE"
  echo "Required: fresh qualification 42/42 over 173 fixtures; sealed Wave A/B V3; external DD-040 bounded-rule receipt after both waves; HDA22 evidence; target runtime evidence 42."
  exit 86
fi

if [[ "$MODE" == "PRE_CLEAN" ]]; then
  : "${ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE:?required}"
  : "${ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR:?required}"
  : "${ONSURE_DD040_BOUND_DECISION_RECEIPT:?required}"
  : "${ONSURE_HDA_RECEIPTS_DIR:?required}"
  : "${ONSURE_HDA_SUCCESSOR_APPROVAL:?required}"
  : "${ONSURE_DD_EVIDENCE_INDEX_SOURCE:?required}"
  : "${ONSURE_DD_TARGET_IDENTITY:?required}"
  : "${ONSURE_DD_EXECUTION_PRINCIPAL:?required}"
  : "${ONSURE_DD_EXECUTION_ENVIRONMENT:?required}"
  DD_RECEIPT="$(cat "$OUT_DIR/current-dd-manual-receipt.path")"; export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"
  python3 - "$DD_RECEIPT" "$HEAD_SHA" "$TREE_SHA" <<'PY'
import hashlib,json,sys
p=json.load(open(sys.argv[1],encoding='utf-8'));x=dict(p);x.pop('receipt_digest',None);c=p.get('claims') or {}
assert p.get('contract')=='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V7'
assert p.get('receipt_digest')==hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
assert p.get('source_commit_sha')==sys.argv[2] and p.get('source_tree_sha')==sys.argv[3] and p.get('verdict')=='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY'
assert c.get('dd_authorized_route_execution_mechanics_count')==42 and c.get('qualification_fixture_mechanics_executed_count')==173 and c.get('dd042_minimum_adversarial_fixture_mechanics_count')==6
PY
  echo "[SUCCESSOR] PRE_CLEAN 1/6 stage + validate current 42/42 qualification"
  python3 scripts/stage-dd-qualification-receipts.py --source "$ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE"
  export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$ROOT/.onsure/dd-independent-qualification/receipts"; echo "$ONSURE_DD_QUALIFICATION_RECEIPTS_DIR" > "$OUT_DIR/current-qualification-receipts.path"
  python3 scripts/validate-dd-semantic-evaluator-qualifications-successor.py; python3 scripts/validate-dd-denominator-42.py
  echo "[SUCCESSOR] PRE_CLEAN 2/6 validate sealed Discovery V3 + external DD-040 bounded rule"
  python3 scripts/reconcile-design-discovery-waves-successor.py
  echo "[SUCCESSOR] PRE_CLEAN 3/6 validate Human Authority 22/22"
  python3 scripts/validate-human-design-authority-successor.py
  echo "[SUCCESSOR] PRE_CLEAN 4/6 execute current target runtime 42/42"
  export ONSURE_DD_EVIDENCE_INDEX_STAGED="$OUT_DIR/evidence-index.json"
  bash scripts/run-dd-semantic-runtime-evidence-successor.sh; echo "$OUT_DIR/evidence-index.json" > "$OUT_DIR/current-evidence-index.path"
  echo "[SUCCESSOR] PRE_CLEAN 5/6 execute V9 successor non-CLEAN closure and seal exact Pre-CLEAN Subject"
  python3 scripts/run-product-design-closure-successor.py --phase preclean
  python3 - "$HEAD_SHA" "$TREE_SHA" <<'PY'
import hashlib,json,pathlib,sys
p=pathlib.Path('.onsure/independent-clean/preclean-subject.json');assert p.is_file(),'PRECLEAN_SUBJECT_MISSING';d=json.loads(p.read_text());x=dict(d);x.pop('subject_digest',None)
assert d.get('subject_digest')==hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest();assert d.get('source_commit_sha')==sys.argv[1] and d.get('source_tree_sha')==sys.argv[2]
assert d.get('decision')=='READY_FOR_INDEPENDENT_CLEAN_NONFINAL' and not d.get('blocking_reasons');print(json.dumps({'decision':'SUCCESSOR_FEATURE_PRECLEAN_READY_NONFINAL','subject_digest':d['subject_digest'],'coverage_digest':d['coverage_digest']},sort_keys=True))
PY
  echo "[SUCCESSOR] PRE_CLEAN 6/6 STOP_FOR_INDEPENDENT_CLEAN_A_B_AND_PR_REVIEW"
  echo "$HEAD_SHA" > "$OUT_DIR/reviewed-feature-head.sha"; exit 84
fi

if [[ "$MODE" != "FINALIZE_PREMERGE" ]]; then echo "Usage: $0 PREPARE_EXTERNAL|PRE_CLEAN|FINALIZE_PREMERGE" >&2; exit 2; fi
: "${ONSURE_INDEPENDENT_CLEAN_RECEIPTS_DIR:?required}"
: "${ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT:?required}"
: "${ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR:?required}"
: "${ONSURE_DD040_BOUND_DECISION_RECEIPT:?required}"
: "${ONSURE_HDA_RECEIPTS_DIR:?required}"
: "${ONSURE_HDA_SUCCESSOR_APPROVAL:?required}"
DD_RECEIPT="$(cat "$OUT_DIR/current-dd-manual-receipt.path")"; QDIR="$(cat "$OUT_DIR/current-qualification-receipts.path")"; export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"; export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$QDIR"
echo "[SUCCESSOR] FINAL 1/4 validate exact feature CLEAN A/B"
python3 scripts/validate-independent-clean-twice.py --source-commit-sha "$HEAD_SHA" --source-tree-sha "$TREE_SHA"
echo "[SUCCESSOR] FINAL 2/4 rerun V9 full closure with CLEAN"
python3 scripts/run-product-design-closure-successor.py --phase final
echo "[SUCCESSOR] FINAL 3/4 validate independent PR review exact feature head"
python3 scripts/validate-pr-independent-review.py --receipt "$ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT" --expected-head-sha "$HEAD_SHA"
echo "[SUCCESSOR] FINAL 4/4 require READY_FOR_MAIN_MERGE_NONFINAL"
python3 scripts/validate-premerge-design-lock-readiness-successor.py --dd-manual-receipt "$DD_RECEIPT" --pr-review-receipt "$ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT"
