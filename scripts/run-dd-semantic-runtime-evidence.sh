#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

: "${ONSURE_DD_TARGET_IDENTITY:?ONSURE_DD_TARGET_IDENTITY is required}"
: "${ONSURE_DD_EXECUTION_PRINCIPAL:?ONSURE_DD_EXECUTION_PRINCIPAL is required}"
: "${ONSURE_DD_EXECUTION_ENVIRONMENT:?ONSURE_DD_EXECUTION_ENVIRONMENT is required}"

RAW="${ONSURE_DD_RUNTIME_RAW:-.onsure/dd-runtime/raw-execution.json}"
TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
COMMIT_SHA="$(git rev-parse HEAD)"

echo "[ONSURE-DD-RUNTIME] 1/6 validate 40/40 independent evaluator qualification"
python3 scripts/validate-dd-semantic-evaluator-qualifications.py --require-all-qualified

echo "[ONSURE-DD-RUNTIME] 2/6 materialize receipt-backed 40/40 runtime activation"
python3 scripts/materialize-dd-qualified-runtime-activation.py

echo "[ONSURE-DD-RUNTIME] 3/6 validate current evidence-index subject"
python3 - "$COMMIT_SHA" "$TREE_SHA" <<'PY'
import json,pathlib,sys
p=pathlib.Path('.onsure/dd-runtime/evidence-index.json')
if not p.is_file(): raise SystemExit('DD_EVIDENCE_INDEX_MISSING')
d=json.loads(p.read_text(encoding='utf-8'))
assert d.get('contract')=='ONSURE_DD_EVIDENCE_INDEX_V2','DD_EVIDENCE_INDEX_CONTRACT_NOT_V2'
assert d.get('source_commit_sha')==sys.argv[1],'DD_EVIDENCE_INDEX_COMMIT_MISMATCH'
assert d.get('source_tree_sha')==sys.argv[2],'DD_EVIDENCE_INDEX_TREE_MISMATCH'
assert d.get('final_claim_allowed') is False
covered=set()
for r in d.get('rows',[]): covered.update(r.get('dd_ids') or [])
expected={f'DD-{i:03d}' for i in range(1,41)}
missing=sorted(expected-covered)
if missing: raise SystemExit('DD_EVIDENCE_INDEX_DD_COVERAGE_MISSING:'+','.join(missing))
PY

echo "[ONSURE-DD-RUNTIME] 4/6 compile and execute all 40 DD operations without GitHub Actions"
mvn -B -DskipTests compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=kr.co.oruda.onsure.platform.DdSemanticRuntimeEvidenceMain \
  -Dexec.args="$ROOT $RAW"

echo "[ONSURE-DD-RUNTIME] 5/6 materialize 40 runtime receipts and status"
python3 scripts/materialize-dd-semantic-runtime-evidence.py \
  --raw "$RAW" \
  --target-identity "$ONSURE_DD_TARGET_IDENTITY" \
  --execution-principal "$ONSURE_DD_EXECUTION_PRINCIPAL" \
  --execution-environment "$ONSURE_DD_EXECUTION_ENVIRONMENT"

echo "[ONSURE-DD-RUNTIME] 6/6 require exact 40/40 PASS_NONFINAL current runtime evidence"
python3 scripts/validate-dd-semantic-runtime-evidence.py --source-tree-sha "$TREE_SHA" --require-all-pass
