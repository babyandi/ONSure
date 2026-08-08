#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKFLOW_AUTHORITY = "contracts/workflow-operation-registry.v1.json"


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

    report = {
        "contract": "ONSURE_STATUS_CONSISTENCY_REPORT_V16",
        "decision": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "workflow_operation_authority": WORKFLOW_AUTHORITY,
        "workflow_operation_count": len(operations),
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
