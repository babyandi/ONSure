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
        "identity": "NON_ROOT_UID_65532",
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

    if migration.get("contract") != "ONSURE_DATABASE_MIGRATION_EXECUTION_SKELETON_V1":
        violations.append("MIGRATION_CONTRACT")
    if migration.get("state") != "PREFLIGHT_ONLY_NOT_APPLICABLE" \
            or migration.get("database_component_present") is not False:
        violations.append("MIGRATION_STATE")
    for field in ("database_engine", "migration_tool"):
        if migration.get(field) != "NOT_SELECTED":
            violations.append("MIGRATION_PREMATURE_" + field.upper())
    if migration.get("ordered_migrations") != []:
        violations.append("MIGRATION_FILES_PREMATURE")
    for field in ("migration_authorized", "destructive_ddl_allowed", "customer_data_fixture_allowed", "final_claim_allowed"):
        if migration.get(field) is not False:
            violations.append("MIGRATION_UNSAFE_" + field.upper())
    if migration.get("apply_command") != "NOT_AUTHORIZED":
        violations.append("MIGRATION_COMMAND_AUTHORITY")
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
        "database_migration_execution": "NOT_RUN_NOT_APPLICABLE",
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
