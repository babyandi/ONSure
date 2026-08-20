#!/usr/bin/env python3
from __future__ import annotations

import hashlib,json,os,sys
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
POLICY_CC=ROOT/'contracts/design-discovery-independence-control-graph.v1.json'
POLICY_P1=ROOT/'contracts/design-discovery-p1-novelty-policy.v1.json'
BLIND_POLICY=ROOT/'contracts/design-discovery-blind-execution-policy.v1.json'
PROMPT=ROOT/'contracts/design-discovery-blind-wave-prompt-template.v1.txt'
RECONCILER_CONTRACT=ROOT/'contracts/design-discovery-reconciler.v2.json'
OUT=ROOT/'.onsure/design-discovery-v3/reconciliation-receipt.json'
WAVES=('INDEPENDENT-SATURATION-A','INDEPENDENT-SATURATION-B')

def raw_sha(p:Path)->str:return hashlib.sha256(p.read_bytes()).hexdigest()
def canon(d:dict,drop:str|None=None)->str:
    x=dict(d)
    if drop:x.pop(drop,None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def load(p:Path)->dict:return json.loads(p.read_text(encoding='utf-8'))
def parse_dt(v:str)->datetime:
    if v.endswith('Z'):v=v[:-1]+'+00:00'
    return datetime.fromisoformat(v)
def self_sha()->str:return raw_sha(Path(__file__))
def files(base:Path,wid:str)->dict[str,Path]:return {'wave':base/f'{wid}.json','ledger':base/f'{wid}.candidate-ledger.json','isolation':base/f'{wid}.execution-isolation.json','custody':base/f'{wid}.custody.json','envelope':base/f'{wid}.envelope.json'}
def require_docs(base:Path,wid:str):
    reasons=[];docs={}
    for k,p in files(base,wid).items():
        if not p.is_file():reasons.append(f'{wid}:MISSING_{k.upper()}');docs[k]={}
        else:
            try:docs[k]=load(p)
            except Exception:reasons.append(f'{wid}:INVALID_JSON_{k.upper()}');docs[k]={}
    return docs['wave'],docs['ledger'],docs['isolation'],docs['custody'],docs['envelope'],reasons

def validate_wave_structural(wid,w,ledger,iso,custody,envelope,pair,baseline,baseline_sha,blind_sha,prompt_sha):
    r=[];tokens=set()
    if w.get('contract')!='ONSURE_INDEPENDENT_DESIGN_DISCOVERY_WAVE_RESULT_V3':r.append('WAVE_CONTRACT_NOT_V3')
    if w.get('wave_id')!=wid:r.append('WAVE_ID_MISMATCH')
    if w.get('receipt_digest')!=canon(w,'receipt_digest'):r.append('WAVE_RECEIPT_DIGEST_INVALID')
    else:tokens.add('immutable_receipt_digest')
    if envelope.get('contract')!='ONSURE_DESIGN_DISCOVERY_SEALED_ENVELOPE_V1':r.append('ENVELOPE_CONTRACT_INVALID')
    if envelope.get('wave_id')!=wid:r.append('ENVELOPE_WAVE_ID_MISMATCH')
    if envelope.get('envelope_digest')!=canon(envelope,'envelope_digest'):r.append('ENVELOPE_DIGEST_INVALID')
    pair_envelope=((pair.get('envelopes') or {}).get(wid) or {})
    if pair_envelope.get('envelope_digest')!=envelope.get('envelope_digest') or pair_envelope.get('envelope_id')!=envelope.get('envelope_id'):r.append('PAIR_ENVELOPE_BINDING_MISMATCH')
    if envelope.get('read_only_frozen_bundle') is not True:r.append('ENVELOPE_NOT_READ_ONLY')
    if envelope.get('repository_browsing_allowed') is not False or envelope.get('issue_pr_comment_browsing_allowed') is not False:r.append('ENVELOPE_BROWSING_NOT_FORBIDDEN')
    if envelope.get('opposite_wave_result_mounted') is not False or envelope.get('result_present_at_prepare') is not False:r.append('ENVELOPE_RESULT_EXPOSURE_AT_PREPARE')
    if iso.get('contract')!='ONSURE_DESIGN_DISCOVERY_WAVE_EXECUTION_ISOLATION_V1':r.append('ISOLATION_CONTRACT_INVALID')
    if iso.get('wave_id')!=wid:r.append('ISOLATION_WAVE_ID_MISMATCH')
    if iso.get('evidence_digest')!=canon(iso,'evidence_digest'):r.append('ISOLATION_DIGEST_INVALID')
    if custody.get('contract')!='ONSURE_DESIGN_DISCOVERY_WAVE_CUSTODY_V1':r.append('CUSTODY_CONTRACT_INVALID')
    if custody.get('wave_id')!=wid:r.append('CUSTODY_WAVE_ID_MISMATCH')
    if custody.get('receipt_digest')!=canon(custody,'receipt_digest'):r.append('CUSTODY_DIGEST_INVALID')
    else:tokens.update({'sealed_result_custody','custody_ledger'})
    ledger_digest=canon(ledger)
    if w.get('candidate_ledger_digest')!=ledger_digest or custody.get('candidate_ledger_digest')!=ledger_digest:r.append('CANDIDATE_LEDGER_DIGEST_MISMATCH')
    expected_tree=baseline.get('git_tree_sha');expected_auth=baseline.get('authority_population_digest')
    for d,name in ((w,'WAVE'),(envelope,'ENVELOPE'),(custody,'CUSTODY')):
        if d.get('frozen_tree_sha')!=expected_tree:r.append(name+'_TREE_NOT_BOUND_TO_BASELINE')
        if d.get('frozen_authority_digest')!=expected_auth:r.append(name+'_AUTHORITY_NOT_BOUND_TO_BASELINE')
    if pair.get('frozen_tree_sha')!=expected_tree or pair.get('frozen_authority_digest')!=expected_auth:r.append('PAIR_NOT_BOUND_TO_BASELINE')
    else:tokens.add('tree_digest_binding')
    if w.get('frozen_baseline_receipt_sha256')!=baseline_sha or envelope.get('frozen_baseline_receipt_sha256')!=baseline_sha or pair.get('frozen_baseline_receipt_sha256')!=baseline_sha:r.append('BASELINE_RECEIPT_SHA_MISMATCH')
    input_digest=pair.get('input_bundle_digest')
    for d,name in ((w,'WAVE'),(envelope,'ENVELOPE'),(iso,'ISOLATION'),(custody,'CUSTODY')):
        if d.get('input_bundle_digest')!=input_digest:r.append(name+'_INPUT_BUNDLE_DIGEST_MISMATCH')
    if input_digest!=expected_auth:r.append('INPUT_BUNDLE_NOT_AUTHORITY_POPULATION')
    if envelope.get('envelope_digest')!=w.get('envelope_digest') or envelope.get('envelope_digest')!=iso.get('envelope_digest') or envelope.get('envelope_digest')!=custody.get('envelope_digest'):r.append('ENVELOPE_BINDING_MISMATCH')
    if iso.get('evidence_digest')!=w.get('execution_isolation_evidence_digest') or iso.get('evidence_digest')!=custody.get('execution_isolation_evidence_digest'):r.append('ISOLATION_BINDING_MISMATCH')
    if custody.get('wave_result_digest')!=w.get('receipt_digest'):r.append('CUSTODY_WAVE_RESULT_BINDING_MISMATCH')
    if custody.get('pair_digest')!=pair.get('pair_digest'):r.append('CUSTODY_PAIR_BINDING_MISMATCH')
    if w.get('reviewer_principal')!=iso.get('reviewer_principal') or w.get('reviewer_principal')!=custody.get('reviewer_principal'):r.append('REVIEWER_PRINCIPAL_BINDING_MISMATCH')
    if w.get('reviewer_process_lineage')!=iso.get('reviewer_process_lineage') or w.get('reviewer_process_lineage')!=custody.get('reviewer_process_lineage'):r.append('REVIEWER_PROCESS_BINDING_MISMATCH')
    if iso.get('session_identity')!=custody.get('session_identity') or iso.get('workspace_identity')!=custody.get('workspace_identity'):r.append('SESSION_WORKSPACE_CUSTODY_BINDING_MISMATCH')
    if pair.get('blind_execution_policy_sha256')!=blind_sha or envelope.get('blind_execution_policy_sha256')!=blind_sha:r.append('BLIND_POLICY_DIGEST_MISMATCH')
    else:tokens.add('blindness_constraints')
    if pair.get('prompt_template_sha256')!=prompt_sha or envelope.get('prompt_template_sha256')!=prompt_sha:r.append('PROMPT_DIGEST_MISMATCH')
    else:tokens.add('prompt_digest')
    for k in ('fresh_session','read_only_frozen_bundle','sealed_envelope_only','result_written_by_reviewer'):
        if iso.get(k) is not True:r.append('ISOLATION_TRUE_REQUIRED:'+k)
    for k in ('repository_browsing_performed','issue_pr_comment_browsing_performed','opposite_wave_result_accessible','opposite_wave_result_accessed','prior_conclusion_source_accessed','shared_memory_context','orchestrator_result_intermediation'):
        if iso.get(k) is not False:r.append('ISOLATION_FALSE_REQUIRED:'+k)
    if iso.get('fresh_session') is True:tokens.add('fresh_session_attestation')
    if iso.get('read_only_frozen_bundle') is True:tokens.add('read_only_frozen_bundle')
    if iso.get('shared_memory_context') is False:tokens.add('memory_isolation_evidence')
    if iso.get('sealed_envelope_only') is True and iso.get('repository_browsing_performed') is False and iso.get('issue_pr_comment_browsing_performed') is False and iso.get('opposite_wave_result_accessible') is False and iso.get('opposite_wave_result_accessed') is False and iso.get('prior_conclusion_source_accessed') is False:tokens.add('blind_input_enforcement')
    if iso.get('orchestrator_result_intermediation') is False and iso.get('result_written_by_reviewer') is True:tokens.add('no_result_intermediation')
    if iso.get('credential_scope_isolated') is True:tokens.add('credential_scope_isolation')
    if iso.get('reviewer_selection_authority'):tokens.add('selection_authority_disclosed')
    if w.get('model_or_method_lineage'):tokens.add('model_or_method_lineage')
    if envelope.get('read_only_frozen_bundle') is True and envelope.get('repository_browsing_allowed') is False:tokens.add('workspace_isolation')
    if w.get('same_authoring_context_attestation') is not False:r.append('SAME_AUTHORING_CONTEXT')
    if w.get('prior_conclusion_exposure_attestation') is not False:r.append('PRIOR_CONCLUSION_EXPOSURE')
    if w.get('mandatory_lens_coverage_percent')!=100:r.append('MANDATORY_LENS_NOT_100')
    if w.get('untriaged_candidate_count')!=0:r.append('UNTRIAGED_NOT_ZERO')
    if w.get('triage_percent')!=100:r.append('TRIAGE_NOT_100')
    if w.get('final_claim_allowed') is not False or iso.get('final_claim_allowed') is not False or custody.get('final_claim_allowed') is not False:r.append('FINAL_CLAIM_NOT_FALSE')
    if not iso.get('evidence_refs') or not iso.get('reviewer_selection_authority'):r.append('ISOLATION_EVIDENCE_PROVENANCE_INCOMPLETE')
    try:
        pp=parse_dt(pair['prepared_at']);s=parse_dt(iso['execution_started_at']);f=parse_dt(iso['execution_finished_at']);z=parse_dt(custody['sealed_at'])
        if not pp<=s<=f<=z:r.append('CUSTODY_TIME_ORDER_INVALID')
        if custody.get('pair_prepared_at')!=pair.get('prepared_at'):r.append('CUSTODY_PAIR_TIME_MISMATCH')
    except Exception:r.append('CUSTODY_TIMESTAMPS_INVALID')
    if custody.get('result_sealed_before_reconciliation') is not True or custody.get('opposite_wave_result_accessed') is not False:r.append('CUSTODY_BLINDNESS_INVALID')
    return r,tokens

def common_control(w,policy,tokens):
    r=[];dims=policy.get('dimensions') or [];supplied=[x for x in w.get('common_control_dimensions',[]) if isinstance(x,dict)];by={x.get('id'):x for x in supplied if x.get('id')};expected={d.get('id') for d in dims}
    if len(supplied)!=len(expected) or len(by)!=len(expected) or set(by)!=expected:r.append('COMMON_CONTROL_DIMENSION_SET_NOT_EXACT')
    for dim in dims:
        did=dim['id'];cls=dim['classification'];item=by.get(did)
        if item is None:continue
        present=item.get('present')
        if not isinstance(present,bool):r.append('COMMON_CONTROL_PRESENT_NOT_BOOLEAN:'+did);continue
        if not present:continue
        if cls=='DISQUALIFYING':r.append('DISQUALIFYING_COMMON_CONTROL:'+did);continue
        required=set(dim.get('required_evidence') or []);claimed=set(item.get('mitigation_evidence') or []);missing=required-claimed;unsupported=(required & claimed)-tokens
        if missing:r.append(('COMMON_CONTROL_MITIGATION_CLAIM_MISSING:' if cls=='REQUIRES_MITIGATION' else 'COMMON_CONTROL_DISCLOSURE_EVIDENCE_MISSING:')+did+':'+'|'.join(sorted(missing)))
        if unsupported:r.append('COMMON_CONTROL_EVIDENCE_UNPROVEN:'+did+':'+'|'.join(sorted(unsupported)))
    if not w.get('common_control_attestation'):r.append('COMMON_CONTROL_ATTESTATION_MISSING')
    return r

def p1_rules(w,p):
    r=[]
    if w.get('new_p0_count')!=p.get('p0_ceiling',0):r.append('P0_CEILING_EXCEEDED')
    if w.get('blocking_p1_count',0)>p.get('blocking_p1_ceiling',0):r.append('BLOCKING_P1_CEILING_EXCEEDED')
    if w.get('triage_percent')!=p.get('required_triage_percent',100):r.append('TRIAGE_NOT_100')
    if w.get('authority_affecting_p1_count',0)!=w.get('authority_affecting_p1_disposition_count',0):r.append('AUTHORITY_AFFECTING_P1_UNDISPOSED')
    ceiling=p.get('nonblocking_p1_ceiling')
    if ceiling is None:r.append('NONBLOCKING_P1_POLICY_DEFERRED_HUMAN_DECISION_REQUIRED')
    elif w.get('nonblocking_p1_count',0)>ceiling:r.append('NONBLOCKING_P1_CEILING_EXCEEDED')
    return r

def main()->int:
    raw=os.environ.get('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR','').strip()
    if not raw:print('ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR required',file=sys.stderr);return 2
    base=Path(raw).expanduser();base=base if base.is_absolute() else ROOT/base
    baseline_path=base/'frozen-baseline-receipt.json';pair_path=base/'envelope-pair-receipt.json';reasons=[]
    if not baseline_path.is_file():reasons.append('FROZEN_BASELINE_RECEIPT_MISSING');baseline={};baseline_sha=''
    else:baseline=load(baseline_path);baseline_sha=raw_sha(baseline_path)
    if not pair_path.is_file():reasons.append('ENVELOPE_PAIR_RECEIPT_MISSING');pair={}
    else:pair=load(pair_path)
    if baseline and baseline.get('contract')!='ONSURE_INDEPENDENT_DISCOVERY_FROZEN_BASELINE_V2':reasons.append('BASELINE_CONTRACT_INVALID')
    if pair:
        if pair.get('contract')!='ONSURE_DESIGN_DISCOVERY_SEALED_ENVELOPE_PAIR_V1':reasons.append('PAIR_CONTRACT_INVALID')
        if pair.get('pair_digest')!=canon(pair,'pair_digest'):reasons.append('PAIR_DIGEST_INVALID')
        if pair.get('prepared_before_any_result') is not True or pair.get('results_present_at_prepare') is not False:reasons.append('PAIR_NOT_PREPARED_BEFORE_RESULTS')
    cc=load(POLICY_CC);p1=load(POLICY_P1);blind_sha=raw_sha(BLIND_POLICY);prompt_sha=raw_sha(PROMPT)
    wave_docs={};token_map={};iso_map={};custody_map={}
    for wid in WAVES:
        w,l,i,c,e,miss=require_docs(base,wid);wave_docs[wid]=w;iso_map[wid]=i;custody_map[wid]=c;reasons+=miss
        rr,tokens=validate_wave_structural(wid,w,l,i,c,e,pair,baseline,baseline_sha,blind_sha,prompt_sha);reasons += [wid+':'+x for x in rr];token_map[wid]=tokens
    a,b=wave_docs[WAVES[0]],wave_docs[WAVES[1]];ai,bi=iso_map[WAVES[0]],iso_map[WAVES[1]]
    processes_distinct=False;workspaces_distinct=False;sessions_distinct=False
    if a and b:
        if a.get('frozen_tree_sha')!=b.get('frozen_tree_sha'):reasons.append('WAVE_TREE_MISMATCH')
        if a.get('frozen_authority_digest')!=b.get('frozen_authority_digest'):reasons.append('WAVE_AUTHORITY_MISMATCH')
        if a.get('reviewer_principal')==b.get('reviewer_principal'):reasons.append('REVIEWER_PRINCIPAL_NOT_DISTINCT')
        if a.get('reviewer_process_lineage')==b.get('reviewer_process_lineage'):reasons.append('REVIEWER_PROCESS_NOT_DISTINCT')
        else:processes_distinct=True
        if ai.get('session_identity')==bi.get('session_identity'):reasons.append('SESSION_IDENTITY_NOT_DISTINCT')
        else:sessions_distinct=True
        if ai.get('workspace_identity')==bi.get('workspace_identity'):reasons.append('WORKSPACE_IDENTITY_NOT_DISTINCT')
        else:workspaces_distinct=True
    for wid in WAVES:
        if sessions_distinct:token_map[wid].add('separate_session_identity')
        if processes_distinct:token_map[wid].add('process_isolation')
        if workspaces_distinct:token_map[wid].update({'separate_workspace','workspace_isolation'})
    reconciler_principal='ONSURE_DETERMINISTIC_DISCOVERY_RECONCILER_V2';reconciler_process='sha256:'+self_sha()
    for wid in WAVES:
        w=wave_docs[wid]
        if w.get('reviewer_principal')==reconciler_principal:reasons.append(wid+':RECONCILER_PRINCIPAL_SOD_VIOLATION')
        if w.get('reviewer_process_lineage')==reconciler_process:reasons.append(wid+':RECONCILER_PROCESS_SOD_VIOLATION')
        token_map[wid].update({'independent_reconciler','promotion_sod_evidence'})
        reasons += [wid+':'+x for x in common_control(w,cc,token_map[wid])]
        reasons += [wid+':'+x for x in p1_rules(w,p1)]
    receipt={'contract':'ONSURE_DESIGN_DISCOVERY_RECONCILIATION_RECEIPT_V2','reconciler_principal':reconciler_principal,'reconciler_process_lineage':reconciler_process,'reconciler_contract_sha256':raw_sha(RECONCILER_CONTRACT),'blind_execution_policy_sha256':blind_sha,'prompt_template_sha256':prompt_sha,'pair_digest':pair.get('pair_digest'),'wave_a_digest':a.get('receipt_digest'),'wave_b_digest':b.get('receipt_digest'),'wave_a_custody_digest':custody_map[WAVES[0]].get('receipt_digest'),'wave_b_custody_digest':custody_map[WAVES[1]].get('receipt_digest'),'frozen_tree_sha':baseline.get('git_tree_sha'),'frozen_authority_digest':baseline.get('authority_population_digest'),'nonblocking_p1_policy_state':p1.get('nonblocking_p1_ceiling_state'),'blocking_reasons':sorted(set(reasons)),'decision':'SATURATION_CANDIDATE_NONFINAL' if not reasons else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    receipt['receipt_digest']=canon(receipt);OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False,sort_keys=True));return 0 if not reasons else 31
if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as e:
        print(json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_RECONCILIATION_RECEIPT_V2','decision':'HOLD_NONFINAL','reason':f'{type(e).__name__}:{e}','final_claim_allowed':False},ensure_ascii=False,sort_keys=True));raise SystemExit(1)
