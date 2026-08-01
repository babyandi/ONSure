#!/usr/bin/env python3
from __future__ import annotations

import argparse
import copy
import datetime as dt
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
POLICY_PATH = ROOT / "contracts/main-branch-protection.v1.json"
EVIDENCE_PATH = ROOT / "status/main-branch-protection-evidence.v1.json"
SHA256 = re.compile(r"^[0-9a-f]{64}$")
CONTROL_TYPES = {
    "pull_request_required": bool,
    "minimum_approvals": int,
    "dismiss_stale_approvals": bool,
    "conversation_resolution_required": bool,
    "direct_push_blocked": bool,
    "force_push_blocked": bool,
    "branch_deletion_blocked": bool,
    "administrators_enforced": bool,
}


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def validate(policy: dict, evidence: dict) -> list[str]:
    errors: list[str] = []
    if policy.get("contract") != "ONSURE_MAIN_BRANCH_PROTECTION_V1":
        errors.append("MAIN_PROTECTION_POLICY_CONTRACT_INVALID")
    if evidence.get("contract") != "ONSURE_MAIN_BRANCH_PROTECTION_EVIDENCE_V1":
        errors.append("MAIN_PROTECTION_EVIDENCE_CONTRACT_INVALID")
    for field in ("repository", "branch"):
        if policy.get(field) != evidence.get(field):
            errors.append(f"MAIN_PROTECTION_TARGET_MISMATCH:{field}")
    if policy.get("repository") != "babyandi/ONSure" or policy.get("branch") != "main":
        errors.append("MAIN_PROTECTION_CANONICAL_TARGET_INVALID")

    controls = policy.get("required_controls")
    if not isinstance(controls, dict) or set(controls) != set(CONTROL_TYPES):
        errors.append("MAIN_PROTECTION_CONTROL_SET_INVALID")
    else:
        for name, expected_type in CONTROL_TYPES.items():
            value = controls.get(name)
            if type(value) is not expected_type:
                errors.append(f"MAIN_PROTECTION_CONTROL_TYPE_INVALID:{name}")
            elif name == "minimum_approvals" and value < 1:
                errors.append("MAIN_PROTECTION_APPROVAL_COUNT_WEAKENED")
            elif name != "minimum_approvals" and value is not True:
                errors.append(f"MAIN_PROTECTION_CONTROL_WEAKENED:{name}")
    if policy.get("github_actions_required") is not False:
        errors.append("MAIN_PROTECTION_ACTIONS_REQUIREMENT_FORBIDDEN")
    if policy.get("independent_status_checks_required_before_merge") is not True:
        errors.append("MAIN_PROTECTION_INDEPENDENT_CHECKS_WEAKENED")
    if policy.get("final_claim_allowed") is not False:
        errors.append("MAIN_PROTECTION_POLICY_FINAL_CLAIM_UNSAFE")

    state = evidence.get("observation_state")
    if state not in {"NOT_RUN", "OBSERVED"}:
        errors.append("MAIN_PROTECTION_OBSERVATION_STATE_INVALID")
    if evidence.get("final_claim_allowed") is not False:
        errors.append("MAIN_PROTECTION_EVIDENCE_FINAL_CLAIM_UNSAFE")
    if state == "NOT_RUN":
        if evidence.get("decision") != "HOLD":
            errors.append("MAIN_PROTECTION_NOT_RUN_MUST_HOLD")
        for field in (
            "observed_at", "observed_source", "observed_controls",
            "source_commit", "evidence_sha256",
        ):
            if evidence.get(field) is not None:
                errors.append(f"MAIN_PROTECTION_NOT_RUN_OVERCLAIM:{field}")
    else:
        if evidence.get("decision") not in {"PASS_NONFINAL", "FAIL"}:
            errors.append("MAIN_PROTECTION_OBSERVED_DECISION_INVALID")
        try:
            observed_at = dt.datetime.fromisoformat(str(evidence.get("observed_at")).replace("Z", "+00:00"))
            if observed_at.tzinfo is None:
                raise ValueError
        except ValueError:
            errors.append("MAIN_PROTECTION_OBSERVED_AT_INVALID")
        if evidence.get("observed_source") not in {"GITHUB_API", "GITHUB_RULESET_EXPORT"}:
            errors.append("MAIN_PROTECTION_OBSERVED_SOURCE_INVALID")
        if not SHA256.fullmatch(str(evidence.get("source_commit", ""))):
            errors.append("MAIN_PROTECTION_SOURCE_COMMIT_INVALID")
        if not SHA256.fullmatch(str(evidence.get("evidence_sha256", ""))):
            errors.append("MAIN_PROTECTION_EVIDENCE_DIGEST_INVALID")
        observed = evidence.get("observed_controls")
        if not isinstance(observed, dict) or not isinstance(controls, dict):
            errors.append("MAIN_PROTECTION_OBSERVED_CONTROLS_INVALID")
        else:
            drift = [
                name for name, required in controls.items()
                if name not in observed
                or (name == "minimum_approvals" and observed[name] < required)
                or (name != "minimum_approvals" and observed[name] is not required)
            ]
            expected = "FAIL" if drift else "PASS_NONFINAL"
            if evidence.get("decision") != expected:
                errors.append(f"MAIN_PROTECTION_DECISION_MISMATCH:{expected}")
    return sorted(set(errors))


def self_test(policy: dict, evidence: dict) -> list[str]:
    missed: list[str] = []

    def expect(name: str, mutate_policy, mutate_evidence, prefix: str) -> None:
        candidate_policy = copy.deepcopy(policy)
        candidate_evidence = copy.deepcopy(evidence)
        mutate_policy(candidate_policy)
        mutate_evidence(candidate_evidence)
        errors = validate(candidate_policy, candidate_evidence)
        if not any(error.startswith(prefix) for error in errors):
            missed.append(f"MAIN_PROTECTION_SELF_TEST_MISSED:{name}:{prefix}")

    expect("direct push weakened", lambda value: value["required_controls"].update(direct_push_blocked=False), lambda value: None, "MAIN_PROTECTION_CONTROL_WEAKENED")
    expect("force push weakened", lambda value: value["required_controls"].update(force_push_blocked=False), lambda value: None, "MAIN_PROTECTION_CONTROL_WEAKENED")
    expect("approval removed", lambda value: value["required_controls"].update(minimum_approvals=0), lambda value: None, "MAIN_PROTECTION_APPROVAL_COUNT_WEAKENED")
    expect("control omitted", lambda value: value["required_controls"].pop("administrators_enforced"), lambda value: None, "MAIN_PROTECTION_CONTROL_SET_INVALID")
    expect("actions enabled", lambda value: value.update(github_actions_required=True), lambda value: None, "MAIN_PROTECTION_ACTIONS_REQUIREMENT_FORBIDDEN")
    expect("not-run overclaim", lambda value: None, lambda value: value.update(observed_source="GITHUB_API"), "MAIN_PROTECTION_NOT_RUN_OVERCLAIM")
    expect("not-run pass", lambda value: None, lambda value: value.update(decision="PASS_NONFINAL"), "MAIN_PROTECTION_NOT_RUN_MUST_HOLD")
    expect("unsafe final", lambda value: None, lambda value: value.update(final_claim_allowed=True), "MAIN_PROTECTION_EVIDENCE_FINAL_CLAIM_UNSAFE")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    policy = load(POLICY_PATH)
    evidence = load(EVIDENCE_PATH)
    errors = validate(policy, evidence)
    self_errors = self_test(policy, evidence) if args.self_test else []
    report = {
        "contract": "ONSURE_MAIN_BRANCH_PROTECTION_REPORT_V1",
        "decision": "PASS_NONFINAL" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "observation_state": evidence.get("observation_state"),
        "server_side_enforcement": "NOT_PROVEN" if evidence.get("observation_state") == "NOT_RUN" else evidence.get("decision"),
        "failure_injection_count": 8 if args.self_test else 0,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors or self_errors:
        print("ONSURE_MAIN_BRANCH_PROTECTION_GATE_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_MAIN_BRANCH_PROTECTION_GATE_PASS_NONFINAL")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
