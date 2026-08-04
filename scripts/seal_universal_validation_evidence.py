#!/usr/bin/env python3
"""Verify universal-run receipts and emit a path-free, digest-bound evidence set."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re


PHASES = {"STRUCTURE_STATIC", "COMPONENT_AND_NEGATIVE", "END_TO_END_LINEAGE", "OPERATIONAL_RESILIENCE"}
GROUPS = {
    "ENVIRONMENT_DEPENDENCY", "STRUCTURE", "VALIDATOR_META", "STAGE_FUNCTIONAL",
    "CONNECTED_E2E", "EVIDENCE_DECISION", "OPERATIONS_RECOVERY",
}
SHA256 = re.compile(r"^[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")


def digest_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def canonical_digest(value: dict) -> str:
    return digest_bytes(json.dumps(value, sort_keys=True, separators=(",", ":")).encode())


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def verified_file(run_root: pathlib.Path, claimed_path: object, claimed_sha: object, label: str) -> str:
    require(isinstance(claimed_path, str), label + "_PATH_INVALID")
    path = pathlib.Path(claimed_path)
    require(path.is_absolute(), label + "_PATH_NOT_ABSOLUTE")
    resolved = path.resolve(strict=True)
    require(resolved.is_relative_to(run_root), label + "_PATH_ESCAPE")
    require(resolved.is_file() and not path.is_symlink(), label + "_FILE_INVALID")
    actual = digest_bytes(resolved.read_bytes())
    require(actual == claimed_sha, label + "_DIGEST_MISMATCH")
    return actual


def verify_run(target_id: str, result_file: pathlib.Path) -> dict:
    require(re.fullmatch(r"[a-z][a-z0-9-]{0,63}", target_id) is not None, "TARGET_ID_INVALID")
    result_file = result_file.resolve(strict=True)
    run_root = result_file.parent.resolve(strict=True)
    result = json.loads(result_file.read_text(encoding="utf-8"))
    require(result.get("contract") == "ONSURE_UNIVERSAL_VALIDATION_RUN_V1", "RUN_CONTRACT_INVALID")
    require(result.get("overall_outcome") == "PASS_NONFINAL", "RUN_NOT_PASS_NONFINAL")
    require(result.get("phase_outcomes") == {key: "PASS_NONFINAL" for key in PHASES}, "RUN_PHASES_INCOMPLETE")
    require(result.get("verification_group_outcomes") == {key: "PASS_NONFINAL" for key in GROUPS},
            "RUN_GROUPS_INCOMPLETE")
    require(result.get("not_run_reasons") == {}, "RUN_HAS_NOT_RUN_REASON")
    require(result.get("source_mutation_detected") is False, "RUN_SOURCE_MUTATED")
    require(result.get("source_digest") == result.get("snapshot_digest"), "RUN_SNAPSHOT_DIGEST_DRIFT")
    require(result.get("final_claim_allowed") is False, "RUN_FINAL_AUTHORITY_UNSAFE")
    environment = result.get("environment_evidence")
    require(isinstance(environment, dict) and SHA256.fullmatch(str(environment.get("sha256", ""))) is not None,
            "RUN_ENVIRONMENT_INVALID")

    sealed_steps = []
    for step in result.get("steps", []):
        require(isinstance(step, dict), "RUN_STEP_INVALID")
        require(step.get("required") is True and step.get("outcome") == "PASS_NONFINAL", "RUN_STEP_NOT_PASS")
        require(step.get("exitCode") == 0, "RUN_STEP_EXIT_NONZERO")
        require(step.get("environmentSha256") == environment["sha256"], "RUN_STEP_ENVIRONMENT_DRIFT")
        output_sha = verified_file(run_root, step.get("logFile"), step.get("outputSha256"), "RUN_STEP_LOG")
        sealed_steps.append({
            "step_id": step.get("stepId"), "phase": step.get("phase"), "kind": step.get("kind"),
            "outcome": step.get("outcome"), "exit_code": step.get("exitCode"),
            "output_sha256": output_sha, "environment_sha256": step.get("environmentSha256"),
            "output_truncated": step.get("outputTruncated"), "reason": step.get("reason"),
        })

    finalization = result.get("final_evidence_integrity")
    require(isinstance(finalization, dict), "RUN_FINALIZATION_MISSING")
    require(finalization.get("contract") == "ONSURE_PASS_EVIDENCE_FINALIZATION_V1", "RUN_FINALIZATION_CONTRACT")
    require(finalization.get("outcome") == "PASS_NONFINAL", "RUN_FINALIZATION_NOT_PASS")
    require(finalization.get("verified_pass_step_count") == len(sealed_steps), "RUN_FINALIZATION_COUNT")
    final_sha = verified_file(
        run_root, finalization.get("log_file"), finalization.get("output_sha256"), "RUN_FINALIZATION_LOG")
    require(finalization.get("environment_sha256") == environment["sha256"], "RUN_FINALIZATION_ENVIRONMENT")
    return {
        "target_id": target_id,
        "profile_id": result.get("profile_id"),
        "assurance_class": result.get("assurance_class"),
        "technologies": sorted(result.get("technologies", [])),
        "overall_outcome": result.get("overall_outcome"),
        "phase_outcomes": result.get("phase_outcomes"),
        "verification_group_outcomes": result.get("verification_group_outcomes"),
        "source_digest": result.get("source_digest"),
        "snapshot_digest": result.get("snapshot_digest"),
        "source_mutation_detected": False,
        "environment_sha256": environment["sha256"],
        "started_at": result.get("started_at"),
        "completed_at": result.get("completed_at"),
        "result_sha256": digest_bytes(result_file.read_bytes()),
        "finalization_sha256": final_sha,
        "verified_pass_step_count": len(sealed_steps),
        "steps": sealed_steps,
    }


def parse_run(value: str) -> tuple[str, pathlib.Path]:
    target, separator, path = value.partition("=")
    if not separator or not target or not path:
        raise argparse.ArgumentTypeError("run must use target-id=/absolute/result.json")
    return target, pathlib.Path(path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", action="append", required=True, type=parse_run)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--oci-image-id", required=True)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    require(COMMIT.fullmatch(args.source_commit) is not None, "SOURCE_COMMIT_INVALID")
    require(re.fullmatch(r"sha256:[0-9a-f]{64}", args.oci_image_id) is not None, "OCI_IMAGE_ID_INVALID")
    target_ids = [target for target, _ in args.run]
    require(len(target_ids) == len(set(target_ids)), "TARGET_ID_DUPLICATED")
    body = {
        "contract": "ONSURE_UNIVERSAL_VALIDATION_EVIDENCE_SET_V1",
        "decision": "PASS_NONFINAL",
        "source_commit": args.source_commit,
        "oci_image_id": args.oci_image_id,
        "runs": [verify_run(target, path) for target, path in args.run],
        "production_authority": False,
        "final_claim_allowed": False,
    }
    body["receipt_sha256"] = canonical_digest(body)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({
        "contract": body["contract"], "decision": body["decision"],
        "run_count": len(body["runs"]), "receipt_sha256": body["receipt_sha256"],
        "output": args.output.as_posix(),
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
