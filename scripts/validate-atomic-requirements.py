#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
from collections import Counter
from typing import Any

from jsonschema import Draft202012Validator

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCHEMA_PATH = ROOT / "contracts/atomic-requirement.v1.schema.json"
VOCABULARY_PATH = ROOT / "contracts/status-vocabulary.v1.json"
DESIGN_MATRIX_PATH = ROOT / "status/design-capability-coverage.v2.json"


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def normalized_requirement(text: str) -> str:
    import re
    return re.sub(r"\s+", " ", re.sub(r"[^0-9A-Za-z가-힣]+", " ", text)).strip().lower()


def load(path: pathlib.Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def reference_path(value: str) -> str:
    return value.split("::", 1)[0].split("#", 1)[0].split(":L", 1)[0]


def validate_candidate_set(body: dict[str, Any], check_paths: bool = True) -> list[str]:
    errors: list[str] = []
    schema = load(SCHEMA_PATH)
    vocabulary = load(VOCABULARY_PATH)
    matrix = load(DESIGN_MATRIX_PATH)

    schema_impl = set(schema["properties"]["implementation_status"]["enum"])
    schema_verify = set(schema["properties"]["verification_status"]["enum"])
    if schema_impl != set(vocabulary.get("implementation_states", [])):
        errors.append("ATOMIC_IMPLEMENTATION_VOCABULARY_MISMATCH")
    if schema_verify != set(vocabulary.get("verification_states", [])):
        errors.append("ATOMIC_VERIFICATION_VOCABULARY_MISMATCH")

    if body.get("contract") != "ONSURE_ATOMIC_REQUIREMENT_CANDIDATE_SET_V2":
        errors.append("ATOMIC_SET_CONTRACT_MISMATCH")
    records = body.get("records")
    if not isinstance(records, list):
        return errors + ["ATOMIC_RECORDS_NOT_ARRAY"]
    if body.get("candidate_count") != len(records):
        errors.append(f"ATOMIC_CANDIDATE_COUNT_MISMATCH:{body.get('candidate_count')}:{len(records)}")

    document_counts = body.get("document_candidate_counts")
    if not isinstance(document_counts, dict):
        errors.append("ATOMIC_DOCUMENT_COUNTS_NOT_OBJECT")
        document_counts = {}
    if body.get("document_count") != len(document_counts):
        errors.append(f"ATOMIC_DOCUMENT_COUNT_MISMATCH:{body.get('document_count')}:{len(document_counts)}")
    authoritative = set(matrix.get("authoritative_documents", []))
    missing_authority = sorted(authoritative - set(document_counts))
    for document in missing_authority:
        errors.append(f"ATOMIC_AUTHORITATIVE_DOCUMENT_UNSCANNED:{document}")

    validator = Draft202012Validator(schema)
    ids: list[str] = []
    actual_counts: Counter[str] = Counter()
    for index, record in enumerate(records):
        if not isinstance(record, dict):
            errors.append(f"ATOMIC_RECORD_NOT_OBJECT:{index}")
            continue
        requirement_id = str(record.get("requirement_id", f"INDEX-{index}"))
        ids.append(requirement_id)
        for failure in validator.iter_errors(record):
            location = "/".join(str(value) for value in failure.absolute_path)
            errors.append(f"ATOMIC_SCHEMA_INVALID:{requirement_id}:{location}:{failure.validator}")

        source_document = str(record.get("source_document", ""))
        actual_counts[source_document] += 1
        if not check_paths:
            continue
        source = (ROOT / source_document).resolve()
        if not source.is_file() or not source.is_relative_to(ROOT.resolve()):
            errors.append(f"ATOMIC_SOURCE_DOCUMENT_MISSING:{requirement_id}:{source_document}")
            continue
        source_bytes = source.read_bytes()
        if record.get("source_document_sha256") != sha256_bytes(source_bytes):
            errors.append(f"ATOMIC_SOURCE_DOCUMENT_DIGEST_MISMATCH:{requirement_id}")
        locator = record.get("source_locator")
        if not isinstance(locator, dict) or not isinstance(locator.get("line"), int):
            errors.append(f"ATOMIC_SOURCE_LOCATOR_INVALID:{requirement_id}")
            continue
        line_number = locator["line"]
        lines = source.read_text(encoding="utf-8", errors="replace").splitlines()
        if line_number < 1 or line_number > len(lines):
            errors.append(f"ATOMIC_SOURCE_LINE_OUT_OF_RANGE:{requirement_id}:{line_number}")
            continue
        raw = lines[line_number - 1]
        if record.get("source_line_sha256") != sha256_bytes(raw.encode()):
            errors.append(f"ATOMIC_SOURCE_LINE_DIGEST_MISMATCH:{requirement_id}")
        normalized = normalized_requirement(str(record.get("normative_text", "")))
        if record.get("normalized_text_sha256") != sha256_bytes(normalized.encode()):
            errors.append(f"ATOMIC_NORMALIZED_TEXT_DIGEST_MISMATCH:{requirement_id}")
        expected_ref = f"{source_document}:L{line_number}"
        if expected_ref not in record.get("design_refs", []):
            errors.append(f"ATOMIC_DESIGN_REF_LINEAGE_MISSING:{requirement_id}:{expected_ref}")
        for field in ("contract_refs", "code_symbols", "test_methods"):
            for reference in record.get(field, []):
                path = reference_path(str(reference))
                if path and not (ROOT / path).is_file():
                    errors.append(f"ATOMIC_REFERENCE_PATH_MISSING:{requirement_id}:{field}:{path}")

        verification = record.get("verification_status")
        implementation = record.get("implementation_status")
        criteria = record.get("acceptance_criteria", [])
        pending_oracle = any(
            isinstance(item, dict) and str(item.get("oracle", "")).endswith("_PENDING")
            for item in criteria
        )
        if verification == "PASS" and (
            pending_oracle or not record.get("test_methods") or not record.get("evidence_refs")
        ):
            errors.append(f"ATOMIC_PASS_WITHOUT_EXECUTED_ORACLE_TEST_EVIDENCE:{requirement_id}")
        if implementation == "IMPLEMENTED" and (
            not record.get("code_symbols") or not record.get("test_methods")
        ):
            errors.append(f"ATOMIC_IMPLEMENTED_WITHOUT_CODE_AND_TEST:{requirement_id}")

    for value, count in Counter(ids).items():
        if count > 1:
            errors.append(f"ATOMIC_REQUIREMENT_ID_DUPLICATE:{value}")
    for document, declared in document_counts.items():
        if declared != actual_counts.get(document, 0):
            errors.append(
                f"ATOMIC_DOCUMENT_CANDIDATE_COUNT_MISMATCH:{document}:{declared}:{actual_counts.get(document, 0)}"
            )
    declared_empty = set(body.get("documents_without_normative_candidates", []))
    actual_empty = {document for document, count in document_counts.items() if count == 0}
    if declared_empty != actual_empty:
        errors.append("ATOMIC_EMPTY_DOCUMENT_SET_MISMATCH")
    if body.get("promotion_allowed") is not False:
        errors.append("ATOMIC_UNSAFE_PROMOTION_ALLOWED")
    if body.get("verification_status") != "NOT_RUN":
        errors.append("ATOMIC_CANDIDATE_SET_VERIFICATION_OVERCLAIMED")
    return sorted(set(errors))


def self_test(body: dict[str, Any]) -> list[str]:
    missed: list[str] = []

    def expect(name, mutate, prefix):
        candidate = copy.deepcopy(body)
        mutate(candidate)
        violations = validate_candidate_set(candidate, check_paths=True)
        if not any(value.startswith(prefix) for value in violations):
            missed.append(f"ATOMIC_SELF_TEST_MISSED:{name}:{prefix}:{violations[:6]}")

    if not body.get("records"):
        return ["ATOMIC_SELF_TEST_REQUIRES_AT_LEAST_ONE_RECORD"]
    expect("required field removed", lambda value: value["records"][0].pop("source_line_sha256", None),
           "ATOMIC_SCHEMA_INVALID:")
    expect("invalid implementation status", lambda value: value["records"][0].update(implementation_status="BLOCKED"),
           "ATOMIC_SCHEMA_INVALID:")
    expect("locator replaced by string", lambda value: value["records"][0].update(source_locator="line 1"),
           "ATOMIC_SCHEMA_INVALID:")
    expect("document digest tampered", lambda value: value["records"][0].update(source_document_sha256="0" * 64),
           "ATOMIC_SOURCE_DOCUMENT_DIGEST_MISMATCH:")
    expect("line digest tampered", lambda value: value["records"][0].update(source_line_sha256="0" * 64),
           "ATOMIC_SOURCE_LINE_DIGEST_MISMATCH:")
    expect("duplicate id", lambda value: value["records"].append(copy.deepcopy(value["records"][0])),
           "ATOMIC_REQUIREMENT_ID_DUPLICATE:")
    expect("candidate count drift", lambda value: value.update(candidate_count=value["candidate_count"] + 1),
           "ATOMIC_CANDIDATE_COUNT_MISMATCH:")
    expect("pass without evidence", lambda value: value["records"][0].update(verification_status="PASS"),
           "ATOMIC_PASS_WITHOUT_EXECUTED_ORACLE_TEST_EVIDENCE:")
    expect("missing code reference", lambda value: value["records"][0].update(code_symbols=["missing/File.java::X"]),
           "ATOMIC_REFERENCE_PATH_MISSING:")
    authority = load(DESIGN_MATRIX_PATH).get("authoritative_documents", [""])[0]
    expect("authoritative document unscanned", lambda value: value["document_candidate_counts"].pop(authority, None),
           "ATOMIC_AUTHORITATIVE_DOCUMENT_UNSCANNED:")
    return missed


def extract(output: pathlib.Path) -> None:
    result = subprocess.run(
        [sys.executable, "scripts/extract-atomic-requirements.py", "--output", str(output)],
        cwd=ROOT, text=True, capture_output=True, check=False
    )
    if result.returncode != 0:
        raise RuntimeError("ATOMIC_EXTRACTION_FAILED:" + result.stdout[-1000:] + result.stderr[-1000:])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    temporary: tempfile.TemporaryDirectory[str] | None = None
    try:
        if args.input:
            candidate_path = args.input.resolve()
        else:
            temporary = tempfile.TemporaryDirectory(prefix="onsure-atomic-")
            candidate_path = pathlib.Path(temporary.name) / "atomic-requirement-candidates.json"
            extract(candidate_path)
        body = load(candidate_path)
        errors = validate_candidate_set(body)
        self_errors = self_test(body) if args.self_test else []
        report = {
            "contract": "ONSURE_ATOMIC_REQUIREMENT_VALIDATION_REPORT_V1",
            "decision": "PASS" if not errors and not self_errors else "FAIL",
            "errors": errors,
            "self_test_errors": self_errors,
            "candidate_count": body.get("candidate_count"),
            "document_count": body.get("document_count"),
            "failure_injection_count": 10 if args.self_test else 0,
            "candidate_authority": "NONAUTHORITATIVE_RECONCILIATION_REQUIRED",
            "final_claim_allowed": False,
        }
        text = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        print(text, end="")
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(text, encoding="utf-8")
        return 0 if report["decision"] == "PASS" else 1
    finally:
        if temporary is not None:
            temporary.cleanup()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"ONSURE_ATOMIC_REQUIREMENTS_VALIDATION_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
