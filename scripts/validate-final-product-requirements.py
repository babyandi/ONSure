#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
import pathlib
import re
import sys
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]
REGISTER = ROOT / "status/final-product-requirement-coverage.v1.json"
HEADING = re.compile(r"^###\s+(FR-FIN-\d{2})\s+(.+)$")


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def extract(source: str) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    lines = source.splitlines()
    for index, raw in enumerate(lines):
        match = HEADING.match(raw.strip())
        if not match:
            continue
        requirement_id, name = match.groups()
        paragraph: list[str] = []
        for following in lines[index + 1:]:
            stripped = following.strip()
            if stripped.startswith("### ") or stripped.startswith("## "):
                break
            if stripped:
                paragraph.append(stripped)
        if not paragraph:
            raise ValueError(f"FINAL_REQUIREMENT_TEXT_MISSING:{requirement_id}")
        if requirement_id in result:
            raise ValueError(f"FINAL_REQUIREMENT_ID_DUPLICATE:{requirement_id}")
        result[requirement_id] = {"name": name.strip(), "normative_phrase": " ".join(paragraph)}
    return result


def ref_exists(value: str) -> bool:
    relative = value.split("::", 1)[0].split("#", 1)[0].split(":L", 1)[0]
    return bool(relative) and (ROOT / relative).is_file()


def validate(body: dict, source_text: str | None = None) -> list[str]:
    errors: list[str] = []
    if body.get("contract") != "ONSURE_FINAL_PRODUCT_REQUIREMENT_COVERAGE_V1":
        errors.append("FINAL_REQUIREMENT_CONTRACT_INVALID")
    source_path = ROOT / str(body.get("source_document", ""))
    if source_text is None:
        source_text = source_path.read_text(encoding="utf-8") if source_path.is_file() else ""
    if not source_text:
        return errors + ["FINAL_REQUIREMENT_SOURCE_MISSING"]
    try:
        expected = extract(source_text)
    except ValueError as failure:
        return errors + [str(failure)]
    items = body.get("requirements", [])
    if not isinstance(items, list):
        return errors + ["FINAL_REQUIREMENT_ITEMS_NOT_ARRAY"]
    registered = {str(item.get("id", "")): item for item in items if isinstance(item, dict)}
    if len(registered) != len(items):
        errors.append("FINAL_REQUIREMENT_ID_DUPLICATE_OR_ITEM_INVALID")
    for requirement_id in sorted(set(expected) - set(registered)):
        errors.append(f"FINAL_REQUIREMENT_SOURCE_UNMAPPED:{requirement_id}")
    for requirement_id in sorted(set(registered) - set(expected)):
        errors.append(f"FINAL_REQUIREMENT_NOT_IN_SOURCE:{requirement_id}")

    implementation = Counter()
    verification = Counter()
    for requirement_id, item in registered.items():
        source_item = expected.get(requirement_id, {})
        if item.get("normative_phrase") != source_item.get("normative_phrase"):
            errors.append(f"FINAL_REQUIREMENT_PHRASE_MISMATCH:{requirement_id}")
        implementation[str(item.get("implementation_status", ""))] += 1
        verification[str(item.get("verification_state", ""))] += 1
        code_refs = item.get("code_refs", [])
        test_refs = item.get("test_refs", [])
        for field, refs in (("code_refs", code_refs), ("test_refs", test_refs)):
            if not isinstance(refs, list):
                errors.append(f"FINAL_REQUIREMENT_REFERENCE_LIST_INVALID:{requirement_id}:{field}")
                continue
            for reference in refs:
                if not ref_exists(str(reference)):
                    errors.append(f"FINAL_REQUIREMENT_REFERENCE_MISSING:{requirement_id}:{field}:{reference}")
        state = item.get("implementation_status")
        if state == "IMPLEMENTED" and (not code_refs or not test_refs):
            errors.append(f"FINAL_REQUIREMENT_IMPLEMENTED_WITHOUT_CODE_TEST:{requirement_id}")
        if state != "IMPLEMENTED" and not item.get("missing_controls"):
            errors.append(f"FINAL_REQUIREMENT_INCOMPLETE_WITHOUT_GAP:{requirement_id}")
        if item.get("verification_state") == "PASS" and not item.get("evidence_refs"):
            errors.append(f"FINAL_REQUIREMENT_PASS_WITHOUT_EVIDENCE:{requirement_id}")
    summary = body.get("summary", {})
    calculated = {
        "total": len(items),
        "implemented": implementation.get("IMPLEMENTED", 0),
        "partial": implementation.get("PARTIAL", 0),
        "stub": implementation.get("STUB", 0),
        "design_only": implementation.get("DESIGN_ONLY", 0),
        "verification_not_run": verification.get("NOT_RUN", 0),
    }
    for field, value in calculated.items():
        if summary.get(field) != value:
            errors.append(f"FINAL_REQUIREMENT_SUMMARY_MISMATCH:{field}:{summary.get(field)}:{value}")
    assurance = body.get("assurance", {})
    if assurance.get("final_product_full_chain") != "NOT_RUN":
        errors.append("FINAL_REQUIREMENT_FULL_CHAIN_OVERCLAIMED")
    if assurance.get("final_claim_allowed") is not False:
        errors.append("FINAL_REQUIREMENT_FINAL_CLAIM_UNSAFE")
    return sorted(set(errors))


def self_test(body: dict, source: str) -> list[str]:
    missed: list[str] = []

    def expect(name: str, mutate, prefix: str, source_mutate=None):
        candidate = copy.deepcopy(body)
        mutate(candidate)
        changed_source = source_mutate(source) if source_mutate else source
        violations = validate(candidate, changed_source)
        if not any(item.startswith(prefix) for item in violations):
            missed.append(f"FINAL_REQUIREMENT_SELF_TEST_MISSED:{name}:{prefix}:{violations[:6]}")

    expect("source requirement added", lambda value: None, "FINAL_REQUIREMENT_SOURCE_UNMAPPED",
           lambda text: text.replace("### FR-FIN-22 Unified Evidence", "### FR-FIN-23 New Requirement\n새로운 최종 요구다.\n\n### FR-FIN-22 Unified Evidence", 1))
    expect("requirement removed", lambda value: value["requirements"].pop(0), "FINAL_REQUIREMENT_SOURCE_UNMAPPED")
    expect("duplicate id", lambda value: value["requirements"][1].update(id="FR-FIN-01"), "FINAL_REQUIREMENT_ID_DUPLICATE_OR_ITEM_INVALID")
    expect("phrase drift", lambda value: value["requirements"][0].update(normative_phrase="drift"), "FINAL_REQUIREMENT_PHRASE_MISMATCH")
    expect("missing reference", lambda value: value["requirements"][0].update(code_refs=["missing/File.java"]), "FINAL_REQUIREMENT_REFERENCE_MISSING")
    expect("incomplete without gap", lambda value: value["requirements"][0].update(missing_controls=[]), "FINAL_REQUIREMENT_INCOMPLETE_WITHOUT_GAP")
    expect("pass without evidence", lambda value: value["requirements"][0].update(verification_state="PASS"), "FINAL_REQUIREMENT_PASS_WITHOUT_EVIDENCE")
    expect("full chain overclaim", lambda value: value["assurance"].update(final_product_full_chain="PASS"), "FINAL_REQUIREMENT_FULL_CHAIN_OVERCLAIMED")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    body = load(REGISTER)
    source_path = ROOT / str(body.get("source_document", ""))
    source = source_path.read_text(encoding="utf-8") if source_path.is_file() else ""
    errors = validate(body, source)
    self_errors = self_test(body, source) if args.self_test and source else []
    try:
        source_count = len(extract(source)) if source else 0
    except ValueError:
        source_count = -1
    report = {
        "contract":"ONSURE_FINAL_PRODUCT_REQUIREMENT_VALIDATION_REPORT_V1",
        "decision":"PASS" if not errors and not self_errors else "FAIL",
        "errors":errors,
        "self_test_errors":self_errors,
        "source_requirement_count":source_count,
        "registered_requirement_count":len(body.get("requirements", [])),
        "failure_injection_count":8 if args.self_test else 0,
        "final_product_full_chain":"NOT_RUN",
        "final_claim_allowed":False
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_FINAL_PRODUCT_REQUIREMENT_GATE_PASS")
        return 0
    print("ONSURE_FINAL_PRODUCT_REQUIREMENT_GATE_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
