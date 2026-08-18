#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
QUEUE=ROOT/'contracts/human-design-authority-decision-queue.candidate.v1.json'
TRACKED=ROOT/'evidence/design-authority/human-decisions'; LOCAL=ROOT/'.onsure/design-authority/human-decisions'

def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None); return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def receipt_files():
    supplied=os.environ.get('ONSURE_HDA_RECEIPTS_DIR','').strip()
    if supplied:
        p=Path(supplied); p=p if p.is_absolute() else ROOT/p
        return sorted(p.glob('*.json')) if p.is_dir() else []
    files=[]
    if LOCAL.exists(): files.extend(sorted(LOCAL.glob('*.json')))
    local_names={p.name for p in files}
    if TRACKED.exists(): files.extend(p for p in sorted(TRACKED.glob('*.json')) if p.name not in local_names)
    return files

def main()->int:
    q=json.loads(QUEUE.read_text(encoding='utf-8')); required={r['decision_id']:set(r['subject_ids']) for r in q['rows']}; closed=set(); approved_subjects=set(); invalid=[]; files=receipt_files()
    for f in files:
        try:d=json.loads(f.read_text(encoding='utf-8'))
        except Exception: invalid.append(f'{f.name}:INVALID_JSON'); continue
        if d.get('contract')!='ONSURE_HUMAN_DESIGN_AUTHORITY_DECISION_V1': invalid.append(f'{f.name}:WRONG_CONTRACT'); continue
        did=d.get('decision_id')
        if did not in required: invalid.append(f'{f.name}:UNKNOWN_DECISION_ID:{did}'); continue
        if d.get('receipt_digest')!=digest_payload(d): invalid.append(f'{f.name}:DIGEST_MISMATCH'); continue
        if not all(d.get(k) for k in ('reviewer_principal','review_authority','decided_at','rationale')): invalid.append(f'{f.name}:PROVENANCE_INCOMPLETE'); continue
        if not required[did].issubset(set(d.get('subject_ids',[]))): invalid.append(f'{f.name}:SUBJECT_DENOMINATOR_INCOMPLETE'); continue
        if d.get('decision') not in ('APPROVE_POLICY','APPROVE_WITH_CONDITIONS'): continue
        closed.add(did); approved_subjects.update(required[did])
    missing=sorted(set(required)-closed); reasons=[]
    if invalid: reasons.append('INVALID_DECISION_RECEIPTS')
    if missing: reasons.append('HUMAN_AUTHORITY_DECISIONS_OPEN')
    out={'contract':'ONSURE_HUMAN_DESIGN_AUTHORITY_COMPLETENESS_V3','required_decision_count':len(required),'closed_decision_count':len(closed),'approved_subject_count':len(approved_subjects),'receipt_file_count':len(files),'receipt_source':os.environ.get('ONSURE_HDA_RECEIPTS_DIR') or 'LOCAL_THEN_TRACKED_FALLBACK','missing_decision_ids':missing,'invalid_receipts':invalid,'blocking_reasons':reasons,'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 33
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_HUMAN_AUTHORITY_FAIL {e}',file=sys.stderr); raise SystemExit(1)
