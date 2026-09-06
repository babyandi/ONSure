#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE_TRACE=ROOT/'contracts/dd-001-040-granular-vertical-trace.candidate.v1.json'
EXT=ROOT/'contracts/dd-041-042-design-gap-extension.candidate.v1.json'
REL=ROOT/'contracts/post-final-target-dd-041-042-to-fr-fin-relation.v1.json'
QUAL=ROOT/'.onsure/dd-independent-qualification/validated-status-successor.json'
RUNTIME=ROOT/'.onsure/dd-runtime-successor/runtime-42-validation.json'

def load(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def canon(d):
    x=dict(d);x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    reasons=[];base=load(BASE_TRACE);ext=load(EXT);rel=load(REL)
    base_rows=base.get('rows',[]);base_ids={r.get('dd') for r in base_rows};expected40={f'DD-{i:03d}' for i in range(1,41)}
    if len(base_rows)!=40 or base_ids!=expected40:reasons.append('BASE_GRANULAR_TRACE_NOT_EXACT_40')
    if any(not r.get('parents') or not r.get('objects') for r in base_rows):reasons.append('BASE_GRANULAR_TRACE_PARENT_OR_OBJECT_GAP')
    ext_rows={r.get('dd'):r for r in ext.get('rows',[]) if r.get('dd')};rel_rows={r.get('dd'):r for r in rel.get('rows',[]) if r.get('dd')}
    if set(ext_rows)!={'DD-041','DD-042'}:reasons.append('SUCCESSOR_EXTENSION_TRACE_NOT_EXACT_2')
    if set(rel_rows)!={'DD-041','DD-042'}:reasons.append('SUCCESSOR_PARENT_RELATION_NOT_EXACT_2')
    for dd,row in ext_rows.items():
        for field in ('requirement','evaluator_id','operation','required_inputs','safe_floor','positive_oracle'):
            if not row.get(field):reasons.append(f'{dd}:SUCCESSOR_TRACE_FIELD_MISSING:{field}')
        if not row.get('acceptance_criteria'):reasons.append(f'{dd}:ACCEPTANCE_CRITERIA_MISSING')
        if not (rel_rows.get(dd) or {}).get('fr_fin'):reasons.append(f'{dd}:FR_FIN_PARENT_MISSING')
    den=subprocess.run([sys.executable,'scripts/validate-dd-denominator-42.py'],cwd=ROOT,capture_output=True,text=True,check=False)
    if den.returncode:reasons.append('SUCCESSOR_STATIC_DENOMINATOR_NOT_PASS')
    manual_ref=os.environ.get('ONSURE_DD_MANUAL_VERIFICATION_RECEIPT','').strip();manual_ok=False;claims={}
    if not manual_ref:reasons.append('SUCCESSOR_MANUAL_RECEIPT_NOT_SUPPLIED')
    else:
        mp=Path(manual_ref);mp=mp if mp.is_absolute() else ROOT/mp
        if not mp.is_file():reasons.append('SUCCESSOR_MANUAL_RECEIPT_MISSING')
        else:
            m=load(mp);claims=m.get('claims') or {};manual_ok=(m.get('contract')=='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V7' and m.get('receipt_digest')==canon(m) and m.get('source_commit_sha')==git('rev-parse','HEAD') and m.get('source_tree_sha')==git('rev-parse','HEAD^{tree}') and m.get('verdict')=='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY')
            if not manual_ok:reasons.append('SUCCESSOR_MANUAL_RECEIPT_INVALID_OR_STALE')
            if claims.get('dd_authorized_route_execution_mechanics_count')!=42:reasons.append('SUCCESSOR_ROUTE_MECHANICS_NOT_42')
            if claims.get('dd_schema_validator_execution_mechanics_count')!=42:reasons.append('SUCCESSOR_SCHEMA_MECHANICS_NOT_42')
            if claims.get('qualification_fixture_mechanics_executed_count')!=173:reasons.append('SUCCESSOR_FIXTURE_MECHANICS_NOT_173')
            if claims.get('dd042_minimum_adversarial_fixture_mechanics_count')!=6:reasons.append('DD042_MINIMUM_SET_MECHANICS_NOT_6')
    qualified=0
    if not QUAL.is_file():reasons.append('SUCCESSOR_DERIVED_QUALIFICATION_STATUS_MISSING')
    else:
        q=load(QUAL);qualified=int(q.get('qualified_nonfinal_count',0))
        if q.get('dd_count')!=42 or qualified!=42:reasons.append('SUCCESSOR_INDEPENDENT_QUALIFICATION_NOT_42_OF_42')
    runtime=0
    if not RUNTIME.is_file():reasons.append('SUCCESSOR_RUNTIME_VALIDATION_MISSING')
    else:
        r=load(RUNTIME);runtime=int(r.get('pass_nonfinal_runtime_count',0))
        if r.get('decision')!='PASS_NONFINAL' or runtime!=42:reasons.append('SUCCESSOR_TARGET_RUNTIME_NOT_42_OF_42')
    out={'contract':'ONSURE_DD_GRANULAR_VERTICAL_TRACE_VALIDATION_V8_SUCCESSOR','dd_count':42,'base_design_trace_count':len(base_rows),'successor_extension_trace_count':len(ext_rows),'manual_verification_receipt_valid':manual_ok,'authorized_route_execution_mechanics_count':claims.get('dd_authorized_route_execution_mechanics_count',0),'schema_validator_execution_mechanics_count':claims.get('dd_schema_validator_execution_mechanics_count',0),'qualification_fixture_mechanics_executed_count':claims.get('qualification_fixture_mechanics_executed_count',0),'semantic_evaluator_independently_qualified_count':qualified,'target_semantic_runtime_evidence_count':runtime,'blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True));return 0 if not reasons else 32
if __name__=='__main__':raise SystemExit(main())
