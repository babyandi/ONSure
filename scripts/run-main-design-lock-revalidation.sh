#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
BRANCH="$(git branch --show-current)"
HEAD_SHA="$(git rev-parse HEAD)"
if [[ "$BRANCH" != "main" ]]; then
  echo "ONSURE_MAIN_REVALIDATION_HOLD NOT_MAIN_REF:$BRANCH" >&2
  exit 80
fi

OUT_DIR="${ONSURE_MAIN_REVALIDATION_OUT:-.onsure/main-design-lock-revalidation}"
mkdir -p "$OUT_DIR"
RUN_ID="main-lock-$(date -u +%Y%m%dT%H%M%SZ)"
LOG="$OUT_DIR/$RUN_ID.log"
exec > >(tee "$LOG") 2>&1

echo "[ONSURE-MAIN-LOCK] subject=$HEAD_SHA"
echo "[ONSURE-MAIN-LOCK] 1/5 current-head Java/JUnit + 160 DD fixture mechanics"
export ONSURE_DD_VERIFY_OUT="$OUT_DIR/dd-manual"
bash scripts/run-dd-semantic-evaluator-manual-verification.sh
DD_RECEIPT="$(ls -1t "$OUT_DIR"/dd-manual/dd-manual-*.json | head -1)"
python3 - "$DD_RECEIPT" "$HEAD_SHA" <<'PY'
import json,sys
p=json.load(open(sys.argv[1],encoding='utf-8'))
assert p['source_tree_sha']==sys.argv[2], 'DD_MANUAL_RECEIPT_HEAD_MISMATCH'
assert p['verdict']=='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY', 'DD_MANUAL_NOT_PASS'
PY

echo "[ONSURE-MAIN-LOCK] 2/5 full post-delta closure on main SHA"
bash scripts/run-product-design-closure-post-delta.sh

echo "[ONSURE-MAIN-LOCK] 3/5 independent review receipt must cover an ancestor PR head"
REVIEW_HEAD="$(python3 - <<'PY'
import json
p=json.load(open('evidence/pr-review/pr-54-independent-review.json',encoding='utf-8'))
print(p['reviewed_head_sha'])
PY
)"
python3 scripts/validate-pr-independent-review.py --expected-head-sha "$REVIEW_HEAD"
git merge-base --is-ancestor "$REVIEW_HEAD" "$HEAD_SHA"

echo "[ONSURE-MAIN-LOCK] 4/5 invoke main-only Design Lock issuer with current-head manual receipt"
set +e
ONSURE_DD_MANUAL_VERIFICATION_RECEIPT="$DD_RECEIPT" python3 scripts/issue-design-lock.py
LOCK_RC=$?
set -e

echo "[ONSURE-MAIN-LOCK] 5/5 require actual lock receipt"
python3 - "$HEAD_SHA" <<'PY'
import json,sys
p=json.load(open('.onsure/design-baseline/design-lock-receipt.json',encoding='utf-8'))
assert p['subject_commit_sha']==sys.argv[1], 'DESIGN_LOCK_SUBJECT_MISMATCH'
assert p['design_lock'] is True, f"DESIGN_LOCK_HOLD:{p.get('blocking_reasons')}"
assert p['final_lock'] is False and p['production_go'] is False and p['commercial_go'] is False
print(json.dumps(p,ensure_ascii=False,sort_keys=True))
PY

if [[ "$LOCK_RC" != "0" ]]; then exit "$LOCK_RC"; fi
