#!/usr/bin/env python3
from __future__ import annotations
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'contracts/dd-semantic-evaluator-registry.candidate.v1.json'
EXT=ROOT/'contracts/dd-041-042-design-gap-extension.candidate.v1.json'

def main()->int:
    base=json.loads(BASE.read_text(encoding='utf-8'))
    ext=json.loads(EXT.read_text(encoding='utf-8'))
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
    if summary.get('runtime_materialized_count')!=2: reasons.append('DD_041_042_RUNTIME_NOT_MATERIALIZED')
    if summary.get('qualification_fixture_plan_materialized_count')!=2: reasons.append('DD_041_042_QUALIFICATION_FIXTURES_NOT_MATERIALIZED')
    if summary.get('independently_qualified_count')!=2: reasons.append('DD_041_042_NOT_INDEPENDENTLY_QUALIFIED')
    receipt={
      'contract':'ONSURE_DD_DENOMINATOR_42_GUARD_V1',
      'required_dd_count':42,
      'materialized_design_dd_count':len(materialized),
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
