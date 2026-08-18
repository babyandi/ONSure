#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
STATUS=ROOT/'contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json'
OUT=ROOT/'.onsure/dd-runtime/activation.json'

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--output',default=str(OUT)); args=ap.parse_args()
    rc=subprocess.run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications.py','--require-all-qualified'],cwd=ROOT).returncode
    if rc:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V2','blocking_reasons':['DD_EVALUATOR_QUALIFICATION_40_OF_40_NOT_PROVEN'],'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
    status=load(STATUS); rows=[]; expected={f'DD-{i:03d}' for i in range(1,41)}
    for r in status.get('rows',[]):
        dd=r.get('dd_id'); ref=r.get('qualification_receipt_ref')
        if dd not in expected or r.get('qualification_state')!='QUALIFIED_NONFINAL' or not ref:
            print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V2','blocking_reasons':[f'INVALID_QUALIFICATION_STATUS:{dd}'],'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
        p=Path(ref); p=p if p.is_absolute() else ROOT/p
        receipt=load(p)
        relative=p.relative_to(ROOT).as_posix() if p.is_relative_to(ROOT) else str(p)
        rows.append({
          'dd_id':dd,
          'evaluator_id':receipt['evaluator_id'],
          'evaluator_version':receipt['evaluator_version'],
          'qualification_receipt_ref':relative,
          'qualification_receipt_digest':receipt['receipt_digest'],
          'qualification_current':True,
          'independent_qualification':True
        })
    if len(rows)!=40 or {r['dd_id'] for r in rows}!=expected:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V2','blocking_reasons':['QUALIFIED_DD_DENOMINATOR_NOT_EXACT_40'],'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
    doc={
      'contract':'ONSURE_DD_QUALIFIED_RUNTIME_ACTIVATION_V1',
      'source_commit_sha':git('rev-parse','HEAD'),
      'source_tree_sha':git('rev-parse','HEAD^{tree}'),
      'qualified_count':40,
      'rows':sorted(rows,key=lambda x:x['dd_id']),
      'generated_at':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),
      'github_actions_authority':False,
      'final_claim_allowed':False
    }
    out=Path(args.output); out=out if out.is_absolute() else ROOT/out; out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(doc,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V2','qualified_count':40,'output':out.relative_to(ROOT).as_posix() if out.is_relative_to(ROOT) else str(out),'decision':'MATERIALIZED_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 0

if __name__=='__main__': raise SystemExit(main())
