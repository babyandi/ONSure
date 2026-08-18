#!/usr/bin/env python3
from __future__ import annotations
import json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
CAND=ROOT/'.onsure/requirement-universe/epoch-0003-candidate'
BASE=ROOT/'.onsure/design-baseline'
DISC=ROOT/'.onsure/design-discovery/saturation-receipt.json'

def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def load(p:Path): return json.loads(p.read_text(encoding='utf-8'))
def run(cmd): return subprocess.run(cmd,cwd=ROOT,capture_output=True,text=True).returncode

def main()->int:
    ref=os.environ.get('GITHUB_REF_NAME') or git('branch','--show-current')
    head=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}')
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
    if run([sys.executable,'scripts/validate-independent-clean-twice.py']): blockers.append('INDEPENDENT_CLEAN_TWICE_NOT_PROVEN')
    receipt={
      'contract':'ONSURE_DESIGN_LOCK_RECEIPT_V1',
      'subject_commit_sha':head,
      'subject_tree_sha':tree,
      'ref_name':ref,
      'blocking_reasons':blockers,
      'decision':'DESIGN_LOCKED_NONFINAL_PRODUCT_AUTHORITY' if not blockers else 'HOLD_NONFINAL',
      'design_lock':not blockers,
      'final_lock':False,
      'production_go':False,
      'commercial_go':False,
      'final_claim_allowed':False
    }
    out=BASE/'design-lock-receipt.json'; out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True)); return 0 if not blockers else 70
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,subprocess.CalledProcessError,ValueError,KeyError) as e:
        print(f'ONSURE_DESIGN_LOCK_FAIL {e}',file=sys.stderr); raise SystemExit(1)
