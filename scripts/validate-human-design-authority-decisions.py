#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
POLICY=ROOT/'contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json'
DECISIONS=ROOT/'.onsure/design-authority/human-decisions'
def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def main()->int:
    p=json.loads(POLICY.read_text(encoding='utf-8')); required=set(p['human_authority_rows']); approved=set(); invalid=[]
    if DECISIONS.exists():
        for f in sorted(DECISIONS.glob('*.json')):
            try: d=json.loads(f.read_text(encoding='utf-8'))
            except Exception: invalid.append(f'{f.name}:INVALID_JSON'); continue
            if d.get('contract')!='ONSURE_HUMAN_DESIGN_AUTHORITY_DECISION_V1': invalid.append(f'{f.name}:WRONG_CONTRACT'); continue
            if d.get('receipt_digest')!=digest_payload(d): invalid.append(f'{f.name}:DIGEST_MISMATCH'); continue
            if not all(d.get(k) for k in ('reviewer_principal','review_authority','decided_at','rationale')): invalid.append(f'{f.name}:PROVENANCE_INCOMPLETE'); continue
            if d.get('decision') not in ('APPROVE_POLICY','APPROVE_WITH_CONDITIONS'): continue
            approved.update(set(d.get('subject_ids',[])) & required)
    missing=sorted(required-approved); reasons=[]
    if invalid: reasons.append('INVALID_DECISION_RECEIPTS')
    if missing: reasons.append('HUMAN_AUTHORITY_SUBJECTS_OPEN')
    out={'contract':'ONSURE_HUMAN_DESIGN_AUTHORITY_COMPLETENESS_V1','required_subject_count':len(required),'approved_subject_count':len(approved),'missing_subject_ids':missing,'invalid_receipts':invalid,'blocking_reasons':reasons,'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 33
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_HUMAN_AUTHORITY_FAIL {e}',file=sys.stderr); raise SystemExit(1)
