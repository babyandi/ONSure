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
from collections import Counter
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
    "contracts/product-process-lineage.v1.json",
    "status/design-capability-coverage.v2.json",
    "status/product-subrequirement-coverage.v1.json",
    "status/implementation-matrix.v1.json",
    "status/omission-detection-status.v1.json",
    "status/verification-status.v1.json",
    "status/remaining-work-register.v1.json",
    "scripts/validate-product-subrequirements.py",
    "scripts/validate-workflow-surface-parity.py",
    "scripts/validate-critical-callpaths.py",
    "src/main/java/io/onsure/platform/ApprovalAuthorityPaths.java",
    "src/main/java/io/onsure/platform/BoundedProcessRunner.java",
]


def load_json(relative: str) -> dict[str, Any]:
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def digest(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tracked_files() -> list[pathlib.Path]:
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted((ROOT / item.decode("utf-8")).resolve()
                  for item in result.stdout.split(b"\0") if item)


def validate_json(files: list[pathlib.Path], errors: list[str]) -> tuple[dict[str, str], dict[str, str]]:
    json_digests: dict[str, str] = {}
    jsonl_digests: dict[str, str] = {}
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        try:
            if path.suffix == ".json":
                body = json.loads(path.read_text(encoding="utf-8"))
                json_digests[relative] = digest(path)
                if relative.endswith(".schema.json"):
                    if not isinstance(body, dict) or not body.get("$schema") or not body.get("$id"):
                        errors.append(f"SCHEMA_META_INVALID:{relative}")
            elif path.suffix == ".jsonl":
                lines = path.read_text(encoding="utf-8").splitlines()
                if not lines:
                    errors.append(f"JSONL_EMPTY:{relative}")
                for number, line in enumerate(lines, 1):
                    if not line.strip(): errors.append(f"JSONL_BLANK_LINE:{relative}:{number}")
                    else: json.loads(line)
                jsonl_digests[relative] = digest(path)
        except Exception as exc:
            errors.append(f"STRUCTURED_FILE_INVALID:{relative}:{type(exc).__name__}")
    return json_digests, jsonl_digests


def validate_trace_and_matrix(errors: list[str]) -> tuple[Counter[str], Counter[str]]:
    vocabulary = load_json("contracts/status-vocabulary.v1.json")
    impl_allowed = set(vocabulary.get("implementation_states", []))
    verify_allowed = set(vocabulary.get("verification_states", []))
    trace = load_json("contracts/requirements-traceability.v1.json")
    if set(trace.get("allowed_implementation_statuses", [])) != impl_allowed:
        errors.append("TRACE_IMPLEMENTATION_VOCABULARY_MISMATCH")
    if set(trace.get("allowed_verification_states", [])) != verify_allowed:
        errors.append("TRACE_VERIFICATION_VOCABULARY_MISMATCH")
    if trace.get("coverage_level") != "CAPABILITY_GROUP_ONLY_ATOMIC_REQUIREMENTS_PENDING":
        errors.append("TRACE_COVERAGE_LEVEL_OVERCLAIMED")
    items = trace.get("items", [])
    ids: set[str] = set()
    impl = Counter()
    verify = Counter()
    for item in items:
        item_id = str(item.get("id", ""))
        if not item_id or item_id in ids: errors.append(f"TRACE_ID_INVALID_OR_DUPLICATE:{item_id}")
        ids.add(item_id)
        state = item.get("status")
        verification = item.get("verification_state")
        if state not in impl_allowed: errors.append(f"TRACE_IMPLEMENTATION_STATUS_INVALID:{item_id}:{state}")
        else: impl[state] += 1
        if verification not in verify_allowed: errors.append(f"TRACE_VERIFICATION_STATUS_INVALID:{item_id}:{verification}")
        else: verify[verification] += 1
        for field in ("design_refs", "contract_refs", "code_refs", "test_refs", "evidence_refs"):
            refs = item.get(field)
            if not isinstance(refs, list):
                errors.append(f"TRACE_REFS_NOT_LIST:{item_id}:{field}")
                continue
            for relative in refs:
                if not (ROOT / relative).exists(): errors.append(f"TRACE_REF_MISSING:{item_id}:{field}:{relative}")
        if verification == "PASS" and not item.get("evidence_refs"):
            errors.append(f"TRACE_PASS_WITHOUT_EVIDENCE:{item_id}")
    summary = trace.get("summary", {})
    for state, count in impl.items():
        if summary.get(state.lower()) != count: errors.append(f"TRACE_IMPLEMENTATION_SUMMARY_MISMATCH:{state}")
    for state, count in verify.items():
        if summary.get("verification", {}).get(state.lower()) != count:
            errors.append(f"TRACE_VERIFICATION_SUMMARY_MISMATCH:{state}")

    matrix = load_json("status/implementation-matrix.v1.json")
    calculated = Counter(matrix.get("capabilities", {}).values())
    for state, count in calculated.items():
        if matrix.get("counts", {}).get(state) != count: errors.append(f"MATRIX_COUNT_MISMATCH:{state}")
        if impl.get(state) != count: errors.append(f"MATRIX_TRACE_MISMATCH:{state}")
    if matrix.get("runtime_source_commit") is not None:
        errors.append("MATRIX_RUNTIME_SOURCE_MUST_BE_RECEIPT_BOUND_NOT_STATIC")
    return impl, verify


def validate_boundaries(errors: list[str]) -> None:
    boundary = load_json("contracts/core-extension-boundary.v1.json")
    profiles = boundary.get("preflight_profiles", {})
    if profiles.get("core", {}).get("default") is not True:
        errors.append("CORE_PROFILE_NOT_DEFAULT")
    if profiles.get("core", {}).get("requires_optional_adapters"):
        errors.append("CORE_PROFILE_REQUIRES_OPTIONAL_ADAPTER")
    oruda = [item for item in boundary.get("optional_adapters", [])
             if item.get("adapter_id") == "ORUDA_V1"]
    if len(oruda) != 1 or oruda[0].get("required_for_core") is not False:
        errors.append("ORUDA_NOT_OPTIONAL_IN_CONTRACT")

    mapping = load_json("contracts/state-model-mapping.v1.json")
    required = {"program_profile", "validation_run", "improvement", "git_delivery", "assurance_publication"}
    if set(mapping.get("machines", {})) != required:
        errors.append("STATE_MACHINE_SET_INVALID")
    rules = set(mapping.get("mapping_rules", []))
    for rule in {
        "VALIDATION_EVIDENCE_LOCKED_DOES_NOT_IMPLY_FINAL_PASS",
        "MERGED_DOES_NOT_IMPLY_PRODUCTION_GO",
        "NOT_RUN_BLOCKED_HOLD_INCONCLUSIVE_CANNOT_MAP_TO_PASS",
    }:
        if rule not in rules: errors.append(f"STATE_MAPPING_RULE_MISSING:{rule}")


def validate_granular_meta(errors: list[str]) -> None:
    subreq = load_json("status/product-subrequirement-coverage.v1.json")
    items = subreq.get("requirements", [])
    if len(items) != 38 or len({item.get("id") for item in items}) != 38:
        errors.append("PRODUCT_SUBREQUIREMENT_COUNT_INVALID")
    counts = Counter(item.get("implementation_status") for item in items)
    expected = {"total": 38, "implemented": counts["IMPLEMENTED"], "partial": counts["PARTIAL"],
                "stub": counts["STUB"], "design_only": counts["DESIGN_ONLY"]}
    for field, value in expected.items():
        if subreq.get("summary", {}).get(field) != value:
            errors.append(f"PRODUCT_SUBREQUIREMENT_SUMMARY_MISMATCH:{field}")

    omission = load_json("status/omission-detection-status.v1.json")
    coverage = omission.get("coverage", {})
    for field, value in {"design_capabilities": 28, "product_subrequirements": 38,
                         "workflow_operations": 39, "workflow_surfaces": 3,
                         "product_process_stages": 20, "lineage_artifacts": 20}.items():
        if coverage.get(field) != value: errors.append(f"OMISSION_COVERAGE_MISMATCH:{field}")
    additional = omission.get("additional_failure_injection", {})
    for field, value in {"atomic_requirement_cases": 10, "automation_boundary_cases": 6,
                         "verification_claim_cases": 10, "product_subrequirement_cases": 10,
                         "workflow_surface_cases": 6, "critical_callpath_cases": 10,
                         "all_registered_cases": 80}.items():
        if additional.get(field) != value: errors.append(f"OMISSION_FAILURE_COUNT_MISMATCH:{field}")

    status = load_json("status/verification-status.v1.json")
    if status.get("omission_failure_injection", {}).get("all_registered_failure_injections") != 80:
        errors.append("VERIFICATION_FAILURE_TOTAL_STALE")
    authority = status.get("approval_authority_boundary", {})
    if authority.get("authority_root") != ".onsure/approval-authority/":
        errors.append("APPROVAL_AUTHORITY_ROOT_INVALID")
    if authority.get("request_path_override_allowed") is not False:
        errors.append("APPROVAL_AUTHORITY_OVERRIDE_ALLOWED")
    if authority.get("external_replay_anchor") != "NOT_IMPLEMENTED":
        errors.append("APPROVAL_EXTERNAL_ANCHOR_OVERCLAIMED")
    if status.get("runtime_source_commit") is not None:
        errors.append("VERIFICATION_STATUS_STATIC_RUNTIME_COMMIT_FORBIDDEN")
    if any(status.get(key) is not False for key in ("final_lock", "production_go", "commercial_go")):
        errors.append("VERIFICATION_STATUS_UNSAFE_GO_CLAIM")


def validate_markdown_links(files: list[pathlib.Path], errors: list[str]) -> None:
    pattern = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
    for document in [path for path in files if path.suffix == ".md"]:
        for raw in pattern.findall(document.read_text(encoding="utf-8", errors="replace")):
            target = raw.strip().split(" ", 1)[0].strip("<>")
            if target.startswith(("http://", "https://", "mailto:", "#")): continue
            normalized = target.split("#", 1)[0]
            if normalized and not any(candidate.exists() for candidate in
                    ((document.parent / normalized).resolve(), (ROOT / normalized).resolve())):
                errors.append(f"MARKDOWN_LINK_MISSING:{document.relative_to(ROOT)}:{target}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    errors: list[str] = []
    for relative in REQUIRED_FILES:
        if not (ROOT / relative).is_file(): errors.append(f"MISSING_REQUIRED_FILE:{relative}")
    try: files = tracked_files()
    except RuntimeError as exc:
        files = []
        errors.append(str(exc))
    json_digests, jsonl_digests = validate_json(files, errors)
    impl, verify = validate_trace_and_matrix(errors)
    validate_boundaries(errors)
    validate_granular_meta(errors)
    validate_markdown_links(files, errors)
    report = {
        "contract": "ONSURE_REPOSITORY_STATIC_CONTRACT_REPORT_V3",
        "decision": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "implementation_counts": dict(sorted(impl.items())),
        "verification_counts": dict(sorted(verify.items())),
        "product_subrequirements": 38,
        "workflow_operations": 39,
        "registered_failure_injections": 80,
        "json_contract_digests": json_digests,
        "jsonl_digests": jsonl_digests,
        "runtime_execution": "NOT_RUN",
        "final_claim_allowed": False,
    }
    text = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")
    if errors:
        print("ONSURE_REPOSITORY_CONTRACTS_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_REPOSITORY_CONTRACTS_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
