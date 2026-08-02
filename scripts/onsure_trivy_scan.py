#!/usr/bin/env python3
"""Run a pinned Trivy container against the committed CycloneDX SBOM and bind evidence."""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
IMAGE = "aquasec/trivy:0.65.0"
SBOM = ROOT / "assurance/dependencies/onsure.cdx.json"
EVIDENCE = ROOT / "assurance/dependencies/onsure-vulnerability-scan.v1.json"


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command: list[str]) -> str:
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    if result.returncode:
        raise ValueError("TRIVY_COMMAND_FAILED:" + (result.stderr or result.stdout)[-2000:])
    return result.stdout.strip()


def scan(cache: pathlib.Path, raw_output: pathlib.Path) -> dict[str, object]:
    if not SBOM.is_file():
        raise ValueError("TRIVY_SBOM_MISSING")
    cache = cache.resolve()
    raw_output = raw_output.resolve()
    if ROOT not in cache.parents or ROOT not in raw_output.parents:
        raise ValueError("TRIVY_OUTPUT_MUST_BE_INSIDE_PRODUCT_ROOT")
    cache.mkdir(parents=True, exist_ok=True)
    raw_output.parent.mkdir(parents=True, exist_ok=True)
    raw_output.unlink(missing_ok=True)
    run([
        "docker", "run", "--rm", "--user", f"{os.getuid()}:{os.getgid()}",
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--pids-limit", "256",
        "--volume", f"{ROOT}:/scan-input:ro",
        "--volume", f"{cache}:/cache:rw",
        "--volume", f"{raw_output.parent}:/out:rw",
        IMAGE, "sbom", "--cache-dir", "/cache", "--format", "json",
        "--output", "/out/" + raw_output.name,
        "/scan-input/assurance/dependencies/onsure.cdx.json",
    ])
    body = json.loads(raw_output.read_text(encoding="utf-8"))
    counts = {severity: 0 for severity in ("critical", "high", "medium", "low")}
    for result in body.get("Results", []):
        for vulnerability in result.get("Vulnerabilities") or []:
            severity = str(vulnerability.get("Severity", "")).lower()
            if severity in counts:
                counts[severity] += 1
    version_output = run(["docker", "run", "--rm", IMAGE, "--version"])
    version = next((line.split(":", 1)[1].strip() for line in version_output.splitlines()
                    if line.lower().startswith("version:")), version_output.splitlines()[0])
    metadata = body.get("Metadata", {})
    database_updated = metadata.get("DB", {}).get("UpdatedAt", "NOT_REPORTED_BY_SCANNER_OUTPUT")
    evidence = {
        "contract": "ONSURE_VULNERABILITY_SCAN_EVIDENCE_V1",
        "state": "COMPLETED",
        "source_sbom": "assurance/dependencies/onsure.cdx.json",
        "source_sbom_file_sha256": sha256(SBOM),
        "scanner": "TRIVY",
        "scanner_version": version,
        "scanner_image": IMAGE,
        "database_updated_at": database_updated,
        "scanned_at": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        **counts,
        "raw_result_sha256": sha256(raw_output),
        "suppressions": [],
        "reason": "Pinned Trivy container scan of the digest-bound CycloneDX SBOM.",
        "release_gate_eligible": counts["critical"] == 0 and counts["high"] == 0,
        "final_claim_allowed": False,
    }
    descriptor, temporary = tempfile.mkstemp(prefix=EVIDENCE.name + ".", dir=EVIDENCE.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(evidence, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, EVIDENCE)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return evidence


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=pathlib.Path, default=ROOT / ".onsure/trivy-cache")
    parser.add_argument("--raw-output", type=pathlib.Path, default=ROOT / ".onsure/trivy-result.json")
    args = parser.parse_args()
    print(json.dumps(scan(args.cache, args.raw_output), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("ONSURE_TRIVY_SCAN_FAIL " + str(error), file=__import__("sys").stderr)
        raise SystemExit(1)
