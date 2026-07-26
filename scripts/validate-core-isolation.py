#!/usr/bin/env python3
"""Fail closed when ONSure Core regains ORUDA compile/test/fixture dependencies."""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONTRACT_PATH = ROOT / "contracts/core-module-isolation.v1.json"
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def tracked_files() -> list[pathlib.Path]:
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=False
    )
    if result.returncode:
        raise RuntimeError("GIT_LS_FILES_FAILED")
    return sorted(
        (ROOT / raw.decode("utf-8")).resolve()
        for raw in result.stdout.split(b"\0")
        if raw
    )


def relative(path: pathlib.Path) -> str:
    return path.relative_to(ROOT).as_posix()


def parse_pom(path: pathlib.Path) -> ET.Element:
    return ET.parse(path).getroot()


def text(root: ET.Element, xpath: str) -> str:
    node = root.find(xpath, POM_NS)
    return "" if node is None or node.text is None else node.text.strip()


def list_text(root: ET.Element, xpath: str) -> list[str]:
    return [node.text.strip() for node in root.findall(xpath, POM_NS) if node.text]


def validate_modules(contract: dict[str, Any], errors: list[str]) -> None:
    parent = parse_pom(ROOT / "pom.xml")
    if text(parent, "m:artifactId") != contract["parent_module"]:
        errors.append("PARENT_ARTIFACT_ID_MISMATCH")
    declared = set(list_text(parent, "m:modules/m:module"))
    required = set(contract["required_modules"])
    if declared != required:
        errors.append(f"MODULE_SET_MISMATCH:{sorted(declared)}:{sorted(required)}")
    for module in required:
        if not (ROOT / module / "pom.xml").is_file():
            errors.append(f"MODULE_POM_MISSING:{module}")

    core = parse_pom(ROOT / "modules/onsure-core/pom.xml")
    if text(core, "m:artifactId") != contract["core_module"]["artifact_id"]:
        errors.append("CORE_ARTIFACT_ID_MISMATCH")
    core_dependencies = set(
        list_text(core, "m:dependencies/m:dependency/m:artifactId")
    )
    if contract["optional_oruda_module"]["artifact_id"] in core_dependencies:
        errors.append("CORE_DEPENDS_ON_ORUDA_ARTIFACT")

    oruda = parse_pom(ROOT / "modules/onsure-adapter-oruda/pom.xml")
    if text(oruda, "m:artifactId") != contract["optional_oruda_module"]["artifact_id"]:
        errors.append("ORUDA_ARTIFACT_ID_MISMATCH")
    oruda_dependencies = set(
        list_text(oruda, "m:dependencies/m:dependency/m:artifactId")
    )
    for required_dependency in contract["optional_oruda_module"]["depends_on"]:
        if required_dependency not in oruda_dependencies:
            errors.append(f"ORUDA_REQUIRED_DEPENDENCY_MISSING:{required_dependency}")


def is_adapter_main(relative_path: str) -> bool:
    return (
        relative_path == "src/main/java/io/onsure/platform/OrudaTargetAdapter.java"
        or relative_path == "src/main/java/io/onsure/platform/ProductPlatformE2EMain.java"
        or relative_path.startswith("src/main/java/io/onsure/platform/oruda/")
    )


def is_adapter_test(relative_path: str) -> bool:
    return relative_path.startswith("src/test/java/io/onsure/platform/oruda/")


def validate_source_boundary(contract: dict[str, Any], errors: list[str]) -> None:
    forbidden_imports = tuple(contract["core_module"]["forbidden_import_prefixes"])
    for path in tracked_files():
        rel = relative(path)
        if not rel.endswith(".java"):
            continue
        if rel.startswith("src/main/java/") and is_adapter_main(rel):
            continue
        if rel.startswith("src/test/java/") and is_adapter_test(rel):
            continue
        if not rel.startswith(("src/main/java/", "src/test/java/")):
            continue
        content = path.read_text(encoding="utf-8", errors="strict")
        for prefix in forbidden_imports:
            if re.search(rf"^import\s+{re.escape(prefix)}(?:\.|;)", content, re.MULTILINE):
                errors.append(f"CORE_FORBIDDEN_IMPORT:{rel}:{prefix}")
        if "OrudaTargetAdapter" in content or "fixtures/oruda" in content or "oruda-target" in content:
            errors.append(f"CORE_ORUDA_SYMBOL_OR_FIXTURE_REFERENCE:{rel}")

    store = (ROOT / "src/main/java/io/onsure/platform/FileValidationStore.java").read_text(
        encoding="utf-8"
    )
    if "OrudaEvidenceRegistry" in store or "OrudaTargetAdapter" in store:
        errors.append("CORE_STORE_CONTAINS_ORUDA_REFERENCE")
    engine = (ROOT / "src/main/java/io/onsure/platform/ValidationEngine.java").read_text(
        encoding="utf-8"
    )
    if "withOrudaAdapter" in engine or "new OrudaTargetAdapter" in engine:
        errors.append("CORE_ENGINE_CONTAINS_ORUDA_FACTORY")


def validate_runners(errors: list[str]) -> None:
    core_runner = ROOT / "scripts/run-core-validator-fixture-e2e.sh"
    if not core_runner.is_file():
        errors.append("CORE_FIXTURE_RUNNER_MISSING")
        return
    content = core_runner.read_text(encoding="utf-8")
    required = [
        "modules/onsure-core",
        "CoreValidatorFixtureE2EMain",
        "ONSURE_CORE_FIXTURE_TWO_RUN_PASS_NONFINAL",
    ]
    for token in required:
        if token not in content:
            errors.append(f"CORE_FIXTURE_RUNNER_TOKEN_MISSING:{token}")
    for forbidden in ("onsure-adapter-oruda", "fixtures/oruda", "oruda-target"):
        if forbidden in content:
            errors.append(f"CORE_FIXTURE_RUNNER_ORUDA_REFERENCE:{forbidden}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()

    errors: list[str] = []
    try:
        contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
        if contract.get("contract") != "ONSURE_CORE_MODULE_ISOLATION_V1":
            errors.append("CORE_ISOLATION_CONTRACT_INVALID")
        validate_modules(contract, errors)
        validate_source_boundary(contract, errors)
        validate_runners(errors)
    except Exception as exc:  # noqa: BLE001
        errors.append(f"CORE_ISOLATION_VALIDATOR_ERROR:{type(exc).__name__}:{exc}")

    report = {
        "contract": "ONSURE_CORE_MODULE_ISOLATION_REPORT_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "compile_runtime_state": "NOT_RUN",
        "final_claim_allowed": False,
    }
    serialized = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        output = args.output.resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(serialized, encoding="utf-8")
    print(serialized, end="")
    if errors:
        print("ONSURE_CORE_ISOLATION_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_CORE_ISOLATION_PASS_STATIC")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
