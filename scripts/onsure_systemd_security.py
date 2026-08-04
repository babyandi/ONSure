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
UBUNTU_OUTPUT = ROOT / "assurance/runtime/onsure-ubuntu-systemd-security.v1.json"
UNITS = (
    ROOT / "deploy/rhel/onsure.service",
    ROOT / "deploy/rhel/onsure-llm-gateway.service",
    ROOT / "deploy/rhel/onsure-migrate.service",
    ROOT / "deploy/ubuntu/onsure-backup.service",
)
MAXIMUM_EXPOSURE = 4.0


def command(arguments: list[str]) -> str:
    result = subprocess.run(arguments, cwd=ROOT, text=True, capture_output=True, check=False)
    if result.returncode:
        raise ValueError("SYSTEMD_ANALYZE_FAILED:" + (result.stderr or result.stdout)[-1000:])
    return result.stdout + result.stderr


def host_os() -> str:
    values: dict[str, str] = {}
    for line in pathlib.Path("/etc/os-release").read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value.strip('"')
    return f"{values.get('ID', 'unknown').upper()}_{values.get('VERSION_ID', 'unknown').replace('.', '_')}"


def analyze(platform: str = "rhel") -> dict[str, object]:
    if platform not in ("rhel", "ubuntu"):
        raise ValueError("SYSTEMD_PLATFORM_UNSUPPORTED:" + platform)
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
    result: dict[str, object] = {
        "contract": f"ONSURE_{platform.upper()}_SYSTEMD_SECURITY_REHEARSAL_V1",
        "decision": "PASS_NONFINAL" if all(item["decision"] == "PASS_NONFINAL" for item in units) else "FAIL",
        "platform": platform.upper(),
        "host_os": host_os(),
        "systemd_analyze_version": version,
        "analysis_mode": "OFFLINE_HOST_TOOL",
        "units": units,
        "service_enable_start": "NOT_RUN",
        "apparmor_or_selinux_execution": "NOT_RUN",
        "firewall_execution": "NOT_RUN",
        "final_claim_allowed": False,
    }
    if platform == "rhel":
        result["rhel_runtime_execution"] = "NOT_RUN_HOST_IS_NOT_RHEL"
    else:
        result["ubuntu_runtime_execution"] = "OFFLINE_ANALYSIS_ONLY"
    return result


def run(platform: str = "rhel") -> int:
    result = analyze(platform)
    output = OUTPUT if platform == "rhel" else UBUNTU_OUTPUT
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


def main() -> int:
    return run("rhel")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print("ONSURE_SYSTEMD_SECURITY_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
