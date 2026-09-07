#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INTAKE=ROOT/'contracts/independent-design-discovery-wave-intake.candidate.v1.json'
SATURATION=ROOT/'contracts/design-discovery-saturation.candidate.v1.json'
LOCAL=ROOT/'.onsure/design-discovery'
DEFAULT_EXTERNAL=Path(os.environ.get('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR','')).expanduser() if os.environ.get('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR') else None

def resolve_dir()->Path:
    if DEFAULT_EXTERNAL:
        return DEFAULT_EXTERNAL if DEFAULT_EXTERNAL.is_absolute() else (ROOT/DEFAULT_EXTERNAL)
    return LOCAL

def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def require_nonnegative_int(value, reason, reasons):
    if isinstance(value,bool) or not isinstance(value,int) or value < 0:
        reasons.append(reason)
        return None
    return value

def main()->int:
    json.loads(INTAKE.read_text(encoding='utf-8'))
    saturation=json.loads(SATURATION.read_text(encoding='utf-8'))
    p1_policy=saturation.get('saturation_candidate_gate',{}).get('p1_novelty_policy')
    policy_reasons=[]
    if not isinstance(p1_policy,dict):
        policy_reasons.append('P1_POLICY_MISSING')
        p1_policy={}
    if p1_policy.get('policy_id')!='COMPOSITE_RISK_TIERED_CEILING_V1': policy_reasons.append('P1_POLICY_ID_MISMATCH')
    p0_ceiling=require_nonnegative_int(p1_policy.get('p0_ceiling'),'P0_CEILING_NOT_CONFIGURED',policy_reasons)
    blocking_ceiling=require_nonnegative_int(p1_policy.get('blocking_p1_ceiling'),'BLOCKING_P1_CEILING_NOT_CONFIGURED',policy_reasons)
    authority_ceiling=require_nonnegative_int(p1_policy.get('authority_affecting_p1_ceiling'),'AUTHORITY_AFFECTING_P1_CEILING_NOT_CONFIGURED',policy_reasons)
    ordinary_ceiling=require_nonnegative_int(p1_policy.get('ordinary_nonblocking_p1_ceiling_per_wave'),'ORDINARY_NONBLOCKING_P1_CEILING_NOT_CONFIGURED',policy_reasons)
    triage_percent=require_nonnegative_int(p1_policy.get('mandatory_triage_percent'),'MANDATORY_TRIAGE_PERCENT_NOT_CONFIGURED',policy_reasons)
    if p1_policy.get('authority_status')!='APPROVED_BY_HUMAN_AUTHORITY': policy_reasons.append('P1_POLICY_HUMAN_AUTHORITY_NOT_APPROVED')
    if p1_policy.get('unknown_or_unconfigured_policy_decision')!='HOLD': policy_reasons.append('P1_POLICY_UNKNOWN_DECISION_NOT_HOLD')

    evidence_dir=resolve_dir().resolve()
    freeze_path=evidence_dir/'frozen-baseline-receipt.json'
    if not freeze_path.exists() and evidence_dir==LOCAL.resolve():
        legacy=LOCAL/'frozen-baseline/freeze-receipt.json'
        if legacy.exists(): freeze_path=legacy
    if not freeze_path.exists():
        print(json.dumps({'gate':'DISCOVERY_SATURATION','status':'HOLD','reason':'FROZEN_BASELINE_RECEIPT_MISSING','evidence_dir':str(evidence_dir)})); return 30
    freeze=json.loads(freeze_path.read_text(encoding='utf-8'))
    reasons=list(policy_reasons)
    if freeze.get('contract')!='ONSURE_INDEPENDENT_DISCOVERY_FROZEN_BASELINE_V2': reasons.append('FROZEN_BASELINE_CONTRACT_NOT_V2')
    if freeze.get('tracked_worktree_clean') is not True: reasons.append('FROZEN_BASELINE_TRACKED_TREE_NOT_CLEAN')
    if freeze.get('conclusion_leakage_count')!=0: reasons.append('FROZEN_BASELINE_CONCLUSION_LEAKAGE')
    if freeze.get('repository_browsing_during_blind_wave_allowed') is not False: reasons.append('FROZEN_BASELINE_REPOSITORY_BROWSING_NOT_FORBIDDEN')
    if freeze.get('final_claim_allowed') is not False: reasons.append('FROZEN_BASELINE_FINAL_CLAIM_NOT_FALSE')
    waves=[]; ids=('INDEPENDENT-SATURATION-A','INDEPENDENT-SATURATION-B')
    for wid in ids:
        p=evidence_dir/f'{wid}.json'
        if not p.exists():
            print(json.dumps({'gate':'DISCOVERY_SATURATION','status':'HOLD','missing_result':str(p)})); return 30
        waves.append(json.loads(p.read_text(encoding='utf-8')))
    for w,wid in zip(waves,ids):
        if w.get('wave_id')!=wid: reasons.append(f'WAVE_ID_MISMATCH:{wid}')
        if w.get('receipt_digest')!=digest_payload(w): reasons.append(f'RECEIPT_DIGEST_MISMATCH:{wid}')
        if w.get('frozen_tree_sha')!=freeze.get('git_tree_sha'): reasons.append(f'TREE_SHA_MISMATCH:{wid}')
        if w.get('frozen_authority_digest')!=freeze.get('authority_population_digest'): reasons.append(f'AUTHORITY_DIGEST_MISMATCH:{wid}')
        if w.get('mandatory_lens_coverage_percent')!=100: reasons.append(f'MANDATORY_LENS_NOT_100:{wid}')
        if w.get('untriaged_candidate_count')!=0: reasons.append(f'UNTRIAGED:{wid}')
        if p0_ceiling is not None and w.get('new_p0_count')!=p0_ceiling: reasons.append(f'NEW_P0_ABOVE_CEILING:{wid}')

        counts=w.get('p1_policy_counts')
        if not isinstance(counts,dict):
            reasons.append(f'P1_POLICY_COUNTS_MISSING:{wid}')
        else:
            blocking=require_nonnegative_int(counts.get('blocking'),'P1_BLOCKING_COUNT_INVALID:'+wid,reasons)
            authority=require_nonnegative_int(counts.get('authority_affecting'),'P1_AUTHORITY_AFFECTING_COUNT_INVALID:'+wid,reasons)
            ordinary=require_nonnegative_int(counts.get('ordinary_nonblocking'),'P1_ORDINARY_NONBLOCKING_COUNT_INVALID:'+wid,reasons)
            classified=require_nonnegative_int(counts.get('classified_total'),'P1_CLASSIFIED_TOTAL_INVALID:'+wid,reasons)
            reported=require_nonnegative_int(w.get('new_p1_count'),'NEW_P1_COUNT_INVALID:'+wid,reasons)
            if None not in (blocking,authority,ordinary,classified,reported):
                if blocking+authority+ordinary != classified: reasons.append(f'P1_CLASSIFICATION_SUM_MISMATCH:{wid}')
                if classified != reported: reasons.append(f'P1_CLASSIFIED_TOTAL_MISMATCH:{wid}')
                if blocking_ceiling is not None and blocking > blocking_ceiling: reasons.append(f'BLOCKING_P1_ABOVE_CEILING:{wid}')
                if authority_ceiling is not None and authority > authority_ceiling: reasons.append(f'AUTHORITY_AFFECTING_P1_ABOVE_CEILING:{wid}')
                if ordinary_ceiling is not None and ordinary > ordinary_ceiling: reasons.append(f'ORDINARY_NONBLOCKING_P1_ABOVE_CEILING:{wid}')
        if triage_percent is not None:
            actual_triage=w.get('triage_completion_percent',100 if w.get('untriaged_candidate_count')==0 else None)
            if actual_triage != triage_percent: reasons.append(f'TRIAGE_PERCENT_NOT_REQUIRED_VALUE:{wid}')
        if w.get('prior_conclusion_exposure_attestation') is not False: reasons.append(f'PRIOR_CONCLUSION_EXPOSURE:{wid}')
        if w.get('same_authoring_context_attestation') is not False: reasons.append(f'SAME_AUTHORING_CONTEXT_NOT_FALSE:{wid}')
        if w.get('common_control_resolved') is not True: reasons.append(f'COMMON_CONTROL_NOT_RESOLVED:{wid}')
        if not w.get('common_control_attestation'): reasons.append(f'COMMON_CONTROL_ATTESTATION_MISSING:{wid}')
        ledger=str(w.get('candidate_ledger_digest',''))
        if len(ledger)!=64 or any(c not in '0123456789abcdef' for c in ledger): reasons.append(f'CANDIDATE_LEDGER_DIGEST_INVALID:{wid}')
        for f in ('reviewer_principal','reviewer_process_lineage','model_or_method_lineage'):
            if not w.get(f): reasons.append(f'MISSING_{f.upper()}:{wid}')
        if w.get('final_claim_allowed') is not False: reasons.append(f'FINAL_CLAIM_NOT_FALSE:{wid}')
    if waves[0].get('reviewer_principal')==waves[1].get('reviewer_principal'): reasons.append('WAVE_A_B_REVIEWER_PRINCIPAL_NOT_DISTINCT')
    if waves[0].get('reviewer_process_lineage')==waves[1].get('reviewer_process_lineage'): reasons.append('WAVE_A_B_PROCESS_LINEAGE_NOT_DISTINCT')
    sig=lambda w:(w.get('reviewer_principal'),w.get('reviewer_process_lineage'),w.get('model_or_method_lineage'))
    if sig(waves[0])==sig(waves[1]): reasons.append('WAVE_A_B_NOT_INDEPENDENT_LINEAGE')
    custody='EXTERNAL_IMMUTABLE' if DEFAULT_EXTERNAL else 'LOCAL_NONTRACKED'
    receipt={
      'contract':'ONSURE_DESIGN_DISCOVERY_SATURATION_RECEIPT_V6',
      'wave_ids':list(ids),
      'git_commit_sha':freeze.get('git_commit_sha'),
      'git_tree_sha':freeze.get('git_tree_sha'),
      'authority_digest':freeze.get('authority_population_digest'),
      'evidence_custody_mode':custody,
      'evidence_dir':str(evidence_dir),
      'p1_policy':p1_policy,
      'blocking_reasons':sorted(set(reasons)),
      'saturation_candidate':not reasons,
      'decision':'SATURATION_CANDIDATE_NONFINAL' if not reasons else 'HOLD_NONFINAL',
      'github_actions_authority':False,
      'final_claim_allowed':False
    }
    LOCAL.mkdir(parents=True,exist_ok=True)
    (LOCAL/'saturation-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 31
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_DISCOVERY_SATURATION_FAIL {e}',file=sys.stderr); raise SystemExit(1)
