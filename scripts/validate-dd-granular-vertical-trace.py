#!/usr/bin/env python3
from __future__ import annotations
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
TRACE=ROOT/'contracts/dd-001-040-granular-vertical-trace.candidate.v1.json'
MACHINE=ROOT/'contracts/dd-machine-operation-schema-fixture-registry.candidate.v1.json'
OPS=ROOT/'contracts/workflow-operation-registry.v1.json'
IMPLEMENTATION=ROOT/'status/dd-machine-runtime-implementation.v1.json'

def main()->int:
    d=json.loads(TRACE.read_text(encoding='utf-8')); rows=d['rows']; reasons=[]
    m=json.loads(MACHINE.read_text(encoding='utf-8')); mrows=m['rows']
    ops=set(json.loads(OPS.read_text(encoding='utf-8'))['operations'])
    dd={r['dd'] for r in rows}; mdd={r['dd'] for r in mrows}
    if len(rows)!=40 or len(dd)!=40: reasons.append('DD_DENOMINATOR_NOT_40_UNIQUE')
    if any(not r.get('parents') for r in rows): reasons.append('PARENT_MAPPING_GAP')
    if any(not r.get('objects') for r in rows): reasons.append('CANONICAL_OBJECT_GAP')
    definition_open=[]
    if dd!=mdd: definition_open.append('DD_MACHINE_REGISTRY_DENOMINATOR_MISMATCH')
    for r in mrows:
        if not r.get('operation') or r['operation'] not in ops: definition_open.append(f"{r['dd']}:operation")
        if not r.get('request_schema') or not r.get('result_schema'): definition_open.append(f"{r['dd']}:schema")
        if not r.get('fixture'): definition_open.append(f"{r['dd']}:fixture")
    if definition_open: reasons.append(f'MACHINE_DEFINITION_GAPS:{len(definition_open)}')
    impl={}
    if IMPLEMENTATION.exists():
        x=json.loads(IMPLEMENTATION.read_text(encoding='utf-8')); impl={r['dd']:r for r in x.get('rows',[])}
    implementation_open=[]; test_execution_open=[]; runtime_open=[]
    for x in sorted(dd):
        s=impl.get(x,{})
        if s.get('operation_dispatcher')!='IMPLEMENTED' or s.get('schema_validation')!='IMPLEMENTED': implementation_open.append(x)
        if s.get('fixture_execution')!='PASS': test_execution_open.append(x)
        if s.get('runtime_evidence')!='EVIDENCED': runtime_open.append(x)
    if implementation_open: reasons.append(f'RUNTIME_IMPLEMENTATION_GAP_ROWS:{len(implementation_open)}')
    if test_execution_open: reasons.append(f'EXECUTABLE_TEST_NOT_RUN_ROWS:{len(test_execution_open)}')
    if runtime_open: reasons.append(f'RUNTIME_EVIDENCE_GAP_ROWS:{len(runtime_open)}')
    out={
      'contract':'ONSURE_DD_GRANULAR_VERTICAL_TRACE_VALIDATION_V3',
      'dd_count':len(rows),
      'machine_definition_gap_instances':len(definition_open),
      'runtime_implementation_gap_rows':len(implementation_open),
      'executable_test_not_run_rows':len(test_execution_open),
      'runtime_evidence_gap_rows':len(runtime_open),
      'definition_open':definition_open,
      'runtime_implementation_open_dd':implementation_open,
      'test_execution_open_dd':test_execution_open,
      'runtime_evidence_open_dd':runtime_open,
      'blocking_reasons':reasons,
      'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL',
      'final_claim_allowed':False
    }
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 32
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_DD_TRACE_FAIL {e}',file=sys.stderr); raise SystemExit(1)
