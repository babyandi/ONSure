#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIRED_TOKENS = {
    "src/main/java/io/onsure/platform/ApprovalAuthorityPaths.java": [
        "AUTHORITY_DIRECTORY", "trusted-key-registry.json", "approval-replay-ledger.jsonl",
        "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED", "requireTrustedKeyRegistry",
    ],
    "src/main/java/io/onsure/platform/ExecutionPlanApprovalService.java": [
        "verifyApprovedPlanBundle", "EXECUTION_PLAN_CONSUMED_APPROVAL_INVALID",
        "EXECUTION_PLAN_APPROVED_ARTIFACT_DERIVATION_MISMATCH", "original_plan_file_sha256",
    ],
    "src/main/java/io/onsure/platform/ValidationEngine.java": [
        "ApprovedExecutionPlanBundle", "APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED",
        "ExecutionPlanActionPolicy.requiredAction", "ExecutionPlanActionPolicy.notApproved",
    ],
    "src/main/java/io/onsure/platform/RiskPlanningStage.java": [
        "verifyApprovedPlanBundle", "EXECUTION_PLAN_APPROVAL_BUNDLE_MISSING",
        "original_execution_plan_file", "signed_plan_approval_receipt",
    ],
    "src/main/java/io/onsure/platform/ValidationCompletionGate.java": [
        "ExecutionPlanActionPolicy.isApproved", "ONSURE_VALIDATION_COMPLETION_GATE_V7",
    ],
    "src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java": [
        "project.register-workspace", "project.register-target", "ApprovedExecutionPlanBundle",
        "INCOMPLETE_EXECUTION_PLAN_APPROVAL_BUNDLE",
        "approvalAuthority.rejectRequestOverrides",
        "approvalAuthority.requireTrustedKeyRegistry",
        "approvalAuthority.replayLedgerForConsumption",
    ],
    "src/main/java/io/onsure/platform/BoundedProcessRunner.java": [
        "onsure-process-output-drain", "COMMAND_TIMEOUT", "OUTPUT_DRAIN_TIMEOUT",
        "maxOutputBytes", "descendants().forEach(ProcessHandle::destroyForcibly)",
    ],
    "src/main/java/io/onsure/platform/ProgramLearningService.java": [
        "BoundedProcessRunner.run", "PROGRAM_GIT_OUTPUT_LIMIT_EXCEEDED",
    ],
    "src/main/java/io/onsure/platform/SourceReferenceBinding.java": [
        "BoundedProcessRunner.run", "IMMUTABLE_GIT_OUTPUT_LIMIT_EXCEEDED",
    ],
    "src/main/java/io/onsure/platform/ImprovementWorkflowService.java": [
        "BoundedProcessRunner.run", "GIT_COMMAND_OUTPUT_LIMIT",
    ],
    "src/main/java/io/onsure/platform/GitWorkflowService.java": [
        "BoundedProcessRunner.run", "COMMAND_OUTPUT_LIMIT",
    ],
    "src/test/java/io/onsure/platform/ExecutionPlanApprovalServiceTest.java": [
        "trustedExactApprovalRequiresOriginalPlanReceiptKeyAndConsumedReplayLedger",
        "verifyApprovedPlanBundle", "verifyApprovedPlan(output",
    ],
    "src/test/java/io/onsure/platform/ApprovalAuthorityPathsTest.java": [
        "everyWorkflowRejectsCallerSelectedKeyRegistryOrReplayLedger",
        "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED", "symlinkedAuthorityRegistryIsRejected",
    ],
    "src/test/java/io/onsure/platform/BoundedProcessRunnerTest.java": [
        "drainsLargeOutputWithoutPipeDeadlockAndMarksTruncation",
        "killsHungProcessAtWallClockDeadline", "preservesNonzeroExitAndBoundedDiagnosticOutput",
    ],
    "src/test/java/io/onsure/platform/ProductRegistrationWorkflowTest.java": [
        "project.register-workspace", "project.register-target", "project.list-targets",
    ],
}


def validate(root: pathlib.Path = ROOT) -> list[str]:
    errors: list[str] = []
    for relative, tokens in REQUIRED_TOKENS.items():
        path = root / relative
        if not path.is_file():
            errors.append(f"CRITICAL_CALLPATH_FILE_MISSING:{relative}")
            continue
        text = path.read_text(encoding="utf-8", errors="strict")
        for token in tokens:
            if token not in text:
                errors.append(f"CRITICAL_CALLPATH_TOKEN_MISSING:{relative}:{token}")
    return sorted(set(errors))


def self_test() -> list[str]:
    import tempfile
    missed: list[str] = []
    with tempfile.TemporaryDirectory() as directory:
        root = pathlib.Path(directory)
        for relative, tokens in REQUIRED_TOKENS.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n".join(tokens) + "\n", encoding="utf-8")
        if validate(root):
            missed.append("CRITICAL_CALLPATH_BASELINE_REJECTED")
        cases = [
            ("approval bundle verifier", "src/main/java/io/onsure/platform/ExecutionPlanApprovalService.java", "verifyApprovedPlanBundle"),
            ("engine bundle entry", "src/main/java/io/onsure/platform/ValidationEngine.java", "ApprovedExecutionPlanBundle"),
            ("stage scope enforcement", "src/main/java/io/onsure/platform/ValidationEngine.java", "ExecutionPlanActionPolicy.notApproved"),
            ("dispatcher registration", "src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java", "project.register-target"),
            ("bypass regression test", "src/test/java/io/onsure/platform/ExecutionPlanApprovalServiceTest.java", "verifyApprovedPlan(output"),
            ("fixed trust root", "src/main/java/io/onsure/platform/ApprovalAuthorityPaths.java", "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED"),
            ("authority override regression", "src/test/java/io/onsure/platform/ApprovalAuthorityPathsTest.java", "everyWorkflowRejectsCallerSelectedKeyRegistryOrReplayLedger"),
            ("bounded process runner", "src/main/java/io/onsure/platform/BoundedProcessRunner.java", "onsure-process-output-drain"),
            ("all git callpaths bounded", "src/main/java/io/onsure/platform/GitWorkflowService.java", "BoundedProcessRunner.run"),
            ("hung process regression", "src/test/java/io/onsure/platform/BoundedProcessRunnerTest.java", "killsHungProcessAtWallClockDeadline"),
        ]
        for name, relative, token in cases:
            path = root / relative
            original = path.read_text(encoding="utf-8")
            path.write_text(original.replace(token, "REMOVED_TOKEN", 1), encoding="utf-8")
            violations = validate(root)
            if not any(value.startswith("CRITICAL_CALLPATH_TOKEN_MISSING") for value in violations):
                missed.append(f"CRITICAL_CALLPATH_SELF_TEST_MISSED:{name}")
            path.write_text(original, encoding="utf-8")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    errors = validate()
    self_errors = self_test() if args.self_test else []
    report = {
        "contract": "ONSURE_CRITICAL_CALLPATH_VALIDATION_REPORT_V3",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "critical_files": len(REQUIRED_TOKENS),
        "failure_injection_count": 10 if args.self_test else 0,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_CRITICAL_CALLPATH_PASS")
        return 0
    print("ONSURE_CRITICAL_CALLPATH_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
