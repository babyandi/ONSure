#!/usr/bin/env python3
from __future__ import annotations
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'contracts/dd-semantic-evaluator-registry.candidate.v1.json'
EXT=ROOT/'contracts/dd-041-042-design-gap-extension.candidate.v1.json'
FIX_EXT=ROOT/'contracts/dd-semantic-evaluator-qualification-fixture-plan.extension-041-042.v1.json'
DD042_MIN=ROOT/'contracts/dd-042-adversarial-minimum-set.v1.json'
FIXTURE_CLASSES={'positive','negative','recovery','adversarial'}

def planned_ids(case):
    ids=case.get('fixture_ids')
    if isinstance(ids,list): return [x for x in ids if isinstance(x,str) and x]
    fid=case.get('fixture_id')
    return [fid] if isinstance(fid,str) and fid else []

def main()->int:
    base=json.loads(BASE.read_text(encoding='utf-8'))
    ext=json.loads(EXT.read_text(encoding='utf-8'))
    fx=json.loads(FIX_EXT.read_text(encoding='utf-8'))
    minimum=json.loads(DD042_MIN.read_text(encoding='utf-8'))
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
    if summary.get('qualification_fixture_case_count')!=13: reasons.append('EXTENSION_FIXTURE_DENOMINATOR_NOT_13')
    if summary.get('successor_total_fixture_case_count')!=173: reasons.append('SUCCESSOR_FIXTURE_DENOMINATOR_NOT_173')
    fx_rows=fx.get('rows',[])
    if [r.get('dd_id') for r in fx_rows]!=['DD-041','DD-042']: reasons.append('EXTENSION_FIXTURE_DD_DENOMINATOR_NOT_2')
    fixture_ids=[]; by_dd={r.get('dd_id'):r for r in fx_rows}
    for row in fx_rows:
        dd=row.get('dd_id'); cases=row.get('cases') or {}
        if set(cases)!=FIXTURE_CLASSES: reasons.append(f'{dd}:FIXTURE_CLASSES_NOT_EXACT_FOUR')
        for klass in FIXTURE_CLASSES:
            ids=planned_ids(cases.get(klass) or {})
            expected_count=6 if dd=='DD-042' and klass=='adversarial' else 1
            if len(ids)!=expected_count: reasons.append(f'{dd}:{klass}:PLANNED_FIXTURE_COUNT_NOT_{expected_count}')
            fixture_ids.extend(ids)
    if len(fixture_ids)!=13 or len(set(fixture_ids))!=13 or any(not x for x in fixture_ids): reasons.append('EXTENSION_FIXTURE_DENOMINATOR_NOT_EXACT_13_UNIQUE')
    mandatory={r.get('fixture_id') for r in minimum.get('mandatory_fixtures',[]) if r.get('fixture_id')}
    dd042_adv=set(planned_ids(((by_dd.get('DD-042') or {}).get('cases') or {}).get('adversarial') or {}))
    if minimum.get('minimum_adversarial_fixture_count')!=6 or len(mandatory)!=6: reasons.append('DD042_MINIMUM_SET_NOT_EXACT_6')
    if dd042_adv!=mandatory: reasons.append('DD042_ADVERSARIAL_PLAN_NOT_BOUND_TO_MINIMUM_SET')
    # Independent qualification is receipt-derived execution authority and must never be
    # inferred from or blocked by this tracked static design summary. The successor
    # qualification validator separately requires exact 42/42 current receipts.
    static_qualified_disclosure=summary.get('independently_qualified_count',0)
    receipt={
      'contract':'ONSURE_DD_DENOMINATOR_42_GUARD_V4',
      'required_dd_count':42,
      'materialized_design_dd_count':len(materialized),
      'required_fixture_case_count':173,
      'extension_fixture_case_count':len(fixture_ids),
      'dd042_minimum_adversarial_fixture_count':len(mandatory),
      'tracked_static_independently_qualified_count_disclosure_only':static_qualified_disclosure,
      'independent_qualification_authority':'scripts/validate-dd-semantic-evaluator-qualifications-successor.py',
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
