#!/usr/bin/env python3
from __future__ import annotations
import json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
TRACE=ROOT/'contracts/dd-001-040-granular-vertical-trace.candidate.v1.json'
def effective(row:dict, defaults:dict, key:str):
    return row.get('layers',{}).get(key, defaults[key])
def main()->int:
    d=json.loads(TRACE.read_text(encoding='utf-8')); rows=d['rows']; defaults=d['default_row_layers']; reasons=[]
    if len(rows)!=40 or len({r['dd'] for r in rows})!=40: reasons.append('DD_DENOMINATOR_NOT_40_UNIQUE')
    if any(not r.get('parents') for r in rows): reasons.append('PARENT_MAPPING_GAP')
    if any(not r.get('objects') for r in rows): reasons.append('CANONICAL_OBJECT_GAP')
    open_by_dd={}
    for r in rows:
        gaps=[]
        for k in ('operation_api_event','schema_contract','executable_test'):
            if effective(r,defaults,k)!='MATERIALIZED': gaps.append(k)
        if effective(r,defaults,'runtime_evidence')!='EVIDENCED': gaps.append('runtime_evidence')
        if gaps: open_by_dd[r['dd']]=gaps
    machine_open=sum(1 for gaps in open_by_dd.values() for k in gaps if k!='runtime_evidence')
    runtime_open=sum(1 for gaps in open_by_dd.values() if 'runtime_evidence' in gaps)
    if machine_open: reasons.append(f'MACHINE_LAYER_GAP_INSTANCES:{machine_open}')
    if runtime_open: reasons.append(f'RUNTIME_EVIDENCE_GAP_ROWS:{runtime_open}')
    out={'contract':'ONSURE_DD_GRANULAR_VERTICAL_TRACE_VALIDATION_V2','dd_count':len(rows),'machine_layer_gap_instances':machine_open,'runtime_evidence_gap_rows':runtime_open,'open_by_dd':open_by_dd,'blocking_reasons':reasons,'decision':'PASS_NONFINAL' if not reasons else 'HOLD_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 32
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError) as e: print(f'ONSURE_DD_TRACE_FAIL {e}',file=sys.stderr); raise SystemExit(1)
