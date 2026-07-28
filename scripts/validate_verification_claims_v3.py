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
    legacy_authority = load("contracts/legacy-product-subrequirement-authority.v1.json")
    legacy_mvp = load("status/mvp-acceptance-coverage.v1.json")
    final_req = load("status/final-product-requirement-coverage.v1.json")
    final_acc = load("status/final-acceptance-coverage.v1.json")
    counts_body = load(COUNT_AUTHORITY); counts = counts_body.get("counts", {}); total = counts_body.get("total")
    if counts_body.get("contract") != "ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1" or total != sum(counts.values()):
        errors.append("FAILURE_COUNT_AUTHORITY_INVALID")
    if status.get("assessment_source_ref") != "main" or status.get("runtime_source_commit") is not None:
        errors.append("VERIFICATION_SOURCE_STATE_INVALID")
    if status.get("runtime_source_binding_state") != "PENDING_ONE_SHOT_RECEIPT":
        errors.append("CURRENT_SOURCE_ONE_SHOT_BINDING_OVERCLAIMED")
    if 23 in status.get("active_remediation_issues", []):
        errors.append("CLOSED_REMEDIATION_ISSUE_STILL_ACTIVE:23")
    policy = status.get("validation_execution_policy", {})
    if policy.get("github_actions") != "DISABLED_BY_USER" or policy.get("workflow_files_allowed") is not False:
        errors.append("GITHUB_ACTIONS_POLICY_NOT_DISABLED")
    if set(policy.get("allowed_execution_modes", [])) != {"LOCAL_STATIC_ONE_SHOT","LOCAL_FULL_GATE","LOCAL_FINAL_STAGE"}:
        errors.append("LOCAL_VALIDATION_EXECUTION_MODES_INVALID")
    historical = status.get("historical_automation_evidence", {})
    if historical.get("retained_for_audit_only") is not True or historical.get("current_source_bound") is not False:
        errors.append("HISTORICAL_AUTOMATION_SCOPE_INVALID")

    for section in ("design_coverage","legacy_product_decomposition","legacy_mvp_acceptance","final_product_requirement_coverage","final_acceptance_coverage","workflow_surface_parity","critical_callpath_boundary","product_process_lineage"):
        if status.get(section, {}).get("source_bound_receipt") != "LOCAL_RECEIPT_REQUIRED":
            errors.append(f"COMMITTED_DYNAMIC_RECEIPT_OVERCLAIM:{section}")
    if legacy_authority.get("may_satisfy_final_requirement") is not False or legacy_authority.get("may_satisfy_final_acceptance") is not False:
        errors.append("LEGACY_REQUIREMENT_AUTHORITY_ESCALATION")
    if legacy_mvp.get("authority_state") != "LEGACY_MVP_NONAUTHORITATIVE_FOR_FINAL_PRODUCT":
        errors.append("LEGACY_MVP_AUTHORITY_ESCALATION")
    legacy_status = status.get("legacy_product_decomposition", {})
    if legacy_status.get("state") != "LEGACY_NONAUTHORITATIVE_FOR_FINAL_PRODUCT":
        errors.append("LEGACY_PRODUCT_STATE_ESCALATION")
    if status.get("legacy_mvp_acceptance", {}).get("state") != "LEGACY_ALL_NOT_RUN":
        errors.append("LEGACY_MVP_STATE_ESCALATION")

    final_status = status.get("final_product_requirement_coverage", {})
    if final_status.get("state") != "NOT_RUN_ALL_22_FINAL_REQUIREMENTS":
        errors.append("FINAL_PRODUCT_REQUIREMENT_STATE_OVERCLAIMED")
    if final_req.get("assurance", {}).get("final_product_full_chain") != "NOT_RUN" or final_req.get("assurance", {}).get("final_claim_allowed") is not False:
        errors.append("FINAL_PRODUCT_REQUIREMENT_REGISTER_OVERCLAIMED")
    acceptance_status = status.get("final_acceptance_coverage", {})
    if acceptance_status.get("state") != "NOT_RUN_ALL_61_FINAL_ACCEPTANCE_CRITERIA":
        errors.append("FINAL_ACCEPTANCE_STATE_OVERCLAIMED")
    final_assurance = final_acc.get("assurance", {})
    for field in ("financial_scenarios_3_sets","external_ai_product_types_5","white_gray_black_box","same_identity_repeat_2","independent_otester_two_clean","independent_oaudit_two_clean","human_approval"):
        if final_assurance.get(field) != "NOT_RUN":
            errors.append(f"FINAL_ACCEPTANCE_ASSURANCE_OVERCLAIMED:{field}")
    if final_assurance.get("final_claim_allowed") is not False:
        errors.append("FINAL_ACCEPTANCE_FINAL_CLAIM_UNSAFE")

    failure = status.get("omission_failure_injection", {})
    if failure.get("authority") != COUNT_AUTHORITY or failure.get("all_registered_failure_injections") != total:
        errors.append("FAILURE_INJECTION_AUTHORITY_MISMATCH")
    for field, expected in counts.items():
        if failure.get(field) != expected:
            errors.append(f"FAILURE_INJECTION_COUNT_STALE:{field}")
    if failure.get("current_head_execution") != "LOCAL_EXECUTION_REQUIRED":
        errors.append("CURRENT_HEAD_FAILURE_INJECTION_EXECUTION_OVERCLAIMED")

    authority = status.get("approval_authority_boundary", {})
    if authority.get("request_path_override_allowed") is not False or authority.get("workspace_symlink_alias_allowed") is not False:
        errors.append("APPROVAL_AUTHORITY_OVERRIDE_OVERCLAIM")
    if authority.get("contained_worktree_authority_discovery") != "UNIQUE_EXISTING_AUTHORITY_REQUIRED" or authority.get("public_key_must_be_inside_authority_root") is not True or authority.get("registry_cross_process_lock") is not True:
        errors.append("APPROVAL_AUTHORITY_BOUNDARY_MISSING")
    if authority.get("receipt_verify_consume_binding") != APPROVAL_SNAPSHOT_STATE:
        errors.append("APPROVAL_RECEIPT_SNAPSHOT_BINDING_MISSING")
    if authority.get("external_replay_anchor") != "NOT_IMPLEMENTED" or authority.get("current_source_execution") != "NOT_RUN":
        errors.append("APPROVAL_AUTHORITY_OVERCLAIMED")

    required = set(sandbox.get("required_attack_fixtures", [])); verified = set(sandbox.get("verified_attack_fixtures", [])); unverified = set(sandbox.get("unverified_attack_fixtures", []))
    if verified & unverified or required != verified | unverified:
        errors.append("SANDBOX_ATTACK_PARTITION_MISMATCH")
    sandbox_status = status.get("sandbox_attack_tests", {})
    if sandbox_status.get("verified_count") != len(verified) or sandbox_status.get("required_count") != len(required) or set(sandbox_status.get("unverified", [])) != unverified:
        errors.append("SANDBOX_STATUS_SCOPE_MISMATCH")
    if unverified and not str(sandbox_status.get("state", "")).startswith("PARTIAL_"):
        errors.append("SANDBOX_PARTIAL_SCOPE_OVERCLAIMED_AS_PASS")

    workflow_root = ROOT / ".github" / "workflows"
    if workflow_root.exists():
        for workflow in sorted(workflow_root.glob("*.yml")) + sorted(workflow_root.glob("*.yaml")):
            errors.append(f"GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:{workflow.name}")
    gate = ROOT / "scripts/onsure-local-gate.sh"
    text = gate.read_text(encoding="utf-8") if gate.is_file() else ""
    for token in ("validate-legacy-product-subrequirements.py --self-test","validate-mvp-acceptance-coverage.py --self-test","validate-final-product-requirements.py --self-test","validate-final-acceptance-coverage.py --self-test","validate-workflow-surface-parity.py --self-test","validate-critical-callpaths.py --self-test",COUNT_AUTHORITY,'"github_actions":"DISABLED"'):
        if token not in text:
            errors.append(f"LOCAL_VALIDATION_GATE_CONTROL_MISSING:{token}")
    for required_test in ("src/test/java/io/onsure/platform/AdversarialConcurrencyAndOutputTest.java","src/test/java/io/onsure/platform/ApprovalAuthorityPathsTest.java","src/test/java/io/onsure/platform/BoundedProcessRunnerTest.java","src/test/java/io/onsure/platform/GitWorkflowServiceTest.java"):
        if not (ROOT / required_test).is_file(): errors.append(f"ADVERSARIAL_TEST_MISSING:{required_test}")
    return sorted(set(errors))


def main() -> int:
    errors = validate(); counts = load(COUNT_AUTHORITY)
    report = {"contract":"ONSURE_VERIFICATION_CLAIM_AUDIT_V7","decision":"PASS" if not errors else "FAIL","errors":errors,"github_actions":"DISABLED_BY_USER","legacy_authority":"NONAUTHORITATIVE_FOR_FINAL","final_product_requirements":"NOT_RUN","final_acceptance_criteria":"NOT_RUN","current_source_evidence":"LOCAL_RECEIPT_REQUIRED","registered_failure_injections":counts.get("total"),"final_claim_allowed":False}
    print(json.dumps(report,ensure_ascii=False,indent=2,sort_keys=True))
    if errors: print("ONSURE_VERIFICATION_CLAIM_AUDIT_FAIL",file=sys.stderr);return 1
    print("ONSURE_VERIFICATION_CLAIM_AUDIT_PASS");return 0


if __name__ == "__main__": raise SystemExit(main())
