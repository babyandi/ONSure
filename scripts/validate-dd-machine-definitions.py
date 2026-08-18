#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
REG=ROOT/'contracts/dd-machine-operation-schema-fixture-registry.candidate.v1.json'
FIX=ROOT/'contracts/dd-machine-fixture-catalog.candidate.v1.json'
BIND=ROOT/'contracts/dd-machine-schema-binding.candidate.v1.json'
REQ=ROOT/'contracts/dd-assurance-request.candidate.v1.schema.json'
RES=ROOT/'contracts/dd-assurance-result.candidate.v1.schema.json'
OPS=ROOT/'contracts/workflow-operation-registry.v1.json'
JAVA=ROOT/'src/main/java/kr/co/oruda/onsure/platform/DdAssuranceOperationRuntime.java'
ENTRY_RE=re.compile(r'Map\.entry\("([a-z][a-z0-9.-]+)",\s*"(DD-\d{3})"\)')

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))

def main()->int:
    reasons=[]
    for p in (REG,FIX,BIND,REQ,RES,OPS,JAVA):
        if not p.is_file(): reasons.append(f'MISSING_FILE:{p.relative_to(ROOT)}')
    if reasons:
        print(json.dumps({'contract':'ONSURE_DD_MACHINE_DEFINITION_VALIDATION_V2','blocking_reasons':reasons,'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 36
    reg=load(REG); fixtures=load(FIX); binding=load(BIND); ops=set(load(OPS)['operations']); req=load(REQ); res=load(RES); java=JAVA.read_text(encoding='utf-8')
    rows=reg.get('rows',[]); frows=fixtures.get('rows',[])
    expected={f'DD-{i:03d}' for i in range(1,41)}
    dd=[r.get('dd') for r in rows]; fdd=[r.get('dd') for r in frows]
    if len(rows)!=40 or set(dd)!=expected or len(set(dd))!=40: reasons.append('REGISTRY_DD_DENOMINATOR_NOT_EXACT_40')
    if len(frows)!=40 or set(fdd)!=expected or len(set(fdd))!=40: reasons.append('FIXTURE_DD_DENOMINATOR_NOT_EXACT_40')
    registry_ops={r.get('operation') for r in rows}
    if len(registry_ops)!=40 or None in registry_ops: reasons.append('REGISTRY_OPERATION_DENOMINATOR_NOT_40_UNIQUE')
    missing_ops=sorted(registry_ops-ops)
    if missing_ops: reasons.append('OPERATIONS_MISSING_FROM_WORKFLOW_REGISTRY')

    req_file=binding.get('request',{}).get('schema_file'); res_file=binding.get('result',{}).get('schema_file')
    if req_file!=REQ.relative_to(ROOT).as_posix(): reasons.append('REQUEST_SCHEMA_BINDING_FILE_MISMATCH')
    if res_file!=RES.relative_to(ROOT).as_posix(): reasons.append('RESULT_SCHEMA_BINDING_FILE_MISMATCH')
    try:
        req_id_re=re.compile(binding['request']['registry_id_pattern']); res_id_re=re.compile(binding['result']['registry_id_pattern'])
    except (KeyError,re.error):
        reasons.append('SCHEMA_BINDING_PATTERN_INVALID'); req_id_re=res_id_re=re.compile(r'a^')

    fixture_by_dd={r['dd']:r for r in frows if r.get('dd')}
    for r in rows:
        ddid=r.get('dd','')
        if not req_id_re.fullmatch(str(r.get('request_schema',''))): reasons.append(f'REQUEST_SCHEMA_ID_MISMATCH:{ddid}')
        if not res_id_re.fullmatch(str(r.get('result_schema',''))): reasons.append(f'RESULT_SCHEMA_ID_MISMATCH:{ddid}')
        expected_prefix=ddid.lower().replace('-','')
        if not str(r.get('request_schema','')).startswith(expected_prefix+'.'): reasons.append(f'REQUEST_SCHEMA_DD_IDENTITY_MISMATCH:{ddid}')
        if not str(r.get('result_schema','')).startswith(expected_prefix+'.'): reasons.append(f'RESULT_SCHEMA_DD_IDENTITY_MISMATCH:{ddid}')
        f=fixture_by_dd.get(ddid)
        if not f or f.get('fixture')!=r.get('fixture'): reasons.append(f'FIXTURE_BINDING_MISMATCH:{ddid}')
        elif not f.get('expected_nonpositive') or not f.get('oracle') or not f.get('condition'): reasons.append(f'FIXTURE_ORACLE_INCOMPLETE:{ddid}')

    request_required=set(req.get('required',[])); result_required=set(res.get('required',[]))
    if not {'dd_id','evidence_refs'} <= request_required: reasons.append('GENERIC_REQUEST_SCHEMA_REQUIRED_FIELDS_GAP')
    if not {'dd_id','operation','decision','claim_strengthening_allowed','external_effect_performed','final_claim_allowed'} <= result_required: reasons.append('GENERIC_RESULT_SCHEMA_REQUIRED_FIELDS_GAP')
    if res.get('properties',{}).get('final_claim_allowed',{}).get('const') is not False: reasons.append('RESULT_SCHEMA_FINAL_CLAIM_NOT_FAIL_CLOSED')

    java_pairs=dict(ENTRY_RE.findall(java)); reg_pairs={r['operation']:r['dd'] for r in rows if r.get('operation') and r.get('dd')}
    missing_java=sorted(set(reg_pairs)-set(java_pairs)); extra_java=sorted(set(java_pairs)-set(reg_pairs)); mismatch_java=sorted(op for op in set(reg_pairs)&set(java_pairs) if reg_pairs[op]!=java_pairs[op])
    if missing_java: reasons.append('JAVA_ROUTE_MISSING_OPERATIONS')
    if extra_java: reasons.append('JAVA_ROUTE_EXTRA_OPERATIONS')
    if mismatch_java: reasons.append('JAVA_ROUTE_DD_MISMATCH')
    fail_closed_tokens=['"HOLD"','"SEMANTIC_EVALUATOR_NOT_QUALIFIED"','"claim_strengthening_allowed", false','"external_effect_performed", false','"final_claim_allowed", false']
    if any(t not in java for t in fail_closed_tokens): reasons.append('JAVA_FAIL_CLOSED_TOKENS_MISSING')

    out={'contract':'ONSURE_DD_MACHINE_DEFINITION_VALIDATION_V2','dd_count':len(rows),'fixture_count':len(frows),'workflow_operation_match_count':len(registry_ops & ops),'java_route_match_count':len(set(reg_pairs)&set(java_pairs))-len(mismatch_java),'schema_binding_model':binding.get('binding_model'),'generic_request_schema':REQ.relative_to(ROOT).as_posix(),'generic_result_schema':RES.relative_to(ROOT).as_posix(),'execution_evidence_established':False,'github_actions_authority':False,'blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 36
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e:
        print(f'ONSURE_DD_MACHINE_DEFINITION_FAIL {e}',file=sys.stderr); raise SystemExit(1)
