#!/usr/bin/env python3
"""Parse-check ONSure AppArmor profiles without loading kernel policy."""

from __future__ import annotations

import hashlib
import json
import pathlib
import subprocess
import sys

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
PROFILE = ROOT / "deploy/ubuntu/apparmor.d/onsure"
OUTPUT = ROOT / "assurance/runtime/onsure-ubuntu-apparmor-candidate.v1.json"
PROFILE_NAMES = ("onsure-api", "onsure-llm-gateway", "onsure-migrate")
DROPINS = {
    "deploy/ubuntu/systemd/onsure.service.d/10-apparmor.conf": "onsure-api",
    "deploy/ubuntu/systemd/onsure-llm-gateway.service.d/10-apparmor.conf": (
        "onsure-llm-gateway"
    ),
    "deploy/ubuntu/systemd/onsure-migrate.service.d/10-apparmor.conf": "onsure-migrate",
}


def validate_documents(profile_text: str, dropins: dict[str, str]) -> list[str]:
    violations: list[str] = []
    for name in PROFILE_NAMES:
        if f"profile {name} " not in profile_text:
            violations.append("APPARMOR_PROFILE_MISSING:" + name)
    if "flags=(complain)" in profile_text:
        violations.append("APPARMOR_PACKAGE_COMPLAIN_MODE")
    for forbidden in ("/** rwklmix,", "capability sys_admin", "network raw"):
        if forbidden in profile_text:
            violations.append("APPARMOR_OVERBROAD_RULE:" + forbidden)
    for path, expected_profile in DROPINS.items():
        value = dropins.get(path, "")
        if "Requires=apparmor.service" not in value \
                or "After=apparmor.service" not in value \
                or f"AppArmorProfile={expected_profile}" not in value:
            violations.append("APPARMOR_DROPIN_BINDING:" + path)
    return violations


def validate() -> dict[str, object]:
    profile_text = PROFILE.read_text(encoding="utf-8")
    dropins = {
        path: (ROOT / path).read_text(encoding="utf-8") for path in DROPINS
    }
    violations = validate_documents(profile_text, dropins)
    parsed = subprocess.run(
        ["apparmor_parser", "-Q", "-K", str(PROFILE)],
        cwd=ROOT, capture_output=True, check=False, text=True, timeout=15,
    )
    if parsed.returncode != 0:
        violations.append("APPARMOR_PARSER:" + (parsed.stderr or parsed.stdout)[-500:])
    names = subprocess.run(
        ["apparmor_parser", "-N", "-Q", "-K", str(PROFILE)],
        cwd=ROOT, capture_output=True, check=False, text=True, timeout=15,
    )
    parsed_names = tuple(line.strip() for line in names.stdout.splitlines() if line.strip())
    if names.returncode != 0 or parsed_names != PROFILE_NAMES:
        violations.append("APPARMOR_PARSED_PROFILE_SET")
    return {
        "contract": "ONSURE_UBUNTU_APPARMOR_CANDIDATE_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "profile": PROFILE.relative_to(ROOT).as_posix(),
        "profile_sha256": hashlib.sha256(PROFILE.read_bytes()).hexdigest(),
        "profiles": list(PROFILE_NAMES),
        "parser_mode": "SKIP_KERNEL_LOAD",
        "kernel_policy_loaded": False,
        "complain_rehearsal": "NOT_RUN",
        "enforce_execution": "NOT_RUN",
        "production_acceptance": "NOT_RUN",
        "final_claim_allowed": False,
    }


def main() -> int:
    result = validate()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print("ONSURE_UBUNTU_APPARMOR_VALIDATION_FAIL " + str(error), file=sys.stderr)
        raise SystemExit(1)
