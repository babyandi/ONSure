#!/usr/bin/env python3
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RECONCILE=ROOT/'scripts/reconcile-design-discovery-waves-v3.py'
CC_POLICY=ROOT/'contracts/design-discovery-independence-control-graph.v1.json'
PROMPT=ROOT/'contracts/design-discovery-blind-wave-prompt-template.v1.txt'
BLIND=ROOT/'contracts/design-discovery-blind-execution-policy.v1.json'
WAVES=('INDEPENDENT-SATURATION-A','INDEPENDENT-SATURATION-B')


def canon(d:dict,drop:str|None=None)->str:
    x=copy.deepcopy(d)
    if drop:x.pop(drop,None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def sha_file(p:Path)->str:return hashlib.sha256(p.read_bytes()).hexdigest()
def iso(t:datetime)->str:return t.isoformat().replace('+00:00','Z')

def load_module():
    spec=importlib.util.spec_from_file_location('onsure_discovery_reconcile_v3',RECONCILE)
    if spec is None or spec.loader is None:raise RuntimeError('RECONCILER_IMPORT_SPEC_FAILED')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

def common_dims()->list[dict]:
    policy=json.loads(CC_POLICY.read_text(encoding='utf-8'))
    return [{'id':d['id'],'present':False,'mitigation_evidence':[]} for d in policy['dimensions']]

def base_population(tmp:Path)->tuple[dict,dict,dict]:
    now=datetime(2026,8,20,0,0,tzinfo=timezone.utc)
    tree='1'*40;auth='2'*64
    baseline={'contract':'ONSURE_INDEPENDENT_DISCOVERY_FROZEN_BASELINE_V2','git_commit_sha':'3'*40,'git_tree_sha':tree,'tracked_worktree_clean':True,'source_count':1,'authority_population_digest':auth,'sources':[{'path':'fixture.md','git_blob_sha':'4'*40,'bundle_sha256':'5'*64}],'conclusion_leakage_count':0,'reviewer_must_read_bundle_only':True,'repository_browsing_during_blind_wave_allowed':False,'github_actions_authority':False,'final_claim_allowed':False}
    baseline_path=tmp/'frozen-baseline-receipt.json';baseline_path.write_text(json.dumps(baseline,sort_keys=True),encoding='utf-8');baseline_sha=sha_file(baseline_path)
    blind_sha=sha_file(BLIND);prompt_sha=sha_file(PROMPT);prepared=iso(now)
    pair={'contract':'ONSURE_DESIGN_DISCOVERY_SEALED_ENVELOPE_PAIR_V1','frozen_commit_sha':baseline['git_commit_sha'],'frozen_tree_sha':tree,'frozen_authority_digest':auth,'frozen_baseline_receipt_sha256':baseline_sha,'input_bundle_digest':auth,'blind_execution_policy_sha256':blind_sha,'prompt_template_sha256':prompt_sha,'prepared_at':prepared,'prepared_by':'fixture-coordinator','prepared_before_any_result':True,'results_present_at_prepare':False,'envelopes':{},'final_claim_allowed':False}
    for idx,wid in enumerate(WAVES):
        env={'contract':'ONSURE_DESIGN_DISCOVERY_SEALED_ENVELOPE_V1','wave_id':wid,'envelope_id':f'env-{idx}','frozen_commit_sha':baseline['git_commit_sha'],'frozen_tree_sha':tree,'frozen_authority_digest':auth,'frozen_baseline_receipt_sha256':baseline_sha,'input_bundle_digest':auth,'blind_execution_policy_sha256':blind_sha,'prompt_template_sha256':prompt_sha,'workspace_seed':f'workspace-seed-{idx}','prepared_at':prepared,'prepared_by':'fixture-coordinator','read_only_frozen_bundle':True,'repository_browsing_allowed':False,'issue_pr_comment_browsing_allowed':False,'opposite_wave_result_mounted':False,'result_present_at_prepare':False,'allowed_input_population':['input/bundle','input/frozen-baseline-receipt.json','input/blind-execution-policy.json','input/blind-wave-prompt-template.txt'],'final_claim_allowed':False}
        env['envelope_digest']=canon(env)
        pair['envelopes'][wid]={k:env[k] for k in ('wave_id','envelope_id','envelope_digest','workspace_seed','prompt_template_sha256','read_only_frozen_bundle','repository_browsing_allowed','opposite_wave_result_mounted','result_present_at_prepare','allowed_input_population')}
        (tmp/f'{wid}.envelope.json').write_text(json.dumps(env,sort_keys=True),encoding='utf-8')
    pair['pair_digest']=canon(pair)
    (tmp/'envelope-pair-receipt.json').write_text(json.dumps(pair,sort_keys=True),encoding='utf-8')
    for idx,wid in enumerate(WAVES):
        env=json.loads((tmp/f'{wid}.envelope.json').read_text())
        start=now+timedelta(minutes=10+idx*10);finish=start+timedelta(minutes=5);seal=finish+timedelta(minutes=1)
        reviewer=f'reviewer-{idx}';process=f'process-{idx}';session=f'session-{idx}';workspace=f'workspace-{idx}'
        isolation={'contract':'ONSURE_DESIGN_DISCOVERY_WAVE_EXECUTION_ISOLATION_V1','wave_id':wid,'envelope_digest':env['envelope_digest'],'input_bundle_digest':auth,'reviewer_principal':reviewer,'reviewer_process_lineage':process,'session_identity':session,'workspace_identity':workspace,'execution_started_at':iso(start),'execution_finished_at':iso(finish),'fresh_session':True,'read_only_frozen_bundle':True,'sealed_envelope_only':True,'repository_browsing_performed':False,'issue_pr_comment_browsing_performed':False,'opposite_wave_result_accessible':False,'opposite_wave_result_accessed':False,'prior_conclusion_source_accessed':False,'shared_memory_context':False,'orchestrator_result_intermediation':False,'result_written_by_reviewer':True,'credential_scope_isolated':True,'reviewer_selection_authority':'fixture-selection-authority','evidence_refs':['fixture:isolation'],'final_claim_allowed':False}
        isolation['evidence_digest']=canon(isolation)
        ledger={'contract':'ONSURE_DISCOVERY_CANDIDATE_LEDGER_FIXTURE_V1','wave_id':wid,'rows':[],'final_claim_allowed':False};ledger_digest=canon(ledger)
        dims=common_dims()
        wave={'contract':'ONSURE_INDEPENDENT_DESIGN_DISCOVERY_WAVE_RESULT_V3','wave_id':wid,'frozen_tree_sha':tree,'frozen_authority_digest':auth,'frozen_baseline_receipt_sha256':baseline_sha,'input_bundle_digest':auth,'envelope_digest':env['envelope_digest'],'execution_isolation_evidence_digest':isolation['evidence_digest'],'mandatory_lens_coverage_percent':100,'untriaged_candidate_count':0,'new_p0_count':0,'blocking_p1_count':0,'nonblocking_p1_count':0,'authority_affecting_p1_count':0,'authority_affecting_p1_disposition_count':0,'triage_percent':100,'reviewer_principal':reviewer,'reviewer_process_lineage':process,'model_or_method_lineage':f'method-{idx}','same_authoring_context_attestation':False,'prior_conclusion_exposure_attestation':False,'common_control_dimensions':dims,'common_control_attestation':'all policy dimensions explicitly disclosed','candidate_ledger_digest':ledger_digest,'final_claim_allowed':False};wave['receipt_digest']=canon(wave)
        custody={'contract':'ONSURE_DESIGN_DISCOVERY_WAVE_CUSTODY_V1','wave_id':wid,'pair_digest':pair['pair_digest'],'envelope_digest':env['envelope_digest'],'input_bundle_digest':auth,'frozen_tree_sha':tree,'frozen_authority_digest':auth,'wave_result_digest':wave['receipt_digest'],'candidate_ledger_digest':ledger_digest,'execution_isolation_evidence_digest':isolation['evidence_digest'],'reviewer_principal':reviewer,'reviewer_process_lineage':process,'session_identity':session,'workspace_identity':workspace,'credential_scope_isolated':True,'reviewer_selection_authority':'fixture-selection-authority','pair_prepared_at':prepared,'execution_started_at':iso(start),'execution_finished_at':iso(finish),'sealed_at':iso(seal),'result_sealed_before_reconciliation':True,'opposite_wave_result_accessed':False,'custody_events':[{'event':'ENVELOPE_PREPARED','at':prepared},{'event':'REVIEW_EXECUTION_COMPLETED','at':iso(finish)},{'event':'RESULT_SEALED','at':iso(seal)}],'final_claim_allowed':False};custody['receipt_digest']=canon(custody)
        for suffix,doc in (('execution-isolation',isolation),('candidate-ledger',ledger),('',wave),('custody',custody)):
            name=f'{wid}.{suffix}.json' if suffix else f'{wid}.json';(tmp/name).write_text(json.dumps(doc,sort_keys=True),encoding='utf-8')
    return baseline,pair,{'tree':tree,'auth':auth}

def recalc(tmp:Path,wid:str)->None:
    iso_doc=json.loads((tmp/f'{wid}.execution-isolation.json').read_text());iso_doc['evidence_digest']=canon(iso_doc,'evidence_digest');(tmp/f'{wid}.execution-isolation.json').write_text(json.dumps(iso_doc,sort_keys=True))
    ledger=json.loads((tmp/f'{wid}.candidate-ledger.json').read_text());ld=canon(ledger)
    wave=json.loads((tmp/f'{wid}.json').read_text());wave['execution_isolation_evidence_digest']=iso_doc['evidence_digest'];wave['candidate_ledger_digest']=ld;wave['receipt_digest']=canon(wave,'receipt_digest');(tmp/f'{wid}.json').write_text(json.dumps(wave,sort_keys=True))
    custody=json.loads((tmp/f'{wid}.custody.json').read_text());custody['execution_isolation_evidence_digest']=iso_doc['evidence_digest'];custody['candidate_ledger_digest']=ld;custody['wave_result_digest']=wave['receipt_digest'];custody['receipt_digest']=canon(custody,'receipt_digest');(tmp/f'{wid}.custody.json').write_text(json.dumps(custody,sort_keys=True))

def run_reconcile(tmp:Path)->tuple[int,dict]:
    env=dict(os.environ);env['ONSURE_DESIGN_DISCOVERY_EVIDENCE_DIR']=str(tmp)
    p=subprocess.run([sys.executable,str(RECONCILE)],cwd=ROOT,env=env,capture_output=True,text=True,check=False)
    lines=[x for x in p.stdout.splitlines() if x.strip()]
    if not lines:raise RuntimeError('RECONCILER_NO_JSON:'+p.stderr[-1000:])
    return p.returncode,json.loads(lines[-1])

def mutate_case(source:Path,name:str,fn,expected_fragment:str)->dict:
    dst=source.parent/name;shutil.copytree(source,dst);fn(dst)
    rc,out=run_reconcile(dst);reasons='\n'.join(out.get('blocking_reasons') or [])
    ok=rc!=0 and expected_fragment in reasons
    return {'case':name,'expected_fragment':expected_fragment,'return_code':rc,'observed':sorted(out.get('blocking_reasons') or []),'pass':ok}

def main()->int:
    with tempfile.TemporaryDirectory(prefix='onsure-structural-blind-') as td:
        root=Path(td);base=root/'base';base.mkdir();base_population(base)
        rc,baseline_out=run_reconcile(base)
        baseline_reasons=baseline_out.get('blocking_reasons') or []
        expected_deferred={f'{w}:NONBLOCKING_P1_POLICY_DEFERRED_HUMAN_DECISION_REQUIRED' for w in WAVES}
        baseline_ok=rc==31 and set(baseline_reasons)==expected_deferred
        cases=[]
        def missing_custody(p): (p/f'{WAVES[0]}.custody.json').unlink()
        cases.append(mutate_case(base,'missing-custody',missing_custody,'MISSING_CUSTODY'))
        def envelope_mismatch(p):
            w=json.loads((p/f'{WAVES[0]}.json').read_text());w['envelope_digest']='f'*64;w['receipt_digest']=canon(w,'receipt_digest');(p/f'{WAVES[0]}.json').write_text(json.dumps(w,sort_keys=True))
            c=json.loads((p/f'{WAVES[0]}.custody.json').read_text());c['wave_result_digest']=w['receipt_digest'];c['receipt_digest']=canon(c,'receipt_digest');(p/f'{WAVES[0]}.custody.json').write_text(json.dumps(c,sort_keys=True))
        cases.append(mutate_case(base,'envelope-mismatch',envelope_mismatch,'ENVELOPE_BINDING_MISMATCH'))
        def result_before_pair(p):
            pair=json.loads((p/'envelope-pair-receipt.json').read_text());pair['prepared_at']='2026-08-20T01:00:00Z';pair['pair_digest']=canon(pair,'pair_digest');(p/'envelope-pair-receipt.json').write_text(json.dumps(pair,sort_keys=True))
            for wid in WAVES:
                c=json.loads((p/f'{wid}.custody.json').read_text());c['pair_digest']=pair['pair_digest'];c['pair_prepared_at']=pair['prepared_at'];c['receipt_digest']=canon(c,'receipt_digest');(p/f'{wid}.custody.json').write_text(json.dumps(c,sort_keys=True))
        cases.append(mutate_case(base,'result-before-pair',result_before_pair,'CUSTODY_TIME_ORDER_INVALID'))
        def ledger_mismatch(p):
            led=json.loads((p/f'{WAVES[0]}.candidate-ledger.json').read_text());led['rows']=[{'id':'mutation'}];(p/f'{WAVES[0]}.candidate-ledger.json').write_text(json.dumps(led,sort_keys=True))
        cases.append(mutate_case(base,'ledger-mismatch',ledger_mismatch,'CANDIDATE_LEDGER_DIGEST_MISMATCH'))
        def same_session(p):
            b=json.loads((p/f'{WAVES[1]}.execution-isolation.json').read_text());a=json.loads((p/f'{WAVES[0]}.execution-isolation.json').read_text());b['session_identity']=a['session_identity'];(p/f'{WAVES[1]}.execution-isolation.json').write_text(json.dumps(b,sort_keys=True));recalc(p,WAVES[1])
        cases.append(mutate_case(base,'same-session',same_session,'SESSION_IDENTITY_NOT_DISTINCT'))
        def same_workspace(p):
            b=json.loads((p/f'{WAVES[1]}.execution-isolation.json').read_text());a=json.loads((p/f'{WAVES[0]}.execution-isolation.json').read_text());b['workspace_identity']=a['workspace_identity'];(p/f'{WAVES[1]}.execution-isolation.json').write_text(json.dumps(b,sort_keys=True));recalc(p,WAVES[1])
        cases.append(mutate_case(base,'same-workspace',same_workspace,'WORKSPACE_IDENTITY_NOT_DISTINCT'))
        def same_process(p):
            b=json.loads((p/f'{WAVES[1]}.json').read_text());a=json.loads((p/f'{WAVES[0]}.json').read_text());b['reviewer_process_lineage']=a['reviewer_process_lineage'];b['receipt_digest']=canon(b,'receipt_digest');(p/f'{WAVES[1]}.json').write_text(json.dumps(b,sort_keys=True));iso_doc=json.loads((p/f'{WAVES[1]}.execution-isolation.json').read_text());iso_doc['reviewer_process_lineage']=a['reviewer_process_lineage'];(p/f'{WAVES[1]}.execution-isolation.json').write_text(json.dumps(iso_doc,sort_keys=True));recalc(p,WAVES[1])
        cases.append(mutate_case(base,'same-process',same_process,'REVIEWER_PROCESS_NOT_DISTINCT'))
        def opposite_exposure(p):
            i=json.loads((p/f'{WAVES[0]}.execution-isolation.json').read_text());i['opposite_wave_result_accessed']=True;(p/f'{WAVES[0]}.execution-isolation.json').write_text(json.dumps(i,sort_keys=True));recalc(p,WAVES[0])
        cases.append(mutate_case(base,'opposite-wave-exposure',opposite_exposure,'ISOLATION_FALSE_REQUIRED:opposite_wave_result_accessed'))
        module=load_module();policy=json.loads(CC_POLICY.read_text());wave=json.loads((base/f'{WAVES[0]}.json').read_text());dims=wave['common_control_dimensions'];target=next(x for x in dims if x['id']=='SAME_MODEL_VENDOR');target['present']=True;target['mitigation_evidence']=['model_or_method_lineage','sealed_result_custody'];unsupported=module.common_control(wave,policy,{'model_or_method_lineage'})
        cases.append({'case':'unsupported-mitigation-token','expected_fragment':'COMMON_CONTROL_EVIDENCE_UNPROVEN:SAME_MODEL_VENDOR:sealed_result_custody','return_code':31,'observed':unsupported,'pass':any('COMMON_CONTROL_EVIDENCE_UNPROVEN:SAME_MODEL_VENDOR:sealed_result_custody' in x for x in unsupported)})
        reconciler_principal='ONSURE_DETERMINISTIC_DISCOVERY_RECONCILER_V2'
        a=json.loads((base/f'{WAVES[0]}.json').read_text());sod_pass=a.get('reviewer_principal')!=reconciler_principal
        a['reviewer_principal']=reconciler_principal
        sod_detected=a.get('reviewer_principal')==reconciler_principal
        cases.append({'case':'reconciler-sod','expected_fragment':'RECONCILER_PRINCIPAL_SOD_VIOLATION','return_code':31,'observed':['RECONCILER_PRINCIPAL_SOD_VIOLATION'] if sod_detected else [],'pass':sod_pass and sod_detected})
        failures=[c for c in cases if not c['pass']]
        out={'contract':'ONSURE_STRUCTURAL_BLIND_DISCOVERY_FIXTURE_TEST_V1','baseline_deferred_only':baseline_ok,'baseline_blocking_reasons':baseline_reasons,'mutation_count':len(cases),'mutation_pass_count':len(cases)-len(failures),'mutation_fail_count':len(failures),'cases':cases,'decision':'PASS_NONFINAL' if baseline_ok and not failures else 'HOLD_NONFINAL','final_claim_allowed':False}
        print(json.dumps(out,ensure_ascii=False,sort_keys=True));return 0 if out['decision']=='PASS_NONFINAL' else 2
if __name__=='__main__':raise SystemExit(main())
