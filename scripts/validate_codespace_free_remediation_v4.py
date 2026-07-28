#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import py_compile
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
COUNT_AUTHORITY="contracts/omission-failure-injection-counts.v1.json"
REQUIRED=[
 "contracts/codespace-free-remediation-plan.v1.json","contracts/product-process-lineage.v1.json",COUNT_AUTHORITY,
 "contracts/legacy-product-subrequirement-authority.v1.json","contracts/final-acceptance-source-registry.v1.json","contracts/workflow-operation-registry.v1.json",
 "status/design-capability-coverage.v2.json","status/product-subrequirement-coverage.v1.json","status/mvp-acceptance-coverage.v1.json",
 "status/final-product-requirement-coverage.v1.json","status/final-acceptance-coverage.v1.json","status/omission-detection-status.v1.json","status/verification-status.v1.json","status/remaining-work-register.v1.json",
 "scripts/onsure-local-gate.sh","scripts/onsure-one-shot.sh","scripts/onsure-final-stage.sh",
 "scripts/validate-legacy-product-subrequirements.py","scripts/validate-mvp-acceptance-coverage.py","scripts/validate-final-product-requirements.py","scripts/validate-final-acceptance-coverage.py",
 "scripts/validate-workflow-surface-parity.py","scripts/validate-critical-callpaths.py","scripts/validate-status-consistency.py","scripts/validate-verification-claims.py",
 "src/main/java/io/onsure/platform/ApprovalAuthorityPaths.java","src/main/java/io/onsure/assurance/LocalKeyRegistry.java","src/main/java/io/onsure/platform/BoundedProcessRunner.java","src/main/java/io/onsure/platform/ExecutionPlanActionPolicy.java"
]
COMMANDS=[
 ([sys.executable,"scripts/check-module-boundaries.py"],"ONSURE_MODULE_BOUNDARY_STATIC_PASS"),
 ([sys.executable,"scripts/validate-repository-contracts.py"],"ONSURE_REPOSITORY_CONTRACTS_PASS"),
 ([sys.executable,"scripts/validate-structured-contracts.py"],"ONSURE_STRUCTURED_CONTRACTS_"),
 ([sys.executable,"scripts/validate-atomic-requirements.py","--self-test"],'"decision": "PASS"'),
 ([sys.executable,"scripts/validate-design-coverage.py","--matrix","status/design-capability-coverage.v2.json","--root",".","--self-test"],'"decision": "PASS"'),
 ([sys.executable,"scripts/validate-legacy-product-subrequirements.py","--self-test"],"ONSURE_LEGACY_PRODUCT_SUBREQUIREMENT_GATE_PASS"),
 ([sys.executable,"scripts/validate-mvp-acceptance-coverage.py","--self-test"],"ONSURE_MVP_ACCEPTANCE_GATE_PASS"),
 ([sys.executable,"scripts/validate-final-product-requirements.py","--self-test"],"ONSURE_FINAL_PRODUCT_REQUIREMENT_GATE_PASS"),
 ([sys.executable,"scripts/validate-final-acceptance-coverage.py","--self-test"],"ONSURE_FINAL_ACCEPTANCE_GATE_PASS"),
 ([sys.executable,"scripts/validate-workflow-surface-parity.py","--self-test"],"ONSURE_WORKFLOW_SURFACE_PARITY_PASS"),
 ([sys.executable,"scripts/validate-critical-callpaths.py","--self-test"],"ONSURE_CRITICAL_CALLPATH_PASS"),
 ([sys.executable,"scripts/validate-status-consistency.py"],"ONSURE_STATUS_CONSISTENCY_PASS"),
 ([sys.executable,"scripts/validate-ci-boundary.py"],"ONSURE_AUTOMATION_BOUNDARY_PASS"),
 ([sys.executable,"scripts/validate-verification-claims.py"],"ONSURE_VERIFICATION_CLAIM_AUDIT_PASS"),
 (["bash","scripts/check-shell-syntax.sh"],"ONSURE_SHELL_SYNTAX_PASS")
]
ASSERTIONS={
 "scripts/onsure-local-gate.sh":["validate-legacy-product-subrequirements.py --self-test","validate-final-product-requirements.py --self-test","validate-final-acceptance-coverage.py --self-test",COUNT_AUTHORITY,'"github_actions":"DISABLED"'],
 "scripts/onsure-one-shot.sh":["LOCAL_GATE_AUTHORITY","legacy_product_decomposition","final_product_requirement_contract","final_acceptance_contract",COUNT_AUTHORITY],
 "src/main/java/io/onsure/platform/ApprovalAuthorityPaths.java":["APPROVAL_AUTHORITY_MUST_BE_OUTSIDE_TARGET_WORKSPACE","APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED","discoverForContainedPath"],
 "src/main/java/io/onsure/assurance/LocalKeyRegistry.java":["PUBLIC_KEY_OUTSIDE_AUTHORITY_ROOT","ExclusiveFileLock.call(lockFile","ATOMIC_MOVE"],
 "src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java":["approvalAuthority.rejectRequestOverrides","ApprovedExecutionPlanBundle","project.register-target","plan.generate"],
 "src/main/java/io/onsure/platform/ExecutionPlanApprovalService.java":["verifyApprovedPlanBundle","EXECUTION_PLAN_CONSUMED_APPROVAL_INVALID"],
 "src/main/java/io/onsure/platform/ValidationEngine.java":["APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED","ExecutionPlanActionPolicy.notApproved"],
 "src/main/java/io/onsure/platform/ProgramLearningService.java":["BoundedProcessRunner.run"],
 "src/main/java/io/onsure/platform/SourceReferenceBinding.java":["BoundedProcessRunner.run"],
 "src/main/java/io/onsure/platform/ImprovementWorkflowService.java":["BoundedProcessRunner.run"],
 "src/main/java/io/onsure/platform/GitWorkflowService.java":["BoundedProcessRunner.run","GIT_DELIVERY_APPROVAL_EXPIRED","discoverForContainedPath"]
}
FORBIDDEN={
 "src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java":['inputPath(request, "trusted_key_registry"','inputPath(request, "approval_key_registry"','outputPath(request, "approval_replay_ledger"','outputPath(request, "verification_replay_ledger"'],
 "src/main/java/io/onsure/platform/ProgramLearningService.java":["getInputStream().readAllBytes()"],
 "src/main/java/io/onsure/platform/SourceReferenceBinding.java":["getInputStream().readAllBytes()"],
 "src/main/java/io/onsure/platform/ImprovementWorkflowService.java":["ProcessBuilder builder","process.waitFor("],
 "src/main/java/io/onsure/platform/GitWorkflowService.java":["ProcessBuilder builder","process.waitFor("]
}


def main()->int:
 errors=[]
 for relative in REQUIRED:
  if not (ROOT/relative).is_file():errors.append(f"MISSING:{relative}")
 workflow_root=ROOT/".github/workflows"
 if workflow_root.exists():
  for path in list(workflow_root.glob("*.yml"))+list(workflow_root.glob("*.yaml")):errors.append(f"GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:{path.name}")
 for path in ROOT.rglob("*.py"):
  if ".onsure" not in path.parts:
   try:py_compile.compile(str(path),doraise=True)
   except Exception as exc:errors.append(f"PYTHON_INVALID:{path.relative_to(ROOT)}:{exc}")
 for relative in REQUIRED:
  path=ROOT/relative
  if not path.is_file():continue
  try:
   if path.suffix==".json":json.loads(path.read_text(encoding="utf-8"))
   if path.name=="pom.xml" or path.suffix==".xml":ET.parse(path)
  except Exception as exc:errors.append(f"INVALID:{relative}:{type(exc).__name__}:{exc}")
 for relative,tokens in ASSERTIONS.items():
  text=(ROOT/relative).read_text(encoding="utf-8",errors="replace") if (ROOT/relative).is_file() else ""
  for token in tokens:
   if token not in text:errors.append(f"SOURCE_ASSERTION_MISSING:{relative}:{token}")
 for relative,tokens in FORBIDDEN.items():
  text=(ROOT/relative).read_text(encoding="utf-8",errors="replace") if (ROOT/relative).is_file() else ""
  for token in tokens:
   if token in text:errors.append(f"FORBIDDEN_SOURCE_TOKEN:{relative}:{token}")
 for command,marker in COMMANDS:
  result=subprocess.run(command,cwd=ROOT,text=True,capture_output=True,check=False);combined=result.stdout+result.stderr
  if result.returncode!=0 or marker not in combined:errors.append(f"COMMAND_FAIL:{' '.join(command)}:{result.returncode}:{combined[-2800:]}")
 counts=json.loads((ROOT/COUNT_AUTHORITY).read_text(encoding="utf-8"));values=counts.get("counts",{});total=counts.get("total")
 if counts.get("contract")!="ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1" or total!=sum(values.values()):errors.append("FAILURE_COUNT_AUTHORITY_INVALID")
 plan=json.loads((ROOT/"contracts/codespace-free-remediation-plan.v1.json").read_text(encoding="utf-8"))
 if plan.get("final_single_command")!="bash scripts/onsure-final-stage.sh --profile core":errors.append("FINAL_SINGLE_COMMAND_MISMATCH")
 if plan.get("local_gate_command")!="bash scripts/onsure-local-gate.sh --mode full --profile core":errors.append("LOCAL_GATE_COMMAND_MISMATCH")
 if plan.get("execution_policy",{}).get("github_actions")!="DISABLED_BY_USER":errors.append("ACTIONS_POLICY_NOT_DISABLED")
 if any(plan.get(field) is not False for field in ("final_lock_allowed","production_go","commercial_go")):errors.append("UNSAFE_GO_FLAG")
 report={"contract":"ONSURE_CODESPACE_FREE_STATIC_GATE_V30","decision":"PASS" if not errors else "FAIL","errors":errors,"legacy_product_requirements":43,"legacy_mvp_acceptance_items":11,"final_product_requirements":22,"final_acceptance_items":61,"workflow_operations":40,"failure_injection_authority":COUNT_AUTHORITY,"registered_failure_injections":total,"runtime_execution":"NOT_RUN_BY_STATIC_GATE","github_actions":"DISABLED_BY_USER","final_claim_allowed":False}
 print(json.dumps(report,indent=2,sort_keys=True))
 if errors:print("ONSURE_CODESPACE_FREE_STATIC_GATE_FAIL",file=sys.stderr);return 1
 print("ONSURE_CODESPACE_FREE_STATIC_GATE_PASS");return 0


if __name__=="__main__":raise SystemExit(main())
