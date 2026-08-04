#!/usr/bin/env python3
"""Validate deployment/migration preconditions while denying execution without authority."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys
from typing import Iterable

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
DEPLOYMENT = ROOT / "deploy/deployment-plan.v1.json"
MIGRATION = ROOT / "config/database-migration/migration-plan.v1.json"


def validate_plans(deployment: dict[str, object], migration: dict[str, object]) -> list[str]:
    violations: list[str] = []
    if deployment.get("contract") != "ONSURE_DEPLOYMENT_EXECUTION_SKELETON_V1":
        violations.append("DEPLOYMENT_CONTRACT")
    if deployment.get("state") != "PREFLIGHT_ONLY_NOT_AUTHORIZED":
        violations.append("DEPLOYMENT_STATE")
    runtime = deployment.get("runtime", {})
    required_runtime = {
        "identity": "NON_ROOT_SYSTEM_USER_ONSURE",
        "application_filesystem": "READ_ONLY",
        "api_bind": "127.0.0.1",
        "evidence_volume": "SEPARATE_WRITABLE_VOLUME_REQUIRED",
        "secret_source": "EXTERNAL_PROVIDER_REQUIRED",
        "secret_values_in_plan": False,
    }
    for field, expected in required_runtime.items():
        if runtime.get(field) != expected:
            violations.append("DEPLOYMENT_RUNTIME_" + field.upper())
    for field in ("deployment_authorized", "production_go", "public_network_exposure", "final_claim_allowed"):
        if deployment.get(field) is not False:
            violations.append("DEPLOYMENT_UNSAFE_" + field.upper())
    if deployment.get("install_command") != "NOT_AUTHORIZED" \
            or deployment.get("rollback_command") != "NOT_AUTHORIZED":
        violations.append("DEPLOYMENT_COMMAND_AUTHORITY")
    if deployment.get("target_os") != "UBUNTU_24_04_LTS" \
            or deployment.get("topology") != "SINGLE_STANDALONE_SERVER" \
            or deployment.get("container_image") != "NOT_USED" \
            or deployment.get("orchestrator") != "SYSTEMD":
        violations.append("DEPLOYMENT_UBUNTU_SYSTEMD_TOPOLOGY")
    if deployment.get("package_command") != "bash scripts/package_onsure_ubuntu.sh":
        violations.append("DEPLOYMENT_PACKAGE_COMMAND")
    if deployment.get("package_validation") != "python3 scripts/validate_onsure_ubuntu_package.py" \
            or deployment.get("package_validation_evidence") \
            != "assurance/runtime/onsure-ubuntu-package-validation.v1.json" \
            or deployment.get("systemd_security_validation") \
            != "python3 scripts/onsure_ubuntu_systemd_security.py" \
            or deployment.get("systemd_security_evidence") \
            != "assurance/runtime/onsure-ubuntu-systemd-security.v1.json":
        violations.append("DEPLOYMENT_VALIDATION_EVIDENCE")

    if migration.get("contract") != "ONSURE_DATABASE_MIGRATION_EXECUTION_SKELETON_V1":
        violations.append("MIGRATION_CONTRACT")
    if migration.get("state") != "CANDIDATE_IMPLEMENTED_EXECUTION_NOT_AUTHORIZED" \
            or migration.get("database_component_present") is not True:
        violations.append("MIGRATION_STATE")
    if migration.get("database_engine") != "POSTGRESQL" \
            or migration.get("migration_tool") != "FLYWAY_12_11_0" \
            or migration.get("schema_owner") != "onsure":
        violations.append("MIGRATION_POSTGRESQL_FLYWAY_SELECTION")
    ordered = migration.get("ordered_migrations", [])
    if ordered != ["modules/onsure-migration-postgresql/src/main/resources/db/migration/postgresql/V1__create_assurance_event.sql"]:
        violations.append("MIGRATION_ORDERED_FILES")
    elif any(not (ROOT / path).is_file() for path in ordered):
        violations.append("MIGRATION_FILE_MISSING")
    for field in ("migration_authorized", "destructive_ddl_allowed", "customer_data_fixture_allowed", "final_claim_allowed"):
        if migration.get(field) is not False:
            violations.append("MIGRATION_UNSAFE_" + field.upper())
    if migration.get("apply_command") != "AUTHORIZATION_GATED_BY_ONSURE_MIGRATION_AUTHORIZED":
        violations.append("MIGRATION_COMMAND_AUTHORITY")
    if migration.get("development_rehearsal_status") != "PASS_POSTGRESQL_16_14_NONFINAL" \
            or migration.get("development_rehearsal_evidence") \
            != "assurance/runtime/onsure-postgresql-flyway-rehearsal.v1.json" \
            or migration.get("rhel_production_rehearsal_status") != "NOT_RUN" \
            or migration.get("ubuntu_production_rehearsal_status") != "NOT_RUN":
        violations.append("MIGRATION_REHEARSAL_STATUS")
    return violations


def preflight() -> dict[str, object]:
    deployment = json.loads(DEPLOYMENT.read_text(encoding="utf-8"))
    migration = json.loads(MIGRATION.read_text(encoding="utf-8"))
    violations = validate_plans(deployment, migration)
    artifact = ROOT / str(deployment.get("artifact", ""))
    artifact_status = "AVAILABLE" if artifact.is_file() else "NOT_RUN_ARTIFACT_MISSING"
    artifact_sha = hashlib.sha256(artifact.read_bytes()).hexdigest() if artifact.is_file() else "NOT_RUN"
    return {
        "contract": "ONSURE_DEPLOYMENT_MIGRATION_PREFLIGHT_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "artifact_status": artifact_status,
        "artifact_sha256": artifact_sha,
        "deployment_execution": "NOT_RUN_NOT_AUTHORIZED",
        "database_migration_execution": "NOT_RUN_NOT_AUTHORIZED",
        "rollback_execution": "NOT_RUN_NOT_AUTHORIZED",
        "production_go": False,
        "final_claim_allowed": False,
    }


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("preflight", "deploy", "migrate", "rollback"))
    args = parser.parse_args(argv)
    if args.action != "preflight":
        raise ValueError(f"EXECUTION_NOT_AUTHORIZED:{args.action.upper()}")
    result = preflight()
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ONSURE_DEPLOY_MIGRATION_FAIL_CLOSED {error}", file=sys.stderr)
        raise SystemExit(1)
