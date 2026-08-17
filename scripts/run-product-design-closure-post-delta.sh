#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
RU="$ROOT/.onsure/requirement-universe"
CAND="$RU/epoch-0003-candidate"
mkdir -p "$CAND"
log(){ printf '[ONSURE-POST-DELTA] %s\n' "$*"; }

log '0/8 validate independent Design Discovery saturation'
python3 scripts/validate-design-discovery-saturation.py

TMP="$(mktemp -d)"
restore(){
  set +e
  for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json global-trace-scan-report.json; do
    if [[ -f "$TMP/$f" ]]; then cp "$TMP/$f" "$RU/$f"; else rm -f "$RU/$f"; fi
  done
  rm -rf "$TMP"
}
trap restore EXIT

log '1/8 materialize post-delta authority with raw SHA-256'
python3 scripts/materialize-product-design-authority.py | tee "$CAND/post-delta-authority.log"
python3 - <<'PY'
import json,pathlib,sys
m=json.loads(pathlib.Path('.onsure/requirement-universe/requirement-authority-source-manifest.json').read_text())
s=m['review_summary']
if s['unreviewed_count'] or s['disputed_count']: print(json.dumps({'gate':'AUTHORITY','status':'HOLD','summary':s})); sys.exit(40)
print(json.dumps({'gate':'AUTHORITY','status':'PASS_NONFINAL','summary':s,'digest':m['population_digest']}))
PY

log '2/8 generate Product Design EPOCH 0003 twice and compare deterministic denominator'
python3 scripts/generate-product-design-epoch-0003.py | tee "$CAND/epoch-a.log"
cp "$CAND/requirement-universe-snapshot.json" "$TMP/a.json"
python3 scripts/generate-product-design-epoch-0003.py | tee "$CAND/epoch-b.log"
cp "$CAND/requirement-universe-snapshot.json" "$TMP/b.json"
python3 - "$TMP/a.json" "$TMP/b.json" <<'PY'
import json,pathlib,sys
A=json.loads(pathlib.Path(sys.argv[1]).read_text()); B=json.loads(pathlib.Path(sys.argv[2]).read_text())
keys=['requirement_manifest_digest','authority_document_population_digest','requirement_ids']
ok=all(A[k]==B[k] for k in keys)
ids=set(B['requirement_ids']); dd={f'DD-{i:03d}' for i in range(1,41)}; fin={f'FR-FIN-{i:02d}' for i in range(1,23)}
missing=sorted((dd|fin)-ids)
print(json.dumps({'gate':'EPOCH_0003','deterministic':ok,'missing_required_ids':missing,'count':len(ids),'digest':B['requirement_manifest_digest']}))
if not ok or missing: sys.exit(41)
PY

log '3/8 activate candidate view locally for scanners; original live evidence is restored by trap'
for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json global-trace-scan-report.json; do [[ -f "$RU/$f" ]] && cp "$RU/$f" "$TMP/$f"; done
for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json; do cp "$CAND/$f" "$RU/$f"; done

log '4/8 run forward and reverse trace/orphan scans'
python3 scripts/scan-global-trace-closure.py | tee "$CAND/global-trace.log"
cp "$RU/global-trace-scan-report.json" "$CAND/global-trace-scan-report.json"
python3 scripts/scan-reverse-orphan-product-design.py | tee "$CAND/reverse-orphan.log"
cp "$RU/product-design-reverse-orphan-scan-report.json" "$CAND/product-design-reverse-orphan-scan-report.json"

log '5/8 run requirement/design validators'
python3 scripts/validate-final-product-requirements.py | tee "$CAND/final-product-requirements.log"
python3 scripts/validate-design-coverage.py | tee "$CAND/design-coverage.log"
set +e
python3 scripts/validate-global-lock-preflight.py > "$CAND/global-lock-preflight.log" 2>&1
LOCK_RC=$?
set -e

log '6/8 restore historical live EPOCH 0002 before independent assurance'
restore
trap - EXIT

log '7/8 run independent/local assurance twice'
set +e
bash scripts/run-local-assurance-twice.sh > "$CAND/independent-clean-twice.log" 2>&1
CLEAN_RC=$?
set -e

log '8/8 aggregate fail-closed receipt'
python3 - "$LOCK_RC" "$CLEAN_RC" <<'PY'
import json,pathlib,sys,datetime
c=pathlib.Path('.onsure/requirement-universe/epoch-0003-candidate')
s=json.loads((c/'requirement-universe-snapshot.json').read_text()); t=json.loads((c/'global-trace-scan-report.json').read_text()); r=json.loads((c/'product-design-reverse-orphan-scan-report.json').read_text())
lock=int(sys.argv[1]); clean=int(sys.argv[2]); blockers=[]
if t['orphans']['p0']: blockers.append('P0_FORWARD_ORPHANS')
if r['stale_reference_count']: blockers.append('STALE_REVERSE_REFERENCES')
if lock: blockers.append('GLOBAL_LOCK_PREFLIGHT_NOT_PASS')
if clean: blockers.append('INDEPENDENT_CLEAN_TWICE_NOT_PASS')
receipt={'contract':'ONSURE_POST_DELTA_PRODUCT_DESIGN_CLOSURE_RECEIPT_V1','generated_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'epoch':'EPOCH::REQUIREMENT::0003::CANDIDATE','requirement_count':len(s['requirement_ids']),'requirement_manifest_digest':s['requirement_manifest_digest'],'authority_population_digest':s['authority_document_population_digest'],'dd_count':len([x for x in s['requirement_ids'] if x.startswith('DD-')]),'forward_p0_orphans':len(t['orphans']['p0']),'forward_p1_orphans':len(t['orphans']['p1']),'reverse_stale_references':r['stale_reference_count'],'global_lock_preflight_rc':lock,'independent_clean_twice_rc':clean,'blocking_reasons':blockers,'decision':'DESIGN_CLOSURE_CANDIDATE_NONFINAL' if not blockers else 'HOLD_NONFINAL','design_lock':False,'final_claim_allowed':False}
(c/'post-delta-product-design-closure-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n'); print(json.dumps(receipt,ensure_ascii=False,sort_keys=True))
PY
