#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-PRE_CLEAN}"
BRANCH="$(git branch --show-current)"
HEAD_SHA="$(git rev-parse HEAD)"
TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
if [[ "$BRANCH" == "main" || -z "$BRANCH" ]]; then
  echo "ONSURE_FEATURE_PREMERGE_HOLD INVALID_FEATURE_REF:${BRANCH:-DETACHED}" >&2
  exit 83
fi
: "${ONSURE_DD_TARGET_IDENTITY:?ONSURE_DD_TARGET_IDENTITY is required}"
: "${ONSURE_DD_EXECUTION_PRINCIPAL:?ONSURE_DD_EXECUTION_PRINCIPAL is required}"
: "${ONSURE_DD_EXECUTION_ENVIRONMENT:?ONSURE_DD_EXECUTION_ENVIRONMENT is required}"
: "${ONSURE_DD_EVIDENCE_INDEX_SOURCE:?ONSURE_DD_EVIDENCE_INDEX_SOURCE is required}"
: "${ONSURE_HDA_RECEIPTS_DIR:?ONSURE_HDA_RECEIPTS_DIR is required}"
OUT_DIR="${ONSURE_FEATURE_PREMERGE_OUT:-.onsure/feature-design-lock-premerge}"
mkdir -p "$OUT_DIR"

if [[ "$MODE" == "PRE_CLEAN" ]]; then
  echo "[ONSURE-FEATURE] 1/9 manual Java/JUnit and 160 fixture mechanics on exact feature subject"
  export ONSURE_DD_VERIFY_OUT="$OUT_DIR/dd-manual"
  bash scripts/run-dd-semantic-evaluator-manual-verification.sh
  DD_RECEIPT="$(ls -1t "$OUT_DIR"/dd-manual/dd-manual-*.json | head -1)"
  export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"
  echo "$DD_RECEIPT" > "$OUT_DIR/current-dd-manual-receipt.path"

  echo "[ONSURE-FEATURE] 2/9 freeze compiled evaluator qualification subject"
  python3 scripts/prepare-dd-independent-qualification-bundle.py
  cp -R .onsure/dd-independent-qualification/frozen-bundle "$OUT_DIR/qualification-bundle"
  echo "$OUT_DIR/qualification-bundle" > "$OUT_DIR/current-qualification-bundle.path"

  echo "[ONSURE-FEATURE] 3/9 require and stage 40 independent qualification receipts"
  : "${ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE:?After independent qualification, ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE is required}"
  python3 scripts/stage-dd-qualification-receipts.py --source "$ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE"
  export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$ROOT/.onsure/dd-independent-qualification/receipts"
  echo "$ONSURE_DD_QUALIFICATION_RECEIPTS_DIR" > "$OUT_DIR/current-qualification-receipts.path"
  python3 scripts/validate-dd-semantic-evaluator-qualifications.py --require-all-qualified

  echo "[ONSURE-FEATURE] 4/9 stage immutable target evidence and execute 40 DD runtime evaluations"
  export ONSURE_DD_EVIDENCE_INDEX_STAGED="$OUT_DIR/evidence-index.json"
  export ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE="$ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE"
  bash scripts/run-dd-semantic-runtime-evidence.sh
  echo "$OUT_DIR/evidence-index.json" > "$OUT_DIR/current-evidence-index.path"

  echo "[ONSURE-FEATURE] 5/9 require independent Discovery Saturation A/B"
  python3 scripts/validate-design-discovery-saturation.py

  echo "[ONSURE-FEATURE] 6/9 require Human Design Authority 18/18"
  python3 scripts/validate-human-design-authority-decisions.py

  echo "[ONSURE-FEATURE] 7/9 execute non-CLEAN closure and seal exact Pre-CLEAN Subject"
  set +e
  bash scripts/run-product-design-closure-post-delta.sh
  CLOSURE_RC=$?
  set -e
  python3 - "$HEAD_SHA" "$TREE_SHA" <<'PY'
import hashlib,json,pathlib,sys
p=pathlib.Path('.onsure/independent-clean/preclean-subject.json')
assert p.is_file(),'PRECLEAN_SUBJECT_MISSING'
d=json.loads(p.read_text(encoding='utf-8'))
x=dict(d); x.pop('subject_digest',None)
assert d.get('subject_digest')==hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
assert d.get('source_commit_sha')==sys.argv[1]
assert d.get('source_tree_sha')==sys.argv[2]
assert d.get('decision')=='READY_FOR_INDEPENDENT_CLEAN_NONFINAL'
assert not d.get('blocking_reasons')
print(json.dumps({'decision':'FEATURE_PRECLEAN_SUBJECT_READY_NONFINAL','subject_digest':d['subject_digest'],'coverage_digest':d['coverage_digest']},sort_keys=True))
PY

  echo "[ONSURE-FEATURE] 8/9 freeze review subject"
  printf '%s\n' "$HEAD_SHA" > "$OUT_DIR/reviewed-feature-head.sha"

  echo "[ONSURE-FEATURE] 9/9 STOP_FOR_INDEPENDENT_CLEAN_A_B_AND_PR_REVIEW"
  echo "Produce external Independent CLEAN A/B against .onsure/independent-clean/preclean-subject.json and independent PR review against head $HEAD_SHA."
  exit 84
fi

if [[ "$MODE" != "FINALIZE_PREMERGE" ]]; then
  echo "Usage: $0 PRE_CLEAN|FINALIZE_PREMERGE" >&2
  exit 2
fi
: "${ONSURE_INDEPENDENT_CLEAN_RECEIPTS_DIR:?ONSURE_INDEPENDENT_CLEAN_RECEIPTS_DIR is required}"
: "${ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT:?ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT is required}"
DD_RECEIPT="$(cat "$OUT_DIR/current-dd-manual-receipt.path")"
QDIR="$(cat "$OUT_DIR/current-qualification-receipts.path")"
export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"
export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$QDIR"

echo "[ONSURE-FEATURE] FINAL 1/4 validate exact feature-subject CLEAN A/B"
python3 scripts/validate-independent-clean-twice.py --source-commit-sha "$HEAD_SHA" --source-tree-sha "$TREE_SHA"

echo "[ONSURE-FEATURE] FINAL 2/4 re-run full closure with CLEAN A/B"
bash scripts/run-product-design-closure-post-delta.sh

echo "[ONSURE-FEATURE] FINAL 3/4 validate independent PR review for exact feature head"
python3 scripts/validate-pr-independent-review.py --receipt "$ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT" --expected-head-sha "$HEAD_SHA"

echo "[ONSURE-FEATURE] FINAL 4/4 require pre-merge readiness"
python3 scripts/validate-premerge-design-lock-readiness.py --dd-manual-receipt "$DD_RECEIPT" --pr-review-receipt "$ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT"
