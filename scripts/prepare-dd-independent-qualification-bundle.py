#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,shutil,subprocess
from datetime import datetime,timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.onsure/dd-independent-qualification/frozen-bundle'
EVALUATOR_CLASS='target/classes/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.class'
FILES=[
 'src/main/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.java','src/main/java/kr/co/oruda/onsure/platform/DdEvidenceResolver.java','src/main/java/kr/co/oruda/onsure/platform/FileBackedDdEvidenceResolver.java','src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluator.java','src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorRegistry.java','src/main/java/kr/co/oruda/onsure/platform/DdAssuranceOperationRuntime.java','src/main/java/kr/co/oruda/onsure/platform/DdQualifiedRuntimeFactory.java','src/main/java/kr/co/oruda/onsure/platform/DdAssuranceContractValidator.java','src/test/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluatorsTest.java','src/test/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorQualificationFixtureTest.java','src/test/java/kr/co/oruda/onsure/platform/DdQualifiedRuntimeFactoryTest.java','contracts/dd-semantic-evaluator-registry.candidate.v1.json','contracts/dd-semantic-evaluator-qualification-fixture-plan.candidate.v1.json','contracts/dd-semantic-evaluator-qualification.candidate.v1.schema.json','contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json','contracts/dd-assurance-request.candidate.v1.schema.json','contracts/dd-assurance-result.candidate.v1.schema.json','contracts/dd-machine-fixture-catalog.candidate.v1.json','contracts/post-final-target-dd-policy-authority-floor.candidate.v1.json','scripts/validate-dd-machine-definitions.py','scripts/validate-dd-semantic-evaluator-qualifications.py','scripts/run-dd-semantic-evaluator-manual-verification.sh',EVALUATOR_CLASS]

def sha256(p:Path)->str:return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args)->str:return subprocess.check_output(['git',*args],cwd=ROOT,text=True).strip()
def canonical_digest(d:dict)->str:
    x=dict(d); x.pop('receipt_digest',None)
    return hashlib.sha256(json.dumps(x,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def main()->int:
    manual_ref=os.environ.get('ONSURE_DD_MANUAL_VERIFICATION_RECEIPT','').strip()
    if not manual_ref:
        print(json.dumps({'decision':'HOLD_NONFINAL','blocking_reasons':['CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_NOT_SUPPLIED'],'final_claim_allowed':False})); return 44
    manual_path=Path(manual_ref); manual_path=manual_path if manual_path.is_absolute() else ROOT/manual_path
    if not manual_path.is_file():
        print(json.dumps({'decision':'HOLD_NONFINAL','blocking_reasons':['CURRENT_HEAD_DD_MANUAL_VERIFICATION_RECEIPT_MISSING'],'final_claim_allowed':False})); return 44
    manual=json.loads(manual_path.read_text(encoding='utf-8')); commit=git('rev-parse','HEAD'); tree=git('rev-parse','HEAD^{tree}'); claims=manual.get('claims') or {}; reasons=[]
    if manual.get('contract')!='ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V5': reasons.append('DD_MANUAL_RECEIPT_CONTRACT_NOT_V5')
    if manual.get('receipt_digest')!=canonical_digest(manual): reasons.append('DD_MANUAL_RECEIPT_DIGEST_INVALID')
    if manual.get('source_commit_sha')!=commit: reasons.append('DD_MANUAL_RECEIPT_COMMIT_MISMATCH')
    if manual.get('source_tree_sha')!=tree: reasons.append('DD_MANUAL_RECEIPT_TREE_MISMATCH')
    if manual.get('verdict')!='PASS_NONFINAL_EXECUTION_MECHANICS_ONLY': reasons.append('DD_MANUAL_RECEIPT_NOT_PASS')
    if not claims.get('compile_and_targeted_junit_established'): reasons.append('CURRENT_HEAD_JAVA_JUNIT_NOT_PROVEN')
    if claims.get('dd_authorized_route_execution_mechanics_count')!=40: reasons.append('DD_ROUTE_MECHANICS_NOT_40_OF_40')
    if claims.get('dd_schema_validator_execution_mechanics_count')!=40: reasons.append('DD_SCHEMA_MECHANICS_NOT_40_OF_40')
    if claims.get('qualification_fixture_mechanics_executed_count')!=160: reasons.append('DD_160_FIXTURE_MECHANICS_NOT_PROVEN')
    if not claims.get('receipt_backed_runtime_activation_mechanics_established'): reasons.append('DD_RUNTIME_ACTIVATION_MECHANICS_NOT_PROVEN')
    compiled={x.get('path'):x.get('sha256') for x in manual.get('compiled_artifacts',[]) if isinstance(x,dict)}; evaluator_class=ROOT/EVALUATOR_CLASS; evaluator_artifact_sha=sha256(evaluator_class) if evaluator_class.is_file() else None
    if not evaluator_artifact_sha or compiled.get(EVALUATOR_CLASS)!=evaluator_artifact_sha: reasons.append('DD_EVALUATOR_COMPILED_ARTIFACT_BINDING_INVALID')
    missing=[f for f in FILES if not (ROOT/f).is_file()]
    if missing: reasons.append('QUALIFICATION_BUNDLE_REQUIRED_FILES_MISSING')
    if reasons:
        print(json.dumps({'decision':'HOLD_NONFINAL','blocking_reasons':sorted(set(reasons)),'missing':missing,'final_claim_allowed':False},sort_keys=True)); return 44
    if OUT.exists():shutil.rmtree(OUT)
    bundle=OUT/'files'; bundle.mkdir(parents=True); rows=[]
    for rel in FILES:
        src=ROOT/rel; dst=bundle/rel; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,dst); rows.append({'path':rel,'sha256':sha256(src),'size':src.stat().st_size})
    manual_dst=OUT/'manual-verification-receipt.json'; shutil.copy2(manual_path,manual_dst)
    population=hashlib.sha256('\n'.join(f"{r['path']}:{r['sha256']}" for r in sorted(rows,key=lambda x:x['path'])).encode()).hexdigest(); obligation_sha=sha256(ROOT/'contracts/dd-semantic-evaluator-registry.candidate.v1.json')
    manifest={'contract':'ONSURE_DD_INDEPENDENT_QUALIFICATION_FROZEN_BUNDLE_V2','generated_at':datetime.now(timezone.utc).isoformat().replace('+00:00','Z'),'source_commit_sha':commit,'source_tree_sha':tree,'artifact_population_digest':population,'evaluator_artifact_path':EVALUATOR_CLASS,'evaluator_artifact_sha256':evaluator_artifact_sha,'obligation_registry_sha256':obligation_sha,'manual_verification_receipt_digest':manual['receipt_digest'],'dd_denominator':40,'qualification_fixture_denominator':160,'files':rows,'reviewer_constraints':['Reviewer must not be the evaluator author or target claim author.','Reviewer must execute positive, negative, recovery and adversarial planned fixture for every DD.','Synthetic mechanics receipts are prerequisite evidence only and cannot themselves be qualification evidence.','Reviewer must independently evaluate semantic correctness of required facts, safe floors and positive oracles, not only test pass/fail.','Every qualified DD requires a separate receipt conforming to dd-semantic-evaluator-qualification.candidate.v1.schema.json.','Every receipt must bind evaluator_artifact_sha256 and obligation_registry_sha256 to this manifest.','Changes to any frozen file, compiled evaluator artifact, obligation registry or artifact population invalidate this qualified subject.','Later evidence-only execution commits do not silently redefine this qualified subject; they must reference qualified_subject_tree_sha explicitly.','GitHub Actions are not qualification authority.'],'qualification_decision':'NOT_PERFORMED','github_actions_authority':False,'final_claim_allowed':False}
    (OUT/'bundle-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')
    print(json.dumps({'decision':'READY_FOR_INDEPENDENT_QUALIFICATION_NONFINAL','source_commit_sha':commit,'source_tree_sha':tree,'artifact_population_digest':population,'evaluator_artifact_sha256':evaluator_artifact_sha,'file_count':len(rows),'dd_count':40,'fixture_count':160,'final_claim_allowed':False},ensure_ascii=False,sort_keys=True)); return 0
if __name__=='__main__': raise SystemExit(main())
