#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def run(cmd):
    p=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True)
    return p.returncode,p.stdout.strip(),p.stderr.strip()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--legacy-args',nargs='*',default=[]); args=ap.parse_args(); reasons=[]; evidence={}
    rc,out,err=run([sys.executable,'scripts/validate-dd-denominator-42.py']); evidence['denominator_42']={'rc':rc,'stdout':out};
    if rc: reasons.append('DD_DENOMINATOR_42_NOT_PASS')
    rc,out,err=run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications-successor.py']); evidence['qualification_42']={'rc':rc,'stdout':out};
    if rc: reasons.append('DD_QUALIFICATION_42_NOT_PASS')
    rc,out,err=run([sys.executable,'scripts/reconcile-design-discovery-waves-v2.py']); evidence['discovery_reconciler']={'rc':rc,'stdout':out};
    if rc: reasons.append('DISCOVERY_RECONCILIATION_NOT_PASS')
    rc,out,err=run([sys.executable,'scripts/validate-human-design-authority-successor.py']); evidence['hda_20']={'rc':rc,'stdout':out};
    if rc: reasons.append('HDA_20_NOT_PASS')
    runtime_receipt=ROOT/'.onsure/dd-runtime-successor/runtime-42-validation.json'
    if not runtime_receipt.is_file(): reasons.append('RUNTIME_42_VALIDATION_MISSING')
    else:
        r=json.loads(runtime_receipt.read_text(encoding='utf-8'))
        if r.get('decision')!='PASS_NONFINAL' or r.get('pass_nonfinal_runtime_count')!=42: reasons.append('RUNTIME_42_NOT_PASS')
    legacy_cmd=[sys.executable,'scripts/validate-premerge-design-lock-readiness.py',*args.legacy_args]
    rc,out,err=run(legacy_cmd); evidence['legacy_closure']={'rc':rc,'stdout':out}
    if rc: reasons.append('LEGACY_CLOSURE_READINESS_NOT_PASS')
    result={'contract':'ONSURE_SUCCESSOR_PREMERGE_READINESS_V1','required_dd_count':42,'required_human_decision_denominator':20,'blocking_reasons':sorted(set(reasons)),'ready_for_main_merge':not reasons,'decision':'READY_FOR_MAIN_MERGE_NONFINAL' if not reasons else 'HOLD_NONFINAL','evidence':evidence,'main_merge_allowed':not reasons,'design_lock':False,'final_claim_allowed':False}
    print(json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True)); return 0 if not reasons else 2
if __name__=='__main__': raise SystemExit(main())
