#!/usr/bin/env python3
"""Produce source-bound diagnostics for ONSure's local sandbox backends."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
import tempfile
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
DEFAULT_OUTPUT = ROOT / "assurance/runtime/onsure-sandbox-backends.v1.json"
BOUNDARY = ROOT / "contracts/sandbox-boundary.v1.json"
BOUND_FILES = (
    "contracts/sandbox-boundary.v1.json",
    "scripts/onsure-sandbox-backend.sh",
    "scripts/fixture-sandbox-launcher.sh",
    "scripts/validation-sandbox-launcher.sh",
    "scripts/test-fixture-sandbox-boundary.sh",
    "fixtures/sandbox-boundary/sandbox-boundary-runner.sh",
    "deploy/validation/Dockerfile",
    "scripts/build-onsure-validation-image.sh",
)


def run(arguments: list[str], *, environment: dict[str, str] | None = None,
        timeout: int = 30) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        arguments, cwd=ROOT, env=environment, text=True, capture_output=True,
        check=False, timeout=timeout,
    )


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as body:
        for chunk in iter(lambda: body.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_sha256(value: object) -> str:
    return hashlib.sha256(json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=False,
    ).encode("utf-8")).hexdigest()


def source_commit() -> str:
    result = run(["git", "rev-parse", "HEAD"])
    value = result.stdout.strip()
    if result.returncode or re.fullmatch(r"[0-9a-f]{40}", value) is None:
        raise ValueError("SOURCE_COMMIT_UNAVAILABLE")
    return value


def dirty_source_paths() -> list[str]:
    tracked = run(["git", "diff", "--name-only", "HEAD", "--", "."])
    untracked = run(["git", "ls-files", "--others", "--exclude-standard"])
    values = {
        line.strip() for line in (tracked.stdout + "\n" + untracked.stdout).splitlines()
        if line.strip() and not line.startswith("assurance/runtime/")
    }
    return sorted(values)


def bwrap_diagnostic() -> dict[str, object]:
    result = run(["python3", "scripts/onsure_bubblewrap_diagnostics.py"])
    try:
        body = json.loads(result.stdout)
    except json.JSONDecodeError:
        body = {
            "decision": "BLOCKED_ENVIRONMENT",
            "reason_code": "BWRAP_DIAGNOSTIC_INVALID",
            "probe_output": (result.stdout + result.stderr)[-2000:],
        }
    return body


def oci_diagnostic() -> dict[str, object]:
    image = os.environ.get(
        "ONSURE_VALIDATION_OCI_IMAGE", "onsure-validation-runtime:java17-node20-v1"
    )
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._/:@-]{0,254}", image) is None:
        return {
            "decision": "BLOCKED_ENVIRONMENT",
            "reason_code": "OCI_IMAGE_REFERENCE_INVALID",
            "image_reference": "REDACTED_INVALID",
            "image_pull": "NOT_RUN",
            "boundary_attack_execution": "NOT_RUN",
            "validation_probe_execution": "NOT_RUN",
        }
    with tempfile.TemporaryDirectory(prefix="onsure-docker-diagnostic-config-") as docker_config:
        docker_environment = {
            "PATH": "/usr/sbin:/usr/bin:/sbin:/bin",
            "DOCKER_HOST": "unix:///var/run/docker.sock",
            "DOCKER_CONFIG": docker_config,
        }
        docker_version = run(
            ["docker", "version", "--format", "{{.Server.Version}}"],
            environment=docker_environment,
        )
        inspected = run(
            ["docker", "image", "inspect", "--format", "{{.Id}}", "--", image],
            environment=docker_environment,
        )
        security = run(
            ["docker", "info", "--format", "{{json .SecurityOptions}}"],
            environment=docker_environment,
        )
    image_id = inspected.stdout.strip()
    if docker_version.returncode or inspected.returncode \
            or re.fullmatch(r"sha256:[0-9a-f]{64}", image_id) is None:
        return {
            "decision": "BLOCKED_ENVIRONMENT",
            "reason_code": "LOCAL_OCI_BACKEND_UNAVAILABLE",
            "image_reference": image,
            "image_id": image_id or "NOT_AVAILABLE",
            "image_pull": "NOT_RUN",
            "boundary_attack_execution": "NOT_RUN",
            "validation_probe_execution": "NOT_RUN",
        }
    environment = {
        "PATH": "/usr/sbin:/usr/bin:/sbin:/bin",
        "ONSURE_FIXTURE_SANDBOX_BACKEND": "OCI_DOCKER",
        "ONSURE_VALIDATION_OCI_IMAGE": image,
    }
    attacks = run(
        ["bash", "scripts/test-fixture-sandbox-boundary.sh"],
        environment=environment, timeout=60,
    )
    with tempfile.TemporaryDirectory(prefix="onsure-validation-oci-probe-") as temporary:
        probe_environment = dict(environment)
        probe_environment.update({
            "ONSURE_SANDBOX_PROBE": "1",
            "ONSURE_VALIDATION_SANDBOX_BACKEND": "OCI_DOCKER",
        })
        probe = run(
            ["bash", "scripts/validation-sandbox-launcher.sh", temporary, "15", "true"],
            environment=probe_environment, timeout=30,
        )
    attack_pass = attacks.returncode == 0 \
        and "ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12" in attacks.stdout
    probe_pass = probe.returncode == 0 \
        and f"ONSURE_VALIDATION_SANDBOX_BACKEND OCI_DOCKER {image_id}" in probe.stdout
    capabilities = run([
        "docker", "run", "--rm", "--pull", "never", "--network", "none",
        "--read-only", "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
        "--entrypoint", "bash", image_id, "-c",
        "set -euo pipefail; java -version 2>&1; node --version; npm --version; "
        "mvn --version | head -1; clamscan --version; "
        "fc-match --format='%{family}' -- 'Noto Sans CJK KR' | grep -F 'Noto Sans CJK KR'",
    ], environment={"PATH": "/usr/sbin:/usr/bin:/sbin:/bin",
                    "DOCKER_HOST": "unix:///var/run/docker.sock"}, timeout=60)
    capability_pass = capabilities.returncode == 0
    all_pass = attack_pass and probe_pass and capability_pass
    return {
        "decision": "PASS_NONFINAL" if all_pass else "BLOCKED_ENVIRONMENT",
        "reason_code": "OCI_SANDBOX_BOUNDARIES_AND_CAPABILITIES_VERIFIED" if all_pass
        else "OCI_SANDBOX_PROBE_FAILED",
        "docker_server_version": docker_version.stdout.strip(),
        "docker_security_options": json.loads(security.stdout) if security.returncode == 0 else [],
        "image_reference": image,
        "image_id": image_id,
        "image_pull": "NOT_RUN_LOCAL_IMAGE_ONLY",
        "boundary_attack_execution": "PASS_NONFINAL" if attack_pass else "FAIL",
        "boundary_attack_probe_count": 12 if attack_pass else 0,
        "boundary_attack_output_sha256": hashlib.sha256(
            attacks.stdout.encode("utf-8")
        ).hexdigest(),
        "validation_probe_execution": "PASS_NONFINAL" if probe_pass else "FAIL",
        "validation_probe_output_sha256": hashlib.sha256(
            probe.stdout.encode("utf-8")
        ).hexdigest(),
        "environment_capability_execution": "PASS_NONFINAL" if capability_pass else "FAIL",
        "environment_capability_count": 6 if capability_pass else 0,
        "environment_capabilities": [
            "JAVA", "MAVEN", "NODE", "NPM", "CLAMAV", "NOTO_SANS_CJK_KR",
        ] if capability_pass else [],
        "environment_capability_output_sha256": hashlib.sha256(
            (capabilities.stdout + capabilities.stderr).encode("utf-8")
        ).hexdigest(),
        "network": "NONE",
        "root_filesystem": "READ_ONLY",
        "capabilities": "DROP_ALL",
        "no_new_privileges": True,
        "docker_socket_mounted": False,
        "original_source_mounted": False,
    }


def build_report() -> dict[str, object]:
    dirty = dirty_source_paths()
    if dirty:
        raise ValueError("SOURCE_CODE_WORKTREE_DIRTY:" + ",".join(dirty[:20]))
    bwrap = bwrap_diagnostic()
    oci = oci_diagnostic()
    available = [
        backend for backend, body in (("ROOTLESS_BWRAP", bwrap), ("OCI_DOCKER", oci))
        if body.get("decision") == "PASS_NONFINAL"
    ]
    result: dict[str, object] = {
        "contract": "ONSURE_SANDBOX_BACKEND_DIAGNOSTIC_V1",
        "decision": "PASS_NONFINAL" if available else "BLOCKED_ENVIRONMENT",
        "source_commit": source_commit(),
        "source_code_dirty_paths": [],
        "selected_backend": available[0] if available else "NONE",
        "available_backends": available,
        "bubblewrap": bwrap,
        "oci": oci,
        "source_bindings": {
            relative: sha256(ROOT / relative) for relative in BOUND_FILES
        },
        "github_actions_used": False,
        "deployment_topology_changed": False,
        "production_authority": False,
        "final_claim_allowed": False,
    }
    result["receipt_sha256"] = canonical_sha256(result)
    return result


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)
    try:
        result = build_report()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print("ONSURE_SANDBOX_DIAGNOSTIC_FAIL " + str(error), file=os.sys.stderr)
        return 1
    output = args.output if args.output.is_absolute() else ROOT / args.output
    output = output.resolve()
    if ROOT not in output.parents or output.is_symlink():
        print("ONSURE_SANDBOX_DIAGNOSTIC_FAIL OUTPUT_PATH_INVALID", file=os.sys.stderr)
        return 1
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 3


if __name__ == "__main__":
    raise SystemExit(main())
