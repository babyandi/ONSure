#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DERIVED=ROOT/'.onsure/dd-independent-qualification/validated-status-successor.json'
BUNDLE=ROOT/'.onsure/dd-independent-qualification/frozen-bundle-successor/bundle-manifest.json'
OUT=ROOT/'.onsure/dd-runtime/activation.json'

def load(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--output',default=str(OUT)); args=ap.parse_args(); reasons=[]
    rc=subprocess.run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications-successor.py'],cwd=ROOT,capture_output=True,text=True).returncode
    if rc: reasons.append('DD_EVALUATOR_QUALIFICATION_42_OF_42_NOT_PROVEN')
    if not BUNDLE.is_file() or not DERIVED.is_file(): reasons.append('SUCCESSOR_QUALIFICATION_BUNDLE_OR_DERIVED_STATUS_MISSING')
    if reasons:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V5','blocking_reasons':reasons,'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
    bundle=load(BUNDLE); derived=load(DERIVED); qualified_tree=bundle.get('source_tree_sha','')
    if len(qualified_tree)!=40 or derived.get('qualified_nonfinal_count')!=42 or derived.get('source_tree_sha')!=qualified_tree:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V5','blocking_reasons':['QUALIFIED_SUBJECT_OR_COUNT_INVALID'],'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
    expected={f'DD-{i:03d}' for i in range(1,43)}; rows=[]
    for r in derived.get('rows',[]):
        dd=r.get('dd_id'); ref=r.get('qualification_receipt_ref')
        if dd not in expected or r.get('qualification_state')!='QUALIFIED_NONFINAL' or not ref:
            print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V5','blocking_reasons':[f'INVALID_DERIVED_QUALIFICATION_STATUS:{dd}'],'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
        p=Path(ref); p=p if p.is_absolute() else ROOT/p; receipt=load(p)
        if receipt.get('source_tree_sha')!=qualified_tree or receipt.get('receipt_digest')!=r.get('qualification_receipt_digest'):
            print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V5','blocking_reasons':[f'QUALIFICATION_RECEIPT_DERIVED_STATUS_MISMATCH:{dd}'],'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
        relative=p.relative_to(ROOT).as_posix() if p.is_relative_to(ROOT) else str(p)
        rows.append({'dd_id':dd,'evaluator_id':receipt['evaluator_id'],'evaluator_version':receipt['evaluator_version'],'qualification_receipt_ref':relative,'qualification_receipt_digest':receipt['receipt_digest'],'qualification_current':True,'independent_qualification':True})
    if len(rows)!=42 or {r['dd_id'] for r in rows}!=expected:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V5','blocking_reasons':['QUALIFIED_DD_DENOMINATOR_NOT_EXACT_42'],'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 75
    doc={'contract':'ONSURE_DD_QUALIFIED_RUNTIME_ACTIVATION_V3','execution_commit_sha':git('rev-parse','HEAD'),'execution_tree_sha':git('rev-parse','HEAD^{tree}'),'qualified_subject_tree_sha':qualified_tree,'qualified_count':42,'rows':sorted(rows,key=lambda x:x['dd_id']),'generated_at':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'github_actions_authority':False,'final_claim_allowed':False}
    out=Path(args.output); out=out if out.is_absolute() else ROOT/out; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(doc,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'contract':'ONSURE_DD_RUNTIME_ACTIVATION_MATERIALIZATION_V5','qualified_subject_tree_sha':qualified_tree,'execution_commit_sha':doc['execution_commit_sha'],'execution_tree_sha':doc['execution_tree_sha'],'qualified_count':42,'output':out.relative_to(ROOT).as_posix() if out.is_relative_to(ROOT) else str(out),'decision':'MATERIALIZED_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 0
if __name__=='__main__': raise SystemExit(main())
