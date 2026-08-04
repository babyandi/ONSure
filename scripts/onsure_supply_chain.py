#!/usr/bin/env python3
"""Generate and validate a reproducible CycloneDX SBOM and license inventory."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import base64
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
DEFAULT_VULNERABILITY = ROOT / "assurance/dependencies/onsure-vulnerability-scan.v1.json"
DEFAULT_NPM_AUDIT = ROOT / "assurance/dependencies/onsure-npm-audit.v1.json"
DEFAULT_VSCODE_INVENTORY = ROOT / "assurance/dependencies/onsure-vscode-dependency-inventory.v1.json"
POLICY_PATH = ROOT / "contracts/onsure-supply-chain-policy.v1.json"
RIGHTS_PATH = ROOT / "contracts/onsure-rights-declaration.v1.json"
ROOT_LICENSE_ID = "LicenseRef-ORUDA-Labs-Proprietary"
ROOT_COPYRIGHT_HOLDER = "ORUDA Labs"


def build_modular_artifacts() -> None:
    """Rebuild module JARs before hashing so stale target output cannot validate an SBOM."""
    command = [
        "mvn", "-B", "-ntp", "-q", "-f", "pom-modular.xml",
        "clean", "package", "-Dmaven.test.skip=true",
    ]
    process = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, check=False)
    if process.returncode != 0:
        raise ValueError("MODULAR_SBOM_BUILD_FAILED:" + process.stderr[-2000:])


def run_cyclonedx(pom: str = "pom.xml") -> dict[str, object]:
    command = [
        "mvn",
        "-B",
        "-ntp",
        "-q",
        "-f",
        pom,
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


def vscode_inventory() -> dict[str, object]:
    lock_path = ROOT / "vscode-extension/package-lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    packages: list[dict[str, object]] = []
    for location, value in sorted(lock.get("packages", {}).items()):
        if not location or not location.startswith("node_modules/"):
            continue
        name = location.removeprefix("node_modules/")
        version = str(value.get("version", ""))
        if not version:
            raise ValueError("VSCODE_DEPENDENCY_VERSION_MISSING:" + name)
        integrity = str(value.get("integrity", ""))
        integrity_algorithm = "NOT_AVAILABLE"
        integrity_hex = "NOT_AVAILABLE"
        if "-" in integrity:
            algorithm, encoded = integrity.split("-", 1)
            try:
                integrity_hex = base64.b64decode(encoded, validate=True).hex()
                integrity_algorithm = algorithm.upper()
            except ValueError:
                raise ValueError("VSCODE_DEPENDENCY_INTEGRITY_INVALID:" + name)
        packages.append({
            "name": name,
            "version": version,
            "purl": f"pkg:npm/{name.replace('@', '%40')}@{version}",
            "license": value.get("license", "NOT_DECLARED_IN_LOCK"),
            "integrity_algorithm": integrity_algorithm,
            "integrity_hex": integrity_hex,
            "development_only": bool(value.get("dev", False)),
            "optional": bool(value.get("optional", False)),
        })
    return {
        "contract": "ONSURE_VSCODE_DEPENDENCY_INVENTORY_V1",
        "package_lock": "vscode-extension/package-lock.json",
        "package_lock_sha256": file_sha256(lock_path),
        "component_count": len(packages),
        "components": packages,
        "artifact_sha256_available": False,
        "lock_integrity_recorded": True,
        "final_claim_allowed": False,
    }


def merged_cyclonedx(vscode: dict[str, object]) -> dict[str, object]:
    build_modular_artifacts()
    root_sbom = run_cyclonedx("pom.xml")
    modular_sbom = run_cyclonedx("pom-modular.xml")
    merged = json.loads(json.dumps(root_sbom))
    by_purl = {str(item.get("purl")): item for item in merged.get("components", [])}
    for component in modular_sbom.get("components", []):
        value = json.loads(json.dumps(component))
        purl = str(value.get("purl", ""))
        if str(value.get("group")) == "io.onsure":
            artifact = str(value.get("name"))
            jar = ROOT / "modules" / artifact / "target" / f"{artifact}-0.1.0-SNAPSHOT.jar"
            if not jar.is_file():
                raise ValueError("MODULAR_SBOM_ARTIFACT_NOT_BUILT:" + artifact)
            value["hashes"] = [{"alg": "SHA-256", "content": file_sha256(jar)}]
            value["properties"] = [{"name": "onsure:module_boundary", "value": "ISOLATED_CANDIDATE"}]
        by_purl[purl] = value
    merged["components"] = list(by_purl.values())
    metadata = merged.setdefault("metadata", {})
    metadata["properties"] = sorted([
        {"name": "onsure:modular_pom", "value": "pom-modular.xml"},
        {"name": "onsure:vscode_dependency_inventory", "value": DEFAULT_VSCODE_INVENTORY.relative_to(ROOT).as_posix()},
        {"name": "onsure:vscode_dependency_inventory_sha256", "value": hashlib.sha256(
            (json.dumps(vscode, sort_keys=True, separators=(",", ":")) + "\n").encode()).hexdigest()},
    ], key=lambda item: item["name"])
    dependencies = {str(item.get("ref")): item for item in merged.get("dependencies", [])}
    for dependency in modular_sbom.get("dependencies", []):
        dependencies[str(dependency.get("ref"))] = json.loads(json.dumps(dependency))
    merged["dependencies"] = list(dependencies.values())
    return merged


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


def root_license_status() -> str:
    required = (ROOT / "LICENSE", ROOT / "NOTICE", ROOT / "THIRD_PARTY_NOTICES.md", RIGHTS_PATH)
    if any(not path.is_file() for path in required):
        return "UNDECLARED"
    declaration = json.loads(RIGHTS_PATH.read_text(encoding="utf-8"))
    license_text = (ROOT / "LICENSE").read_text(encoding="utf-8")
    notice_text = (ROOT / "NOTICE").read_text(encoding="utf-8")
    if declaration.get("contract") != "ONSURE_RIGHTS_DECLARATION_V1" \
            or declaration.get("copyright_holder") != ROOT_COPYRIGHT_HOLDER \
            or declaration.get("outbound_license") != ROOT_LICENSE_ID \
            or declaration.get("other_developers_companies_or_contractors") != "NONE_ATTESTED" \
            or declaration.get("copied_external_repository_source") != "NONE_ATTESTED" \
            or declaration.get("copied_external_assets") != "NONE_ATTESTED" \
            or declaration.get("production_release_authority") is not False \
            or ROOT_LICENSE_ID not in license_text \
            or ROOT_COPYRIGHT_HOLDER not in license_text \
            or ROOT_COPYRIGHT_HOLDER not in notice_text:
        return "DECLARATION_INVALID"
    extension_license = ROOT / "vscode-extension/LICENSE"
    if not extension_license.is_file() or extension_license.read_bytes() != (ROOT / "LICENSE").read_bytes():
        return "DISTRIBUTION_LICENSE_DRIFT"
    return ROOT_LICENSE_ID


def build_inventory(sbom: dict[str, object]) -> dict[str, object]:
    dependencies: list[dict[str, object]] = []
    license_counts: Counter[str] = Counter()
    review_required = 0
    for component in sbom.get("components", []):
        licenses = license_ids(component)
        project_module = component.get("group") == "io.onsure"
        status = "PROJECT_SOURCE_LICENSE_INHERITS_ROOT" if project_module else (
            "DECLARED" if licenses else "REVIEW_REQUIRED")
        if not licenses and not project_module:
            review_required += 1
        for value in licenses or (["PROJECT_SOURCE_LICENSE_INHERITS_ROOT"] if project_module else ["UNDECLARED"]):
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
    root_license = root_license_status()
    return {
        "contract": "ONSURE_DEPENDENCY_LICENSE_INVENTORY_V1",
        "decision": "REVIEW_REQUIRED" if review_required or root_license != ROOT_LICENSE_ID else "PASS_NONFINAL",
        "source_sbom": DEFAULT_SBOM.relative_to(ROOT).as_posix(),
        "source_sbom_sha256": hashlib.sha256(raw).hexdigest(),
        "cyclonedx_plugin": f"org.cyclonedx:cyclonedx-maven-plugin:{PLUGIN_VERSION}",
        "cyclonedx_schema": SCHEMA_VERSION,
        "component_count": len(dependencies),
        "dependency_license_counts": dict(sorted(license_counts.items())),
        "dependency_license_review_required_count": review_required,
        "root_source_license": root_license,
        "copyright_holder": ROOT_COPYRIGHT_HOLDER if root_license == ROOT_LICENSE_ID else "UNDETERMINED",
        "rights_declaration": RIGHTS_PATH.relative_to(ROOT).as_posix(),
        "dependencies": dependencies,
        "limitations": [
            "DECLARED_DEPENDENCY_LICENSE_IS_NOT_LEGAL_APPROVAL",
            "TRANSITIVE_NOTICE_AND_ATTRIBUTION_REQUIREMENTS_REQUIRE_HUMAN_REVIEW",
            "INBOUND_RIGHTS_ARE_OWNER_ATTESTED_NOT_INDEPENDENTLY_VERIFIED",
        ],
        "final_claim_allowed": False,
    }


def write_json(path: pathlib.Path, body: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(body, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def generate() -> tuple[dict[str, object], dict[str, object], dict[str, object]]:
    vscode = vscode_inventory()
    sbom = normalize_sbom(merged_cyclonedx(vscode))
    if sbom.get("specVersion") != SCHEMA_VERSION:
        raise ValueError(f"CYCLONEDX_SCHEMA_DRIFT:{sbom.get('specVersion')}")
    inventory = build_inventory(sbom)
    return sbom, inventory, vscode


def file_sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_policy(
    sbom: dict[str, object], inventory: dict[str, object],
    policy: dict[str, object], vulnerability: dict[str, object],
    npm_audit: dict[str, object] | None = None,
) -> tuple[list[str], list[str]]:
    violations: list[str] = []
    blockers: list[str] = []
    if policy.get("contract") != "ONSURE_SUPPLY_CHAIN_POLICY_V1" \
            or policy.get("cyclonedx_schema") != SCHEMA_VERSION \
            or policy.get("final_claim_allowed") is not False:
        violations.append("SUPPLY_CHAIN_POLICY_INVALID")
    if policy.get("root_source_license") != ROOT_LICENSE_ID \
            or policy.get("copyright_holder") != ROOT_COPYRIGHT_HOLDER \
            or policy.get("rights_declaration_required") != RIGHTS_PATH.relative_to(ROOT).as_posix():
        violations.append("SUPPLY_CHAIN_ROOT_RIGHTS_POLICY_INVALID")
    components = sbom.get("components", [])
    purls = [str(component.get("purl", "")) for component in components]
    if any(not value for value in purls) or len(set(purls)) != len(purls):
        violations.append("SBOM_COMPONENT_PURL_NOT_UNIQUE")
    for component in components:
        hashes = component.get("hashes", [])
        if not any(
            value.get("alg") == "SHA-256"
            and len(str(value.get("content", ""))) == 64
            for value in hashes
        ):
            violations.append("SBOM_COMPONENT_SHA256_MISSING:" + str(component.get("purl", "")))
    license_policy = policy.get("dependency_license_policy", {})
    denied = set(license_policy.get("denied", []))
    review = set(license_policy.get("human_compatibility_review_required", []))
    for dependency in inventory.get("dependencies", []):
        if dependency.get("group") == "io.onsure":
            continue
        licenses = dependency.get("licenses", []) or ["UNDECLARED"]
        if denied.intersection(licenses):
            violations.append("DEPENDENCY_LICENSE_DENIED:" + str(dependency.get("purl", "")))
        if review.intersection(licenses):
            blockers.append("DEPENDENCY_LICENSE_COMPATIBILITY_REVIEW:" + str(dependency.get("purl", "")))
    if inventory.get("root_source_license") != ROOT_LICENSE_ID:
        blockers.append("ROOT_SOURCE_LICENSE_INVALID")

    if vulnerability.get("contract") != "ONSURE_VULNERABILITY_SCAN_EVIDENCE_V1" \
            or vulnerability.get("final_claim_allowed") is not False:
        violations.append("VULNERABILITY_EVIDENCE_CONTRACT_INVALID")
    if vulnerability.get("source_sbom") != DEFAULT_SBOM.relative_to(ROOT).as_posix() \
            or vulnerability.get("source_sbom_file_sha256") != file_sha256(DEFAULT_SBOM):
        violations.append("VULNERABILITY_EVIDENCE_SBOM_BINDING_INVALID")
    if vulnerability.get("state") == "NOT_RUN":
        blockers.append("VULNERABILITY_SCAN_NOT_RUN")
        if any(vulnerability.get(field) != "NOT_RUN" for field in (
            "scanner", "scanner_version", "database_updated_at", "scanned_at",
            "critical", "high", "medium", "low",
        )):
            violations.append("VULNERABILITY_NOT_RUN_EVIDENCE_INCONSISTENT")
    elif vulnerability.get("state") == "COMPLETED":
        gate = policy.get("vulnerability_gate", {})
        if vulnerability.get("scanner") not in gate.get("accepted_scanners", []):
            violations.append("VULNERABILITY_SCANNER_NOT_ACCEPTED")
        for severity in ("critical", "high", "medium", "low"):
            if not isinstance(vulnerability.get(severity), int) or vulnerability[severity] < 0:
                violations.append("VULNERABILITY_COUNT_INVALID:" + severity)
        if isinstance(vulnerability.get("critical"), int) \
                and vulnerability["critical"] > gate.get("maximum_critical", 0):
            blockers.append("VULNERABILITY_CRITICAL_OPEN")
        if isinstance(vulnerability.get("high"), int) \
                and vulnerability["high"] > gate.get("maximum_high", 0):
            blockers.append("VULNERABILITY_HIGH_OPEN")
    else:
        violations.append("VULNERABILITY_EVIDENCE_STATE_INVALID")
    if npm_audit is not None:
        lock = ROOT / "vscode-extension/package-lock.json"
        if npm_audit.get("contract") != "ONSURE_NPM_AUDIT_EVIDENCE_V1" \
                or npm_audit.get("state") != "COMPLETED" \
                or npm_audit.get("package_lock") != "vscode-extension/package-lock.json" \
                or npm_audit.get("package_lock_sha256") != file_sha256(lock) \
                or npm_audit.get("final_claim_allowed") is not False:
            violations.append("NPM_AUDIT_EVIDENCE_BINDING_INVALID")
        counts = npm_audit.get("vulnerabilities", {})
        if any(not isinstance(counts.get(severity), int) or counts[severity] < 0
               for severity in ("critical", "high", "moderate", "low", "info", "total")):
            violations.append("NPM_AUDIT_COUNTS_INVALID")
        else:
            gate = policy.get("npm_audit_gate", {})
            if counts["critical"] > gate.get("maximum_critical", 0):
                blockers.append("NPM_CRITICAL_VULNERABILITY_OPEN")
            if counts["high"] > gate.get("maximum_high", 0):
                blockers.append("NPM_HIGH_VULNERABILITY_OPEN")
    return violations, blockers


def validate() -> dict[str, object]:
    if not DEFAULT_SBOM.is_file() or not DEFAULT_INVENTORY.is_file():
        raise ValueError("SUPPLY_CHAIN_BASELINE_MISSING")
    actual_sbom, actual_inventory, actual_vscode = generate()
    expected_sbom = json.loads(DEFAULT_SBOM.read_text(encoding="utf-8"))
    expected_inventory = json.loads(DEFAULT_INVENTORY.read_text(encoding="utf-8"))
    expected_vscode = json.loads(DEFAULT_VSCODE_INVENTORY.read_text(encoding="utf-8"))
    policy = json.loads(POLICY_PATH.read_text(encoding="utf-8"))
    vulnerability = json.loads(DEFAULT_VULNERABILITY.read_text(encoding="utf-8"))
    npm_audit = json.loads(DEFAULT_NPM_AUDIT.read_text(encoding="utf-8"))
    violations: list[str] = []
    if actual_sbom != expected_sbom:
        violations.append("CYCLONEDX_SBOM_DRIFT")
    if actual_inventory != expected_inventory:
        violations.append("DEPENDENCY_LICENSE_INVENTORY_DRIFT")
    if actual_vscode != expected_vscode:
        violations.append("VSCODE_DEPENDENCY_INVENTORY_DRIFT")
    policy_violations, release_blockers = validate_policy(
        actual_sbom, actual_inventory, policy, vulnerability, npm_audit
    )
    violations.extend(policy_violations)
    return {
        "contract": "ONSURE_SUPPLY_CHAIN_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "component_count": actual_inventory["component_count"],
        "dependency_license_review_required_count": actual_inventory[
            "dependency_license_review_required_count"
        ],
        "root_source_license": actual_inventory["root_source_license"],
        "release_blockers": release_blockers,
        "release_gate_eligible": not violations and not release_blockers,
        "vulnerability_scan_state": vulnerability["state"],
        "npm_audit_state": npm_audit["state"],
        "npm_vulnerability_total": npm_audit["vulnerabilities"]["total"],
        "vscode_dependency_component_count": actual_vscode["component_count"],
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("generate", "validate"))
    args = parser.parse_args(argv)
    if args.mode == "generate":
        sbom, inventory, vscode = generate()
        write_json(DEFAULT_SBOM, sbom)
        write_json(DEFAULT_INVENTORY, inventory)
        write_json(DEFAULT_VSCODE_INVENTORY, vscode)
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
