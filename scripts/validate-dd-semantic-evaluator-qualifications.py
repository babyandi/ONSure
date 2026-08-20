#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,os,sys
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
EXPECTED={f"DD-{i:03d}" for i in range(1,41)}
QUALIFIED="QUALIFIED_NONFINAL"
FIXTURE_CLASSES={"positive","negative","recovery","adversarial"}
DERIVED=ROOT/'.onsure/dd-independent-qualification/validated-status.json'

def resolve(p:str)->Path:
    x=Path(p); return x if x.is_absolute() else ROOT/x

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def parse_dt(v:str):
    if v.endswith('Z'): v=v[:-1]+'+00:00'
    return datetime.fromisoformat(v)
def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def valid_hex(v,ln):
    s=str(v or ''); return len(s)==ln and all(c in '0123456789abcdef' for c in s)

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--registry',default='contracts/dd-semantic-evaluator-registry.candidate.v1.json')
    ap.add_argument('--status',default='contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json')
    ap.add_argument('--fixture-plan',default='contracts/dd-semantic-evaluator-qualification-fixture-plan.candidate.v1.json')
    ap.add_argument('--receipts-dir',default=os.environ.get('ONSURE_DD_QUALIFICATION_RECEIPTS_DIR','receipts/dd-semantic-evaluator-qualification'))
    ap.add_argument('--bundle-manifest',default='.onsure/dd-independent-qualification/frozen-bundle/bundle-manifest.json')
    ap.add_argument('--derived-status',default=str(DERIVED))
    ap.add_argument('--require-all-qualified',action='store_true')
    args=ap.parse_args()

    registry=load(resolve(args.registry)); disclosure=load(resolve(args.status)); fixture_plan=load(resolve(args.fixture_plan)); errors=[]
    reg_rows=registry.get('rows',[]); reg_dd={r.get('dd') for r in reg_rows}
    if reg_dd!=EXPECTED or len(reg_rows)!=40: errors.append('registry DD population must be exact 40')
    if registry.get('final_claim_allowed') is not False: errors.append('registry final_claim_allowed must be false')
    disclosure_rows=disclosure.get('rows',[])
    if len(disclosure_rows)!=40 or {r.get('dd_id') for r in disclosure_rows}!=EXPECTED: errors.append('code-status disclosure DD population must be exact 40')
    code_materialized=sum(1 for r in disclosure_rows if r.get('implementation_state')=='CODE_MATERIALIZED_UNVERIFIED')
    if code_materialized!=40: errors.append(f'concrete evaluator code materialization disclosure must be 40/40 current={code_materialized}')
    if disclosure.get('final_claim_allowed') is not False: errors.append('code-status disclosure final_claim_allowed must be false')

    fixture_rows=fixture_plan.get('rows',[]); fixture_dd={r.get('dd_id') for r in fixture_rows}; fixture_ids=[]; plan_by_dd={r.get('dd_id'):r for r in fixture_rows}
    if len(fixture_rows)!=40 or fixture_dd!=EXPECTED: errors.append('qualification fixture DD denominator must be exact 40')
    for row in fixture_rows:
        dd=row.get('dd_id'); cases=row.get('cases') or {}
        if set(cases)!=FIXTURE_CLASSES: errors.append(f'{dd}: qualification fixture classes must be exact four'); continue
        for klass in sorted(FIXTURE_CLASSES):
            case=cases.get(klass) or {}; fid=case.get('fixture_id')
            if not fid or not case.get('expected') or not case.get('purpose'): errors.append(f'{dd}:{klass}: fixture definition incomplete')
            else: fixture_ids.append(fid)
    if len(fixture_ids)!=160 or len(set(fixture_ids))!=160: errors.append('qualification fixture denominator must be 160 unique cases')

    bundle_path=resolve(args.bundle_manifest); bundle=None
    if bundle_path.is_file():
        try: bundle=load(bundle_path)
        except Exception as e: errors.append(f'qualification bundle unreadable:{e}')
    receipts_dir=resolve(args.receipts_dir); now=datetime.now(timezone.utc); derived_rows=[]; qualified=0; invalid_count=0
    for dd in sorted(EXPECTED):
        p=receipts_dir/f'{dd}.json'
        if not p.is_file():
            derived_rows.append({'dd_id':dd,'qualification_state':'IMPLEMENTED_UNQUALIFIED','qualification_receipt_ref':None,'blocking_reasons':['QUALIFICATION_RECEIPT_MISSING']})
            continue
        local_errors=[]
        try:r=load(p)
        except Exception as e:
            local_errors.append('RECEIPT_UNREADABLE:'+str(e)); r={}
        if r:
            if r.get('contract')!='ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_V1': local_errors.append('WRONG_RECEIPT_CONTRACT')
            if r.get('dd_id')!=dd: local_errors.append('RECEIPT_DD_ID_MISMATCH')
            if r.get('decision')!=QUALIFIED: local_errors.append('RECEIPT_DECISION_NOT_QUALIFIED')
            if r.get('final_claim_allowed') is not False: local_errors.append('FINAL_CLAIM_NOT_FALSE')
            if r.get('receipt_digest')!=digest_payload(r): local_errors.append('RECEIPT_DIGEST_MISMATCH')
            for f in ('evaluator_id','evaluator_version','qualification_principal','qualification_process_lineage'):
                if not r.get(f): local_errors.append(f.upper()+'_MISSING')
            if not valid_hex(r.get('evaluator_artifact_sha256'),64): local_errors.append('EVALUATOR_ARTIFACT_SHA_INVALID')
            if not valid_hex(r.get('obligation_registry_sha256'),64): local_errors.append('OBLIGATION_REGISTRY_SHA_INVALID')
            if bundle is None: local_errors.append('FROZEN_QUALIFICATION_BUNDLE_MISSING')
            else:
                if bundle.get('contract')!='ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V2': local_errors.append('BUNDLE_CONTRACT_NOT_V2')
                if r.get('source_tree_sha')!=bundle.get('source_tree_sha'): local_errors.append('SOURCE_TREE_NOT_BOUND_TO_BUNDLE')
                if r.get('evaluator_artifact_sha256')!=bundle.get('evaluator_artifact_sha256'): local_errors.append('EVALUATOR_ARTIFACT_NOT_BOUND_TO_BUNDLE')
                if r.get('obligation_registry_sha256')!=bundle.get('obligation_registry_sha256'): local_errors.append('OBLIGATION_REGISTRY_NOT_BOUND_TO_BUNDLE')
            att=r.get('independence_attestation') or {}
            for key in ('independent_from_evaluator_authoring','independent_from_target_claim_author','common_control_disclosed'):
                if att.get(key) is not True: local_errors.append('INDEPENDENCE_'+key.upper()+'_NOT_TRUE')
            plan_cases=(plan_by_dd.get(dd) or {}).get('cases') or {}
            for klass in ('positive','negative','recovery','adversarial'):
                fr=(r.get('fixture_results') or {}).get(klass) or {}; executed=fr.get('executed_count',0); passed=fr.get('passed_count',0); failed=fr.get('failed_count',0)
                if executed<1 or passed!=executed or failed!=0: local_errors.append(f'{klass.upper()}_FIXTURE_NOT_ALL_PASS')
                if not fr.get('evidence_refs'): local_errors.append(f'{klass.upper()}_EVIDENCE_REFS_MISSING')
                expected_id=(plan_cases.get(klass) or {}).get('fixture_id')
                if expected_id not in (fr.get('fixture_ids') or []): local_errors.append(f'{klass.upper()}_PLANNED_FIXTURE_ID_MISSING')
            if not r.get('positive_oracle_refs'): local_errors.append('POSITIVE_ORACLE_REFS_MISSING')
            if not r.get('policy_authority_digests'): local_errors.append('POLICY_AUTHORITY_DIGESTS_MISSING')
            try:
                qa=parse_dt(r['qualified_at']); ex=parse_dt(r['expires_at'])
                if ex<=qa or ex<=now: local_errors.append('QUALIFICATION_EXPIRED_OR_INTERVAL_INVALID')
            except Exception: local_errors.append('QUALIFICATION_TIMESTAMPS_INVALID')
        if local_errors:
            invalid_count+=1; errors.extend(f'{dd}:{x}' for x in local_errors)
            derived_rows.append({'dd_id':dd,'qualification_state':'QUALIFICATION_HOLD','qualification_receipt_ref':str(p),'blocking_reasons':sorted(set(local_errors))})
        else:
            qualified+=1
            derived_rows.append({'dd_id':dd,'qualification_state':QUALIFIED,'qualification_receipt_ref':str(p),'qualification_receipt_digest':r['receipt_digest'],'evaluator_id':r['evaluator_id'],'evaluator_version':r['evaluator_version'],'source_tree_sha':r['source_tree_sha'],'blocking_reasons':[]})

    if args.require_all_qualified and qualified!=40: errors.append(f'all-qualified gate requires 40/40 current={qualified}/40')
    derived={'contract':'ONSURE_DD_SEMANTIC_EVALUATOR_DERIVED_STATUS_V1','dd_count':40,'code_materialized_count':code_materialized,'qualified_nonfinal_count':qualified,'invalid_receipt_count':invalid_count,'receipts_dir':str(receipts_dir),'bundle_manifest':str(bundle_path),'rows':derived_rows,'github_actions_authority':False,'final_claim_allowed':False}
    outpath=resolve(args.derived_status); outpath.parent.mkdir(parents=True,exist_ok=True); outpath.write_text(json.dumps(derived,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    result={'contract':'ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_VALIDATION_V4','dd_count':40,'code_materialized_count':code_materialized,'qualification_fixture_case_count':len(fixture_ids),'qualified_nonfinal_count':qualified,'invalid_receipt_count':invalid_count,'frozen_bundle_bound':bundle is not None,'require_all_qualified':args.require_all_qualified,'derived_status':str(outpath),'errors':errors,'verdict':'PASS_NONFINAL' if not errors else 'HOLD','github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(result,ensure_ascii=False,indent=2)); return 0 if not errors else 2
if __name__=='__main__': sys.exit(main())
