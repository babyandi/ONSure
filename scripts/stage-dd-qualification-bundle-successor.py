#!/usr/bin/env python3
from __future__ import annotations
import argparse,json,shutil,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];DEST=ROOT/'.onsure/dd-independent-qualification/frozen-bundle-successor'
def main()->int:
    ap=argparse.ArgumentParser();ap.add_argument('--source',required=True);args=ap.parse_args();src=Path(args.source).expanduser();src=src if src.is_absolute() else ROOT/src
    manifest=src/'bundle-manifest.json';reasons=[]
    if not src.is_dir() or not manifest.is_file():reasons.append('SUCCESSOR_QUALIFICATION_BUNDLE_SOURCE_INVALID');doc={}
    else:doc=json.loads(manifest.read_text(encoding='utf-8'))
    if doc:
        if doc.get('contract')!='ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V4':reasons.append('SUCCESSOR_QUALIFICATION_BUNDLE_NOT_V4')
        if doc.get('dd_denominator')!=42:reasons.append('SUCCESSOR_QUALIFICATION_BUNDLE_DD_NOT_42')
        if doc.get('qualification_fixture_denominator')!=173:reasons.append('SUCCESSOR_QUALIFICATION_BUNDLE_FIXTURE_NOT_173')
        for key in ('source_commit_sha','source_tree_sha','base_evaluator_artifact_sha256','extension_evaluator_artifact_sha256','obligation_registry_population_sha256','fixture_authority_population_sha256'):
            if not doc.get(key):reasons.append('SUCCESSOR_QUALIFICATION_BUNDLE_FIELD_MISSING:'+key)
    if reasons:
        print(json.dumps({'contract':'ONSURE_DD_SUCCESSOR_QUALIFICATION_BUNDLE_STAGE_V1','blocking_reasons':reasons,'decision':'HOLD_NONFINAL','final_claim_allowed':False},sort_keys=True));return 44
    if DEST.exists():shutil.rmtree(DEST)
    shutil.copytree(src,DEST)
    out={'contract':'ONSURE_DD_SUCCESSOR_QUALIFICATION_BUNDLE_STAGE_V1','qualified_subject_commit_sha':doc['source_commit_sha'],'qualified_subject_tree_sha':doc['source_tree_sha'],'dd_count':42,'fixture_count':173,'destination':str(DEST),'decision':'STAGED_NONFINAL','final_claim_allowed':False}
    print(json.dumps(out,sort_keys=True));return 0
if __name__=='__main__':raise SystemExit(main())
