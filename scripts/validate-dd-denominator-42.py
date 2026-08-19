#!/usr/bin/env python3
from __future__ import annotations
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'contracts/dd-semantic-evaluator-registry.candidate.v1.json'
EXT=ROOT/'contracts/dd-041-042-design-gap-extension.candidate.v1.json'
FIX_EXT=ROOT/'contracts/dd-semantic-evaluator-qualification-fixture-plan.extension-041-042.v1.json'

def main()->int:
    base=json.loads(BASE.read_text(encoding='utf-8'))
    ext=json.loads(EXT.read_text(encoding='utf-8'))
    fx=json.loads(FIX_EXT.read_text(encoding='utf-8'))
    reasons=[]
    base_rows=base.get('rows',[]); ext_rows=ext.get('rows',[])
    base_ids=[r.get('dd') for r in base_rows]; ext_ids=[r.get('dd') for r in ext_rows]
    expected=[f'DD-{i:03d}' for i in range(1,43)]
    materialized=base_ids+ext_ids
    if len(base_rows)!=40: reasons.append('BASE_DD_DENOMINATOR_NOT_40')
    if ext_ids!=['DD-041','DD-042']: reasons.append('DD_041_042_EXTENSION_MISSING_OR_MISORDERED')
    if materialized!=expected: reasons.append('DESIGN_DD_DENOMINATOR_NOT_EXACT_42')
    summary=ext.get('summary',{})
    if summary.get('new_dd_count')!=42: reasons.append('EXTENSION_SUMMARY_NOT_42')
    if summary.get('evaluator_materialized_count')!=2: reasons.append('DD_041_042_EVALUATORS_NOT_MATERIALIZED')
    if summary.get('runtime_routed_count')!=2: reasons.append('DD_041_042_RUNTIME_NOT_ROUTED')
    if summary.get('qualification_fixture_plan_materialized_count')!=2: reasons.append('DD_041_042_QUALIFICATION_FIXTURES_NOT_MATERIALIZED')
    if summary.get('successor_total_fixture_case_count')!=168: reasons.append('SUCCESSOR_FIXTURE_DENOMINATOR_NOT_168')
    fx_rows=fx.get('rows',[])
    if [r.get('dd_id') for r in fx_rows]!=['DD-041','DD-042']: reasons.append('EXTENSION_FIXTURE_DD_DENOMINATOR_NOT_2')
    fixture_ids=[]
    for row in fx_rows:
        cases=row.get('cases') or {}
        if set(cases)!={'positive','negative','recovery','adversarial'}: reasons.append(f"{row.get('dd_id')}:FIXTURE_CLASSES_NOT_EXACT_FOUR")
        fixture_ids.extend((cases.get(k) or {}).get('fixture_id') for k in ('positive','negative','recovery','adversarial'))
    if len(fixture_ids)!=8 or len(set(fixture_ids))!=8 or any(not x for x in fixture_ids): reasons.append('EXTENSION_FIXTURE_DENOMINATOR_NOT_EXACT_8_UNIQUE')
    if summary.get('independently_qualified_count')!=2: reasons.append('DD_041_042_NOT_INDEPENDENTLY_QUALIFIED')
    receipt={
      'contract':'ONSURE_DD_DENOMINATOR_42_GUARD_V2',
      'required_dd_count':42,
      'materialized_design_dd_count':len(materialized),
      'required_fixture_case_count':168,
      'extension_fixture_case_count':len(fixture_ids),
      'blocking_reasons':sorted(set(reasons)),
      'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL',
      'final_claim_allowed':False
    }
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True))
    return 0 if not reasons else 42
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e:
        print(f'ONSURE_DD_DENOMINATOR_42_FAIL {e}',file=sys.stderr); raise SystemExit(1)
