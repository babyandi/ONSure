#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EXPECTED_EXT={'DD-041','DD-042'}
FIXTURE_CLASSES={'positive','negative','recovery','adversarial'}
BUNDLE=ROOT/'.onsure/dd-independent-qualification/frozen-bundle-successor/bundle-manifest.json'
FIX_EXT=ROOT/'contracts/dd-semantic-evaluator-qualification-fixture-plan.extension-041-042.v1.json'
RECEIPTS=Path(os.environ.get('ONSURE_DD_QUALIFICATION_RECEIPTS_DIR','receipts/dd-semantic-evaluator-qualification'))
RECEIPTS=RECEIPTS if RECEIPTS.is_absolute() else ROOT/RECEIPTS

def load(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def digest_payload(d):
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def parse_dt(v):
    if v.endswith('Z'): v=v[:-1]+'+00:00'
    return datetime.fromisoformat(v)
def valid_sha(v): return isinstance(v,str) and len(v)==64 and all(c in '0123456789abcdef' for c in v)

def main()->int:
    errors=[]
    base=subprocess.run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications.py','--receipts-dir',str(RECEIPTS),'--require-all-qualified'],cwd=ROOT,capture_output=True,text=True)
    if base.returncode!=0: errors.append('BASE_40_QUALIFICATION_NOT_VALID')
    if not BUNDLE.is_file():
        errors.append('SUCCESSOR_QUALIFICATION_BUNDLE_V3_MISSING'); bundle={}
    else: bundle=load(BUNDLE)
    if bundle and bundle.get('contract')!='ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V3': errors.append('SUCCESSOR_BUNDLE_CONTRACT_NOT_V3')
    if bundle and bundle.get('dd_denominator')!=42: errors.append('SUCCESSOR_BUNDLE_DD_DENOMINATOR_NOT_42')
    if bundle and bundle.get('qualification_fixture_denominator')!=168: errors.append('SUCCESSOR_BUNDLE_FIXTURE_DENOMINATOR_NOT_168')
    plan=load(FIX_EXT); rows=plan.get('rows',[]); by_dd={r.get('dd_id'):r for r in rows}
    if set(by_dd)!=EXPECTED_EXT or len(rows)!=2: errors.append('EXTENSION_FIXTURE_PLAN_NOT_EXACT_2_DD')
    fixture_ids=[]
    for dd,row in by_dd.items():
        cases=row.get('cases') or {}
        if set(cases)!=FIXTURE_CLASSES: errors.append(f'{dd}:FIXTURE_CLASSES_NOT_EXACT_FOUR')
        for k in FIXTURE_CLASSES:
            fid=(cases.get(k) or {}).get('fixture_id'); fixture_ids.append(fid)
    if len(fixture_ids)!=8 or len(set(fixture_ids))!=8 or any(not x for x in fixture_ids): errors.append('EXTENSION_FIXTURE_DENOMINATOR_NOT_8_UNIQUE')
    qualified=0; now=datetime.now(timezone.utc)
    for dd in sorted(EXPECTED_EXT):
        p=RECEIPTS/f'{dd}.json'; local=[]
        if not p.is_file(): local.append('QUALIFICATION_RECEIPT_MISSING'); r={}
        else:
            try:r=load(p)
            except Exception as e: local.append('RECEIPT_UNREADABLE:'+str(e)); r={}
        if r:
            if r.get('contract')!='ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_V1': local.append('WRONG_RECEIPT_CONTRACT')
            if r.get('dd_id')!=dd: local.append('RECEIPT_DD_ID_MISMATCH')
            if r.get('decision')!='QUALIFIED_NONFINAL': local.append('RECEIPT_DECISION_NOT_QUALIFIED')
            if r.get('final_claim_allowed') is not False: local.append('FINAL_CLAIM_NOT_FALSE')
            if r.get('receipt_digest')!=digest_payload(r): local.append('RECEIPT_DIGEST_MISMATCH')
            for f in ('evaluator_id','evaluator_version','qualification_principal','qualification_process_lineage'):
                if not r.get(f): local.append(f.upper()+'_MISSING')
            if not valid_sha(r.get('evaluator_artifact_sha256')): local.append('EVALUATOR_ARTIFACT_SHA_INVALID')
            if bundle:
                if r.get('source_tree_sha')!=bundle.get('source_tree_sha'): local.append('SOURCE_TREE_NOT_BOUND_TO_SUCCESSOR_BUNDLE')
                if r.get('evaluator_artifact_sha256')!=bundle.get('extension_evaluator_artifact_sha256'): local.append('EXTENSION_EVALUATOR_ARTIFACT_NOT_BOUND')
            att=r.get('independence_attestation') or {}
            for key in ('independent_from_evaluator_authoring','independent_from_target_claim_author','common_control_disclosed'):
                if att.get(key) is not True: local.append('INDEPENDENCE_'+key.upper()+'_NOT_TRUE')
            cases=(by_dd.get(dd) or {}).get('cases') or {}
            for klass in ('positive','negative','recovery','adversarial'):
                fr=(r.get('fixture_results') or {}).get(klass) or {}
                executed=fr.get('executed_count',0); passed=fr.get('passed_count',0); failed=fr.get('failed_count',0)
                if executed<1 or passed!=executed or failed!=0: local.append(f'{klass.upper()}_FIXTURE_NOT_ALL_PASS')
                if (cases.get(klass) or {}).get('fixture_id') not in (fr.get('fixture_ids') or []): local.append(f'{klass.upper()}_PLANNED_FIXTURE_ID_MISSING')
                if not fr.get('evidence_refs'): local.append(f'{klass.upper()}_EVIDENCE_REFS_MISSING')
            if not r.get('positive_oracle_refs'): local.append('POSITIVE_ORACLE_REFS_MISSING')
            if not r.get('policy_authority_digests'): local.append('POLICY_AUTHORITY_DIGESTS_MISSING')
            try:
                qa=parse_dt(r['qualified_at']); ex=parse_dt(r['expires_at'])
                if ex<=qa or ex<=now: local.append('QUALIFICATION_EXPIRED_OR_INTERVAL_INVALID')
            except Exception: local.append('QUALIFICATION_TIMESTAMPS_INVALID')
        if local: errors.extend(f'{dd}:{x}' for x in local)
        else: qualified+=1
    if qualified!=2: errors.append(f'EXTENSION_ALL_QUALIFIED_GATE_REQUIRES_2_OF_2:CURRENT={qualified}/2')
    out={'contract':'ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_VALIDATION_V5','base_qualified_required':40,'extension_qualified_required':2,'dd_count':42,'qualification_fixture_case_count':168,'extension_qualified_nonfinal_count':qualified,'blocking_reasons':sorted(set(errors)),'verdict':'PASS_NONFINAL' if not errors else 'HOLD','github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)); return 0 if not errors else 2
if __name__=='__main__': raise SystemExit(main())
