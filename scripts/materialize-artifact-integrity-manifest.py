#!/usr/bin/env python3
"""Materialize the content SHA-256 artifact manifest + CLEAN deterministic rerun evidence
(159 SS11 gate 10/11; Autonomous Development Mode standing policy execution queue item 9).

Two independent guarantees, both real (not asserted):
1. artifact_manifest: sha256 of every git-tracked contracts/*.json and contracts/*.schema.json
   file, plus this progress registry itself -- a tamper-evidence baseline a future run can diff
   against.
2. deterministic_rerun: runs scripts/generate-requirement-universe.py TWICE in a row (clean,
   no flags -- the live epoch path) and requires requirement_manifest_digest to match both times,
   satisfying doc 159 SS11 gate 11's "minimum two independent clean reruns" as a standalone,
   inspectable artifact rather than only a transient unittest assertion.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_tracked_contract_files() -> list[str]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", "contracts"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    paths = sorted(item.decode("utf-8") for item in result.stdout.split(b"\0") if item)
    return [p for p in paths if p.endswith(".json")]


def run_generator() -> dict:
    result = subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "generate-requirement-universe.py")],
        cwd=ROOT, text=True, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"GENERATOR_RUN_FAILED:{result.stdout}{result.stderr}")
    return json.loads(result.stdout)


def main() -> int:
    contract_paths = git_tracked_contract_files()
    artifact_manifest = {
        path: sha256_file(ROOT / path) for path in contract_paths if (ROOT / path).exists()
    }
    manifest_digest_source = "\n".join(f"{p}:{h}" for p, h in sorted(artifact_manifest.items())).encode()
    artifact_manifest_digest = hashlib.sha256(manifest_digest_source).hexdigest()

    registry_path = ROOT / "contracts" / "claude-development-progress-registry.v1.json"
    registry_digest = sha256_file(registry_path)

    run1 = run_generator()
    run2 = run_generator()
    digests_match = run1["requirement_manifest_digest"] == run2["requirement_manifest_digest"]

    manifest = {
        "contract": "ONSURE_ARTIFACT_INTEGRITY_MANIFEST_V1",
        "authority_ref": "docs/master/semantic-assurance/159_REVERSE_ALIGNMENT_AND_GLOBAL_LOCK_GATE_PREPARATION.md SS11 gates 10/11",
        "artifact_manifest_file_count": len(artifact_manifest),
        "artifact_manifest_digest": artifact_manifest_digest,
        "artifact_manifest": artifact_manifest,
        "progress_registry_content_sha256": registry_digest,
        "deterministic_rerun": {
            "rerun_count": 2,
            "run_1_requirement_manifest_digest": run1["requirement_manifest_digest"],
            "run_2_requirement_manifest_digest": run2["requirement_manifest_digest"],
            "digests_match": digests_match,
            "decision": "CLEAN_DETERMINISTIC_RERUN_CONFIRMED" if digests_match else "NONDETERMINISM_DETECTED",
        },
        "self_validation_nonfinal": True,
        "final_claim_allowed": False,
    }

    out_path = ROOT / ".onsure" / "requirement-universe" / "artifact-integrity-manifest.v1.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(json.dumps({
        "artifact_manifest_file_count": len(artifact_manifest),
        "artifact_manifest_digest": artifact_manifest_digest,
        "digests_match": digests_match,
    }))
    return 0 if digests_match else 1


if __name__ == "__main__":
    raise SystemExit(main())
