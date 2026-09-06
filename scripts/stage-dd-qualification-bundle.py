#!/usr/bin/env python3
from __future__ import annotations
import argparse,hashlib,json,shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.onsure/dd-independent-qualification/frozen-bundle'

def sha(p:Path)->str:return hashlib.sha256(p.read_bytes()).hexdigest()

def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument('--source',required=True); args=ap.parse_args()
    src=Path(args.source).expanduser().resolve()
    manifest=src/'bundle-manifest.json' if src.is_dir() else src
    if not manifest.is_file(): raise SystemExit('DD_QUALIFICATION_BUNDLE_MANIFEST_MISSING')
    d=json.loads(manifest.read_text(encoding='utf-8'))
    if d.get('contract')!='ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V2': raise SystemExit('DD_QUALIFICATION_BUNDLE_CONTRACT_MISMATCH')
    for f,n in (('source_commit_sha',40),('source_tree_sha',40),('artifact_population_digest',64),('evaluator_artifact_sha256',64),('obligation_registry_sha256',64),('manual_verification_receipt_digest',64)):
        v=str(d.get(f,''))
        if len(v)!=n or any(c not in '0123456789abcdef' for c in v): raise SystemExit('DD_QUALIFICATION_BUNDLE_FIELD_INVALID:'+f)
    if d.get('dd_denominator')!=40 or d.get('qualification_fixture_denominator')!=160 or d.get('final_claim_allowed') is not False: raise SystemExit('DD_QUALIFICATION_BUNDLE_DENOMINATOR_INVALID')
    # Preserve the original manifest bytes; do not regenerate or rewrite the qualified subject.
    OUT.mkdir(parents=True,exist_ok=True)
    shutil.copy2(manifest,OUT/'bundle-manifest.json')
    # Optional frozen file population is copied when a directory bundle is supplied.
    source_dir=manifest.parent/'files'
    if source_dir.is_dir():
        target=OUT/'files'
        if target.exists(): shutil.rmtree(target)
        shutil.copytree(source_dir,target)
    manual=manifest.parent/'manual-verification-receipt.json'
    if manual.is_file(): shutil.copy2(manual,OUT/'manual-verification-receipt.json')
    print(json.dumps({'contract':'ONSURE_DD_QUALIFICATION_BUNDLE_STAGE_V1','source_tree_sha':d['source_tree_sha'],'artifact_population_digest':d['artifact_population_digest'],'manifest_sha256':sha(OUT/'bundle-manifest.json'),'decision':'STAGED_IMMUTABLE_NONFINAL','final_claim_allowed':False},sort_keys=True)); return 0
if __name__=='__main__': raise SystemExit(main())
