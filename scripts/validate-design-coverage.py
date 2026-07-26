#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
import pathlib
from collections import Counter
from typing import Any

REQUIRED_CAPABILITIES = {
    "CORE-ISOLATION", "WORKSPACE-INTAKE", "PROGRAM-LEARNING", "BEHAVIOR-LEARNING",
    "RISK-PLANNING", "OREVIEW", "VERIFICATION-STATIC", "VERIFICATION-BUILD",
    "VERIFICATION-RUNTIME", "VERIFICATION-API-UI", "VERIFICATION-SECURITY",
    "VERIFICATION-PERFORMANCE-RECOVERY", "RCA", "IMPROVEMENT-PATCH",
    "IMPROVEMENT-PROOF", "GIT-DELIVERY", "EVIDENCE-RECEIPTS", "LEARNING-MEMORY",
    "VSCODE-EXTENSION", "LOCAL-AUTHENTICATED-API", "WEB-SERVICE-CASE", "OLICENSE",
    "TENANT-IDENTITY", "SANDBOX", "RETENTION-DELETION", "OBSERVABILITY-OPERATIONS",
    "DELIVERY", "DEPLOYMENT"
}
ALLOWED_IMPLEMENTATION = {"IMPLEMENTED", "PARTIAL", "STUB", "DESIGN_ONLY", "BLOCKED", "DEPRECATED"}
ALLOWED_VERIFICATION = {"PASS", "FAIL", "HOLD", "BLOCKED", "NOT_RUN", "INCONCLUSIVE", "NON_FINAL"}
BASE_FIELDS = {"capability_id", "name", "design_refs", "contract_refs", "code_refs", "test_refs",
               "evidence_refs", "implementation_status", "verification_state", "coverage_profile", "limitations"}


def load_json(path: pathlib.Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def expand(matrix: dict[str, Any], item: dict[str, Any]) -> dict[str, Any]:
    profile_name = item.get("coverage_profile")
    profile = matrix.get("coverage_profiles", {}).get(profile_name)
    if not isinstance(profile, dict):
        return item
    result = copy.deepcopy(item)
    cid = str(item.get("capability_id", "UNKNOWN"))
    for field in ("process_steps", "required_data", "failure_cases", "detection_controls"):
        value = copy.deepcopy(profile.get(field, []))
        encoded = json.dumps(value).replace("${CAP}", cid)
        result[field] = json.loads(encoded)
    for field in ("process_steps", "required_data", "failure_cases", "detection_controls"):
        if field in item:
            result[field] = copy.deepcopy(item[field])
    return result


def validate(matrix: dict[str, Any], root: pathlib.Path | None, check_paths: bool = True) -> list[str]:
    errors: list[str] = []
    if matrix.get("contract") != "ONSURE_DESIGN_CAPABILITY_COVERAGE_V2":
        errors.append("CONTRACT_MISMATCH")
    items = matrix.get("capabilities")
    if not isinstance(items, list):
        return errors + ["CAPABILITIES_NOT_ARRAY"]
    ids = [item.get("capability_id") for item in items if isinstance(item, dict)]
    for value, count in Counter(ids).items():
        if count > 1:
            errors.append(f"DUPLICATE_CAPABILITY:{value}")
    for cid in sorted(REQUIRED_CAPABILITIES - set(ids)):
        errors.append(f"REQUIRED_CAPABILITY_MISSING:{cid}")

    mapped_docs: set[str] = set()
    for index, raw in enumerate(items):
        if not isinstance(raw, dict):
            errors.append(f"CAPABILITY_NOT_OBJECT:{index}")
            continue
        cid = str(raw.get("capability_id", f"INDEX-{index}"))
        for field in sorted(BASE_FIELDS - set(raw)):
            errors.append(f"CAPABILITY_FIELD_MISSING:{cid}:{field}")
        item = expand(matrix, raw)
        if raw.get("coverage_profile") not in matrix.get("coverage_profiles", {}):
            errors.append(f"COVERAGE_PROFILE_UNKNOWN:{cid}:{raw.get('coverage_profile')}")
        impl, verification = item.get("implementation_status"), item.get("verification_state")
        if impl not in ALLOWED_IMPLEMENTATION:
            errors.append(f"IMPLEMENTATION_STATUS_INVALID:{cid}:{impl}")
        if verification not in ALLOWED_VERIFICATION:
            errors.append(f"VERIFICATION_STATE_INVALID:{cid}:{verification}")

        for field in ("design_refs", "contract_refs", "code_refs", "test_refs", "evidence_refs", "limitations",
                      "process_steps", "required_data", "failure_cases", "detection_controls"):
            value = item.get(field)
            if not isinstance(value, list):
                errors.append(f"CAPABILITY_LIST_FIELD_INVALID:{cid}:{field}")
        for field in ("design_refs", "process_steps", "required_data", "failure_cases", "detection_controls"):
            if not item.get(field):
                errors.append(f"CAPABILITY_REQUIRED_LIST_EMPTY:{cid}:{field}")

        design_refs = item.get("design_refs", []) if isinstance(item.get("design_refs"), list) else []
        mapped_docs.update(str(v).split("#", 1)[0].split(":L", 1)[0] for v in design_refs)
        code_refs = item.get("code_refs", []) if isinstance(item.get("code_refs"), list) else []
        test_refs = item.get("test_refs", []) if isinstance(item.get("test_refs"), list) else []
        evidence_refs = item.get("evidence_refs", []) if isinstance(item.get("evidence_refs"), list) else []
        if impl in {"IMPLEMENTED", "PARTIAL", "STUB"} and not code_refs:
            errors.append(f"IMPLEMENTATION_WITHOUT_CODE:{cid}")
        if impl in {"IMPLEMENTED", "PARTIAL"} and not test_refs:
            errors.append(f"IMPLEMENTATION_WITHOUT_TEST:{cid}")
        if impl == "IMPLEMENTED" and verification in {"NOT_RUN", "BLOCKED", "INCONCLUSIVE"}:
            errors.append(f"IMPLEMENTED_WITHOUT_EXECUTED_VERIFICATION:{cid}")
        if verification == "PASS" and not evidence_refs:
            errors.append(f"PASS_WITHOUT_EVIDENCE:{cid}")
        if verification == "PASS" and matrix.get("assurance", {}).get("independent_otester") != "PASS":
            errors.append(f"PASS_WITHOUT_INDEPENDENT_OTESTER:{cid}")

        steps = item.get("process_steps", []) if isinstance(item.get("process_steps"), list) else []
        step_ids = [s.get("step_id") for s in steps if isinstance(s, dict)]
        for value, count in Counter(step_ids).items():
            if count > 1:
                errors.append(f"DUPLICATE_PROCESS_STEP:{cid}:{value}")
        seen: set[str] = set()
        for step in steps:
            if not isinstance(step, dict) or not step.get("step_id"):
                errors.append(f"PROCESS_STEP_ID_MISSING:{cid}")
                continue
            sid = str(step["step_id"])
            requires = step.get("requires", [])
            outputs = step.get("outputs", [])
            if not isinstance(requires, list) or not isinstance(outputs, list) or not outputs:
                errors.append(f"PROCESS_STEP_CONTRACT_INVALID:{cid}:{sid}")
            for pred in requires if isinstance(requires, list) else []:
                if pred not in seen:
                    errors.append(f"PROCESS_PREDECESSOR_MISSING_OR_OUT_OF_ORDER:{cid}:{sid}:{pred}")
            seen.add(sid)

        data = item.get("required_data", []) if isinstance(item.get("required_data"), list) else []
        data_ids = {d.get("data_id") for d in data if isinstance(d, dict)}
        for d in data:
            if not isinstance(d, dict) or not d.get("data_id") or not d.get("producer") or not d.get("consumer"):
                errors.append(f"REQUIRED_DATA_CONTRACT_INVALID:{cid}")
                continue
            if d.get("lineage_required") is True and not d.get("parent_binding"):
                errors.append(f"LINEAGE_PARENT_BINDING_MISSING:{cid}:{d.get('data_id')}")
        for step in steps:
            if isinstance(step, dict):
                for data_id in step.get("consumes", []) if isinstance(step.get("consumes"), list) else []:
                    if data_id not in data_ids:
                        errors.append(f"PROCESS_CONSUMES_UNDECLARED_DATA:{cid}:{step.get('step_id')}:{data_id}")

        controls = item.get("detection_controls", []) if isinstance(item.get("detection_controls"), list) else []
        control_ids = {c.get("control_id") for c in controls if isinstance(c, dict)}
        for failure in item.get("failure_cases", []) if isinstance(item.get("failure_cases"), list) else []:
            if not isinstance(failure, dict) or not failure.get("failure_id"):
                errors.append(f"FAILURE_CASE_CONTRACT_INVALID:{cid}")
                continue
            detects = failure.get("detected_by", [])
            if not isinstance(detects, list) or not detects:
                errors.append(f"FAILURE_CASE_UNDETECTED:{cid}:{failure.get('failure_id')}")
                continue
            for control in detects:
                if control not in control_ids:
                    errors.append(f"FAILURE_CASE_UNKNOWN_CONTROL:{cid}:{failure.get('failure_id')}:{control}")

        if check_paths and root:
            for field in ("design_refs", "contract_refs", "code_refs", "test_refs"):
                for ref in item.get(field, []) if isinstance(item.get(field), list) else []:
                    path_text = str(ref).split("#", 1)[0].split(":L", 1)[0]
                    if path_text and not (root / path_text).exists():
                        errors.append(f"REFERENCE_PATH_MISSING:{cid}:{field}:{path_text}")

    for document in matrix.get("authoritative_documents", []):
        if document not in mapped_docs:
            errors.append(f"AUTHORITATIVE_DOCUMENT_UNMAPPED:{document}")
    assurance = matrix.get("assurance", {})
    if assurance.get("final_lock_allowed") is not False:
        errors.append("FINAL_LOCK_UNSAFE")
    if assurance.get("production_go") is not False:
        errors.append("PRODUCTION_GO_UNSAFE")
    if assurance.get("commercial_go") is not False:
        errors.append("COMMERCIAL_GO_UNSAFE")
    return sorted(set(errors))


def self_test(matrix: dict[str, Any]) -> list[str]:
    failures: list[str] = []

    def expect(name, mutate, prefix):
        candidate = copy.deepcopy(matrix)
        mutate(candidate)
        violations = validate(candidate, None, False)
        if not any(value.startswith(prefix) for value in violations):
            failures.append(f"SELF_TEST_MISSED:{name}:{prefix}:{violations[:5]}")

    expect("missing capability", lambda m: m["capabilities"].pop(0), "REQUIRED_CAPABILITY_MISSING:")
    expect("duplicate capability", lambda m: m["capabilities"].append(copy.deepcopy(m["capabilities"][0])), "DUPLICATE_CAPABILITY:")
    expect("unknown profile", lambda m: m["capabilities"][0].update(coverage_profile="MISSING"), "COVERAGE_PROFILE_UNKNOWN:")
    expect("missing process", lambda m: m["capabilities"][0].update(process_steps=[]), "CAPABILITY_REQUIRED_LIST_EMPTY:")
    expect("broken predecessor", lambda m: m["capabilities"][0].update(process_steps=[{"step_id": "VERIFY", "requires": ["PROCESS"], "consumes": [], "outputs": ["x"]}]), "PROCESS_PREDECESSOR_MISSING_OR_OUT_OF_ORDER:")
    expect("missing lineage", lambda m: m["capabilities"][0].update(required_data=[{"data_id": "x", "producer": "a", "consumer": "b", "lineage_required": True, "parent_binding": ""}]), "LINEAGE_PARENT_BINDING_MISSING:")
    expect("undetected failure", lambda m: m["capabilities"][0].update(failure_cases=[{"failure_id": "x", "detected_by": []}]), "FAILURE_CASE_UNDETECTED:")
    expect("unknown control", lambda m: m["capabilities"][0].update(failure_cases=[{"failure_id": "x", "detected_by": ["UNKNOWN"]}]), "FAILURE_CASE_UNKNOWN_CONTROL:")
    expect("partial no test", lambda m: m["capabilities"][0].update(implementation_status="PARTIAL", test_refs=[]), "IMPLEMENTATION_WITHOUT_TEST:")
    expect("pass no evidence", lambda m: m["capabilities"][0].update(verification_state="PASS", evidence_refs=[]), "PASS_WITHOUT_EVIDENCE:")
    expect("implemented not run", lambda m: m["capabilities"][0].update(implementation_status="IMPLEMENTED", verification_state="NOT_RUN"), "IMPLEMENTED_WITHOUT_EXECUTED_VERIFICATION:")
    expect("unmapped design", lambda m: m["authoritative_documents"].append("docs/missing-authority.md"), "AUTHORITATIVE_DOCUMENT_UNMAPPED:")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix", type=pathlib.Path, required=True)
    parser.add_argument("--root", type=pathlib.Path)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--skip-path-checks", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    matrix = load_json(args.matrix)
    errors = validate(matrix, args.root, not args.skip_path_checks)
    self_errors = self_test(matrix) if args.self_test else []
    report = {
        "contract": "ONSURE_DESIGN_COVERAGE_VALIDATION_REPORT_V1",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "coverage_errors": errors,
        "self_test_errors": self_errors,
        "failure_injection_count": 12 if args.self_test else 0,
        "final_claim_allowed": False,
    }
    text = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")
    return 0 if report["decision"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
