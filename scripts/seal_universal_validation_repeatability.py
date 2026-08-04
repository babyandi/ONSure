#!/usr/bin/env python3
"""Seal two current-source universal runs after comparing their stable semantics."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import pathlib
import re


SCRIPT_ROOT = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "seal_universal_validation_observation",
    SCRIPT_ROOT / "seal_universal_validation_observation.py",
)
OBSERVATION = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(OBSERVATION)
COMMIT = re.compile(r"^[0-9a-f]{40}$")


def canonical_digest(value: object) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def stable_projection(observation: dict) -> dict:
    return {
        "assurance_class": observation["assurance_class"],
        "decision": observation["decision"],
        "phase_outcomes": observation["phase_outcomes"],
        "verification_group_outcomes": observation["verification_group_outcomes"],
        "not_run_reasons": observation["not_run_reasons"],
        "source_digest": observation["source_digest"],
        "snapshot_digest": observation["snapshot_digest"],
        "source_mutation_detected": observation["source_mutation_detected"],
        "environment_sha256": observation["environment_sha256"],
        "verified_pass_step_count": observation["verified_pass_step_count"],
        "steps": [{
            key: step[key] for key in (
                "step_id", "phase", "kind", "required", "outcome", "exit_code",
                "environment_sha256", "output_truncated", "reason",
            )
        } for step in observation["steps"]],
    }


def build_receipt(observations: list[dict], source_commit: str) -> dict:
    if COMMIT.fullmatch(source_commit) is None:
        raise ValueError("SOURCE_COMMIT_INVALID")
    if len(observations) != 2:
        raise ValueError("EXACTLY_TWO_RUNS_REQUIRED")
    projections = [stable_projection(item) for item in observations]
    if any(item["decision"] != "PASS_NONFINAL" for item in observations):
        raise ValueError("REPEAT_RUN_NOT_PASS_NONFINAL")
    if projections[0] != projections[1]:
        raise ValueError("REPEAT_RUN_SEMANTIC_DRIFT")
    body = {
        "contract": "ONSURE_UNIVERSAL_VALIDATION_REPEATABILITY_V1",
        "decision": "PASS_NONFINAL",
        "source_commit": source_commit,
        "run_count": 2,
        "semantic_projection_sha256": canonical_digest(projections[0]),
        "source_digest": projections[0]["source_digest"],
        "environment_sha256": projections[0]["environment_sha256"],
        "verified_pass_step_count_per_run": projections[0]["verified_pass_step_count"],
        "runs": [{
            "profile_id": item["profile_id"],
            "result_sha256": item["result_sha256"],
            "finalization_sha256": item["finalization_sha256"],
            "observation_sha256": canonical_digest(item),
        } for item in observations],
        "source_mutation_detected": False,
        "production_authority": False,
        "final_claim_allowed": False,
    }
    body["receipt_sha256"] = canonical_digest(body)
    return body


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result", action="append", required=True, type=pathlib.Path)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    if len(args.result) != 2:
        raise ValueError("EXACTLY_TWO_RUNS_REQUIRED")
    observations = [OBSERVATION.verify_observation(
        f"onsure-self-repeat-{index}", result, args.source_commit,
    ) for index, result in enumerate(args.result, 1)]
    body = build_receipt(observations, args.source_commit)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({
        "contract": body["contract"], "decision": body["decision"],
        "run_count": body["run_count"], "receipt_sha256": body["receipt_sha256"],
        "output": args.output.as_posix(),
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
