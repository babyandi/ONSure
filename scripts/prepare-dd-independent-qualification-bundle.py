#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.onsure/dd-independent-qualification/frozen-bundle'
FILES=[
 'src/main/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdEvidenceResolver.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluator.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorRegistry.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdAssuranceOperationRuntime.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdAssuranceContractValidator.java',
 'src/test/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluatorsTest.java',
 'src/test/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorQualificationFixtureTest.java',
 'contracts/dd-semantic-evaluator-registry.candidate.v1.json',
 'contracts/dd-semantic-evaluator-qualification-fixture-plan.candidate.v1.json',
 'contracts/dd-semantic-evaluator-qualification.candidate.v1.schema.json',
 'contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json',
 'contracts/dd-assurance-request.candidate.v1.schema.json',
 'contracts/dd-assurance-result.candidate.v1.schema.json',
 'contracts/dd-machine-fixture-catalog.candidate.v1.json',
 'contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json',
 'scripts/validate-dd-machine-definitions.py',
 'scripts/validate-dd-semantic-evaluator-qualifications.py',
 'scripts/run-dd-semantic-evaluator-manual-verification.sh',
]

def sha256(p:Path)->str: return hashlib.sha256(p.read_bytes()).hexdigest()
def head()->str: return subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()

def main()->int:
    missing=[f for f in FILES if not (ROOT/f).is_file()]
    if missing:
        print(json.dumps({'decision':'HOLD_NONFINAL','missing':missing,'final_claim_allowed':False})); return 44
    if OUT.exists(): shutil.rmtree(OUT)
    bundle=OUT/'files'; bundle.mkdir(parents=True)
    rows=[]
    for rel in FILES:
        src=ROOT/rel; dst=bundle/rel; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,dst)
        rows.append({'path':rel,'sha256':sha256(src),'size':src.stat().st_size})
    tree=head()
    population=hashlib.sha256('\n'.join(f"{r['path']}:{r['sha256']}" for r in sorted(rows,key=lambda x:x['path'])).encode()).hexdigest()
    manifest={
      'contract':'ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V1',
      'generated_at':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),
      'source_tree_sha':tree,
      'artifact_population_digest':population,
      'dd_denominator':40,
      'qualification_fixture_denominator':160,
      'files':rows,
      'reviewer_constraints':[
        'Reviewer must not be the evaluator author or target claim author.',
        'Reviewer must execute positive, negative, recovery and adversarial planned fixture for every DD.',
        'Synthetic mechanics receipts are inputs only and cannot themselves be qualification evidence.',
        'Reviewer must independently evaluate semantic correctness of required facts, safe floors and positive oracles, not only test pass/fail.',
        'Every qualified DD requires a separate receipt conforming to dd-semantic-evaluator-qualification.candidate.v1.schema.json.',
        'Any source-tree or artifact-population change invalidates this frozen bundle.',
        'GitHub Actions are not qualification authority.'
      ],
      'qualification_decision':'NOT_PERFORMED',
      'github_actions_authority':False,
      'final_claim_allowed':False
    }
    (OUT/'bundle-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'decision':'READY_FOR_INDEPENDENT_QUALIFICATION_NONFINAL','source_tree_sha':tree,'artifact_population_digest':population,'file_count':len(rows),'dd_count':40,'fixture_count':160,'final_claim_allowed':False},ensure_ascii=False,sort_keys=True)); return 0

if __name__=='__main__': raise SystemExit(main())
