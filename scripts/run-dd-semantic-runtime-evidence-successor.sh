#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
: "${ONSURE_DD_TARGET_IDENTITY:?ONSURE_DD_TARGET_IDENTITY is required}"
: "${ONSURE_DD_EXECUTION_PRINCIPAL:?ONSURE_DD_EXECUTION_PRINCIPAL is required}"
: "${ONSURE_DD_EXECUTION_ENVIRONMENT:?ONSURE_DD_EXECUTION_ENVIRONMENT is required}"

RAW="${ONSURE_DD_RUNTIME_RAW:-.onsure/dd-runtime-successor/raw-execution.json}"
if [[ -n "${ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE:-}" ]]; then
  python3 scripts/stage-dd-qualification-receipts.py --source "$ONSURE_DD_QUALIFICATION_RECEIPTS_SOURCE"
  export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$ROOT/.onsure/dd-independent-qualification/receipts"
elif [[ -z "${ONSURE_DD_QUALIFICATION_RECEIPTS_DIR:-}" ]]; then
  export ONSURE_DD_QUALIFICATION_RECEIPTS_DIR="$ROOT/receipts/dd-semantic-evaluator-qualification"
fi
if [[ -n "${ONSURE_DD_EVIDENCE_INDEX_SOURCE:-}" ]]; then
  STAGED_INDEX="${ONSURE_DD_EVIDENCE_INDEX_STAGED:-.onsure/dd-runtime/evidence-index.json}"
  python3 scripts/stage-dd-evidence-index.py --input "$ONSURE_DD_EVIDENCE_INDEX_SOURCE" --output "$STAGED_INDEX"
  EVIDENCE_INDEX="$STAGED_INDEX"
else
  EVIDENCE_INDEX="${ONSURE_DD_EVIDENCE_INDEX:-.onsure/dd-runtime/evidence-index.json}"
fi
export ONSURE_DD_EVIDENCE_INDEX="$EVIDENCE_INDEX"
TREE_SHA="$(git rev-parse 'HEAD^{tree}')"; COMMIT_SHA="$(git rev-parse HEAD)"

echo "[ONSURE-DD-RUNTIME-SUCCESSOR] 1/6 validate fresh 42/42 independent evaluator qualification"
python3 scripts/validate-dd-semantic-evaluator-qualifications-successor.py

echo "[ONSURE-DD-RUNTIME-SUCCESSOR] 2/6 materialize receipt-backed 42/42 runtime activation"
python3 scripts/materialize-dd-qualified-runtime-activation-successor.py

echo "[ONSURE-DD-RUNTIME-SUCCESSOR] 3/6 validate current evidence-index covers DD-001..042"
python3 - "$EVIDENCE_INDEX" "$COMMIT_SHA" "$TREE_SHA" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1]); p=p if p.is_absolute() else pathlib.Path.cwd()/p
if not p.is_file(): raise SystemExit('DD_EVIDENCE_INDEX_MISSING')
d=json.loads(p.read_text(encoding='utf-8'))
assert d.get('contract')=='ONSURE_DD_EVIDENCE_INDEX_V2','DD_EVIDENCE_INDEX_CONTRACT_NOT_V2'
assert d.get('source_commit_sha')==sys.argv[2],'DD_EVIDENCE_INDEX_COMMIT_MISMATCH'
assert d.get('source_tree_sha')==sys.argv[3],'DD_EVIDENCE_INDEX_TREE_MISMATCH'
assert d.get('path_base') in ('WORKSPACE_ROOT','INDEX_DIRECTORY'),'DD_EVIDENCE_INDEX_PATH_BASE_INVALID'
assert d.get('final_claim_allowed') is False
covered=set()
for r in d.get('rows',[]): covered.update(r.get('dd_ids') or [])
expected={f'DD-{i:03d}' for i in range(1,43)}
missing=sorted(expected-covered)
if missing: raise SystemExit('DD_EVIDENCE_INDEX_DD_COVERAGE_MISSING:'+','.join(missing))
PY

echo "[ONSURE-DD-RUNTIME-SUCCESSOR] 4/6 compile and execute all 42 DD operations without GitHub Actions"
mkdir -p "$(dirname "$RAW")"
mvn -B -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=kr.co.oruda.onsure.platform.DdSemanticRuntimeEvidenceMain \
  -Dexec.args="$ROOT $RAW"

echo "[ONSURE-DD-RUNTIME-SUCCESSOR] 5/6 materialize 42 runtime receipts/status"
python3 scripts/materialize-dd-semantic-runtime-evidence-successor.py \
  --raw "$RAW" --evidence-index "$EVIDENCE_INDEX" \
  --target-identity "$ONSURE_DD_TARGET_IDENTITY" \
  --execution-principal "$ONSURE_DD_EXECUTION_PRINCIPAL" \
  --execution-environment "$ONSURE_DD_EXECUTION_ENVIRONMENT"

echo "[ONSURE-DD-RUNTIME-SUCCESSOR] 6/6 require exact 42/42 PASS_NONFINAL current runtime evidence"
python3 scripts/validate-dd-semantic-runtime-evidence-successor.py \
  --source-commit-sha "$COMMIT_SHA" --source-tree-sha "$TREE_SHA" --require-all-pass
