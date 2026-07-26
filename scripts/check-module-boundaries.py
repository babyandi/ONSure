#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "contracts/module-boundary.v1.json"


def tracked_java() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", "*.java", "**/*.java"],
        cwd=ROOT,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(ROOT / value.decode("utf-8") for value in result.stdout.split(b"\0") if value)


def main() -> int:
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    core = contract["modules"]["onsure-core"]
    forbidden_imports = tuple(core["forbidden_import_prefixes"])
    violations: list[str] = []

    for path in tracked_java():
        relative = path.relative_to(ROOT).as_posix()
        if relative.startswith("src/main/java/io/onsure/platform/oruda/"):
            continue
        if relative == "src/main/java/io/onsure/platform/OrudaTargetAdapter.java":
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for imported in re.findall(r"^import\s+([^;]+);", text, flags=re.MULTILINE):
            if imported.startswith(forbidden_imports):
                violations.append(f"CORE_FORBIDDEN_IMPORT:{relative}:{imported}")
        if "OrudaTargetAdapter" in text and relative not in {
            "src/main/java/io/onsure/platform/OrudaTargetAdapter.java",
            "src/main/java/io/onsure/platform/ProductPlatformE2EMain.java",
        }:
            violations.append(f"CORE_ORUDA_SYMBOL_REFERENCE:{relative}")

    result = {
        "contract": "ONSURE_MODULE_BOUNDARY_REPORT_V1",
        "decision": "PASS" if not violations else "FAIL",
        "violations": sorted(set(violations)),
        "physical_module_compile": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    if violations:
        print("ONSURE_MODULE_BOUNDARY_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_MODULE_BOUNDARY_STATIC_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
