#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
RU="$ROOT/.onsure/requirement-universe"
CAND="$RU/epoch-0003-candidate"
mkdir -p "$CAND"
log(){ printf '[ONSURE-POST-DELTA] %s\n' "$*"; }

log '0/13 validate reconciled design-document authority registry'
python3 scripts/validate-design-document-authority.py | tee "$CAND/design-document-authority.log"

log '1/13 validate uncontaminated exact-SHA independent Design Discovery saturation'
python3 scripts/validate-design-discovery-saturation.py | tee "$CAND/design-discovery-saturation.log"

log '2/13 materialize exact design inventory and verify two-run reconstructability'
python3 scripts/materialize-design-artifact-inventory.py | tee "$CAND/design-artifact-inventory.log"
python3 scripts/verify-design-baseline-reconstructability.py | tee "$CAND/design-reconstructability.log"

TMP="$(mktemp -d)"
restore(){
  set +e
  for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json global-trace-scan-report.json; do
    if [[ -f "$TMP/$f" ]]; then cp "$TMP/$f" "$RU/$f"; else rm -f "$RU/$f"; fi
  done
  rm -rf "$TMP"
}
trap restore EXIT

log '3/13 materialize post-reconciliation authority with raw SHA-256'
python3 scripts/materialize-product-design-authority.py | tee "$CAND/post-delta-authority.log"
python3 - <<'PY'
import json,pathlib,sys
m=json.loads(pathlib.Path('.onsure/requirement-universe/requirement-authority-source-manifest.json').read_text())
s=m['review_summary']
if s['unreviewed_count'] or s['disputed_count']:
    print(json.dumps({'gate':'AUTHORITY','status':'HOLD','summary':s})); sys.exit(40)
print(json.dumps({'gate':'AUTHORITY','status':'PASS_NONFINAL','summary':s,'digest':m['population_digest']}))
PY

log '4/13 generate Product Design EPOCH 0003 twice and compare deterministic denominator'
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

log '5/13 activate candidate view locally for scanners; historical live evidence restored by trap'
for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json global-trace-scan-report.json; do [[ -f "$RU/$f" ]] && cp "$RU/$f" "$TMP/$f"; done
for f in requirement-records.json requirement-universe-snapshot.json requirement-universe-generation-receipt.json explicit-id-cross-document-variants.json raw-extraction-evidence.json; do cp "$CAND/$f" "$RU/$f"; done

log '6/13 run forward and reverse trace/orphan scans'
python3 scripts/scan-global-trace-closure.py | tee "$CAND/global-trace.log"
cp "$RU/global-trace-scan-report.json" "$CAND/global-trace-scan-report.json"
python3 scripts/scan-reverse-orphan-product-design.py | tee "$CAND/reverse-orphan.log"
cp "$RU/product-design-reverse-orphan-scan-report.json" "$CAND/product-design-reverse-orphan-scan-report.json"

log '7/13 run requirement/design/runtime/human prelock validators'
set +e
python3 scripts/validate-final-product-requirements.py > "$CAND/final-product-requirements.log" 2>&1; FINAL_REQ_RC=$?
python3 scripts/validate-design-coverage.py > "$CAND/design-coverage.log" 2>&1; COVERAGE_RC=$?
python3 scripts/validate-dd-granular-vertical-trace.py > "$CAND/dd-granular-trace.log" 2>&1; GRANULAR_RC=$?
python3 scripts/validate-human-design-authority-decisions.py > "$CAND/human-design-authority.log" 2>&1; HUMAN_RC=$?
python3 scripts/validate-global-lock-preflight.py > "$CAND/global-lock-preflight.log" 2>&1; LOCK_RC=$?
set -e

log '8/13 restore historical live EPOCH before reproducibility execution'
restore
trap - EXIT

log '9/13 execute local assurance twice as reproducibility evidence only'
set +e
bash scripts/run-local-assurance-twice.sh > "$CAND/local-reproducibility-twice.log" 2>&1; LOCAL_TWICE_RC=$?
set -e

log '10/13 validate tracked independent CLEAN A/B evidence separately'
set +e
python3 scripts/validate-independent-clean-twice.py > "$CAND/independent-clean-twice.log" 2>&1; INDEP_CLEAN_RC=$?
set -e

log '11/13 aggregate blocker dimensions without allowing CLEAN to override design gaps'
python3 - "$FINAL_REQ_RC" "$COVERAGE_RC" "$GRANULAR_RC" "$HUMAN_RC" "$LOCK_RC" "$LOCAL_TWICE_RC" "$INDEP_CLEAN_RC" <<'PY'
import json,pathlib,sys,datetime
c=pathlib.Path('.onsure/requirement-universe/epoch-0003-candidate')
s=json.loads((c/'requirement-universe-snapshot.json').read_text()); t=json.loads((c/'global-trace-scan-report.json').read_text()); r=json.loads((c/'product-design-reverse-orphan-scan-report.json').read_text())
inv=json.loads(pathlib.Path('.onsure/design-baseline/design-artifact-inventory.json').read_text())
rec=json.loads(pathlib.Path('.onsure/design-baseline/reconstructability-receipt.json').read_text())
fr,cv,gr,hu,lk,lr,ic=map(int,sys.argv[1:]); blockers=[]
if t['orphans']['p0']: blockers.append('P0_FORWARD_ORPHANS')
if r['stale_reference_count']: blockers.append('STALE_REVERSE_REFERENCES')
if fr: blockers.append('FINAL_PRODUCT_REQUIREMENT_VALIDATION_NOT_PASS')
if cv: blockers.append('DESIGN_COVERAGE_NOT_PASS')
if gr: blockers.append('DD_GRANULAR_RUNTIME_TRACE_NOT_CLOSED')
if hu: blockers.append('HUMAN_DESIGN_AUTHORITY_DECISIONS_OPEN')
if lk: blockers.append('GLOBAL_LOCK_PREFLIGHT_NOT_PASS')
if lr: blockers.append('LOCAL_REPRODUCIBILITY_TWICE_NOT_PASS')
if ic: blockers.append('INDEPENDENT_CLEAN_TWICE_NOT_PASS')
if inv.get('missing_registered_paths'): blockers.append('DESIGN_ARTIFACT_INVENTORY_INCOMPLETE')
if not rec.get('deterministic_two_run'): blockers.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
receipt={'contract':'ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V3','generated_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'epoch':'EPOCH::REQUIREMENT::0003::CANDIDATE','requirement_count':len(s['requirement_ids']),'requirement_manifest_digest':s['requirement_manifest_digest'],'authority_population_digest':s['authority_document_population_digest'],'design_artifact_population_digest':inv['population_digest'],'design_reconstructable':rec['deterministic_two_run'],'dd_count':len([x for x in s['requirement_ids'] if x.startswith('DD-')]),'forward_p0_orphans':len(t['orphans']['p0']),'forward_p1_orphans':len(t['orphans']['p1']),'reverse_stale_references':r['stale_reference_count'],'validator_rcs':{'final_requirement':fr,'coverage':cv,'granular_trace':gr,'human_authority':hu,'lock_preflight':lk,'local_reproducibility_twice':lr,'independent_clean_twice':ic},'blocking_reasons':blockers,'decision':'DESIGN_CLOSURE_CANDIDATE_NONFINAL' if not blockers else 'HOLD_NONFINAL','design_lock':False,'final_claim_allowed':False}
(c/'post-reconciliation-product-design-closure-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n'); print(json.dumps(receipt,ensure_ascii=False,sort_keys=True))
PY

log '12/13 prelock evidence aggregation complete'
log '13/13 no Design Lock/Final/GO claim is emitted by this runner; issue-design-lock.py is a separate main-only gate'
