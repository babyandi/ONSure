#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-PRE_CLEAN}"
BRANCH="$(git branch --show-current)"; HEAD_SHA="$(git rev-parse HEAD)"; TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
if [[ "$BRANCH" != "main" ]]; then echo "ONSURE_MAIN_REVALIDATION_HOLD NOT_MAIN_REF:$BRANCH" >&2; exit 80; fi
: "${ONSURE_DD_TARGET_IDENTITY:?ONSURE_DD_TARGET_IDENTITY is required}"
: "${ONSURE_DD_EXECUTION_PRINCIPAL:?ONSURE_DD_EXECUTION_PRINCIPAL is required}"
: "${ONSURE_DD_EXECUTION_ENVIRONMENT:?ONSURE_DD_EXECUTION_ENVIRONMENT is required}"
: "${ONSURE_DD_EVIDENCE_INDEX_SOURCE:?ONSURE_DD_EVIDENCE_INDEX_SOURCE is required for fresh main revalidation}"
: "${ONSURE_DD_QUALIFICATION_BUNDLE_SOURCE:?ONSURE_DD_QUALIFICATION_BUNDLE_SOURCE is required; do not regenerate the independently-qualified subject on main}"
: "${ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE:?ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE is required for fresh main revalidation}"
OUT_DIR="${ONSURE_MAIN_REVALIDATION_OUT:-.onsure/main-design-lock-revalidation}"; mkdir -p "$OUT_DIR"
RUN_ID="main-lock-$(date -u +%Y%m%dT%H%M%SZ)"; LOG="$OUT_DIR/$RUN_ID.log"; exec > >(tee "$LOG") 2>&1

echo "[ONSURE-MAIN-LOCK] mode=$MODE subject_commit=$HEAD_SHA subject_tree=$TREE_SHA"
if [[ "$MODE" == "PRE_CLEAN" ]]; then
  echo "[ONSURE-MAIN-LOCK] 1/9 current-main Java/JUnit + 160 DD fixture mechanics + activation security tests"
  export ONSURE_DD_VERIFY_OUT="$OUT_DIR/dd-manual"; bash scripts/run-dd-semantic-evaluator-manual-verification.sh
  DD_RECEIPT="$(ls -1t "$OUT_DIR"/dd-manual/dd-manual-*.json | head -1)"
  python3 - "$DD_RECEIPT" "$HEAD_SHA" "$TREE_SHA" <<'PY'
import hashlib,json,sys
p=json.load(open(sys.argv[1],encoding='utf-8')); x=dict(p); x.pop('receipt_digest',None); d=hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
assert p.get('contract')=='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V5'; assert p.get('receipt_digest')==d
assert p.get('source_commit_sha')==sys.argv[2]; assert p.get('source_tree_sha')==sys.argv[3]; assert p.get('verdict')=='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY'
c=p.get('claims') or {}; assert c.get('dd_authorized_route_execution_mechanics_count')==40; assert c.get('dd_schema_validator_execution_mechanics_count')==40; assert c.get('qualification_fixture_mechanics_executed_count')==160
PY
  echo "$DD_RECEIPT" > "$OUT_DIR/current-dd-manual-receipt.path"

  echo "[ONSURE-MAIN-LOCK] 2/9 stage exact frozen qualification subject"
  python3 scripts/stage-dd-qualification-bundle.py --source "$ONSURE_DD_QUALIFICATION_BUNDLE_SOURCE"

  echo "[ONSURE-MAIN-LOCK] 3/9 stage immutable 40 independent qualification receipts"
  python3 scripts/stage-dd-qualification-receipts.py --source "$ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE"
  export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$ROOT/.onsure/dd-independent-qualification/receipts"
  echo "$ONSURE_DD_QUALIFICATION_RECEIPTS_DIR" > "$OUT_DIR/current-qualification-receipts.path"
  python3 scripts/validate-dd-semantic-evaluator-qualifications.py --require-all-qualified

  echo "[ONSURE-MAIN-LOCK] 4/9 prove current compiled evaluator artifact matches independently-qualified artifact"
  python3 - <<'PY'
import hashlib,json,pathlib
m=json.loads(pathlib.Path('.onsure/dd-independent-qualification/frozen-bundle/bundle-manifest.json').read_text())
p=pathlib.Path('target/classes/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.class'); assert p.is_file(),'CURRENT_EVALUATOR_CLASS_MISSING'
assert hashlib.sha256(p.read_bytes()).hexdigest()==m['evaluator_artifact_sha256'],'CURRENT_MAIN_EVALUATOR_ARTIFACT_DIFFERS_FROM_QUALIFIED_SUBJECT'
PY

  echo "[ONSURE-MAIN-LOCK] 5/9 materialize qualified runtime activation"
  python3 scripts/materialize-dd-qualified-runtime-activation.py

  echo "[ONSURE-MAIN-LOCK] 6/9 stage immutable target evidence for current execution subject"
  STAGED_INDEX="$OUT_DIR/$RUN_ID.evidence-index.json"
  python3 scripts/stage-dd-evidence-index.py --input "$ONSURE_DD_EVIDENCE_INDEX_SOURCE" --output "$STAGED_INDEX"
  echo "$STAGED_INDEX" > "$OUT_DIR/current-evidence-index.path"; export ONSURE_DD_EVIDENCE_INDEX="$STAGED_INDEX"

  echo "[ONSURE-MAIN-LOCK] 7/9 re-run all 40 DD target semantic evaluations"
  bash scripts/run-dd-semantic-runtime-evidence.sh

  echo "[ONSURE-MAIN-LOCK] 8/9 execute post-delta closure through exact Pre-CLEAN Subject"
  export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"
  set +e; bash scripts/run-product-design-closure-post-delta.sh; CLOSURE_RC=$?; set -e
  python3 - "$HEAD_SHA" "$TREE_SHA" <<'PY'
import hashlib,json,sys,pathlib
p=pathlib.Path('.onsure/independent-clean/preclean-subject.json'); assert p.is_file(),'PRECLEAN_SUBJECT_MISSING'
d=json.loads(p.read_text()); x=dict(d); x.pop('subject_digest',None); calc=hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
assert d.get('contract')=='ONSURE_INDEPENDENT_CLEAN_PRECLEAN_SUBJECT_V2'; assert d.get('subject_digest')==calc
assert d.get('source_commit_sha')==sys.argv[1]; assert d.get('source_tree_sha')==sys.argv[2]
assert d.get('decision')=='READY_FOR_INDEPENDENT_CLEAN_NONFINAL'; assert not d.get('blocking_reasons')
print(json.dumps({'decision':'MAIN_PRECLEAN_SUBJECT_READY_NONFINAL','subject_digest':d['subject_digest'],'coverage_digest':d['coverage_digest']},sort_keys=True))
PY

  echo "[ONSURE-MAIN-LOCK] 9/9 STOP_FOR_INDEPENDENT_CLEAN_A_B"
  echo "Independent CLEAN A/B must be produced against the exact Pre-CLEAN Subject by two independent verifier lineages."
  exit 82
fi

if [[ "$MODE" != "FINALIZE_LOCK" ]]; then echo "Usage: $0 PRE_CLEAN|FINALIZE_LOCK" >&2; exit 2; fi

echo "[ONSURE-MAIN-LOCK] FINAL 1/5 restore staged qualification/runtime inputs and validate CLEAN A/B"
DD_RECEIPT="$(cat "$OUT_DIR/current-dd-manual-receipt.path")"; STAGED_INDEX="$(cat "$OUT_DIR/current-evidence-index.path")"; QDIR="$(cat "$OUT_DIR/current-qualification-receipts.path")"
export ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT"; export ONSURE_DD_EVIDENCE_INDEX="$STAGED_INDEX"; export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$QDIR"
python3 scripts/validate-independent-clean-twice.py --source-commit-sha "$HEAD_SHA" --source-tree-sha "$TREE_SHA"

echo "[ONSURE-MAIN-LOCK] FINAL 2/5 re-run full post-delta closure with CLEAN A/B present"
bash scripts/run-product-design-closure-post-delta.sh

echo "[ONSURE-MAIN-LOCK] FINAL 3/5 independent PR review must cover an ancestor feature subject"
REVIEW_HEAD="$(python3 - <<'PY'
import json
p=json.load(open('evidence/pr-review/pr-54-independent-review.json',encoding='utf-8')); print(p['reviewed_head_sha'])
PY
)"
python3 scripts/validate-pr-independent-review.py --expected-head-sha "$REVIEW_HEAD"; git merge-base --is-ancestor "$REVIEW_HEAD" "$HEAD_SHA"

echo "[ONSURE-MAIN-LOCK] FINAL 4/5 invoke main-only Design Lock issuer"
set +e; python3 scripts/issue-design-lock.py; LOCK_RC=$?; set -e

echo "[ONSURE-MAIN-LOCK] FINAL 5/5 require actual Design Lock receipt"
python3 - "$HEAD_SHA" "$TREE_SHA" <<'PY'
import json,sys
p=json.load(open('.onsure/design-baseline/design-lock-receipt.json',encoding='utf-8'))
assert p['subject_commit_sha']==sys.argv[1]; assert p['subject_tree_sha']==sys.argv[2]; assert p['design_lock'] is True,f"DESIGN_LOCK_HOLD:{p.get('blocking_reasons')}"
assert p['final_lock'] is False and p['production_go'] is False and p['commercial_go'] is False
print(json.dumps(p,ensure_ascii=False,sort_keys=True))
PY
if [[ "$LOCK_RC" != "0" ]]; then exit "$LOCK_RC"; fi
