#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
BRANCH="$(git branch --show-current)"
HEAD_SHA="$(git rev-parse HEAD)"
TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
if [[ "$BRANCH" != "main" ]]; then
  echo "ONSURE_MAIN_REVALIDATION_HOLD NOT_MAIN_REF:$BRANCH" >&2
  exit 80
fi
: "${ONSURE_DD_TARGET_IDENTITY:?ONSURE_DD_TARGET_IDENTITY is required}"
: "${ONSURE_DD_EXECUTION_PRINCIPAL:?ONSURE_DD_EXECUTION_PRINCIPAL is required}"
: "${ONSURE_DD_EXECUTION_ENVIRONMENT:?ONSURE_DD_EXECUTION_ENVIRONMENT is required}"
: "${ONSURE_DD_EVIDENCE_INDEX_SOURCE:?ONSURE_DD_EVIDENCE_INDEX_SOURCE is required for fresh main revalidation}"

OUT_DIR="${ONSURE_MAIN_REVALIDATION_OUT:-.onsure/main-design-lock-revalidation}"
mkdir -p "$OUT_DIR"
RUN_ID="main-lock-$(date -u +%Y%m%dT%H%M%SZ)"
LOG="$OUT_DIR/$RUN_ID.log"
exec > >(tee "$LOG") 2>&1

echo "[ONSURE-MAIN-LOCK] subject_commit=$HEAD_SHA subject_tree=$TREE_SHA"
echo "[ONSURE-MAIN-LOCK] 1/8 current-main Java/JUnit + 160 DD fixture mechanics + activation security tests"
export ONSURE_DD_VERIFY_OUT="$OUT_DIR/dd-manual"
bash scripts/run-dd-semantic-evaluator-manual-verification.sh
DD_RECEIPT="$(ls -1t "$OUT_DIR"/dd-manual/dd-manual-*.json | head -1)"
python3 - "$DD_RECEIPT" "$HEAD_SHA" "$TREE_SHA" <<'PY'
import hashlib,json,sys
p=json.load(open(sys.argv[1],encoding='utf-8'))
x=dict(p); x.pop('receipt_digest',None)
d=hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
assert p.get('contract')=='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V4'
assert p.get('receipt_digest')==d,'DD_MANUAL_RECEIPT_DIGEST_MISMATCH'
assert p.get('source_commit_sha')==sys.argv[2],'DD_MANUAL_RECEIPT_COMMIT_MISMATCH'
assert p.get('source_tree_sha')==sys.argv[3],'DD_MANUAL_RECEIPT_TREE_MISMATCH'
assert p.get('verdict')=='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY','DD_MANUAL_NOT_PASS'
PY

echo "[ONSURE-MAIN-LOCK] 2/8 rebuild frozen independent-qualification binding for main commit/tree"
ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT" python3 scripts/prepare-dd-independent-qualification-bundle.py
python3 scripts/validate-dd-semantic-evaluator-qualifications.py --require-all-qualified

echo "[ONSURE-MAIN-LOCK] 3/8 materialize qualified runtime activation for main commit/tree"
python3 scripts/materialize-dd-qualified-runtime-activation.py

echo "[ONSURE-MAIN-LOCK] 4/8 stage immutable target evidence index for main commit without mutating source custody"
STAGED_INDEX="$OUT_DIR/$RUN_ID.evidence-index.json"
python3 scripts/stage-dd-evidence-index.py --input "$ONSURE_DD_EVIDENCE_INDEX_SOURCE" --output "$STAGED_INDEX"
export ONSURE_DD_EVIDENCE_INDEX="$STAGED_INDEX"

echo "[ONSURE-MAIN-LOCK] 5/8 re-run all 40 DD target semantic runtime evaluations on main"
bash scripts/run-dd-semantic-runtime-evidence.sh

echo "[ONSURE-MAIN-LOCK] 6/8 full post-delta closure on main SHA"
ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT" bash scripts/run-product-design-closure-post-delta.sh

echo "[ONSURE-MAIN-LOCK] 7/8 independent review receipt must cover an ancestor PR head"
REVIEW_HEAD="$(python3 - <<'PY'
import json
p=json.load(open('evidence/pr-review/pr-54-independent-review.json',encoding='utf-8'))
print(p['reviewed_head_sha'])
PY
)"
python3 scripts/validate-pr-independent-review.py --expected-head-sha "$REVIEW_HEAD"
git merge-base --is-ancestor "$REVIEW_HEAD" "$HEAD_SHA"

echo "[ONSURE-MAIN-LOCK] 8/8 invoke main-only Design Lock issuer and require actual lock"
set +e
ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT" python3 scripts/issue-design-lock.py
LOCK_RC=$?
set -e
python3 - "$HEAD_SHA" "$TREE_SHA" <<'PY'
import json,sys
p=json.load(open('.onsure/design-baseline/design-lock-receipt.json',encoding='utf-8'))
assert p['subject_commit_sha']==sys.argv[1], 'DESIGN_LOCK_COMMIT_SUBJECT_MISMATCH'
assert p['subject_tree_sha']==sys.argv[2], 'DESIGN_LOCK_TREE_SUBJECT_MISMATCH'
assert p['design_lock'] is True, f"DESIGN_LOCK_HOLD:{p.get('blocking_reasons')}"
assert p['final_lock'] is False and p['production_go'] is False and p['commercial_go'] is False
print(json.dumps(p,ensure_ascii=False,sort_keys=True))
PY
if [[ "$LOCK_RC" != "0" ]]; then exit "$LOCK_RC"; fi
