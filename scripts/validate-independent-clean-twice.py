#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'evidence/independent-clean'
IDS=('INDEPENDENT-CLEAN-A','INDEPENDENT-CLEAN-B')

def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    waves=[]; reasons=[]
    for cid in IDS:
        p=EVIDENCE/f'{cid}.json'
        if not p.exists(): reasons.append(f'MISSING_RECEIPT:{cid}'); continue
        try:d=json.loads(p.read_text(encoding='utf-8'))
        except Exception: reasons.append(f'INVALID_JSON:{cid}'); continue
        waves.append(d)
        if d.get('clean_id')!=cid: reasons.append(f'CLEAN_ID_MISMATCH:{cid}')
        if d.get('receipt_digest')!=digest_payload(d): reasons.append(f'DIGEST_MISMATCH:{cid}')
        if d.get('decision')!='CLEAN': reasons.append(f'NOT_CLEAN:{cid}')
        if d.get('blocking_finding_count')!=0: reasons.append(f'BLOCKING_FINDINGS:{cid}')
        for f in ('source_commit_sha','source_tree_sha','requirement_manifest_digest','authority_population_digest','coverage_digest','verifier_principal','verifier_process_lineage','model_or_method_lineage','common_control_attestation'):
            if f not in d: reasons.append(f'MISSING_{f.upper()}:{cid}')
    if len(waves)==2:
        for field in ('source_commit_sha','source_tree_sha','requirement_manifest_digest','authority_population_digest','coverage_digest'):
            if waves[0].get(field)!=waves[1].get(field): reasons.append(f'{field.upper()}_DIVERGENCE')
        sig=lambda w:(w.get('verifier_principal'),w.get('verifier_process_lineage'),w.get('model_or_method_lineage'))
        if sig(waves[0])==sig(waves[1]): reasons.append('CLEAN_A_B_NOT_INDEPENDENT_LINEAGE')
        if waves[0].get('common_control_attestation') is True and waves[1].get('common_control_attestation') is True:
            reasons.append('CLEAN_COMMON_CONTROL_NOT_RESOLVED')
    out={'contract':'ONSURE_INDEPENDENT_CLEAN_TWICE_VALIDATION_V1','receipt_count':len(waves),'blocking_reasons':sorted(set(reasons)),'decision':'CLEAN_TWICE_NONFINAL' if not reasons else 'HOLD_NONFINAL','design_lock':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 61
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_INDEPENDENT_CLEAN_FAIL {e}',file=sys.stderr); raise SystemExit(1)
