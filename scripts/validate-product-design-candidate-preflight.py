#!/usr/bin/env python3
from __future__ import annotations

import argparse,json,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate'
RU=ROOT/'.onsure/requirement-universe'
BASELINE=ROOT/'.onsure/design-baseline'


def load(path:Path)->dict: return json.loads(path.read_text(encoding='utf-8'))

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--exclude-independent-clean',action='store_true'); args=ap.parse_args()
    reasons=[]
    required=[CAND/'requirement-universe-snapshot.json',CAND/'global-trace-scan-report.json',CAND/'product-design-reverse-orphan-scan-report.json',CAND/'design-coverage-report.json',BASELINE/'design-artifact-inventory.json',BASELINE/'reconstructability-receipt.json',RU/'requirement-authority-source-manifest.json']
    missing=[p.relative_to(ROOT).as_posix() for p in required if not p.is_file()]
    if missing:
        reasons.append('CANDIDATE_PREFLIGHT_INPUT_MISSING')
        out={'contract':'ONSURE_PRODUCT_DESIGN_CANDIDATE_PREFLIGHT_V2','missing_inputs':missing,'exclude_independent_clean':args.exclude_independent_clean,'blocking_reasons':reasons,'decision':'HOLD_NONFINAL','design_lock_allowed':False,'final_claim_allowed':False}
        print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 35
    snap=load(CAND/'requirement-universe-snapshot.json'); trace=load(CAND/'global-trace-scan-report.json'); reverse=load(CAND/'product-design-reverse-orphan-scan-report.json'); coverage=load(CAND/'design-coverage-report.json'); inv=load(BASELINE/'design-artifact-inventory.json'); rec=load(BASELINE/'reconstructability-receipt.json'); auth=load(RU/'requirement-authority-source-manifest.json')
    ids=set(snap.get('requirement_ids',[])); expected={f'DD-{i:03d}' for i in range(1,41)}|{f'FR-FIN-{i:02d}' for i in range(1,23)}; absent=sorted(expected-ids)
    if absent: reasons.append('REQUIRED_POST_FINAL_TARGET_IDS_MISSING')
    if trace.get('universe_digest')!=snap.get('requirement_manifest_digest'): reasons.append('FORWARD_TRACE_DENOMINATOR_DIGEST_MISMATCH')
    if trace.get('orphans',{}).get('p0'): reasons.append('P0_FORWARD_ORPHANS')
    if reverse.get('universe_digest')!=snap.get('requirement_manifest_digest'): reasons.append('REVERSE_TRACE_DENOMINATOR_DIGEST_MISMATCH')
    if reverse.get('stale_reference_count'): reasons.append('STALE_REVERSE_REFERENCES')
    if coverage.get('decision')!='PASS': reasons.append('DESIGN_CAPABILITY_COVERAGE_NOT_PASS')
    if inv.get('missing_registered_paths'): reasons.append('DESIGN_ARTIFACT_INVENTORY_INCOMPLETE')
    if not rec.get('deterministic_two_run'): reasons.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
    summary=auth.get('review_summary',{})
    if summary.get('eligible_unreviewed_count',summary.get('unreviewed_count',0)): reasons.append('ELIGIBLE_AUTHORITY_UNREVIEWED')
    if summary.get('eligible_disputed_count',summary.get('disputed_count',0)): reasons.append('ELIGIBLE_AUTHORITY_DISPUTED')
    subprocess_gates={
      'dd_granular_trace':['scripts/validate-dd-granular-vertical-trace.py'],
      'human_authority':['scripts/validate-human-design-authority-decisions.py'],
      'discovery_saturation':['scripts/validate-design-discovery-saturation.py'],
    }
    if not args.exclude_independent_clean: subprocess_gates['independent_clean']=['scripts/validate-independent-clean-twice.py']
    return_codes={}
    for name,cmd in subprocess_gates.items():
        rc=subprocess.run([sys.executable,*cmd],cwd=ROOT,capture_output=True,text=True,check=False); return_codes[name]=rc.returncode
        if rc.returncode: reasons.append(name.upper()+'_NOT_PASS')
    out={'contract':'ONSURE_PRODUCT_DESIGN_CANDIDATE_PREFLIGHT_V2','epoch':'EPOCH::REQUIREMENT::0003::CANDIDATE','exclude_independent_clean':args.exclude_independent_clean,'requirement_count':len(ids),'required_post_final_target_ids_missing':absent,'requirement_manifest_digest':snap.get('requirement_manifest_digest'),'authority_population_digest':snap.get('authority_document_population_digest'),'forward_p0_orphan_count':len(trace.get('orphans',{}).get('p0',[])),'reverse_stale_reference_count':reverse.get('stale_reference_count'),'design_coverage_decision':coverage.get('decision'),'design_reconstructable':bool(rec.get('deterministic_two_run')),'eligible_authority_unreviewed_count':summary.get('eligible_unreviewed_count',summary.get('unreviewed_count',0)),'eligible_authority_disputed_count':summary.get('eligible_disputed_count',summary.get('disputed_count',0)),'subprocess_gate_return_codes':return_codes,'github_actions_authority':False,'execution_method_required':'LOCAL_OR_AUTOPILOT_EXPLICIT_RUN','blocking_reasons':sorted(set(reasons)),'decision':'PRECLEAN_PREFLIGHT_NONFINAL' if not reasons and args.exclude_independent_clean else ('PRELOCK_CANDIDATE_NONFINAL' if not reasons else 'HOLD_NONFINAL'),'design_lock_allowed':False,'final_claim_allowed':False}
    name='candidate-preflight-preclean-report.json' if args.exclude_independent_clean else 'candidate-preflight-report.json'
    (CAND/name).write_text(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 35

if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e:
        print(f'ONSURE_PRODUCT_DESIGN_CANDIDATE_PREFLIGHT_FAIL {e}',file=sys.stderr); raise SystemExit(1)
