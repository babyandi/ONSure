#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
POLICY_CC=ROOT/'contracts/design-discovery-independence-control-graph.v1.json'
POLICY_P1=ROOT/'contracts/design-discovery-p1-novelty-policy.v1.json'
LOCAL=ROOT/'.onsure/design-discovery-v2'

def digest_payload(d):
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def load_wave(base:Path,wid:str):
    p=base/f'{wid}.json'
    if not p.exists(): raise FileNotFoundError(str(p))
    w=json.loads(p.read_text(encoding='utf-8'))
    if w.get('contract')!='ONSURE_INDEPENDENT_DESIGN_DISCOVERY_WAVE_RESULT_V2': raise ValueError(f'WAVE_CONTRACT_NOT_V2:{wid}')
    if w.get('wave_id')!=wid: raise ValueError(f'WAVE_ID_MISMATCH:{wid}')
    if w.get('receipt_digest')!=digest_payload(w): raise ValueError(f'RECEIPT_DIGEST_MISMATCH:{wid}')
    return w

def evaluate_common_control(w,policy):
    reasons=[]
    supplied={x.get('id'):x for x in w.get('common_control_dimensions',[]) if isinstance(x,dict)}
    for dim in policy.get('dimensions',[]):
        did=dim['id']; cls=dim['classification']; item=supplied.get(did)
        if item is None:
            reasons.append(f'COMMON_CONTROL_DIMENSION_UNKNOWN:{did}'); continue
        if not item.get('present',False): continue
        if cls=='DISQUALIFYING': reasons.append(f'DISQUALIFYING_COMMON_CONTROL:{did}'); continue
        if cls=='REQUIRES_MITIGATION':
            ev=set(item.get('mitigation_evidence',[]))
            missing=[x for x in dim.get('required_evidence',[]) if x not in ev]
            if missing: reasons.append(f'COMMON_CONTROL_MITIGATION_MISSING:{did}:{"|".join(missing)}')
    if w.get('prior_conclusion_exposure_attestation') is not False: reasons.append('PRIOR_CONCLUSION_EXPOSURE')
    if w.get('same_authoring_context_attestation') is not False: reasons.append('SAME_AUTHORING_CONTEXT')
    return reasons

def evaluate_p1(w,policy):
    reasons=[]
    if w.get('new_p0_count')!=policy.get('p0_ceiling',0): reasons.append('P0_CEILING_EXCEEDED')
    if w.get('blocking_p1_count',0)>policy.get('blocking_p1_ceiling',0): reasons.append('BLOCKING_P1_CEILING_EXCEEDED')
    if w.get('triage_percent')!=policy.get('required_triage_percent',100): reasons.append('TRIAGE_NOT_100')
    aa=w.get('authority_affecting_p1_count',0); ad=w.get('authority_affecting_p1_disposition_count',0)
    if aa!=ad: reasons.append('AUTHORITY_AFFECTING_P1_UNDISPOSED')
    n=w.get('nonblocking_p1_count',0); ceiling=policy.get('nonblocking_p1_ceiling')
    if ceiling is None and n>0: reasons.append('NONBLOCKING_P1_CEILING_UNCONFIGURED')
    elif ceiling is not None and n>ceiling: reasons.append('NONBLOCKING_P1_CEILING_EXCEEDED')
    return reasons

def main()->int:
    base=Path(os.environ.get('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR','')).expanduser()
    if not str(base): print('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR required',file=sys.stderr); return 2
    if not base.is_absolute(): base=ROOT/base
    cc=json.loads(POLICY_CC.read_text(encoding='utf-8')); p1=json.loads(POLICY_P1.read_text(encoding='utf-8'))
    a=load_wave(base,'INDEPENDENT-SATURATION-A'); b=load_wave(base,'INDEPENDENT-SATURATION-B')
    reasons=[]
    if a.get('frozen_tree_sha')!=b.get('frozen_tree_sha'): reasons.append('WAVE_TREE_MISMATCH')
    if a.get('frozen_authority_digest')!=b.get('frozen_authority_digest'): reasons.append('WAVE_AUTHORITY_DIGEST_MISMATCH')
    if a.get('reviewer_principal')==b.get('reviewer_principal'): reasons.append('REVIEWER_PRINCIPAL_NOT_DISTINCT')
    if a.get('reviewer_process_lineage')==b.get('reviewer_process_lineage'): reasons.append('PROCESS_LINEAGE_NOT_DISTINCT')
    for w,wid in ((a,'A'),(b,'B')):
        if w.get('mandatory_lens_coverage_percent')!=100: reasons.append(f'MANDATORY_LENS_NOT_100:{wid}')
        if w.get('untriaged_candidate_count')!=0: reasons.append(f'UNTRIAGED:{wid}')
        reasons += [f'{x}:{wid}' for x in evaluate_common_control(w,cc)]
        reasons += [f'{x}:{wid}' for x in evaluate_p1(w,p1)]
        if w.get('final_claim_allowed') is not False: reasons.append(f'FINAL_CLAIM_NOT_FALSE:{wid}')
    receipt={
      'contract':'ONSURE_DESIGN_DISCOVERY_RECONCILIATION_RECEIPT_V1',
      'wave_a_digest':a.get('receipt_digest'),'wave_b_digest':b.get('receipt_digest'),
      'frozen_tree_sha':a.get('frozen_tree_sha'),'frozen_authority_digest':a.get('frozen_authority_digest'),
      'blocking_reasons':sorted(set(reasons)),
      'decision':'SATURATION_CANDIDATE_NONFINAL' if not reasons else 'HOLD_NONFINAL',
      'final_claim_allowed':False
    }
    LOCAL.mkdir(parents=True,exist_ok=True)
    (LOCAL/'reconciliation-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 31
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_DISCOVERY_RECONCILE_FAIL {e}',file=sys.stderr); raise SystemExit(1)
