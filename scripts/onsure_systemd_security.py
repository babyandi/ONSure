#!/usr/bin/env python3
"""Verify candidate systemd units and record digest-bound offline exposure scores."""

from __future__ import annotations

import hashlib
import json
import pathlib
import re
import subprocess
import sys

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
OUTPUT = ROOT / "assurance/runtime/onsure-rhel-systemd-security.v1.json"
UNITS = (ROOT / "deploy/rhel/onsure.service", ROOT / "deploy/rhel/onsure-migrate.service")
MAXIMUM_EXPOSURE = 4.0


def command(arguments: list[str]) -> str:
    result = subprocess.run(arguments, cwd=ROOT, text=True, capture_output=True, check=False)
    if result.returncode:
        raise ValueError("SYSTEMD_ANALYZE_FAILED:" + (result.stderr or result.stdout)[-1000:])
    return result.stdout + result.stderr


def analyze() -> dict[str, object]:
    command(["systemd-analyze", "verify", *(str(path) for path in UNITS)])
    units: list[dict[str, object]] = []
    for path in UNITS:
        output = command([
            "systemd-analyze", "security", "--offline=yes", str(path), "--no-pager",
        ])
        match = re.search(r"Overall exposure level for [^:]+:\s+([0-9]+(?:\.[0-9]+)?)", output)
        if not match:
            raise ValueError("SYSTEMD_EXPOSURE_SCORE_MISSING:" + path.name)
        score = float(match.group(1))
        units.append({
            "path": path.relative_to(ROOT).as_posix(),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "exposure_score": score,
            "maximum_allowed": MAXIMUM_EXPOSURE,
            "decision": "PASS_NONFINAL" if score <= MAXIMUM_EXPOSURE else "FAIL",
        })
    version = command(["systemd-analyze", "--version"]).splitlines()[0]
    return {
        "contract": "ONSURE_RHEL_SYSTEMD_SECURITY_REHEARSAL_V1",
        "decision": "PASS_NONFINAL" if all(item["decision"] == "PASS_NONFINAL" for item in units) else "FAIL",
        "systemd_analyze_version": version,
        "analysis_mode": "OFFLINE_HOST_TOOL",
        "units": units,
        "rhel_runtime_execution": "NOT_RUN_HOST_IS_NOT_RHEL",
        "service_enable_start": "NOT_RUN",
        "final_claim_allowed": False,
    }


def main() -> int:
    result = analyze()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print("ONSURE_SYSTEMD_SECURITY_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
