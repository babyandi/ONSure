#!/usr/bin/env python3
from __future__ import annotations
import json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INV=ROOT/'.onsure/design-baseline/design-artifact-inventory.json'
OUT=ROOT/'.onsure/design-baseline/reconstructability-receipt.json'

def run():
    r=subprocess.run([sys.executable,'scripts/materialize-design-artifact-inventory.py'],cwd=ROOT,capture_output=True,text=True)
    if r.returncode: raise RuntimeError(f'INVENTORY_MATERIALIZATION_FAILED:{r.returncode}:{r.stderr}')
    return json.loads(INV.read_text(encoding='utf-8'))

def main()->int:
    a=run(); b=run()
    keys=['git_commit_sha','git_tree_sha','artifact_count','population_digest','missing_registered_paths']
    stable=all(a[k]==b[k] for k in keys)
    rows_stable=[(r['path'],r['content_sha256'],r['git_blob_sha'],r['authority_role'],r['lifecycle_state']) for r in a['rows']]==[(r['path'],r['content_sha256'],r['git_blob_sha'],r['authority_role'],r['lifecycle_state']) for r in b['rows']]
    blockers=[]
    if not stable or not rows_stable: blockers.append('DESIGN_ARTIFACT_INVENTORY_NONDETERMINISTIC')
    if b['missing_registered_paths']: blockers.append('REGISTERED_DESIGN_ARTIFACT_MISSING')
    receipt={
      'contract':'ONSURE_DESIGN_BASELINE_RECONSTRUCTABILITY_RECEIPT_V1',
      'git_commit_sha':b['git_commit_sha'],
      'git_tree_sha':b['git_tree_sha'],
      'artifact_count':b['artifact_count'],
      'population_digest':b['population_digest'],
      'deterministic_two_run':stable and rows_stable,
      'blocking_reasons':blockers,
      'decision':'RECONSTRUCTABLE_NONFINAL' if not blockers else 'HOLD_NONFINAL',
      'design_lock':False,
      'final_claim_allowed':False
    }
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(receipt,ensure_ascii=False,sort_keys=True)); return 0 if not blockers else 51
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,RuntimeError,ValueError,KeyError) as e:
        print(f'ONSURE_DESIGN_RECONSTRUCTABILITY_FAIL {e}',file=sys.stderr); raise SystemExit(1)
