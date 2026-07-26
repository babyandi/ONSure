#!/usr/bin/env python3
"""Fail-closed static validation for ONSure design, traceability and status contracts."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
ALLOWED = {
    "IMPLEMENTED", "PARTIAL", "STUB", "DESIGN_ONLY",
    "NOT_RUN", "BLOCKED", "CONFLICT", "DEPRECATED",
}
REQUIRED_FILES = [
    "docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md",
    "docs/verification/ONSURE_FULL_DESIGN_GAP_ASSESSMENT_v1.md",
    "contracts/status-vocabulary.v1.json",
    "contracts/core-extension-boundary.v1.json",
    "contracts/state-model-mapping.v1.json",
    "contracts/requirements-traceability.v1.json",
    "contracts/program-profile.v1.schema.json",
    "contracts/behavior-profile.v1.schema.json",
    "contracts/failure-memory.v1.schema.json",
    "contracts/improvement-memory.v1.schema.json",
    "contracts/evidence-receipt.v1.schema.json",
    "status/implementation-matrix.v1.json",
    "status/design-conflict-register.v1.json",
    "status/missing-capability-register.v1.json",
]


def load_json(relative: str) -> dict[str, Any]:
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_required_files(errors: list[str]) -> None:
    for relative in REQUIRED_FILES:
        if not (ROOT / relative).is_file():
            errors.append(f"MISSING_REQUIRED_FILE:{relative}")


def validate_json_files(errors: list[str]) -> dict[str, str]:
    digests: dict[str, str] = {}
    for path in sorted(ROOT.rglob("*.json")):
        if any(part in {"target", ".git", ".onsure"} for part in path.parts):
            continue
        try:
            json.loads(path.read_text(encoding="utf-8"))
            digests[str(path.relative_to(ROOT)).replace("\\", "/")] = sha256(path)
        except Exception as exc:  # noqa: BLE001 - report malformed contract
            errors.append(f"JSON_INVALID:{path.relative_to(ROOT)}:{type(exc).__name__}")
    return digests


def validate_traceability(errors: list[str]) -> dict[str, int]:
    trace = load_json("contracts/requirements-traceability.v1.json")
    items = trace.get("items", [])
    seen: set[str] = set()
    counts = {state: 0 for state in ALLOWED}
    for item in items:
        item_id = item.get("id")
        status = item.get("status")
        if not item_id or item_id in seen:
            errors.append(f"TRACE_ID_INVALID_OR_DUPLICATE:{item_id}")
        seen.add(str(item_id))
        if status not in ALLOWED:
            errors.append(f"TRACE_STATUS_INVALID:{item_id}:{status}")
            continue
        counts[status] += 1
        for key in ("design_refs", "contract_refs", "code_refs", "test_refs"):
            refs = item.get(key, [])
            if not isinstance(refs, list):
                errors.append(f"TRACE_REFS_NOT_LIST:{item_id}:{key}")
                continue
            for relative in refs:
                if not (ROOT / relative).exists():
                    errors.append(f"TRACE_REF_MISSING:{item_id}:{key}:{relative}")
    declared = trace.get("summary", {})
    for state, count in counts.items():
        key = state.lower()
        if declared.get(key) != count:
            errors.append(f"TRACE_SUMMARY_MISMATCH:{state}:{declared.get(key)}:{count}")
    return counts


def validate_matrix(errors: list[str], trace_counts: dict[str, int]) -> None:
    matrix = load_json("status/implementation-matrix.v1.json")
    capabilities = matrix.get("capabilities", {})
    calculated = {state: 0 for state in ALLOWED}
    for capability, status in capabilities.items():
        if status not in ALLOWED:
            errors.append(f"MATRIX_STATUS_INVALID:{capability}:{status}")
        else:
            calculated[status] += 1
    declared = matrix.get("counts", {})
    for state, count in calculated.items():
        if declared.get(state) != count:
            errors.append(f"MATRIX_COUNT_MISMATCH:{state}:{declared.get(state)}:{count}")
        if trace_counts.get(state) != count:
            errors.append(f"MATRIX_TRACE_MISMATCH:{state}:{trace_counts.get(state)}:{count}")


def validate_boundary(errors: list[str]) -> None:
    boundary = load_json("contracts/core-extension-boundary.v1.json")
    profiles = boundary.get("preflight_profiles", {})
    if profiles.get("core", {}).get("default") is not True:
        errors.append("CORE_PROFILE_NOT_DEFAULT")
    if profiles.get("core", {}).get("requires_optional_adapters"):
        errors.append("CORE_PROFILE_REQUIRES_OPTIONAL_ADAPTER")
    optional = boundary.get("optional_adapters", [])
    oruda = [item for item in optional if item.get("adapter_id") == "ORUDA_V1"]
    if len(oruda) != 1 or oruda[0].get("required_for_core") is not False:
        errors.append("ORUDA_NOT_OPTIONAL")


def validate_state_mapping(errors: list[str]) -> None:
    mapping = load_json("contracts/state-model-mapping.v1.json")
    machines = mapping.get("machines", {})
    required = {"program_profile", "validation_run", "improvement", "git_delivery", "assurance_publication"}
    if set(machines) != required:
        errors.append(f"STATE_MACHINE_SET_INVALID:{sorted(machines)}")
    rules = set(mapping.get("mapping_rules", []))
    for required_rule in {
        "VALIDATION_EVIDENCE_LOCKED_DOES_NOT_IMPLY_FINAL_PASS",
        "MERGED_DOES_NOT_IMPLY_PRODUCTION_GO",
        "NOT_RUN_BLOCKED_HOLD_INCONCLUSIVE_CANNOT_MAP_TO_PASS",
    }:
        if required_rule not in rules:
            errors.append(f"STATE_MAPPING_RULE_MISSING:{required_rule}")


def validate_readme_links(errors: list[str]) -> None:
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", readme):
        if target.startswith(("http://", "https://", "#")):
            continue
        normalized = target.split("#", 1)[0]
        if normalized and not (ROOT / normalized).exists():
            errors.append(f"README_LINK_MISSING:{target}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()

    errors: list[str] = []
    validate_required_files(errors)
    json_digests = validate_json_files(errors)
    counts = validate_traceability(errors)
    validate_matrix(errors, counts)
    validate_boundary(errors)
    validate_state_mapping(errors)
    validate_readme_links(errors)

    report = {
        "contract": "ONSURE_REPOSITORY_STATIC_CONTRACT_REPORT_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "traceability_counts": dict(sorted(counts.items())),
        "json_contract_digests": json_digests,
        "final_claim_allowed": False,
    }
    serialized = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        output = args.output.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(serialized, encoding="utf-8")
    print(serialized, end="")
    if errors:
        print("ONSURE_REPOSITORY_CONTRACTS_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_REPOSITORY_CONTRACTS_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
