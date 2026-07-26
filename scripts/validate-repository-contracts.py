#!/usr/bin/env python3
"""Fail-closed tracked-file validation for ONSure design, traceability and status contracts."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIRED_FILES = [
    "docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md",
    "docs/verification/ONSURE_FULL_DESIGN_GAP_ASSESSMENT_v1.md",
    "docs/verification/ONSURE_POST_MERGE_SELF_AUDIT_v1.md",
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
    "status/verification-status.v1.json",
]


def load_json(relative: str) -> dict[str, Any]:
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tracked_files() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False
    )
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    values = [item for item in result.stdout.split(b"\0") if item]
    return sorted((ROOT / item.decode("utf-8")).resolve() for item in values)


def files_with_suffix(files: list[pathlib.Path], suffix: str) -> list[pathlib.Path]:
    return [path for path in files if path.name.endswith(suffix)]


def validate_required_files(errors: list[str]) -> None:
    for relative in REQUIRED_FILES:
        if not (ROOT / relative).is_file():
            errors.append(f"MISSING_REQUIRED_FILE:{relative}")


def validate_json_files(files: list[pathlib.Path], errors: list[str]) -> dict[str, str]:
    digests: dict[str, str] = {}
    for path in files_with_suffix(files, ".json"):
        relative = str(path.relative_to(ROOT)).replace("\\", "/")
        try:
            body = json.loads(path.read_text(encoding="utf-8"))
            digests[relative] = sha256(path)
            if relative.endswith(".schema.json"):
                if not isinstance(body, dict):
                    errors.append(f"SCHEMA_NOT_OBJECT:{relative}")
                    continue
                if not body.get("$schema"):
                    errors.append(f"SCHEMA_META_MISSING:{relative}:$schema")
                if not body.get("$id"):
                    errors.append(f"SCHEMA_META_MISSING:{relative}:$id")
                if not any(key in body for key in ("type", "oneOf", "anyOf", "allOf", "$ref")):
                    errors.append(f"SCHEMA_ROOT_CONSTRAINT_MISSING:{relative}")
        except Exception as exc:  # noqa: BLE001
            errors.append(f"JSON_INVALID:{relative}:{type(exc).__name__}")
    return digests


def validate_jsonl_files(files: list[pathlib.Path], errors: list[str]) -> dict[str, str]:
    digests: dict[str, str] = {}
    for path in files_with_suffix(files, ".jsonl"):
        relative = str(path.relative_to(ROOT)).replace("\\", "/")
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
            if not lines:
                errors.append(f"JSONL_EMPTY:{relative}")
                continue
            for line_number, line in enumerate(lines, start=1):
                if not line.strip():
                    errors.append(f"JSONL_BLANK_LINE:{relative}:{line_number}")
                    continue
                json.loads(line)
            digests[relative] = sha256(path)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"JSONL_INVALID:{relative}:{type(exc).__name__}")
    return digests


def vocabulary() -> tuple[set[str], set[str]]:
    body = load_json("contracts/status-vocabulary.v1.json")
    return set(body.get("implementation_states", [])), set(body.get("verification_states", []))


def validate_traceability(errors: list[str]) -> tuple[dict[str, int], dict[str, int]]:
    implementation_allowed, verification_allowed = vocabulary()
    trace = load_json("contracts/requirements-traceability.v1.json")
    if set(trace.get("allowed_implementation_statuses", [])) != implementation_allowed:
        errors.append("TRACE_IMPLEMENTATION_VOCABULARY_MISMATCH")
    if set(trace.get("allowed_verification_states", [])) != verification_allowed:
        errors.append("TRACE_VERIFICATION_VOCABULARY_MISMATCH")
    if trace.get("coverage_level") != "CAPABILITY_GROUP_ONLY_ATOMIC_REQUIREMENTS_PENDING":
        errors.append("TRACE_COVERAGE_LEVEL_MISSING_OR_OVERCLAIMED")

    items = trace.get("items", [])
    seen: set[str] = set()
    implementation_counts = {state: 0 for state in implementation_allowed}
    verification_counts = {state: 0 for state in verification_allowed}
    for item in items:
        item_id = item.get("id")
        status = item.get("status")
        verification_state = item.get("verification_state")
        if not item_id or item_id in seen:
            errors.append(f"TRACE_ID_INVALID_OR_DUPLICATE:{item_id}")
        seen.add(str(item_id))
        if status not in implementation_allowed:
            errors.append(f"TRACE_IMPLEMENTATION_STATUS_INVALID:{item_id}:{status}")
        else:
            implementation_counts[status] += 1
        if verification_state not in verification_allowed:
            errors.append(f"TRACE_VERIFICATION_STATUS_INVALID:{item_id}:{verification_state}")
        else:
            verification_counts[verification_state] += 1

        for key in ("design_refs", "contract_refs", "code_refs", "test_refs", "evidence_refs"):
            refs = item.get(key)
            if not isinstance(refs, list):
                errors.append(f"TRACE_REFS_NOT_LIST:{item_id}:{key}")
                continue
            for relative in refs:
                if not (ROOT / relative).exists():
                    errors.append(f"TRACE_REF_MISSING:{item_id}:{key}:{relative}")
        if verification_state == "PASS" and not item.get("evidence_refs"):
            errors.append(f"TRACE_PASS_WITHOUT_EVIDENCE:{item_id}")

    declared = trace.get("summary", {})
    for state, count in implementation_counts.items():
        if declared.get(state.lower()) != count:
            errors.append(
                f"TRACE_IMPLEMENTATION_SUMMARY_MISMATCH:{state}:"
                f"{declared.get(state.lower())}:{count}"
            )
    declared_verification = declared.get("verification", {})
    for state, count in verification_counts.items():
        if declared_verification.get(state.lower()) != count:
            errors.append(
                f"TRACE_VERIFICATION_SUMMARY_MISMATCH:{state}:"
                f"{declared_verification.get(state.lower())}:{count}"
            )
    return implementation_counts, verification_counts


def validate_matrix(errors: list[str], trace_counts: dict[str, int]) -> None:
    implementation_allowed, _ = vocabulary()
    matrix = load_json("status/implementation-matrix.v1.json")
    calculated = {state: 0 for state in implementation_allowed}
    for capability, status in matrix.get("capabilities", {}).items():
        if status not in implementation_allowed:
            errors.append(f"MATRIX_STATUS_INVALID:{capability}:{status}")
        else:
            calculated[status] += 1
    declared = matrix.get("counts", {})
    for state, count in calculated.items():
        if declared.get(state) != count:
            errors.append(f"MATRIX_COUNT_MISMATCH:{state}:{declared.get(state)}:{count}")
        if trace_counts.get(state) != count:
            errors.append(f"MATRIX_TRACE_MISMATCH:{state}:{trace_counts.get(state)}:{count}")
    if matrix.get("runtime_source_commit") is not None:
        errors.append("MATRIX_RUNTIME_SOURCE_MUST_BE_RECEIPT_BOUND_NOT_STATIC")


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
        errors.append("ORUDA_NOT_OPTIONAL_IN_CONTRACT")


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


def validate_markdown_links(files: list[pathlib.Path], errors: list[str]) -> None:
    pattern = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
    for document in files_with_suffix(files, ".md"):
        relative_document = str(document.relative_to(ROOT)).replace("\\", "/")
        text = document.read_text(encoding="utf-8", errors="replace")
        for raw_target in pattern.findall(text):
            target = raw_target.strip().split(" ", 1)[0].strip("<>")
            if target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            normalized = target.split("#", 1)[0]
            if not normalized:
                continue
            candidates = [
                (document.parent / normalized).resolve(),
                (ROOT / normalized).resolve(),
            ]
            if not any(candidate.exists() for candidate in candidates):
                errors.append(f"MARKDOWN_LINK_MISSING:{relative_document}:{target}")


def validate_verification_status(errors: list[str]) -> None:
    body = load_json("status/verification-status.v1.json")
    if body.get("runtime_source_commit") is not None:
        errors.append("VERIFICATION_STATUS_STATIC_RUNTIME_COMMIT_FORBIDDEN")
    if body.get("runtime_source_binding_state") != "PENDING_ONE_SHOT_RECEIPT":
        errors.append("VERIFICATION_STATUS_RUNTIME_BINDING_INVALID")
    if any(body.get(key) is not False for key in ("final_lock", "production_go", "commercial_go")):
        errors.append("VERIFICATION_STATUS_UNSAFE_GO_CLAIM")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()

    errors: list[str] = []
    try:
        files = tracked_files()
    except RuntimeError as exc:
        files = []
        errors.append(str(exc))
    validate_required_files(errors)
    json_digests = validate_json_files(files, errors)
    jsonl_digests = validate_jsonl_files(files, errors)
    implementation_counts, verification_counts = validate_traceability(errors)
    validate_matrix(errors, implementation_counts)
    validate_boundary(errors)
    validate_state_mapping(errors)
    validate_markdown_links(files, errors)
    validate_verification_status(errors)

    report = {
        "contract": "ONSURE_REPOSITORY_STATIC_CONTRACT_REPORT_V2",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "implementation_counts": dict(sorted(implementation_counts.items())),
        "verification_counts": dict(sorted(verification_counts.items())),
        "json_contract_digests": json_digests,
        "jsonl_digests": jsonl_digests,
        "limitations": {
            "json_schema_instance_validation": "NOT_RUN",
            "yaml_parser_validation": "NOT_RUN",
            "runtime_execution": "NOT_RUN",
            "atomic_requirement_coverage": "NOT_COMPLETE",
        },
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
