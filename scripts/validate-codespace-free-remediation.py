#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import py_compile
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
REQUIRED = [
    "contracts/codespace-free-remediation-plan.v1.json",
    "contracts/atomic-requirement.v1.schema.json",
    "contracts/module-boundary.v1.json",
    "contracts/ledger-hardening.v1.json",
    "contracts/sandbox-boundary.v1.json",
    "pom-modular.xml",
    "modules/onsure-core/pom.xml",
    "modules/onsure-cli/pom.xml",
    "modules/onsure-test-fixtures/pom.xml",
    "modules/onsure-adapter-oruda/pom.xml",
    "scripts/check-module-boundaries.py",
    "scripts/create-source-snapshot.py",
    "scripts/extract-atomic-requirements.py",
    "scripts/run-core-modular-twice.sh",
    "scripts/onsure-final-stage.sh",
]


def main() -> int:
    errors: list[str] = []
    for relative in REQUIRED:
        if not (ROOT / relative).is_file():
            errors.append(f"MISSING:{relative}")

    for relative in REQUIRED:
        path = ROOT / relative
        if not path.is_file():
            continue
        try:
            if path.suffix == ".json":
                json.loads(path.read_text(encoding="utf-8"))
            elif path.suffix == ".xml" or path.name == "pom.xml":
                ET.parse(path)
            elif path.suffix == ".py":
                py_compile.compile(str(path), doraise=True)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"INVALID:{relative}:{type(exc).__name__}:{exc}")

    for command, marker in [
        ([sys.executable, "scripts/check-module-boundaries.py"], "ONSURE_MODULE_BOUNDARY_STATIC_PASS"),
        ([sys.executable, "scripts/validate-repository-contracts.py"], "ONSURE_REPOSITORY_CONTRACTS_PASS"),
        (["bash", "scripts/check-shell-syntax.sh"], "ONSURE_SHELL_SYNTAX_PASS"),
    ]:
        result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
        if result.returncode != 0 or marker not in result.stdout:
            errors.append(
                f"COMMAND_FAIL:{' '.join(command)}:{result.returncode}:"
                f"{result.stdout[-500:]}:{result.stderr[-500:]}"
            )

    plan = json.loads((ROOT / "contracts/codespace-free-remediation-plan.v1.json").read_text(encoding="utf-8"))
    if plan.get("final_single_command") != "bash scripts/onsure-final-stage.sh --profile core":
        errors.append("FINAL_SINGLE_COMMAND_MISMATCH")
    if plan.get("assurance_ceiling") != "SELF_VALIDATION_NONFINAL":
        errors.append("ASSURANCE_CEILING_UNSAFE")
    if any(plan.get(field) is not False for field in ("final_lock_allowed", "production_go", "commercial_go")):
        errors.append("UNSAFE_GO_FLAG")

    report = {
        "contract": "ONSURE_CODESPACE_FREE_STATIC_GATE_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "runtime_execution": "NOT_RUN",
        "modular_compile": "NOT_RUN",
        "independent_otester": "NOT_RUN",
        "independent_oaudit": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_CODESPACE_FREE_STATIC_GATE_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_CODESPACE_FREE_STATIC_GATE_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
