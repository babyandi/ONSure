#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate';BASE=ROOT/'.onsure/design-baseline';RU=ROOT/'.onsure/requirement-universe'
def load(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def main()->int:
    ap=argparse.ArgumentParser();ap.add_argument('--exclude-independent-clean',action='store_true');args=ap.parse_args();reasons=[]
    required=[CAND/'requirement-universe-snapshot.json',CAND/'global-trace-scan-report.json',CAND/'product-design-reverse-orphan-scan-report.json',CAND/'design-coverage-report.json',BASE/'design-artifact-inventory.json',BASE/'reconstructability-receipt.json',RU/'requirement-authority-source-manifest.json']
    missing=[p.relative_to(ROOT).as_posix() for p in required if not p.is_file()]
    if missing:reasons.append('SUCCESSOR_PREFLIGHT_INPUT_MISSING')
    if not missing:
        snap=load(CAND/'requirement-universe-snapshot.json');trace=load(CAND/'global-trace-scan-report.json');reverse=load(CAND/'product-design-reverse-orphan-scan-report.json');coverage=load(CAND/'design-coverage-report.json');inv=load(BASE/'design-artifact-inventory.json');rec=load(BASE/'reconstructability-receipt.json');auth=load(RU/'requirement-authority-source-manifest.json')
        ids=set(snap.get('requirement_ids',[]));expected={f'DD-{i:03d}' for i in range(1,43)}|{f'FR-FIN-{i:02d}' for i in range(1,23)};absent=sorted(expected-ids)
        if absent:reasons.append('SUCCESSOR_REQUIRED_IDS_MISSING')
        if trace.get('universe_digest')!=snap.get('requirement_manifest_digest'):reasons.append('FORWARD_TRACE_DENOMINATOR_DIGEST_MISMATCH')
        if trace.get('orphans',{}).get('p0'):reasons.append('P0_FORWARD_ORPHANS')
        if reverse.get('universe_digest')!=snap.get('requirement_manifest_digest'):reasons.append('REVERSE_TRACE_DENOMINATOR_DIGEST_MISMATCH')
        if reverse.get('stale_reference_count'):reasons.append('STALE_REVERSE_REFERENCES')
        if coverage.get('decision')!='PASS':reasons.append('DESIGN_CAPABILITY_COVERAGE_NOT_PASS')
        if inv.get('missing_registered_paths'):reasons.append('DESIGN_ARTIFACT_INVENTORY_INCOMPLETE')
        if not rec.get('deterministic_two_run'):reasons.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
        s=auth.get('review_summary',{})
        if s.get('eligible_unreviewed_count',s.get('unreviewed_count',0)):reasons.append('ELIGIBLE_AUTHORITY_UNREVIEWED')
        if s.get('eligible_disputed_count',s.get('disputed_count',0)):reasons.append('ELIGIBLE_AUTHORITY_DISPUTED')
    gates={
      'dd_denominator':['scripts/validate-dd-denominator-42.py'],
      'dd_granular_trace':['scripts/validate-dd-granular-vertical-trace-successor.py'],
      'qualification':['scripts/validate-dd-semantic-evaluator-qualifications-successor.py'],
      'discovery':['scripts/reconcile-design-discovery-waves-successor.py'],
      'human_authority':['scripts/validate-human-design-authority-successor.py'],
      'runtime':['scripts/validate-dd-semantic-runtime-evidence-successor.py','--require-all-pass']
    }
    if not args.exclude_independent_clean:gates['independent_clean']=['scripts/validate-independent-clean-twice.py']
    rcs={}
    for name,cmd in gates.items():
        p=subprocess.run([sys.executable,*cmd],cwd=ROOT,capture_output=True,text=True,check=False);rcs[name]=p.returncode
        if p.returncode:reasons.append(name.upper()+'_NOT_PASS')
    out={'contract':'ONSURE_PRODUCT_DESIGN_CANDIDATE_PREFLIGHT_V3_SUCCESSOR','exclude_independent_clean':args.exclude_independent_clean,'required_dd_count':42,'required_fixture_count':173,'missing_inputs':missing,'subprocess_gate_return_codes':rcs,'blocking_reasons':sorted(set(reasons)),'decision':'PRECLEAN_PREFLIGHT_NONFINAL' if not reasons and args.exclude_independent_clean else ('PRELOCK_CANDIDATE_NONFINAL' if not reasons else 'HOLD_NONFINAL'),'design_lock_allowed':False,'github_actions_authority':False,'final_claim_allowed':False}
    name='candidate-preflight-preclean-successor.json' if args.exclude_independent_clean else 'candidate-preflight-successor.json';CAND.mkdir(parents=True,exist_ok=True);(CAND/name).write_text(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8');print(json.dumps(out,ensure_ascii=False,sort_keys=True));return 0 if not reasons else 35
if __name__=='__main__':raise SystemExit(main())
