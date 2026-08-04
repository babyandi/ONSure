#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import py_compile
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
COUNT_AUTHORITY = "contracts/omission-failure-injection-counts.v1.json"
REQUIRED = [
    "contracts/codespace-free-remediation-plan.v1.json",
    "contracts/product-process-lineage.v1.json",
    COUNT_AUTHORITY,
    "status/design-capability-coverage.v2.json",
    "status/product-subrequirement-coverage.v1.json",
    "status/mvp-acceptance-coverage.v1.json",
    "status/omission-detection-status.v1.json",
    "status/verification-status.v1.json",
    "status/remaining-work-register.v1.json",
    "pom-modular.xml",
    "scripts/onsure-local-gate.sh",
    "scripts/onsure-one-shot.sh",
    "scripts/onsure-final-stage.sh",
    "scripts/validate-product-subrequirements.py",
    "scripts/validate-mvp-acceptance-coverage.py",
    "scripts/validate-mvp-status-consistency.py",
    "scripts/validate-workflow-surface-parity.py",
    "scripts/validate-critical-callpaths.py",
    "scripts/validate-status-consistency.py",
    "scripts/validate_status_consistency_v2.py",
    "scripts/validate-verification-claims.py",
    "scripts/validate_verification_claims_v2.py",
    "scripts/validate_codespace_free_remediation_v2.py",
    "modules/onsure-core/src/main/java/io/onsure/platform/ApprovalAuthorityPaths.java",
    "modules/onsure-core/src/main/java/io/onsure/assurance/LocalKeyRegistry.java",
    "modules/onsure-core/src/main/java/io/onsure/platform/BoundedProcessRunner.java",
    "modules/onsure-core/src/main/java/io/onsure/platform/ExecutionPlanActionPolicy.java",
    "src/test/java/io/onsure/platform/ApprovalAuthorityPathsTest.java",
    "src/test/java/io/onsure/platform/BoundedProcessRunnerTest.java",
    "src/test/java/io/onsure/platform/ExecutionPlanBundleEntryTest.java",
]
ASSERTIONS = {
    "scripts/onsure-local-gate.sh": [
        "validate-product-subrequirements.py --self-test",
        "validate-mvp-acceptance-coverage.py --self-test",
        "validate-mvp-status-consistency.py",
        "validate-workflow-surface-parity.py --self-test",
        "validate-critical-callpaths.py --self-test",
        COUNT_AUTHORITY,
    ],
    "scripts/onsure-one-shot.sh": ["LOCAL_GATE_AUTHORITY", "local_gate_authority", COUNT_AUTHORITY],
    "modules/onsure-core/src/main/java/io/onsure/platform/ApprovalAuthorityPaths.java": [
        "AUTHORITY_BASE_PROPERTY", "APPROVAL_AUTHORITY_MUST_BE_OUTSIDE_TARGET_WORKSPACE",
        "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED", "discoverForContainedPath",
    ],
    "modules/onsure-core/src/main/java/io/onsure/assurance/LocalKeyRegistry.java": [
        "PUBLIC_KEY_OUTSIDE_AUTHORITY_ROOT", "ExclusiveFileLock.call(lockFile", "ATOMIC_MOVE",
    ],
    "modules/onsure-core/src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java": [
        "approvalAuthority.rejectRequestOverrides", "approvalAuthority.requireTrustedKeyRegistry",
        "ApprovedExecutionPlanBundle", "project.register-target", "plan.generate",
    ],
    "modules/onsure-core/src/main/java/io/onsure/platform/ExecutionPlanApprovalService.java": [
        "verifyApprovedPlanBundle", "EXECUTION_PLAN_CONSUMED_APPROVAL_INVALID",
    ],
    "modules/onsure-core/src/main/java/io/onsure/platform/ValidationEngine.java": [
        "APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED", "ExecutionPlanActionPolicy.notApproved",
    ],
    "modules/onsure-core/src/main/java/io/onsure/platform/ProgramLearningService.java": ["BoundedProcessRunner.run"],
    "modules/onsure-core/src/main/java/io/onsure/platform/SourceReferenceBinding.java": ["BoundedProcessRunner.run"],
    "modules/onsure-core/src/main/java/io/onsure/platform/ImprovementWorkflowService.java": ["BoundedProcessRunner.run"],
    "modules/onsure-core/src/main/java/io/onsure/platform/GitWorkflowService.java": [
        "BoundedProcessRunner.run", "GIT_DELIVERY_APPROVAL_EXPIRED", "discoverForContainedPath",
    ],
}
FORBIDDEN = {
    "modules/onsure-core/src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java": [
        'inputPath(request, "trusted_key_registry"',
        'inputPath(request, "approval_key_registry"',
        'outputPath(request, "approval_replay_ledger"',
        'outputPath(request, "verification_replay_ledger"',
    ],
    "modules/onsure-core/src/main/java/io/onsure/platform/ProgramLearningService.java": ["getInputStream().readAllBytes()"],
    "modules/onsure-core/src/main/java/io/onsure/platform/SourceReferenceBinding.java": ["getInputStream().readAllBytes()"],
    "modules/onsure-core/src/main/java/io/onsure/platform/ImprovementWorkflowService.java": ["ProcessBuilder builder", "process.waitFor("],
    "modules/onsure-core/src/main/java/io/onsure/platform/GitWorkflowService.java": ["ProcessBuilder builder", "process.waitFor("],
}
COMMANDS = [
    ([sys.executable, "scripts/check-module-boundaries.py"], "ONSURE_MODULE_BOUNDARY_STATIC_PASS"),
    ([sys.executable, "scripts/validate-repository-contracts.py"], "ONSURE_REPOSITORY_CONTRACTS_PASS"),
    ([sys.executable, "scripts/validate-structured-contracts.py"], "ONSURE_STRUCTURED_CONTRACTS_"),
    ([sys.executable, "scripts/validate-atomic-requirements.py", "--self-test"], '"decision": "PASS"'),
    ([sys.executable, "scripts/validate-design-coverage.py", "--matrix", "status/design-capability-coverage.v2.json", "--root", ".", "--self-test"], '"decision": "PASS"'),
    ([sys.executable, "scripts/validate-product-subrequirements.py", "--self-test"], "ONSURE_PRODUCT_SUBREQUIREMENT_GATE_PASS"),
    ([sys.executable, "scripts/validate-mvp-acceptance-coverage.py", "--self-test"], "ONSURE_MVP_ACCEPTANCE_GATE_PASS"),
    ([sys.executable, "scripts/validate-mvp-status-consistency.py"], "ONSURE_MVP_STATUS_CONSISTENCY_PASS"),
    ([sys.executable, "scripts/validate-workflow-surface-parity.py", "--self-test"], "ONSURE_WORKFLOW_SURFACE_PARITY_PASS"),
    ([sys.executable, "scripts/validate-critical-callpaths.py", "--self-test"], "ONSURE_CRITICAL_CALLPATH_PASS"),
    ([sys.executable, "scripts/validate-status-consistency.py"], "ONSURE_STATUS_CONSISTENCY_PASS"),
    ([sys.executable, "scripts/validate-ci-boundary.py"], "ONSURE_AUTOMATION_BOUNDARY_PASS"),
    ([sys.executable, "scripts/validate-verification-claims.py"], "ONSURE_VERIFICATION_CLAIM_AUDIT_PASS"),
    (["bash", "scripts/check-shell-syntax.sh"], "ONSURE_SHELL_SYNTAX_PASS"),
]


def main() -> int:
    errors: list[str] = []
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            errors.append(f"MISSING:{relative}")
    workflow_root = ROOT / ".github" / "workflows"
    if workflow_root.exists():
        for path in sorted(workflow_root.glob("*.yml")) + sorted(workflow_root.glob("*.yaml")):
            errors.append(f"GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:{path.name}")
    for path in ROOT.rglob("*.py"):
        if ".onsure" not in path.parts:
            try:
                py_compile.compile(str(path), doraise=True)
            except Exception as exc:
                errors.append(f"PYTHON_INVALID:{path.relative_to(ROOT)}:{exc}")
    for relative in REQUIRED:
        path = ROOT / relative
        if not path.is_file():
            continue
        try:
            if path.suffix == ".json":
                json.loads(path.read_text(encoding="utf-8"))
            if path.name == "pom.xml" or path.suffix == ".xml":
                ET.parse(path)
        except Exception as exc:
            errors.append(f"INVALID:{relative}:{type(exc).__name__}:{exc}")
    for relative, tokens in ASSERTIONS.items():
        text = (ROOT / relative).read_text(encoding="utf-8", errors="replace") if (ROOT / relative).is_file() else ""
        for token in tokens:
            if token not in text:
                errors.append(f"SOURCE_ASSERTION_MISSING:{relative}:{token}")
    for relative, tokens in FORBIDDEN.items():
        text = (ROOT / relative).read_text(encoding="utf-8", errors="replace") if (ROOT / relative).is_file() else ""
        for token in tokens:
            if token in text:
                errors.append(f"FORBIDDEN_SOURCE_TOKEN:{relative}:{token}")
    for command, marker in COMMANDS:
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
        combined = result.stdout + result.stderr
        if result.returncode != 0 or marker not in combined:
            errors.append(f"COMMAND_FAIL:{' '.join(command)}:{result.returncode}:{result.stdout[-2400:]}:{result.stderr[-1600:]}")

    counts_body = json.loads((ROOT / COUNT_AUTHORITY).read_text(encoding="utf-8"))
    count_values = counts_body.get("counts", {})
    total = counts_body.get("total")
    if counts_body.get("contract") != "ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1":
        errors.append("FAILURE_COUNT_AUTHORITY_CONTRACT_INVALID")
    if total != sum(count_values.values()):
        errors.append("FAILURE_COUNT_AUTHORITY_TOTAL_MISMATCH")
    subreq = json.loads((ROOT / "status/product-subrequirement-coverage.v1.json").read_text(encoding="utf-8"))
    mvp = json.loads((ROOT / "status/mvp-acceptance-coverage.v1.json").read_text(encoding="utf-8"))
    verification = json.loads((ROOT / "status/verification-status.v1.json").read_text(encoding="utf-8"))
    workflow = verification.get("workflow_surface_parity", {})
    process = json.loads((ROOT / "contracts/product-process-lineage.v1.json").read_text(encoding="utf-8"))

    plan = json.loads((ROOT / "contracts/codespace-free-remediation-plan.v1.json").read_text(encoding="utf-8"))
    if plan.get("final_single_command") != "bash scripts/onsure-final-stage.sh --profile core":
        errors.append("FINAL_SINGLE_COMMAND_MISMATCH")
    if plan.get("local_gate_command") != "bash scripts/onsure-local-gate.sh --mode full --profile core":
        errors.append("LOCAL_GATE_COMMAND_MISMATCH")
    if plan.get("execution_policy", {}).get("github_actions") != "DISABLED_BY_USER":
        errors.append("ACTIONS_POLICY_NOT_DISABLED")
    if plan.get("assurance_ceiling") != "SELF_VALIDATION_NONFINAL":
        errors.append("ASSURANCE_CEILING_UNSAFE")
    if any(plan.get(field) is not False for field in ("final_lock_allowed", "production_go", "commercial_go")):
        errors.append("UNSAFE_GO_FLAG")

    report = {
        "contract": "ONSURE_CODESPACE_FREE_STATIC_GATE_V20",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "github_actions": "DISABLED_BY_USER",
        "local_gate_required": True,
        "design_capability_count": len(json.loads((ROOT / "status/design-capability-coverage.v2.json").read_text(encoding="utf-8")).get("capabilities", [])),
        "product_subrequirement_count": len(subreq.get("requirements", [])),
        "mvp_acceptance_item_count": len(mvp.get("acceptance_items", [])),
        "workflow_operation_count": workflow.get("dispatcher_operation_count"),
        "product_process_stage_count": len(process.get("stages", [])),
        "product_lineage_artifact_count": len(process.get("artifacts", [])),
        "failure_injection_authority": COUNT_AUTHORITY,
        "failure_injection_count": total,
        **count_values,
        "sandbox_required_attacks": 12,
        "sandbox_verified_attacks": 10,
        "sandbox_unverified_attacks": ["CROSS_TENANT_READ", "CROSS_TENANT_WRITE"],
        "runtime_execution": "NOT_RUN_BY_STATIC_GATE",
        "modular_compile": "NOT_RUN_BY_STATIC_GATE",
        "independent_otester": "NOT_RUN",
        "independent_oaudit": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_CODESPACE_FREE_STATIC_GATE_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_CODESPACE_FREE_STATIC_GATE_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
