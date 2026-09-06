#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate';OUT=ROOT/'.onsure/independent-clean/preclean-subject.json';ONLY='INDEPENDENT_CLEAN_TWICE_NOT_PASS'
def git(*a):return subprocess.check_output(['git',*a],cwd=ROOT,text=True).strip()
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def dig(x):return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
def main()->int:
    reasons=[];cp=CAND/'post-reconciliation-product-design-closure-receipt.json'
    if not cp.is_file():closure={};reasons.append('SUCCESSOR_CLOSURE_RECEIPT_MISSING')
    else:closure=json.loads(cp.read_text(encoding='utf-8'))
    blockers=set(closure.get('blocking_reasons') or []);unexpected=sorted(blockers-{ONLY})
    if unexpected:reasons.append('NON_CLEAN_BLOCKERS_REMAIN:'+','.join(unexpected))
    if ONLY not in blockers:reasons.append('EXPECTED_INDEPENDENT_CLEAN_BLOCKER_NOT_PRESENT')
    if closure.get('contract')!='ONSURE_POST_RECONCILIATION_PRODUCT_DESIGN_CLOSURE_RECEIPT_V9_SUCCESSOR_PRECLEAN':reasons.append('SUCCESSOR_PRECLEAN_CLOSURE_CONTRACT_INVALID')
    req=closure.get('requirement_manifest_digest');auth=closure.get('authority_population_digest')
    if not isinstance(req,str) or len(req)!=64:reasons.append('REQUIREMENT_MANIFEST_DIGEST_INVALID')
    if not isinstance(auth,str) or len(auth)!=64:reasons.append('AUTHORITY_POPULATION_DIGEST_INVALID')
    critical=[
      CAND/'requirement-universe-snapshot.json',CAND/'global-trace-scan-report.json',CAND/'product-design-reverse-orphan-scan-report.json',CAND/'design-coverage-report.json',
      CAND/'final-product-requirements.log',CAND/'dd-denominator-42.log',CAND/'dd-granular-trace-successor.log',CAND/'dd-evaluator-qualification-successor.log',CAND/'dd-runtime-evidence-successor.log',CAND/'human-design-authority-successor.log',CAND/'design-discovery-reconciliation-successor.log',CAND/'local-reproducibility-twice.log',CAND/'candidate-preflight-preclean-successor.json',
      ROOT/'.onsure/design-baseline/design-artifact-inventory.json',ROOT/'.onsure/design-baseline/reconstructability-receipt.json',ROOT/'.onsure/design-discovery-v3/reconciliation-receipt-successor.json',ROOT/'.onsure/dd-independent-qualification/validated-status-successor.json',ROOT/'.onsure/dd-independent-qualification/frozen-bundle-successor/bundle-manifest.json',ROOT/'.onsure/dd-runtime-successor/runtime-42-validation.json'
    ]
    inputs={}
    for p in critical:
        if not p.is_file():reasons.append('PRECLEAN_INPUT_MISSING:'+p.relative_to(ROOT).as_posix())
        else:inputs[p.relative_to(ROOT).as_posix()]=sha(p)
    for env_name,label in (('ONSURE_DD040_BOUND_DECISION_RECEIPT','external:dd040-bound-decision'),('ONSURE_HDA_SUCCESSOR_APPROVAL','external:hda-successor-approval')):
        ref=os.environ.get(env_name,'').strip()
        if not ref:reasons.append(env_name+'_NOT_SUPPLIED');continue
        p=Path(ref).expanduser();p=p if p.is_absolute() else ROOT/p
        if not p.is_file():reasons.append(env_name+'_MISSING')
        else:inputs[label]=sha(p)
    coverage=dig(inputs) if inputs else '0'*64
    subject={'contract':'ONSURE_INDEPENDENT_CLEAN_PRECLEAN_SUBJECT_V2','source_commit_sha':git('rev-parse','HEAD'),'source_tree_sha':git('rev-parse','HEAD^{tree}'),'requirement_manifest_digest':req or '0'*64,'authority_population_digest':auth or '0'*64,'coverage_inputs':dict(sorted(inputs.items())),'coverage_digest':coverage,'blocking_reasons':sorted(set(reasons)),'decision':'READY_FOR_INDEPENDENT_CLEAN_NONFINAL' if not reasons else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    subject['subject_digest']=dig(subject);OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_text(json.dumps(subject,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8');print(json.dumps(subject,ensure_ascii=False,sort_keys=True));return 0 if not reasons else 62
if __name__=='__main__':raise SystemExit(main())
