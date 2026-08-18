#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INTAKE=ROOT/'contracts/independent-design-discovery-wave-intake.candidate.v1.json'
LOCAL=ROOT/'.onsure/design-discovery'
DEFAULT_EXTERNAL=Path(os.environ.get('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR','')).expanduser() if os.environ.get('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR') else None

def resolve_dir()->Path:
    if DEFAULT_EXTERNAL:
        return DEFAULT_EXTERNAL if DEFAULT_EXTERNAL.is_absolute() else (ROOT/DEFAULT_EXTERNAL)
    return LOCAL

def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    json.loads(INTAKE.read_text(encoding='utf-8'))
    evidence_dir=resolve_dir().resolve()
    freeze_path=evidence_dir/'frozen-baseline-receipt.json'
    if not freeze_path.exists() and evidence_dir==LOCAL.resolve():
        legacy=LOCAL/'frozen-baseline/freeze-receipt.json'
        if legacy.exists(): freeze_path=legacy
    if not freeze_path.exists():
        print(json.dumps({'gate':'DISCOVERY_SATURATION','status':'HOLD','reason':'FROZEN_BASELINE_RECEIPT_MISSING','evidence_dir':str(evidence_dir)})); return 30
    freeze=json.loads(freeze_path.read_text(encoding='utf-8'))
    reasons=[]
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
        if w.get('new_p0_count')!=0: reasons.append(f'NEW_P0:{wid}')
        if not w.get('p1_novelty_within_policy_ceiling',False): reasons.append(f'P1_CEILING:{wid}')
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
      'contract':'ONSURE_DESIGN_DISCOVERY_SATURATION_RECEIPT_V5',
      'wave_ids':list(ids),
      'git_commit_sha':freeze.get('git_commit_sha'),
      'git_tree_sha':freeze.get('git_tree_sha'),
      'authority_digest':freeze.get('authority_population_digest'),
      'evidence_custody_mode':custody,
      'evidence_dir':str(evidence_dir),
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
