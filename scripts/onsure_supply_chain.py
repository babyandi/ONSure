#!/usr/bin/env python3
"""Generate and validate a reproducible CycloneDX SBOM and license inventory."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import subprocess
import sys
from collections import Counter
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
PLUGIN_VERSION = "2.9.1"
SCHEMA_VERSION = "1.6"
RAW_SBOM = ROOT / "target/bom.json"
DEFAULT_SBOM = ROOT / "assurance/dependencies/onsure.cdx.json"
DEFAULT_INVENTORY = ROOT / "assurance/dependencies/onsure-dependency-license-inventory.v1.json"


def run_cyclonedx() -> dict[str, object]:
    command = [
        "mvn",
        "-B",
        "-ntp",
        "-q",
        f"org.cyclonedx:cyclonedx-maven-plugin:{PLUGIN_VERSION}:makeAggregateBom",
        "-Dcyclonedx.outputFormat=json",
        "-Dcyclonedx.skipAttach=true",
    ]
    process = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    if process.returncode != 0:
        raise ValueError("CYCLONEDX_MAVEN_FAILED:" + process.stderr[-2000:])
    if not RAW_SBOM.is_file():
        raise ValueError("CYCLONEDX_SBOM_MISSING")
    return json.loads(RAW_SBOM.read_text(encoding="utf-8"))


def normalize_sbom(body: dict[str, object]) -> dict[str, object]:
    normalized = json.loads(json.dumps(body))
    normalized.pop("serialNumber", None)
    metadata = normalized.get("metadata", {})
    if isinstance(metadata, dict):
        metadata.pop("timestamp", None)
    components = normalized.get("components", [])
    if isinstance(components, list):
        components.sort(key=lambda item: str(item.get("purl", item.get("bom-ref", ""))))
        for component in components:
            licenses = component.get("licenses", [])
            if isinstance(licenses, list):
                licenses.sort(key=lambda item: json.dumps(item, sort_keys=True))
            hashes = component.get("hashes", [])
            if isinstance(hashes, list):
                hashes.sort(key=lambda item: str(item.get("alg", "")))
    dependencies = normalized.get("dependencies", [])
    if isinstance(dependencies, list):
        dependencies.sort(key=lambda item: str(item.get("ref", "")))
        for dependency in dependencies:
            values = dependency.get("dependsOn")
            if isinstance(values, list):
                values.sort()
    return normalized


def license_ids(component: dict[str, object]) -> list[str]:
    values: list[str] = []
    for wrapper in component.get("licenses", []):
        license_body = wrapper.get("license", {})
        value = license_body.get("id") or license_body.get("name")
        if value:
            values.append(str(value))
    return sorted(set(values))


def build_inventory(sbom: dict[str, object]) -> dict[str, object]:
    dependencies: list[dict[str, object]] = []
    license_counts: Counter[str] = Counter()
    review_required = 0
    for component in sbom.get("components", []):
        licenses = license_ids(component)
        status = "DECLARED" if licenses else "REVIEW_REQUIRED"
        if not licenses:
            review_required += 1
        for value in licenses or ["UNDECLARED"]:
            license_counts[value] += 1
        dependencies.append({
            "group": component.get("group", ""),
            "name": component.get("name", ""),
            "version": component.get("version", ""),
            "scope": component.get("scope", ""),
            "purl": component.get("purl", ""),
            "licenses": licenses,
            "license_status": status,
        })
    dependencies.sort(key=lambda item: str(item["purl"]))
    raw = json.dumps(sbom, sort_keys=True, separators=(",", ":")).encode("utf-8")
    root_license_present = any(
        (ROOT / name).is_file()
        for name in ("LICENSE", "LICENSE.md", "NOTICE", "COPYING")
    )
    return {
        "contract": "ONSURE_DEPENDENCY_LICENSE_INVENTORY_V1",
        "decision": "REVIEW_REQUIRED" if review_required or not root_license_present else "PASS_NONFINAL",
        "source_sbom": DEFAULT_SBOM.relative_to(ROOT).as_posix(),
        "source_sbom_sha256": hashlib.sha256(raw).hexdigest(),
        "cyclonedx_plugin": f"org.cyclonedx:cyclonedx-maven-plugin:{PLUGIN_VERSION}",
        "cyclonedx_schema": SCHEMA_VERSION,
        "component_count": len(dependencies),
        "dependency_license_counts": dict(sorted(license_counts.items())),
        "dependency_license_review_required_count": review_required,
        "root_source_license": "UNDECLARED" if not root_license_present else "PRESENT_REVIEW_REQUIRED",
        "dependencies": dependencies,
        "limitations": [
            "DECLARED_DEPENDENCY_LICENSE_IS_NOT_LEGAL_APPROVAL",
            "TRANSITIVE_NOTICE_AND_ATTRIBUTION_REQUIREMENTS_REQUIRE_HUMAN_REVIEW",
            "SOURCE_FILE_COPYRIGHT_AND_INBOUND_RIGHTS_REQUIRE_HUMAN_ATTESTATION",
        ],
        "final_claim_allowed": False,
    }


def write_json(path: pathlib.Path, body: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(body, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def generate() -> tuple[dict[str, object], dict[str, object]]:
    sbom = normalize_sbom(run_cyclonedx())
    if sbom.get("specVersion") != SCHEMA_VERSION:
        raise ValueError(f"CYCLONEDX_SCHEMA_DRIFT:{sbom.get('specVersion')}")
    inventory = build_inventory(sbom)
    return sbom, inventory


def validate() -> dict[str, object]:
    if not DEFAULT_SBOM.is_file() or not DEFAULT_INVENTORY.is_file():
        raise ValueError("SUPPLY_CHAIN_BASELINE_MISSING")
    actual_sbom, actual_inventory = generate()
    expected_sbom = json.loads(DEFAULT_SBOM.read_text(encoding="utf-8"))
    expected_inventory = json.loads(DEFAULT_INVENTORY.read_text(encoding="utf-8"))
    violations: list[str] = []
    if actual_sbom != expected_sbom:
        violations.append("CYCLONEDX_SBOM_DRIFT")
    if actual_inventory != expected_inventory:
        violations.append("DEPENDENCY_LICENSE_INVENTORY_DRIFT")
    return {
        "contract": "ONSURE_SUPPLY_CHAIN_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "component_count": actual_inventory["component_count"],
        "dependency_license_review_required_count": actual_inventory[
            "dependency_license_review_required_count"
        ],
        "root_source_license": actual_inventory["root_source_license"],
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("generate", "validate"))
    args = parser.parse_args(argv)
    if args.mode == "generate":
        sbom, inventory = generate()
        write_json(DEFAULT_SBOM, sbom)
        write_json(DEFAULT_INVENTORY, inventory)
        print(
            "ONSURE_SUPPLY_CHAIN_GENERATED "
            f"components={inventory['component_count']} schema={sbom['specVersion']}"
        )
        return 0
    result = validate()
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ONSURE_SUPPLY_CHAIN_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
