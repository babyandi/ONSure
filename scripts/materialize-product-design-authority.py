#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE_MANIFEST=ROOT/'.onsure/requirement-universe/requirement-authority-source-manifest.json'
DELTA_ALLOWLIST=ROOT/'contracts/requirement-authority-source-allowlist.delta-final-target.v1.json'
DELTA_SEED=ROOT/'contracts/requirement-authority-source-review.seed-final-target-delta.v1.json'
ELIGIBLE={"NORMATIVE_CURRENT","NORMATIVE_REFINEMENT"}
def sha256_bytes(b:bytes)->str:return hashlib.sha256(b).hexdigest()
def main()->int:
    rc=subprocess.run([sys.executable,'scripts/materialize-requirement-authority-manifest.py'],cwd=ROOT)
    if rc.returncode:return rc.returncode
    manifest=json.loads(BASE_MANIFEST.read_text(encoding='utf-8')); allow=json.loads(DELTA_ALLOWLIST.read_text(encoding='utf-8')); seed=json.loads(DELTA_SEED.read_text(encoding='utf-8'))
    seed_rows={r['artifact_path']:r for r in seed['rows']}; rows={r['artifact_path']:r for r in manifest['rows']}
    for rel in allow['paths']:
        p=ROOT/rel
        if not p.is_file():raise RuntimeError(f'DELTA_AUTHORITY_SOURCE_MISSING:{rel}')
        if rel not in seed_rows:raise RuntimeError(f'DELTA_AUTHORITY_SOURCE_UNREVIEWED:{rel}')
        s=seed_rows[rel]
        rows[rel]={"artifact_path":rel,"content_sha256":sha256_bytes(p.read_bytes()),"artifact_inventory_authority_class":"COMPANION","requirement_source_disposition":s['disposition'],"authority_parent_refs":s.get('authority_parent_refs',[]),"supersedes":s.get('supersedes',[]),"rationale":s['rationale'],"review_state":s['review_state']}
    ordered=[rows[k] for k in sorted(rows)]; source='\n'.join(f"{r['artifact_path']}:{r['content_sha256']}:{r['requirement_source_disposition']}" for r in ordered).encode()
    eligible_rows=[r for r in ordered if r['requirement_source_disposition'] in ELIGIBLE]; ineligible_rows=[r for r in ordered if r['requirement_source_disposition'] not in ELIGIBLE]
    summary={
      'row_count':len(ordered),
      'reviewed_count':sum(r['review_state']=='REVIEWED' for r in ordered),
      'unreviewed_count':sum(r['review_state']=='UNREVIEWED' for r in ordered),
      'disputed_count':sum(r['review_state']=='DISPUTED' for r in ordered),
      'eligible_count':len(eligible_rows),
      'ineligible_count':len(ineligible_rows),
      'eligible_unreviewed_count':sum(r['review_state']=='UNREVIEWED' for r in eligible_rows),
      'eligible_disputed_count':sum(r['review_state']=='DISPUTED' for r in eligible_rows),
      'ineligible_unreviewed_count':sum(r['review_state']=='UNREVIEWED' for r in ineligible_rows)
    }
    manifest['rows']=ordered; manifest['population_digest']=sha256_bytes(source); manifest['review_summary']=summary
    manifest['gate_semantics']={'eligible_unreviewed_or_disputed_blocks_denominator':True,'ineligible_unreviewed_is_disclosed_but_does_not_originate_requirements':True}
    manifest['delta_authority_extension']={'allowlist':DELTA_ALLOWLIST.relative_to(ROOT).as_posix(),'seed':DELTA_SEED.relative_to(ROOT).as_posix(),'added_paths':allow['paths']}
    BASE_MANIFEST.write_text(json.dumps(manifest,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    eligible_paths=sorted(r['artifact_path'] for r in ordered if r['requirement_source_disposition'] in ELIGIBLE)
    (BASE_MANIFEST.parent/'eligible-authority-sources.json').write_text(json.dumps(eligible_paths,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'row_count':len(ordered),'delta_paths':len(allow['paths']),'review_summary':summary,'population_digest':manifest['population_digest']}));return 0
if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,RuntimeError,ValueError,KeyError) as e:print(f'ONSURE_PRODUCT_DESIGN_AUTHORITY_FAIL {e}',file=sys.stderr);raise SystemExit(1)
