#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
EXPECTED={f'DD-{i:03d}' for i in range(1,43)}
POSITIVE='PASS_NONFINAL'
DERIVED=ROOT/'.onsure/dd-independent-qualification/validated-status-successor.json'
BUNDLE=ROOT/'.onsure/dd-independent-qualification/frozen-bundle-successor/bundle-manifest.json'
STATUS=ROOT/'.onsure/dd-runtime-successor/status.json'
BASE_MACHINE=ROOT/'contracts/dd-machine-operation-schema-fixture-registry.candidate.v1.json'
EXT_OP={'DD-041':'crypto.erasure-completeness.evaluate','DD-042':'ai-safety.self-referential-claim.evaluate'}

def load(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def digest_payload(d):
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def valid_sha(v): return isinstance(v,str) and len(v)==64 and all(c in '0123456789abcdef' for c in v)

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--receipts-dir',default='receipts/dd-semantic-runtime-evidence-successor'); ap.add_argument('--source-commit-sha',default=None); ap.add_argument('--source-tree-sha',default=None); ap.add_argument('--require-all-pass',action='store_true'); ap.add_argument('--output',default='.onsure/dd-runtime-successor/runtime-42-validation.json'); args=ap.parse_args(); errors=[]
    qrc=subprocess.run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications-successor.py'],cwd=ROOT,capture_output=True,text=True).returncode
    if qrc: errors.append('QUALIFICATION_42_OF_42_NOT_VALID_FOR_RUNTIME')
    expected_commit=args.source_commit_sha or git('rev-parse','HEAD'); expected_tree=args.source_tree_sha or git('rev-parse','HEAD^{tree}')
    if not DERIVED.is_file() or not BUNDLE.is_file(): errors.append('SUCCESSOR_QUALIFICATION_STATUS_OR_BUNDLE_MISSING'); qrows={}; qualified_tree=None
    else:
        qderived=load(DERIVED); qrows={r.get('dd_id'):r for r in qderived.get('rows',[])}; bundle=load(BUNDLE); qualified_tree=bundle.get('source_tree_sha')
        if qderived.get('qualified_nonfinal_count')!=42 or set(qrows)!=EXPECTED: errors.append('DERIVED_QUALIFICATION_STATUS_NOT_42_OF_42')
    if not STATUS.is_file(): errors.append('SUCCESSOR_RUNTIME_STATUS_MISSING'); status={}; rows=[]
    else: status=load(STATUS); rows=status.get('rows',[])
    if status:
        if status.get('contract')!='ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_STATUS_V4': errors.append('RUNTIME_STATUS_CONTRACT_NOT_V4')
        if len(rows)!=42 or {r.get('dd_id') for r in rows}!=EXPECTED: errors.append('RUNTIME_EVIDENCE_DENOMINATOR_NOT_EXACT_42')
        if status.get('source_commit_sha')!=expected_commit: errors.append('RUNTIME_STATUS_COMMIT_MISMATCH')
        if status.get('source_tree_sha')!=expected_tree: errors.append('RUNTIME_STATUS_TREE_MISMATCH')
        if qualified_tree and status.get('qualified_subject_tree_sha')!=qualified_tree: errors.append('RUNTIME_STATUS_QUALIFIED_TREE_MISMATCH')
    machine=load(BASE_MACHINE); operation_by_dd={r.get('dd'):r.get('operation') for r in machine.get('rows',[]) if r.get('dd') and r.get('operation')}; operation_by_dd.update(EXT_OP)
    if set(operation_by_dd)!=EXPECTED or len(set(operation_by_dd.values()))!=42: errors.append('MACHINE_OPERATION_DD_DENOMINATOR_NOT_EXACT_42')
    receipt_dir=Path(args.receipts_dir); receipt_dir=receipt_dir if receipt_dir.is_absolute() else ROOT/receipt_dir
    positive=nonpositive=notrun=0; valid_states={'NOT_RUN','HOLD','FAIL','BLOCKED','INCONCLUSIVE','PASS_NONFINAL'}
    for row in rows:
        dd=row.get('dd_id'); state=row.get('runtime_state'); ref=row.get('runtime_receipt_ref')
        if state not in valid_states: errors.append(f'{dd}:INVALID_RUNTIME_STATE:{state}'); continue
        if state=='NOT_RUN': notrun+=1; continue
        p=Path(ref) if ref else None; p=(p if p and p.is_absolute() else ROOT/p) if p else None
        if not p or not p.is_file(): errors.append(f'{dd}:RUNTIME_RECEIPT_MISSING'); nonpositive+=1; continue
        try:r=load(p)
        except Exception as e: errors.append(f'{dd}:RUNTIME_RECEIPT_UNREADABLE:{e}'); nonpositive+=1; continue
        if r.get('contract')!='ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_V1': errors.append(f'{dd}:WRONG_RUNTIME_RECEIPT_CONTRACT')
        if r.get('receipt_digest')!=digest_payload(r): errors.append(f'{dd}:RUNTIME_RECEIPT_DIGEST_MISMATCH')
        if r.get('dd_id')!=dd: errors.append(f'{dd}:RUNTIME_RECEIPT_DD_MISMATCH')
        if r.get('operation')!=operation_by_dd.get(dd): errors.append(f'{dd}:RUNTIME_OPERATION_DD_MISMATCH')
        if r.get('source_commit_sha')!=expected_commit: errors.append(f'{dd}:SOURCE_COMMIT_SHA_MISMATCH')
        if r.get('source_tree_sha')!=expected_tree: errors.append(f'{dd}:SOURCE_TREE_SHA_MISMATCH')
        if qualified_tree and r.get('qualified_subject_tree_sha')!=qualified_tree: errors.append(f'{dd}:QUALIFIED_SUBJECT_TREE_MISMATCH')
        if not r.get('target_identity') or not r.get('execution_principal') or not r.get('execution_environment'): errors.append(f'{dd}:TARGET_EXECUTION_PROVENANCE_INCOMPLETE')
        if r.get('synthetic_fixture') is not False: errors.append(f'{dd}:SYNTHETIC_FIXTURE_CANNOT_BE_RUNTIME_EVIDENCE')
        if r.get('github_actions_authority') is not False: errors.append(f'{dd}:GITHUB_ACTIONS_AUTHORITY_FORBIDDEN')
        if r.get('final_claim_allowed') is not False: errors.append(f'{dd}:FINAL_CLAIM_MUST_BE_FALSE')
        qr=qrows.get(dd) or {}
        if qr.get('qualification_state')!='QUALIFIED_NONFINAL': errors.append(f'{dd}:RUNTIME_REQUIRES_DERIVED_QUALIFIED_EVALUATOR')
        qref=qr.get('qualification_receipt_ref'); qpath=Path(qref) if qref else None; qpath=(qpath if qpath and qpath.is_absolute() else ROOT/qpath) if qpath else None
        if not qpath or not qpath.is_file(): errors.append(f'{dd}:QUALIFICATION_RECEIPT_NOT_FOUND_FOR_RUNTIME')
        else:
            qreceipt=load(qpath); qdigest=qreceipt.get('receipt_digest')
            if qdigest!=digest_payload(qreceipt): errors.append(f'{dd}:QUALIFICATION_RECEIPT_DIGEST_INVALID')
            if r.get('qualification_receipt_digest')!=qdigest: errors.append(f'{dd}:RUNTIME_QUALIFICATION_DIGEST_MISMATCH')
            if r.get('evaluator_id')!=qreceipt.get('evaluator_id') or r.get('evaluator_version')!=qreceipt.get('evaluator_version'): errors.append(f'{dd}:RUNTIME_EVALUATOR_IDENTITY_MISMATCH')
        refs=[]
        for x in r.get('input_evidence') or []:
            if x.get('integrity_verified') is not True or x.get('current') is not True: errors.append(f'{dd}:INPUT_EVIDENCE_NOT_VERIFIED_CURRENT')
            if not valid_sha(x.get('content_digest')) or not valid_sha(x.get('declared_content_digest')): errors.append(f'{dd}:INPUT_EVIDENCE_DIGEST_INVALID')
            if x.get('content_digest')!=x.get('declared_content_digest'): errors.append(f'{dd}:INPUT_EVIDENCE_DIGEST_MISMATCH')
            if not x.get('authority_ref'): errors.append(f'{dd}:INPUT_EVIDENCE_AUTHORITY_MISSING')
            if x.get('evidence_ref'): refs.append(x.get('evidence_ref'))
        if not refs: errors.append(f'{dd}:TARGET_INPUT_EVIDENCE_REQUIRED')
        result=r.get('result') or {}; decision=result.get('decision')
        if decision!=state: errors.append(f'{dd}:STATUS_RECEIPT_DECISION_MISMATCH')
        if result.get('final_claim_allowed') is not False: errors.append(f'{dd}:RESULT_FINAL_CLAIM_MUST_BE_FALSE')
        if result.get('external_effect_performed') is not False: errors.append(f'{dd}:RUNTIME_EVALUATOR_EXTERNAL_EFFECT_PROHIBITED')
        result_refs=result.get('evidence_receipt_refs') or []
        if decision==POSITIVE:
            if result.get('claim_strengthening_allowed') is not True: errors.append(f'{dd}:POSITIVE_RESULT_STRENGTH_FLAG_REQUIRED')
            if result.get('blocking_reasons'): errors.append(f'{dd}:POSITIVE_RESULT_CANNOT_HAVE_BLOCKERS')
            if not result_refs or not set(result_refs).issubset(set(refs)): errors.append(f'{dd}:POSITIVE_RESULT_EVIDENCE_BINDING_INVALID')
            positive+=1
        else:
            if result.get('claim_strengthening_allowed') is not False: errors.append(f'{dd}:NONPOSITIVE_RESULT_CANNOT_STRENGTHEN')
            nonpositive+=1
    if status:
        summary=status.get('summary') or {}
        if summary.get('dd_count')!=42: errors.append('STATUS_SUMMARY_DD_COUNT_MUST_BE_42')
        if summary.get('not_run_count')!=notrun or summary.get('pass_nonfinal_runtime_count')!=positive or summary.get('nonpositive_runtime_count')!=nonpositive: errors.append('STATUS_SUMMARY_RUNTIME_COUNTS_MISMATCH')
    if args.require_all_pass and positive!=42: errors.append(f'ALL_POSITIVE_CURRENT_GATE_REQUIRES_42_OF_42:CURRENT={positive}')
    out={'contract':'ONSURE_DD_SEMANTIC_RUNTIME_EVIDENCE_VALIDATION_V5','source_commit_sha':expected_commit,'source_tree_sha':expected_tree,'qualified_subject_tree_sha':qualified_tree,'dd_count':42,'not_run_count':notrun,'nonpositive_runtime_count':nonpositive,'pass_nonfinal_runtime_count':positive,'blocking_reasons':sorted(set(errors)),'decision':'PASS_NONFINAL' if not errors else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    op=Path(args.output); op=op if op.is_absolute() else ROOT/op; op.parent.mkdir(parents=True,exist_ok=True); op.write_text(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(out,ensure_ascii=False,indent=2,sort_keys=True)); return 0 if not errors else 43
if __name__=='__main__': raise SystemExit(main())
