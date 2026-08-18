#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,sys
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[1]
PACKET=ROOT/'contracts/human-design-authority-recommendation-packet.candidate.v1.json'
QUEUE=ROOT/'contracts/human-design-authority-decision-queue.candidate.v1.json'
OUT=ROOT/'evidence/design-authority/human-decisions'

def canon_digest(d):
    return hashlib.sha256(json.dumps(d,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def receipt_digest(d):
    x=dict(d); x.pop('receipt_digest',None)
    return canon_digest(x)

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--approval-input',required=True); args=ap.parse_args()
    approval=json.loads(Path(args.approval_input).read_text(encoding='utf-8'))
    packet=json.loads(PACKET.read_text(encoding='utf-8')); queue=json.loads(QUEUE.read_text(encoding='utf-8'))
    reasons=[]
    if approval.get('contract')!='ONSURE_HDA_BULK_APPROVAL_INPUT_V1': reasons.append('APPROVAL_INPUT_CONTRACT_MISMATCH')
    pd=canon_digest(packet)
    if approval.get('approved_recommendation_packet_digest')!=pd: reasons.append('RECOMMENDATION_PACKET_DIGEST_MISMATCH')
    expected=[r['decision_id'] for r in queue.get('rows',[])]
    if len(expected)!=18 or set(approval.get('decision_ids',[]))!=set(expected): reasons.append('DECISION_DENOMINATOR_NOT_EXACT_18')
    statement=str(approval.get('approval_statement',''))
    if not statement.strip(): reasons.append('EXPLICIT_APPROVAL_STATEMENT_REQUIRED')
    for f in ('approver_principal','review_authority','approved_at'):
        if not approval.get(f): reasons.append(f'MISSING_{f.upper()}')
    if reasons:
        print(json.dumps({'contract':'ONSURE_HDA_BULK_MATERIALIZATION_V1','blocking_reasons':reasons,'decision':'HOLD_NONFINAL','final_claim_allowed':False},ensure_ascii=False,sort_keys=True)); return 74
    by_id={r['decision_id']:r for r in packet['rows']}; qby={r['decision_id']:r for r in queue['rows']}
    OUT.mkdir(parents=True,exist_ok=True); written=[]
    source_digests=[pd,canon_digest(queue)]
    for did in expected:
        rec=by_id[did]; q=qby[did]
        d={
          'contract':'ONSURE_HUMAN_DESIGN_AUTHORITY_DECISION_V1',
          'decision_id':did,
          'subject_ids':q['subject_ids'],
          'reviewer_principal':approval['approver_principal'],
          'review_authority':approval['review_authority'],
          'decided_at':approval['approved_at'],
          'source_digests':source_digests,
          'decision':rec['recommended_decision'],
          'rationale':rec['recommendation']+' Explicit bulk approval statement: '+statement,
          'conditions':rec.get('conditions',[]),
          'final_claim_allowed':False
        }
        d['receipt_digest']=receipt_digest(d)
        p=OUT/f'{did}.json'; p.write_text(json.dumps(d,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8'); written.append(p.as_posix())
    print(json.dumps({'contract':'ONSURE_HDA_BULK_MATERIALIZATION_V1','written_count':len(written),'decision':'MATERIALIZED_NONFINAL','recommendation_packet_digest':pd,'outputs':written,'final_claim_allowed':False},ensure_ascii=False,sort_keys=True)); return 0
if __name__=='__main__': raise SystemExit(main())
