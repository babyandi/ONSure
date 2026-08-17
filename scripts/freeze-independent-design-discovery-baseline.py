#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,re,shutil,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.onsure/design-discovery/frozen-baseline'; BUNDLE=OUT/'bundle'
BASE_FILES=[
'docs/05_PRODUCT_REQUIREMENTS_AND_ACCEPTANCE.md','docs/41_ONSURE_FINAL_TARGET_ARCHITECTURE.md','docs/42_VSCODE_AGENT_AND_GIT_FULL_CHAIN_DESIGN.md','docs/43_FINANCIAL_CONTROL_TRACE_AND_ACCEPTANCE.md','docs/44_UNIFIED_AI_WORK_DEVELOPER_ASSURANCE_DESIGN.md',
'docs/master/01_BUSINESS_PRODUCT_SERVICE_PLAN.md','docs/master/02_FUNCTIONAL_REQUIREMENTS_AND_PROGRAMS.md','docs/master/03_OREVIEW_CODE_REVIEW_SPECIFICATION.md','docs/master/04_ARCHITECTURE_DATA_API_OLICENSE.md','docs/master/05_UI_UX_WORKFLOW_SPECIFICATION.md','docs/master/06_TEST_OPERATION_IMPLEMENTATION_PLAN.md','docs/master/07_COMPONENT_MODEL_AND_AI_METHODOLOGY.md']
# Design-bearing pre-closure semantic sources. Closure/status/handoff/fresh-review documents are intentionally excluded.
SEMANTIC_PREFIXES=set(list(range(0,8))+list(range(11,16))+list(range(21,79))+[83,84,85,88,89,90,92,93,94,95,96,97,98])
FORBIDDEN=[re.compile(r'DD-\d{3}'),re.compile(r'GLOBAL_DISCOVERY_EXHAUSTED'),re.compile(r'PRODUCT_DESIGN_SCOPE_COMPLETE_CANDIDATE'),re.compile(r'40_POST_FINAL_TARGET_DELTA_OBLIGATIONS'),re.compile(r'DISCOVERY_SATURATION_NOT_PROVEN')]
def sha(b:bytes)->str:return hashlib.sha256(b).hexdigest()
def git(*args)->str:return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def sanitize_08a(text:str)->str:
    marker='## 9. Final-target delta discovery 결정 추적'
    return text.split(marker,1)[0].rstrip()+'\n'
def main()->int:
    head=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}')
    files=list(BASE_FILES)
    sem=ROOT/'docs/master/semantic-assurance'
    for p in sorted(sem.glob('*.md')):
        m=re.match(r'^(\d+)_',p.name)
        if m and int(m.group(1)) in SEMANTIC_PREFIXES: files.append(p.relative_to(ROOT).as_posix())
    files.append('docs/master/08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md')
    files=sorted(set(files)); rows=[]
    if OUT.exists(): shutil.rmtree(OUT)
    BUNDLE.mkdir(parents=True)
    for rel in files:
        src=ROOT/rel
        if not src.exists(): raise RuntimeError(f'MISSING_BASELINE_SOURCE:{rel}')
        text=src.read_text(encoding='utf-8')
        if rel.endswith('08A_ASSURANCE_POLICY_AND_OPEN_DECISION_INTEGRATION.md'): text=sanitize_08a(text)
        hits=[pat.pattern for pat in FORBIDDEN if pat.search(text)]
        if hits: raise RuntimeError(f'CONCLUSION_LEAKAGE:{rel}:{hits}')
        dst=BUNDLE/rel; dst.parent.mkdir(parents=True,exist_ok=True); dst.write_text(text,encoding='utf-8')
        blob=git('rev-parse',f'HEAD:{rel}')
        rows.append({'path':rel,'git_blob_sha':blob,'bundle_sha256':sha(text.encode())})
    authority_digest=sha('\n'.join(f"{r['path']}:{r['bundle_sha256']}" for r in rows).encode())
    receipt={'contract':'ONSURE_INDEPENDENT_DISCOVERY_FROZEN_BASELINE_V1','git_commit_sha':head,'git_tree_sha':tree,'source_count':len(rows),'authority_population_digest':authority_digest,'sources':rows,'conclusion_leakage_count':0,'reviewer_must_read_bundle_only':True,'repository_browsing_during_blind_wave_allowed':False,'final_claim_allowed':False}
    (OUT/'freeze-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'git_commit_sha':head,'git_tree_sha':tree,'source_count':len(rows),'authority_population_digest':authority_digest,'decision':'FROZEN_BASELINE_READY_NONFINAL'},ensure_ascii=False))
    return 0
if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,subprocess.CalledProcessError,RuntimeError) as e: print(f'ONSURE_DISCOVERY_FREEZE_FAIL {e}',file=sys.stderr); raise SystemExit(1)
