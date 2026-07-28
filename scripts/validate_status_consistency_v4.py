#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import subprocess
import sys
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]
COUNT_AUTHORITY = "contracts/omission-failure-injection-counts.v1.json"
WORKFLOW_AUTHORITY = "contracts/workflow-operation-registry.v1.json"


def load(relative: str):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def run(relative: str, marker: str, *arguments: str) -> list[str]:
    result = subprocess.run([sys.executable, relative, *arguments], cwd=ROOT, text=True, capture_output=True, check=False)
    combined = result.stdout + result.stderr
    return [] if result.returncode == 0 and marker in combined else [f"DELEGATED_VALIDATOR_FAIL:{relative}:{result.returncode}:{combined[-2400:]}"]


def summary(items: list[dict], status_field: str = "implementation_status") -> dict[str, int]:
    impl = Counter(item.get(status_field) for item in items)
    verify = Counter(item.get("verification_state") for item in items)
    return {"total":len(items),"implemented":impl.get("IMPLEMENTED",0),"partial":impl.get("PARTIAL",0),"stub":impl.get("STUB",0),"design_only":impl.get("DESIGN_ONLY",0),"verification_not_run":verify.get("NOT_RUN",0)}


def main() -> int:
    errors: list[str] = []
    errors += run("scripts/validate-design-coverage.py", '"decision": "PASS"', "--matrix", "status/design-capability-coverage.v2.json", "--root", ".", "--self-test")
    errors += run("scripts/validate-legacy-product-subrequirements.py", "ONSURE_LEGACY_PRODUCT_SUBREQUIREMENT_GATE_PASS", "--self-test")
    errors += run("scripts/validate-mvp-acceptance-coverage.py", "ONSURE_MVP_ACCEPTANCE_GATE_PASS", "--self-test")
    errors += run("scripts/validate-final-product-requirements.py", "ONSURE_FINAL_PRODUCT_REQUIREMENT_GATE_PASS", "--self-test")
    errors += run("scripts/validate-final-acceptance-coverage.py", "ONSURE_FINAL_ACCEPTANCE_GATE_PASS", "--self-test")
    errors += run("scripts/validate-workflow-surface-parity.py", "ONSURE_WORKFLOW_SURFACE_PARITY_PASS", "--self-test")
    errors += run("scripts/validate-critical-callpaths.py", "ONSURE_CRITICAL_CALLPATH_PASS", "--self-test")

    counts = load(COUNT_AUTHORITY); count_values = counts.get("counts", {}); total = counts.get("total")
    if counts.get("contract") != "ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1" or total != sum(count_values.values()):
        errors.append("FAILURE_COUNT_AUTHORITY_INVALID")
    workflow = load(WORKFLOW_AUTHORITY); operations = workflow.get("operations", [])
    if workflow.get("contract") != "ONSURE_WORKFLOW_OPERATION_REGISTRY_V1" or workflow.get("operation_count") != len(operations) or len(operations) != len(set(operations)):
        errors.append("WORKFLOW_AUTHORITY_INVALID")

    design = load("status/design-capability-coverage.v2.json")
    trace = load("contracts/requirements-traceability.v1.json")
    matrix = load("status/implementation-matrix.v1.json")
    design_items = design.get("capabilities", []); trace_items = trace.get("items", [])
    design_by_id = {item.get("capability_id"):item for item in design_items}; trace_by_id = {item.get("id"):item for item in trace_items}
    if len(design_items) != 28 or set(design_by_id) != set(trace_by_id): errors.append("DESIGN_TRACE_AUTHORITY_MISMATCH")
    for key,item in design_by_id.items():
        traced = trace_by_id.get(key,{})
        if traced.get("status") != item.get("implementation_status") or traced.get("verification_state") != item.get("verification_state"):
            errors.append(f"DESIGN_TRACE_STATE_MISMATCH:{key}")
    expected_matrix = {key.lower().replace("-","_"):item.get("implementation_status") for key,item in design_by_id.items()}
    if matrix.get("capabilities") != expected_matrix or matrix.get("runtime_source_commit") is not None:
        errors.append("IMPLEMENTATION_MATRIX_MISMATCH")

    legacy_authority = load("contracts/legacy-product-subrequirement-authority.v1.json")
    legacy = load("status/product-subrequirement-coverage.v1.json")
    legacy_mvp = load("status/mvp-acceptance-coverage.v1.json")
    final_req = load("status/final-product-requirement-coverage.v1.json")
    final_acc = load("status/final-acceptance-coverage.v1.json")
    final_source = load("contracts/final-acceptance-source-registry.v1.json")
    verification = load("status/verification-status.v1.json")
    omission = load("status/omission-detection-status.v1.json")
    remaining = load("status/remaining-work-register.v1.json")
    process = load("contracts/product-process-lineage.v1.json")

    if legacy_authority.get("may_satisfy_final_requirement") is not False or legacy_authority.get("may_satisfy_final_acceptance") is not False:
        errors.append("LEGACY_AUTHORITY_ESCALATION")
    if legacy_mvp.get("authority_state") != "LEGACY_MVP_NONAUTHORITATIVE_FOR_FINAL_PRODUCT": errors.append("LEGACY_MVP_AUTHORITY_STATE_INVALID")
    legacy_summary = summary(legacy.get("requirements", [])); final_summary = summary(final_req.get("requirements", []))
    if legacy.get("summary") != legacy_summary: errors.append("LEGACY_REQUIREMENT_SUMMARY_MISMATCH")
    if final_req.get("summary") != final_summary: errors.append("FINAL_REQUIREMENT_SUMMARY_MISMATCH")
    if len(final_req.get("requirements", [])) != 22: errors.append("FINAL_REQUIREMENT_COUNT_MISMATCH")
    if final_source.get("total_expected_items") != 61 or final_acc.get("summary",{}).get("registered_items") != 61:
        errors.append("FINAL_ACCEPTANCE_COUNT_MISMATCH")
    if any(item.get("state") != "NOT_RUN" for item in final_acc.get("groups", [])):
        errors.append("FINAL_ACCEPTANCE_GROUP_OVERCLAIM")

    status_legacy = verification.get("legacy_product_decomposition",{}); status_final = verification.get("final_product_requirement_coverage",{}); status_accept = verification.get("final_acceptance_coverage",{})
    if status_legacy.get("requirement_count") != legacy_summary["total"] or status_legacy.get("state") != "LEGACY_NONAUTHORITATIVE_FOR_FINAL_PRODUCT": errors.append("VERIFICATION_LEGACY_REQUIREMENT_STATE_MISMATCH")
    for field in ("total","implemented","partial","stub","design_only"):
        status_field = "requirement_count" if field == "total" else field
        if status_final.get(status_field) != final_summary[field]: errors.append(f"VERIFICATION_FINAL_REQUIREMENT_SUMMARY_MISMATCH:{field}")
    if status_accept.get("acceptance_item_count") != 61 or status_accept.get("state") != "NOT_RUN_ALL_61_FINAL_ACCEPTANCE_CRITERIA": errors.append("VERIFICATION_FINAL_ACCEPTANCE_STATE_MISMATCH")
    if verification.get("workflow_surface_parity",{}).get("authority") != WORKFLOW_AUTHORITY or verification.get("workflow_surface_parity",{}).get("dispatcher_operation_count") != len(operations): errors.append("VERIFICATION_WORKFLOW_AUTHORITY_MISMATCH")
    failure = verification.get("omission_failure_injection",{})
    if failure.get("authority") != COUNT_AUTHORITY or failure.get("all_registered_failure_injections") != total: errors.append("VERIFICATION_FAILURE_AUTHORITY_MISMATCH")
    for field,value in count_values.items():
        if failure.get(field) != value: errors.append(f"VERIFICATION_FAILURE_COUNT_MISMATCH:{field}")

    coverage = omission.get("coverage",{})
    expected_coverage = {"design_capabilities":28,"legacy_product_subrequirements":legacy_summary["total"],"legacy_mvp_acceptance_items":len(legacy_mvp.get("acceptance_items",[])),"final_product_requirements":22,"final_acceptance_items":61,"workflow_operations":len(operations),"workflow_surfaces":3,"product_process_stages":len(process.get("stages",[])),"lineage_artifacts":len(process.get("artifacts",[]))}
    for field,value in expected_coverage.items():
        if coverage.get(field) != value: errors.append(f"OMISSION_COVERAGE_MISMATCH:{field}")
    additional = omission.get("additional_failure_injection",{})
    if additional.get("authority") != COUNT_AUTHORITY or additional.get("all_registered_cases") != total: errors.append("OMISSION_FAILURE_AUTHORITY_MISMATCH")
    for field,value in count_values.items():
        if additional.get(field) != value: errors.append(f"OMISSION_FAILURE_COUNT_MISMATCH:{field}")

    if remaining.get("failure_injection_authority") != COUNT_AUTHORITY or remaining.get("final_product_requirement_authority") != "status/final-product-requirement-coverage.v1.json" or remaining.get("final_acceptance_authority") != "status/final-acceptance-coverage.v1.json": errors.append("REMAINING_FINAL_AUTHORITY_MISMATCH")
    readme = (ROOT/"README.md").read_text(encoding="utf-8")
    for token in (f"합계 {total}개", "22개 Final 요구", "61개 Final 수용기준", f"— {len(operations)}개 Workflow"):
        if token not in readme: errors.append(f"README_AUTHORITY_SUMMARY_MISMATCH:{token}")

    if verification.get("runtime_source_commit") is not None or verification.get("assessment_source_ref") != "main": errors.append("VERIFICATION_SOURCE_STATE_INVALID")
    policy = verification.get("validation_execution_policy",{})
    if policy.get("github_actions") != "DISABLED_BY_USER" or policy.get("workflow_files_allowed") is not False: errors.append("ACTIONS_POLICY_INVALID")
    for source, flags in ((design.get("assurance",{}),("final_lock_allowed","production_go","commercial_go")),(legacy.get("assurance",{}),("final_claim_allowed",)),(legacy_mvp.get("assurance",{}),("final_claim_allowed",)),(final_req.get("assurance",{}),("final_claim_allowed",)),(final_acc.get("assurance",{}),("final_claim_allowed",)),(omission.get("assurance",{}),("final_lock_allowed","production_go","commercial_go")),(verification,("final_lock","production_go","commercial_go")),(remaining,("final_lock_allowed","production_go","commercial_go")),(process.get("release_gate",{}),("final_lock_allowed","production_go","commercial_go"))):
        for flag in flags:
            if source.get(flag) is not False: errors.append(f"UNSAFE_RELEASE_FLAG:{flag}")

    report = {"contract":"ONSURE_STATUS_CONSISTENCY_REPORT_V20","decision":"PASS" if not errors else "FAIL","errors":sorted(set(errors)),"legacy_product_requirements":legacy_summary["total"],"final_product_requirements":22,"final_acceptance_items":61,"workflow_operations":len(operations),"registered_failure_injections":total,"final_claim_allowed":False}
    print(json.dumps(report,ensure_ascii=False,indent=2,sort_keys=True))
    if errors:
        print("ONSURE_STATUS_CONSISTENCY_FAIL",file=sys.stderr);return 1
    print("ONSURE_STATUS_CONSISTENCY_PASS");return 0


if __name__ == "__main__": raise SystemExit(main())
