#!/usr/bin/env python3
from __future__ import annotations
import json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate'
BASE=ROOT/'.onsure/design-baseline'
DISC=ROOT/'.onsure/design-discovery/saturation-receipt.json'
REVIEW=ROOT/'evidence/pr-review/pr-54-independent-review.json'

def git(*args,check=True):
    p=subprocess.run(['git',*args],cwd=ROOT,text=True,capture_output=True)
    if check and p.returncode: raise subprocess.CalledProcessError(p.returncode,p.args,p.stdout,p.stderr)
    return p.stdout.strip(),p.returncode

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def run(cmd): return subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True).returncode

def main()->int:
    ref=os.environ.get('GITHUB_REF_NAME') or git('branch','--show-current')[0]
    head=git('rev-parse','HEAD')[0]; tree=git('rev-parse','HEAD^{tree}')[0]
    blockers=[]
    if ref!='main': blockers.append(f'NOT_MAIN_REF:{ref or "DETACHED"}')

    closure=CAND/'post-reconciliation-product-design-closure-receipt.json'
    recon=BASE/'reconstructability-receipt.json'
    if not closure.exists(): blockers.append('POST_RECONCILIATION_CLOSURE_RECEIPT_MISSING')
    else:
        c=load(closure)
        if c.get('blocking_reasons'): blockers.append('POST_RECONCILIATION_CLOSURE_BLOCKED')
        if c.get('requirement_manifest_digest') is None: blockers.append('REQUIREMENT_MANIFEST_DIGEST_MISSING')
        if c.get('authority_population_digest') is None: blockers.append('AUTHORITY_POPULATION_DIGEST_MISSING')
    if not recon.exists() or not load(recon).get('deterministic_two_run'): blockers.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')

    if not DISC.exists(): blockers.append('DISCOVERY_SATURATION_RECEIPT_MISSING')
    else:
        d=load(DISC)
        if not d.get('saturation_candidate'): blockers.append('DISCOVERY_SATURATION_NOT_PROVEN')
        if not d.get('tracked_evidence_inputs'): blockers.append('DISCOVERY_SATURATION_NOT_TRACKED')

    if run([sys.executable,'scripts/validate-human-design-authority-decisions.py']): blockers.append('HUMAN_DESIGN_AUTHORITY_OPEN')
    if run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications.py','--require-all-qualified']): blockers.append('DD_EVALUATOR_QUALIFICATION_40_OF_40_NOT_PROVEN')
    if run([sys.executable,'scripts/validate-dd-semantic-runtime-evidence.py','--require-all-pass']): blockers.append('DD_TARGET_RUNTIME_EVIDENCE_40_OF_40_NOT_PROVEN')
    if run([sys.executable,'scripts/validate-independent-clean-twice.py']): blockers.append('INDEPENDENT_CLEAN_TWICE_NOT_PROVEN')

    manual_path=os.environ.get('ONSURE_DD_MANUAL_VERIFICATION_RECEIPT','')
    if not manual_path: blockers.append('CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_NOT_SUPPLIED')
    else:
        mp=Path(manual_path); mp=mp if mp.is_absolute() else ROOT/mp
        if not mp.is_file(): blockers.append('CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_MISSING')
        else:
            m=load(mp); claims=m.get('claims') or {}
            if m.get('source_tree_sha')!=head: blockers.append('DD_MANUAL_VERIFICATION_HEAD_SHA_MISMATCH')
            if m.get('verdict')!='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY': blockers.append('DD_MANUAL_VERIFICATION_NOT_PASS')
            if not claims.get('compile_and_targeted_junit_established'): blockers.append('CURRENT_HEAD_JAVA_JUNIT_NOT_PROVEN')
            if not claims.get('qualification_fixture_mechanics_established'): blockers.append('DD_160_FIXTURE_MECHANICS_NOT_PROVEN')
            if m.get('github_actions_authority') is not False: blockers.append('DD_MANUAL_VERIFICATION_ACTIONS_AUTHORITY_INVALID')

    if not REVIEW.is_file(): blockers.append('INDEPENDENT_PR_REVIEW_RECEIPT_MISSING')
    else:
        review=load(REVIEW); reviewed_head=str(review.get('reviewed_head_sha',''))
        if not reviewed_head: blockers.append('INDEPENDENT_PR_REVIEW_HEAD_MISSING')
        else:
            if run([sys.executable,'scripts/validate-pr-independent-review.py','--expected-head-sha',reviewed_head]): blockers.append('INDEPENDENT_PR_REVIEW_INVALID')
            _,ancestor_rc=git('merge-base','--is-ancestor',reviewed_head,head,check=False)
            if ancestor_rc: blockers.append('REVIEWED_PR_HEAD_NOT_ANCESTOR_OF_MAIN_LOCK_SUBJECT')

    receipt={
      'contract':'ONSURE_DESIGN_LOCK_RECEIPT_V2',
      'subject_commit_sha':head,
      'subject_tree_sha':tree,
      'ref_name':ref,
      'blocking_reasons':sorted(set(blockers)),
      'decision':'DESIGN_LOCKED_NONFINAL_PRODUCT_AUTHORITY' if not blockers else 'HOLD_NONFINAL',
      'design_lock':not blockers,
      'final_lock':False,
      'production_go':False,
      'commercial_go':False,
      'github_actions_authority':False,
      'final_claim_allowed':False
    }
    out=BASE/'design-lock-receipt.json'; out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True)); return 0 if not blockers else 70
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,subprocess.CalledProcessError,ValueError,KeyError) as e:
        print(f'ONSURE_DESIGN_LOCK_FAIL {e}',file=sys.stderr); raise SystemExit(1)
