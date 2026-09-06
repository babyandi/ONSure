#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,subprocess,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TRACE=ROOT/'contracts/dd-001-040-granular-vertical-trace.candidate.v1.json'
STATIC_STATUS=ROOT/'status/dd-machine-runtime-implementation.v1.json'
QUAL_DERIVED=ROOT/'.onsure/dd-independent-qualification/validated-status.json'
RUNTIME_STATUS=ROOT/'contracts/dd-semantic-runtime-evidence-status.candidate.v1.json'

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def canonical_digest(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    reasons=[]; trace=load(TRACE); rows=trace.get('rows',[]); dd={r.get('dd') for r in rows}; expected={f'DD-{i:03d}' for i in range(1,41)}
    if len(rows)!=40 or dd!=expected: reasons.append('DD_DENOMINATOR_NOT_40_UNIQUE')
    if any(not r.get('parents') for r in rows): reasons.append('PARENT_MAPPING_GAP')
    if any(not r.get('objects') for r in rows): reasons.append('CANONICAL_OBJECT_GAP')

    static=subprocess.run([sys.executable,'scripts/validate-dd-machine-definitions.py'],cwd=ROOT,capture_output=True,text=True,check=False); static_payload={}
    try: static_payload=json.loads(static.stdout.strip().splitlines()[-1]) if static.stdout.strip() else {}
    except Exception: pass
    if static.returncode: reasons.append('DD_MACHINE_DEFINITION_NOT_PASS')
    st=load(STATIC_STATUS) if STATIC_STATUS.is_file() else {}; counts=st.get('counts',{})
    code_routes=int(counts.get('dd_code_route_materialized',0)); schema_code=int(counts.get('dd_generic_schema_validator_code_materialized',0)); concrete_eval=int(counts.get('dd_concrete_semantic_evaluator_code_materialized_unverified',0)); fixture_denominator=int(counts.get('dd_qualification_fixture_case_denominator',0))
    if code_routes!=40: reasons.append(f'DD_CODE_ROUTE_MATERIALIZATION_GAP:{40-code_routes}')
    if schema_code!=40: reasons.append(f'DD_SCHEMA_VALIDATOR_CODE_MATERIALIZATION_GAP:{40-schema_code}')
    if concrete_eval!=40: reasons.append(f'DD_CONCRETE_EVALUATOR_CODE_MATERIALIZATION_GAP:{40-concrete_eval}')
    if fixture_denominator!=160: reasons.append(f'DD_QUALIFICATION_FIXTURE_DENOMINATOR_GAP:{160-fixture_denominator}')

    manual_ref=os.environ.get('ONSURE_DD_MANUAL_VERIFICATION_RECEIPT','').strip(); route_execution=schema_execution=fixture_execution=0; manual_valid=False
    if not manual_ref: reasons.append('DD_MANUAL_VERIFICATION_RECEIPT_NOT_SUPPLIED')
    else:
        mp=Path(manual_ref); mp=mp if mp.is_absolute() else ROOT/mp
        if not mp.is_file(): reasons.append('DD_MANUAL_VERIFICATION_RECEIPT_MISSING')
        else:
            try:
                m=load(mp); claims=m.get('claims') or {}; head=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}')
                manual_valid=(m.get('contract')=='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V5' and m.get('receipt_digest')==canonical_digest(m) and m.get('source_commit_sha')==head and m.get('source_tree_sha')==tree and m.get('verdict')=='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY' and claims.get('compile_and_targeted_junit_established') is True)
                if not manual_valid: reasons.append('DD_MANUAL_VERIFICATION_RECEIPT_INVALID_OR_STALE')
                else:
                    route_execution=int(claims.get('dd_authorized_route_execution_mechanics_count',0)); schema_execution=int(claims.get('dd_schema_validator_execution_mechanics_count',0)); fixture_execution=int(claims.get('qualification_fixture_mechanics_executed_count',0))
            except Exception: reasons.append('DD_MANUAL_VERIFICATION_RECEIPT_UNREADABLE')
    if route_execution!=40: reasons.append(f'DD_AUTHORIZED_ROUTE_EXECUTION_MECHANICS_GAP:{40-route_execution}')
    if schema_execution!=40: reasons.append(f'DD_SCHEMA_VALIDATOR_EXECUTION_MECHANICS_GAP:{40-schema_execution}')
    if fixture_execution!=160: reasons.append(f'DD_QUALIFICATION_FIXTURE_MECHANICS_EXECUTION_GAP:{160-fixture_execution}')

    semantic=0
    if not QUAL_DERIVED.is_file(): reasons.append('DD_DERIVED_QUALIFICATION_STATUS_MISSING')
    else:
        q=load(QUAL_DERIVED); semantic=int(q.get('qualified_nonfinal_count',0))
        if q.get('dd_count')!=40: reasons.append('DD_DERIVED_QUALIFICATION_DENOMINATOR_INVALID')
    if semantic!=40: reasons.append(f'DD_SEMANTIC_EVALUATOR_INDEPENDENT_QUALIFICATION_GAP:{40-semantic}')

    runtime_status=load(RUNTIME_STATUS) if RUNTIME_STATUS.is_file() else {}; runtime_summary=runtime_status.get('summary',{}); semantic_runtime=int(runtime_summary.get('pass_nonfinal_runtime_count',0))
    if semantic_runtime!=40: reasons.append(f'DD_TARGET_SEMANTIC_RUNTIME_EVIDENCE_GAP:{40-semantic_runtime}')

    out={'contract':'ONSURE_DD_GRANULAR_VERTICAL_TRACE_VALIDATION_V7','dd_count':len(rows),'design_layer_complete_count':sum(1 for r in rows if r.get('parents') and r.get('objects')),'machine_definition_static_validator_rc':static.returncode,'machine_definition_static_decision':static_payload.get('decision','UNAVAILABLE'),'machine_definition_gap_instances':len(static_payload.get('blocking_reasons',[])) if static_payload else None,'code_route_materialized_count':code_routes,'schema_validator_code_materialized_count':schema_code,'concrete_evaluator_code_materialized_unverified_count':concrete_eval,'qualification_fixture_case_denominator':fixture_denominator,'manual_verification_receipt_valid':manual_valid,'authorized_route_execution_mechanics_count':route_execution,'schema_validator_execution_mechanics_count':schema_execution,'qualification_fixture_mechanics_executed_count':fixture_execution,'semantic_evaluator_independently_qualified_count':semantic,'target_semantic_runtime_evidence_count':semantic_runtime,'github_actions_authority':False,'execution_method_required':'LOCAL_OR_AUTOPILOT_EXPLICIT_RUN','blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 32
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError,subprocess.CalledProcessError) as e: print(f'ONSURE_DD_TRACE_FAIL {e}',file=sys.stderr); raise SystemExit(1)
