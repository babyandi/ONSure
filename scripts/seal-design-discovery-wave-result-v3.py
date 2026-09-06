#!/usr/bin/env python3
from __future__ import annotations

import argparse,hashlib,json,shutil
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ENVELOPES=ROOT/'.onsure/design-discovery-v3/envelopes'
SEALED=ROOT/'.onsure/design-discovery-v3/sealed-results'

def canon(d:dict,drop:str|None=None)->str:
    x=dict(d)
    if drop:x.pop(drop,None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def load(p:Path)->dict:return json.loads(p.read_text(encoding='utf-8'))
def dt(v:str)->datetime:
    if v.endswith('Z'):v=v[:-1]+'+00:00'
    return datetime.fromisoformat(v)
def now()->str:return datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
def wave_name(v:str)->str:
    if v not in {'INDEPENDENT-SATURATION-A','INDEPENDENT-SATURATION-B'}:raise ValueError('INVALID_WAVE_ID')
    return v

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--wave-result',required=True); ap.add_argument('--candidate-ledger',required=True); ap.add_argument('--execution-isolation',required=True); args=ap.parse_args()
    wave_result_path=Path(args.wave_result).resolve(); ledger_path=Path(args.candidate_ledger).resolve(); isolation_path=Path(args.execution_isolation).resolve()
    wave=load(wave_result_path); wid=wave_name(str(wave.get('wave_id')))
    pair=load(ENVELOPES/'envelope-pair-receipt.json'); manifest=load(ENVELOPES/wid/'envelope-manifest.json'); isolation=load(isolation_path); ledger=load(ledger_path)
    reasons=[]
    if pair.get('pair_digest')!=canon(pair,'pair_digest'):reasons.append('PAIR_DIGEST_INVALID')
    if manifest.get('envelope_digest')!=canon(manifest,'envelope_digest'):reasons.append('ENVELOPE_DIGEST_INVALID')
    if wave.get('contract')!='ONSURE_INDEPENDENT_DESIGN_DISCOVERY_WAVE_RESULT_V3':reasons.append('WAVE_CONTRACT_NOT_V3')
    if wave.get('receipt_digest')!=canon(wave,'receipt_digest'):reasons.append('WAVE_RECEIPT_DIGEST_INVALID')
    if isolation.get('contract')!='ONSURE_DESIGN_DISCOVERY_WAVE_EXECUTION_ISOLATION_V1':reasons.append('ISOLATION_CONTRACT_INVALID')
    if isolation.get('evidence_digest')!=canon(isolation,'evidence_digest'):reasons.append('ISOLATION_EVIDENCE_DIGEST_INVALID')
    ledger_digest=canon(ledger)
    if wave.get('candidate_ledger_digest')!=ledger_digest:reasons.append('CANDIDATE_LEDGER_DIGEST_MISMATCH')
    for d,label in ((pair,'PAIR'),(manifest,'ENVELOPE'),(wave,'WAVE')):
        if d.get('frozen_tree_sha')!=pair.get('frozen_tree_sha'):reasons.append(label+'_TREE_MISMATCH')
        if d.get('frozen_authority_digest')!=pair.get('frozen_authority_digest'):reasons.append(label+'_AUTHORITY_MISMATCH')
    if wave.get('frozen_baseline_receipt_sha256')!=pair.get('frozen_baseline_receipt_sha256'):reasons.append('WAVE_BASELINE_RECEIPT_MISMATCH')
    if wave.get('input_bundle_digest')!=pair.get('input_bundle_digest') or manifest.get('input_bundle_digest')!=pair.get('input_bundle_digest'):reasons.append('INPUT_BUNDLE_DIGEST_MISMATCH')
    if wave.get('envelope_digest')!=manifest.get('envelope_digest') or isolation.get('envelope_digest')!=manifest.get('envelope_digest'):reasons.append('ENVELOPE_BINDING_MISMATCH')
    if wave.get('execution_isolation_evidence_digest')!=isolation.get('evidence_digest'):reasons.append('ISOLATION_BINDING_MISMATCH')
    if isolation.get('wave_id')!=wid:reasons.append('ISOLATION_WAVE_ID_MISMATCH')
    if isolation.get('input_bundle_digest')!=pair.get('input_bundle_digest'):reasons.append('ISOLATION_INPUT_DIGEST_MISMATCH')
    if wave.get('reviewer_principal')!=isolation.get('reviewer_principal') or wave.get('reviewer_process_lineage')!=isolation.get('reviewer_process_lineage'):reasons.append('REVIEWER_PROVENANCE_MISMATCH')
    required_true=('fresh_session','read_only_frozen_bundle','sealed_envelope_only','result_written_by_reviewer')
    required_false=('repository_browsing_performed','issue_pr_comment_browsing_performed','opposite_wave_result_accessible','opposite_wave_result_accessed','prior_conclusion_source_accessed','shared_memory_context','orchestrator_result_intermediation')
    for k in required_true:
        if isolation.get(k) is not True:reasons.append('ISOLATION_TRUE_REQUIRED:'+k)
    for k in required_false:
        if isolation.get(k) is not False:reasons.append('ISOLATION_FALSE_REQUIRED:'+k)
    if not isolation.get('session_identity') or not isolation.get('workspace_identity') or not isolation.get('reviewer_selection_authority') or not isolation.get('evidence_refs'):reasons.append('ISOLATION_PROVENANCE_INCOMPLETE')
    if pair.get('prepared_before_any_result') is not True or pair.get('results_present_at_prepare') is not False:reasons.append('PAIR_NOT_PREPARED_BEFORE_RESULTS')
    try:
        pair_at=dt(pair['prepared_at']); started=dt(isolation['execution_started_at']); finished=dt(isolation['execution_finished_at'])
        if not pair_at<=started<=finished:reasons.append('CUSTODY_TIME_ORDER_INVALID')
    except Exception:reasons.append('CUSTODY_TIMESTAMPS_INVALID')
    if wave.get('same_authoring_context_attestation') is not False:reasons.append('SAME_AUTHORING_CONTEXT')
    if wave.get('prior_conclusion_exposure_attestation') is not False:reasons.append('PRIOR_CONCLUSION_EXPOSURE')
    if wave.get('final_claim_allowed') is not False or isolation.get('final_claim_allowed') is not False:reasons.append('FINAL_CLAIM_NOT_FALSE')
    if reasons:
        print(json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_WAVE_SEALING_V1','wave_id':wid,'decision':'HOLD_NONFINAL','blocking_reasons':sorted(set(reasons)),'final_claim_allowed':False},ensure_ascii=False,sort_keys=True)); return 42
    sealed_at=now(); custody={
      'contract':'ONSURE_DESIGN_DISCOVERY_WAVE_CUSTODY_V1','wave_id':wid,
      'pair_digest':pair['pair_digest'],'envelope_digest':manifest['envelope_digest'],'input_bundle_digest':pair['input_bundle_digest'],
      'frozen_tree_sha':pair['frozen_tree_sha'],'frozen_authority_digest':pair['frozen_authority_digest'],
      'wave_result_digest':wave['receipt_digest'],'candidate_ledger_digest':ledger_digest,'execution_isolation_evidence_digest':isolation['evidence_digest'],
      'reviewer_principal':wave['reviewer_principal'],'reviewer_process_lineage':wave['reviewer_process_lineage'],
      'session_identity':isolation['session_identity'],'workspace_identity':isolation['workspace_identity'],
      'credential_scope_isolated':bool(isolation.get('credential_scope_isolated',False)),
      'reviewer_selection_authority':isolation['reviewer_selection_authority'],
      'pair_prepared_at':pair['prepared_at'],'execution_started_at':isolation['execution_started_at'],'execution_finished_at':isolation['execution_finished_at'],'sealed_at':sealed_at,
      'result_sealed_before_reconciliation':True,'opposite_wave_result_accessed':False,
      'custody_events':[{'event':'ENVELOPE_PREPARED','at':pair['prepared_at']},{'event':'REVIEW_EXECUTION_COMPLETED','at':isolation['execution_finished_at']},{'event':'RESULT_SEALED','at':sealed_at}],
      'final_claim_allowed':False}
    custody['receipt_digest']=canon(custody)
    out=SEALED/wid
    if out.exists():shutil.rmtree(out)
    out.mkdir(parents=True)
    for src,name in ((wave_result_path,'wave-result.json'),(ledger_path,'candidate-ledger.json'),(isolation_path,'execution-isolation.json'),(ENVELOPES/wid/'envelope-manifest.json','envelope-manifest.json'),(ENVELOPES/'envelope-pair-receipt.json','envelope-pair-receipt.json')):shutil.copy2(src,out/name)
    (out/'custody-receipt.json').write_text(json.dumps(custody,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_WAVE_SEALING_V1','wave_id':wid,'decision':'SEALED_NONFINAL','wave_result_digest':wave['receipt_digest'],'candidate_ledger_digest':ledger_digest,'execution_isolation_evidence_digest':isolation['evidence_digest'],'custody_receipt_digest':custody['receipt_digest'],'sealed_dir':str(out),'final_claim_allowed':False},ensure_ascii=False,sort_keys=True)); return 0
if __name__=='__main__':raise SystemExit(main())
