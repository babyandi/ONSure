#!/usr/bin/env python3
"""Read-only Ubuntu host preflight without reading runtime secret values."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import stat
import subprocess
from typing import Iterable


SERVICES = ("onsure-runtime.service", "onsure-llm-gateway.service")
PORTS = (47311, 47312, 5432)


def run(command: list[str]) -> tuple[int, str]:
    completed = subprocess.run(
        command, capture_output=True, check=False, text=True, timeout=10
    )
    return completed.returncode, (completed.stdout + completed.stderr).strip()


def parse_os_release(text: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line or line.lstrip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        values[key] = value.strip().strip('"')
    return values


def loopback_address(value: str) -> bool:
    address = value.strip("[]")
    if address.startswith("::ffff:"):
        address = address.removeprefix("::ffff:")
    return address in {"127.0.0.1", "::1", "localhost"}


def parse_listeners(text: str) -> dict[int, list[str]]:
    listeners = {port: [] for port in PORTS}
    for line in text.splitlines():
        columns = line.split()
        if len(columns) < 4:
            continue
        endpoint = columns[3]
        match = re.match(r"^(.+):(\d+)$", endpoint)
        if not match:
            continue
        port = int(match.group(2))
        if port in listeners:
            listeners[port].append(match.group(1))
    return listeners


def evaluate(observation: dict[str, object]) -> dict[str, object]:
    errors: list[str] = []
    blockers = ["PRODUCTION_ACCEPTANCE_NOT_AUTHORIZED"]
    os_release = observation.get("os_release", {})
    if not isinstance(os_release, dict) or os_release.get("ID") != "ubuntu" \
            or os_release.get("VERSION_ID") != "24.04":
        errors.append("UBUNTU_24_04_REQUIRED")

    services = observation.get("services", {})
    service_result: dict[str, dict[str, bool]] = {}
    for name in SERVICES:
        current = services.get(name, {}) if isinstance(services, dict) else {}
        active = isinstance(current, dict) and current.get("active") is True
        enabled = isinstance(current, dict) and current.get("enabled") is True
        service_result[name] = {"active": active, "enabled": enabled}
        if not active:
            errors.append("SERVICE_NOT_ACTIVE:" + name)
        if not enabled:
            errors.append("SERVICE_NOT_ENABLED:" + name)

    listeners = observation.get("listeners", {})
    listener_result: dict[str, dict[str, object]] = {}
    for port in PORTS:
        addresses = listeners.get(port, []) if isinstance(listeners, dict) else []
        if not isinstance(addresses, list):
            addresses = []
        loopback_only = bool(addresses) and all(loopback_address(str(item)) for item in addresses)
        listener_result[str(port)] = {
            "listener_count": len(addresses), "loopback_only": loopback_only
        }
        if not addresses:
            errors.append("LISTENER_MISSING:" + str(port))
        elif not loopback_only:
            errors.append("NON_LOOPBACK_LISTENER:" + str(port))

    config = observation.get("runtime_config", {})
    config_exists = isinstance(config, dict) and config.get("exists") is True
    config_mode = config.get("mode") if isinstance(config, dict) else None
    if not config_exists:
        errors.append("RUNTIME_CONFIG_MISSING")
    elif config_mode != "0600":
        errors.append("RUNTIME_CONFIG_MODE_NOT_0600")

    apparmor = observation.get("apparmor", {})
    module_enabled = isinstance(apparmor, dict) and apparmor.get("module_enabled") is True
    profile_status = apparmor.get("profile_status") if isinstance(apparmor, dict) else "UNKNOWN"
    if not module_enabled:
        errors.append("APPARMOR_MODULE_NOT_ENABLED")
    if profile_status != "VERIFIED":
        blockers.append("APPARMOR_PROFILE_ENFORCEMENT_NOT_VERIFIED")

    ufw = observation.get("ufw", {})
    ufw_status = ufw.get("status") if isinstance(ufw, dict) else "UNKNOWN"
    if ufw_status != "ACTIVE_VERIFIED":
        blockers.append("UFW_POLICY_NOT_VERIFIED")

    return {
        "contract": "ONSURE_UBUNTU_HOST_PREFLIGHT_V1",
        "decision": "PASS_NONFINAL" if not errors else "FAIL",
        "errors": errors,
        "production_blockers": blockers,
        "host_os": "UBUNTU_24_04" if not errors or (
            isinstance(os_release, dict) and os_release.get("ID") == "ubuntu"
            and os_release.get("VERSION_ID") == "24.04"
        ) else "UNSUPPORTED",
        "services": service_result,
        "listeners": listener_result,
        "apparmor": {
            "module_enabled": module_enabled, "profile_status": profile_status
        },
        "ufw": {"status": ufw_status},
        "runtime_config": {
            "exists": config_exists,
            "mode": config_mode,
            "path_disclosed": False,
            "secret_values_read": False,
        },
        "host_modified": False,
        "production_acceptance": "NOT_RUN",
        "final_claim_allowed": False,
    }


def observe(runtime_root: pathlib.Path) -> dict[str, object]:
    os_release = parse_os_release(pathlib.Path("/etc/os-release").read_text(encoding="utf-8"))
    services: dict[str, dict[str, bool]] = {}
    for name in SERVICES:
        active_code, active_output = run(["systemctl", "--user", "is-active", name])
        enabled_code, enabled_output = run(["systemctl", "--user", "is-enabled", name])
        services[name] = {
            "active": active_code == 0 and active_output.splitlines()[:1] == ["active"],
            "enabled": enabled_code == 0 and enabled_output.splitlines()[:1] == ["enabled"],
        }

    _, socket_output = run(["ss", "-H", "-ltn"])
    parameter = pathlib.Path("/sys/module/apparmor/parameters/enabled")
    module_enabled = parameter.is_file() and parameter.read_text(encoding="ascii").strip() == "Y"
    aa_code, aa_output = run(["aa-status"]) if pathlib.Path("/usr/sbin/aa-status").exists() else (127, "")
    if aa_code == 0:
        profile_status = "VERIFIED"
    elif "enough privilege" in aa_output.lower():
        profile_status = "NOT_RUN_INSUFFICIENT_PRIVILEGE"
    else:
        profile_status = "NOT_RUN_UNAVAILABLE"

    ufw_code, ufw_output = run(["ufw", "status"]) if pathlib.Path("/usr/sbin/ufw").exists() else (127, "")
    if ufw_code == 0 and re.search(r"^Status:\s+active$", ufw_output, re.MULTILINE):
        ufw_status = "ACTIVE_VERIFIED"
    elif "need to be root" in ufw_output.lower():
        ufw_status = "NOT_RUN_INSUFFICIENT_PRIVILEGE"
    else:
        ufw_status = "NOT_RUN_UNAVAILABLE"

    config_path = runtime_root / "config" / "onsure.env"
    try:
        file_stat = config_path.stat()
        config = {
            "exists": stat.S_ISREG(file_stat.st_mode),
            "mode": format(stat.S_IMODE(file_stat.st_mode), "04o"),
        }
    except FileNotFoundError:
        config = {"exists": False, "mode": None}
    return {
        "os_release": os_release,
        "services": services,
        "listeners": parse_listeners(socket_output),
        "apparmor": {"module_enabled": module_enabled, "profile_status": profile_status},
        "ufw": {"status": ufw_status},
        "runtime_config": config,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime-root", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args(argv)
    result = evaluate(observe(args.runtime_root.resolve()))
    encoded = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        print("ONSURE_UBUNTU_HOST_PREFLIGHT_FAIL " + str(error))
        raise SystemExit(1)
