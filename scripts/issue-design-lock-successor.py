#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate';BASE=ROOT/'.onsure/design-baseline'
def git(*a,check=True):
    p=subprocess.run(['git',*a],cwd=ROOT,capture_output=True,text=True,check=False)
    if check and p.returncode:raise RuntimeError('GIT_FAILED:'+p.stderr[-500:])
    return p.stdout.strip(),p.returncode
def load(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def run(cmd):return subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,check=False).returncode
def canon(d):
    x=dict(d);x.pop('receipt_digest',None);return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def main()->int:
    ref=os.environ.get('GITHUB_REF_NAME') or git('branch','--show-current')[0];head=git('rev-parse','HEAD')[0];tree=git('rev-parse','HEAD^{tree}')[0];blockers=[]
    if ref!='main':blockers.append('NOT_MAIN_REF:'+str(ref))
    closure_path=CAND/'post-reconciliation-product-design-closure-receipt.json';c={}
    if not closure_path.is_file():blockers.append('SUCCESSOR_CLOSURE_RECEIPT_MISSING')
    else:
        c=load(closure_path)
        if c.get('contract')!='ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V9_SUCCESSOR':blockers.append('SUCCESSOR_CLOSURE_CONTRACT_INVALID')
        if c.get('decision')!='DESIGN_CLOSURE_CANDIDATE_NONFINAL' or c.get('blocking_reasons'):blockers.append('SUCCESSOR_CLOSURE_NOT_CANDIDATE')
        if c.get('dd_count')!=42 or c.get('qualification_fixture_count')!=173:blockers.append('SUCCESSOR_CLOSURE_DENOMINATOR_INVALID')
        for f in ('requirement_manifest_digest','authority_population_digest','preclean_subject_digest','coverage_digest'):
            if len(str(c.get(f,'')))!=64:blockers.append(f.upper()+'_INVALID')
    recon=BASE/'reconstructability-receipt.json'
    if not recon.is_file() or not load(recon).get('deterministic_two_run'):blockers.append('DESIGN_BASELINE_NOT_RECONSTRUCTABLE')
    gates=[
      ('DD_DENOMINATOR',[sys.executable,'scripts/validate-dd-denominator-42.py']),
      ('DISCOVERY',[sys.executable,'scripts/reconcile-design-discovery-waves-successor.py']),
      ('HDA',[sys.executable,'scripts/validate-human-design-authority-successor.py']),
      ('QUALIFICATION',[sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications-successor.py']),
      ('RUNTIME',[sys.executable,'scripts/validate-dd-semantic-runtime-evidence-successor.py','--source-commit-sha',head,'--source-tree-sha',tree,'--require-all-pass']),
      ('CLEAN',[sys.executable,'scripts/validate-independent-clean-twice.py','--source-commit-sha',head,'--source-tree-sha',tree])
    ]
    for name,cmd in gates:
        if run(cmd):blockers.append(name+'_NOT_PASS')
    manual_ref=os.environ.get('ONSURE_DD_MANUAL_VERIFICATION_RECEIPT','').strip()
    if not manual_ref:blockers.append('CURRENT_HEAD_DD_MANUAL_RECEIPT_NOT_SUPPLIED')
    else:
        mp=Path(manual_ref);mp=mp if mp.is_absolute() else ROOT/mp
        if not mp.is_file():blockers.append('CURRENT_HEAD_DD_MANUAL_RECEIPT_MISSING')
        else:
            m=load(mp);cl=m.get('claims') or {}
            if m.get('contract')!='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V7' or m.get('receipt_digest')!=canon(m) or m.get('source_commit_sha')!=head or m.get('source_tree_sha')!=tree or m.get('verdict')!='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY':blockers.append('CURRENT_HEAD_DD_MANUAL_RECEIPT_INVALID')
            if cl.get('dd_authorized_route_execution_mechanics_count')!=42 or cl.get('qualification_fixture_mechanics_executed_count')!=173 or cl.get('dd042_minimum_adversarial_fixture_mechanics_count')!=6:blockers.append('CURRENT_HEAD_DD_MANUAL_DENOMINATOR_INVALID')
    review_ref=os.environ.get('ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT','').strip()
    if not review_ref:blockers.append('INDEPENDENT_PR_REVIEW_RECEIPT_NOT_SUPPLIED')
    else:
        rp=Path(review_ref);rp=rp if rp.is_absolute() else ROOT/rp
        if not rp.is_file():blockers.append('INDEPENDENT_PR_REVIEW_RECEIPT_MISSING')
        else:
            review=load(rp);reviewed=str(review.get('reviewed_head_sha',''))
            if not reviewed:blockers.append('INDEPENDENT_PR_REVIEW_HEAD_MISSING')
            else:
                if run([sys.executable,'scripts/validate-pr-independent-review.py','--receipt',str(rp),'--expected-head-sha',reviewed]):blockers.append('INDEPENDENT_PR_REVIEW_INVALID')
                _,arc=git('merge-base','--is-ancestor',reviewed,head,check=False)
                if arc:blockers.append('REVIEWED_PR_HEAD_NOT_ANCESTOR_OF_MAIN_LOCK_SUBJECT')
    blockers=sorted(set(blockers));receipt={'contract':'ONSURE_DESIGN_LOCK_RECEIPT_V6_SUCCESSOR','subject_commit_sha':head,'subject_tree_sha':tree,'ref_name':ref,'requirement_manifest_digest':c.get('requirement_manifest_digest'),'authority_population_digest':c.get('authority_population_digest'),'preclean_subject_digest':c.get('preclean_subject_digest'),'coverage_digest':c.get('coverage_digest'),'dd_denominator':42,'qualification_fixture_denominator':173,'human_decision_denominator':22,'blocking_reasons':blockers,'decision':'DESIGN_LOCKED_NONFINAL_PRODUCT_AUTHORITY' if not blockers else 'HOLD_NONFINAL','design_lock':not blockers,'final_lock':False,'production_go':False,'commercial_go':False,'github_actions_authority':False,'final_claim_allowed':False}
    receipt['receipt_digest']=canon(receipt);out=BASE/'design-lock-receipt-successor.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8');print(json.dumps(receipt,ensure_ascii=False,sort_keys=True));return 0 if not blockers else 70
if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as e:print(json.dumps({'contract':'ONSURE_DESIGN_LOCK_RECEIPT_V6_SUCCESSOR','decision':'HOLD_NONFINAL','blocking_reasons':[f'{type(e).__name__}:{e}'],'design_lock':False,'final_lock':False,'production_go':False,'commercial_go':False,'final_claim_allowed':False},sort_keys=True));raise SystemExit(70)
