#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST_CLASSES = (
    "BuiltInDdSemanticEvaluatorsTest",
    "DdSemanticEvaluatorQualificationFixtureTest",
    "DdQualifiedRuntimeFactoryTest",
)


def sha256(path: Path) -> str | None:
    if not path.is_file(): return None
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_surefire() -> dict:
    import xml.etree.ElementTree as ET
    report_dir = ROOT / "target" / "surefire-reports"
    reports=[]; totals={"tests":0,"failures":0,"errors":0,"skipped":0}
    for klass in TEST_CLASSES:
        path=report_dir/f"TEST-kr.co.oruda.onsure.platform.{klass}.xml"
        if not path.is_file(): reports.append({"test_class":klass,"report_present":False,"report_sha256":None}); continue
        root=ET.fromstring(path.read_bytes())
        item={"test_class":klass,"report_present":True,"tests":int(root.attrib.get("tests",0)),"failures":int(root.attrib.get("failures",0)),"errors":int(root.attrib.get("errors",0)),"skipped":int(root.attrib.get("skipped",0)),"report_sha256":sha256(path)}
        reports.append(item)
        for key in totals: totals[key]+=item[key]
    return {"all_reports_present":all(r["report_present"] for r in reports),"reports":reports,**totals}


def main()->int:
    ap=argparse.ArgumentParser(); ap.add_argument("--run-id",required=True); ap.add_argument("--source-tree-sha",required=True); ap.add_argument("--started-at",required=True); ap.add_argument("--static-rc",type=int,required=True); ap.add_argument("--qualification-status-rc",type=int,required=True); ap.add_argument("--maven-rc",type=int,required=True); ap.add_argument("--output",required=True); args=ap.parse_args()
    out=Path(args.output); prefix=out.parent/args.run_id; surefire=parse_surefire()
    code_refs=[
        ROOT/"src/main/java/kr/co/oruda/onsure/platform/BuiltInDdSemanticEvaluators.java",
        ROOT/"src/main/java/kr/co/oruda/onsure/platform/DdEvidenceResolver.java",
        ROOT/"src/main/java/kr/co/oruda/onsure/platform/FileBackedDdEvidenceResolver.java",
        ROOT/"src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluator.java",
        ROOT/"src/main/java/kr/co/oruda/onsure/platform/DdSemanticEvaluatorRegistry.java",
        ROOT/"src/main/java/kr/co/oruda/onsure/platform/DdAssuranceOperationRuntime.java",
        ROOT/"src/main/java/kr/co/oruda/onsure/platform/DdQualifiedRuntimeFactory.java",
        ROOT/"contracts/dd-semantic-evaluator-qualification-fixture-plan.candidate.v1.json",
        ROOT/"contracts/dd-qualified-runtime-activation.candidate.v1.schema.json",
        ROOT/"contracts/dd-evidence-index.candidate.v1.schema.json",
    ]
    fixture_report=next((r for r in surefire["reports"] if r["test_class"]=="DdSemanticEvaluatorQualificationFixtureTest"),{})
    activation_report=next((r for r in surefire["reports"] if r["test_class"]=="DdQualifiedRuntimeFactoryTest"),{})
    exact_160=(fixture_report.get("report_present") is True and fixture_report.get("tests")==160 and fixture_report.get("failures")==0 and fixture_report.get("errors")==0 and fixture_report.get("skipped")==0)
    activation_ok=(activation_report.get("report_present") is True and activation_report.get("tests",0)>=5 and activation_report.get("failures")==0 and activation_report.get("errors")==0 and activation_report.get("skipped")==0)
    all_ok=(args.static_rc==0 and args.qualification_status_rc==0 and args.maven_rc==0 and surefire["all_reports_present"] and surefire["failures"]==0 and surefire["errors"]==0 and exact_160 and activation_ok)
    receipt={
      "contract":"ONSURE_DD_MANUAL_VERIFICATION_RECEIPT_V3","run_id":args.run_id,"source_tree_sha":args.source_tree_sha,"started_at":args.started_at,"completed_at":datetime.now(timezone.utc).isoformat().replace("+00:00","Z"),"execution_mode":"MANUAL_LOCAL_OR_APPROVED_EXECUTION_NODE_NO_GITHUB_ACTIONS",
      "steps":{
        "static_machine_definition":{"return_code":args.static_rc,"stdout_sha256":sha256(Path(str(prefix)+".static.stdout")),"stderr_sha256":sha256(Path(str(prefix)+".static.stderr"))},
        "qualification_status_validation":{"return_code":args.qualification_status_rc,"stdout_sha256":sha256(Path(str(prefix)+".qualification_status.stdout")),"stderr_sha256":sha256(Path(str(prefix)+".qualification_status.stderr"))},
        "maven_targeted_junit":{"return_code":args.maven_rc,"stdout_sha256":sha256(Path(str(prefix)+".maven.stdout")),"stderr_sha256":sha256(Path(str(prefix)+".maven.stderr")),"surefire":surefire}
      },
      "code_artifacts":[{"path":p.relative_to(ROOT).as_posix(),"sha256":sha256(p)} for p in code_refs],
      "claims":{"concrete_evaluator_code_materialized_count":40,"compile_and_targeted_junit_established":all_ok,"qualification_fixture_denominator":160,"qualification_fixture_mechanics_executed_count":160 if exact_160 else 0,"qualification_fixture_mechanics_established":exact_160,"receipt_backed_runtime_activation_mechanics_established":activation_ok,"independent_evaluator_qualification_count":0,"semantic_runtime_evidence_count":0,"independent_clean":False,"design_lock":False},
      "limitations":["Synthetic fixture mechanics are not independent evaluator qualification.","Receipt-backed activation mechanics do not make synthetic qualification authoritative.","Semantic runtime evidence remains separate and must use target/current evidence rather than synthetic mechanics fixtures."],
      "verdict":"PASS_NONFINAL_EXECUTION_MECHANICS_ONLY" if all_ok else "HOLD_NONFINAL","github_actions_authority":False,"final_claim_allowed":False
    }
    canonical=json.dumps(receipt,ensure_ascii=False,sort_keys=True,separators=(",",":")).encode(); receipt["receipt_digest"]=hashlib.sha256(canonical).hexdigest(); out.parent.mkdir(parents=True,exist_ok=True); out.write_text(json.dumps(receipt,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf-8"); return 0 if all_ok else 2

if __name__=="__main__": raise SystemExit(main())
