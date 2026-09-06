#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MINIMUM=ROOT/'contracts/dd-042-adversarial-minimum-set.v1.json'
PLAN=ROOT/'contracts/dd-semantic-evaluator-qualification-fixture-plan.extension-041-042.v1.json'

def planned_ids(case):
    ids=case.get('fixture_ids')
    if isinstance(ids,list): return [x for x in ids if isinstance(x,str) and x]
    fid=case.get('fixture_id')
    return [fid] if isinstance(fid,str) and fid else []

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--omit-fixture-id',default=''); args=ap.parse_args()
    minimum=json.loads(MINIMUM.read_text(encoding='utf-8')); plan=json.loads(PLAN.read_text(encoding='utf-8')); reasons=[]
    mandatory={r.get('fixture_id') for r in minimum.get('mandatory_fixtures',[]) if r.get('fixture_id')}
    rows={r.get('dd_id'):r for r in plan.get('rows',[])}
    planned=set(planned_ids((((rows.get('DD-042') or {}).get('cases') or {}).get('adversarial') or {})))
    if args.omit_fixture_id:
        if args.omit_fixture_id not in planned: reasons.append('MUTATION_FIXTURE_ID_NOT_IN_PLAN')
        planned.discard(args.omit_fixture_id)
    if minimum.get('contract')!='ONSURE_DD_042_ADVERSARIAL_MINIMUM_SET_V1': reasons.append('MINIMUM_SET_CONTRACT_MISMATCH')
    if minimum.get('minimum_adversarial_fixture_count')!=6 or len(mandatory)!=6: reasons.append('MANDATORY_FIXTURE_DENOMINATOR_NOT_6')
    if planned!=mandatory: reasons.append('PLANNED_ADVERSARIAL_SET_NOT_EXACT_AUTHORITY_MINIMUM')
    if len(planned)!=6: reasons.append('PLANNED_ADVERSARIAL_FIXTURE_COUNT_NOT_6')
    out={'contract':'ONSURE_DD_042_ADVERSARIAL_MINIMUM_SET_VALIDATION_V1','mandatory_fixture_count':len(mandatory),'observed_planned_fixture_count':len(planned),'mutation_omit_fixture_id':args.omit_fixture_id or None,'blocking_reasons':sorted(set(reasons)),'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True))
    return 0 if not reasons else 42
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e:
        print(f'ONSURE_DD042_MINIMUM_SET_FAIL {e}',file=sys.stderr); raise SystemExit(1)
