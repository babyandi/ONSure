#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.onsure/dd-independent-qualification/receipts'
EXPECTED={f'DD-{i:03d}' for i in range(1,41)}

def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--source',required=True); args=ap.parse_args()
    src=Path(args.source).expanduser().resolve()
    if not src.is_dir(): raise SystemExit('DD_QUALIFICATION_RECEIPTS_SOURCE_NOT_DIRECTORY')
    found={p.stem:p for p in src.glob('DD-*.json') if p.stem in EXPECTED}
    if set(found)!=EXPECTED: raise SystemExit('DD_QUALIFICATION_RECEIPT_DENOMINATOR_NOT_EXACT_40')
    if OUT.exists(): shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    digests={}
    for dd in sorted(EXPECTED):
        p=found[dd]; d=json.loads(p.read_text(encoding='utf-8'))
        if d.get('contract')!='ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_V1' or d.get('dd_id')!=dd: raise SystemExit('DD_QUALIFICATION_RECEIPT_IDENTITY_INVALID:'+dd)
        if d.get('receipt_digest')!=digest_payload(d): raise SystemExit('DD_QUALIFICATION_RECEIPT_DIGEST_INVALID:'+dd)
        dst=OUT/f'{dd}.json'; shutil.copy2(p,dst); digests[dd]=d['receipt_digest']
    population=hashlib.sha256('\n'.join(f'{dd}:{digests[dd]}' for dd in sorted(digests)).encode()).hexdigest()
    print(json.dumps({'contract':'ONSURE_DD_QUALIFICATION_RECEIPT_STAGE_V1','receipt_count':40,'receipt_population_digest':population,'output':OUT.relative_to(ROOT).as_posix(),'decision':'STAGED_IMMUTABLE_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 0
if __name__=='__main__': raise SystemExit(main())
