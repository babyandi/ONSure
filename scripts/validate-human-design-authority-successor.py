#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EXPECTED={'DD-036','DD-040','F-04','F-05'}

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--approval',default=os.environ.get('ONSURE_HDA_SUCCESSOR_APPROVAL','')); args=ap.parse_args(); reasons=[]
    base=os.system(f"cd {ROOT} && python3 scripts/validate-human-design-authority-decisions.py >/dev/null 2>&1")
    if base!=0: reasons.append('BASE_HDA_18_NOT_VALID')
    if not args.approval: reasons.append('SUCCESSOR_HDA_APPROVAL_INPUT_NOT_SUPPLIED'); doc={}
    else:
        p=Path(args.approval); p=p if p.is_absolute() else ROOT/p
        if not p.is_file(): reasons.append('SUCCESSOR_HDA_APPROVAL_INPUT_MISSING'); doc={}
        else: doc=json.loads(p.read_text(encoding='utf-8'))
    if doc:
        if doc.get('contract')!='ONSURE_HDA_SUCCESSOR_APPROVAL_V1': reasons.append('SUCCESSOR_HDA_CONTRACT_MISMATCH')
        if set(doc.get('decision_ids') or [])!=EXPECTED: reasons.append('SUCCESSOR_HDA_DECISION_DENOMINATOR_NOT_EXACT_4')
        if not doc.get('approver_principal') or not doc.get('review_authority') or not doc.get('approval_statement') or not doc.get('approved_at'): reasons.append('SUCCESSOR_HDA_APPROVAL_PROVENANCE_INCOMPLETE')
        if doc.get('final_claim_allowed') is not False: reasons.append('SUCCESSOR_HDA_FINAL_CLAIM_NOT_FALSE')
    out={'contract':'ONSURE_HDA_SUCCESSOR_VALIDATION_V1','base_hda_required':18,'successor_decision_required':4,'human_decision_denominator':20,'blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 2
if __name__=='__main__': raise SystemExit(main())
