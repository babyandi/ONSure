#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--receipt',default='evidence/pr-review/pr-54-independent-review.json')
    ap.add_argument('--expected-head-sha',required=True)
    args=ap.parse_args()
    p=ROOT/args.receipt; reasons=[]
    if not p.is_file(): reasons.append('INDEPENDENT_PR_REVIEW_RECEIPT_MISSING'); data={}
    else:
        try: data=json.loads(p.read_text(encoding='utf-8'))
        except Exception: data={}; reasons.append('INDEPENDENT_PR_REVIEW_RECEIPT_UNREADABLE')
    if data:
        if data.get('contract')!='ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT_V1': reasons.append('PR_REVIEW_CONTRACT_MISMATCH')
        if data.get('repository')!='babyandi/ONSure' or data.get('pr_number')!=54: reasons.append('PR_REVIEW_SUBJECT_MISMATCH')
        if data.get('reviewed_head_sha')!=args.expected_head_sha: reasons.append('PR_REVIEW_HEAD_SHA_MISMATCH')
        if data.get('review_state')!='APPROVED': reasons.append('PR_REVIEW_NOT_APPROVED')
        att=data.get('independence_attestation') or {}
        for k in ('not_pr_author','not_change_authority_self_approval','common_control_disclosed'):
            if att.get(k) is not True: reasons.append(f'PR_REVIEW_INDEPENDENCE_{k.upper()}_NOT_TRUE')
        if data.get('final_claim_allowed') is not False: reasons.append('PR_REVIEW_FINAL_CLAIM_NOT_FALSE')
        if not data.get('reviewer_principal'): reasons.append('PR_REVIEWER_PRINCIPAL_MISSING')
        if not data.get('source_ref'): reasons.append('PR_REVIEW_SOURCE_REF_MISSING')
        if not str(data.get('receipt_digest','')).isalnum() or len(str(data.get('receipt_digest','')))!=64: reasons.append('PR_REVIEW_RECEIPT_DIGEST_INVALID')
    out={'contract':'ONSURE_PR_INDEPENDENT_REVIEW_VALIDATION_V1','expected_head_sha':args.expected_head_sha,'blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 71

if __name__=='__main__': raise SystemExit(main())
