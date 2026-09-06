#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
WAVES=('INDEPENDENT-SATURATION-A','INDEPENDENT-SATURATION-B')

def load(p:Path):return json.loads(p.read_text(encoding='utf-8'))
def canon(d:dict)->str:
    x=dict(d);x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    evidence=os.environ.get('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR','').strip(); receipt_ref=os.environ.get('ONSURE_DD040_BOUND_DECISION_RECEIPT','').strip(); reasons=[]
    if not evidence: reasons.append('DISCOVERY_EVIDENCE_DIR_NOT_SUPPLIED'); base=None
    else:
        base=Path(evidence).expanduser();base=base if base.is_absolute() else ROOT/base
    if not receipt_ref: reasons.append('DD040_BOUND_DECISION_RECEIPT_NOT_SUPPLIED'); decision={};rp=None
    else:
        rp=Path(receipt_ref).expanduser();rp=rp if rp.is_absolute() else ROOT/rp
        if not rp.is_file(): reasons.append('DD040_BOUND_DECISION_RECEIPT_MISSING');decision={}
        else: decision=load(rp)
    waves={}
    if base:
        for wid in WAVES:
            p=base/f'{wid}.json'
            if not p.is_file(): reasons.append(f'{wid}:WAVE_RESULT_MISSING');waves[wid]={}
            else:waves[wid]=load(p)
    if decision:
        if decision.get('contract')!='ONSURE_DD040_NONBLOCKING_P1_BOUND_DECISION_V1': reasons.append('DD040_BOUND_DECISION_CONTRACT_INVALID')
        if decision.get('receipt_digest')!=canon(decision): reasons.append('DD040_BOUND_DECISION_DIGEST_INVALID')
        if decision.get('final_claim_allowed') is not False: reasons.append('DD040_BOUND_DECISION_FINAL_CLAIM_NOT_FALSE')
        if decision.get('rule_type')!='NUMERIC_CEILING' or not isinstance(decision.get('nonblocking_p1_ceiling'),int) or isinstance(decision.get('nonblocking_p1_ceiling'),bool) or decision.get('nonblocking_p1_ceiling')<0: reasons.append('DD040_BOUND_RULE_INVALID')
        if not decision.get('approver_principal') or not decision.get('review_authority') or not decision.get('decision_statement') or not decision.get('decided_at'): reasons.append('DD040_BOUND_DECISION_PROVENANCE_INCOMPLETE')
    a=waves.get(WAVES[0]) or {};b=waves.get(WAVES[1]) or {}
    if a and b and decision:
        if decision.get('wave_a_receipt_digest')!=a.get('receipt_digest'): reasons.append('DD040_BOUND_WAVE_A_DIGEST_MISMATCH')
        if decision.get('wave_b_receipt_digest')!=b.get('receipt_digest'): reasons.append('DD040_BOUND_WAVE_B_DIGEST_MISMATCH')
        if decision.get('observed_wave_a_nonblocking_p1_count')!=a.get('nonblocking_p1_count'): reasons.append('DD040_BOUND_WAVE_A_BASELINE_COUNT_MISMATCH')
        if decision.get('observed_wave_b_nonblocking_p1_count')!=b.get('nonblocking_p1_count'): reasons.append('DD040_BOUND_WAVE_B_BASELINE_COUNT_MISMATCH')
        if a.get('frozen_tree_sha')!=b.get('frozen_tree_sha'): reasons.append('DD040_BOUND_WAVE_TREE_MISMATCH')
        expected_tree=a.get('frozen_tree_sha')
        baseline_path=base/'frozen-baseline-receipt.json'
        baseline=load(baseline_path) if baseline_path.is_file() else {}
        expected_commit=baseline.get('git_commit_sha')
        if decision.get('subject_tree_sha')!=expected_tree: reasons.append('DD040_BOUND_SUBJECT_TREE_MISMATCH')
        if expected_commit and decision.get('subject_commit_sha')!=expected_commit: reasons.append('DD040_BOUND_SUBJECT_COMMIT_MISMATCH')
    out={'contract':'ONSURE_DD040_NONBLOCKING_P1_BOUND_VALIDATION_V1','blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','nonblocking_p1_ceiling':decision.get('nonblocking_p1_ceiling') if decision else None,'decision_receipt_digest':decision.get('receipt_digest') if decision else None,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True));return 0 if not reasons else 43
if __name__=='__main__':raise SystemExit(main())
