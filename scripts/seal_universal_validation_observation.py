#!/usr/bin/env python3
"""Seal any universal validation outcome after reading every claimed evidence file."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
from datetime import datetime


PHASES = {"STRUCTURE_STATIC", "COMPONENT_AND_NEGATIVE", "END_TO_END_LINEAGE", "OPERATIONAL_RESILIENCE"}
GROUPS = {
    "ENVIRONMENT_DEPENDENCY", "STRUCTURE", "VALIDATOR_META", "STAGE_FUNCTIONAL",
    "CONNECTED_E2E", "EVIDENCE_DECISION", "OPERATIONS_RECOVERY",
}
OUTCOMES = {"PASS_NONFINAL", "FAIL", "BLOCKED", "NOT_RUN", "INCONCLUSIVE"}
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical_digest(value: dict) -> str:
    return digest_bytes(json.dumps(value, sort_keys=True, separators=(",", ":")).encode())


def parse_time(value: object, label: str) -> datetime:
    require(isinstance(value, str), label + "_INVALID")
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ValueError(label + "_INVALID") from error


def verified_file(run_root: pathlib.Path, claimed_path: object, claimed_sha: object, label: str) -> str:
    require(isinstance(claimed_path, str) and SHA256.fullmatch(str(claimed_sha)) is not None,
            label + "_CLAIM_INVALID")
    path = pathlib.Path(claimed_path)
    require(path.is_absolute() and not path.is_symlink(), label + "_PATH_INVALID")
    resolved = path.resolve(strict=True)
    require(resolved.is_relative_to(run_root) and resolved.is_file(), label + "_PATH_ESCAPE_OR_INVALID")
    actual = digest_bytes(resolved.read_bytes())
    require(actual == claimed_sha, label + "_DIGEST_MISMATCH")
    return actual


def verify_observation(target_id: str, result_file: pathlib.Path, source_commit: str) -> dict:
    require(re.fullmatch(r"[a-z][a-z0-9-]{0,63}", target_id) is not None, "TARGET_ID_INVALID")
    require(COMMIT.fullmatch(source_commit) is not None, "SOURCE_COMMIT_INVALID")
    result_file = result_file.resolve(strict=True)
    run_root = result_file.parent.resolve(strict=True)
    result = json.loads(result_file.read_text(encoding="utf-8"))
    require(result.get("contract") == "ONSURE_UNIVERSAL_VALIDATION_RUN_V1", "RUN_CONTRACT_INVALID")
    require(result.get("overall_outcome") in OUTCOMES, "RUN_OUTCOME_INVALID")
    require(set((result.get("phase_outcomes") or {}).keys()) == PHASES, "RUN_PHASE_SET_INVALID")
    require(set((result.get("verification_group_outcomes") or {}).keys()) == GROUPS,
            "RUN_GROUP_SET_INVALID")
    require(set(result["phase_outcomes"].values()) <= OUTCOMES, "RUN_PHASE_OUTCOME_INVALID")
    require(set(result["verification_group_outcomes"].values()) <= OUTCOMES, "RUN_GROUP_OUTCOME_INVALID")
    require(result.get("source_mutation_detected") is False, "RUN_SOURCE_MUTATED")
    require(result.get("source_digest") == result.get("snapshot_digest"), "RUN_SNAPSHOT_DIGEST_DRIFT")
    require(SHA256.fullmatch(str(result.get("source_digest", ""))) is not None, "RUN_SOURCE_DIGEST_INVALID")
    require(result.get("final_claim_allowed") is False, "RUN_FINAL_AUTHORITY_UNSAFE")
    environment = result.get("environment_evidence")
    require(isinstance(environment, dict) and SHA256.fullmatch(str(environment.get("sha256", ""))) is not None,
            "RUN_ENVIRONMENT_INVALID")
    run_started = parse_time(result.get("started_at"), "RUN_STARTED_AT")
    run_completed = parse_time(result.get("completed_at"), "RUN_COMPLETED_AT")
    require(run_started <= run_completed, "RUN_TIME_ORDER_INVALID")

    sealed_steps = []
    for index, step in enumerate(result.get("steps", [])):
        require(isinstance(step, dict) and step.get("outcome") in OUTCOMES, "RUN_STEP_INVALID")
        started = parse_time(step.get("startedAt"), "RUN_STEP_STARTED_AT")
        completed = parse_time(step.get("completedAt"), "RUN_STEP_COMPLETED_AT")
        require(run_started <= started <= completed <= run_completed, "RUN_STEP_TIME_ORDER_INVALID")
        require(step.get("environmentSha256") == environment["sha256"], "RUN_STEP_ENVIRONMENT_DRIFT")
        output_sha = verified_file(run_root, step.get("logFile"), step.get("outputSha256"),
                                   f"RUN_STEP_{index}_LOG")
        sealed_steps.append({
            "step_id": step.get("stepId"), "phase": step.get("phase"), "kind": step.get("kind"),
            "required": step.get("required"), "outcome": step.get("outcome"),
            "exit_code": step.get("exitCode"), "output_sha256": output_sha,
            "environment_sha256": step.get("environmentSha256"),
            "output_truncated": step.get("outputTruncated"), "reason": step.get("reason"),
            "started_at": step.get("startedAt"), "completed_at": step.get("completedAt"),
        })

    finalization = result.get("final_evidence_integrity")
    require(isinstance(finalization, dict), "RUN_FINALIZATION_MISSING")
    require(finalization.get("contract") == "ONSURE_PASS_EVIDENCE_FINALIZATION_V1",
            "RUN_FINALIZATION_CONTRACT_INVALID")
    final_sha = verified_file(run_root, finalization.get("log_file"), finalization.get("output_sha256"),
                              "RUN_FINALIZATION_LOG")
    pass_count = sum(step["outcome"] == "PASS_NONFINAL" for step in sealed_steps)
    require(finalization.get("verified_pass_step_count") == pass_count, "RUN_FINALIZATION_COUNT_INVALID")
    require(finalization.get("environment_sha256") == environment["sha256"],
            "RUN_FINALIZATION_ENVIRONMENT_DRIFT")
    return {
        "contract": "ONSURE_UNIVERSAL_VALIDATION_OBSERVATION_V1",
        "target_id": target_id,
        "source_commit": source_commit,
        "profile_id": result.get("profile_id"),
        "assurance_class": result.get("assurance_class"),
        "decision": result.get("overall_outcome"),
        "phase_outcomes": result.get("phase_outcomes"),
        "verification_group_outcomes": result.get("verification_group_outcomes"),
        "not_run_reasons": result.get("not_run_reasons"),
        "source_digest": result.get("source_digest"),
        "snapshot_digest": result.get("snapshot_digest"),
        "source_mutation_detected": False,
        "environment_sha256": environment["sha256"],
        "result_sha256": digest_bytes(result_file.read_bytes()),
        "finalization_sha256": final_sha,
        "verified_pass_step_count": pass_count,
        "steps": sealed_steps,
        "production_authority": False,
        "final_claim_allowed": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target-id", required=True)
    parser.add_argument("--result", required=True, type=pathlib.Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    body = verify_observation(args.target_id, args.result, args.source_commit)
    body["receipt_sha256"] = canonical_digest(body)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"contract": body["contract"], "decision": body["decision"],
                      "receipt_sha256": body["receipt_sha256"], "output": args.output.as_posix()},
                     sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
