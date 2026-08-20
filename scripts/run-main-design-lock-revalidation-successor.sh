#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$ROOT"
MODE="${1:-PRE_CLEAN}"; BRANCH="$(git branch --show-current)"; HEAD_SHA="$(git rev-parse HEAD)"; TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
[[ "$BRANCH" == "main" ]] || { echo "ONSURE_MAIN_SUCCESSOR_HOLD NOT_ON_MAIN" >&2; exit 83; }
: "${ONSURE_DD_TARGET_IDENTITY:?required}"; : "${ONSURE_DD_EXECUTION_PRINCIPAL:?required}"; : "${ONSURE_DD_EXECUTION_ENVIRONMENT:?required}"; : "${ONSURE_DD_EVIDENCE_INDEX_SOURCE:?required}"
: "${ONSURE_DD_QUALIFICATION_BUNDLE_SOURCE:?required}"; : "${ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE:?required}"
: "${ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR:?required}"; : "${ONSURE_DD040_BOUND_DECISION_RECEIPT:?required}"
: "${ONSURE_HDA_RECEIPTS_DIR:?required}"; : "${ONSURE_HDA_SUCCESSOR_APPROVAL:?required}"
OUT_DIR="${ONSURE_MAIN_REVALIDATION_OUT:-.onsure/main-design-lock-revalidation-successor}"; mkdir -p "$OUT_DIR"
if [[ "$MODE" == "PRE_CLEAN" ]]; then
  echo "[MAIN-SUCCESSOR] 1/8 rerun current-main Manual V7 DD42/fixture173 mechanics"
  export ONSURE_DD_VERIFY_OUT="$OUT_DIR/dd-manual"; bash scripts/run-dd-semantic-evaluator-manual-verification-successor.sh
  DD_RECEIPT="$(ls -1t "$OUT_DIR"/dd-manual/dd-manual-successor-*.json | head -1)"; export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"; echo "$DD_RECEIPT" > "$OUT_DIR/current-dd-manual-receipt.path"
  python3 scripts/validate-dd-denominator-42.py
  echo "[MAIN-SUCCESSOR] 2/8 stage immutable qualified feature bundle V4 and 42 receipts"
  python3 scripts/stage-dd-qualification-bundle-successor.py --source "$ONSURE_DD_QUALIFICATION_BUNDLE_SOURCE"
  python3 scripts/stage-dd-qualification-receipts.py --source "$ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE"
  export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$ROOT/.onsure/dd-independent-qualification/receipts"; echo "$ONSURE_DD_QUALIFICATION_RECEIPTS_DIR" > "$OUT_DIR/current-qualification-receipts.path"
  python3 scripts/validate-dd-semantic-evaluator-qualifications-successor.py
  echo "[MAIN-SUCCESSOR] 3/8 prove current-main compiled base+extension evaluators equal qualified artifacts"
  python3 - <<'PY'
import hashlib,json,pathlib
m=json.loads(pathlib.Path('.onsure/dd-independent-qualification/frozen-bundle-successor/bundle-manifest.json').read_text())
checks=[('target/classes/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.class','base_evaluator_artifact_sha256'),('target/classes/kr/co/oruda/onsure/platform/DesignGapDdSemanticEvaluators.class','extension_evaluator_artifact_sha256')]
for rel,key in checks:
 p=pathlib.Path(rel);assert p.is_file(),rel+':MISSING';assert hashlib.sha256(p.read_bytes()).hexdigest()==m[key],rel+':DIFFERS_FROM_QUALIFIED_SUBJECT'
PY
  echo "[MAIN-SUCCESSOR] 4/8 revalidate structural Discovery + DD040 bound + HDA22"
  python3 scripts/reconcile-design-discovery-waves-successor.py; python3 scripts/validate-human-design-authority-successor.py
  echo "[MAIN-SUCCESSOR] 5/8 execute target runtime42 on current main commit/tree"
  export ONSURE_DD_EVIDENCE_INDEX_STAGED="$OUT_DIR/evidence-index.json"; bash scripts/run-dd-semantic-runtime-evidence-successor.sh; echo "$OUT_DIR/evidence-index.json" > "$OUT_DIR/current-evidence-index.path"
  echo "[MAIN-SUCCESSOR] 6/8 execute V9 successor non-CLEAN closure"
  python3 scripts/run-product-design-closure-successor.py --phase preclean
  echo "[MAIN-SUCCESSOR] 7/8 require NEW main-specific Pre-CLEAN Subject"
  python3 - "$HEAD_SHA" "$TREE_SHA" <<'PY'
import hashlib,json,pathlib,sys
p=pathlib.Path('.onsure/independent-clean/preclean-subject.json');assert p.is_file(),'MAIN_PRECLEAN_SUBJECT_MISSING';d=json.loads(p.read_text());x=dict(d);x.pop('subject_digest',None)
assert d.get('subject_digest')==hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest();assert d.get('source_commit_sha')==sys.argv[1] and d.get('source_tree_sha')==sys.argv[2];assert d.get('decision')=='READY_FOR_INDEPENDENT_CLEAN_NONFINAL' and not d.get('blocking_reasons')
print(json.dumps({'decision':'MAIN_SUCCESSOR_PRECLEAN_READY_NONFINAL','subject_digest':d['subject_digest'],'coverage_digest':d['coverage_digest']},sort_keys=True))
PY
  echo "[MAIN-SUCCESSOR] 8/8 STOP_FOR_NEW_MAIN_INDEPENDENT_CLEAN_A_B"; exit 82
fi
if [[ "$MODE" != "FINALIZE_LOCK" ]]; then echo "Usage: $0 PRE_CLEAN|FINALIZE_LOCK" >&2; exit 2; fi
: "${ONSURE_INDEPENDENT_CLEAN_RECEIPTS_DIR:?required}"; : "${ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT:?required}"
DD_RECEIPT="$(cat "$OUT_DIR/current-dd-manual-receipt.path")"; QDIR="$(cat "$OUT_DIR/current-qualification-receipts.path")"; export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"; export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$QDIR"
echo "[MAIN-SUCCESSOR] FINAL 1/5 validate NEW main CLEAN A/B"
python3 scripts/validate-independent-clean-twice.py --source-commit-sha "$HEAD_SHA" --source-tree-sha "$TREE_SHA"
echo "[MAIN-SUCCESSOR] FINAL 2/5 rerun V9 final closure"
python3 scripts/run-product-design-closure-successor.py --phase final
echo "[MAIN-SUCCESSOR] FINAL 3/5 validate independent PR review and reviewed-head ancestry"
REVIEW_HEAD="$(python3 - "$ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT" <<'PY'
import json,sys
print(json.load(open(sys.argv[1],encoding='utf-8'))['reviewed_head_sha'])
PY
)"
python3 scripts/validate-pr-independent-review.py --receipt "$ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT" --expected-head-sha "$REVIEW_HEAD"; git merge-base --is-ancestor "$REVIEW_HEAD" "$HEAD_SHA"
echo "[MAIN-SUCCESSOR] FINAL 4/5 invoke successor Design Lock issuer"
python3 scripts/issue-design-lock-successor.py
echo "[MAIN-SUCCESSOR] FINAL 5/5 require actual V6 successor Design Lock receipt"
python3 - "$HEAD_SHA" "$TREE_SHA" <<'PY'
import json,sys
p=json.load(open('.onsure/design-baseline/design-lock-receipt-successor.json',encoding='utf-8'));assert p.get('contract')=='ONSURE_DESIGN_LOCK_RECEIPT_V6_SUCCESSOR';assert p.get('subject_commit_sha')==sys.argv[1] and p.get('subject_tree_sha')==sys.argv[2];assert p.get('design_lock') is True,f"DESIGN_LOCK_HOLD:{p.get('blocking_reasons')}";assert p.get('final_lock') is False and p.get('production_go') is False and p.get('commercial_go') is False and p.get('final_claim_allowed') is False
print(json.dumps(p,ensure_ascii=False,sort_keys=True))
PY
