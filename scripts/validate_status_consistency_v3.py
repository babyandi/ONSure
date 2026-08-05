#!/usr/bin/env python3
from __future__ import annotations

import json
import hashlib
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKFLOW_AUTHORITY = "contracts/workflow-operation-registry.v1.json"
IMPLEMENTATION_IDS = {
    "VALIDATOR-FIXTURE-ENGINE", "CORE-ORUDA-ISOLATION", "FILE-EVIDENCE-STORE",
    "OFFICIAL-LEARNING-LEDGER", "RAG-PREPARATION-CONTROL", "UNATTENDED-RUNNER",
    "PROGRAM-LEARNING", "BEHAVIOR-LEARNING", "OREVIEW", "ATOMIC-TRACEABILITY",
    "OPLANNING", "OIMPROVEMENT-PATCH", "VSCODE-EXTENSION", "GIT-FULL-CHAIN",
    "WEB-COMMERCE-OLICENSE",
}


def load(relative: str):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def run_validator(relative: str, marker: str) -> list[str]:
    result = subprocess.run(
        [sys.executable, relative], cwd=ROOT, text=True,
        capture_output=True, check=False,
    )
    combined = result.stdout + result.stderr
    if result.returncode != 0 or marker not in combined:
        return [f"DELEGATED_VALIDATOR_FAIL:{relative}:{result.returncode}:{combined[-2400:]}"]
    return []


def main() -> int:
    errors: list[str] = []
    errors.extend(run_validator("scripts/validate_status_consistency_v2.py", "ONSURE_STATUS_CONSISTENCY_PASS"))
    errors.extend(run_validator("scripts/validate-mvp-status-consistency.py", "ONSURE_MVP_STATUS_CONSISTENCY_PASS"))
    workflow_result = subprocess.run(
        [sys.executable, "scripts/validate-workflow-surface-parity.py", "--self-test"],
        cwd=ROOT, text=True, capture_output=True, check=False,
    )
    if workflow_result.returncode != 0 or "ONSURE_WORKFLOW_SURFACE_PARITY_PASS" not in (
            workflow_result.stdout + workflow_result.stderr):
        errors.append(f"DELEGATED_WORKFLOW_VALIDATOR_FAIL:{workflow_result.returncode}:{(workflow_result.stdout + workflow_result.stderr)[-2400:]}")

    authority = load(WORKFLOW_AUTHORITY)
    operations = authority.get("operations", [])
    if authority.get("contract") != "ONSURE_WORKFLOW_OPERATION_REGISTRY_V1":
        errors.append("WORKFLOW_AUTHORITY_CONTRACT_INVALID")
    if not isinstance(operations, list) or len(operations) != len(set(operations)):
        errors.append("WORKFLOW_AUTHORITY_OPERATION_LIST_INVALID")
    if authority.get("operation_count") != len(operations):
        errors.append("WORKFLOW_AUTHORITY_COUNT_MISMATCH")

    verification = load("status/verification-status.v1.json")
    omission = load("status/omission-detection-status.v1.json")
    remaining = load("status/remaining-work-register.v1.json")
    workflow_status = verification.get("workflow_surface_parity", {})
    if workflow_status.get("authority") != WORKFLOW_AUTHORITY:
        errors.append("VERIFICATION_WORKFLOW_AUTHORITY_MISSING")
    if workflow_status.get("dispatcher_operation_count") != len(operations):
        errors.append("VERIFICATION_WORKFLOW_COUNT_MISMATCH")
    if omission.get("coverage", {}).get("workflow_operations") != len(operations):
        errors.append("OMISSION_WORKFLOW_COUNT_MISMATCH")
    workflow_item = next((item for item in remaining.get("items", [])
                          if item.get("id") == "P0-WORKFLOW-SURFACE-PARITY"), {})
    if not str(workflow_item.get("state", "")).startswith(f"{len(operations)}_DISPATCHER_OPERATIONS_"):
        errors.append("REMAINING_WORKFLOW_COUNT_MISMATCH")
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if f"— {len(operations)}개 Workflow" not in readme:
        errors.append("README_WORKFLOW_COUNT_MISMATCH")

    implementation = load("status/implementation-status.v1.json")
    implementation_items = implementation.get("items", [])
    implementation_ids = {item.get("id") for item in implementation_items}
    if implementation.get("contract") != "ONSURE_IMPLEMENTATION_STATUS_V1" \
            or implementation.get("version") != "2.3.0":
        errors.append("IMPLEMENTATION_STATUS_AUTHORITY_VERSION_STALE")
    if implementation_ids != IMPLEMENTATION_IDS or len(implementation_items) != len(IMPLEMENTATION_IDS):
        errors.append("IMPLEMENTATION_STATUS_ITEM_SET_MISMATCH")
    for item in implementation_items:
        if item.get("state") not in {"PARTIAL_PASS_NONFINAL", "IMPLEMENTED_PASS_NONFINAL"}:
            errors.append("IMPLEMENTATION_STATUS_STATE_INVALID:" + str(item.get("id")))
        for relative in item.get("evidence", []):
            if not isinstance(relative, str) or not (ROOT / relative).exists():
                errors.append("IMPLEMENTATION_STATUS_EVIDENCE_MISSING:" + str(item.get("id"))
                              + ":" + str(relative))
    summary = implementation.get("summary", {})
    if summary.get("independent_assurance") != "NOT_RUN" \
            or any(summary.get(field) is not False
                   for field in ("final_lock_allowed", "production_go", "commercial_go")):
        errors.append("IMPLEMENTATION_STATUS_UNSAFE_ASSURANCE")

    completion = (ROOT / "docs/development/ONSURE_COMPLETION_CHECKLIST_v1.md").read_text(
        encoding="utf-8")
    for expected in ("Java 419개", "Python 207개", "Node 10"):
        if expected not in (readme + "\n" + completion):
            errors.append("COMPLETION_COUNT_STALE:" + expected)
    gradle_source = (ROOT / "modules/onsure-core/src/main/java/io/onsure/platform/GradleValidationPack.java") \
        .read_text(encoding="utf-8")
    for token in ("negative-paths", "gradle.connected-", "gradle.operations-", "integrationTest"):
        if token not in gradle_source:
            errors.append("GRADLE_STANDARD_PACK_FACET_MISSING:" + token)
    universal_evidence = load("assurance/runtime/onsure-universal-validation-evidence.v1.json")
    universal_runs = {run.get("target_id"): run for run in universal_evidence.get("runs", [])}
    gradle_run = universal_runs.get("gradle", {})
    if set(universal_runs) != {"self", "java", "python", "node", "gradle"} \
            or gradle_run.get("overall_outcome") != "PASS_NONFINAL" \
            or gradle_run.get("technologies") != ["GRADLE", "JAVA"] \
            or gradle_run.get("verified_pass_step_count") != 20 \
            or gradle_run.get("source_mutation_detected") is not False:
        errors.append("UNIVERSAL_EXTERNAL_GRADLE_EVIDENCE_INVALID")
    repeatability = load("assurance/runtime/onsure-self-repeatability.v1.json")
    if repeatability.get("decision") != "PASS_NONFINAL" \
            or repeatability.get("run_count") != 2 \
            or repeatability.get("verified_pass_step_count_per_run") != 26 \
            or repeatability.get("source_mutation_detected") is not False:
        errors.append("UNIVERSAL_SELF_REPEATABILITY_EVIDENCE_INVALID")

    vscode_evidence = load("assurance/runtime/vscode-extension-host-e2e.v1.json")
    claimed_payload = vscode_evidence.pop("evidence_payload_sha256", None)
    actual_payload = hashlib.sha256(json.dumps(
        vscode_evidence, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    if claimed_payload != actual_payload or vscode_evidence.get("decision") != "PASS_NONFINAL":
        errors.append("VSCODE_EXTENSION_HOST_EVIDENCE_INVALID")
    for relative, digest in vscode_evidence.get("source_file_sha256", {}).items():
        file = ROOT / relative
        if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != digest:
            errors.append("VSCODE_EXTENSION_HOST_SOURCE_BINDING_INVALID:" + str(relative))
    remaining_by_id = {item.get("id"): item for item in remaining.get("items", [])}
    stale_states = {
        "P0-VSCODE-LOCAL-API": ("PREVIOUS_HEAD", "EXTENSION_HOST_NOT_RUN"),
        "P0-PUBLIC-SDK": ("STUB",),
        "P0-TENANT-IDENTITY": ("STUB",),
        "P1-PERFORMANCE-RECOVERY": ("DESIGN_ONLY",),
        "P1-OBSERVABILITY-OPERATIONS": ("DESIGN_ONLY",),
        "P1-DEPLOYMENT": ("DESIGN_ONLY",),
    }
    for item_id, prohibited in stale_states.items():
        state = str(remaining_by_id.get(item_id, {}).get("state", ""))
        if not state or any(value in state for value in prohibited):
            errors.append("REMAINING_WORK_STATE_STALE:" + item_id)
    sandbox_state = str(remaining_by_id.get("P0-SANDBOX-ADVERSARIAL-COVERAGE", {}).get("state", ""))
    if "12_OF_12_OCI" not in sandbox_state or "6_ENVIRONMENT_CAPABILITIES" not in sandbox_state:
        errors.append("REMAINING_WORK_SANDBOX_EVIDENCE_STALE")

    report = {
        "contract": "ONSURE_STATUS_CONSISTENCY_REPORT_V23",
        "decision": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "workflow_operation_authority": WORKFLOW_AUTHORITY,
        "workflow_operation_count": len(operations),
        "implementation_status_item_count": len(implementation_items),
        "vscode_extension_host_evidence_payload_sha256": claimed_payload,
        "mvp_acceptance_items": len(load("status/mvp-acceptance-coverage.v1.json").get("acceptance_items", [])),
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_STATUS_CONSISTENCY_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_STATUS_CONSISTENCY_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
