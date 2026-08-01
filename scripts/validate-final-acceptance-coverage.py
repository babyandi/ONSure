#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_REGISTRY = ROOT / "contracts/final-acceptance-source-registry.v1.json"
COVERAGE = ROOT / "status/final-acceptance-coverage.v1.json"
NUMBERED = re.compile(r"^\d+\.\s+(.+)$")


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def extract_items(document: str, anchor: str, style: str) -> list[str]:
    lines = document.splitlines()
    start = next((index for index, line in enumerate(lines) if line.strip() == anchor), -1)
    if start < 0:
        raise ValueError(f"FINAL_ACCEPTANCE_SECTION_MISSING:{anchor}")
    values: list[str] = []
    started = False
    for raw in lines[start + 1:]:
        line = raw.strip()
        if line.startswith("## ") and started:
            break
        if style == "BULLET" and line.startswith("- "):
            started = True
            values.append(line[2:].strip())
        elif style == "NUMBERED":
            match = NUMBERED.match(line)
            if match:
                started = True
                values.append(match.group(1).strip())
        elif started and line and not line.startswith(("- ", "### ")):
            # End at the first prose paragraph after the list.
            break
    return values


def validate(registry: dict, coverage: dict, root: pathlib.Path = ROOT) -> tuple[list[str], dict[str, list[str]]]:
    errors: list[str] = []
    extracted: dict[str, list[str]] = {}
    if registry.get("contract") != "ONSURE_FINAL_ACCEPTANCE_SOURCE_REGISTRY_V1":
        errors.append("FINAL_ACCEPTANCE_SOURCE_REGISTRY_CONTRACT_INVALID")
    if coverage.get("contract") != "ONSURE_FINAL_ACCEPTANCE_COVERAGE_V1":
        errors.append("FINAL_ACCEPTANCE_COVERAGE_CONTRACT_INVALID")
    sources = registry.get("sources", [])
    if not isinstance(sources, list):
        return errors + ["FINAL_ACCEPTANCE_SOURCES_NOT_ARRAY"], extracted
    source_ids = [item.get("id") for item in sources if isinstance(item, dict)]
    if len(source_ids) != len(sources) or len(source_ids) != len(set(source_ids)):
        errors.append("FINAL_ACCEPTANCE_SOURCE_ID_DUPLICATE_OR_INVALID")
    for source in sources:
        source_id = str(source.get("id", ""))
        relative = str(source.get("document", ""))
        path = root / relative
        if not path.is_file():
            errors.append(f"FINAL_ACCEPTANCE_DOCUMENT_MISSING:{source_id}:{relative}")
            continue
        try:
            values = extract_items(
                path.read_text(encoding="utf-8"),
                str(source.get("section_anchor", "")),
                str(source.get("item_style", "")),
            )
        except ValueError as failure:
            errors.append(f"{source_id}:{failure}")
            continue
        extracted[source_id] = values
        if len(values) != source.get("expected_count"):
            errors.append(f"FINAL_ACCEPTANCE_SOURCE_COUNT_MISMATCH:{source_id}:{len(values)}:{source.get('expected_count')}")
        if len(values) != len(set(values)):
            errors.append(f"FINAL_ACCEPTANCE_SOURCE_DUPLICATE_ITEM:{source_id}")
    actual_total = sum(len(values) for values in extracted.values())
    if registry.get("total_expected_items") != actual_total:
        errors.append(f"FINAL_ACCEPTANCE_TOTAL_MISMATCH:{registry.get('total_expected_items')}:{actual_total}")
    if registry.get("coverage_state") != "SOURCE_INVENTORY_ONLY_ATOMIC_IMPLEMENTATION_MAPPING_PENDING":
        errors.append("FINAL_ACCEPTANCE_COVERAGE_STATE_OVERCLAIMED")
    if registry.get("final_claim_allowed") is not False:
        errors.append("FINAL_ACCEPTANCE_SOURCE_FINAL_CLAIM_UNSAFE")

    groups = coverage.get("groups", [])
    group_map = {item.get("id"): item for item in groups if isinstance(item, dict)}
    if len(group_map) != len(groups) or set(group_map) != set(source_ids):
        errors.append("FINAL_ACCEPTANCE_COVERAGE_GROUP_SET_MISMATCH")
    for source_id, values in extracted.items():
        group = group_map.get(source_id, {})
        if group.get("registered_items") != len(values):
            errors.append(f"FINAL_ACCEPTANCE_GROUP_COUNT_MISMATCH:{source_id}")
        if group.get("implemented_items") != 0 or group.get("verified_items") != 0:
            errors.append(f"FINAL_ACCEPTANCE_GROUP_OVERCLAIMED:{source_id}")
        if group.get("state") != "NOT_RUN":
            errors.append(f"FINAL_ACCEPTANCE_GROUP_STATE_OVERCLAIMED:{source_id}")
        if not group.get("missing_controls"):
            errors.append(f"FINAL_ACCEPTANCE_GROUP_GAP_MISSING:{source_id}")
    summary = coverage.get("summary", {})
    expected_summary = {
        "source_groups": len(sources),
        "registered_items": actual_total,
        "implemented_items": 0,
        "verified_items": 0,
        "not_run_items": actual_total,
    }
    for field, expected in expected_summary.items():
        if summary.get(field) != expected:
            errors.append(f"FINAL_ACCEPTANCE_SUMMARY_MISMATCH:{field}:{summary.get(field)}:{expected}")
    assurance = coverage.get("assurance", {})
    for field in (
        "financial_scenarios_3_sets", "external_ai_product_types_5", "white_gray_black_box",
        "same_identity_repeat_2", "independent_otester_two_clean",
        "independent_oaudit_two_clean", "human_approval"
    ):
        if assurance.get(field) != "NOT_RUN":
            errors.append(f"FINAL_ACCEPTANCE_ASSURANCE_OVERCLAIMED:{field}")
    if assurance.get("final_claim_allowed") is not False:
        errors.append("FINAL_ACCEPTANCE_FINAL_CLAIM_UNSAFE")
    return sorted(set(errors)), extracted


def self_test(registry: dict, coverage: dict) -> list[str]:
    missed: list[str] = []

    def expect(name: str, mutate_registry, mutate_coverage, prefix: str) -> None:
        candidate_registry = copy.deepcopy(registry)
        candidate_coverage = copy.deepcopy(coverage)
        mutate_registry(candidate_registry)
        mutate_coverage(candidate_coverage)
        violations, _ = validate(candidate_registry, candidate_coverage)
        if not any(value.startswith(prefix) for value in violations):
            missed.append(f"FINAL_ACCEPTANCE_SELF_TEST_MISSED:{name}:{prefix}:{violations[:6]}")

    expect("wrong expected count", lambda value: value["sources"][0].update(expected_count=999), lambda value: None, "FINAL_ACCEPTANCE_SOURCE_COUNT_MISMATCH")
    expect("duplicate source id", lambda value: value["sources"][1].update(id=value["sources"][0]["id"]), lambda value: None, "FINAL_ACCEPTANCE_SOURCE_ID_DUPLICATE_OR_INVALID")
    expect("wrong total", lambda value: value.update(total_expected_items=999), lambda value: None, "FINAL_ACCEPTANCE_TOTAL_MISMATCH")
    expect("coverage group removed", lambda value: None, lambda value: value["groups"].pop(), "FINAL_ACCEPTANCE_COVERAGE_GROUP_SET_MISMATCH")
    expect("group implemented overclaim", lambda value: None, lambda value: value["groups"][0].update(implemented_items=1), "FINAL_ACCEPTANCE_GROUP_OVERCLAIMED")
    expect("group state overclaim", lambda value: None, lambda value: value["groups"][0].update(state="PASS"), "FINAL_ACCEPTANCE_GROUP_STATE_OVERCLAIMED")
    expect("assurance overclaim", lambda value: None, lambda value: value["assurance"].update(financial_scenarios_3_sets="PASS"), "FINAL_ACCEPTANCE_ASSURANCE_OVERCLAIMED")
    expect("final claim unsafe", lambda value: None, lambda value: value["assurance"].update(final_claim_allowed=True), "FINAL_ACCEPTANCE_FINAL_CLAIM_UNSAFE")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    registry = load(SOURCE_REGISTRY)
    coverage = load(COVERAGE)
    errors, extracted = validate(registry, coverage)
    self_errors = self_test(registry, coverage) if args.self_test else []
    canonical = json.dumps(extracted, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    report = {
        "contract":"ONSURE_FINAL_ACCEPTANCE_VALIDATION_REPORT_V1",
        "decision":"PASS" if not errors and not self_errors else "FAIL",
        "errors":errors,
        "self_test_errors":self_errors,
        "source_groups":len(extracted),
        "registered_acceptance_items":sum(len(values) for values in extracted.values()),
        "criteria_sha256":hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        "failure_injection_count":8 if args.self_test else 0,
        "final_acceptance_execution":"NOT_RUN",
        "final_claim_allowed":False
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_FINAL_ACCEPTANCE_GATE_PASS")
        return 0
    print("ONSURE_FINAL_ACCEPTANCE_GATE_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
