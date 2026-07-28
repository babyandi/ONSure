#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
COUNT_AUTHORITY = "contracts/omission-failure-injection-counts.v1.json"
APPROVAL_SNAPSHOT_STATE = "IMMUTABLE_SNAPSHOT_RETURNED_AND_CONSUMED_LOCAL_EXECUTION_REQUIRED"


def load(relative: str) -> dict:
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def validate() -> list[str]:
    errors: list[str] = []
    status = load("status/verification-status.v1.json")
    sandbox = load("contracts/sandbox-boundary.v1.json")
    mvp = load("status/mvp-acceptance-coverage.v1.json")
    counts_body = load(COUNT_AUTHORITY)
    counts = counts_body.get("counts", {})
    total = counts_body.get("total")
    if counts_body.get("contract") != "ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1":
        errors.append("FAILURE_COUNT_AUTHORITY_CONTRACT_INVALID")
    if total != sum(counts.values()):
        errors.append("FAILURE_COUNT_AUTHORITY_TOTAL_MISMATCH")

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
    if set(policy.get("allowed_execution_modes", [])) != {
        "LOCAL_STATIC_ONE_SHOT", "LOCAL_FULL_GATE", "LOCAL_FINAL_STAGE"
    }:
        errors.append("LOCAL_VALIDATION_EXECUTION_MODES_INVALID")

    historical = status.get("historical_automation_evidence", {})
    if historical.get("retained_for_audit_only") is not True:
        errors.append("HISTORICAL_AUTOMATION_NOT_AUDIT_ONLY")
    if historical.get("current_source_bound") is not False:
        errors.append("HISTORICAL_AUTOMATION_FALSELY_BOUND_TO_CURRENT_SOURCE")

    for section in (
        "design_coverage", "product_subrequirement_coverage", "mvp_acceptance_coverage",
        "workflow_surface_parity", "critical_callpath_boundary", "product_process_lineage"
    ):
        receipt = status.get(section, {}).get("source_bound_receipt")
        if receipt != "LOCAL_RECEIPT_REQUIRED":
            errors.append(f"COMMITTED_DYNAMIC_RECEIPT_OVERCLAIM:{section}:{receipt}")

    mvp_status = status.get("mvp_acceptance_coverage", {})
    if mvp_status.get("state") != "NOT_RUN_ALL_11_ACCEPTANCE_ITEMS":
        errors.append("MVP_ACCEPTANCE_STATE_OVERCLAIMED")
    if mvp_status.get("two_consecutive_real_repository_runs") != "NOT_RUN":
        errors.append("MVP_ACCEPTANCE_REPEAT_OVERCLAIMED")
    mvp_assurance = mvp.get("assurance", {})
    if mvp_assurance.get("mvp_full_chain") != "NOT_RUN" \
            or mvp_assurance.get("two_consecutive_real_repository_runs") != "NOT_RUN" \
            or mvp_assurance.get("final_claim_allowed") is not False:
        errors.append("MVP_ACCEPTANCE_REGISTER_OVERCLAIMED")

    failure = status.get("omission_failure_injection", {})
    if failure.get("authority") != COUNT_AUTHORITY:
        errors.append("FAILURE_INJECTION_AUTHORITY_MISSING")
    for field, expected in counts.items():
        if failure.get(field) != expected:
            errors.append(f"FAILURE_INJECTION_COUNT_STALE:{field}")
    if failure.get("all_registered_failure_injections") != total:
        errors.append("FAILURE_INJECTION_TOTAL_STALE")
    if failure.get("current_head_execution") != "LOCAL_EXECUTION_REQUIRED":
        errors.append("CURRENT_HEAD_FAILURE_INJECTION_EXECUTION_OVERCLAIMED")

    authority = status.get("approval_authority_boundary", {})
    if authority.get("request_path_override_allowed") is not False:
        errors.append("APPROVAL_AUTHORITY_OVERRIDE_OVERCLAIM")
    if authority.get("workspace_symlink_alias_allowed") is not False:
        errors.append("APPROVAL_AUTHORITY_WORKSPACE_ALIAS_OVERCLAIM")
    if authority.get("contained_worktree_authority_discovery") != "UNIQUE_EXISTING_AUTHORITY_REQUIRED":
        errors.append("APPROVAL_AUTHORITY_WORKTREE_DISCOVERY_MISSING")
    if authority.get("public_key_must_be_inside_authority_root") is not True:
        errors.append("APPROVAL_PUBLIC_KEY_BOUNDARY_MISSING")
    if authority.get("registry_cross_process_lock") is not True:
        errors.append("APPROVAL_REGISTRY_LOCK_MISSING")
    if authority.get("receipt_verify_consume_binding") != APPROVAL_SNAPSHOT_STATE:
        errors.append("APPROVAL_RECEIPT_SNAPSHOT_BINDING_MISSING")
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
        errors.append("SANDBOX_ATTACK_PARTITION_MISMATCH")
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
            "python3 scripts/validate-mvp-acceptance-coverage.py --self-test",
            "python3 scripts/validate-mvp-status-consistency.py",
            "python3 scripts/validate-workflow-surface-parity.py --self-test",
            "python3 scripts/validate-critical-callpaths.py --self-test",
            "python3 scripts/validate-verification-claims.py",
            "bash scripts/test-fixture-sandbox-boundary.sh",
            COUNT_AUTHORITY,
            '"github_actions":"DISABLED"',
        ):
            if token not in text:
                errors.append(f"LOCAL_VALIDATION_GATE_CONTROL_MISSING:{token}")

    if "ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12" not in (
            ROOT / "scripts/test-fixture-sandbox-boundary.sh").read_text(encoding="utf-8"):
        errors.append("SANDBOX_EXPANDED_BOUNDARY_MARKER_MISSING")
    for required_test in (
        "src/test/java/io/onsure/platform/AdversarialConcurrencyAndOutputTest.java",
        "src/test/java/io/onsure/platform/ApprovalAuthorityPathsTest.java",
        "src/test/java/io/onsure/platform/BoundedProcessRunnerTest.java",
        "src/test/java/io/onsure/platform/GitWorkflowServiceTest.java",
    ):
        if not (ROOT / required_test).is_file():
            errors.append(f"ADVERSARIAL_TEST_MISSING:{required_test}")
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    counts = load(COUNT_AUTHORITY)
    report = {
        "contract": "ONSURE_VERIFICATION_CLAIM_AUDIT_V6",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "github_actions": "DISABLED_BY_USER",
        "committed_dynamic_validation_claims": "PROHIBITED",
        "current_source_evidence": "LOCAL_RECEIPT_REQUIRED",
        "failure_injection_authority": COUNT_AUTHORITY,
        "registered_failure_injections": counts.get("total"),
        "mvp_full_chain": "NOT_RUN",
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
