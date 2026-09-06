#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EXPECTED={f'DD-{i:03d}' for i in range(1,43)}
FIXTURE_CLASSES={'positive','negative','recovery','adversarial'}
BUNDLE=ROOT/'.onsure/dd-independent-qualification/frozen-bundle-successor/bundle-manifest.json'
BASE_PLAN=ROOT/'contracts/dd-semantic-evaluator-qualification-fixture-plan.candidate.v1.json'
EXT_PLAN=ROOT/'contracts/dd-semantic-evaluator-qualification-fixture-plan.extension-041-042.v1.json'
DD042_MIN=ROOT/'contracts/dd-042-adversarial-minimum-set.v1.json'
DERIVED=ROOT/'.onsure/dd-independent-qualification/validated-status-successor.json'
RECEIPTS=Path(os.environ.get('ONSURE_DD_QUALIFICATION_RECEIPTS_DIR','receipts/dd-semantic-evaluator-qualification'))
RECEIPTS=RECEIPTS if RECEIPTS.is_absolute() else ROOT/RECEIPTS

def load(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def sha256_file(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def digest_payload(d):
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def parse_dt(v):
    if v.endswith('Z'): v=v[:-1]+'+00:00'
    return datetime.fromisoformat(v)
def valid_sha(v): return isinstance(v,str) and len(v)==64 and all(c in '0123456789abcdef' for c in v)
def planned_ids(case):
    ids=case.get('fixture_ids')
    if isinstance(ids,list): return [x for x in ids if isinstance(x,str) and x]
    fid=case.get('fixture_id')
    return [fid] if isinstance(fid,str) and fid else []

def main()->int:
    errors=[]
    if not BUNDLE.is_file():
        errors.append('SUCCESSOR_QUALIFICATION_BUNDLE_V4_MISSING'); bundle={}
    else: bundle=load(BUNDLE)
    if bundle:
        if bundle.get('contract')!='ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V4': errors.append('SUCCESSOR_BUNDLE_CONTRACT_NOT_V4')
        if bundle.get('dd_denominator')!=42: errors.append('SUCCESSOR_BUNDLE_DD_DENOMINATOR_NOT_42')
        if bundle.get('qualification_fixture_denominator')!=173: errors.append('SUCCESSOR_BUNDLE_FIXTURE_DENOMINATOR_NOT_173')
        for f in ('base_evaluator_artifact_sha256','extension_evaluator_artifact_sha256','obligation_registry_population_sha256','base_fixture_plan_sha256','extension_fixture_plan_sha256','dd042_adversarial_minimum_set_sha256','fixture_authority_population_sha256'):
            if not valid_sha(bundle.get(f)): errors.append('SUCCESSOR_BUNDLE_'+f.upper()+'_INVALID')
        v=bundle.get('source_tree_sha')
        if not isinstance(v,str) or len(v)!=40: errors.append('SUCCESSOR_BUNDLE_SOURCE_TREE_INVALID')
    base_plan=load(BASE_PLAN); ext_plan=load(EXT_PLAN); minimum=load(DD042_MIN)
    base_rows=base_plan.get('rows',[]); ext_rows=ext_plan.get('rows',[])
    all_plan_rows=base_rows+ext_rows; by_dd={r.get('dd_id'):r for r in all_plan_rows}
    if len(base_rows)!=40 or len(ext_rows)!=2 or set(by_dd)!=EXPECTED: errors.append('SUCCESSOR_FIXTURE_DD_DENOMINATOR_NOT_EXACT_42')
    base_plan_sha=sha256_file(BASE_PLAN); ext_plan_sha=sha256_file(EXT_PLAN); minimum_sha=sha256_file(DD042_MIN)
    if bundle:
        if bundle.get('base_fixture_plan_sha256')!=base_plan_sha: errors.append('BASE_FIXTURE_PLAN_NOT_BOUND_TO_BUNDLE')
        if bundle.get('extension_fixture_plan_sha256')!=ext_plan_sha: errors.append('EXTENSION_FIXTURE_PLAN_NOT_BOUND_TO_BUNDLE')
        if bundle.get('dd042_adversarial_minimum_set_sha256')!=minimum_sha: errors.append('DD042_MINIMUM_SET_NOT_BOUND_TO_BUNDLE')
    fixture_ids=[]
    for dd,row in by_dd.items():
        cases=row.get('cases') or {}
        if set(cases)!=FIXTURE_CLASSES: errors.append(f'{dd}:FIXTURE_CLASSES_NOT_EXACT_FOUR')
        for klass in FIXTURE_CLASSES:
            case=cases.get(klass) or {}; ids=planned_ids(case)
            required_count=6 if dd=='DD-042' and klass=='adversarial' else 1
            if len(ids)!=required_count or not case.get('expected') or not case.get('purpose'): errors.append(f'{dd}:{klass}:FIXTURE_DEFINITION_INCOMPLETE_OR_COUNT_NOT_{required_count}')
            fixture_ids.extend(ids)
    if len(fixture_ids)!=173 or len(set(fixture_ids))!=173 or any(not x for x in fixture_ids): errors.append('SUCCESSOR_FIXTURE_DENOMINATOR_NOT_173_UNIQUE')
    minimum_ids={r.get('fixture_id') for r in minimum.get('mandatory_fixtures',[]) if r.get('fixture_id')}
    dd042_adv=set(planned_ids(((by_dd.get('DD-042') or {}).get('cases') or {}).get('adversarial') or {}))
    if minimum.get('minimum_adversarial_fixture_count')!=6 or len(minimum_ids)!=6: errors.append('DD042_MINIMUM_SET_NOT_EXACT_6')
    if dd042_adv!=minimum_ids: errors.append('DD042_ADVERSARIAL_PLAN_NOT_EXACT_MINIMUM_SET')
    qualified=0; invalid=0; now=datetime.now(timezone.utc); derived_rows=[]
    qualified_tree=bundle.get('source_tree_sha') if bundle else None
    population_sha=bundle.get('obligation_registry_population_sha256') if bundle else None
    for dd in sorted(EXPECTED):
        p=RECEIPTS/f'{dd}.json'; local=[]; r={}
        if not p.is_file(): local.append('QUALIFICATION_RECEIPT_MISSING')
        else:
            try:r=load(p)
            except Exception as e: local.append('RECEIPT_UNREADABLE:'+str(e))
        if r:
            if r.get('contract')!='ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_V1': local.append('WRONG_RECEIPT_CONTRACT')
            if r.get('dd_id')!=dd: local.append('RECEIPT_DD_ID_MISMATCH')
            if r.get('decision')!='QUALIFIED_NONFINAL': local.append('RECEIPT_DECISION_NOT_QUALIFIED')
            if r.get('final_claim_allowed') is not False: local.append('FINAL_CLAIM_NOT_FALSE')
            if r.get('receipt_digest')!=digest_payload(r): local.append('RECEIPT_DIGEST_MISMATCH')
            expected_version='design-gap-dd-evaluators-v2' if dd in {'DD-041','DD-042'} else 'builtin-dd-evaluators-v2'
            expected_artifact=(bundle.get('extension_evaluator_artifact_sha256') if dd in {'DD-041','DD-042'} else bundle.get('base_evaluator_artifact_sha256')) if bundle else None
            expected_plan_sha=ext_plan_sha if dd in {'DD-041','DD-042'} else base_plan_sha
            if r.get('evaluator_version')!=expected_version: local.append('EVALUATOR_VERSION_NOT_CURRENT')
            if r.get('evaluator_artifact_sha256')!=expected_artifact: local.append('EVALUATOR_ARTIFACT_NOT_BOUND_TO_SUCCESSOR_BUNDLE')
            if r.get('obligation_registry_sha256')!=population_sha: local.append('OBLIGATION_REGISTRY_POPULATION_NOT_BOUND')
            if r.get('source_tree_sha')!=qualified_tree: local.append('SOURCE_TREE_NOT_BOUND_TO_SUCCESSOR_BUNDLE')
            if r.get('fixture_plan_sha256')!=expected_plan_sha: local.append('FIXTURE_PLAN_NOT_BOUND_TO_CURRENT_PLAN')
            for f in ('evaluator_id','qualification_principal','qualification_process_lineage'):
                if not r.get(f): local.append(f.upper()+'_MISSING')
            att=r.get('independence_attestation') or {}
            for key in ('independent_from_evaluator_authoring','independent_from_target_claim_author','common_control_disclosed'):
                if att.get(key) is not True: local.append('INDEPENDENCE_'+key.upper()+'_NOT_TRUE')
            cases=(by_dd.get(dd) or {}).get('cases') or {}
            for klass in ('positive','negative','recovery','adversarial'):
                planned=planned_ids(cases.get(klass) or {})
                fr=(r.get('fixture_results') or {}).get(klass) or {}
                executed=fr.get('executed_count',0); passed=fr.get('passed_count',0); failed=fr.get('failed_count',0); reported=fr.get('fixture_ids') or []
                if executed<len(planned) or passed!=executed or failed!=0: local.append(f'{klass.upper()}_FIXTURE_NOT_ALL_PASS')
                if set(reported)!=set(planned): local.append(f'{klass.upper()}_PLANNED_FIXTURE_SET_MISMATCH')
                if not fr.get('evidence_refs'): local.append(f'{klass.upper()}_EVIDENCE_REFS_MISSING')
            if not r.get('positive_oracle_refs'): local.append('POSITIVE_ORACLE_REFS_MISSING')
            policy_digests=r.get('policy_authority_digests') or []
            if not policy_digests or any(not valid_sha(x) for x in policy_digests): local.append('POLICY_AUTHORITY_DIGESTS_MISSING_OR_INVALID')
            if dd=='DD-042' and minimum_sha not in policy_digests: local.append('DD042_MINIMUM_SET_AUTHORITY_DIGEST_MISSING')
            try:
                qa=parse_dt(r['qualified_at']); ex=parse_dt(r['expires_at'])
                if ex<=qa or ex<=now: local.append('QUALIFICATION_EXPIRED_OR_INTERVAL_INVALID')
            except Exception: local.append('QUALIFICATION_TIMESTAMPS_INVALID')
        if local:
            invalid+=1; errors.extend(f'{dd}:{x}' for x in local); derived_rows.append({'dd_id':dd,'qualification_state':'QUALIFICATION_HOLD','qualification_receipt_ref':str(p),'blocking_reasons':sorted(set(local))})
        else:
            qualified+=1; derived_rows.append({'dd_id':dd,'qualification_state':'QUALIFIED_NONFINAL','qualification_receipt_ref':str(p),'qualification_receipt_digest':r['receipt_digest'],'evaluator_id':r['evaluator_id'],'evaluator_version':r['evaluator_version'],'source_tree_sha':r['source_tree_sha'],'fixture_plan_sha256':r['fixture_plan_sha256'],'blocking_reasons':[]})
    if qualified!=42: errors.append(f'SUCCESSOR_ALL_QUALIFIED_GATE_REQUIRES_42_OF_42:CURRENT={qualified}/42')
    derived={'contract':'ONSURE_DD_SEMANTIC_EVALUATOR_DERIVED_STATUS_V3','dd_count':42,'qualification_fixture_case_count':173,'dd042_minimum_adversarial_fixture_count':6,'qualified_nonfinal_count':qualified,'invalid_receipt_count':invalid,'source_tree_sha':qualified_tree,'rows':derived_rows,'github_actions_authority':False,'final_claim_allowed':False}
    DERIVED.parent.mkdir(parents=True,exist_ok=True); DERIVED.write_text(json.dumps(derived,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    out={'contract':'ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_VALIDATION_V7','dd_count':42,'qualification_fixture_case_count':173,'dd042_minimum_adversarial_fixture_count':6,'qualified_nonfinal_count':qualified,'invalid_receipt_count':invalid,'fresh_successor_subject_required':True,'fixture_authority_population_sha256':bundle.get('fixture_authority_population_sha256') if bundle else None,'blocking_reasons':sorted(set(errors)),'derived_status':str(DERIVED),'verdict':'PASS_NONFINAL' if not errors else 'HOLD','github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
