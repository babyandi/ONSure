#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
RU="$ROOT/.onsure/requirement-universe"
CAND="$RU/epoch-0003-candidate"
MATRIX="$ROOT/contracts/design-capability-coverage.candidate.v2.json"
mkdir -p "$CAND"
log(){ printf '[ONSURE-POST-DELTA] %s\n' "$*"; }

log '0/12 validate reconciled design-document authority registry'
python3 scripts/validate-design-document-authority.py | tee "$CAND/design-document-authority.log"

log '1/12 validate uncontaminated independent Design Discovery saturation evidence'
python3 scripts/validate-design-discovery-saturation.py | tee "$CAND/design-discovery-saturation.log"

log '2/12 materialize exact design inventory and verify two-run reconstructability'
python3 scripts/materialize-design-artifact-inventory.py | tee "$CAND/design-artifact-inventory.log"
python3 scripts/verify-design-baseline-reconstructability.py | tee "$CAND/design-reconstructability.log"

log '3/12 materialize post-reconciliation authority with raw SHA-256'
python3 scripts/materialize-product-design-authority.py | tee "$CAND/post-delta-authority.log"
python3 - <<'PY'
import json,pathlib,sys
m=json.loads(pathlib.Path('.onsure/requirement-universe/requirement-authority-source-manifest.json').read_text())
s=m['review_summary']
eu=s.get('eligible_unreviewed_count',s.get('unreviewed_count',0)); ed=s.get('eligible_disputed_count',s.get('disputed_count',0))
if eu or ed:
    print(json.dumps({'gate':'AUTHORITY','status':'HOLD','eligible_unreviewed_count':eu,'eligible_disputed_count':ed,'summary':s})); sys.exit(40)
print(json.dumps({'gate':'AUTHORITY','status':'PASS_NONFINAL','eligible_unreviewed_count':eu,'eligible_disputed_count':ed,'total_unreviewed_disclosure':s.get('unreviewed_count',0),'summary':s,'digest':m['population_digest']}))
PY

log '4/12 generate Product Design EPOCH 0003 twice and compare deterministic denominator'
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
python3 scripts/generate-product-design-epoch-0003.py | tee "$CAND/epoch-a.log"
cp "$CAND/requirement-universe-snapshot.json" "$TMP/a.json"
python3 scripts/generate-product-design-epoch-0003.py | tee "$CAND/epoch-b.log"
cp "$CAND/requirement-universe-snapshot.json" "$TMP/b.json"
python3 - "$TMP/a.json" "$TMP/b.json" <<'PY'
import json,pathlib,sys
A=json.loads(pathlib.Path(sys.argv[1]).read_text()); B=json.loads(pathlib.Path(sys.argv[2]).read_text())
keys=['requirement_manifest_digest','authority_document_population_digest','requirement_ids']; ok=all(A[k]==B[k] for k in keys)
ids=set(B['requirement_ids']); expected={f'DD-{i:03d}' for i in range(1,41)}|{f'FR-FIN-{i:02d}' for i in range(1,23)}; missing=sorted(expected-ids)
print(json.dumps({'gate':'EPOCH_0003','deterministic':ok,'missing_required_ids':missing,'count':len(ids),'digest':B['requirement_manifest_digest']}))
if not ok or missing: sys.exit(41)
PY
rm -rf "$TMP"; trap - EXIT

log '5/12 scan candidate EPOCH directly; historical live Requirement Universe is not mutated'
python3 scripts/scan-global-trace-closure.py --universe-dir "$CAND" | tee "$CAND/global-trace.log"
python3 scripts/scan-reverse-orphan-product-design.py --universe-dir "$CAND" | tee "$CAND/reverse-orphan.log"

log '6/12 run requirement/design/DD machine/evaluator/human validators'
set +e
python3 scripts/validate-final-product-requirements.py --self-test > "$CAND/final-product-requirements.log" 2>&1; FINAL_REQ_RC=$?
python3 scripts/validate-design-coverage.py --matrix "$MATRIX" --root "$ROOT" --self-test --output "$CAND/design-coverage-report.json" > "$CAND/design-coverage.log" 2>&1; COVERAGE_RC=$?
python3 scripts/validate-dd-machine-definition.py > "$CAND/dd-machine-definition.log" 2>&1; DD_MACHINE_RC=$?
python3 scripts/validate-dd-granular-vertical-trace.py > "$CAND/dd-granular-trace.log" 2>&1; GRANULAR_RC=$?
python3 scripts/validate-dd-semantic-evaluator-qualifications.py --require-all-qualified > "$CAND/dd-evaluator-qualification.log" 2>&1; DD_QUAL_RC=$?
python3 scripts/validate-human-design-authority-decisions.py > "$CAND/human-design-authority.log" 2>&1; HUMAN_RC=$?
set -e

log '7/12 execute local assurance twice as reproducibility evidence only'
set +e
bash scripts/run-local-assurance-twice.sh > "$CAND/local-reproducibility-twice.log" 2>&1; LOCAL_TWICE_RC=$?
set -e

log '8/12 validate tracked independent CLEAN A/B evidence separately'
set +e
python3 scripts/validate-independent-clean-twice.py > "$CAND/independent-clean-twice.log" 2>&1; INDEP_CLEAN_RC=$?
set -e

log '9/12 execute candidate-native preflight; no live-universe preflight is used'
set +e
python3 scripts/validate-product-design-candidate-preflight.py > "$CAND/candidate-preflight.log" 2>&1; PREFLIGHT_RC=$?
set -e

log '10/12 aggregate blocker dimensions without allowing self-validation to override evaluator qualification, independent or human gates'
python3 - "$FINAL_REQ_RC" "$COVERAGE_RC" "$DD_MACHINE_RC" "$GRANULAR_RC" "$DD_QUAL_RC" "$HUMAN_RC" "$LOCAL_TWICE_RC" "$INDEP_CLEAN_RC" "$PREFLIGHT_RC" <<'PY'
import json,pathlib,sys,datetime
c=pathlib.Path('.onsure/requirement-universe/epoch-0003-candidate')
s=json.loads((c/'requirement-universe-snapshot.json').read_text()); t=json.loads((c/'global-trace-scan-report.json').read_text()); r=json.loads((c/'product-design-reverse-orphan-scan-report.json').read_text())
inv=json.loads(pathlib.Path('.onsure/design-baseline/design-artifact-inventory.json').read_text()); rec=json.loads(pathlib.Path('.onsure/design-baseline/reconstructability-receipt.json').read_text())
fr,cv,dm,gr,dq,hu,lr,ic,pf=map(int,sys.argv[1:]); blockers=[]
if t['orphans']['p0']: blockers.append('P0_FORWARD_ORPHANS')
if r['stale_reference_count']: blockers.append('STALE_REVERSE_REFERENCES')
if fr: blockers.append('FINAL_PRODUCT_REQUIREMENT_VALIDATION_NOT_PASS')
if cv: blockers.append('DESIGN_COVERAGE_NOT_PASS')
if dm: blockers.append('DD_MACHINE_DEFINITION_NOT_PASS')
if gr: blockers.append('DD_GRANULAR_RUNTIME_TRACE_NOT_CLOSED')
if dq: blockers.append('DD_SEMANTIC_EVALUATORS_NOT_40_OF_40_INDEPENDENTLY_QUALIFIED')
if hu: blockers.append('HUMAN_DESIGN_AUTHORITY_DECISIONS_OPEN')
if lr: blockers.append('LOCAL_REPRODUCIBILITY_TWICE_NOT_PASS')
if ic: blockers.append('INDEPENDENT_CLEAN_TWICE_NOT_PASS')
if pf: blockers.append('CANDIDATE_NATIVE_PREFLIGHT_NOT_PASS')
if inv.get('missing_registered_paths'): blockers.append('DESIGN_ARTIFACT_INVENTORY_INCOMPLETE')
if not rec.get('deterministic_two_run'): blockers.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
receipt={'contract':'ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V6','generated_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'execution_method':'LOCAL_OR_AUTOPILOT_EXPLICIT_RUN_ONLY','github_actions_authority':False,'epoch':'EPOCH::REQUIREMENT::0003::CANDIDATE','requirement_count':len(s['requirement_ids']),'requirement_manifest_digest':s['requirement_manifest_digest'],'authority_population_digest':s['authority_document_population_digest'],'design_artifact_population_digest':inv['population_digest'],'design_reconstructable':rec['deterministic_two_run'],'dd_count':len([x for x in s['requirement_ids'] if x.startswith('DD-')]),'forward_p0_orphans':len(t['orphans']['p0']),'forward_p1_orphans':len(t['orphans']['p1']),'reverse_stale_references':r['stale_reference_count'],'validator_rcs':{'final_requirement':fr,'coverage':cv,'dd_machine_definition':dm,'granular_trace':gr,'dd_evaluator_qualification':dq,'human_authority':hu,'local_reproducibility_twice':lr,'independent_clean_twice':ic,'candidate_preflight':pf},'blocking_reasons':sorted(set(blockers)),'decision':'DESIGN_CLOSURE_CANDIDATE_NONFINAL' if not blockers else 'HOLD_NONFINAL','design_lock':False,'final_claim_allowed':False}
(c/'post-reconciliation-product-design-closure-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n'); print(json.dumps(receipt,ensure_ascii=False,sort_keys=True))
PY

log '11/12 live EPOCH was never replaced; no GitHub Actions authority is accepted'
log '12/12 no Design Lock/Final/GO claim is emitted; main merge and independent approvals remain separate gates'
