#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

def run(cmd): return subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True,env=os.environ.copy()).returncode
def load(p): return json.loads(Path(p).read_text(encoding='utf-8'))
def digest_payload(d):
    x=dict(d); x.pop('receipt_digest',None); return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--dd-manual-receipt',required=True); ap.add_argument('--pr-review-receipt',default=os.environ.get('ONSURE_PR_INDEPENDENT_REVIEW_RECEIPT','')); args=ap.parse_args()
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(); tree=subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip(); branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip(); reasons=[]
    if branch=='main': reasons.append('PREMERGE_GATE_MUST_RUN_ON_FEATURE_BRANCH')
    if run([sys.executable,'scripts/validate-dd-machine-definitions.py']): reasons.append('DD_MACHINE_DEFINITION_NOT_PASS')
    if run([sys.executable,'scripts/validate-dd-semantic-evaluator-qualifications.py','--require-all-qualified']): reasons.append('DD_EVALUATOR_QUALIFICATION_40_OF_40_NOT_PROVEN')
    if run([sys.executable,'scripts/validate-dd-semantic-runtime-evidence.py','--source-commit-sha',head,'--source-tree-sha',tree,'--require-all-pass']): reasons.append('DD_TARGET_RUNTIME_EVIDENCE_40_OF_40_NOT_PROVEN')
    if run([sys.executable,'scripts/validate-design-discovery-saturation.py']): reasons.append('DISCOVERY_SATURATION_A_B_NOT_PROVEN')
    if run([sys.executable,'scripts/validate-human-design-authority-decisions.py']): reasons.append('HUMAN_DESIGN_AUTHORITY_18_NOT_CLOSED')
    if run([sys.executable,'scripts/validate-independent-clean-twice.py','--source-commit-sha',head,'--source-tree-sha',tree]): reasons.append('INDEPENDENT_CLEAN_A_B_NOT_PROVEN')
    mp=Path(args.dd_manual_receipt); mp=mp if mp.is_absolute() else ROOT/mp
    if not mp.is_file(): reasons.append('DD_MANUAL_VERIFICATION_RECEIPT_MISSING')
    else:
        m=load(mp); c=m.get('claims') or {}
        if m.get('contract')!='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V5': reasons.append('DD_MANUAL_VERIFICATION_RECEIPT_NOT_V5')
        if m.get('receipt_digest')!=digest_payload(m): reasons.append('DD_MANUAL_VERIFICATION_RECEIPT_DIGEST_INVALID')
        if m.get('source_commit_sha')!=head: reasons.append('DD_MANUAL_VERIFICATION_COMMIT_MISMATCH')
        if m.get('source_tree_sha')!=tree: reasons.append('DD_MANUAL_VERIFICATION_TREE_MISMATCH')
        if m.get('verdict')!='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY': reasons.append('DD_MANUAL_VERIFICATION_NOT_PASS')
        if not c.get('compile_and_targeted_junit_established'): reasons.append('CURRENT_HEAD_JAVA_JUNIT_NOT_PROVEN')
        if c.get('dd_authorized_route_execution_mechanics_count')!=40: reasons.append('DD_ROUTE_EXECUTION_MECHANICS_NOT_40_OF_40')
        if c.get('dd_schema_validator_execution_mechanics_count')!=40: reasons.append('DD_SCHEMA_EXECUTION_MECHANICS_NOT_40_OF_40')
        if c.get('qualification_fixture_mechanics_executed_count')!=160: reasons.append('DD_160_FIXTURE_MECHANICS_NOT_PROVEN')
        if not c.get('receipt_backed_runtime_activation_mechanics_established'): reasons.append('DD_RUNTIME_ACTIVATION_MECHANICS_NOT_PROVEN')
    review_ref=args.pr_review_receipt.strip()
    if not review_ref: reasons.append('INDEPENDENT_PR_REVIEW_RECEIPT_NOT_SUPPLIED')
    else:
        review=Path(review_ref); review=review if review.is_absolute() else ROOT/review
        if not review.is_file(): reasons.append('INDEPENDENT_PR_REVIEW_RECEIPT_MISSING')
        else:
            r=load(review)
            if r.get('reviewed_head_sha')!=head: reasons.append('INDEPENDENT_PR_REVIEW_NOT_FOR_CURRENT_HEAD')
            elif run([sys.executable,'scripts/validate-pr-independent-review.py','--receipt',str(review),'--expected-head-sha',head]): reasons.append('INDEPENDENT_PR_REVIEW_INVALID')
    out={'contract':'ONSURE_PREMERGE_DESIGN_LOCK_READINESS_V3','branch':branch,'head_commit_sha':head,'head_tree_sha':tree,'blocking_reasons':sorted(set(reasons)),'decision':'READY_FOR_MAIN_MERGE_NONFINAL' if not reasons else 'HOLD_NONFINAL','main_merge_allowed':not reasons,'design_lock':False,'github_actions_authority':False,'final_claim_allowed':False}
    print(json.dumps(out,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 81
if __name__=='__main__': raise SystemExit(main())
