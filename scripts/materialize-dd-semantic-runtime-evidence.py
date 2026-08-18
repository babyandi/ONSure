#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,subprocess
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
EXPECTED={f'DD-{i:03d}' for i in range(1,41)}
ALLOWED={'PASS_NONFINAL','HOLD','FAIL','BLOCKED','INCONCLUSIVE','NOT_RUN'}

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def sha256_bytes(b:bytes)->str: return hashlib.sha256(b).hexdigest()
def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def git(*args)->str: return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--raw',required=True)
    ap.add_argument('--target-identity',required=True)
    ap.add_argument('--execution-principal',required=True)
    ap.add_argument('--execution-environment',required=True)
    ap.add_argument('--receipts-dir',default='receipts/dd-semantic-runtime-evidence')
    ap.add_argument('--status',default='contracts/dd-semantic-runtime-evidence-status.candidate.v1.json')
    args=ap.parse_args()
    raw=load(Path(args.raw)); index=load(ROOT/'.onsure/dd-runtime/evidence-index.json'); activation=load(ROOT/'.onsure/dd-runtime/activation.json')
    commit=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}'); reasons=[]
    if raw.get('contract')!='ONSURE_DD_TARGET_RUNTIME_EXECUTION_RAW_V1': reasons.append('RAW_RUNTIME_CONTRACT_MISMATCH')
    if raw.get('source_tree_sha')!=tree: reasons.append('RAW_RUNTIME_TREE_MISMATCH')
    if index.get('contract')!='ONSURE_DD_EVIDENCE_INDEX_V2' or index.get('source_tree_sha')!=tree or index.get('source_commit_sha')!=commit: reasons.append('EVIDENCE_INDEX_CURRENT_HEAD_MISMATCH')
    if activation.get('contract')!='ONSURE_DD_QUALIFIED_RUNTIME_ACTIVATION_V1' or activation.get('source_tree_sha')!=tree or activation.get('source_commit_sha')!=commit: reasons.append('ACTIVATION_CURRENT_HEAD_MISMATCH')
    raw_rows=raw.get('rows',[])
    if len(raw_rows)!=40 or {r.get('dd_id') for r in raw_rows}!=EXPECTED: reasons.append('RAW_RUNTIME_DD_DENOMINATOR_NOT_EXACT_40')
    act={r.get('dd_id'):r for r in activation.get('rows',[])}
    if len(act)!=40 or set(act)!=EXPECTED: reasons.append('ACTIVATION_DD_DENOMINATOR_NOT_EXACT_40')
    evidence={r.get('evidence_ref'):r for r in index.get('rows',[]) if r.get('evidence_ref')}
    if reasons:
        print(json.dumps({'contract':'ONSURE_DD_RUNTIME_EVIDENCE_MATERIALIZATION_V1','blocking_reasons':reasons,'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 76

    outdir=ROOT/args.receipts_dir; outdir.mkdir(parents=True,exist_ok=True); status_rows=[]; positive=nonpositive=notrun=0
    executed_at=datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
    for rr in sorted(raw_rows,key=lambda x:x['dd_id']):
        dd=rr['dd_id']; op=rr.get('operation'); result=rr.get('result') or {}; decision=result.get('decision','HOLD')
        if decision not in ALLOWED: decision='HOLD'
        activation_row=act[dd]; qref=activation_row.get('qualification_receipt_ref'); qpath=ROOT/qref
        qreceipt=load(qpath)
        refs=rr.get('evidence_refs') or []; input_evidence=[]
        for ref in refs:
            entry=evidence.get(ref)
            if not entry: continue
            p=(ROOT/entry['path']).resolve()
            actual=sha256_bytes(p.read_bytes()) if p.is_file() else ''
            input_evidence.append({'evidence_ref':ref,'content_digest':actual,'declared_content_digest':entry.get('sha256'),'integrity_verified':bool(actual and actual==entry.get('sha256')),'current':entry.get('current') is True,'authority_ref':entry.get('authority_ref')})
        receipt={
          'contract':'ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_V1','dd_id':dd,'operation':op,'source_commit_sha':commit,'source_tree_sha':tree,
          'target_identity':args.target_identity,'execution_principal':args.execution_principal,'execution_environment':args.execution_environment,
          'evaluator_id':qreceipt.get('evaluator_id'),'evaluator_version':qreceipt.get('evaluator_version'),'qualification_receipt_digest':qreceipt.get('receipt_digest'),
          'input_evidence':input_evidence,'result':result,'executed_at':executed_at,'synthetic_fixture':False,'github_actions_authority':False,'final_claim_allowed':False
        }
        receipt['receipt_digest']=digest_payload(receipt)
        rel=f'receipts/dd-semantic-runtime-evidence/{dd}.json'; (ROOT/rel).write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
        state=decision
        if state=='PASS_NONFINAL': positive+=1
        elif state=='NOT_RUN': notrun+=1
        else: nonpositive+=1
        status_rows.append({'dd_id':dd,'runtime_state':state,'runtime_receipt_ref':rel})
    status={
      'contract':'ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_STATUS_V2','status':'RUNTIME_EXECUTED_NONFINAL' if notrun==0 else 'PARTIALLY_EXECUTED_NONFINAL','rows':status_rows,
      'summary':{'dd_count':40,'not_run_count':notrun,'pass_nonfinal_runtime_count':positive,'nonpositive_runtime_count':nonpositive},
      'source_commit_sha':commit,'source_tree_sha':tree,'target_identity':args.target_identity,'github_actions_authority':False,'final_claim_allowed':False
    }
    (ROOT/args.status).write_text(json.dumps(status,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    verdict='PASS_NONFINAL_40_OF_40' if positive==40 else 'HOLD_NONFINAL'
    print(json.dumps({'contract':'ONSURE_DD_RUNTIME_EVIDENCE_MATERIALIZATION_V1','pass_nonfinal_runtime_count':positive,'nonpositive_runtime_count':nonpositive,'not_run_count':notrun,'decision':verdict,'final_claim_allowed':False},sort_keys=True)); return 0 if positive==40 else 76

if __name__=='__main__': raise SystemExit(main())
