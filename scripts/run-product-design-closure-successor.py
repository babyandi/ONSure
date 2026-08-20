#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate';BASE=ROOT/'.onsure/design-baseline';MATRIX=ROOT/'contracts/design-capability-coverage.candidate.v2.json'
def load(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def run(name,argv):
    CAND.mkdir(parents=True,exist_ok=True);p=subprocess.run(argv,cwd=ROOT,capture_output=True,text=True,check=False);(CAND/f'{name}.log').write_text((p.stdout or '')+(('\nSTDERR\n'+p.stderr) if p.stderr else ''),encoding='utf-8');return p.returncode
def main()->int:
    ap=argparse.ArgumentParser();ap.add_argument('--phase',choices=('preclean','final'),required=True);args=ap.parse_args();phase=args.phase;blockers=[]
    if run('design-document-authority',[sys.executable,'scripts/validate-design-document-authority.py']):blockers.append('DESIGN_DOCUMENT_AUTHORITY_NOT_PASS')
    if run('design-artifact-inventory',[sys.executable,'scripts/materialize-design-artifact-inventory.py']):blockers.append('DESIGN_ARTIFACT_INVENTORY_NOT_PASS')
    if run('design-reconstructability',[sys.executable,'scripts/verify-design-baseline-reconstructability.py']):blockers.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
    if run('post-delta-authority',[sys.executable,'scripts/materialize-product-design-authority.py']):blockers.append('PRODUCT_DESIGN_AUTHORITY_NOT_PASS')
    if run('epoch-successor-a',[sys.executable,'scripts/generate-product-design-epoch-0003-successor.py']):blockers.append('SUCCESSOR_EPOCH_GENERATION_A_NOT_PASS')
    snap_a=load(CAND/'requirement-universe-snapshot.json') if (CAND/'requirement-universe-snapshot.json').is_file() else {}
    if run('epoch-successor-b',[sys.executable,'scripts/generate-product-design-epoch-0003-successor.py']):blockers.append('SUCCESSOR_EPOCH_GENERATION_B_NOT_PASS')
    snap=load(CAND/'requirement-universe-snapshot.json') if (CAND/'requirement-universe-snapshot.json').is_file() else {}
    if not snap_a or not snap or any(snap_a.get(k)!=snap.get(k) for k in ('requirement_manifest_digest','authority_document_population_digest','requirement_ids')):blockers.append('SUCCESSOR_EPOCH_NOT_DETERMINISTIC')
    dd_ids={x for x in snap.get('requirement_ids',[]) if str(x).startswith('DD-')}
    if dd_ids!={f'DD-{i:03d}' for i in range(1,43)}:blockers.append('SUCCESSOR_EPOCH_DD_DENOMINATOR_NOT_42')
    if run('global-trace',[sys.executable,'scripts/scan-global-trace-closure.py','--universe-dir',str(CAND)]):blockers.append('GLOBAL_TRACE_SCAN_FAILED')
    if run('reverse-orphan',[sys.executable,'scripts/scan-reverse-orphan-product-design.py','--universe-dir',str(CAND)]):blockers.append('REVERSE_ORPHAN_SCAN_FAILED')
    if run('final-product-requirements',[sys.executable,'scripts/validate-final-product-requirements.py','--self-test']):blockers.append('FINAL_PRODUCT_REQUIREMENT_VALIDATION_NOT_PASS')
    if run('design-coverage',[sys.executable,'scripts/validate-design-coverage.py','--matrix',str(MATRIX),'--root',str(ROOT),'--self-test','--output',str(CAND/'design-coverage-report.json')]):blockers.append('DESIGN_COVERAGE_NOT_PASS')
    gates={
      'dd-denominator-42':[sys.executable,'scripts/validate-dd-denominator-42.py'],
      'dd-granular-trace-successor':[sys.executable,'scripts/validate-dd-granular-vertical-trace-successor.py'],
      'dd-evaluator-qualification-successor':[sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications-successor.py'],
      'dd-runtime-evidence-successor':[sys.executable,'scripts/validate-dd-semantic-runtime-evidence-successor.py','--require-all-pass'],
      'human-design-authority-successor':[sys.executable,'scripts/validate-human-design-authority-successor.py'],
      'design-discovery-reconciliation-successor':[sys.executable,'scripts/reconcile-design-discovery-waves-successor.py'],
      'local-reproducibility-twice':['bash','scripts/run-local-assurance-twice.sh'],
      'candidate-preflight-preclean-successor':[sys.executable,'scripts/validate-product-design-candidate-preflight-successor.py','--exclude-independent-clean']
    }
    rcs={}
    for name,cmd in gates.items():rc=run(name,cmd);rcs[name]=rc;blockers += ([] if rc==0 else [name.upper().replace('-','_')+'_NOT_PASS'])
    trace=load(CAND/'global-trace-scan-report.json') if (CAND/'global-trace-scan-report.json').is_file() else {};reverse=load(CAND/'product-design-reverse-orphan-scan-report.json') if (CAND/'product-design-reverse-orphan-scan-report.json').is_file() else {};inv=load(BASE/'design-artifact-inventory.json') if (BASE/'design-artifact-inventory.json').is_file() else {};recon=load(BASE/'reconstructability-receipt.json') if (BASE/'reconstructability-receipt.json').is_file() else {}
    if trace.get('orphans',{}).get('p0'):blockers.append('P0_FORWARD_ORPHANS')
    if reverse.get('stale_reference_count'):blockers.append('STALE_REVERSE_REFERENCES')
    if inv.get('missing_registered_paths'):blockers.append('DESIGN_ARTIFACT_INVENTORY_INCOMPLETE')
    if not recon.get('deterministic_two_run'):blockers.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
    blockers=sorted(set(blockers))
    if phase=='preclean':
        blockers_with_clean=sorted(set(blockers+['INDEPENDENT_CLEAN_TWICE_NOT_PASS']))
        receipt={'contract':'ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V9_SUCCESSOR_PRECLEAN','generated_at':datetime.now(timezone.utc).isoformat(),'execution_method':'LOCAL_OR_AUTOPILOT_EXPLICIT_RUN_ONLY','github_actions_authority':False,'epoch':'EPOCH::REQUIREMENT::0003::SUCCESSOR_CANDIDATE','requirement_count':len(snap.get('requirement_ids',[])),'requirement_manifest_digest':snap.get('requirement_manifest_digest'),'authority_population_digest':snap.get('authority_document_population_digest'),'design_artifact_population_digest':inv.get('population_digest'),'design_reconstructable':recon.get('deterministic_two_run',False),'dd_count':len(dd_ids),'qualification_fixture_count':173,'forward_p0_orphans':len(trace.get('orphans',{}).get('p0',[])),'reverse_stale_references':reverse.get('stale_reference_count'),'validator_rcs':rcs,'blocking_reasons':blockers_with_clean,'decision':'HOLD_NONFINAL','design_lock':False,'final_claim_allowed':False}
        (CAND/'post-reconciliation-product-design-closure-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
        if blockers:print(json.dumps(receipt,ensure_ascii=False,sort_keys=True));return 48
        p=subprocess.run([sys.executable,'scripts/materialize-independent-clean-preclean-subject-successor.py'],cwd=ROOT,check=False)
        print(json.dumps(receipt,ensure_ascii=False,sort_keys=True));return p.returncode
    clean_rc=run('independent-clean-twice',[sys.executable,'scripts/validate-independent-clean-twice.py']);
    if clean_rc:blockers.append('INDEPENDENT_CLEAN_TWICE_NOT_PASS')
    full_preflight_rc=run('candidate-preflight-successor',[sys.executable,'scripts/validate-product-design-candidate-preflight-successor.py'])
    if full_preflight_rc:blockers.append('CANDIDATE_NATIVE_SUCCESSOR_PREFLIGHT_NOT_PASS')
    pre=load(ROOT/'.onsure/independent-clean/preclean-subject.json') if (ROOT/'.onsure/independent-clean/preclean-subject.json').is_file() else {};blockers=sorted(set(blockers))
    receipt={'contract':'ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V9_SUCCESSOR','generated_at':datetime.now(timezone.utc).isoformat(),'execution_method':'LOCAL_OR_AUTOPILOT_EXPLICIT_RUN_ONLY','github_actions_authority':False,'epoch':'EPOCH::REQUIREMENT::0003::SUCCESSOR_CANDIDATE','requirement_count':len(snap.get('requirement_ids',[])),'requirement_manifest_digest':snap.get('requirement_manifest_digest'),'authority_population_digest':snap.get('authority_document_population_digest'),'preclean_subject_digest':pre.get('subject_digest'),'coverage_digest':pre.get('coverage_digest'),'design_artifact_population_digest':inv.get('population_digest'),'design_reconstructable':recon.get('deterministic_two_run',False),'dd_count':len(dd_ids),'qualification_fixture_count':173,'blocking_reasons':blockers,'decision':'DESIGN_CLOSURE_CANDIDATE_NONFINAL' if not blockers else 'HOLD_NONFINAL','design_lock':False,'final_claim_allowed':False}
    (CAND/'post-reconciliation-product-design-closure-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False,sort_keys=True));return 0 if not blockers else 48
if __name__=='__main__':raise SystemExit(main())
