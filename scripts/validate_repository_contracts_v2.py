#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
COUNT_AUTHORITY = "contracts/omission-failure-injection-counts.v1.json"
REQUIRED = [
    "docs/architecture/ONSURE_DESIGN_AUTHORITY_AND_SCOPE_v1.md",
    "docs/verification/ONSURE_FULL_DESIGN_GAP_ASSESSMENT_v1.md",
    "docs/verification/ONSURE_POST_MERGE_SELF_AUDIT_v1.md",
    "contracts/status-vocabulary.v1.json",
    "contracts/core-extension-boundary.v1.json",
    "contracts/state-model-mapping.v1.json",
    "contracts/requirements-traceability.v1.json",
    "contracts/product-process-lineage.v1.json",
    COUNT_AUTHORITY,
    "status/design-capability-coverage.v2.json",
    "status/product-subrequirement-coverage.v1.json",
    "status/mvp-acceptance-coverage.v1.json",
    "status/implementation-matrix.v1.json",
    "status/omission-detection-status.v1.json",
    "status/verification-status.v1.json",
    "status/remaining-work-register.v1.json",
    "scripts/validate-product-subrequirements.py",
    "scripts/validate-mvp-acceptance-coverage.py",
    "scripts/validate-mvp-status-consistency.py",
    "scripts/validate-workflow-surface-parity.py",
    "scripts/validate-critical-callpaths.py",
    "src/main/java/kr/co/oruda/onsure/platform/ApprovalAuthorityPaths.java",
    "src/main/java/kr/co/oruda/onsure/platform/BoundedProcessRunner.java",
]
COMMANDS = [
    ([sys.executable, "scripts/validate-status-consistency.py"], "ONSURE_STATUS_CONSISTENCY_PASS"),
    ([sys.executable, "scripts/validate-product-subrequirements.py", "--self-test"], "ONSURE_PRODUCT_SUBREQUIREMENT_GATE_PASS"),
    ([sys.executable, "scripts/validate-mvp-acceptance-coverage.py", "--self-test"], "ONSURE_MVP_ACCEPTANCE_GATE_PASS"),
    ([sys.executable, "scripts/validate-mvp-status-consistency.py"], "ONSURE_MVP_STATUS_CONSISTENCY_PASS"),
    ([sys.executable, "scripts/validate-workflow-surface-parity.py", "--self-test"], "ONSURE_WORKFLOW_SURFACE_PARITY_PASS"),
    ([sys.executable, "scripts/validate-critical-callpaths.py", "--self-test"], "ONSURE_CRITICAL_CALLPATH_PASS"),
]


def load(relative: str):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def tracked_files() -> list[pathlib.Path]:
    try:
        result = subprocess.run(
            ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True,
            check=False, timeout=20,
        )
    except subprocess.TimeoutExpired as failure:
        raise RuntimeError("GIT_LS_FILES_TIMEOUT") from failure
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    if len(result.stdout) > 32 * 1024 * 1024 or len(result.stderr) > 4 * 1024 * 1024:
        raise RuntimeError("GIT_LS_FILES_OUTPUT_LIMIT")
    return sorted((ROOT / value.decode("utf-8")).resolve()
                  for value in result.stdout.split(b"\0") if value)


def file_digest(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_structured(files: list[pathlib.Path], errors: list[str]):
    json_digests: dict[str, str] = {}
    jsonl_digests: dict[str, str] = {}
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        try:
            if path.suffix == ".json":
                body = json.loads(path.read_text(encoding="utf-8"))
                json_digests[relative] = file_digest(path)
                if relative.endswith(".schema.json"):
                    if not isinstance(body, dict) or not body.get("$schema") or not body.get("$id"):
                        errors.append(f"SCHEMA_META_INVALID:{relative}")
            elif path.suffix == ".jsonl":
                lines = path.read_text(encoding="utf-8").splitlines()
                if not lines:
                    errors.append(f"JSONL_EMPTY:{relative}")
                for number, line in enumerate(lines, 1):
                    if not line.strip():
                        errors.append(f"JSONL_BLANK_LINE:{relative}:{number}")
                    else:
                        json.loads(line)
                jsonl_digests[relative] = file_digest(path)
        except Exception as failure:
            errors.append(f"STRUCTURED_FILE_INVALID:{relative}:{type(failure).__name__}")
    return json_digests, jsonl_digests


def validate_links(files: list[pathlib.Path], errors: list[str]):
    pattern = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
    for document in [item for item in files if item.suffix == ".md"]:
        for raw in pattern.findall(document.read_text(encoding="utf-8", errors="replace")):
            target = raw.strip().split(" ", 1)[0].strip("<>")
            if target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            normalized = target.split("#", 1)[0]
            candidates = ((document.parent / normalized).resolve(), (ROOT / normalized).resolve())
            if normalized and not any(candidate.exists() for candidate in candidates):
                errors.append(f"MARKDOWN_LINK_MISSING:{document.relative_to(ROOT)}:{target}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    errors: list[str] = []
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            errors.append(f"MISSING_REQUIRED_FILE:{relative}")
    try:
        files = tracked_files()
    except RuntimeError as failure:
        files = []
        errors.append(str(failure))
    json_digests, jsonl_digests = validate_structured(files, errors)
    validate_links(files, errors)
    for command, marker in COMMANDS:
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
        combined = result.stdout + result.stderr
        if result.returncode != 0 or marker not in combined:
            errors.append(f"COMMAND_FAIL:{' '.join(command)}:{result.returncode}:{combined[-3000:]}")

    counts = load(COUNT_AUTHORITY)
    count_values = counts.get("counts", {})
    total = counts.get("total")
    if counts.get("contract") != "ONSURE_OMISSION_FAILURE_INJECTION_COUNTS_V1":
        errors.append("OMISSION_COUNT_AUTHORITY_CONTRACT_INVALID")
    if total != sum(count_values.values()):
        errors.append("OMISSION_COUNT_AUTHORITY_TOTAL_MISMATCH")
    subreq = load("status/product-subrequirement-coverage.v1.json")
    mvp = load("status/mvp-acceptance-coverage.v1.json")
    verification = load("status/verification-status.v1.json")
    if verification.get("runtime_source_commit") is not None:
        errors.append("VERIFICATION_STATUS_STATIC_RUNTIME_COMMIT_FORBIDDEN")
    if any(verification.get(key) is not False for key in ("final_lock", "production_go", "commercial_go")):
        errors.append("VERIFICATION_STATUS_UNSAFE_GO_CLAIM")

    report = {
        "contract": "ONSURE_REPOSITORY_STATIC_CONTRACT_REPORT_V5",
        "decision": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "product_subrequirements": len(subreq.get("requirements", [])),
        "mvp_acceptance_items": len(mvp.get("acceptance_items", [])),
        "workflow_operations": verification.get("workflow_surface_parity", {}).get("dispatcher_operation_count"),
        "failure_injection_authority": COUNT_AUTHORITY,
        "registered_failure_injections": total,
        "json_contract_count": len(json_digests),
        "jsonl_contract_count": len(jsonl_digests),
        "tracked_file_count": len(files),
        "json_digests": json_digests,
        "jsonl_digests": jsonl_digests,
        "runtime_execution": "NOT_RUN_BY_STATIC_VALIDATOR",
        "final_claim_allowed": False,
    }
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_REPOSITORY_CONTRACTS_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_REPOSITORY_CONTRACTS_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
