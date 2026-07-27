#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import json
import pathlib
from collections import Counter
from typing import Any

REQUIRED_STAGES = [
    "SOURCE_INTAKE", "PROGRAM_PROFILE", "EXECUTION_PLAN", "PLAN_APPROVAL",
    "BEHAVIOR_PROFILE", "OREVIEW", "VERIFICATION", "RCA", "PATCH_PLAN",
    "HUNK_APPROVAL", "PATCH_APPLY", "REVALIDATION", "IMPROVEMENT_PROOF",
    "GIT_APPROVAL", "COMMIT", "PUSH", "DRAFT_PR", "EVIDENCE_LOCK",
    "INDEPENDENT_OTESTER", "INDEPENDENT_OAUDIT"
]
REQUIRED_ARTIFACTS = {
    "SOURCE_SNAPSHOT", "PROGRAM_PROFILE", "EXECUTION_PLAN", "PLAN_APPROVAL_RECEIPT",
    "BEHAVIOR_PROFILE", "OREVIEW_RESULT", "VALIDATION_REPORT", "RCA_SET", "PATCH_PLAN",
    "HUNK_APPROVAL_RECEIPT", "PATCH_APPLY_RECEIPT", "REVALIDATION_REPORT",
    "IMPROVEMENT_PROOF", "GIT_APPROVAL_RECEIPT", "GIT_CHANGE_SET", "PUSH_RECEIPT",
    "DRAFT_PR_RECEIPT", "EVIDENCE_BUNDLE", "OTESTER_RECEIPT", "OAUDIT_RECEIPT"
}


def validate(model: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if model.get("contract") != "ONSURE_PRODUCT_PROCESS_LINEAGE_V1":
        errors.append("CONTRACT_MISMATCH")
    stages = model.get("stages")
    artifacts = model.get("artifacts")
    if not isinstance(stages, list):
        return errors + ["STAGES_NOT_ARRAY"]
    if not isinstance(artifacts, list):
        return errors + ["ARTIFACTS_NOT_ARRAY"]

    stage_ids = [stage.get("stage_id") for stage in stages if isinstance(stage, dict)]
    artifact_ids = [artifact.get("artifact_id") for artifact in artifacts if isinstance(artifact, dict)]
    for value, count in Counter(stage_ids).items():
        if count > 1:
            errors.append(f"DUPLICATE_STAGE:{value}")
    for value, count in Counter(artifact_ids).items():
        if count > 1:
            errors.append(f"DUPLICATE_ARTIFACT:{value}")
    for stage in REQUIRED_STAGES:
        if stage not in stage_ids:
            errors.append(f"REQUIRED_STAGE_MISSING:{stage}")
    if stage_ids != REQUIRED_STAGES:
        errors.append(f"PROCESS_STAGE_SEQUENCE_MISMATCH:{stage_ids}")
    for artifact in sorted(REQUIRED_ARTIFACTS - set(artifact_ids)):
        errors.append(f"REQUIRED_ARTIFACT_MISSING:{artifact}")

    artifact_by_id = {artifact.get("artifact_id"): artifact for artifact in artifacts if isinstance(artifact, dict)}
    seen_stages: set[str] = set()
    produced: set[str] = set()
    for stage in stages:
        if not isinstance(stage, dict) or not stage.get("stage_id"):
            errors.append("STAGE_CONTRACT_INVALID")
            continue
        stage_id = str(stage["stage_id"])
        requires = stage.get("requires", [])
        consumes = stage.get("consumes", [])
        outputs = stage.get("outputs", [])
        controls = stage.get("controls", [])
        if not isinstance(requires, list) or not isinstance(consumes, list) or not isinstance(outputs, list):
            errors.append(f"STAGE_LIST_INVALID:{stage_id}")
            continue
        if not outputs:
            errors.append(f"STAGE_OUTPUT_MISSING:{stage_id}")
        if not isinstance(controls, list) or not controls:
            errors.append(f"STAGE_CONTROL_MISSING:{stage_id}")
        for predecessor in requires:
            if predecessor not in seen_stages:
                errors.append(f"STAGE_PREDECESSOR_MISSING_OR_OUT_OF_ORDER:{stage_id}:{predecessor}")
        for artifact_id in consumes:
            if artifact_id not in artifact_by_id:
                errors.append(f"STAGE_CONSUMES_UNDECLARED_ARTIFACT:{stage_id}:{artifact_id}")
            elif artifact_id not in produced:
                errors.append(f"STAGE_CONSUMES_UNPRODUCED_ARTIFACT:{stage_id}:{artifact_id}")
        for artifact_id in outputs:
            if artifact_id not in artifact_by_id:
                errors.append(f"STAGE_PRODUCES_UNDECLARED_ARTIFACT:{stage_id}:{artifact_id}")
            elif artifact_by_id[artifact_id].get("producer") != stage_id:
                errors.append(f"ARTIFACT_PRODUCER_MISMATCH:{artifact_id}:{stage_id}")
            produced.add(artifact_id)
        seen_stages.add(stage_id)

    for artifact in artifacts:
        if not isinstance(artifact, dict) or not artifact.get("artifact_id"):
            errors.append("ARTIFACT_CONTRACT_INVALID")
            continue
        artifact_id = str(artifact["artifact_id"])
        producer = artifact.get("producer")
        parents = artifact.get("parent_bindings")
        if producer not in stage_ids:
            errors.append(f"ARTIFACT_UNKNOWN_PRODUCER:{artifact_id}:{producer}")
        if artifact.get("lineage_required") is True:
            if not isinstance(parents, list) or (artifact_id != "SOURCE_SNAPSHOT" and not parents):
                errors.append(f"ARTIFACT_PARENT_BINDING_MISSING:{artifact_id}")
            for parent in parents if isinstance(parents, list) else []:
                if parent not in artifact_by_id:
                    errors.append(f"ARTIFACT_UNKNOWN_PARENT:{artifact_id}:{parent}")
        if artifact.get("tenant_bound") is not True:
            errors.append(f"ARTIFACT_NOT_TENANT_BOUND:{artifact_id}")
        if artifact.get("source_bound") is not True:
            errors.append(f"ARTIFACT_NOT_SOURCE_BOUND:{artifact_id}")
        if not artifact.get("missing_detection"):
            errors.append(f"ARTIFACT_MISSING_DETECTION_UNDEFINED:{artifact_id}")

    invariants = set(model.get("invariants", []))
    for required in {
        "NO_STAGE_SKIP", "NO_STALE_RECEIPT_REPLAY", "NO_CROSS_TENANT_SUBSTITUTION",
        "NO_FINAL_WITHOUT_INDEPENDENT_RECEIPTS", "NO_COMMIT_WITHOUT_IMPROVEMENT_PROOF",
        "NO_PATCH_WITHOUT_HUNK_APPROVAL", "NO_RCA_CONFIRMATION_WITHOUT_CAUSAL_EXPERIMENT",
        "NO_DELETION_DURING_LEGAL_HOLD", "NO_LICENSE_CREDIT_INVARIANT_BREAK",
        "NO_VERIFICATION_BEFORE_PLAN_APPROVAL", "NO_REVIEW_BEFORE_BEHAVIOR_OBSERVATION"
    }:
        if required not in invariants:
            errors.append(f"REQUIRED_INVARIANT_MISSING:{required}")

    release = model.get("release_gate", {})
    if release.get("independent_otester") != "NOT_RUN":
        errors.append("UNSUPPORTED_OTESTER_STATE")
    if release.get("independent_oaudit") != "NOT_RUN":
        errors.append("UNSUPPORTED_OAUDIT_STATE")
    if release.get("final_lock_allowed") is not False:
        errors.append("FINAL_LOCK_UNSAFE")
    if release.get("production_go") is not False:
        errors.append("PRODUCTION_GO_UNSAFE")
    if release.get("commercial_go") is not False:
        errors.append("COMMERCIAL_GO_UNSAFE")
    return sorted(set(errors))


def self_test(model: dict[str, Any]) -> list[str]:
    missed: list[str] = []

    def expect(name, mutate, prefix):
        candidate = copy.deepcopy(model)
        mutate(candidate)
        errors = validate(candidate)
        if not any(value.startswith(prefix) for value in errors):
            missed.append(f"SELF_TEST_MISSED:{name}:{prefix}:{errors[:6]}")

    expect("stage omitted", lambda model: model["stages"].pop(1), "REQUIRED_STAGE_MISSING:")
    expect("artifact omitted", lambda model: model["artifacts"].pop(1), "REQUIRED_ARTIFACT_MISSING:")
    expect("out of order", lambda model: model["stages"].insert(0, model["stages"].pop(5)), "PROCESS_STAGE_SEQUENCE_MISMATCH:")
    expect("consume before produce", lambda model: model["stages"][0].update(consumes=["PROGRAM_PROFILE"]), "STAGE_CONSUMES_UNPRODUCED_ARTIFACT:")
    expect("parent dropped", lambda model: model["artifacts"][1].update(parent_bindings=[]), "ARTIFACT_PARENT_BINDING_MISSING:")
    expect("unknown parent", lambda model: model["artifacts"][1].update(parent_bindings=["MISSING"]), "ARTIFACT_UNKNOWN_PARENT:")
    expect("producer mismatch", lambda model: model["artifacts"][1].update(producer="VERIFICATION"), "ARTIFACT_PRODUCER_MISMATCH:")
    expect("cross tenant", lambda model: model["artifacts"][1].update(tenant_bound=False), "ARTIFACT_NOT_TENANT_BOUND:")
    expect("source unbound", lambda model: model["artifacts"][1].update(source_bound=False), "ARTIFACT_NOT_SOURCE_BOUND:")
    expect("missing detector absent", lambda model: model["artifacts"][1].update(missing_detection=""), "ARTIFACT_MISSING_DETECTION_UNDEFINED:")
    expect("stage no control", lambda model: model["stages"][1].update(controls=[]), "STAGE_CONTROL_MISSING:")
    expect("stale replay invariant removed", lambda model: model["invariants"].remove("NO_STALE_RECEIPT_REPLAY"), "REQUIRED_INVARIANT_MISSING:")
    expect("commit proof invariant removed", lambda model: model["invariants"].remove("NO_COMMIT_WITHOUT_IMPROVEMENT_PROOF"), "REQUIRED_INVARIANT_MISSING:")
    expect("causal RCA invariant removed", lambda model: model["invariants"].remove("NO_RCA_CONFIRMATION_WITHOUT_CAUSAL_EXPERIMENT"), "REQUIRED_INVARIANT_MISSING:")
    expect("final unsafe", lambda model: model["release_gate"].update(final_lock_allowed=True), "FINAL_LOCK_UNSAFE")
    expect("duplicate receipt artifact", lambda model: model["artifacts"].append(copy.deepcopy(model["artifacts"][1])), "DUPLICATE_ARTIFACT:")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=pathlib.Path, required=True)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    model = json.loads(args.model.read_text(encoding="utf-8"))
    errors = validate(model)
    missed = self_test(model) if args.self_test else []
    report = {
        "contract": "ONSURE_PRODUCT_PROCESS_LINEAGE_REPORT_V2",
        "decision": "PASS" if not errors and not missed else "FAIL",
        "errors": errors,
        "self_test_errors": missed,
        "failure_injection_count": 16 if args.self_test else 0,
        "final_claim_allowed": False,
    }
    text = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    print(text, end="")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    return 0 if report["decision"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
