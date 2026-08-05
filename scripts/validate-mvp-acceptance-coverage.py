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
REGISTER = ROOT / "status/mvp-acceptance-coverage.v1.json"
NUMBERED = re.compile(r"^(\d+)\.\s+(.+)$")


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def extract(source: str) -> dict[str, str]:
    result: dict[str, str] = {}
    in_section = False
    for raw in source.splitlines():
        line = raw.strip()
        if line == "## 7. MVP 수용 시나리오":
            in_section = True
            continue
        if in_section and line.startswith("## "):
            break
        if not in_section:
            continue
        match = NUMBERED.match(line)
        if match:
            number = int(match.group(1))
            key = f"AC-{number:02d}"
            if key in result:
                raise ValueError(f"MVP_ACCEPTANCE_SOURCE_ID_DUPLICATE:{key}")
            result[key] = match.group(2).strip()
        elif line.startswith("이 시나리오가 실제 저장소에서"):
            if "AC-11" in result:
                raise ValueError("MVP_ACCEPTANCE_SOURCE_ID_DUPLICATE:AC-11")
            result["AC-11"] = line
    return result


def ref_exists(value: str) -> bool:
    relative = value.split("::", 1)[0].split("#", 1)[0].split(":L", 1)[0]
    return bool(relative) and (ROOT / relative).is_file()


def validate(body: dict, source_text: str | None = None) -> list[str]:
    errors: list[str] = []
    if body.get("contract") != "ONSURE_MVP_ACCEPTANCE_COVERAGE_V1":
        errors.append("MVP_ACCEPTANCE_CONTRACT_INVALID")
    source_path = ROOT / str(body.get("source_document", ""))
    if source_text is None:
        source_text = source_path.read_text(encoding="utf-8") if source_path.is_file() else ""
    if not source_text:
        return errors + ["MVP_ACCEPTANCE_SOURCE_DOCUMENT_MISSING"]
    try:
        expected = extract(source_text)
    except ValueError as failure:
        return errors + [str(failure)]
    items = body.get("acceptance_items", [])
    if not isinstance(items, list):
        return errors + ["MVP_ACCEPTANCE_ITEMS_NOT_ARRAY"]
    registered = {str(item.get("id", "")): item for item in items if isinstance(item, dict)}
    if len(registered) != len(items):
        errors.append("MVP_ACCEPTANCE_ID_DUPLICATE_OR_ITEM_INVALID")
    for item_id in sorted(set(expected) - set(registered)):
        errors.append(f"MVP_ACCEPTANCE_SOURCE_STEP_UNMAPPED:{item_id}")
    for item_id in sorted(set(registered) - set(expected)):
        errors.append(f"MVP_ACCEPTANCE_REGISTER_STEP_NOT_IN_SOURCE:{item_id}")

    implementation = Counter()
    verification = Counter()
    ordered_numbers: list[int] = []
    for item_id, item in registered.items():
        match = re.fullmatch(r"AC-(\d{2})", item_id)
        if not match:
            errors.append(f"MVP_ACCEPTANCE_ID_INVALID:{item_id}")
            continue
        ordered_numbers.append(int(match.group(1)))
        phrase = str(item.get("normative_phrase", "")).strip()
        if expected.get(item_id) != phrase:
            errors.append(f"MVP_ACCEPTANCE_PHRASE_MISMATCH:{item_id}")
        implementation[str(item.get("implementation_status", ""))] += 1
        verification[str(item.get("verification_state", ""))] += 1
        controls = item.get("detector_controls", [])
        if not isinstance(controls, list) or not controls:
            errors.append(f"MVP_ACCEPTANCE_DETECTOR_MISSING:{item_id}")
        code_refs = item.get("code_refs", [])
        test_refs = item.get("test_refs", [])
        for field, refs in (("code_refs", code_refs), ("test_refs", test_refs)):
            if not isinstance(refs, list):
                errors.append(f"MVP_ACCEPTANCE_REFERENCE_LIST_INVALID:{item_id}:{field}")
                continue
            for reference in refs:
                if not ref_exists(str(reference)):
                    errors.append(f"MVP_ACCEPTANCE_REFERENCE_MISSING:{item_id}:{field}:{reference}")
        state = item.get("implementation_status")
        if state == "IMPLEMENTED" and (not code_refs or not test_refs):
            errors.append(f"MVP_ACCEPTANCE_IMPLEMENTED_WITHOUT_CODE_TEST:{item_id}")
        if state != "IMPLEMENTED" and not item.get("missing_controls"):
            errors.append(f"MVP_ACCEPTANCE_INCOMPLETE_WITHOUT_GAP:{item_id}")
        if item.get("verification_state") == "PASS" and not item.get("evidence_refs"):
            errors.append(f"MVP_ACCEPTANCE_PASS_WITHOUT_EVIDENCE:{item_id}")
    if ordered_numbers != list(range(1, len(ordered_numbers) + 1)):
        errors.append(f"MVP_ACCEPTANCE_SEQUENCE_INVALID:{ordered_numbers}")

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
            errors.append(f"MVP_ACCEPTANCE_SUMMARY_MISMATCH:{field}:{summary.get(field)}:{value}")
    assurance = body.get("assurance", {})
    if assurance.get("mvp_full_chain") != "NOT_RUN":
        errors.append("MVP_ACCEPTANCE_FULL_CHAIN_OVERCLAIMED")
    if assurance.get("two_consecutive_real_repository_runs") != "NOT_RUN":
        errors.append("MVP_ACCEPTANCE_REPEAT_OVERCLAIMED")
    if assurance.get("final_claim_allowed") is not False:
        errors.append("MVP_ACCEPTANCE_FINAL_CLAIM_UNSAFE")
    return sorted(set(errors))


def self_test(body: dict, source: str) -> list[str]:
    missed: list[str] = []

    def expect(name: str, mutate, prefix: str, source_mutate=None):
        candidate = copy.deepcopy(body)
        mutate(candidate)
        changed_source = source_mutate(source) if source_mutate else source
        violations = validate(candidate, changed_source)
        if not any(item.startswith(prefix) for item in violations):
            missed.append(f"MVP_ACCEPTANCE_SELF_TEST_MISSED:{name}:{prefix}:{violations[:6]}")

    expect("source step added", lambda value: None, "MVP_ACCEPTANCE_SOURCE_STEP_UNMAPPED",
           lambda text: text.replace(
               "10. VS Code를 재시작해도 상태가 복원됩니다.",
               "10. VS Code를 재시작해도 상태가 복원됩니다.\n12. 새 수용 단계입니다.", 1))
    expect("step removed", lambda value: value["acceptance_items"].pop(2),
           "MVP_ACCEPTANCE_SOURCE_STEP_UNMAPPED")
    expect("duplicate id", lambda value: value["acceptance_items"][1].update(id="AC-01"),
           "MVP_ACCEPTANCE_ID_DUPLICATE_OR_ITEM_INVALID")
    expect("phrase drift", lambda value: value["acceptance_items"][0].update(normative_phrase="drift"),
           "MVP_ACCEPTANCE_PHRASE_MISMATCH")
    expect("missing detector", lambda value: value["acceptance_items"][0].update(detector_controls=[]),
           "MVP_ACCEPTANCE_DETECTOR_MISSING")
    expect("incomplete without gap", lambda value: value["acceptance_items"][0].update(missing_controls=[]),
           "MVP_ACCEPTANCE_INCOMPLETE_WITHOUT_GAP")
    expect("pass without evidence", lambda value: value["acceptance_items"][0].update(verification_state="PASS"),
           "MVP_ACCEPTANCE_PASS_WITHOUT_EVIDENCE")
    expect("repeat overclaim", lambda value: value["assurance"].update(
        two_consecutive_real_repository_runs="PASS"), "MVP_ACCEPTANCE_REPEAT_OVERCLAIMED")
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
        "contract": "ONSURE_MVP_ACCEPTANCE_VALIDATION_REPORT_V2",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "source_step_count": source_count,
        "registered_step_count": len(body.get("acceptance_items", [])),
        "failure_injection_count": 8 if args.self_test else 0,
        "mvp_full_chain": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_MVP_ACCEPTANCE_GATE_PASS", file=sys.stderr)
        return 0
    print("ONSURE_MVP_ACCEPTANCE_GATE_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
