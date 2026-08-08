#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIRED_TOKENS = {
    "src/main/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPaths.java": [
        "AUTHORITY_BASE_PROPERTY", "DEFAULT_AUTHORITY_BASE", "trusted-key-registry.json",
        "approval-replay-ledger.jsonl", "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED",
        "PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED", "PRODUCT_STATE_OVERRIDE_FIELDS",
        "patchApplyShape", "APPROVAL_AUTHORITY_MUST_BE_OUTSIDE_TARGET_WORKSPACE",
        "APPROVAL_AUTHORITY_WORKSPACE_SYMLINK_PROHIBITED", "requireTrustedKeyRegistry",
        "discoverForContainedPath", "APPROVAL_AUTHORITY_NOT_DISCOVERABLE_FROM_PATH",
        "APPROVAL_AUTHORITY_AMBIGUOUS_FOR_PATH",
    ],
    "src/main/java/kr/co/oruda/onsure/assurance/ApprovalReceiptVerifier.java": [
        "Files.createTempFile(\"onsure-approval-receipt-\"", "Files.copy(receiptFile, snapshot",
        "appendConsumption(snapshot, receipt", "Files.deleteIfExists(snapshot)",
        "record ConsumedReceipt", "requireValidAndConsumeSnapshot",
    ],
    "src/main/java/kr/co/oruda/onsure/assurance/LocalKeyRegistry.java": [
        "PUBLIC_KEY_OUTSIDE_AUTHORITY_ROOT", "ExclusiveFileLock.call(lockFile",
        "ATOMIC_MOVE", "KEY_REGISTRY_AUTHORITY_ROOT_SYMLINK",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/ExecutionPlanApprovalService.java": [
        "verifyApprovedPlanBundle", "EXECUTION_PLAN_CONSUMED_APPROVAL_INVALID",
        "EXECUTION_PLAN_APPROVED_ARTIFACT_DERIVATION_MISMATCH", "original_plan_file_sha256",
        "requireValidAndConsumeSnapshot", "consumedApproval.sha256()",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/ExecutionPlanService.java": [
        "TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY",
        "Boolean.getBoolean(TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY)",
        "EXECUTION_PLAN_FIXTURE_AUTO_APPROVAL_PROCESS_GATE_DISABLED",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/RegisteredExecutionPlanGenerationService.java": [
        "PROGRAM_PROFILE_PROJECT_MISMATCH", "PROGRAM_PROFILE_TARGET_MISMATCH",
        "PROGRAM_PROFILE_SOURCE_DRIFT", "ExecutionPlanService().plan",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/FixtureRegistryStage.java": [
        "trustedFixtureAutoApproval", "signedFixtureApproval",
        "EXECUTABLE_FIXTURE_REQUIRES_PROCESS_GATE_OR_SIGNED_PLAN_APPROVAL",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/ValidationEngine.java": [
        "ApprovedExecutionPlanBundle", "APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED",
        "ExecutionPlanActionPolicy.requiredAction", "ExecutionPlanActionPolicy.notApproved",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/RiskPlanningStage.java": [
        "verifyApprovedPlanBundle", "EXECUTION_PLAN_APPROVAL_BUNDLE_MISSING",
        "original_execution_plan_file", "signed_plan_approval_receipt",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/ValidationCompletionGate.java": [
        "ExecutionPlanActionPolicy.isApproved", "ONSURE_VALIDATION_COMPLETION_GATE_V7",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/LocalWorkflowDispatcher.java": [
        "project.register-workspace", "project.register-target", "plan.generate",
        "requireRegisteredTarget", "REGISTERED_TARGET_NOT_FOUND_IN_PROJECT",
        "REGISTERED_TARGET_FIELD_OVERRIDE_PROHIBITED", "ApprovedExecutionPlanBundle",
        "INCOMPLETE_EXECUTION_PLAN_APPROVAL_BUNDLE",
        "approvalAuthority.rejectRequestOverrides",
        "approvalAuthority.requireTrustedKeyRegistry",
        "approvalAuthority.replayLedgerForConsumption",
        "new TenantRbacService(workspaceRoot).execute",
        "AuthenticatedWorkflowIdentity authenticatedIdentity",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/TenantRbacService.java": [
        "RBAC_OPERATION_DENIED", "CROSS_TENANT_RESOURCE_ACCESS_DENIED",
        "CROSS_TENANT_RESOURCE_WRITE_DENIED", "AUTHENTICATED_ACTOR_SUBSTITUTION",
        "AUTHENTICATED_TENANT_CONTEXT_SUBSTITUTION", "resultClaims",
        "TENANT_RBAC_STATE_SYMLINK",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/LocalAuthenticatedApiServer.java": [
        "new LocalWorkflowDispatcher(workspaceRoot, identity)",
        "catch (SecurityException denied)", "artifact.read", "PATH_SYMLINK_PROHIBITED",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/BoundedProcessRunner.java": [
        "onsure-process-output-drain", "COMMAND_TIMEOUT", "OUTPUT_DRAIN_TIMEOUT",
        "maxOutputBytes", "descendants().forEach(ProcessHandle::destroyForcibly)",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/ProgramLearningService.java": [
        "BoundedProcessRunner.run", "PROGRAM_GIT_OUTPUT_LIMIT_EXCEEDED",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/SourceReferenceBinding.java": [
        "BoundedProcessRunner.run", "IMMUTABLE_GIT_OUTPUT_LIMIT_EXCEEDED",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/ImprovementWorkflowService.java": [
        "BoundedProcessRunner.run", "GIT_COMMAND_OUTPUT_LIMIT",
    ],
    "src/main/java/kr/co/oruda/onsure/platform/GitWorkflowService.java": [
        "BoundedProcessRunner.run", "COMMAND_OUTPUT_LIMIT",
        "requireApprovalNotExpired", "GIT_DELIVERY_APPROVAL_EXPIRED",
        "approval_expires_at", "discoverForContainedPath",
        "GIT_DELIVERY_CONSUMED_APPROVAL_INVALID",
    ],
    "src/test/java/kr/co/oruda/onsure/platform/ExecutionPlanApprovalServiceTest.java": [
        "trustedExactApprovalRequiresOriginalPlanReceiptKeyAndConsumedReplayLedger",
        "verifyApprovedPlanBundle", "verifyApprovedPlan(output",
    ],
    "src/test/java/kr/co/oruda/onsure/platform/ExecutionPlanAutoApprovalBoundaryTest.java": [
        "autoApprovalRequiresProcessGateAndTrustedFixtureProfile",
        "System.clearProperty", "LOCAL_REVIEWED",
    ],
    "src/test/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPathsTest.java": [
        "authorityPathsAreCanonicalAndPhysicallyOutsideWorkspace",
        "authorityBaseInsideWorkspaceIsRejected",
        "containedWorktreeDiscoversExactlyOneFixedWorkspaceAuthority",
        "containedPathWithoutAuthorityIsRejected",
        "everyWorkflowRejectsCallerSelectedAuthorityPaths",
        "productOwnedStateAndOutputPathsCannotBeForkedOrPointAtSourceFiles",
        "PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED",
        "symlinkedAuthorityRegistryIsRejected", "symlinkedWorkspaceAliasCannotForkApprovalAuthority",
    ],
    "src/test/java/kr/co/oruda/onsure/assurance/LocalKeyRegistryBoundaryTest.java": [
        "publicKeyOutsideAuthorityRootIsRejected",
        "concurrentRegistryInstancesPreserveEveryKey",
        "PUBLIC_KEY_OUTSIDE_AUTHORITY_ROOT",
    ],
    "src/test/java/kr/co/oruda/onsure/platform/BoundedProcessRunnerTest.java": [
        "drainsLargeOutputWithoutPipeDeadlockAndMarksTruncation",
        "killsHungProcessAtWallClockDeadline", "preservesNonzeroExitAndBoundedDiagnosticOutput",
    ],
    "src/test/java/kr/co/oruda/onsure/platform/GitWorkflowServiceTest.java": [
        "expiredDeliveryApprovalCannotReachPushTransition", "GIT_DELIVERY_APPROVAL_EXPIRED",
    ],
    "src/test/java/kr/co/oruda/onsure/platform/ProductRegistrationWorkflowTest.java": [
        "workspaceProjectTargetLearningAndPlanUseOneRegisteredIdentity",
        "plan.generate", "PROGRAM_PROFILE_SOURCE_DRIFT",
        "REGISTERED_TARGET_FIELD_OVERRIDE_PROHIBITED",
    ],
    "src/test/java/kr/co/oruda/onsure/platform/TenantRbacServiceTest.java": [
        "rolesAndDurableOwnershipDenyCrossTenantReadsAndWrites",
        "callerCannotSubstituteAuthenticatedActorTenantRegionOrRoles",
        "failedWorkflowDoesNotLeaveAResourceClaimBehind",
        "validationResultBindsItsRunArtifactsToTheExecutingTenant",
        "dispatcherCannotBypassTheCommonAuthorizationBoundary",
        "dispatcherPersistsOnlyTheServerAuthenticatedTenantAndActor",
        "tenantRegistryCannotBeRedirectedThroughAWorkspaceSymlink",
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
            ("approval receipt immutable snapshot", "src/main/java/kr/co/oruda/onsure/assurance/ApprovalReceiptVerifier.java", "appendConsumption(snapshot, receipt"),
            ("approval service exact consumed snapshot", "src/main/java/kr/co/oruda/onsure/platform/ExecutionPlanApprovalService.java", "consumedApproval.sha256()"),
            ("approval bundle verifier", "src/main/java/kr/co/oruda/onsure/platform/ExecutionPlanApprovalService.java", "verifyApprovedPlanBundle"),
            ("fixture auto approval process gate", "src/main/java/kr/co/oruda/onsure/platform/ExecutionPlanService.java", "Boolean.getBoolean(TRUSTED_FIXTURE_AUTO_APPROVAL_PROPERTY)"),
            ("product state path boundary", "src/main/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPaths.java", "PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED"),
            ("registered target binding", "src/main/java/kr/co/oruda/onsure/platform/LocalWorkflowDispatcher.java", "REGISTERED_TARGET_NOT_FOUND_IN_PROJECT"),
            ("registered plan generation", "src/main/java/kr/co/oruda/onsure/platform/RegisteredExecutionPlanGenerationService.java", "PROGRAM_PROFILE_SOURCE_DRIFT"),
            ("engine bundle entry", "src/main/java/kr/co/oruda/onsure/platform/ValidationEngine.java", "ApprovedExecutionPlanBundle"),
            ("stage scope enforcement", "src/main/java/kr/co/oruda/onsure/platform/ValidationEngine.java", "ExecutionPlanActionPolicy.notApproved"),
            ("dispatcher registration", "src/main/java/kr/co/oruda/onsure/platform/LocalWorkflowDispatcher.java", "project.register-target"),
            ("bypass regression test", "src/test/java/kr/co/oruda/onsure/platform/ExecutionPlanApprovalServiceTest.java", "verifyApprovedPlan(output"),
            ("fixed trust root", "src/main/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPaths.java", "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED"),
            ("authority outside workspace", "src/main/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPaths.java", "APPROVAL_AUTHORITY_MUST_BE_OUTSIDE_TARGET_WORKSPACE"),
            ("authority separation regression", "src/test/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPathsTest.java", "authorityBaseInsideWorkspaceIsRejected"),
            ("workspace alias trust fork", "src/main/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPaths.java", "APPROVAL_AUTHORITY_WORKSPACE_SYMLINK_PROHIBITED"),
            ("workspace alias regression", "src/test/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPathsTest.java", "symlinkedWorkspaceAliasCannotForkApprovalAuthority"),
            ("draft PR authority discovery", "src/main/java/kr/co/oruda/onsure/platform/GitWorkflowService.java", "discoverForContainedPath"),
            ("authority discovery regression", "src/test/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPathsTest.java", "containedWorktreeDiscoversExactlyOneFixedWorkspaceAuthority"),
            ("public key reference boundary", "src/main/java/kr/co/oruda/onsure/assurance/LocalKeyRegistry.java", "PUBLIC_KEY_OUTSIDE_AUTHORITY_ROOT"),
            ("key registry cross-process lock", "src/main/java/kr/co/oruda/onsure/assurance/LocalKeyRegistry.java", "ExclusiveFileLock.call(lockFile"),
            ("key registry boundary regression", "src/test/java/kr/co/oruda/onsure/assurance/LocalKeyRegistryBoundaryTest.java", "publicKeyOutsideAuthorityRootIsRejected"),
            ("key registry concurrency regression", "src/test/java/kr/co/oruda/onsure/assurance/LocalKeyRegistryBoundaryTest.java", "concurrentRegistryInstancesPreserveEveryKey"),
            ("bounded process runner", "src/main/java/kr/co/oruda/onsure/platform/BoundedProcessRunner.java", "onsure-process-output-drain"),
            ("push expiry regression", "src/main/java/kr/co/oruda/onsure/platform/GitWorkflowService.java", "requireApprovalNotExpired"),
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
        "contract": "ONSURE_CRITICAL_CALLPATH_VALIDATION_REPORT_V12",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "critical_files": len(REQUIRED_TOKENS),
        "failure_injection_count": 24 if args.self_test else 0,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_CRITICAL_CALLPATH_PASS")
        return 0
    print("ONSURE_CRITICAL_CALLPATH_FAIL", file=__import__("sys").stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
