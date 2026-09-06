#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate'; BASE=ROOT/'.onsure/design-baseline'; DISC=ROOT/'.onsure/design-discovery/saturation-receipt.json'

def git(*args,check=True):
    p=subprocess.run(['git',*args],cwd=ROOT,text=True,capture_output=True)
    if check and p.returncode: raise subprocess.CalledProcessError(p.returncode,p.args,p.stdout,p.stderr)
    return p.stdout.strip(),p.returncode

def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def run(cmd,env=None): return subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,env=env).returncode
def digest_payload(d):
    x=dict(d); x.pop('receipt_digest',None); return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    ref=os.environ.get('GITHUB_REF_NAME') or git('branch','--show-current')[0]; head=git('rev-parse','HEAD')[0]; tree=git('rev-parse','HEAD^{tree}')[0]; blockers=[]
    if ref!='main': blockers.append(f'NOT_MAIN_REF:{ref or "DETACHED"}')
    closure=CAND/'post-reconciliation-product-design-closure-receipt.json'; recon=BASE/'reconstructability-receipt.json'
    if not closure.exists(): blockers.append('POST_RECONCILIATION_CLOSURE_RECEIPT_MISSING'); c={}
    else:
        c=load(closure)
        if c.get('blocking_reasons'): blockers.append('POST_RECONCILIATION_CLOSURE_BLOCKED')
        if c.get('decision')!='DESIGN_CLOSURE_CANDIDATE_NONFINAL': blockers.append('POST_RECONCILIATION_CLOSURE_NOT_CANDIDATE')
        for f in ('requirement_manifest_digest','authority_population_digest','preclean_subject_digest','coverage_digest'):
            if len(str(c.get(f,'')))!=64: blockers.append(f'{f.upper()}_MISSING_OR_INVALID')
    if not recon.exists() or not load(recon).get('deterministic_two_run'): blockers.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
    if not DISC.exists(): blockers.append('DISCOVERY_SATURATION_RECEIPT_MISSING')
    else:
        d=load(DISC)
        if d.get('contract')!='ONSURE_DESIGN_DISCOVERY_SATURATION_RECEIPT_V5': blockers.append('DISCOVERY_SATURATION_RECEIPT_NOT_V5')
        if not d.get('saturation_candidate'): blockers.append('DISCOVERY_SATURATION_NOT_PROVEN')
        if d.get('evidence_custody_mode') not in ('EXTERNAL_IMMUTABLE','LOCAL_NONTRACKED'): blockers.append('DISCOVERY_SATURATION_CUSTODY_INVALID')
        if d.get('github_actions_authority') is not False: blockers.append('DISCOVERY_SATURATION_ACTIONS_AUTHORITY_INVALID')

    env=os.environ.copy()
    if run([sys.executable,'scripts/validate-design-discovery-saturation.py'],env): blockers.append('DISCOVERY_SATURATION_REVALIDATION_NOT_PASS')
    if run([sys.executable,'scripts/validate-human-design-authority-decisions.py'],env): blockers.append('HUMAN_DESIGN_AUTHORITY_OPEN')
    if run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications.py','--require-all-qualified'],env): blockers.append('DD_EVALUATOR_QUALIFICATION_40_OF_40_NOT_PROVEN')
    if run([sys.executable,'scripts/validate-dd-semantic-runtime-evidence.py','--source-commit-sha',head,'--source-tree-sha',tree,'--require-all-pass'],env): blockers.append('DD_TARGET_RUNTIME_EVIDENCE_40_OF_40_NOT_PROVEN')
    if run([sys.executable,'scripts/validate-independent-clean-twice.py','--source-commit-sha',head,'--source-tree-sha',tree],env): blockers.append('INDEPENDENT_CLEAN_TWICE_NOT_PROVEN')

    manual_ref=os.environ.get('ONSURE_DD_MANUAL_VERIFICATION_RECEIPT','').strip()
    if not manual_ref: blockers.append('CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_NOT_SUPPLIED')
    else:
        mp=Path(manual_ref); mp=mp if mp.is_absolute() else ROOT/mp
        if not mp.is_file(): blockers.append('CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_MISSING')
        else:
            m=load(mp); claims=m.get('claims') or {}
            if m.get('contract')!='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V5': blockers.append('DD_MANUAL_VERIFICATION_RECEIPT_NOT_V5')
            if m.get('receipt_digest')!=digest_payload(m): blockers.append('DD_MANUAL_VERIFICATION_RECEIPT_DIGEST_INVALID')
            if m.get('source_commit_sha')!=head: blockers.append('DD_MANUAL_VERIFICATION_COMMIT_SHA_MISMATCH')
            if m.get('source_tree_sha')!=tree: blockers.append('DD_MANUAL_VERIFICATION_TREE_SHA_MISMATCH')
            if m.get('verdict')!='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY': blockers.append('DD_MANUAL_VERIFICATION_NOT_PASS')
            if not claims.get('compile_and_targeted_junit_established'): blockers.append('CURRENT_HEAD_JAVA_JUNIT_NOT_PROVEN')
            if claims.get('dd_authorized_route_execution_mechanics_count')!=40: blockers.append('DD_ROUTE_EXECUTION_MECHANICS_NOT_40_OF_40')
            if claims.get('dd_schema_validator_execution_mechanics_count')!=40: blockers.append('DD_SCHEMA_EXECUTION_MECHANICS_NOT_40_OF_40')
            if claims.get('qualification_fixture_mechanics_executed_count')!=160: blockers.append('DD_160_FIXTURE_MECHANICS_NOT_PROVEN')
            if not claims.get('receipt_backed_runtime_activation_mechanics_established'): blockers.append('DD_RUNTIME_ACTIVATION_MECHANICS_NOT_PROVEN')
            compiled={x.get('path'):x.get('sha256') for x in m.get('compiled_artifacts',[]) if isinstance(x,dict)}
            if not compiled.get('target/classes/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.class'): blockers.append('DD_COMPILED_EVALUATOR_ARTIFACT_NOT_BOUND')
            if m.get('github_actions_authority') is not False: blockers.append('DD_MANUAL_VERIFICATION_ACTIONS_AUTHORITY_INVALID')

    review_ref=os.environ.get('ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT','').strip()
    if not review_ref: blockers.append('INDEPENDENT_PR_REVIEW_RECEIPT_NOT_SUPPLIED')
    else:
        rp=Path(review_ref); rp=rp if rp.is_absolute() else ROOT/rp
        if not rp.is_file(): blockers.append('INDEPENDENT_PR_REVIEW_RECEIPT_MISSING')
        else:
            review=load(rp); reviewed_head=str(review.get('reviewed_head_sha',''))
            if not reviewed_head: blockers.append('INDEPENDENT_PR_REVIEW_HEAD_MISSING')
            else:
                if run([sys.executable,'scripts/validate-pr-independent-review.py','--receipt',str(rp),'--expected-head-sha',reviewed_head],env): blockers.append('INDEPENDENT_PR_REVIEW_INVALID')
                _,ancestor_rc=git('merge-base','--is-ancestor',reviewed_head,head,check=False)
                if ancestor_rc: blockers.append('REVIEWED_PR_HEAD_NOT_ANCESTOR_OF_MAIN_LOCK_SUBJECT')

    receipt={'contract':'ONSURE_DESIGN_LOCK_RECEIPT_V5','subject_commit_sha':head,'subject_tree_sha':tree,'ref_name':ref,'requirement_manifest_digest':c.get('requirement_manifest_digest'),'authority_population_digest':c.get('authority_population_digest'),'preclean_subject_digest':c.get('preclean_subject_digest'),'coverage_digest':c.get('coverage_digest'),'blocking_reasons':sorted(set(blockers)),'decision':'DESIGN_LOCKED_NONFINAL_PRODUCT_AUTHORITY' if not blockers else 'HOLD_NONFINAL','design_lock':not blockers,'final_lock':False,'production_go':False,'commercial_go':False,'github_actions_authority':False,'final_claim_allowed':False}
    out=BASE/'design-lock-receipt.json'; out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True)); return 0 if not blockers else 70
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,subprocess.CalledProcessError,ValueError,KeyError) as e: print(f'ONSURE_DESIGN_LOCK_FAIL {e}',file=sys.stderr); raise SystemExit(1)
