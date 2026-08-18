#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
EXPECTED={f'DD-{i:03d}' for i in range(1,41)}

def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def sha256(p:Path): return hashlib.sha256(p.read_bytes()).hexdigest()

def resolve(index:Path, path_base:str, value:str)->Path:
    p=Path(value)
    if p.is_absolute(): return p.resolve()
    base=ROOT if path_base=='WORKSPACE_ROOT' else index.parent
    out=(base/p).resolve()
    if not out.is_relative_to(base.resolve()): raise ValueError(f'EVIDENCE_PATH_ESCAPE:{value}')
    return out

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--input',required=True); ap.add_argument('--output',default='.onsure/dd-runtime/evidence-index.json'); args=ap.parse_args()
    src=Path(args.input); src=src if src.is_absolute() else ROOT/src
    if not src.is_file(): raise SystemExit('DD_EVIDENCE_INDEX_SOURCE_MISSING')
    d=json.loads(src.read_text(encoding='utf-8')); commit=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}')
    reasons=[]
    if d.get('contract')!='ONSURE_DD_EVIDENCE_INDEX_V2': reasons.append('DD_EVIDENCE_INDEX_CONTRACT_NOT_V2')
    if d.get('source_tree_sha')!=tree: reasons.append('DD_EVIDENCE_INDEX_TREE_MISMATCH')
    path_base=d.get('path_base')
    if path_base not in ('WORKSPACE_ROOT','INDEX_DIRECTORY'): reasons.append('DD_EVIDENCE_INDEX_PATH_BASE_INVALID')
    covered=set(); normalized=[]
    if not reasons:
        for r in d.get('rows',[]):
            dd_ids=set(r.get('dd_ids') or []); covered.update(dd_ids)
            if not dd_ids or not dd_ids<=EXPECTED: reasons.append(f"DD_EVIDENCE_INDEX_DD_BINDING_INVALID:{r.get('evidence_ref')}"); continue
            try: p=resolve(src,path_base,r.get('path',''))
            except Exception as e: reasons.append(str(e)); continue
            if not p.is_file(): reasons.append(f"DD_EVIDENCE_FILE_MISSING:{r.get('evidence_ref')}"); continue
            actual=sha256(p)
            if actual!=r.get('sha256'): reasons.append(f"DD_EVIDENCE_DIGEST_MISMATCH:{r.get('evidence_ref')}")
            if r.get('current') is not True: reasons.append(f"DD_EVIDENCE_NOT_CURRENT:{r.get('evidence_ref')}")
            item=dict(r); item['path']=str(p); normalized.append(item)
    missing=sorted(EXPECTED-covered)
    if missing: reasons.append('DD_EVIDENCE_INDEX_COVERAGE_MISSING:'+','.join(missing))
    if reasons:
        print(json.dumps({'contract':'ONSURE_DD_EVIDENCE_INDEX_STAGING_V1','blocking_reasons':sorted(set(reasons)),'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 77
    staged=dict(d); staged['source_commit_sha']=commit; staged['source_tree_sha']=tree; staged['path_base']='WORKSPACE_ROOT'; staged['rows']=normalized; staged['staged_from_index_sha256']=sha256(src); staged['staged_from_index_path']=str(src); staged['github_actions_authority']=False; staged['final_claim_allowed']=False
    out=Path(args.output); out=out if out.is_absolute() else ROOT/out; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(staged,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'contract':'ONSURE_DD_EVIDENCE_INDEX_STAGING_V1','source_commit_sha':commit,'source_tree_sha':tree,'row_count':len(normalized),'output':str(out),'decision':'STAGED_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 0

if __name__=='__main__': raise SystemExit(main())
