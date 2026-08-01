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
AUTHORITY = ROOT / "contracts/legacy-product-subrequirement-authority.v1.json"
HEADING = re.compile(r"^###\s+(FR-\d{2})\b")
SURFACE_DETECTORS = {"SUBREQ_SURFACE_GATE", "SUBREQ_WORKFLOW_REACHABILITY_GATE"}
ALLOWED_SURFACES = {"CORE", "CLI", "LOCAL_API", "VSCODE", "LOCAL_GATE", "PUBLIC_API_OR_SDK"}


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def extract(source: str) -> dict[str, str]:
    result: dict[str, str] = {}
    current: str | None = None
    index = 0
    in_nfr = False
    nfr = 0
    for raw in source.splitlines():
        line = raw.strip()
        match = HEADING.match(line)
        if match:
            current = match.group(1)
            index = 0
            in_nfr = False
            continue
        if line == "## 5. 비기능 요구사항":
            current = None
            in_nfr = True
            continue
        if line.startswith("## "):
            current = None
            if line != "## 5. 비기능 요구사항":
                in_nfr = False
            continue
        if not line.startswith("- "):
            continue
        phrase = line[2:].strip()
        if current:
            index += 1
            key = f"{current}-{chr(ord('A') + index - 1)}"
        elif in_nfr:
            nfr += 1
            key = f"NFR-{nfr:02d}"
        else:
            continue
        if key in result:
            raise ValueError(f"LEGACY_SUBREQ_SOURCE_ID_DUPLICATE:{key}")
        result[key] = phrase
    return result


def ref_exists(value: str) -> bool:
    relative = value.split("::", 1)[0].split("#", 1)[0].split(":L", 1)[0]
    return bool(relative) and (ROOT / relative).is_file()


def validate(authority: dict, register: dict, source: str) -> list[str]:
    errors: list[str] = []
    if authority.get("contract") != "ONSURE_LEGACY_PRODUCT_SUBREQUIREMENT_AUTHORITY_V1":
        errors.append("LEGACY_SUBREQ_AUTHORITY_CONTRACT_INVALID")
    if authority.get("authority_state") != "LEGACY_IMPLEMENTATION_DECOMPOSITION_NONAUTHORITATIVE_FOR_FINAL_PRODUCT":
        errors.append("LEGACY_SUBREQ_AUTHORITY_STATE_INVALID")
    if authority.get("may_satisfy_final_requirement") is not False \
            or authority.get("may_satisfy_final_acceptance") is not False:
        errors.append("LEGACY_SUBREQ_FINAL_AUTHORITY_UNSAFE")
    if register.get("contract") != "ONSURE_PRODUCT_SUBREQUIREMENT_COVERAGE_V1":
        errors.append("LEGACY_SUBREQ_REGISTER_CONTRACT_INVALID")
    try:
        expected = extract(source)
    except ValueError as failure:
        return errors + [str(failure)]
    items = register.get("requirements", [])
    if not isinstance(items, list):
        return errors + ["LEGACY_SUBREQ_ITEMS_NOT_ARRAY"]
    registered = {str(item.get("id", "")): item for item in items if isinstance(item, dict)}
    if len(registered) != len(items):
        errors.append("LEGACY_SUBREQ_ID_DUPLICATE_OR_INVALID")
    for key in sorted(set(expected) - set(registered)):
        errors.append(f"LEGACY_SUBREQ_SOURCE_UNMAPPED:{key}")
    for key in sorted(set(registered) - set(expected)):
        errors.append(f"LEGACY_SUBREQ_NOT_IN_SOURCE:{key}")
    implementation = Counter()
    verification = Counter()
    for key, item in registered.items():
        if item.get("normative_phrase") != expected.get(key):
            errors.append(f"LEGACY_SUBREQ_PHRASE_MISMATCH:{key}")
        implementation[str(item.get("implementation_status", ""))] += 1
        verification[str(item.get("verification_state", ""))] += 1
        for field in ("code_refs", "test_refs"):
            refs = item.get(field, [])
            if not isinstance(refs, list):
                errors.append(f"LEGACY_SUBREQ_REFERENCE_LIST_INVALID:{key}:{field}")
                continue
            for reference in refs:
                if not ref_exists(str(reference)):
                    errors.append(f"LEGACY_SUBREQ_REFERENCE_MISSING:{key}:{field}:{reference}")
        if item.get("implementation_status") != "IMPLEMENTED" and not item.get("missing_controls"):
            errors.append(f"LEGACY_SUBREQ_INCOMPLETE_WITHOUT_GAP:{key}")
        if item.get("verification_state") == "PASS" and not item.get("evidence_refs"):
            errors.append(f"LEGACY_SUBREQ_PASS_WITHOUT_EVIDENCE:{key}")
        required = set(item.get("required_surfaces", []))
        implemented = set(item.get("implemented_surfaces", []))
        if not required <= ALLOWED_SURFACES or not implemented <= ALLOWED_SURFACES:
            errors.append(f"LEGACY_SUBREQ_SURFACE_INVALID:{key}")
        if required - implemented and not SURFACE_DETECTORS.intersection(item.get("detector_controls", [])):
            errors.append(f"LEGACY_SUBREQ_SURFACE_GAP_UNDETECTED:{key}")
    summary = register.get("summary", {})
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
            errors.append(f"LEGACY_SUBREQ_SUMMARY_MISMATCH:{field}:{summary.get(field)}:{value}")
    if register.get("assurance", {}).get("final_claim_allowed") is not False:
        errors.append("LEGACY_SUBREQ_FINAL_CLAIM_UNSAFE")
    return sorted(set(errors))


def self_test(authority: dict, register: dict, source: str) -> list[str]:
    missed: list[str] = []

    def expect(name: str, mutate_authority, mutate_register, prefix: str):
        candidate_authority = copy.deepcopy(authority)
        candidate_register = copy.deepcopy(register)
        mutate_authority(candidate_authority)
        mutate_register(candidate_register)
        violations = validate(candidate_authority, candidate_register, source)
        if not any(value.startswith(prefix) for value in violations):
            missed.append(f"LEGACY_SUBREQ_SELF_TEST_MISSED:{name}:{prefix}:{violations[:6]}")

    expect("authority escalation", lambda value: value.update(may_satisfy_final_requirement=True), lambda value: None, "LEGACY_SUBREQ_FINAL_AUTHORITY_UNSAFE")
    expect("requirement removed", lambda value: None, lambda value: value["requirements"].pop(), "LEGACY_SUBREQ_SOURCE_UNMAPPED")
    expect("duplicate id", lambda value: None, lambda value: value["requirements"][1].update(id=value["requirements"][0]["id"]), "LEGACY_SUBREQ_ID_DUPLICATE_OR_INVALID")
    expect("phrase drift", lambda value: None, lambda value: value["requirements"][0].update(normative_phrase="drift"), "LEGACY_SUBREQ_PHRASE_MISMATCH")
    expect("pass without evidence", lambda value: None, lambda value: value["requirements"][0].update(verification_state="PASS"), "LEGACY_SUBREQ_PASS_WITHOUT_EVIDENCE")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    authority = load(AUTHORITY)
    register = load(ROOT / str(authority.get("register", "")))
    source_path = ROOT / str(authority.get("source_document", ""))
    source = source_path.read_text(encoding="utf-8") if source_path.is_file() else ""
    errors = validate(authority, register, source)
    self_errors = self_test(authority, register, source) if args.self_test and source else []
    report = {
        "contract":"ONSURE_LEGACY_PRODUCT_SUBREQUIREMENT_REPORT_V1",
        "decision":"PASS" if not errors and not self_errors else "FAIL",
        "errors":errors,
        "self_test_errors":self_errors,
        "legacy_requirement_count":len(register.get("requirements", [])),
        "authority_state":authority.get("authority_state"),
        "may_satisfy_final_requirement":False,
        "failure_injection_count":5 if args.self_test else 0,
        "final_claim_allowed":False
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_LEGACY_PRODUCT_SUBREQUIREMENT_GATE_PASS")
        return 0
    print("ONSURE_LEGACY_PRODUCT_SUBREQUIREMENT_GATE_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
