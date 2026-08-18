#!/usr/bin/env python3
from __future__ import annotations
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INTAKE=ROOT/'contracts/independent-design-discovery-wave-intake.candidate.v1.json'
LOCAL=ROOT/'.onsure/design-discovery'
TRACKED=ROOT/'evidence/design-discovery'

def choose(name:str)->Path:
    tracked=TRACKED/name
    return tracked if tracked.exists() else LOCAL/name

def main()->int:
    json.loads(INTAKE.read_text(encoding='utf-8'))
    freeze_path=choose('frozen-baseline-receipt.json')
    if not freeze_path.exists():
        legacy=LOCAL/'frozen-baseline/freeze-receipt.json'
        freeze_path=legacy if legacy.exists() else freeze_path
    if not freeze_path.exists():
        print(json.dumps({'gate':'DISCOVERY_SATURATION','status':'HOLD','reason':'FROZEN_BASELINE_RECEIPT_MISSING'})); return 30
    freeze=json.loads(freeze_path.read_text(encoding='utf-8'))
    waves=[]; ids=('INDEPENDENT-SATURATION-A','INDEPENDENT-SATURATION-B')
    for wid in ids:
        p=choose(f'{wid}.json')
        if not p.exists():
            print(json.dumps({'gate':'DISCOVERY_SATURATION','status':'HOLD','missing_result':str(p.relative_to(ROOT))})); return 30
        waves.append(json.loads(p.read_text(encoding='utf-8')))
    reasons=[]
    for w,wid in zip(waves,ids):
        if w.get('wave_id')!=wid: reasons.append(f'WAVE_ID_MISMATCH:{wid}')
        if w.get('frozen_tree_sha')!=freeze.get('git_tree_sha'): reasons.append(f'TREE_SHA_MISMATCH:{wid}')
        if w.get('frozen_authority_digest')!=freeze.get('authority_population_digest'): reasons.append(f'AUTHORITY_DIGEST_MISMATCH:{wid}')
        if w.get('mandatory_lens_coverage_percent')!=100: reasons.append(f'MANDATORY_LENS_NOT_100:{wid}')
        if w.get('untriaged_candidate_count')!=0: reasons.append(f'UNTRIAGED:{wid}')
        if w.get('new_p0_count')!=0: reasons.append(f'NEW_P0:{wid}')
        if not w.get('p1_novelty_within_policy_ceiling',False): reasons.append(f'P1_CEILING:{wid}')
        if w.get('prior_conclusion_exposure_attestation') is not False: reasons.append(f'PRIOR_CONCLUSION_EXPOSURE:{wid}')
        for f in ('reviewer_principal','reviewer_process_lineage','model_or_method_lineage','common_control_attestation'):
            if f not in w: reasons.append(f'MISSING_{f.upper()}:{wid}')
    sig=lambda w:(w.get('reviewer_principal'),w.get('reviewer_process_lineage'),w.get('model_or_method_lineage'))
    if sig(waves[0])==sig(waves[1]): reasons.append('WAVE_A_B_NOT_INDEPENDENT_LINEAGE')
    if waves[0].get('common_control_attestation') is True and waves[1].get('common_control_attestation') is True: reasons.append('COMMON_CONTROL_NOT_RESOLVED')
    tracked_inputs=all((TRACKED/x).exists() for x in ('frozen-baseline-receipt.json','INDEPENDENT-SATURATION-A.json','INDEPENDENT-SATURATION-B.json'))
    receipt={'contract':'ONSURE_DESIGN_DISCOVERY_SATURATION_RECEIPT_V3','wave_ids':list(ids),'git_tree_sha':freeze.get('git_tree_sha'),'authority_digest':freeze.get('authority_population_digest'),'tracked_evidence_inputs':tracked_inputs,'blocking_reasons':sorted(set(reasons)),'saturation_candidate':not reasons,'decision':'SATURATION_CANDIDATE_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    LOCAL.mkdir(parents=True,exist_ok=True); (LOCAL/'saturation-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 31
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_DISCOVERY_SATURATION_FAIL {e}',file=sys.stderr); raise SystemExit(1)
