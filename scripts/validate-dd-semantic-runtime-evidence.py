#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

EXPECTED={f'DD-{i:03d}' for i in range(1,41)}
POSITIVE='PASS_NONFINAL'

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))

def git_head():
    try: return subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
    except Exception: return 'UNKNOWN'

def main()->int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--status',default='contracts/dd-semantic-runtime-evidence-status.candidate.v1.json')
    ap.add_argument('--receipts-dir',default='receipts/dd-semantic-runtime-evidence')
    ap.add_argument('--source-tree-sha',default=None)
    ap.add_argument('--require-all-positive-current',action='store_true')
    args=ap.parse_args()
    status=load(Path(args.status)); rows=status.get('rows',[]); errors=[]
    if len(rows)!=40 or {r.get('dd_id') for r in rows}!=EXPECTED: errors.append('RUNTIME_EVIDENCE_DENOMINATOR_NOT_EXACT_40')
    expected_sha=args.source_tree_sha or git_head(); receipt_dir=Path(args.receipts_dir); positive=0; nonpositive=0; notrun=0
    valid_states={'NOT_RUN','HOLD','FAIL','BLOCKED','INCONCLUSIVE','PASS_NONFINAL'}
    for row in rows:
        dd=row.get('dd_id'); state=row.get('runtime_state'); ref=row.get('runtime_receipt_ref')
        if state not in valid_states:
            errors.append(f'{dd}:INVALID_RUNTIME_STATE:{state}'); continue
        if state=='NOT_RUN':
            notrun+=1
            if ref: errors.append(f'{dd}:NOT_RUN_CANNOT_HAVE_RECEIPT')
            continue
        if not ref:
            errors.append(f'{dd}:EXECUTED_STATE_REQUIRES_RECEIPT'); nonpositive+=1; continue
        p=Path(ref)
        if not p.is_absolute() and not p.exists(): p=receipt_dir/p.name
        if not p.is_file(): errors.append(f'{dd}:RUNTIME_RECEIPT_MISSING:{ref}'); nonpositive+=1; continue
        try: r=load(p)
        except Exception as e: errors.append(f'{dd}:RUNTIME_RECEIPT_UNREADABLE:{e}'); nonpositive+=1; continue
        if r.get('contract')!='ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_V1': errors.append(f'{dd}:WRONG_RUNTIME_RECEIPT_CONTRACT')
        if r.get('dd_id')!=dd: errors.append(f'{dd}:RUNTIME_RECEIPT_DD_MISMATCH')
        if expected_sha!='UNKNOWN' and r.get('source_tree_sha')!=expected_sha: errors.append(f'{dd}:SOURCE_TREE_SHA_MISMATCH')
        if r.get('synthetic_fixture') is not False: errors.append(f'{dd}:SYNTHETIC_FIXTURE_CANNOT_BE_RUNTIME_EVIDENCE')
        if r.get('github_actions_authority') is not False: errors.append(f'{dd}:GITHUB_ACTIONS_AUTHORITY_FORBIDDEN')
        if r.get('final_claim_allowed') is not False: errors.append(f'{dd}:FINAL_CLAIM_MUST_BE_FALSE')
        if not r.get('qualification_receipt_digest'): errors.append(f'{dd}:QUALIFICATION_RECEIPT_DIGEST_REQUIRED')
        inputs=r.get('input_evidence') or []
        if not inputs: errors.append(f'{dd}:TARGET_INPUT_EVIDENCE_REQUIRED')
        if any(x.get('integrity_verified') is not True or x.get('current') is not True for x in inputs): errors.append(f'{dd}:INPUT_EVIDENCE_NOT_VERIFIED_CURRENT')
        result=r.get('result') or {}; decision=result.get('decision')
        if decision!=state: errors.append(f'{dd}:STATUS_RECEIPT_DECISION_MISMATCH')
        if result.get('final_claim_allowed') is not False: errors.append(f'{dd}:RESULT_FINAL_CLAIM_MUST_BE_FALSE')
        if decision==POSITIVE:
            if result.get('claim_strengthening_allowed') is not True: errors.append(f'{dd}:POSITIVE_RESULT_MUST_EXPLICITLY_ALLOW_NONFINAL_STRENGTHENING')
            if result.get('blocking_reasons'): errors.append(f'{dd}:POSITIVE_RESULT_CANNOT_HAVE_BLOCKERS')
            if not result.get('evidence_receipt_refs'): errors.append(f'{dd}:POSITIVE_RESULT_EVIDENCE_REFS_REQUIRED')
            positive+=1
        else:
            nonpositive+=1
    summary=status.get('summary') or {}
    if summary.get('dd_count')!=40: errors.append('STATUS_SUMMARY_DD_COUNT_MUST_BE_40')
    if summary.get('not_run_count')!=notrun or summary.get('pass_nonfinal_runtime_count')!=positive or summary.get('nonpositive_runtime_count')!=nonpositive:
        errors.append('STATUS_SUMMARY_RUNTIME_COUNTS_MISMATCH')
    if status.get('final_claim_allowed') is not False: errors.append('STATUS_FINAL_CLAIM_MUST_BE_FALSE')
    if args.require_all_positive_current and positive!=40: errors.append(f'ALL_POSITIVE_CURRENT_GATE_REQUIRES_40_OF_40:CURRENT={positive}')
    out={'contract':'ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_VALIDATION_V1','source_tree_sha':expected_sha,'dd_count':40,'not_run_count':notrun,'nonpositive_runtime_count':nonpositive,'pass_nonfinal_runtime_count':positive,'require_all_positive_current':args.require_all_positive_current,'blocking_reasons':errors,'decision':'PASS_NONFINAL' if not errors else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)); return 0 if not errors else 43

if __name__=='__main__': raise SystemExit(main())
