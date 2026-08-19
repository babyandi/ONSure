#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import shutil
import stat
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
FROZEN=ROOT/'.onsure/design-discovery/frozen-baseline'
BASELINE=FROZEN/'freeze-receipt.json'
BUNDLE=FROZEN/'bundle'
POLICY=ROOT/'contracts/design-discovery-blind-execution-policy.v1.json'
OUT=ROOT/'.onsure/design-discovery-v3/envelopes'
RESULT_ROOT=ROOT/'.onsure/design-discovery-v3/sealed-results'
WAVES=('INDEPENDENT-SATURATION-A','INDEPENDENT-SATURATION-B')

def sha256_bytes(b:bytes)->str:return hashlib.sha256(b).hexdigest()
def sha256_file(p:Path)->str:return sha256_bytes(p.read_bytes())
def canonical_digest(d:dict,drop:str|None=None)->str:
    x=dict(d)
    if drop:x.pop(drop,None)
    return sha256_bytes(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode())
def git(*args)->str:return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def now()->str:return datetime.now(timezone.utc).isoformat().replace('+00:00','Z')

def make_read_only(root:Path)->None:
    for p in sorted(root.rglob('*'),reverse=True):
        try:
            p.chmod(0o555 if p.is_dir() else 0o444)
        except OSError: pass
    root.chmod(0o555)

def main()->int:
    if git('status','--porcelain','--untracked-files=no'):
        raise RuntimeError('TRACKED_WORKTREE_NOT_CLEAN')
    if not BASELINE.is_file() or not BUNDLE.is_dir() or not POLICY.is_file():
        raise RuntimeError('FROZEN_BASELINE_OR_POLICY_MISSING')
    if RESULT_ROOT.exists() and any(RESULT_ROOT.iterdir()):
        raise RuntimeError('WAVE_RESULTS_ALREADY_EXIST_BEFORE_ENVELOPE_PREPARATION')
    baseline=json.loads(BASELINE.read_text(encoding='utf-8'))
    if baseline.get('contract')!='ONSURE_INDEPENDENT_DISCOVERY_FROZEN_BASELINE_V2':
        raise RuntimeError('BASELINE_CONTRACT_MISMATCH')
    if baseline.get('tracked_worktree_clean') is not True or baseline.get('conclusion_leakage_count')!=0:
        raise RuntimeError('BASELINE_NOT_CLEAN_OR_LEAK_FREE')
    if baseline.get('reviewer_must_read_bundle_only') is not True or baseline.get('repository_browsing_during_blind_wave_allowed') is not False:
        raise RuntimeError('BASELINE_BLINDNESS_POLICY_MISMATCH')
    head=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}')
    if baseline.get('git_commit_sha')!=head or baseline.get('git_tree_sha')!=tree:
        raise RuntimeError('BASELINE_SUBJECT_MISMATCH')
    rows=baseline.get('sources') or []
    checked=[]
    for row in rows:
        rel=row.get('path'); expected=row.get('bundle_sha256'); p=BUNDLE/str(rel)
        if not rel or not p.is_file() or sha256_file(p)!=expected:
            raise RuntimeError('BASELINE_BUNDLE_FILE_DIGEST_MISMATCH:'+str(rel))
        checked.append((str(rel),expected))
    population=sha256_bytes('\n'.join(f'{p}:{d}' for p,d in checked).encode())
    if population!=baseline.get('authority_population_digest'):
        raise RuntimeError('BASELINE_AUTHORITY_POPULATION_DIGEST_MISMATCH')
    baseline_sha=sha256_file(BASELINE); policy_sha=sha256_file(POLICY); prepared=now()
    if OUT.exists(): shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    pair_envelopes={}
    for wave in WAVES:
        wave_dir=OUT/wave
        input_dir=wave_dir/'input'
        input_dir.mkdir(parents=True)
        shutil.copytree(BUNDLE,input_dir/'bundle')
        shutil.copy2(BASELINE,input_dir/'frozen-baseline-receipt.json')
        shutil.copy2(POLICY,input_dir/'blind-execution-policy.json')
        workspace_seed=sha256_bytes(f'{head}:{population}:{wave}:workspace'.encode())
        envelope_id='ONSURE-'+wave+'-'+workspace_seed[:20]
        manifest={
          'contract':'ONSURE_DESIGN_DISCOVERY_SEALED_ENVELOPE_V1',
          'wave_id':wave,
          'envelope_id':envelope_id,
          'frozen_commit_sha':head,
          'frozen_tree_sha':tree,
          'frozen_authority_digest':population,
          'frozen_baseline_receipt_sha256':baseline_sha,
          'input_bundle_digest':population,
          'blind_execution_policy_sha256':policy_sha,
          'workspace_seed':workspace_seed,
          'prepared_at':prepared,
          'prepared_by':'ONSURE_SEALED_ENVELOPE_COORDINATOR_V1',
          'read_only_frozen_bundle':True,
          'repository_browsing_allowed':False,
          'issue_pr_comment_browsing_allowed':False,
          'opposite_wave_result_mounted':False,
          'result_present_at_prepare':False,
          'allowed_input_population':['input/bundle','input/frozen-baseline-receipt.json','input/blind-execution-policy.json'],
          'final_claim_allowed':False
        }
        manifest['envelope_digest']=canonical_digest(manifest)
        (wave_dir/'envelope-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
        pair_envelopes[wave]={k:manifest[k] for k in ('wave_id','envelope_id','envelope_digest','workspace_seed','read_only_frozen_bundle','repository_browsing_allowed','opposite_wave_result_mounted','result_present_at_prepare','allowed_input_population')}
        make_read_only(input_dir)
    pair={
      'contract':'ONSURE_DESIGN_DISCOVERY_SEALED_ENVELOPE_PAIR_V1',
      'frozen_commit_sha':head,'frozen_tree_sha':tree,'frozen_authority_digest':population,
      'frozen_baseline_receipt_sha256':baseline_sha,'input_bundle_digest':population,
      'blind_execution_policy_sha256':policy_sha,'prepared_at':prepared,
      'prepared_by':'ONSURE_SEALED_ENVELOPE_COORDINATOR_V1',
      'prepared_before_any_result':True,'results_present_at_prepare':False,
      'envelopes':pair_envelopes,'final_claim_allowed':False
    }
    pair['pair_digest']=canonical_digest(pair)
    (OUT/'envelope-pair-receipt.json').write_text(json.dumps(pair,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_ENVELOPE_PREPARATION_V1','decision':'PAIRED_ENVELOPES_READY_NONFINAL','frozen_commit_sha':head,'frozen_tree_sha':tree,'frozen_authority_digest':population,'input_bundle_digest':population,'pair_digest':pair['pair_digest'],'wave_a_envelope_digest':pair_envelopes[WAVES[0]]['envelope_digest'],'wave_b_envelope_digest':pair_envelopes[WAVES[1]]['envelope_digest'],'output_dir':str(OUT),'final_claim_allowed':False},ensure_ascii=False,sort_keys=True))
    return 0

if __name__=='__main__':
    try: raise SystemExit(main())
    except Exception as e:
        print(json.dumps({'contract':'ONSURE_DESIGN_DISCOVERY_ENVELOPE_PREPARATION_V1','decision':'HOLD_NONFINAL','reason':f'{type(e).__name__}:{e}','final_claim_allowed':False},ensure_ascii=False,sort_keys=True))
        raise SystemExit(1)
