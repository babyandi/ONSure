#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate'
OUT=ROOT/'.onsure/independent-clean/preclean-subject.json'
ALLOWED_ONLY_BLOCKER='INDEPENDENT_CLEAN_TWICE_NOT_PASS'

def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def sha(p:Path): return hashlib.sha256(p.read_bytes()).hexdigest()
def digest_obj(x): return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    commit=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}'); reasons=[]
    closure_path=CAND/'post-reconciliation-product-design-closure-receipt.json'
    if not closure_path.is_file():
        print(json.dumps({'contract':'ONSURE_INDEPENDENT_CLEAN_PRECLEAN_SUBJECT_V2','blocking_reasons':['POST_RECONCILIATION_CLOSURE_RECEIPT_MISSING'],'decision':'HOLD_NONFINAL','final_claim_allowed':False})); return 62
    closure=json.loads(closure_path.read_text(encoding='utf-8')); blockers=set(closure.get('blocking_reasons') or []); unexpected=sorted(blockers-{ALLOWED_ONLY_BLOCKER})
    if unexpected: reasons.append('NON_CLEAN_BLOCKERS_REMAIN:'+','.join(unexpected))
    if ALLOWED_ONLY_BLOCKER not in blockers: reasons.append('EXPECTED_INDEPENDENT_CLEAN_BLOCKER_NOT_PRESENT')
    req=closure.get('requirement_manifest_digest'); auth=closure.get('authority_population_digest')
    if not isinstance(req,str) or len(req)!=64: reasons.append('REQUIREMENT_MANIFEST_DIGEST_INVALID')
    if not isinstance(auth,str) or len(auth)!=64: reasons.append('AUTHORITY_POPULATION_DIGEST_INVALID')
    critical=[
      CAND/'requirement-universe-snapshot.json',CAND/'global-trace-scan-report.json',CAND/'product-design-reverse-orphan-scan-report.json',CAND/'design-coverage-report.json',CAND/'final-product-requirements.log',CAND/'dd-machine-definition.log',CAND/'dd-granular-trace.log',CAND/'dd-evaluator-qualification.log',CAND/'dd-runtime-evidence.log',CAND/'human-design-authority.log',CAND/'local-reproducibility-twice.log',CAND/'candidate-preflight.log',
      ROOT/'.onsure/design-baseline/design-artifact-inventory.json',ROOT/'.onsure/design-baseline/reconstructability-receipt.json',ROOT/'.onsure/design-discovery/saturation-receipt.json',ROOT/'.onsure/dd-independent-qualification/validated-status.json',ROOT/'.onsure/dd-independent-qualification/frozen-bundle/bundle-manifest.json',ROOT/'contracts/dd-semantic-runtime-evidence-status.candidate.v1.json'
    ]
    inputs={}
    for p in critical:
        if not p.is_file(): reasons.append('PRECLEAN_INPUT_MISSING:'+p.relative_to(ROOT).as_posix())
        else: inputs[p.relative_to(ROOT).as_posix()]=sha(p)
    coverage_digest=digest_obj(inputs) if inputs else '0'*64
    subject={'contract':'ONSURE_INDEPENDENT_CLEAN_PRECLEAN_SUBJECT_V2','source_commit_sha':commit,'source_tree_sha':tree,'requirement_manifest_digest':req or '0'*64,'authority_population_digest':auth or '0'*64,'coverage_inputs':dict(sorted(inputs.items())),'coverage_digest':coverage_digest,'blocking_reasons':sorted(set(reasons)),'decision':'READY_FOR_INDEPENDENT_CLEAN_NONFINAL' if not reasons else 'HOLD_NONFINAL','github_actions_authority':False,'final_claim_allowed':False}
    subject['subject_digest']=digest_obj(subject); OUT.parent.mkdir(parents=True,exist_ok=True); OUT.write_text(json.dumps(subject,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(subject,ensure_ascii=False,sort_keys=True)); return 0 if not reasons else 62
if __name__=='__main__': raise SystemExit(main())
