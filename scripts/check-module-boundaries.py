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
    result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        ROOT / value.decode("utf-8")
        for value in result.stdout.split(b"\0")
        if value and value.decode("utf-8").endswith(".java")
    )


def physical_core_source(relative: str) -> bool:
    if relative.startswith("modules/onsure-core/src/"):
        return True
    if not relative.startswith("src/main/java/"):
        return False
    return relative not in {
        "src/main/java/io/onsure/platform/ONSureCli.java",
        "src/main/java/io/onsure/platform/LocalAuthenticatedApiServer.java",
        "src/main/java/io/onsure/platform/OrudaTargetAdapter.java",
        "src/main/java/io/onsure/platform/ProductPlatformE2EMain.java",
    } and not relative.startswith("src/main/java/io/onsure/platform/oruda/")


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
        if "OrudaTargetAdapter" in text:
            violations.append(f"CORE_ORUDA_SYMBOL_REFERENCE:{relative}")
        if "io.onsure.platform.oruda" in text:
            violations.append(f"CORE_ORUDA_PACKAGE_REFERENCE:{relative}")
        if "fixtures/oruda" in text or "fixtures/e2e/oruda-target" in text:
            violations.append(f"CORE_ORUDA_FIXTURE_REFERENCE:{relative}")
        if "com.sun.net.httpserver" in text:
            violations.append(f"CORE_LOCAL_API_JDK_MODULE_REFERENCE:{relative}")

    aggregator = ET.parse(ROOT / "pom-modular.xml").getroot()
    modules = text_values(aggregator, "m:modules/m:module")
    expected_modules = {
        "modules/onsure-core",
        "modules/onsure-cli",
        "modules/onsure-local-api",
        "modules/onsure-test-fixtures",
        "modules/onsure-adapter-oruda",
    }
    if modules != expected_modules:
        violations.append(f"MODULAR_AGGREGATOR_MODULE_SET:{sorted(modules)}")

    core_pom = ET.parse(ROOT / "modules/onsure-core/pom.xml").getroot()
    core_excludes = text_values(core_pom, ".//m:excludes/m:exclude")
    required_core_excludes = {
        "io/onsure/platform/ONSureCli.java",
        "io/onsure/platform/LocalAuthenticatedApiServer.java",
        "io/onsure/platform/OrudaTargetAdapter.java",
        "io/onsure/platform/ProductPlatformE2EMain.java",
        "io/onsure/platform/oruda/**",
    }
    if not required_core_excludes.issubset(core_excludes):
        violations.append(f"CORE_POM_EXCLUDES_MISSING:{sorted(required_core_excludes - core_excludes)}")

    adapter_pom = ET.parse(ROOT / "modules/onsure-adapter-oruda/pom.xml").getroot()
    adapter_includes = text_values(adapter_pom, ".//m:includes/m:include")
    required_adapter_includes = {
        "io/onsure/platform/OrudaTargetAdapter.java",
        "io/onsure/platform/ProductPlatformE2EMain.java",
        "io/onsure/platform/oruda/**",
    }
    if not required_adapter_includes.issubset(adapter_includes):
        violations.append(f"ORUDA_POM_INCLUDES_MISSING:{sorted(required_adapter_includes - adapter_includes)}")

    cli_pom = ET.parse(ROOT / "modules/onsure-cli/pom.xml").getroot()
    cli_includes = text_values(cli_pom, ".//m:includes/m:include")
    if cli_includes != {"io/onsure/platform/ONSureCli.java"}:
        violations.append(f"CLI_POM_INCLUDE_SET:{sorted(cli_includes)}")

    api_pom = ET.parse(ROOT / "modules/onsure-local-api/pom.xml").getroot()
    api_includes = text_values(api_pom, ".//m:includes/m:include")
    if api_includes != {"io/onsure/platform/LocalAuthenticatedApiServer.java"}:
        violations.append(f"LOCAL_API_POM_INCLUDE_SET:{sorted(api_includes)}")

    for module in expected_modules:
        if not (ROOT / module / "pom.xml").is_file():
            violations.append(f"MODULE_POM_MISSING:{module}")

    result = {
        "contract": "ONSURE_MODULE_BOUNDARY_REPORT_V4",
        "decision": "PASS" if not violations else "FAIL",
        "violations": sorted(set(violations)),
        "declared_modules": sorted(modules),
        "inspected_core_source_count": len(inspected_core_files),
        "core_direct_optional_reference_count": sum(1 for value in violations if value.startswith("CORE_")),
        "physical_module_compile": "NOT_RUN",
        "oruda_module_removal_test": "NOT_RUN",
        "local_api_module_test": "NOT_RUN",
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
