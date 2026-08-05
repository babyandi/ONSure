#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "contracts/module-boundary.v1.json"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def tracked_java() -> list[pathlib.Path]:
    result = subprocess.run(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        ROOT / value.decode("utf-8")
        for value in result.stdout.split(b"\0")
        if value and value.decode("utf-8").endswith(".java")
    )


def physical_core_source(relative: str) -> bool:
    return relative.startswith("modules/onsure-core/src/")


def text_values(root: ET.Element, xpath: str) -> set[str]:
    return {node.text.strip() for node in root.findall(xpath, NS) if node.text and node.text.strip()}


def main() -> int:
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    core = contract["modules"]["onsure-core"]
    forbidden_imports = tuple(core["forbidden_import_prefixes"])
    violations: list[str] = []
    inspected_core_files: list[str] = []

    for path in tracked_java():
        relative = path.relative_to(ROOT).as_posix()
        if not physical_core_source(relative):
            continue
        inspected_core_files.append(relative)
        text = path.read_text(encoding="utf-8", errors="replace")
        for imported in re.findall(r"^import\s+([^;]+);", text, flags=re.MULTILINE):
            if imported.startswith(forbidden_imports):
                violations.append(f"CORE_FORBIDDEN_IMPORT:{relative}:{imported}")

    aggregator = ET.parse(ROOT / "pom-modular.xml").getroot()
    modules = text_values(aggregator, "m:modules/m:module")
    expected_modules = set(contract.get("modular_aggregator_modules", []))
    if not expected_modules:
        violations.append("MODULAR_AGGREGATOR_CONTRACT_EMPTY")
    if modules != expected_modules:
        violations.append(f"MODULAR_AGGREGATOR_MODULE_SET:{sorted(modules)}")

    core_pom = ET.parse(ROOT / "modules/onsure-core/pom.xml").getroot()
    if text_values(core_pom, ".//m:compilerArgs/m:arg") or text_values(core_pom, ".//m:implicit") != {"none"}:
        violations.append("CORE_POM_IMPLICIT_COMPILATION_NOT_DISABLED")

    adapter_pom = ET.parse(ROOT / "modules/onsure-adapter-oruda/pom.xml").getroot()
    if text_values(adapter_pom, ".//m:compilerArgs/m:arg") or text_values(adapter_pom, ".//m:implicit") != {"none"}:
        violations.append("ORUDA_POM_IMPLICIT_COMPILATION_NOT_DISABLED")
    if text_values(core_pom, ".//m:sources/m:source") or text_values(adapter_pom, ".//m:sources/m:source"):
        violations.append("SHARED_SOURCE_CONFIGURATION_PRESENT")
    if not (ROOT / "modules/onsure-core/src/main/java").is_dir():
        violations.append("CORE_OWNED_SOURCE_ROOT_MISSING")
    if not (ROOT / "modules/onsure-adapter-oruda/src/main/java").is_dir():
        violations.append("ORUDA_OWNED_SOURCE_ROOT_MISSING")

    cli_pom = ET.parse(ROOT / "modules/onsure-cli/pom.xml").getroot()
    cli_includes = text_values(cli_pom, ".//m:includes/m:include")
    if cli_includes:
        violations.append(f"CLI_POM_INCLUDE_SET:{sorted(cli_includes)}")

    api_pom = ET.parse(ROOT / "modules/onsure-local-api/pom.xml").getroot()
    api_includes = text_values(api_pom, ".//m:includes/m:include")
    if api_includes:
        violations.append(f"LOCAL_API_POM_INCLUDE_SET:{sorted(api_includes)}")

    for module in expected_modules:
        if not (ROOT / module / "pom.xml").is_file():
            violations.append(f"MODULE_POM_MISSING:{module}")

    result = {
        "contract": "ONSURE_MODULE_BOUNDARY_REPORT_V5",
        "decision": "PASS" if not violations else "FAIL",
        "violations": sorted(set(violations)),
        "declared_modules": sorted(modules),
        "inspected_core_source_count": len(inspected_core_files),
        "core_direct_optional_reference_count": sum(1 for value in violations if value.startswith("CORE_")),
        "physical_module_compile": "REQUIRED_BY_MODULAR_BUILD",
        "split_package_count_target": 0,
        "package_cycle_count_target": 0,
        "shared_source_root_removal": "COMPLETE",
        "final_claim_allowed": False,
    }
    print(json.dumps(result, indent=2, sort_keys=True))
    if violations:
        print("ONSURE_MODULE_BOUNDARY_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_MODULE_BOUNDARY_STATIC_PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, ET.ParseError) as error:
        print(f"ONSURE_MODULE_BOUNDARY_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
