#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def load(relative: str) -> dict:
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def validate() -> list[str]:
    errors: list[str] = []
    status = load("status/verification-status.v1.json")
    sandbox = load("contracts/sandbox-boundary.v1.json")

    if status.get("assessment_source_ref") != "main":
        errors.append("VERIFICATION_ASSESSMENT_SOURCE_NOT_MAIN")
    if status.get("runtime_source_commit") is not None:
        errors.append("STATIC_STATUS_MUST_NOT_EMBED_RUNTIME_SOURCE_COMMIT")
    if status.get("runtime_source_binding_state") != "PENDING_ONE_SHOT_RECEIPT":
        errors.append("CURRENT_SOURCE_ONE_SHOT_BINDING_OVERCLAIMED")
    if 23 in status.get("active_remediation_issues", []):
        errors.append("CLOSED_REMEDIATION_ISSUE_STILL_ACTIVE:23")

    policy = status.get("validation_execution_policy", {})
    if policy.get("github_actions") != "DISABLED_BY_USER":
        errors.append("GITHUB_ACTIONS_POLICY_NOT_DISABLED")
    if policy.get("workflow_files_allowed") is not False:
        errors.append("GITHUB_ACTIONS_WORKFLOW_FILES_NOT_FORBIDDEN")
    expected_modes = {"LOCAL_STATIC_ONE_SHOT", "LOCAL_FULL_GATE", "LOCAL_FINAL_STAGE"}
    if set(policy.get("allowed_execution_modes", [])) != expected_modes:
        errors.append("LOCAL_VALIDATION_EXECUTION_MODES_INVALID")

    historical = status.get("historical_automation_evidence", {})
    if historical.get("retained_for_audit_only") is not True:
        errors.append("HISTORICAL_AUTOMATION_NOT_AUDIT_ONLY")
    if historical.get("current_source_bound") is not False:
        errors.append("HISTORICAL_AUTOMATION_FALSELY_BOUND_TO_CURRENT_SOURCE")

    for section in (
            "design_coverage", "product_subrequirement_coverage",
            "workflow_surface_parity", "critical_callpath_boundary",
            "product_process_lineage"):
        receipt = status.get(section, {}).get("source_bound_receipt")
        if receipt != "LOCAL_RECEIPT_REQUIRED":
            errors.append(f"COMMITTED_DYNAMIC_RECEIPT_OVERCLAIM:{section}:{receipt}")

    failure = status.get("omission_failure_injection", {})
    if failure.get("all_registered_failure_injections") != 75:
        errors.append("FAILURE_INJECTION_TOTAL_STALE")
    if failure.get("critical_callpath_cases") != 7:
        errors.append("CRITICAL_CALLPATH_FAILURE_COUNT_STALE")
    if failure.get("current_head_execution") != "LOCAL_EXECUTION_REQUIRED":
        errors.append("CURRENT_HEAD_FAILURE_INJECTION_EXECUTION_OVERCLAIMED")

    authority = status.get("approval_authority_boundary", {})
    if authority.get("authority_root") != ".onsure/approval-authority/":
        errors.append("APPROVAL_AUTHORITY_ROOT_NOT_CANONICAL")
    if authority.get("trusted_key_registry") != ".onsure/approval-authority/trusted-key-registry.json":
        errors.append("APPROVAL_KEY_REGISTRY_NOT_CANONICAL")
    if authority.get("replay_ledger") != ".onsure/approval-authority/approval-replay-ledger.jsonl":
        errors.append("APPROVAL_REPLAY_LEDGER_NOT_CANONICAL")
    if authority.get("request_path_override_allowed") is not False:
        errors.append("APPROVAL_AUTHORITY_OVERRIDE_OVERCLAIM")
    if authority.get("external_replay_anchor") != "NOT_IMPLEMENTED":
        errors.append("APPROVAL_REPLAY_EXTERNAL_ANCHOR_OVERCLAIMED")
    if authority.get("current_source_execution") != "NOT_RUN":
        errors.append("APPROVAL_AUTHORITY_CURRENT_SOURCE_EXECUTION_OVERCLAIMED")

    required = set(sandbox.get("required_attack_fixtures", []))
    verified = set(sandbox.get("verified_attack_fixtures", []))
    unverified = set(sandbox.get("unverified_attack_fixtures", []))
    if verified & unverified:
        errors.append("SANDBOX_ATTACK_VERIFIED_UNVERIFIED_OVERLAP")
    if required != verified | unverified:
        errors.append(
            "SANDBOX_ATTACK_PARTITION_MISMATCH:"
            f"missing={sorted(required - verified - unverified)}:"
            f"extra={sorted((verified | unverified) - required)}"
        )
    sandbox_status = status.get("sandbox_attack_tests", {})
    if sandbox_status.get("verified_count") != len(verified):
        errors.append("SANDBOX_VERIFIED_COUNT_MISMATCH")
    if sandbox_status.get("required_count") != len(required):
        errors.append("SANDBOX_REQUIRED_COUNT_MISMATCH")
    if set(sandbox_status.get("unverified", [])) != unverified:
        errors.append("SANDBOX_UNVERIFIED_SET_MISMATCH")
    state = str(sandbox_status.get("state", ""))
    if unverified and not state.startswith("PARTIAL_"):
        errors.append("SANDBOX_PARTIAL_SCOPE_OVERCLAIMED_AS_PASS")
    if not unverified and not state.startswith("PASS_"):
        errors.append("SANDBOX_FULL_SCOPE_NOT_MARKED_PASS")

    workflow_root = ROOT / ".github" / "workflows"
    if workflow_root.exists():
        for workflow in sorted(workflow_root.glob("*.yml")) + sorted(workflow_root.glob("*.yaml")):
            errors.append(f"GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:{workflow.name}")

    local_gate = ROOT / "scripts/onsure-local-gate.sh"
    if not local_gate.is_file():
        errors.append("LOCAL_VALIDATION_GATE_MISSING")
    else:
        text = local_gate.read_text(encoding="utf-8")
        for token in (
            "python3 scripts/validate-product-subrequirements.py --self-test",
            "python3 scripts/validate-workflow-surface-parity.py --self-test",
            "python3 scripts/validate-critical-callpaths.py --self-test",
            "python3 scripts/validate-verification-claims.py",
            "bash scripts/test-fixture-sandbox-boundary.sh",
            '"github_actions": "DISABLED"',
        ):
            if token not in text:
                errors.append(f"LOCAL_VALIDATION_GATE_CONTROL_MISSING:{token}")

    if "ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12" not in (
        ROOT / "scripts/test-fixture-sandbox-boundary.sh"
    ).read_text(encoding="utf-8"):
        errors.append("SANDBOX_EXPANDED_BOUNDARY_MARKER_MISSING")
    for required_test in (
            "src/test/java/io/onsure/platform/AdversarialConcurrencyAndOutputTest.java",
            "src/test/java/io/onsure/platform/ApprovalAuthorityPathsTest.java",
            "src/test/java/io/onsure/platform/BoundedProcessRunnerTest.java"):
        if not (ROOT / required_test).is_file():
            errors.append(f"ADVERSARIAL_TEST_MISSING:{required_test}")

    return sorted(set(errors))


def main() -> int:
    errors = validate()
    report = {
        "contract": "ONSURE_VERIFICATION_CLAIM_AUDIT_V3",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "github_actions": "DISABLED_BY_USER",
        "committed_dynamic_validation_claims": "PROHIBITED",
        "current_source_evidence": "LOCAL_RECEIPT_REQUIRED",
        "registered_failure_injections": 75,
        "approval_external_replay_anchor": "NOT_IMPLEMENTED",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_VERIFICATION_CLAIM_AUDIT_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_VERIFICATION_CLAIM_AUDIT_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
