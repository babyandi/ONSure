#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
import pathlib
import sys
from collections import Counter
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
REGISTER = ROOT / "status/product-subrequirement-coverage.v1.json"
VOCABULARY = ROOT / "contracts/status-vocabulary.v1.json"
EXPECTED_IDS = {
    "FR-01-A", "FR-01-B", "FR-01-C",
    "FR-02-A", "FR-02-B", "FR-02-C",
    "FR-03-A", "FR-03-B",
    "FR-04-A", "FR-04-B", "FR-04-C",
    "FR-05-A", "FR-05-B", "FR-05-C",
    "FR-06-A", "FR-06-B",
    "FR-07-A", "FR-07-B",
    "FR-08-A", "FR-08-B",
    "FR-09-A", "FR-09-B", "FR-09-C",
    "FR-10-A", "FR-10-B", "FR-10-C",
    "FR-11-A", "FR-11-B",
    "FR-12-A", "FR-12-B",
    "NFR-01", "NFR-02", "NFR-03", "NFR-04",
    "NFR-05", "NFR-06", "NFR-07", "NFR-08",
}
ALLOWED_SURFACES = {"CORE", "CLI", "LOCAL_API", "VSCODE", "LOCAL_GATE", "PUBLIC_API_OR_SDK"}


def load(path: pathlib.Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def path_exists(root: pathlib.Path, value: str) -> bool:
    path = value.split("::", 1)[0].split("#", 1)[0].split(":L", 1)[0]
    return bool(path) and (root / path).is_file()


def validate(body: dict[str, Any], root: pathlib.Path = ROOT) -> list[str]:
    errors: list[str] = []
    vocabulary = load(root / "contracts/status-vocabulary.v1.json")
    implementation_states = set(vocabulary.get("implementation_states", []))
    verification_states = set(vocabulary.get("verification_states", []))

    if body.get("contract") != "ONSURE_PRODUCT_SUBREQUIREMENT_COVERAGE_V1":
        errors.append("SUBREQ_CONTRACT_MISMATCH")
    source_document = str(body.get("source_document", ""))
    source_path = root / source_document
    source_text = source_path.read_text(encoding="utf-8") if source_path.is_file() else ""
    if not source_text:
        errors.append(f"SUBREQ_SOURCE_DOCUMENT_MISSING:{source_document}")

    requirements = body.get("requirements")
    if not isinstance(requirements, list):
        return errors + ["SUBREQ_REQUIREMENTS_NOT_ARRAY"]
    ids = [str(item.get("id", "")) for item in requirements if isinstance(item, dict)]
    if len(ids) != len(set(ids)):
        errors.append("SUBREQ_ID_DUPLICATE")
    if set(ids) != EXPECTED_IDS:
        errors.append(
            "SUBREQ_EXPECTED_ID_SET_MISMATCH:"
            f"missing={sorted(EXPECTED_IDS-set(ids))}:extra={sorted(set(ids)-EXPECTED_IDS)}"
        )

    status_counts: Counter[str] = Counter()
    verification_counts: Counter[str] = Counter()
    for index, item in enumerate(requirements):
        if not isinstance(item, dict):
            errors.append(f"SUBREQ_ITEM_NOT_OBJECT:{index}")
            continue
        requirement_id = str(item.get("id", f"INDEX-{index}"))
        implementation = str(item.get("implementation_status", ""))
        verification = str(item.get("verification_state", ""))
        status_counts[implementation] += 1
        verification_counts[verification] += 1
        if implementation not in implementation_states:
            errors.append(f"SUBREQ_IMPLEMENTATION_STATUS_INVALID:{requirement_id}:{implementation}")
        if verification not in verification_states:
            errors.append(f"SUBREQ_VERIFICATION_STATUS_INVALID:{requirement_id}:{verification}")

        phrase = str(item.get("normative_phrase", "")).strip()
        if not phrase or phrase not in source_text:
            errors.append(f"SUBREQ_NORMATIVE_PHRASE_MISSING:{requirement_id}")
        if not item.get("detector_controls"):
            errors.append(f"SUBREQ_DETECTOR_CONTROL_MISSING:{requirement_id}")

        code_refs = item.get("code_refs", [])
        test_refs = item.get("test_refs", [])
        evidence_refs = item.get("evidence_refs", [])
        for field, values in (("code_refs", code_refs), ("test_refs", test_refs)):
            if not isinstance(values, list):
                errors.append(f"SUBREQ_REFERENCE_LIST_INVALID:{requirement_id}:{field}")
                continue
            for reference in values:
                if not path_exists(root, str(reference)):
                    errors.append(f"SUBREQ_REFERENCE_MISSING:{requirement_id}:{field}:{reference}")

        if implementation == "IMPLEMENTED" and (not code_refs or not test_refs):
            errors.append(f"SUBREQ_IMPLEMENTED_WITHOUT_CODE_TEST:{requirement_id}")
        if implementation != "IMPLEMENTED" and not item.get("missing_controls"):
            errors.append(f"SUBREQ_INCOMPLETE_WITHOUT_EXPLICIT_GAP:{requirement_id}")
        if verification == "PASS" and not evidence_refs:
            errors.append(f"SUBREQ_PASS_WITHOUT_EVIDENCE:{requirement_id}")

        required_surfaces = set(item.get("required_surfaces", []))
        implemented_surfaces = set(item.get("implemented_surfaces", []))
        if not required_surfaces <= ALLOWED_SURFACES or not implemented_surfaces <= ALLOWED_SURFACES:
            errors.append(f"SUBREQ_SURFACE_VOCABULARY_INVALID:{requirement_id}")
        if not implemented_surfaces <= required_surfaces:
            errors.append(f"SUBREQ_IMPLEMENTED_SURFACE_NOT_REQUIRED:{requirement_id}")
        if implementation == "IMPLEMENTED" and implemented_surfaces != required_surfaces:
            errors.append(f"SUBREQ_IMPLEMENTED_SURFACE_INCOMPLETE:{requirement_id}")
        if required_surfaces - implemented_surfaces and "SUBREQ_SURFACE_GATE" not in item.get("detector_controls", []):
            errors.append(f"SUBREQ_MISSING_SURFACE_UNDETECTED:{requirement_id}")

        for assertion in item.get("semantic_assertions", []):
            if not isinstance(assertion, dict):
                errors.append(f"SUBREQ_SEMANTIC_ASSERTION_INVALID:{requirement_id}")
                continue
            relative = str(assertion.get("path", ""))
            path = root / relative
            if not path.is_file():
                errors.append(f"SUBREQ_SEMANTIC_PATH_MISSING:{requirement_id}:{relative}")
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            for token in assertion.get("tokens", []):
                if str(token) not in text:
                    errors.append(f"SUBREQ_SEMANTIC_TOKEN_MISSING:{requirement_id}:{relative}:{token}")

    summary = body.get("summary", {})
    expected_summary = {
        "total": len(requirements),
        "implemented": status_counts.get("IMPLEMENTED", 0),
        "partial": status_counts.get("PARTIAL", 0),
        "stub": status_counts.get("STUB", 0),
        "design_only": status_counts.get("DESIGN_ONLY", 0),
        "verification_not_run": verification_counts.get("NOT_RUN", 0),
    }
    for field, expected in expected_summary.items():
        if summary.get(field) != expected:
            errors.append(f"SUBREQ_SUMMARY_MISMATCH:{field}:{summary.get(field)}:{expected}")
    assurance = body.get("assurance", {})
    if assurance.get("final_claim_allowed") is not False:
        errors.append("SUBREQ_UNSAFE_FINAL_CLAIM")
    return sorted(set(errors))


def self_test(body: dict[str, Any]) -> list[str]:
    missed: list[str] = []

    def expect(name: str, mutate, prefix: str) -> None:
        candidate = copy.deepcopy(body)
        mutate(candidate)
        violations = validate(candidate)
        if not any(value.startswith(prefix) for value in violations):
            missed.append(f"SUBREQ_SELF_TEST_MISSED:{name}:{prefix}:{violations[:8]}")

    expect("requirement removed", lambda value: value["requirements"].pop(), "SUBREQ_EXPECTED_ID_SET_MISMATCH")
    expect("duplicate id", lambda value: value["requirements"][1].update(id=value["requirements"][0]["id"]),
           "SUBREQ_ID_DUPLICATE")
    expect("normative phrase drift", lambda value: value["requirements"][0].update(normative_phrase="missing phrase"),
           "SUBREQ_NORMATIVE_PHRASE_MISSING")
    expect("partial without gap", lambda value: value["requirements"][0].update(missing_controls=[]),
           "SUBREQ_INCOMPLETE_WITHOUT_EXPLICIT_GAP")
    expect("implemented without test", lambda value: value["requirements"][0].update(
        implementation_status="IMPLEMENTED", test_refs=[], missing_controls=[]),
        "SUBREQ_IMPLEMENTED_WITHOUT_CODE_TEST")
    expect("pass without evidence", lambda value: value["requirements"][0].update(verification_state="PASS"),
           "SUBREQ_PASS_WITHOUT_EVIDENCE")
    expect("missing code reference", lambda value: value["requirements"][0].update(code_refs=["missing/File.java"]),
           "SUBREQ_REFERENCE_MISSING")
    expect("missing detector", lambda value: value["requirements"][0].update(detector_controls=[]),
           "SUBREQ_DETECTOR_CONTROL_MISSING")
    expect("surface gap undetected", lambda value: value["requirements"][0].update(
        implemented_surfaces=["CORE"], detector_controls=["SUBREQ_REFERENCE_GATE"]),
        "SUBREQ_MISSING_SURFACE_UNDETECTED")
    target = next(item for item in body["requirements"] if item["id"] == "FR-04-C")
    expect("semantic implementation token removed", lambda value: next(
        item for item in value["requirements"] if item["id"] == target["id"]
    )["semantic_assertions"][0]["tokens"].append("TOKEN_THAT_MUST_NOT_EXIST"),
        "SUBREQ_SEMANTIC_TOKEN_MISSING")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=pathlib.Path, default=REGISTER)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    body = load(args.input)
    errors = validate(body)
    self_errors = self_test(body) if args.self_test else []
    report = {
        "contract": "ONSURE_PRODUCT_SUBREQUIREMENT_VALIDATION_REPORT_V1",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "requirement_count": len(body.get("requirements", [])),
        "failure_injection_count": 10 if args.self_test else 0,
        "known_incomplete_count": sum(
            1 for item in body.get("requirements", [])
            if item.get("implementation_status") != "IMPLEMENTED"
        ),
        "current_main_runtime_receipt": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_PRODUCT_SUBREQUIREMENT_GATE_PASS")
        return 0
    print("ONSURE_PRODUCT_SUBREQUIREMENT_GATE_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
