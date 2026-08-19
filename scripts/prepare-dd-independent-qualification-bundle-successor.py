#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,shutil,subprocess
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.onsure/dd-independent-qualification/frozen-bundle-successor'
BASE_CLASS='target/classes/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.class'
EXT_CLASS='target/classes/kr/co/oruda/onsure/platform/DesignGapDdSemanticEvaluators.class'
BASE_REGISTRY='contracts/dd-semantic-evaluator-registry.candidate.v1.json'
EXT_REGISTRY='contracts/dd-041-042-design-gap-extension.candidate.v1.json'
FIX_EXT='contracts/dd-semantic-evaluator-qualification-fixture-plan.extension-041-042.v1.json'
FILES=[
 'src/main/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.java',
 'src/main/java/kr/co/oruda/onsure/platform/DesignGapDdSemanticEvaluators.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdEvidenceResolver.java',
 'src/main/java/kr/co/oruda/onsure/platform/FileBackedDdEvidenceResolver.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluator.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorRegistry.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdAssuranceOperationRuntime.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdQualifiedRuntimeFactory.java',
 'src/main/java/kr/co/oruda/onsure/platform/DdAssuranceContractValidator.java',
 'src/test/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluatorsTest.java',
 'src/test/java/kr/co/oruda/onsure/platform/DesignGapDdSemanticEvaluatorsTest.java',
 'src/test/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorQualificationFixtureTest.java',
 'src/test/java/kr/co/oruda/onsure/platform/DdQualifiedRuntimeFactoryTest.java',
 BASE_REGISTRY,EXT_REGISTRY,
 'contracts/dd-semantic-evaluator-qualification-fixture-plan.candidate.v1.json',FIX_EXT,
 'contracts/dd-semantic-evaluator-qualification.candidate.v1.schema.json',
 'contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json',
 'contracts/dd-assurance-request.candidate.v1.schema.json',
 'contracts/dd-assurance-result.candidate.v1.schema.json',
 'contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json',
 'contracts/design-discovery-independence-control-graph.v1.json',
 'contracts/design-discovery-p1-novelty-policy.v1.json',
 'scripts/validate-dd-denominator-42.py',
 'scripts/validate-dd-semantic-evaluator-qualifications.py',
 BASE_CLASS,EXT_CLASS]

def sha256(p:Path)->str:return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args)->str:return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def canonical_digest(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    manual_ref=os.environ.get('ONSURE_DD_MANUAL_VERIFICATION_RECEIPT','').strip()
    reasons=[]
    if not manual_ref:
        reasons.append('CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_NOT_SUPPLIED'); manual={}; manual_path=None
    else:
        manual_path=Path(manual_ref); manual_path=manual_path if manual_path.is_absolute() else ROOT/manual_path
        if not manual_path.is_file(): reasons.append('CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_MISSING'); manual={}
        else: manual=json.loads(manual_path.read_text(encoding='utf-8'))
    commit=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}')
    if manual:
        claims=manual.get('claims') or {}
        if manual.get('receipt_digest')!=canonical_digest(manual): reasons.append('DD_MANUAL_RECEIPT_DIGEST_INVALID')
        if manual.get('source_commit_sha')!=commit or manual.get('source_tree_sha')!=tree: reasons.append('DD_MANUAL_RECEIPT_SUBJECT_MISMATCH')
        if manual.get('verdict')!='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY': reasons.append('DD_MANUAL_RECEIPT_NOT_PASS')
        if claims.get('dd_authorized_route_execution_mechanics_count')!=42: reasons.append('DD_ROUTE_MECHANICS_NOT_42_OF_42')
        if claims.get('dd_schema_validator_execution_mechanics_count')!=42: reasons.append('DD_SCHEMA_MECHANICS_NOT_42_OF_42')
        if claims.get('qualification_fixture_mechanics_executed_count')!=168: reasons.append('DD_168_FIXTURE_MECHANICS_NOT_PROVEN')
    missing=[f for f in FILES if not (ROOT/f).is_file()]
    if missing: reasons.append('QUALIFICATION_BUNDLE_REQUIRED_FILES_MISSING')
    if reasons:
        print(json.dumps({'decision':'HOLD_NONFINAL','blocking_reasons':sorted(set(reasons)),'missing':missing,'required_dd_count':42,'required_fixture_count':168,'final_claim_allowed':False},sort_keys=True)); return 44
    if OUT.exists(): shutil.rmtree(OUT)
    bundle=OUT/'files'; bundle.mkdir(parents=True); rows=[]
    for rel in FILES:
        src=ROOT/rel; dst=bundle/rel; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,dst); rows.append({'path':rel,'sha256':sha256(src),'size':src.stat().st_size})
    shutil.copy2(manual_path,OUT/'manual-verification-receipt.json')
    population=hashlib.sha256('\n'.join(f"{r['path']}:{r['sha256']}" for r in sorted(rows,key=lambda x:x['path'])).encode()).hexdigest()
    base_reg_sha=sha256(ROOT/BASE_REGISTRY); ext_reg_sha=sha256(ROOT/EXT_REGISTRY)
    obligation_population_sha=hashlib.sha256(f'{base_reg_sha}:{ext_reg_sha}'.encode()).hexdigest()
    manifest={
      'contract':'ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V3',
      'generated_at':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),
      'source_commit_sha':commit,'source_tree_sha':tree,
      'artifact_population_digest':population,
      'base_evaluator_artifact_path':BASE_CLASS,'base_evaluator_artifact_sha256':sha256(ROOT/BASE_CLASS),
      'extension_evaluator_artifact_path':EXT_CLASS,'extension_evaluator_artifact_sha256':sha256(ROOT/EXT_CLASS),
      'base_obligation_registry_sha256':base_reg_sha,'extension_obligation_registry_sha256':ext_reg_sha,
      'obligation_registry_population_sha256':obligation_population_sha,
      'manual_verification_receipt_digest':manual['receipt_digest'],
      'dd_denominator':42,'qualification_fixture_denominator':168,'files':rows,
      'qualification_decision':'NOT_PERFORMED','github_actions_authority':False,'final_claim_allowed':False}
    (OUT/'bundle-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'decision':'READY_FOR_INDEPENDENT_QUALIFICATION_NONFINAL','source_commit_sha':commit,'source_tree_sha':tree,'dd_count':42,'fixture_count':168,'artifact_population_digest':population,'final_claim_allowed':False},sort_keys=True)); return 0
if __name__=='__main__': raise SystemExit(main())
