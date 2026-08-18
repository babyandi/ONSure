#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REGISTRY=ROOT/'contracts/design-document-authority-registry.v1.json'
OUT=ROOT/'.onsure/design-baseline/design-artifact-inventory.json'

def sha(b:bytes)->str:return hashlib.sha256(b).hexdigest()
def git(*args)->str:return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()

def main()->int:
    reg=json.loads(REGISTRY.read_text(encoding='utf-8'))
    by_path={r['path']:r for r in reg['documents']}
    tracked=set(git('ls-files').splitlines())
    selected=set(by_path)
    selected.update(p for p in tracked if p.startswith('docs/master/') and p.endswith('.md'))
    selected.update(p for p in tracked if p.startswith('docs/architecture/') and p.endswith('.md'))
    selected.update(p for p in tracked if p.startswith('contracts/') and (p.endswith('.json') or p.endswith('.schema.json')))
    rows=[]; missing=[]
    for rel in sorted(selected):
        p=ROOT/rel
        if rel not in tracked or not p.is_file(): missing.append(rel); continue
        meta=by_path.get(rel,{})
        rows.append({
          'path':rel,
          'document_id':meta.get('document_id'),
          'authority_role':meta.get('role','MACHINE_CONTRACT_OR_UNCLASSIFIED_DESIGN_ARTIFACT' if rel.startswith('contracts/') else 'DESIGN_ARTIFACT'),
          'lifecycle_state':meta.get('state','CURRENT_UNCLASSIFIED'),
          'content_sha256':sha(p.read_bytes()),
          'git_blob_sha':git('rev-parse',f'HEAD:{rel}'),
          'size_bytes':p.stat().st_size
        })
    digest=sha('\n'.join(f"{r['path']}:{r['content_sha256']}:{r['git_blob_sha']}:{r['authority_role']}:{r['lifecycle_state']}" for r in rows).encode())
    receipt={
      'contract':'ONSURE_EXACT_DESIGN_ARTIFACT_INVENTORY_V1',
      'git_commit_sha':git('rev-parse','HEAD'),
      'git_tree_sha':git('rev-parse','HEAD^{tree}'),
      'artifact_count':len(rows),
      'missing_registered_paths':missing,
      'population_digest':digest,
      'document_id_registry':'contracts/design-document-authority-registry.v1.json',
      'rows':rows,
      'decision':'MATERIALIZED_NONFINAL' if not missing else 'HOLD_NONFINAL',
      'final_claim_allowed':False
    }
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'artifact_count':len(rows),'missing_registered_paths':missing,'population_digest':digest,'decision':receipt['decision']},ensure_ascii=False,sort_keys=True))
    return 0 if not missing else 50
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,subprocess.CalledProcessError,ValueError,KeyError) as e:
        print(f'ONSURE_DESIGN_ARTIFACT_INVENTORY_FAIL {e}',file=sys.stderr); raise SystemExit(1)
