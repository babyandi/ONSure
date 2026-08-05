#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
import pathlib
import subprocess
import sys
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
    capability_id = str(item.get("capability_id", "UNKNOWN"))
    for field in ("process_steps", "required_data", "failure_cases", "detection_controls"):
        value = copy.deepcopy(profile.get(field, []))
        encoded = json.dumps(value).replace("${CAP}", capability_id)
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
    for capability_id in sorted(REQUIRED_CAPABILITIES - set(ids)):
        errors.append(f"REQUIRED_CAPABILITY_MISSING:{capability_id}")

    mapped_docs: set[str] = set()
    for index, raw in enumerate(items):
        if not isinstance(raw, dict):
            errors.append(f"CAPABILITY_NOT_OBJECT:{index}")
            continue
        capability_id = str(raw.get("capability_id", f"INDEX-{index}"))
        for field in sorted(BASE_FIELDS - set(raw)):
            errors.append(f"CAPABILITY_FIELD_MISSING:{capability_id}:{field}")
        item = expand(matrix, raw)
        if raw.get("coverage_profile") not in matrix.get("coverage_profiles", {}):
            errors.append(f"COVERAGE_PROFILE_UNKNOWN:{capability_id}:{raw.get('coverage_profile')}")
        implementation, verification = item.get("implementation_status"), item.get("verification_state")
        if implementation not in ALLOWED_IMPLEMENTATION:
            errors.append(f"IMPLEMENTATION_STATUS_INVALID:{capability_id}:{implementation}")
        if verification not in ALLOWED_VERIFICATION:
            errors.append(f"VERIFICATION_STATE_INVALID:{capability_id}:{verification}")

        for field in ("design_refs", "contract_refs", "code_refs", "test_refs", "evidence_refs", "limitations",
                      "process_steps", "required_data", "failure_cases", "detection_controls"):
            if not isinstance(item.get(field), list):
                errors.append(f"CAPABILITY_LIST_FIELD_INVALID:{capability_id}:{field}")
        for field in ("design_refs", "process_steps", "required_data", "failure_cases", "detection_controls"):
            if not item.get(field):
                errors.append(f"CAPABILITY_REQUIRED_LIST_EMPTY:{capability_id}:{field}")

        design_refs = item.get("design_refs", []) if isinstance(item.get("design_refs"), list) else []
        mapped_docs.update(str(value).split("#", 1)[0].split(":L", 1)[0] for value in design_refs)
        code_refs = item.get("code_refs", []) if isinstance(item.get("code_refs"), list) else []
        test_refs = item.get("test_refs", []) if isinstance(item.get("test_refs"), list) else []
        evidence_refs = item.get("evidence_refs", []) if isinstance(item.get("evidence_refs"), list) else []
        if implementation in {"IMPLEMENTED", "PARTIAL", "STUB"} and not code_refs:
            errors.append(f"IMPLEMENTATION_WITHOUT_CODE:{capability_id}")
        if implementation in {"IMPLEMENTED", "PARTIAL"} and not test_refs:
            errors.append(f"IMPLEMENTATION_WITHOUT_TEST:{capability_id}")
        if implementation == "IMPLEMENTED" and verification in {"NOT_RUN", "BLOCKED", "INCONCLUSIVE"}:
            errors.append(f"IMPLEMENTED_WITHOUT_EXECUTED_VERIFICATION:{capability_id}")
        if verification == "PASS" and not evidence_refs:
            errors.append(f"PASS_WITHOUT_EVIDENCE:{capability_id}")
        if verification == "PASS" and matrix.get("assurance", {}).get("independent_otester") != "PASS":
            errors.append(f"PASS_WITHOUT_INDEPENDENT_OTESTER:{capability_id}")

        steps = item.get("process_steps", []) if isinstance(item.get("process_steps"), list) else []
        step_ids = [step.get("step_id") for step in steps if isinstance(step, dict)]
        for value, count in Counter(step_ids).items():
            if count > 1:
                errors.append(f"DUPLICATE_PROCESS_STEP:{capability_id}:{value}")
        seen: set[str] = set()
        for step in steps:
            if not isinstance(step, dict) or not step.get("step_id"):
                errors.append(f"PROCESS_STEP_ID_MISSING:{capability_id}")
                continue
            step_id = str(step["step_id"])
            requires = step.get("requires", [])
            outputs = step.get("outputs", [])
            if not isinstance(requires, list) or not isinstance(outputs, list) or not outputs:
                errors.append(f"PROCESS_STEP_CONTRACT_INVALID:{capability_id}:{step_id}")
            for predecessor in requires if isinstance(requires, list) else []:
                if predecessor not in seen:
                    errors.append(f"PROCESS_PREDECESSOR_MISSING_OR_OUT_OF_ORDER:{capability_id}:{step_id}:{predecessor}")
            seen.add(step_id)

        data = item.get("required_data", []) if isinstance(item.get("required_data"), list) else []
        data_ids = {entry.get("data_id") for entry in data if isinstance(entry, dict)}
        for entry in data:
            if not isinstance(entry, dict) or not entry.get("data_id") or not entry.get("producer") or not entry.get("consumer"):
                errors.append(f"REQUIRED_DATA_CONTRACT_INVALID:{capability_id}")
                continue
            if entry.get("lineage_required") is True and not entry.get("parent_binding"):
                errors.append(f"LINEAGE_PARENT_BINDING_MISSING:{capability_id}:{entry.get('data_id')}")
        for step in steps:
            if isinstance(step, dict):
                for data_id in step.get("consumes", []) if isinstance(step.get("consumes"), list) else []:
                    if data_id not in data_ids:
                        errors.append(f"PROCESS_CONSUMES_UNDECLARED_DATA:{capability_id}:{step.get('step_id')}:{data_id}")

        controls = item.get("detection_controls", []) if isinstance(item.get("detection_controls"), list) else []
        control_ids = {control.get("control_id") for control in controls if isinstance(control, dict)}
        for failure in item.get("failure_cases", []) if isinstance(item.get("failure_cases"), list) else []:
            if not isinstance(failure, dict) or not failure.get("failure_id"):
                errors.append(f"FAILURE_CASE_CONTRACT_INVALID:{capability_id}")
                continue
            detects = failure.get("detected_by", [])
            if not isinstance(detects, list) or not detects:
                errors.append(f"FAILURE_CASE_UNDETECTED:{capability_id}:{failure.get('failure_id')}")
                continue
            for control in detects:
                if control not in control_ids:
                    errors.append(f"FAILURE_CASE_UNKNOWN_CONTROL:{capability_id}:{failure.get('failure_id')}:{control}")

        if check_paths and root:
            for field in ("design_refs", "contract_refs", "code_refs", "test_refs"):
                for reference in item.get(field, []) if isinstance(item.get(field), list) else []:
                    path_text = str(reference).split("#", 1)[0].split(":L", 1)[0]
                    if path_text and not (root / path_text).exists():
                        errors.append(f"REFERENCE_PATH_MISSING:{capability_id}:{field}:{path_text}")

    for document in matrix.get("authoritative_documents", []):
        if document not in mapped_docs:
            errors.append(f"AUTHORITATIVE_DOCUMENT_UNMAPPED:{document}")
    universal = matrix.get("universal_validation_authority")
    if not isinstance(universal, dict):
        errors.append("UNIVERSAL_VALIDATION_AUTHORITY_MISSING")
    else:
        expected_phases = [
            "STRUCTURE_STATIC", "COMPONENT_AND_NEGATIVE",
            "END_TO_END_LINEAGE", "OPERATIONAL_RESILIENCE",
        ]
        phases = universal.get("four_phase_model")
        if not isinstance(phases, list) or [item.get("phase_id") for item in phases
                if isinstance(item, dict)] != expected_phases:
            errors.append("UNIVERSAL_FOUR_PHASE_AUTHORITY_INVALID")
        for phase in phases if isinstance(phases, list) else []:
            if not isinstance(phase, dict) or phase.get("implementation_status") not in ALLOWED_IMPLEMENTATION \
                    or phase.get("verification_state") not in ALLOWED_VERIFICATION \
                    or not isinstance(phase.get("evidence_refs"), list):
                errors.append("UNIVERSAL_PHASE_STATE_INVALID")
            elif phase.get("verification_state") == "PASS" and not phase.get("evidence_refs"):
                errors.append(f"UNIVERSAL_PHASE_PASS_WITHOUT_EVIDENCE:{phase.get('phase_id')}")
        targets = universal.get("required_real_targets")
        target_ids = {item.get("target_id") for item in targets if isinstance(item, dict)} \
            if isinstance(targets, list) else set()
        if target_ids != {"self", "python", "node"}:
            errors.append("UNIVERSAL_REQUIRED_REAL_TARGET_SET_INVALID")
        verified = 0
        for target in targets if isinstance(targets, list) else []:
            if not isinstance(target, dict):
                errors.append("UNIVERSAL_REAL_TARGET_ENTRY_INVALID")
                continue
            target_id = target.get("target_id", "UNKNOWN")
            if target.get("required_classification") != "REAL_REPOSITORY":
                errors.append(f"UNIVERSAL_REAL_TARGET_CLASSIFICATION_INVALID:{target_id}")
            state = target.get("verification_state")
            evidence = target.get("evidence_refs")
            if state not in ALLOWED_VERIFICATION or not isinstance(evidence, list):
                errors.append(f"UNIVERSAL_REAL_TARGET_STATE_INVALID:{target_id}")
            if state == "PASS":
                verified += 1
                if not evidence or target.get("provenance_binding_state") != "VERIFIED_BEFORE_AND_AFTER" \
                        or target.get("real_target_universality_evidence_eligible") is not True:
                    errors.append(f"UNIVERSAL_REAL_TARGET_PASS_WITHOUT_BOUND_EVIDENCE:{target_id}")
            elif target.get("real_target_universality_evidence_eligible") is True:
                errors.append(f"UNIVERSAL_REAL_TARGET_UNVERIFIED_ELIGIBILITY:{target_id}")
        if universal.get("required_real_target_count") != 3 \
                or universal.get("verified_real_target_count") != verified:
            errors.append("UNIVERSAL_REAL_TARGET_COUNT_MISMATCH")
        inference = universal.get("automatic_inference")
        if not isinstance(inference, dict) or inference.get("candidate_only") is not True \
                or inference.get("inference_is_pass_evidence") is not False \
                or inference.get("verification_state") == "PASS" \
                or not isinstance(inference.get("evidence_refs"), list):
            errors.append("AUTOMATIC_INFERENCE_AUTHORITY_UNSAFE")
        binding = universal.get("evidence_binding")
        if not isinstance(binding, dict) or binding.get("verification_state") not in ALLOWED_VERIFICATION \
                or binding.get("provenance_alone_is_pass_evidence") is not False \
                or not isinstance(binding.get("evidence_refs"), list) \
                or binding.get("verification_state") == "PASS" and not binding.get("evidence_refs"):
            errors.append("TARGET_PROVENANCE_BINDING_AUTHORITY_INVALID")
        if check_paths and root:
            for section_name, section in (("automatic_inference", inference), ("evidence_binding", binding)):
                if not isinstance(section, dict):
                    continue
                for field in ("code_refs", "test_refs", "evidence_refs"):
                    for reference in section.get(field, []) if isinstance(section.get(field), list) else []:
                        if not (root / str(reference)).exists():
                            errors.append(f"UNIVERSAL_AUTHORITY_REFERENCE_MISSING:{section_name}:{field}:{reference}")
        legacy = universal.get("legacy_evidence_assessment")
        if not isinstance(legacy, dict) or legacy.get("authority_state") \
                != "INVALID_FOR_REAL_TARGET_UNIVERSALITY":
            errors.append("UNIVERSAL_LEGACY_EVIDENCE_ASSESSMENT_INVALID")
        elif check_paths and root:
            evidence_path = root / str(legacy.get("evidence_ref", ""))
            try:
                evidence_set = load_json(evidence_path)
                runs = evidence_set.get("runs", [])
                eligible = [run for run in runs if isinstance(run, dict)
                            and run.get("target_classification") == "REAL_REPOSITORY"
                            and run.get("target_provenance_binding_state") == "VERIFIED_BEFORE_AND_AFTER"
                            and run.get("real_target_universality_evidence_eligible") is True]
                if legacy.get("observed_run_count") != len(runs) \
                        or legacy.get("eligible_real_target_run_count") != len(eligible):
                    errors.append("UNIVERSAL_LEGACY_EVIDENCE_COUNT_MISMATCH")
            except (OSError, ValueError, TypeError, json.JSONDecodeError):
                errors.append("UNIVERSAL_LEGACY_EVIDENCE_UNREADABLE")
        if universal.get("decision") != "HOLD" or universal.get("final_claim_allowed") is not False:
            errors.append("UNIVERSAL_VALIDATION_AUTHORITY_UNSAFE")
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

    expect("missing capability", lambda model: model["capabilities"].pop(0), "REQUIRED_CAPABILITY_MISSING:")
    expect("duplicate capability", lambda model: model["capabilities"].append(copy.deepcopy(model["capabilities"][0])), "DUPLICATE_CAPABILITY:")
    expect("unknown profile", lambda model: model["capabilities"][0].update(coverage_profile="MISSING"), "COVERAGE_PROFILE_UNKNOWN:")
    expect("missing process", lambda model: model["capabilities"][0].update(process_steps=[]), "CAPABILITY_REQUIRED_LIST_EMPTY:")
    expect("broken predecessor", lambda model: model["capabilities"][0].update(process_steps=[{"step_id": "VERIFY", "requires": ["PROCESS"], "consumes": [], "outputs": ["x"]}]), "PROCESS_PREDECESSOR_MISSING_OR_OUT_OF_ORDER:")
    expect("missing lineage", lambda model: model["capabilities"][0].update(required_data=[{"data_id": "x", "producer": "a", "consumer": "b", "lineage_required": True, "parent_binding": ""}]), "LINEAGE_PARENT_BINDING_MISSING:")
    expect("undetected failure", lambda model: model["capabilities"][0].update(failure_cases=[{"failure_id": "x", "detected_by": []}]), "FAILURE_CASE_UNDETECTED:")
    expect("unknown control", lambda model: model["capabilities"][0].update(failure_cases=[{"failure_id": "x", "detected_by": ["UNKNOWN"]}]), "FAILURE_CASE_UNKNOWN_CONTROL:")
    expect("partial no test", lambda model: model["capabilities"][0].update(implementation_status="PARTIAL", test_refs=[]), "IMPLEMENTATION_WITHOUT_TEST:")
    expect("pass no evidence", lambda model: model["capabilities"][0].update(verification_state="PASS", evidence_refs=[]), "PASS_WITHOUT_EVIDENCE:")
    expect("implemented not run", lambda model: model["capabilities"][0].update(implementation_status="IMPLEMENTED", verification_state="NOT_RUN"), "IMPLEMENTED_WITHOUT_EXECUTED_VERIFICATION:")
    expect("unmapped design", lambda model: model["authoritative_documents"].append("docs/missing-authority.md"), "AUTHORITATIVE_DOCUMENT_UNMAPPED:")
    expect("missing universal authority", lambda model: model.pop("universal_validation_authority"), "UNIVERSAL_VALIDATION_AUTHORITY_MISSING")
    expect("missing real target", lambda model: model["universal_validation_authority"]["required_real_targets"].pop(), "UNIVERSAL_REQUIRED_REAL_TARGET_SET_INVALID")
    expect("unbound real target pass", lambda model: model["universal_validation_authority"]["required_real_targets"][0].update(verification_state="PASS"), "UNIVERSAL_REAL_TARGET_PASS_WITHOUT_BOUND_EVIDENCE:")
    expect("inference becomes pass evidence", lambda model: model["universal_validation_authority"]["automatic_inference"].update(inference_is_pass_evidence=True), "AUTOMATIC_INFERENCE_AUTHORITY_UNSAFE")
    expect("phase pass without evidence", lambda model: model["universal_validation_authority"]["four_phase_model"][0].update(verification_state="PASS"), "UNIVERSAL_PHASE_PASS_WITHOUT_EVIDENCE:")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix", type=pathlib.Path, required=True)
    parser.add_argument("--root", type=pathlib.Path)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--skip-path-checks", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--process-model", type=pathlib.Path)
    args = parser.parse_args()
    matrix = load_json(args.matrix)
    errors = validate(matrix, args.root, not args.skip_path_checks)
    self_errors = self_test(matrix) if args.self_test else []
    process_errors: list[str] = []
    process_injections = 0
    root = args.root.resolve() if args.root else None
    process_model = args.process_model
    if process_model is None and root is not None:
        process_model = root / "contracts/product-process-lineage.v1.json"
    if process_model is not None:
        validator = (root / "scripts/validate-product-process-lineage.py") if root else pathlib.Path("scripts/validate-product-process-lineage.py")
        if not validator.is_file() or not process_model.is_file():
            process_errors.append("PRODUCT_PROCESS_LINEAGE_VALIDATOR_OR_MODEL_MISSING")
        else:
            command = [sys.executable, str(validator), "--model", str(process_model)]
            if args.self_test:
                command.append("--self-test")
            result = subprocess.run(command, text=True, capture_output=True, check=False)
            if result.returncode != 0:
                process_errors.append("PRODUCT_PROCESS_LINEAGE_FAIL:" + result.stdout[-2000:] + result.stderr[-1000:])
            else:
                payload = json.loads(result.stdout)
                process_injections = int(payload.get("failure_injection_count", 0))
                if payload.get("decision") != "PASS":
                    process_errors.append("PRODUCT_PROCESS_LINEAGE_NON_PASS")
    report = {
        "contract": "ONSURE_DESIGN_COVERAGE_VALIDATION_REPORT_V2",
        "decision": "PASS" if not errors and not self_errors and not process_errors else "FAIL",
        "coverage_errors": errors,
        "self_test_errors": self_errors,
        "process_lineage_errors": process_errors,
        "failure_injection_count": (12 if args.self_test else 0) + process_injections,
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
