#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOTAL_FAILURE_INJECTIONS = 87


def load(relative: str):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def capability_key(value: str) -> str:
    return value.lower().replace("-", "_")


def main() -> int:
    errors: list[str] = []
    design = load("status/design-capability-coverage.v2.json")
    subreq = load("status/product-subrequirement-coverage.v1.json")
    trace = load("contracts/requirements-traceability.v1.json")
    matrix = load("status/implementation-matrix.v1.json")
    omission = load("status/omission-detection-status.v1.json")
    verification = load("status/verification-status.v1.json")
    remaining = load("status/remaining-work-register.v1.json")
    process = load("contracts/product-process-lineage.v1.json")

    design_items = design.get("capabilities", [])
    design_by_id = {item.get("capability_id"): item for item in design_items}
    trace_items = trace.get("items", [])
    trace_by_id = {item.get("id"): item for item in trace_items}
    if len(design_items) != 28 or len(design_by_id) != 28:
        errors.append("DESIGN_CAPABILITY_COUNT_MISMATCH")
    if set(design_by_id) != set(trace_by_id):
        errors.append("TRACE_DESIGN_ID_SET_MISMATCH")
    impl_counts = Counter(item.get("implementation_status") for item in design_items)
    verify_counts = Counter(item.get("verification_state") for item in design_items)
    for capability_id, item in design_by_id.items():
        trace_item = trace_by_id.get(capability_id, {})
        if trace_item.get("status") != item.get("implementation_status"):
            errors.append(f"TRACE_DESIGN_IMPLEMENTATION_MISMATCH:{capability_id}")
        if trace_item.get("verification_state") != item.get("verification_state"):
            errors.append(f"TRACE_DESIGN_VERIFICATION_MISMATCH:{capability_id}")
        if item.get("verification_state") == "PASS" and not item.get("evidence_refs"):
            errors.append(f"PASS_WITHOUT_EVIDENCE:{capability_id}")
    trace_summary = trace.get("summary", {})
    for state, count in impl_counts.items():
        if trace_summary.get(state.lower()) != count:
            errors.append(f"TRACE_SUMMARY_MISMATCH:{state}")
    for state, count in verify_counts.items():
        if trace_summary.get("verification", {}).get(state.lower()) != count:
            errors.append(f"TRACE_VERIFICATION_SUMMARY_MISMATCH:{state}")

    expected_matrix = {capability_key(key): value.get("implementation_status")
                       for key, value in design_by_id.items()}
    if matrix.get("capabilities") != expected_matrix:
        errors.append("IMPLEMENTATION_MATRIX_CAPABILITY_MAP_MISMATCH")
    for state, count in impl_counts.items():
        if matrix.get("counts", {}).get(state) != count:
            errors.append(f"IMPLEMENTATION_MATRIX_COUNT_MISMATCH:{state}")
    if matrix.get("runtime_source_commit") is not None:
        errors.append("MATRIX_RUNTIME_SOURCE_COMMIT_MUST_BE_NULL")

    sub_items = subreq.get("requirements", [])
    if len(sub_items) != 38 or len({item.get("id") for item in sub_items}) != 38:
        errors.append("PRODUCT_SUBREQUIREMENT_COUNT_MISMATCH")
    sub_impl = Counter(item.get("implementation_status") for item in sub_items)
    sub_verify = Counter(item.get("verification_state") for item in sub_items)
    sub_summary = {
        "total": 38,
        "implemented": sub_impl.get("IMPLEMENTED", 0),
        "partial": sub_impl.get("PARTIAL", 0),
        "stub": sub_impl.get("STUB", 0),
        "design_only": sub_impl.get("DESIGN_ONLY", 0),
        "verification_not_run": sub_verify.get("NOT_RUN", 0),
    }
    for field, expected in sub_summary.items():
        if subreq.get("summary", {}).get(field) != expected:
            errors.append(f"PRODUCT_SUBREQUIREMENT_SUMMARY_MISMATCH:{field}")
    partial = next((item for item in sub_items if item.get("id") == "FR-04-C"), {})
    if partial.get("implementation_status") != "PARTIAL":
        errors.append("PARTIAL_APPROVAL_SUBREQUIREMENT_STATE_INVALID")
    if "VSCODE_PARTIAL_APPROVAL_UI_NOT_IMPLEMENTED" not in partial.get("missing_controls", []):
        errors.append("PARTIAL_APPROVAL_UI_GAP_NOT_RECORDED")

    stages = process.get("stages", [])
    artifacts = process.get("artifacts", [])
    if len(stages) != 20 or len({item.get("stage_id") for item in stages}) != 20:
        errors.append("PROCESS_STAGE_COUNT_OR_DUPLICATE")
    if len(artifacts) != 20 or len({item.get("artifact_id") for item in artifacts}) != 20:
        errors.append("PROCESS_ARTIFACT_COUNT_OR_DUPLICATE")

    expected_coverage = {
        "design_capabilities": 28,
        "product_subrequirements": 38,
        "workflow_operations": 39,
        "workflow_surfaces": 3,
        "product_process_stages": 20,
        "lineage_artifacts": 20,
    }
    for field, expected in expected_coverage.items():
        if omission.get("coverage", {}).get(field) != expected:
            errors.append(f"OMISSION_COVERAGE_MISMATCH:{field}")
    if omission.get("failure_injection", {}).get("total") != 28:
        errors.append("OMISSION_BASE_FAILURE_COUNT_MISMATCH")

    sub_status = verification.get("product_subrequirement_coverage", {})
    for field in ("implemented", "partial", "stub", "design_only"):
        if sub_status.get(field) != sub_summary[field]:
            errors.append(f"VERIFICATION_SUBREQUIREMENT_SUMMARY_MISMATCH:{field}")
    if verification.get("workflow_surface_parity", {}).get("dispatcher_operation_count") != 39:
        errors.append("VERIFICATION_WORKFLOW_OPERATION_COUNT_MISMATCH")
    if verification.get("critical_callpath_boundary", {}).get("runner") \
            != "python3 scripts/validate-critical-callpaths.py --self-test":
        errors.append("VERIFICATION_CRITICAL_CALLPATH_RUNNER_MISSING")

    authority = verification.get("approval_authority_boundary", {})
    if authority.get("state") != "IMPLEMENTED_OUTSIDE_TARGET_WORKSPACE_KEY_REFERENCES_BOUNDED_LOCAL_EXECUTION_REQUIRED":
        errors.append("APPROVAL_AUTHORITY_STATE_MISMATCH")
    if authority.get("request_path_override_allowed") is not False:
        errors.append("APPROVAL_AUTHORITY_OVERRIDE_UNSAFE")
    if authority.get("workspace_symlink_alias_allowed") is not False:
        errors.append("APPROVAL_AUTHORITY_WORKSPACE_ALIAS_UNSAFE")
    if authority.get("public_key_must_be_inside_authority_root") is not True:
        errors.append("APPROVAL_PUBLIC_KEY_BOUNDARY_MISSING")
    if authority.get("registry_cross_process_lock") is not True:
        errors.append("APPROVAL_REGISTRY_CROSS_PROCESS_LOCK_MISSING")
    if authority.get("registry_atomic_replace") is not True:
        errors.append("APPROVAL_REGISTRY_ATOMIC_REPLACE_MISSING")
    if authority.get("receipt_verify_consume_binding") \
            != "IMMUTABLE_SNAPSHOT_IMPLEMENTED_LOCAL_EXECUTION_REQUIRED":
        errors.append("APPROVAL_RECEIPT_SNAPSHOT_BINDING_MISSING")
    if authority.get("external_replay_anchor") != "NOT_IMPLEMENTED":
        errors.append("APPROVAL_EXTERNAL_ANCHOR_STATE_MISMATCH")
    if authority.get("current_source_execution") != "NOT_RUN":
        errors.append("APPROVAL_AUTHORITY_RUNTIME_OVERCLAIMED")

    bounded = verification.get("bounded_process_execution", {})
    if set(bounded.get("covered_callpaths", [])) != {
        "PROGRAM_LEARNING_GIT", "SOURCE_REFERENCE_GIT",
        "PATCH_WORKFLOW_GIT", "GIT_DELIVERY_GIT_GH",
    }:
        errors.append("BOUNDED_PROCESS_CALLPATH_SET_MISMATCH")
    if bounded.get("current_repository_maven") != "NOT_RUN":
        errors.append("BOUNDED_PROCESS_MAVEN_OVERCLAIMED")

    expected_failure_counts = {
        "design_process_lineage_cases": 28,
        "atomic_requirement_cases": 10,
        "automation_boundary_cases": 6,
        "verification_claim_cases": 10,
        "product_subrequirement_cases": 10,
        "workflow_surface_cases": 6,
        "critical_callpath_cases": 17,
        "all_registered_failure_injections": TOTAL_FAILURE_INJECTIONS,
    }
    current_failure = verification.get("omission_failure_injection", {})
    for field, expected in expected_failure_counts.items():
        if current_failure.get(field) != expected:
            errors.append(f"VERIFICATION_FAILURE_INJECTION_MISMATCH:{field}")
    additional = omission.get("additional_failure_injection", {})
    for field, expected in {
        "automation_boundary_cases": 6,
        "verification_claim_cases": 10,
        "product_subrequirement_cases": 10,
        "workflow_surface_cases": 6,
        "critical_callpath_cases": 17,
        "all_registered_cases": TOTAL_FAILURE_INJECTIONS,
    }.items():
        if additional.get(field) != expected:
            errors.append(f"OMISSION_ADDITIONAL_COUNT_MISMATCH:{field}")

    if verification.get("runtime_source_commit") is not None:
        errors.append("VERIFICATION_RUNTIME_SOURCE_COMMIT_MUST_BE_NULL")
    if verification.get("assessment_source_ref") != "main":
        errors.append("VERIFICATION_ASSESSMENT_SOURCE_NOT_MAIN")
    if verification.get("active_remediation_issues") != [20]:
        errors.append("VERIFICATION_ACTIVE_ISSUES_STALE")
    policy = verification.get("validation_execution_policy", {})
    if policy.get("github_actions") != "DISABLED_BY_USER" or policy.get("workflow_files_allowed") is not False:
        errors.append("VERIFICATION_ACTIONS_POLICY_INVALID")
    if set(policy.get("allowed_execution_modes", [])) != {
        "LOCAL_STATIC_ONE_SHOT", "LOCAL_FULL_GATE", "LOCAL_FINAL_STAGE"
    }:
        errors.append("VERIFICATION_LOCAL_EXECUTION_MODES_INVALID")

    sandbox = verification.get("sandbox_attack_tests", {})
    if sandbox.get("verified_count") != 10 or sandbox.get("required_count") != 12:
        errors.append("VERIFICATION_SANDBOX_SCOPE_COUNT_MISMATCH")
    if set(sandbox.get("unverified", [])) != {"CROSS_TENANT_READ", "CROSS_TENANT_WRITE"}:
        errors.append("VERIFICATION_SANDBOX_UNVERIFIED_SET_MISMATCH")

    if remaining.get("authority") != "status/design-capability-coverage.v2.json":
        errors.append("REMAINING_WORK_AUTHORITY_MISMATCH")
    if remaining.get("subrequirement_authority") != "status/product-subrequirement-coverage.v1.json":
        errors.append("REMAINING_WORK_SUBREQUIREMENT_AUTHORITY_MISMATCH")
    if remaining.get("process_lineage_authority") != "contracts/product-process-lineage.v1.json":
        errors.append("REMAINING_WORK_PROCESS_AUTHORITY_MISMATCH")
    if remaining.get("validation_execution_policy", {}).get("github_actions") != "DISABLED_BY_USER":
        errors.append("REMAINING_WORK_ACTIONS_POLICY_NOT_DISABLED")
    if omission.get("authorities", {}).get("critical_callpaths") != "scripts/validate-critical-callpaths.py":
        errors.append("OMISSION_CRITICAL_CALLPATH_AUTHORITY_MISSING")

    for source, flags in (
        (design.get("assurance", {}), ("final_lock_allowed", "production_go", "commercial_go")),
        (subreq.get("assurance", {}), ("final_claim_allowed",)),
        (omission.get("assurance", {}), ("final_lock_allowed", "production_go", "commercial_go")),
        (verification, ("final_lock", "production_go", "commercial_go")),
        (remaining, ("final_lock_allowed", "production_go", "commercial_go")),
        (process.get("release_gate", {}), ("final_lock_allowed", "production_go", "commercial_go")),
    ):
        for flag in flags:
            if source.get(flag) is not False:
                errors.append(f"UNSAFE_RELEASE_FLAG:{flag}")

    report = {
        "contract": "ONSURE_STATUS_CONSISTENCY_REPORT_V12",
        "decision": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "design_capabilities": len(design_items),
        "product_subrequirements": len(sub_items),
        "workflow_operations": verification.get("workflow_surface_parity", {}).get("dispatcher_operation_count"),
        "process_stages": len(stages),
        "lineage_artifacts": len(artifacts),
        "all_registered_failure_injections": TOTAL_FAILURE_INJECTIONS,
        "implementation_counts": dict(sorted(impl_counts.items())),
        "subrequirement_implementation_counts": dict(sorted(sub_impl.items())),
        "verification_counts": dict(sorted(verify_counts.items())),
        "github_actions": "DISABLED_BY_USER",
        "runtime_execution": "LOCAL_RECEIPT_REQUIRED_CURRENT_MAIN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_STATUS_CONSISTENCY_FAIL", file=__import__("sys").stderr)
        return 1
    print("ONSURE_STATUS_CONSISTENCY_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
