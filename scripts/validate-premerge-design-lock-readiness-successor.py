#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate'
def run(cmd):
    p=subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,check=False);return p.returncode,p.stdout.strip(),p.stderr.strip()
def git(*a):return subprocess.check_output(['git',*a],cwd=ROOT,text=True).strip()
def load(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def canon(d):
    x=dict(d);x.pop('receipt_digest',None);return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def main()->int:
    ap=argparse.ArgumentParser();ap.add_argument('--dd-manual-receipt',required=True);ap.add_argument('--pr-review-receipt',required=True);args=ap.parse_args();reasons=[];evidence={};head=git('rev-parse','HEAD');tree=git('rev-parse','HEAD^{tree}')
    mp=Path(args.dd_manual_receipt);mp=mp if mp.is_absolute() else ROOT/mp
    if not mp.is_file():reasons.append('CURRENT_HEAD_DD_MANUAL_RECEIPT_MISSING')
    else:
        m=load(mp);c=m.get('claims') or {}
        if m.get('contract')!='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V7' or m.get('receipt_digest')!=canon(m) or m.get('source_commit_sha')!=head or m.get('source_tree_sha')!=tree or m.get('verdict')!='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY':reasons.append('CURRENT_HEAD_DD_MANUAL_RECEIPT_INVALID')
        if c.get('dd_authorized_route_execution_mechanics_count')!=42 or c.get('qualification_fixture_mechanics_executed_count')!=173 or c.get('dd042_minimum_adversarial_fixture_mechanics_count')!=6:reasons.append('CURRENT_HEAD_DD_MANUAL_DENOMINATOR_INVALID')
    gates={
      'denominator':[sys.executable,'scripts/validate-dd-denominator-42.py'],
      'qualification':[sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications-successor.py'],
      'discovery':[sys.executable,'scripts/reconcile-design-discovery-waves-successor.py'],
      'hda':[sys.executable,'scripts/validate-human-design-authority-successor.py'],
      'runtime':[sys.executable,'scripts/validate-dd-semantic-runtime-evidence-successor.py','--source-commit-sha',head,'--source-tree-sha',tree,'--require-all-pass'],
      'clean':[sys.executable,'scripts/validate-independent-clean-twice.py','--source-commit-sha',head,'--source-tree-sha',tree]
    }
    for name,cmd in gates.items():rc,out,err=run(cmd);evidence[name]={'rc':rc,'stdout':out[-4000:]};reasons += ([] if rc==0 else [name.upper()+'_NOT_PASS'])
    closure_path=CAND/'post-reconciliation-product-design-closure-receipt.json'
    if not closure_path.is_file():reasons.append('SUCCESSOR_CLOSURE_RECEIPT_MISSING')
    else:
        cl=load(closure_path)
        if cl.get('contract')!='ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V9_SUCCESSOR' or cl.get('decision')!='DESIGN_CLOSURE_CANDIDATE_NONFINAL' or cl.get('blocking_reasons'):reasons.append('SUCCESSOR_CLOSURE_NOT_CANDIDATE')
        if cl.get('dd_count')!=42 or cl.get('qualification_fixture_count')!=173:reasons.append('SUCCESSOR_CLOSURE_DENOMINATOR_INVALID')
    rr=Path(args.pr_review_receipt);rr=rr if rr.is_absolute() else ROOT/rr
    if not rr.is_file():reasons.append('INDEPENDENT_PR_REVIEW_RECEIPT_MISSING')
    else:
        rc,out,err=run([sys.executable,'scripts/validate-pr-independent-review.py','--receipt',str(rr),'--expected-head-sha',head]);evidence['pr_review']={'rc':rc,'stdout':out[-4000:]};reasons += ([] if rc==0 else ['INDEPENDENT_PR_REVIEW_NOT_PASS'])
    reasons=sorted(set(reasons));result={'contract':'ONSURE_SUCCESSOR_PREMERGE_READINESS_V4','subject_commit_sha':head,'subject_tree_sha':tree,'required_dd_count':42,'required_qualification_fixture_count':173,'required_human_decision_denominator':22,'blocking_reasons':reasons,'ready_for_main_merge':not reasons,'decision':'READY_FOR_MAIN_MERGE_NONFINAL' if not reasons else 'HOLD_NONFINAL','evidence':evidence,'main_merge_allowed':not reasons,'design_lock':False,'final_claim_allowed':False}
    print(json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True));return 0 if not reasons else 2
if __name__=='__main__':raise SystemExit(main())
