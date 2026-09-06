#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,os,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EXPECTED={f'DD-{i:03d}' for i in range(1,43)}
ALLOWED={'PASS_NONFINAL','HOLD','FAIL','BLOCKED','INCONCLUSIVE','NOT_RUN'}

def load(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def sha256_bytes(b): return hashlib.sha256(b).hexdigest()
def digest_payload(d):
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def resolve_evidence(index_path,index,value):
    p=Path(value)
    if p.is_absolute(): return p.resolve()
    base=ROOT if index.get('path_base')=='WORKSPACE_ROOT' else index_path.parent
    out=(base/p).resolve()
    if not out.is_relative_to(base.resolve()): raise ValueError('DD_EVIDENCE_PATH_ESCAPE:'+value)
    return out

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--raw',required=True); ap.add_argument('--target-identity',required=True); ap.add_argument('--execution-principal',required=True); ap.add_argument('--execution-environment',required=True); ap.add_argument('--evidence-index',default=os.environ.get('ONSURE_DD_EVIDENCE_INDEX','.onsure/dd-runtime/evidence-index.json')); ap.add_argument('--receipts-dir',default='receipts/dd-semantic-runtime-evidence-successor'); ap.add_argument('--status',default='.onsure/dd-runtime-successor/status.json'); args=ap.parse_args()
    raw_path=Path(args.raw); raw_path=raw_path if raw_path.is_absolute() else ROOT/raw_path
    index_path=Path(args.evidence_index); index_path=index_path if index_path.is_absolute() else ROOT/index_path
    raw=load(raw_path); index=load(index_path); activation=load(ROOT/'.onsure/dd-runtime/activation.json'); commit=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}'); reasons=[]
    if raw.get('contract')!='ONSURE_DD_TARGET_RUNTIME_EXECUTION_RAW_V2': reasons.append('RAW_RUNTIME_CONTRACT_NOT_V2')
    if raw.get('source_tree_sha')!=tree: reasons.append('RAW_RUNTIME_TREE_MISMATCH')
    if raw.get('dd_count')!=42: reasons.append('RAW_RUNTIME_DD_COUNT_NOT_42')
    if index.get('contract')!='ONSURE_DD_EVIDENCE_INDEX_V2' or index.get('source_tree_sha')!=tree or index.get('source_commit_sha')!=commit: reasons.append('EVIDENCE_INDEX_CURRENT_EXECUTION_SUBJECT_MISMATCH')
    if index.get('path_base') not in ('WORKSPACE_ROOT','INDEX_DIRECTORY'): reasons.append('EVIDENCE_INDEX_PATH_BASE_INVALID')
    if activation.get('contract')!='ONSURE_DD_QUALIFIED_RUNTIME_ACTIVATION_V3': reasons.append('ACTIVATION_CONTRACT_NOT_V3')
    if activation.get('execution_tree_sha')!=tree or activation.get('execution_commit_sha')!=commit: reasons.append('ACTIVATION_EXECUTION_SUBJECT_MISMATCH')
    qualified_tree=activation.get('qualified_subject_tree_sha')
    if not isinstance(qualified_tree,str) or len(qualified_tree)!=40: reasons.append('ACTIVATION_QUALIFIED_SUBJECT_TREE_INVALID')
    raw_rows=raw.get('rows',[])
    if len(raw_rows)!=42 or {r.get('dd_id') for r in raw_rows}!=EXPECTED: reasons.append('RAW_RUNTIME_DD_DENOMINATOR_NOT_EXACT_42')
    act={r.get('dd_id'):r for r in activation.get('rows',[])}
    if len(act)!=42 or set(act)!=EXPECTED or activation.get('qualified_count')!=42: reasons.append('ACTIVATION_DD_DENOMINATOR_NOT_EXACT_42')
    evidence={r.get('evidence_ref'):r for r in index.get('rows',[]) if r.get('evidence_ref')}
    if reasons:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_EVIDENCE_MATERIALIZATION_V4','blocking_reasons':reasons,'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 76
    outdir=ROOT/args.receipts_dir; outdir.mkdir(parents=True,exist_ok=True); status_rows=[]; positive=nonpositive=notrun=0; executed_at=datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
    for rr in sorted(raw_rows,key=lambda x:x['dd_id']):
        dd=rr['dd_id']; op=rr.get('operation'); result=rr.get('result') or {}; decision=result.get('decision','HOLD')
        if decision not in ALLOWED: decision='HOLD'
        activation_row=act[dd]; qref=activation_row.get('qualification_receipt_ref'); qpath=Path(qref); qpath=qpath if qpath.is_absolute() else ROOT/qpath; qreceipt=load(qpath)
        if qreceipt.get('source_tree_sha')!=qualified_tree: reasons.append(f'{dd}:QUALIFICATION_SUBJECT_TREE_MISMATCH')
        refs=rr.get('evidence_refs') or []; input_evidence=[]
        for ref in refs:
            entry=evidence.get(ref)
            if not entry: continue
            try:p=resolve_evidence(index_path,index,entry['path'])
            except Exception:p=Path('/nonexistent')
            actual=sha256_bytes(p.read_bytes()) if p.is_file() else ''
            input_evidence.append({'evidence_ref':ref,'content_digest':actual,'declared_content_digest':entry.get('sha256'),'integrity_verified':bool(actual and actual==entry.get('sha256')),'current':entry.get('current') is True,'authority_ref':entry.get('authority_ref')})
        receipt={'contract':'ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_V1','dd_id':dd,'operation':op,'source_commit_sha':commit,'source_tree_sha':tree,'qualified_subject_tree_sha':qualified_tree,'target_identity':args.target_identity,'execution_principal':args.execution_principal,'execution_environment':args.execution_environment,'evaluator_id':qreceipt.get('evaluator_id'),'evaluator_version':qreceipt.get('evaluator_version'),'qualification_receipt_digest':qreceipt.get('receipt_digest'),'input_evidence':input_evidence,'result':result,'executed_at':executed_at,'synthetic_fixture':False,'github_actions_authority':False,'final_claim_allowed':False}
        receipt['receipt_digest']=digest_payload(receipt); rel=(Path(args.receipts_dir)/f'{dd}.json').as_posix(); (ROOT/rel).write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
        if decision=='PASS_NONFINAL': positive+=1
        elif decision=='NOT_RUN': notrun+=1
        else: nonpositive+=1
        status_rows.append({'dd_id':dd,'runtime_state':decision,'runtime_receipt_ref':rel})
    if reasons:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_EVIDENCE_MATERIALIZATION_V4','blocking_reasons':sorted(set(reasons)),'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 76
    status={'contract':'ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_STATUS_V4','status':'RUNTIME_EXECUTED_NONFINAL' if notrun==0 else 'PARTIALLY_EXECUTED_NONFINAL','rows':status_rows,'summary':{'dd_count':42,'not_run_count':notrun,'pass_nonfinal_runtime_count':positive,'nonpositive_runtime_count':nonpositive},'source_commit_sha':commit,'source_tree_sha':tree,'qualified_subject_tree_sha':qualified_tree,'target_identity':args.target_identity,'evidence_index_path':str(index_path),'github_actions_authority':False,'final_claim_allowed':False}
    sp=ROOT/args.status; sp.parent.mkdir(parents=True,exist_ok=True); sp.write_text(json.dumps(status,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    verdict='PASS_NONFINAL_42_OF_42' if positive==42 else 'HOLD_NONFINAL'
    print(json.dumps({'contract':'ONSURE_DD_RUNTIME_EVIDENCE_MATERIALIZATION_V4','qualified_subject_tree_sha':qualified_tree,'execution_tree_sha':tree,'pass_nonfinal_runtime_count':positive,'nonpositive_runtime_count':nonpositive,'not_run_count':notrun,'decision':verdict,'final_claim_allowed':False},sort_keys=True)); return 0 if positive==42 else 76
if __name__=='__main__': raise SystemExit(main())
