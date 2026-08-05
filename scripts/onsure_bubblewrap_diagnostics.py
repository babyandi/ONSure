#!/usr/bin/env python3
"""Diagnose whether the current host can run ONSure's rootless bubblewrap boundary."""

from __future__ import annotations

import json
import pathlib
import platform
import shutil
import subprocess
import sys
from typing import Iterable


SYSCTL_PATHS = {
    "kernel.unprivileged_userns_clone": pathlib.Path(
        "/proc/sys/kernel/unprivileged_userns_clone"
    ),
    "user.max_user_namespaces": pathlib.Path("/proc/sys/user/max_user_namespaces"),
    "user.max_net_namespaces": pathlib.Path("/proc/sys/user/max_net_namespaces"),
}


def classify_probe(exit_code: int, output: str) -> tuple[str, str]:
    normalized = output.lower()
    if exit_code == 0:
        return "PASS_NONFINAL", "ROOTLESS_BWRAP_NETWORK_NAMESPACE_AVAILABLE"
    if (
        "loopback" in normalized
        and "rtm_newaddr" in normalized
        and "operation not permitted" in normalized
    ):
        return "BLOCKED_ENVIRONMENT", "BWRAP_LOOPBACK_PERMISSION_DENIED"
    if "no permissions to create new namespace" in normalized:
        return "BLOCKED_ENVIRONMENT", "USER_NAMESPACE_CREATION_DENIED"
    if "unshare" in normalized and "operation not permitted" in normalized:
        return "BLOCKED_ENVIRONMENT", "NAMESPACE_CREATION_DENIED"
    return "BLOCKED_ENVIRONMENT", "BWRAP_PROBE_FAILED"


def read_sysctls() -> dict[str, str]:
    values: dict[str, str] = {}
    for name, path in SYSCTL_PATHS.items():
        try:
            values[name] = path.read_text(encoding="utf-8").strip()
        except OSError:
            values[name] = "UNAVAILABLE"
    return values


def run_probe() -> tuple[int, str, str]:
    executable = shutil.which("bwrap")
    if executable is None:
        return 127, "bwrap executable not found", "NOT_INSTALLED"
    version = subprocess.run(
        [executable, "--version"],
        text=True,
        capture_output=True,
        check=False,
    )
    probe = subprocess.run(
        [
            executable,
            "--die-with-parent",
            "--new-session",
            "--unshare-user",
            "--uid",
            "0",
            "--gid",
            "0",
            "--unshare-net",
            "--unshare-pid",
            "--unshare-ipc",
            "--unshare-uts",
            "--cap-drop",
            "ALL",
            "--ro-bind",
            "/",
            "/",
            "--proc",
            "/proc",
            "--dev",
            "/dev",
            "/bin/true",
        ],
        text=True,
        capture_output=True,
        check=False,
        timeout=10,
    )
    output = (probe.stdout + probe.stderr).strip()
    return probe.returncode, output, (version.stdout + version.stderr).strip()


def build_report(exit_code: int, output: str, version: str) -> dict[str, object]:
    if exit_code == 127 and version == "NOT_INSTALLED":
        decision, reason = "BLOCKED_ENVIRONMENT", "BWRAP_NOT_INSTALLED"
    else:
        decision, reason = classify_probe(exit_code, output)
    return {
        "contract": "ONSURE_BUBBLEWRAP_ENVIRONMENT_DIAGNOSTIC_V1",
        "decision": decision,
        "reason_code": reason,
        "probe_exit_code": exit_code,
        "probe_output": output[-2000:] if output else "",
        "bwrap_version": version or "UNKNOWN",
        "platform": {
            "system": platform.system(),
            "release": platform.release(),
            "machine": platform.machine(),
        },
        "namespace_limits": read_sysctls(),
        "required_security_properties": [
            "ROOTLESS_USER_NAMESPACE",
            "PRIVATE_NETWORK_NAMESPACE",
            "LOOPBACK_CONFIGURATION_INSIDE_PRIVATE_NAMESPACE",
            "CAPABILITY_DROP_ALL",
            "READ_ONLY_SOURCE_BIND",
        ],
        "security_fallback_allowed": False,
        "github_actions_required": False,
        "next_command": (
            "bash scripts/onsure-local-gate.sh --mode full --profile core"
            if decision == "PASS_NONFINAL"
            else "READ docs/operations/ONSURE_BUBBLEWRAP_EXECUTION_ENVIRONMENT_v1.md"
        ),
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    del argv
    try:
        exit_code, output, version = run_probe()
    except (OSError, subprocess.SubprocessError) as error:
        exit_code, output, version = 1, str(error), "UNKNOWN"
    report = build_report(exit_code, output, version)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
