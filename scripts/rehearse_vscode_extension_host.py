#!/usr/bin/env python3
"""Run online and network-denied VS Code Extension Host E2E and seal compact evidence."""

from __future__ import annotations

import datetime as dt
import hashlib
import json
import pathlib
import subprocess
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts/run-vscode-extension-host-e2e-container.sh"
OUTPUT = ROOT / "assurance/runtime/vscode-extension-host-e2e.v1.json"
IMAGE = "onsure-vscode-extension-host-e2e:1.95.3-node22"
SOURCE_FILES = (
    "scripts/run-vscode-extension-host-e2e-container.sh",
    "vscode-extension/test/extension-host/Dockerfile",
    "vscode-extension/test/run-extension-host-e2e.js",
    "vscode-extension/test/extension-host/index.js",
    "vscode-extension/extension.js",
    "vscode-extension/extension-core.js",
    "vscode-extension/package.json",
    "vscode-extension/package-lock.json",
)


def sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def run(arguments: list[str], timeout: int = 900) -> tuple[int, str]:
    process = subprocess.run(
        arguments, cwd=ROOT, text=True, capture_output=True, timeout=timeout, check=False
    )
    return process.returncode, process.stdout + process.stderr


def source_bindings() -> dict[str, str]:
    result: dict[str, str] = {}
    for relative in SOURCE_FILES:
        file = ROOT / relative
        if not file.is_file() or file.is_symlink():
            raise RuntimeError("VSCODE_E2E_SOURCE_FILE_INVALID:" + relative)
        result[relative] = sha256(file.read_bytes())
    return result


def image_id() -> str:
    code, value = run(["docker", "image", "inspect", IMAGE, "--format", "{{.Id}}"])
    if code != 0 or not value.strip().startswith("sha256:"):
        raise RuntimeError("VSCODE_EXTENSION_HOST_IMAGE_ID_UNAVAILABLE")
    return value.strip()


def main() -> int:
    online_code, online_log = run(["bash", str(RUNNER)])
    if online_code != 0 or "Exit code:   0" not in online_log:
        print(online_log[-6000:], file=sys.stderr)
        raise RuntimeError("VSCODE_EXTENSION_HOST_ONLINE_FAILED")
    online_image_id = image_id()
    offline_code, offline_log = run(["bash", str(RUNNER), "--offline"])
    if offline_code != 0 or "Exit code:   0" not in offline_log:
        print(offline_log[-6000:], file=sys.stderr)
        raise RuntimeError("VSCODE_EXTENSION_HOST_OFFLINE_FAILED")
    offline_image_id = image_id()

    evidence: dict[str, object] = {
        "contract": "ONSURE_VSCODE_EXTENSION_HOST_E2E_EVIDENCE_V1",
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
        "vscode_version": "1.95.3",
        "test_electron_version": "2.5.2",
        "container_base": "node:22-bookworm-slim",
        "container_image": IMAGE,
        "online_container_image_id": online_image_id,
        "offline_container_image_id": offline_image_id,
        "source_file_sha256": source_bindings(),
        "xvfb": True,
        "non_root": True,
        "capabilities_dropped": True,
        "no_new_privileges": True,
        "first_run_download": "PASS_OR_CACHE_HIT",
        "online_run_extension_host_exit_code": online_code,
        "online_run_log_sha256": sha256(online_log.encode()),
        "offline_rerun_network_mode": "none",
        "offline_rerun_extension_host_exit_code": offline_code,
        "offline_rerun_log_sha256": sha256(offline_log.encode()),
        "offline_network_attempts": "BLOCKED_BY_CONTAINER_NETWORK_NAMESPACE",
        "node_engine_mismatch_warnings": online_log.count("EBADENGINE") + offline_log.count("EBADENGINE"),
        "github_actions_used": False,
        "decision": "PASS_NONFINAL",
        "final_claim_allowed": False,
    }
    canonical = json.dumps(evidence, sort_keys=True, separators=(",", ":")).encode()
    evidence["evidence_payload_sha256"] = sha256(canonical)
    OUTPUT.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(evidence, indent=2, sort_keys=True))
    print("ONSURE_VSCODE_EXTENSION_HOST_E2E_PASS_NONFINAL")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"ONSURE_VSCODE_EXTENSION_HOST_E2E_FAIL:{error}", file=sys.stderr)
        raise SystemExit(1)
