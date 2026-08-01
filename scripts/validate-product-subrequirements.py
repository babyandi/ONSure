#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
import pathlib
import re
import sys
from collections import Counter
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
REGISTER = ROOT / "status/product-subrequirement-coverage.v1.json"
VOCABULARY = ROOT / "contracts/status-vocabulary.v1.json"
FR_HEADING = re.compile(r"^###\s+(FR-\d{2})\b")
SURFACE_DETECTORS = {"SUBREQ_SURFACE_GATE", "SUBREQ_WORKFLOW_REACHABILITY_GATE"}
ALLOWED_SURFACES = {"CORE", "CLI", "LOCAL_API", "VSCODE", "LOCAL_GATE", "PUBLIC_API_OR_SDK"}


def load(path: pathlib.Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def path_exists(root: pathlib.Path, value: str) -> bool:
    path = value.split("::", 1)[0].split("#", 1)[0].split(":L", 1)[0]
    return bool(path) and (root / path).is_file()


def extract_normative_requirements(source_text: str) -> dict[str, str]:
    result: dict[str, str] = {}
    current_fr: str | None = None
    fr_index = 0
    in_nfr = False
    nfr_index = 0
    for raw in source_text.splitlines():
        line = raw.strip()
        match = FR_HEADING.match(line)
        if match:
            current_fr = match.group(1)
            fr_index = 0
            in_nfr = False
            continue
        if line.startswith("## 5. 비기능 요구사항"):
            current_fr = None
            in_nfr = True
            continue
        if line.startswith("## "):
            current_fr = None
            if not line.startswith("## 5. 비기능 요구사항"):
                in_nfr = False
            continue
        if not line.startswith("- "):
            continue
        phrase = line[2:].strip()
        if current_fr:
            fr_index += 1
            if fr_index > 26:
                raise ValueError(f"TOO_MANY_REQUIREMENTS:{current_fr}")
            requirement_id = f"{current_fr}-{chr(ord('A') + fr_index - 1)}"
        elif in_nfr:
            nfr_index += 1
            requirement_id = f"NFR-{nfr_index:02d}"
        else:
            continue
        if requirement_id in result:
            raise ValueError(f"SOURCE_REQUIREMENT_ID_DUPLICATE:{requirement_id}")
        result[requirement_id] = phrase
    return result


def validate(
        body: dict[str, Any], root: pathlib.Path = ROOT, source_text: str | None = None) -> list[str]:
    errors: list[str] = []
    vocabulary = load(root / "contracts/status-vocabulary.v1.json")
    implementation_states = set(vocabulary.get("implementation_states", []))
    verification_states = set(vocabulary.get("verification_states", []))

    if body.get("contract") != "ONSURE_PRODUCT_SUBREQUIREMENT_COVERAGE_V1":
        errors.append("SUBREQ_CONTRACT_MISMATCH")
    source_document = str(body.get("source_document", ""))
    source_path = root / source_document
    if source_text is None:
        source_text = source_path.read_text(encoding="utf-8") if source_path.is_file() else ""
    if not source_text:
        errors.append(f"SUBREQ_SOURCE_DOCUMENT_MISSING:{source_document}")
        return errors
    try:
        expected = extract_normative_requirements(source_text)
    except ValueError as failure:
        return errors + [str(failure)]

    requirements = body.get("requirements")
    if not isinstance(requirements, list):
        return errors + ["SUBREQ_REQUIREMENTS_NOT_ARRAY"]
    items = [item for item in requirements if isinstance(item, dict)]
    if len(items) != len(requirements):
        errors.append("SUBREQ_ITEM_NOT_OBJECT")
    ids = [str(item.get("id", "")) for item in items]
    if len(ids) != len(set(ids)):
        errors.append("SUBREQ_ID_DUPLICATE")
    registered = {str(item.get("id", "")): item for item in items}
    for requirement_id in sorted(set(expected) - set(registered)):
        errors.append(f"SUBREQ_SOURCE_REQUIREMENT_UNMAPPED:{requirement_id}")
    for requirement_id in sorted(set(registered) - set(expected)):
        errors.append(f"SUBREQ_REGISTER_REQUIREMENT_NOT_IN_SOURCE:{requirement_id}")

    status_counts: Counter[str] = Counter()
    verification_counts: Counter[str] = Counter()
    phrases: list[str] = []
    for index, item in enumerate(items):
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
        phrases.append(phrase)
        if not phrase or expected.get(requirement_id) != phrase:
            errors.append(f"SUBREQ_NORMATIVE_PHRASE_MISMATCH:{requirement_id}")
        controls = item.get("detector_controls", [])
        if not isinstance(controls, list) or not controls:
            errors.append(f"SUBREQ_DETECTOR_CONTROL_MISSING:{requirement_id}")
            controls = []

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
        if required_surfaces - implemented_surfaces and not SURFACE_DETECTORS.intersection(controls):
            errors.append(f"SUBREQ_MISSING_SURFACE_UNDETECTED:{requirement_id}")

        assertions = item.get("semantic_assertions")
        if not isinstance(assertions, list):
            errors.append(f"SUBREQ_SEMANTIC_ASSERTIONS_INVALID:{requirement_id}")
            assertions = []
        for assertion in assertions:
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

    if any(not phrase for phrase in phrases) or len(phrases) != len(set(phrases)):
        errors.append("SUBREQ_NORMATIVE_PHRASE_EMPTY_OR_DUPLICATE")
    summary = body.get("summary", {})
    expected_summary = {
        "total": len(requirements),
        "implemented": status_counts.get("IMPLEMENTED", 0),
        "partial": status_counts.get("PARTIAL", 0),
        "stub": status_counts.get("STUB", 0),
        "design_only": status_counts.get("DESIGN_ONLY", 0),
        "verification_not_run": verification_counts.get("NOT_RUN", 0),
    }
    for field, expected_value in expected_summary.items():
        if summary.get(field) != expected_value:
            errors.append(f"SUBREQ_SUMMARY_MISMATCH:{field}:{summary.get(field)}:{expected_value}")
    if body.get("assurance", {}).get("final_claim_allowed") is not False:
        errors.append("SUBREQ_UNSAFE_FINAL_CLAIM")
    return sorted(set(errors))


def self_test(body: dict[str, Any], source_text: str) -> list[str]:
    missed: list[str] = []

    def expect(name: str, mutate, prefix: str, source_mutate=None) -> None:
        candidate = copy.deepcopy(body)
        mutate(candidate)
        candidate_source = source_mutate(source_text) if source_mutate else source_text
        violations = validate(candidate, source_text=candidate_source)
        if not any(value.startswith(prefix) for value in violations):
            missed.append(f"SUBREQ_SELF_TEST_MISSED:{name}:{prefix}:{violations[:8]}")

    expect("source bullet unmapped", lambda value: None, "SUBREQ_SOURCE_REQUIREMENT_UNMAPPED",
           lambda text: text.replace(
               "- 로컬 폴더와 Git 저장소를 등록할 수 있어야 합니다.",
               "- 로컬 폴더와 Git 저장소를 등록할 수 있어야 합니다.\n- 새 원문 요구사항은 대장에 자동 반영되어야 합니다.", 1))
    expect("duplicate id", lambda value: value["requirements"][1].update(id=value["requirements"][0]["id"]),
           "SUBREQ_ID_DUPLICATE")
    expect("normative phrase drift", lambda value: value["requirements"][0].update(normative_phrase="drift"),
           "SUBREQ_NORMATIVE_PHRASE_MISMATCH")
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
    target_id = "FR-04-C"

    def add_missing_semantic_token(value: dict[str, Any]) -> None:
        target = next(item for item in value["requirements"] if item["id"] == target_id)
        assertions = target.setdefault("semantic_assertions", [])
        if not assertions:
            assertions.append({
                "path": "src/main/java/io/onsure/platform/ExecutionPlanApprovalService.java",
                "tokens": [],
            })
        assertions[0].setdefault("tokens", []).append("TOKEN_THAT_MUST_NOT_EXIST")

    expect("semantic implementation token removed", add_missing_semantic_token,
        "SUBREQ_SEMANTIC_TOKEN_MISSING")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=pathlib.Path, default=REGISTER)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    body = load(args.input)
    source_path = ROOT / str(body.get("source_document", ""))
    source_text = source_path.read_text(encoding="utf-8") if source_path.is_file() else ""
    errors = validate(body, source_text=source_text)
    self_errors = self_test(body, source_text) if args.self_test and source_text else []
    source_count = len(extract_normative_requirements(source_text)) if source_text else 0
    report = {
        "contract": "ONSURE_PRODUCT_SUBREQUIREMENT_VALIDATION_REPORT_V2",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "source_requirement_count": source_count,
        "registered_requirement_count": len(body.get("requirements", [])),
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
        print("ONSURE_PRODUCT_SUBREQUIREMENT_GATE_PASS", file=sys.stderr)
        return 0
    print("ONSURE_PRODUCT_SUBREQUIREMENT_GATE_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
