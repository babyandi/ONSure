#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,re,subprocess,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.onsure/requirement-universe/epoch-0003-candidate'
EXT=ROOT/'contracts/dd-041-042-design-gap-extension.candidate.v1.json'
REL=ROOT/'contracts/post-final-target-dd-041-042-to-fr-fin-relation.v1.json'
MIN=ROOT/'contracts/dd-042-adversarial-minimum-set.v1.json'
TRACE=ROOT/'docs/master/semantic-assurance/166_DD_041_042_SUCCESSOR_TRACE_AUTHORITY.md'
STATUS=ROOT/'status/dd-041-042-successor-design-trace.v1.json'
AUTHORITY_FILES=(EXT,REL,MIN,TRACE,STATUS)

def sha(b:bytes)->str:return hashlib.sha256(b).hexdigest()
def norm(s:str)->str:return re.sub(r'\s+',' ',re.sub(r'[^0-9A-Za-z가-힣]+',' ',s)).strip().lower()
def run(cmd):
    p=subprocess.run(cmd,cwd=ROOT,check=False)
    if p.returncode: raise RuntimeError(f'COMMAND_FAILED:{cmd}:{p.returncode}')

def main()->int:
    run([sys.executable,'scripts/generate-product-design-epoch-0003.py'])
    records_path=OUT/'requirement-records.json'; snapshot_path=OUT/'requirement-universe-snapshot.json'; receipt_path=OUT/'requirement-universe-generation-receipt.json'
    records=json.loads(records_path.read_text(encoding='utf-8')); snapshot=json.loads(snapshot_path.read_text(encoding='utf-8')); receipt=json.loads(receipt_path.read_text(encoding='utf-8'))
    ext=json.loads(EXT.read_text(encoding='utf-8')); rel=json.loads(REL.read_text(encoding='utf-8'))
    ext_rows={r['dd']:r for r in ext.get('rows',[]) if r.get('dd')}; rel_rows={r['dd']:r for r in rel.get('rows',[]) if r.get('dd')}
    if set(ext_rows)!={'DD-041','DD-042'} or set(rel_rows)!={'DD-041','DD-042'}: raise RuntimeError('SUCCESSOR_EXTENSION_OR_RELATION_DENOMINATOR_INVALID')
    records=[r for r in records if r.get('requirement_id') not in {'DD-041','DD-042'}]
    authority_rel=EXT.relative_to(ROOT).as_posix(); authority_sha=sha(EXT.read_bytes())
    for dd in ('DD-041','DD-042'):
        row=ext_rows[dd]; parents=rel_rows[dd].get('fr_fin') or []
        if not parents: raise RuntimeError(f'SUCCESSOR_PARENT_RELATION_MISSING:{dd}')
        criteria=' '.join(str(x) for x in row.get('acceptance_criteria',[]) if x)
        text=(str(row.get('requirement') or '').strip()+' Acceptance: '+criteria).strip()
        if not text: raise RuntimeError(f'SUCCESSOR_REQUIREMENT_TEXT_MISSING:{dd}')
        records.append({
          'requirement_id':dd,'explicit_id':dd,'source_class':'DISCOVERY_SUCCESSOR_AUTHORITY','extraction_method':'EXPLICIT_SUCCESSOR_EXTENSION_CONTRACT',
          'authority_document':authority_rel,'source_anchor':{'json_pointer':f"/rows/{0 if dd=='DD-041' else 1}"},'authority_document_sha256':authority_sha,
          'exact_source_digest':sha(text.encode()),'normative_text':text,'normative_text_digest':sha(norm(text).encode()),
          'owner_domain':'post-final-target-design-discovery','taxonomy':'ASSURANCE','subject':'PRODUCT','criticality':'CRITICAL',
          'claim_effect':'QUALIFICATION_GATE' if dd=='DD-042' else 'POSITIVE_CLAIM_GATE','waivability':'NON_WAIVABLE',
          'applicability_state':'UNKNOWN','applicability_rule_ref':None,'status':'ACTIVE','node_type':'CANONICAL_DISCOVERY_OBLIGATION',
          'parent_fr_fin':parents,'double_count_with_parent':False
        })
    records.sort(key=lambda r:r['requirement_id']); ids=[r['requirement_id'] for r in records]
    expected={f'DD-{i:03d}' for i in range(1,43)}
    actual={x for x in ids if str(x).startswith('DD-')}
    if actual!=expected or len(actual)!=42: raise RuntimeError(f'SUCCESSOR_DD_DENOMINATOR_INVALID:{sorted(expected-actual)}:{sorted(actual-expected)}')
    if len(ids)!=len(set(ids)): raise RuntimeError('DUPLICATE_REQUIREMENT_IDS_AFTER_SUCCESSOR_ADMISSION')
    manifest_digest=sha('\n'.join(f"{r['requirement_id']}:{r['normative_text_digest']}" for r in records).encode())
    population={x['path']:x for x in snapshot.get('authority_document_population',[]) if isinstance(x,dict) and x.get('path')}
    for p in AUTHORITY_FILES:
        population[p.relative_to(ROOT).as_posix()]={'path':p.relative_to(ROOT).as_posix(),'content_sha256':sha(p.read_bytes())}
    authority_population=sorted(population.values(),key=lambda x:x['path'])
    authority_digest=sha('\n'.join(f"{x['path']}:{x['content_sha256']}" for x in authority_population).encode())
    snapshot.update({
      'requirement_ids':ids,'requirement_manifest_digest':manifest_digest,'authority_document_population':authority_population,
      'authority_document_population_digest':authority_digest,'generated_at':datetime.now(timezone.utc).isoformat(),
      'dd_authority_admission':{
        'canonical_dd_count':42,
        'base_admission_contract':'contracts/post-final-target-dd-001-040-authority-admission.candidate.v1.json',
        'base_relation_registry':'contracts/post-final-target-dd-to-fr-fin-relation.candidate.v1.json',
        'successor_extension_contract':EXT.relative_to(ROOT).as_posix(),
        'successor_relation_registry':REL.relative_to(ROOT).as_posix(),
        'dd042_minimum_set':MIN.relative_to(ROOT).as_posix(),
        'double_count_with_parent':False
      }
    })
    receipt.update({'requirement_manifest_digest':manifest_digest,'authority_document_count':len(authority_population),'canonical_dd_requirement_count':42,'decision':'GENERATED_SUCCESSOR_POST_DELTA_NONFINAL'})
    records_path.write_text(json.dumps(records,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    snapshot_path.write_text(json.dumps(snapshot,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    receipt_path.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    admission={'contract':'ONSURE_DD_SUCCESSOR_AUTHORITY_ADMISSION_RECEIPT_V1','base_dd_count':40,'successor_added_dd_count':2,'dd_count':42,'missing_dd':[],'unmapped_dd':[],'requirement_count':len(ids),'requirement_manifest_digest':manifest_digest,'authority_population_digest':authority_digest,'decision':'ADMITTED_TO_EPOCH_0003_SUCCESSOR_CANDIDATE_NONFINAL','final_claim_allowed':False}
    (OUT/'dd-successor-authority-admission-receipt.json').write_text(json.dumps(admission,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps(admission,ensure_ascii=False,sort_keys=True)); return 0
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,RuntimeError,ValueError,KeyError) as e:
        print(f'ONSURE_PRODUCT_DESIGN_SUCCESSOR_EPOCH_0003_FAIL {e}',file=sys.stderr); raise SystemExit(1)
