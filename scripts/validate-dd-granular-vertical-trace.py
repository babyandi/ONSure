#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TRACE=ROOT/'contracts/dd-001-040-granular-vertical-trace.candidate.v1.json'
STATUS=ROOT/'status/dd-machine-runtime-implementation.v1.json'


def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))


def main()->int:
    reasons=[]
    trace=load(TRACE); rows=trace['rows']; dd={r['dd'] for r in rows}
    if len(rows)!=40 or len(dd)!=40: reasons.append('DD_DENOMINATOR_NOT_40_UNIQUE')
    if any(not r.get('parents') for r in rows): reasons.append('PARENT_MAPPING_GAP')
    if any(not r.get('objects') for r in rows): reasons.append('CANONICAL_OBJECT_GAP')

    static=subprocess.run([sys.executable,'scripts/validate-dd-machine-definitions.py'],cwd=ROOT,capture_output=True,text=True,check=False)
    static_payload={}
    try: static_payload=json.loads(static.stdout.strip().splitlines()[-1]) if static.stdout.strip() else {}
    except (ValueError,IndexError): pass
    if static.returncode: reasons.append('DD_MACHINE_DEFINITION_NOT_PASS')

    status=load(STATUS) if STATUS.exists() else {}
    counts=status.get('counts',{})
    code_routes=int(counts.get('dd_code_route_materialized',0))
    authorized_route_execution=int(counts.get('dd_route_execution_evidenced_by_authorized_current_method',0))
    semantic=int(counts.get('dd_semantic_evaluator_qualified',0))
    schema_runtime=int(counts.get('dd_specific_schema_validator_implemented',0))
    fixture_execution=int(counts.get('dd_semantic_fixture_oracle_executed',0))

    # Code route presence is a materialization fact, not runtime execution evidence.
    if code_routes != 40: reasons.append(f'DD_CODE_ROUTE_MATERIALIZATION_GAP:{40-code_routes}')
    if schema_runtime != 40: reasons.append(f'DD_SPECIFIC_SCHEMA_RUNTIME_GAP:{40-schema_runtime}')
    if semantic != 40: reasons.append(f'DD_SEMANTIC_EVALUATOR_QUALIFICATION_GAP:{40-semantic}')
    if fixture_execution != 40: reasons.append(f'DD_SEMANTIC_FIXTURE_EXECUTION_GAP:{40-fixture_execution}')
    if authorized_route_execution != 40: reasons.append(f'DD_AUTHORIZED_ROUTE_EXECUTION_EVIDENCE_GAP:{40-authorized_route_execution}')

    # Positive semantic runtime evidence is intentionally distinct from routing evidence.
    semantic_runtime_evidence_count=0
    if semantic_runtime_evidence_count != 40:
        reasons.append(f'DD_SEMANTIC_RUNTIME_EVIDENCE_GAP:{40-semantic_runtime_evidence_count}')

    out={
      'contract':'ONSURE_DD_GRANULAR_VERTICAL_TRACE_VALIDATION_V4',
      'dd_count':len(rows),
      'design_layer_complete_count':sum(1 for r in rows if r.get('parents') and r.get('objects')),
      'machine_definition_static_validator_rc':static.returncode,
      'machine_definition_static_decision':static_payload.get('decision','UNAVAILABLE'),
      'machine_definition_gap_instances':len(static_payload.get('blocking_reasons',[])) if static_payload else None,
      'code_route_materialized_count':code_routes,
      'authorized_route_execution_evidence_count':authorized_route_execution,
      'dd_specific_schema_runtime_count':schema_runtime,
      'semantic_evaluator_qualified_count':semantic,
      'semantic_fixture_oracle_executed_count':fixture_execution,
      'semantic_runtime_evidence_count':semantic_runtime_evidence_count,
      'github_actions_authority':False,
      'execution_method_required':'LOCAL_OR_AUTOPILOT_EXPLICIT_RUN',
      'blocking_reasons':sorted(set(reasons)),
      'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL',
      'final_claim_allowed':False
    }
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 32

if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e:
        print(f'ONSURE_DD_TRACE_FAIL {e}',file=sys.stderr); raise SystemExit(1)
