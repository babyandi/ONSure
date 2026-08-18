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
        if d.get('contract')!='ONSURE_INDEPENDENT_CLEAN_RECEIPT_V1': reasons.append(f'CONTRACT_MISMATCH:{cid}')
        if d.get('clean_id')!=cid: reasons.append(f'CLEAN_ID_MISMATCH:{cid}')
        if d.get('receipt_digest')!=digest_payload(d): reasons.append(f'DIGEST_MISMATCH:{cid}')
        if d.get('decision')!='CLEAN': reasons.append(f'NOT_CLEAN:{cid}')
        if d.get('blocking_finding_count')!=0: reasons.append(f'BLOCKING_FINDINGS:{cid}')
        if d.get('final_claim_allowed') is not False: reasons.append(f'FINAL_CLAIM_NOT_FALSE:{cid}')
        for f,n in (('source_commit_sha',40),('source_tree_sha',40),('requirement_manifest_digest',64),('authority_population_digest',64),('coverage_digest',64)):
            v=str(d.get(f,''))
            if len(v)!=n or any(c not in '0123456789abcdef' for c in v): reasons.append(f'INVALID_{f.upper()}:{cid}')
        for f in ('verifier_principal','verifier_process_lineage','model_or_method_lineage','verified_at'):
            if not d.get(f): reasons.append(f'MISSING_{f.upper()}:{cid}')
        cca=d.get('common_control_attestation') or {}
        if not isinstance(cca,dict): reasons.append(f'COMMON_CONTROL_ATTESTATION_NOT_OBJECT:{cid}')
        else:
            if 'common_control_present' not in cca: reasons.append(f'COMMON_CONTROL_PRESENCE_MISSING:{cid}')
            if not isinstance(cca.get('details',[]),list): reasons.append(f'COMMON_CONTROL_DETAILS_INVALID:{cid}')
            if cca.get('independence_still_satisfied') is not True: reasons.append(f'COMMON_CONTROL_INDEPENDENCE_NOT_SATISFIED:{cid}')
    if len(waves)==2:
        for field in ('source_commit_sha','source_tree_sha','requirement_manifest_digest','authority_population_digest','coverage_digest'):
            if waves[0].get(field)!=waves[1].get(field): reasons.append(f'{field.upper()}_DIVERGENCE')
        sig=lambda w:(w.get('verifier_principal'),w.get('verifier_process_lineage'),w.get('model_or_method_lineage'))
        if sig(waves[0])==sig(waves[1]): reasons.append('CLEAN_A_B_NOT_INDEPENDENT_LINEAGE')
        a=(waves[0].get('common_control_attestation') or {}).get('common_control_present')
        b=(waves[1].get('common_control_attestation') or {}).get('common_control_present')
        if a is True and b is True:
            da=(waves[0].get('common_control_attestation') or {}).get('details',[])
            db=(waves[1].get('common_control_attestation') or {}).get('details',[])
            if set(da)&set(db): reasons.append('CLEAN_A_B_SHARED_COMMON_CONTROL_UNRESOLVED')
    out={'contract':'ONSURE_INDEPENDENT_CLEAN_TWICE_VALIDATION_V2','receipt_count':len(waves),'blocking_reasons':sorted(set(reasons)),'decision':'CLEAN_TWICE_NONFINAL' if not reasons else 'HOLD_NONFINAL','design_lock':False,'github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 61
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_INDEPENDENT_CLEAN_FAIL {e}',file=sys.stderr); raise SystemExit(1)
