#!/usr/bin/env python3
"""Validate non-mutating ONSure monorepo migration preparation artifacts."""

from __future__ import annotations

import json
import pathlib
import re
import sys

import onsure_monorepo_manifest as manifest
import validate_onsure_build_boundary as build_boundary
import validate_onsure_operational_boundary as operational_boundary
import validate_onsure_product_metadata as product_metadata


ROOT = manifest.ROOT
MANIFEST = ROOT / "assurance/migration/onsure-migration-manifest.v1.json"
ABSOLUTE_WORKSPACE_PATTERNS = (
    re.compile(r"/" + "workspace/"),
    re.compile(r"/" + r"home/[A-Za-z0-9._-]+/"),
    re.compile(
        r"(?i)(?<![A-Za-z0-9_])[A-Z]:[\\/]{1,2}"
        r"(?:Users|workspace|projects|repos|src)[\\/]"
    ),
)
STANDALONE_INSTALLATION_PATHS = (
    "/var/lib/onsure/workspace",
)
EXTERNAL_PRODUCT_SOURCE = re.compile(
    r"(?:/" + r"workspace/(?:ORUDA|aTops|AsterDB)|\.\./(?:ORUDA|aTops|AsterDB)|"
    r"[A-Za-z]:\\\\[^\r\n]*(?:ORUDA|aTops|AsterDB))",
    re.IGNORECASE,
)


def text_files() -> list[pathlib.Path]:
    return [
        path
        for path in manifest.candidate_paths(MANIFEST)
        if not path.is_symlink() and path.stat().st_size <= 5_000_000
    ]


def contains_absolute_workspace(text: str) -> bool:
    inspected = text
    for approved in STANDALONE_INSTALLATION_PATHS:
        inspected = inspected.replace(approved, "ONSURE_STANDALONE_DATA_ROOT")
    return any(pattern.search(inspected) for pattern in ABSOLUTE_WORKSPACE_PATTERNS)


def main() -> int:
    errors: list[str] = []
    if not MANIFEST.is_file():
        errors.append("MANIFEST_MISSING")
        body: dict[str, object] = {}
    else:
        body = json.loads(MANIFEST.read_text(encoding="utf-8"))

    expected = manifest.build_manifest(MANIFEST)
    if body and body != expected:
        errors.append("MANIFEST_CONTENT_OR_DIGEST_DRIFT")

    java_files = list((ROOT / "modules").glob("*/src/main/java/**/*.java"))
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        package = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        if not package or not package.group(1).startswith(manifest.CURRENT_NAMESPACE):
            errors.append(f"CURRENT_JAVA_NAMESPACE_DRIFT:{path.relative_to(ROOT)}")
        if manifest.FUTURE_NAMESPACE in text:
            errors.append(f"FUTURE_NAMESPACE_APPLIED_PREMATURELY:{path.relative_to(ROOT)}")

    for pom in (ROOT / "pom.xml", ROOT / "pom-modular.xml"):
        text = pom.read_text(encoding="utf-8")
        if "<groupId>io.onsure</groupId>" not in text:
            errors.append(f"MAVEN_GROUP_ID_DRIFT:{pom.name}")

    absolute_reference_files: list[str] = []
    external_product_reference_files: list[str] = []
    for path in text_files():
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        relative = path.relative_to(ROOT).as_posix()
        if contains_absolute_workspace(text):
            absolute_reference_files.append(relative)
        if relative.startswith(("src/", "modules/", "onsure_core/")) and EXTERNAL_PRODUCT_SOURCE.search(text):
            external_product_reference_files.append(relative)
    if absolute_reference_files:
        errors.append("ABSOLUTE_WORKSPACE_REFERENCES:" + ",".join(sorted(absolute_reference_files)))
    if external_product_reference_files:
        errors.append(
            "EXTERNAL_PRODUCT_SOURCE_REFERENCES:"
            + ",".join(sorted(external_product_reference_files))
        )

    high_risk = int(
        expected["summary"]["sensitivity_counts"].get("HIGH_RISK_PATTERN_MATCH", 0)
    )
    if high_risk:
        errors.append(f"HIGH_RISK_SECRET_PATTERNS:{high_risk}")

    build_result = build_boundary.validate()
    if build_result["decision"] != "PASS_NONFINAL":
        errors.append("BUILD_BOUNDARY_VALIDATION_FAILED")
    metadata_result = product_metadata.validate()
    if metadata_result["decision"] != "PASS_NONFINAL":
        errors.append("PRODUCT_METADATA_VALIDATION_FAILED")
    operational_result = operational_boundary.validate()
    if operational_result["decision"] != "PASS_NONFINAL":
        errors.append("OPERATIONAL_BOUNDARY_VALIDATION_FAILED")

    api_baseline = json.loads(
        (ROOT / "contracts/java-public-api-baseline.v1.json").read_text(encoding="utf-8")
    )
    license_inventory = json.loads(
        (ROOT / "assurance/dependencies/onsure-dependency-license-inventory.v1.json")
        .read_text(encoding="utf-8")
    )
    overlap = json.loads(
        (ROOT / "assurance/migration/onsure-open-pr-overlap.v1.json")
        .read_text(encoding="utf-8")
    )
    if overlap.get("automatic_merge_allowed") is not False:
        errors.append("OPEN_PR_OVERLAP_AUTOMATIC_MERGE_AUTHORITY_DRIFT")

    known_blockers = [
        "RHEL_INSTALL_AND_SYSTEMD_START_NOT_RUN",
        "UBUNTU_PRODUCTION_ACCEPTANCE_NOT_RUN",
        "PRODUCTION_POSTGRESQL_REHEARSAL_NOT_RUN",
        "OPENAI_LIVE_REQUEST_NOT_RUN",
    ]
    if build_result["shared_source_module_count"]:
        known_blockers.insert(0, "TRANSITIONAL_SHARED_SOURCE_ROOT")
    if license_inventory.get("root_source_license") != manifest.ROOT_LICENSE:
        known_blockers.append("ROOT_LICENSE_INVALID")

    report = {
        "contract": "ONSURE_MONOREPO_MIGRATION_READINESS_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not errors else "FAIL",
        "errors": errors,
        "current_java_namespace": manifest.CURRENT_NAMESPACE,
        "future_java_namespace_candidate": manifest.FUTURE_NAMESPACE,
        "absolute_workspace_reference_count": len(absolute_reference_files),
        "external_product_source_reference_count": len(external_product_reference_files),
        "manifest_file_count": expected["summary"]["file_count"],
        "high_risk_secret_pattern_count": high_risk,
        "build_boundary_decision": build_result["decision"],
        "product_metadata_decision": metadata_result["decision"],
        "operational_boundary_decision": operational_result["decision"],
        "deployment_runtime_status": operational_result["deployment_runtime_status"],
        "database_migration_component_status": operational_result[
            "database_migration_component_status"
        ],
        "github_actions_used": operational_result["github_actions_used"],
        "module_dependency_cycle_count": build_result["module_dependency_cycle_count"],
        "main_source_single_owner_count": build_result["main_source_single_owner_count"],
        "shared_source_module_count": build_result["shared_source_module_count"],
        "public_api_baseline_class_count": api_baseline["class_count"],
        "dependency_component_count": license_inventory["component_count"],
        "dependency_license_review_required_count": license_inventory[
            "dependency_license_review_required_count"
        ],
        "root_source_license": license_inventory["root_source_license"],
        "copyright_holder": license_inventory.get("copyright_holder", "UNDETERMINED"),
        "rights_declaration": license_inventory.get("rights_declaration", "NOT_RECORDED"),
        "open_pr_merge_decision": overlap["merge_decision"],
        "known_blockers": known_blockers,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
