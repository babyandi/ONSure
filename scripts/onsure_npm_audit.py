#!/usr/bin/env python3
"""Run npm audit for the VS Code extension and emit digest-bound evidence."""

from __future__ import annotations

import datetime
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
EXTENSION = ROOT / "vscode-extension"
LOCK = EXTENSION / "package-lock.json"
EVIDENCE = ROOT / "assurance/dependencies/onsure-npm-audit.v1.json"


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def run(command: list[str]) -> str:
    result = subprocess.run(command, cwd=EXTENSION, text=True, capture_output=True, check=False)
    if result.returncode:
        raise ValueError("NPM_AUDIT_COMMAND_FAILED:" + (result.stderr or result.stdout)[-2000:])
    return result.stdout.strip()


def generate(output: pathlib.Path = EVIDENCE) -> dict[str, object]:
    if not LOCK.is_file():
        raise ValueError("NPM_AUDIT_PACKAGE_LOCK_MISSING")
    raw = run(["npm", "audit", "--json", "--ignore-scripts"])
    body = json.loads(raw)
    source_counts = body.get("metadata", {}).get("vulnerabilities", {})
    counts = {
        severity: int(source_counts.get(severity, 0))
        for severity in ("info", "low", "moderate", "high", "critical", "total")
    }
    npm_version = run(["npm", "--version"])
    evidence = {
        "contract": "ONSURE_NPM_AUDIT_EVIDENCE_V1",
        "state": "COMPLETED",
        "package_lock": "vscode-extension/package-lock.json",
        "package_lock_sha256": sha256_bytes(LOCK.read_bytes()),
        "command": "npm audit --json --ignore-scripts",
        "npm_version": npm_version,
        "scanned_at": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "audit_result_sha256": sha256_bytes(
            (json.dumps(body, sort_keys=True, separators=(",", ":")) + "\n").encode()
        ),
        "vulnerabilities": counts,
        "release_authority": False,
        "final_claim_allowed": False,
    }
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=output.name + ".", dir=output.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(evidence, stream, indent=2, sort_keys=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    return evidence


def main() -> int:
    print(json.dumps(generate(), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print("ONSURE_NPM_AUDIT_FAIL " + str(error), file=__import__("sys").stderr)
        raise SystemExit(1)
