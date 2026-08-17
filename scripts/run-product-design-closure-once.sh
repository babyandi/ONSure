#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

RU_ROOT="$ROOT/.onsure/requirement-universe"
CAND="$RU_ROOT/epoch-0003-candidate"
RECEIPT="$CAND/product-design-closure-one-shot.receipt.json"
mkdir -p "$CAND"

log(){ printf '[ONSURE-DESIGN-CLOSURE] %s\n' "$*"; }
fail(){ log "FAIL: $*"; exit 1; }

TMP="$(mktemp -d)"
restore_live(){
  set +e
  for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json global-trace-scan-report.json; do
    if [[ -f "$TMP/$f" ]]; then cp "$TMP/$f" "$RU_ROOT/$f"; else rm -f "$RU_ROOT/$f"; fi
  done
  rm -rf "$TMP"
}
trap restore_live EXIT

log '1/9 materialize full Requirement Authority Source Manifest with raw-byte SHA-256'
python3 scripts/materialize-requirement-authority-manifest.py | tee "$CAND/01-authority-materialization.stdout.json"

python3 - <<'PY'
import json, pathlib, sys
p=pathlib.Path('.onsure/requirement-universe/requirement-authority-source-manifest.json')
d=json.loads(p.read_text())
s=d['review_summary']
if s['unreviewed_count'] != 0 or s['disputed_count'] != 0:
    print(json.dumps({'gate':'AUTHORITY_POPULATION','status':'HOLD','review_summary':s}))
    sys.exit(20)
print(json.dumps({'gate':'AUTHORITY_POPULATION','status':'PASS_NONFINAL','review_summary':s,'population_digest':d['population_digest']}))
PY

log '2/9 generate EPOCH 0003 candidate run A'
python3 scripts/generate-requirement-universe.py --epoch-candidate | tee "$CAND/02-epoch-run-a.stdout.json"
cp "$CAND/requirement-universe-snapshot.json" "$TMP/epoch-a.json"

log '3/9 generate EPOCH 0003 candidate run B and prove deterministic digest'
python3 scripts/generate-requirement-universe.py --epoch-candidate | tee "$CAND/03-epoch-run-b.stdout.json"
cp "$CAND/requirement-universe-snapshot.json" "$TMP/epoch-b.json"
python3 - <<'PY'
import json, pathlib, sys
A=json.loads(pathlib.Path('/tmp/nonexistent').read_text()) if False else json.loads(pathlib.Path('.onsure/requirement-universe/epoch-0003-candidate/requirement-universe-snapshot.json').read_text())
# run A snapshot was preserved by shell in a temp path; compare using env-exported digest files below is intentionally handled by shell python invocation after copying.
print(json.dumps({'gate':'EPOCH_0003_RUN_B','requirement_manifest_digest':A['requirement_manifest_digest'],'authority_document_population_digest':A['authority_document_population_digest'],'requirement_count':len(A['requirement_ids'])}))
PY
python3 - "$TMP/epoch-a.json" "$TMP/epoch-b.json" <<'PY'
import json, pathlib, sys
A=json.loads(pathlib.Path(sys.argv[1]).read_text()); B=json.loads(pathlib.Path(sys.argv[2]).read_text())
keys=['requirement_manifest_digest','authority_document_population_digest','requirement_ids']
ok=all(A[k]==B[k] for k in keys)
print(json.dumps({'gate':'DETERMINISTIC_EPOCH_0003','status':'PASS_NONFINAL' if ok else 'HOLD','requirement_manifest_digest':B['requirement_manifest_digest'],'authority_document_population_digest':B['authority_document_population_digest'],'requirement_count':len(B['requirement_ids'])}))
if not ok: sys.exit(21)
PY

log '4/9 assert FR-FIN-01..22 gap-free in candidate denominator'
python3 - <<'PY'
import json, pathlib, sys
p=pathlib.Path('.onsure/requirement-universe/epoch-0003-candidate/requirement-universe-snapshot.json')
d=json.loads(p.read_text()); ids=set(d['requirement_ids'])
req={f'FR-FIN-{i:02d}' for i in range(1,23)}; missing=sorted(req-ids)
print(json.dumps({'gate':'FR_FIN_01_22','status':'PASS_NONFINAL' if not missing else 'HOLD','missing':missing}))
if missing: sys.exit(22)
PY

log '5/9 switch only local .onsure active view to EPOCH 0003 candidate for existing scanners'
for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json global-trace-scan-report.json; do
  [[ -f "$RU_ROOT/$f" ]] && cp "$RU_ROOT/$f" "$TMP/$f"
done
cp "$CAND/requirement-records.json" "$RU_ROOT/requirement-records.json"
cp "$CAND/requirement-universe-snapshot.json" "$RU_ROOT/requirement-universe-snapshot.json"
cp "$CAND/requirement-universe-generation-receipt.json" "$RU_ROOT/requirement-universe-generation-receipt.json"
cp "$CAND/explicit-id-cross-document-variants.json" "$RU_ROOT/explicit-id-cross-document-variants.json"
cp "$CAND/raw-extraction-evidence.json" "$RU_ROOT/raw-extraction-evidence.json"

log '6/9 rerun global trace + reverse orphan + design coverage + lock preflight on candidate view'
python3 scripts/scan-global-trace-closure.py | tee "$CAND/06-global-trace.stdout.json"
cp "$RU_ROOT/global-trace-scan-report.json" "$CAND/global-trace-scan-report.json"
python3 scripts/scan-reverse-orphan.py | tee "$CAND/reverse-orphan-scan-report.json"
python3 scripts/validate-final-product-requirements.py | tee "$CAND/validate-final-product-requirements.log"
python3 scripts/validate-design-coverage.py | tee "$CAND/validate-design-coverage.log"
set +e
python3 scripts/validate-global-lock-preflight.py > "$CAND/validate-global-lock-preflight.log" 2>&1
LOCK_RC=$?
set -e

log '7/9 restore live EPOCH 0002 view before independent assurance'
restore_live
trap - EXIT

log '8/9 run independent/local assurance twice'
set +e
bash scripts/run-local-assurance-twice.sh > "$CAND/08-independent-clean-twice.log" 2>&1
CLEAN_RC=$?
set -e

log '9/9 aggregate final nonfinal receipt; human-authority blockers remain fail-closed'
python3 - "$LOCK_RC" "$CLEAN_RC" <<'PY'
import json, pathlib, sys, datetime
cand=pathlib.Path('.onsure/requirement-universe/epoch-0003-candidate')
snap=json.loads((cand/'requirement-universe-snapshot.json').read_text())
trace=json.loads((cand/'global-trace-scan-report.json').read_text())
rev=json.loads((cand/'reverse-orphan-scan-report.json').read_text())
manifest=json.loads(pathlib.Path('.onsure/requirement-universe/requirement-authority-source-manifest.json').read_text())
lock_rc=int(sys.argv[1]); clean_rc=int(sys.argv[2])
stale=sum(v.get('cites_a_stale_nonexistent_id',0) for v in rev.get('categories',{}).values())
blocking=[]
if manifest['review_summary']['unreviewed_count']: blocking.append('UNREVIEWED_AUTHORITY_SOURCES')
if manifest['review_summary']['disputed_count']: blocking.append('DISPUTED_AUTHORITY_SOURCES')
if trace['orphans']['p0']: blocking.append('P0_FORWARD_ORPHANS')
if stale: blocking.append('STALE_REVERSE_ORPHAN_REFERENCES')
if lock_rc != 0: blocking.append('GLOBAL_LOCK_PREFLIGHT_NOT_PASS')
if clean_rc != 0: blocking.append('INDEPENDENT_CLEAN_TWICE_NOT_PASS')
# Human design-authority contradiction confirmation is deliberately not auto-approved.
blocking.append('P1_POLICY_BINDINGS_REQUIRE_HUMAN_DESIGN_AUTHORITY_CONFIRMATION')
receipt={
 'contract':'ONSURE_PRODUCT_DESIGN_CLOSURE_ONE_SHOT_RECEIPT_V1',
 'generated_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'requirement_epoch_candidate':'EPOCH::REQUIREMENT::0003::CANDIDATE',
 'requirement_manifest_digest':snap['requirement_manifest_digest'],
 'authority_document_population_digest':snap['authority_document_population_digest'],
 'requirement_count':len(snap['requirement_ids']),
 'authority_review_summary':manifest['review_summary'],
 'forward_trace':{'p0':len(trace['orphans']['p0']),'p1':len(trace['orphans']['p1']),'trace_completeness_ratio':trace['trace_completeness_ratio']},
 'reverse_orphan_stale_reference_count':stale,
 'global_lock_preflight_rc':lock_rc,
 'independent_clean_twice_rc':clean_rc,
 'blocking_reasons':blocking,
 'decision':'DESIGN_CLOSURE_CANDIDATE' if not blocking else 'HOLD_NONFINAL',
 'design_lock':False,
 'final_claim_allowed':False
}
(cand/'product-design-closure-one-shot.receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n')
print(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True))
PY

log "receipt: $RECEIPT"
