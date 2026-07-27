#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]


def load(relative: str):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def capability_key(capability_id: str) -> str:
    return capability_id.lower().replace("-", "_")


def main() -> int:
    errors: list[str] = []
    design = load("status/design-capability-coverage.v2.json")
    trace = load("contracts/requirements-traceability.v1.json")
    matrix = load("status/implementation-matrix.v1.json")
    omission = load("status/omission-detection-status.v1.json")
    verification = load("status/verification-status.v1.json")
    remaining = load("status/remaining-work-register.v1.json")
    process = load("contracts/product-process-lineage.v1.json")

    design_items = design.get("capabilities", [])
    trace_items = trace.get("items", [])
    design_by_id = {item.get("capability_id"): item for item in design_items}
    trace_by_id = {item.get("id"): item for item in trace_items}
    if len(design_by_id) != 28 or len(design_items) != 28:
        errors.append(f"DESIGN_CAPABILITY_COUNT:{len(design_items)}:{len(design_by_id)}")
    if set(design_by_id) != set(trace_by_id):
        errors.append(
            "TRACE_DESIGN_ID_SET_MISMATCH:"
            f"missing={sorted(set(design_by_id)-set(trace_by_id))}:"
            f"extra={sorted(set(trace_by_id)-set(design_by_id))}"
        )

    implementation_counts = Counter(item.get("implementation_status") for item in design_items)
    verification_counts = Counter(item.get("verification_state") for item in design_items)
    for capability_id, item in design_by_id.items():
        trace_item = trace_by_id.get(capability_id, {})
        if trace_item.get("status") != item.get("implementation_status"):
            errors.append(f"TRACE_DESIGN_IMPLEMENTATION_MISMATCH:{capability_id}")
        if trace_item.get("verification_state") != item.get("verification_state"):
            errors.append(f"TRACE_DESIGN_VERIFICATION_MISMATCH:{capability_id}")
        if item.get("implementation_status") == "IMPLEMENTED" and item.get("verification_state") == "NOT_RUN":
            errors.append(f"IMPLEMENTED_NOT_RUN:{capability_id}")
        if item.get("verification_state") == "PASS" and not item.get("evidence_refs"):
            errors.append(f"PASS_WITHOUT_EVIDENCE:{capability_id}")

    trace_summary = trace.get("summary", {})
    for state, count in implementation_counts.items():
        if trace_summary.get(state.lower()) != count:
            errors.append(f"TRACE_SUMMARY_MISMATCH:{state}:{trace_summary.get(state.lower())}:{count}")
    for state, count in verification_counts.items():
        if trace_summary.get("verification", {}).get(state.lower()) != count:
            errors.append(f"TRACE_VERIFICATION_SUMMARY_MISMATCH:{state}")

    expected_matrix = {capability_key(capability_id): item.get("implementation_status")
                       for capability_id, item in design_by_id.items()}
    if matrix.get("capabilities") != expected_matrix:
        errors.append("IMPLEMENTATION_MATRIX_CAPABILITY_MAP_MISMATCH")
    for state, count in implementation_counts.items():
        if matrix.get("counts", {}).get(state) != count:
            errors.append(f"IMPLEMENTATION_MATRIX_COUNT_MISMATCH:{state}")
    if matrix.get("runtime_source_commit") is not None:
        errors.append("MATRIX_RUNTIME_SOURCE_COMMIT_MUST_BE_NULL")

    stages = process.get("stages", [])
    artifacts = process.get("artifacts", [])
    if len(stages) != 20:
        errors.append(f"PROCESS_STAGE_COUNT:{len(stages)}")
    if len(artifacts) != 20:
        errors.append(f"PROCESS_ARTIFACT_COUNT:{len(artifacts)}")
    if len({item.get("stage_id") for item in stages}) != len(stages):
        errors.append("PROCESS_STAGE_DUPLICATE")
    if len({item.get("artifact_id") for item in artifacts}) != len(artifacts):
        errors.append("PROCESS_ARTIFACT_DUPLICATE")

    coverage = omission.get("coverage", {})
    failure = omission.get("failure_injection", {})
    if coverage.get("design_capabilities") != 28:
        errors.append("OMISSION_DESIGN_COUNT_MISMATCH")
    if coverage.get("product_process_stages") != 20:
        errors.append("OMISSION_PROCESS_COUNT_MISMATCH")
    if coverage.get("lineage_artifacts") != 20:
        errors.append("OMISSION_ARTIFACT_COUNT_MISMATCH")
    if failure.get("total") != 28 or len(failure.get("cases", [])) != 28:
        errors.append("OMISSION_FAILURE_CASE_COUNT_MISMATCH")
    if failure.get("logic_self_test") != "PASS":
        errors.append("OMISSION_LOGIC_SELF_TEST_NOT_PASS")

    design_status = verification.get("design_coverage", {})
    process_status = verification.get("product_process_lineage", {})
    failure_status = verification.get("omission_failure_injection", {})
    if design_status.get("capability_count") != 28:
        errors.append("VERIFICATION_DESIGN_COUNT_MISMATCH")
    if process_status.get("stage_count") != 20 or process_status.get("artifact_count") != 20:
        errors.append("VERIFICATION_PROCESS_COUNT_MISMATCH")
    if failure_status.get("total_cases") != 28 or failure_status.get("logic_self_test") != "PASS":
        errors.append("VERIFICATION_FAILURE_INJECTION_MISMATCH")
    if verification.get("runtime_source_commit") is not None:
        errors.append("VERIFICATION_RUNTIME_SOURCE_COMMIT_MUST_BE_NULL")

    if remaining.get("authority") != "status/design-capability-coverage.v2.json":
        errors.append("REMAINING_WORK_AUTHORITY_MISMATCH")
    if remaining.get("process_lineage_authority") != "contracts/product-process-lineage.v1.json":
        errors.append("REMAINING_WORK_PROCESS_AUTHORITY_MISMATCH")

    for source, flags in (
        (design.get("assurance", {}), ("final_lock_allowed", "production_go", "commercial_go")),
        (omission.get("assurance", {}), ("final_lock_allowed", "production_go", "commercial_go")),
        (verification, ("final_lock", "production_go", "commercial_go")),
        (remaining, ("final_lock_allowed", "production_go", "commercial_go")),
        (process.get("release_gate", {}), ("final_lock_allowed", "production_go", "commercial_go")),
    ):
        for flag in flags:
            if source.get(flag) is not False:
                errors.append(f"UNSAFE_RELEASE_FLAG:{flag}")

    report = {
        "contract": "ONSURE_STATUS_CONSISTENCY_REPORT_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "design_capabilities": len(design_items),
        "process_stages": len(stages),
        "lineage_artifacts": len(artifacts),
        "failure_injections": failure.get("total"),
        "implementation_counts": dict(sorted(implementation_counts.items())),
        "verification_counts": dict(sorted(verification_counts.items())),
        "runtime_execution": "NOT_RUN",
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
