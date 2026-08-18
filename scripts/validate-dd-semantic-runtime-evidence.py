#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,subprocess
from pathlib import Path

EXPECTED={f'DD-{i:03d}' for i in range(1,41)}
POSITIVE='PASS_NONFINAL'
ROOT=Path(__file__).resolve().parents[1]
BUNDLE=ROOT/'.onsure/dd-independent-qualification/frozen-bundle/bundle-manifest.json'

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def digest_payload(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def git(*args):
    try:return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
    except Exception:return 'UNKNOWN'

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--status',default='contracts/dd-semantic-runtime-evidence-status.candidate.v1.json'); ap.add_argument('--receipts-dir',default='receipts/dd-semantic-runtime-evidence'); ap.add_argument('--machine-registry',default='contracts/dd-machine-operation-schema-fixture-registry.candidate.v1.json'); ap.add_argument('--qualification-status',default='contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json'); ap.add_argument('--qualification-receipts-dir',default='receipts/dd-semantic-evaluator-qualification'); ap.add_argument('--source-tree-sha',default=None); ap.add_argument('--source-commit-sha',default=None); ap.add_argument('--require-all-pass',action='store_true'); ap.add_argument('--require-all-positive-current',action='store_true'); args=ap.parse_args()
    status=load(ROOT/args.status); rows=status.get('rows',[]); errors=[]
    expected_tree=args.source_tree_sha or git('rev-parse','HEAD^{tree}'); expected_commit=args.source_commit_sha or git('rev-parse','HEAD')
    qualified_tree=load(BUNDLE).get('source_tree_sha') if BUNDLE.is_file() else None
    if not qualified_tree: errors.append('QUALIFICATION_BUNDLE_SUBJECT_TREE_MISSING')
    if len(rows)!=40 or {r.get('dd_id') for r in rows}!=EXPECTED: errors.append('RUNTIME_EVIDENCE_DENOMINATOR_NOT_EXACT_40')
    if status.get('contract') not in ('ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_STATUS_V2','ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_STATUS_V3'): errors.append('RUNTIME_STATUS_CONTRACT_UNSUPPORTED')
    if status.get('source_commit_sha') not in (None,expected_commit): errors.append('RUNTIME_STATUS_COMMIT_MISMATCH')
    if status.get('source_tree_sha') not in (None,expected_tree): errors.append('RUNTIME_STATUS_TREE_MISMATCH')
    if qualified_tree and status.get('qualified_subject_tree_sha') not in (None,qualified_tree): errors.append('RUNTIME_STATUS_QUALIFIED_SUBJECT_TREE_MISMATCH')
    machine=load(ROOT/args.machine_registry); operation_by_dd={r.get('dd'):r.get('operation') for r in machine.get('rows',[]) if r.get('dd') and r.get('operation')}
    if set(operation_by_dd)!=EXPECTED or len(set(operation_by_dd.values()))!=40: errors.append('MACHINE_OPERATION_DD_DENOMINATOR_NOT_EXACT_40')
    qstatus=load(ROOT/args.qualification_status); qrows={r.get('dd_id'):r for r in qstatus.get('rows',[])}
    if set(qrows)!=EXPECTED: errors.append('QUALIFICATION_STATUS_DENOMINATOR_NOT_EXACT_40')
    receipt_dir=ROOT/args.receipts_dir; qreceipt_dir=ROOT/args.qualification_receipts_dir; positive=nonpositive=notrun=0
    valid_states={'NOT_RUN','HOLD','FAIL','BLOCKED','INCONCLUSIVE','PASS_NONFINAL'}
    for row in rows:
        dd=row.get('dd_id'); state=row.get('runtime_state'); ref=row.get('runtime_receipt_ref')
        if state not in valid_states: errors.append(f'{dd}:INVALID_RUNTIME_STATE:{state}'); continue
        if state=='NOT_RUN':
            notrun+=1
            if ref: errors.append(f'{dd}:NOT_RUN_CANNOT_HAVE_RECEIPT')
            continue
        if not ref: errors.append(f'{dd}:EXECUTED_STATE_REQUIRES_RECEIPT'); nonpositive+=1; continue
        p=Path(ref); p=p if p.is_absolute() else ROOT/p
        if not p.is_file():
            alt=receipt_dir/p.name
            if alt.is_file(): p=alt
        if not p.is_file(): errors.append(f'{dd}:RUNTIME_RECEIPT_MISSING:{ref}'); nonpositive+=1; continue
        try:r=load(p)
        except Exception as e: errors.append(f'{dd}:RUNTIME_RECEIPT_UNREADABLE:{e}'); nonpositive+=1; continue
        if r.get('contract')!='ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_V1': errors.append(f'{dd}:WRONG_RUNTIME_RECEIPT_CONTRACT')
        if r.get('receipt_digest')!=digest_payload(r): errors.append(f'{dd}:RUNTIME_RECEIPT_DIGEST_MISMATCH')
        if r.get('dd_id')!=dd: errors.append(f'{dd}:RUNTIME_RECEIPT_DD_MISMATCH')
        if r.get('operation')!=operation_by_dd.get(dd): errors.append(f'{dd}:RUNTIME_OPERATION_DD_MISMATCH')
        if expected_commit!='UNKNOWN' and r.get('source_commit_sha')!=expected_commit: errors.append(f'{dd}:SOURCE_COMMIT_SHA_MISMATCH')
        if expected_tree!='UNKNOWN' and r.get('source_tree_sha')!=expected_tree: errors.append(f'{dd}:SOURCE_TREE_SHA_MISMATCH')
        if qualified_tree and r.get('qualified_subject_tree_sha')!=qualified_tree: errors.append(f'{dd}:QUALIFIED_SUBJECT_TREE_MISMATCH')
        if not r.get('target_identity') or not r.get('execution_principal') or not r.get('execution_environment'): errors.append(f'{dd}:TARGET_EXECUTION_PROVENANCE_INCOMPLETE')
        if r.get('synthetic_fixture') is not False: errors.append(f'{dd}:SYNTHETIC_FIXTURE_CANNOT_BE_RUNTIME_EVIDENCE')
        if r.get('github_actions_authority') is not False: errors.append(f'{dd}:GITHUB_ACTIONS_AUTHORITY_FORBIDDEN')
        if r.get('final_claim_allowed') is not False: errors.append(f'{dd}:FINAL_CLAIM_MUST_BE_FALSE')
        qr=qrows.get(dd) or {}
        if qr.get('qualification_state')!='QUALIFIED_NONFINAL': errors.append(f'{dd}:RUNTIME_REQUIRES_QUALIFIED_EVALUATOR_STATUS')
        qref=qr.get('qualification_receipt_ref'); qpath=Path(qref) if qref else None
        if qpath is not None and not qpath.is_absolute(): qpath=ROOT/qpath
        if qpath is not None and not qpath.is_file():
            alt=qreceipt_dir/qpath.name
            if alt.is_file():qpath=alt
        if not qpath or not qpath.is_file(): errors.append(f'{dd}:QUALIFICATION_RECEIPT_NOT_FOUND_FOR_RUNTIME')
        else:
            qreceipt=load(qpath); qdigest=qreceipt.get('receipt_digest')
            if qdigest!=digest_payload(qreceipt): errors.append(f'{dd}:QUALIFICATION_RECEIPT_DIGEST_INVALID')
            if r.get('qualification_receipt_digest')!=qdigest: errors.append(f'{dd}:RUNTIME_QUALIFICATION_DIGEST_MISMATCH')
            if r.get('evaluator_id')!=qreceipt.get('evaluator_id') or r.get('evaluator_version')!=qreceipt.get('evaluator_version'): errors.append(f'{dd}:RUNTIME_EVALUATOR_IDENTITY_MISMATCH')
            if qualified_tree and qreceipt.get('source_tree_sha')!=qualified_tree: errors.append(f'{dd}:QUALIFICATION_SUBJECT_TREE_MISMATCH')
        inputs=r.get('input_evidence') or []
        if not inputs: errors.append(f'{dd}:TARGET_INPUT_EVIDENCE_REQUIRED')
        refs=[]
        for x in inputs:
            if x.get('integrity_verified') is not True or x.get('current') is not True: errors.append(f'{dd}:INPUT_EVIDENCE_NOT_VERIFIED_CURRENT')
            for f in ('content_digest','declared_content_digest'):
                v=str(x.get(f,''));
                if len(v)!=64 or any(c not in '0123456789abcdef' for c in v): errors.append(f'{dd}:INPUT_EVIDENCE_{f.upper()}_INVALID')
            if x.get('content_digest')!=x.get('declared_content_digest'): errors.append(f'{dd}:INPUT_EVIDENCE_DIGEST_MISMATCH')
            if not x.get('authority_ref'): errors.append(f'{dd}:INPUT_EVIDENCE_AUTHORITY_MISSING')
            if x.get('evidence_ref'): refs.append(x.get('evidence_ref'))
        result=r.get('result') or {}; decision=result.get('decision')
        if decision!=state: errors.append(f'{dd}:STATUS_RECEIPT_DECISION_MISMATCH')
        if result.get('final_claim_allowed') is not False: errors.append(f'{dd}:RESULT_FINAL_CLAIM_MUST_BE_FALSE')
        if result.get('external_effect_performed') is not False: errors.append(f'{dd}:RUNTIME_EVALUATOR_EXTERNAL_EFFECT_PROHIBITED')
        result_refs=result.get('evidence_receipt_refs') or []
        if decision==POSITIVE:
            if result.get('claim_strengthening_allowed') is not True: errors.append(f'{dd}:POSITIVE_RESULT_MUST_EXPLICITLY_ALLOW_NONFINAL_STRENGTHENING')
            if result.get('blocking_reasons'): errors.append(f'{dd}:POSITIVE_RESULT_CANNOT_HAVE_BLOCKERS')
            if not result_refs: errors.append(f'{dd}:POSITIVE_RESULT_EVIDENCE_REFS_REQUIRED')
            if not set(result_refs).issubset(set(refs)): errors.append(f'{dd}:RESULT_EVIDENCE_REFS_NOT_BOUND_TO_INPUT')
            positive+=1
        else:
            if result.get('claim_strengthening_allowed') is not False: errors.append(f'{dd}:NONPOSITIVE_RESULT_CANNOT_STRENGTHEN')
            nonpositive+=1
    summary=status.get('summary') or {}
    if summary.get('dd_count')!=40: errors.append('STATUS_SUMMARY_DD_COUNT_MUST_BE_40')
    if summary.get('not_run_count')!=notrun or summary.get('pass_nonfinal_runtime_count')!=positive or summary.get('nonpositive_runtime_count')!=nonpositive: errors.append('STATUS_SUMMARY_RUNTIME_COUNTS_MISMATCH')
    if status.get('final_claim_allowed') is not False: errors.append('STATUS_FINAL_CLAIM_MUST_BE_FALSE')
    require_all=args.require_all_pass or args.require_all_positive_current
    if require_all and positive!=40: errors.append(f'ALL_POSITIVE_CURRENT_GATE_REQUIRES_40_OF_40:CURRENT={positive}')
    out={'contract':'ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_VALIDATION_V3','source_commit_sha':expected_commit,'source_tree_sha':expected_tree,'qualified_subject_tree_sha':qualified_tree,'dd_count':40,'not_run_count':notrun,'nonpositive_runtime_count':nonpositive,'pass_nonfinal_runtime_count':positive,'require_all_positive_current':require_all,'blocking_reasons':errors,'decision':'PASS_NONFINAL' if not errors else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)); return 0 if not errors else 43
if __name__=='__main__': raise SystemExit(main())
