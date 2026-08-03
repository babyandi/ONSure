#!/usr/bin/env python3
"""Validate fail-closed ONSure deployment and database migration design boundaries."""

from __future__ import annotations

import json
import sys

import yaml

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
CONTRACT = ROOT / "contracts/onsure-operational-boundary.v1.json"


def validate_rhel_candidate() -> list[str]:
    violations: list[str] = []
    app_unit = (ROOT / "deploy/rhel/onsure.service").read_text(encoding="utf-8")
    migration_unit = (ROOT / "deploy/rhel/onsure-migrate.service").read_text(encoding="utf-8")
    environment = (ROOT / "deploy/rhel/onsure.env.example").read_text(encoding="utf-8")
    required_app = (
        "User=onsure", "Group=onsure", "NoNewPrivileges=yes", "ProtectSystem=strict",
        "ProtectHome=yes", "PrivateDevices=yes", "CapabilityBoundingSet=",
        "ReadWritePaths=/var/lib/onsure /var/log/onsure",
        "io.onsure.localapi.LocalApiMain",
    )
    required_migration = (
        "User=onsure", "NoNewPrivileges=yes", "ProtectSystem=strict",
        "io.onsure.migration.postgresql.PostgresqlMigrationMain migrate",
    )
    for value in required_app:
        if value not in app_unit:
            violations.append("RHEL_APP_UNIT_MISSING:" + value)
    for value in required_migration:
        if value not in migration_unit:
            violations.append("RHEL_MIGRATION_UNIT_MISSING:" + value)
    if "ONSURE_WORKSPACE_ROOT=/var/lib/onsure/workspace" not in environment \
            or "ONSURE_DB_URL=jdbc:postgresql://127.0.0.1:5432/onsure" not in environment \
            or "ONSURE_MIGRATION_AUTHORIZED=false" not in environment:
        violations.append("RHEL_ENVIRONMENT_FAIL_CLOSED_DEFAULTS")
    for secret in ("OPENAI_API_KEY", "ONSURE_DB_PASSWORD", "ONSURE_LOCAL_API_TOKEN"):
        for line in environment.splitlines():
            if not line.lstrip().startswith("#") and line.startswith(secret + "="):
                violations.append("RHEL_ENVIRONMENT_SECRET_VALUE_SLOT:" + secret)
    if "0.0.0.0" in app_unit or "User=root" in app_unit + migration_unit:
        violations.append("RHEL_UNIT_UNSAFE_RUNTIME")
    return violations


def validate_documents(
    boundary: dict[str, object],
    product: dict[str, object],
    obuilder: dict[str, object],
) -> list[str]:
    violations: list[str] = []
    if boundary.get("contract") != "ONSURE_OPERATIONAL_BOUNDARY_V1":
        violations.append("OPERATIONAL_CONTRACT_ID")
    if boundary.get("status") != "DESIGN_ONLY_NONFINAL":
        violations.append("OPERATIONAL_STATUS")
    if boundary.get("final_claim_allowed") is not False:
        violations.append("OPERATIONAL_FINAL_AUTHORITY")

    deployment = boundary.get("deployment", {})
    if deployment.get("runtime_definition_status") != "RHEL_SYSTEMD_CANDIDATE_IMPLEMENTED":
        violations.append("DEPLOYMENT_RUNTIME_STATUS")
    if deployment.get("deployment_authorized") is not False:
        violations.append("DEPLOYMENT_AUTHORITY")
    if deployment.get("production_go") is not False:
        violations.append("DEPLOYMENT_PRODUCTION_GO")
    if deployment.get("public_network_exposure") != "NOT_AUTHORIZED":
        violations.append("PUBLIC_NETWORK_AUTHORITY")
    if deployment.get("secret_material_in_repository") != "FORBIDDEN":
        violations.append("SECRET_REPOSITORY_BOUNDARY")
    if deployment.get("rollback_required") is not True:
        violations.append("DEPLOYMENT_ROLLBACK_REQUIRED")
    if deployment.get("target_os") != "RHEL_FAMILY" \
            or deployment.get("container_image") != "NOT_USED" \
            or deployment.get("orchestrator") != "SYSTEMD":
        violations.append("DEPLOYMENT_RHEL_SYSTEMD_SELECTION")

    migration = boundary.get("database_migration", {})
    if migration.get("component_status") != "POSTGRESQL_FLYWAY_CANDIDATE_IMPLEMENTED":
        violations.append("DATABASE_MIGRATION_COMPONENT_STATUS")
    if migration.get("migration_authorized") is not False:
        violations.append("DATABASE_MIGRATION_AUTHORITY")
    if migration.get("database_engine") != "POSTGRESQL" \
            or migration.get("migration_tool") != "FLYWAY_12_11_0" \
            or migration.get("schema_owner") != "onsure":
        violations.append("DATABASE_POSTGRESQL_FLYWAY_SELECTION")
    if migration.get("current_command") != "NOT_RUN_NOT_AUTHORIZED":
        violations.append("DATABASE_MIGRATION_COMMAND_STATUS")
    if migration.get("destructive_ddl_default") != "DENY":
        violations.append("DESTRUCTIVE_DDL_BOUNDARY")
    if migration.get("customer_data_fixture_default") != "DENY":
        violations.append("CUSTOMER_DATA_FIXTURE_BOUNDARY")
    if migration.get("rollback_required") is not True:
        violations.append("DATABASE_ROLLBACK_REQUIRED")

    actions = boundary.get("github_actions", {})
    if actions.get("used") is not False or actions.get("required") is not False:
        violations.append("GITHUB_ACTIONS_BOUNDARY")

    product_components = product.get("components", {})
    if product_components.get("migration", {}).get("status") != "IMPLEMENTED_CANDIDATE_NONFINAL":
        violations.append("PRODUCT_MIGRATION_STATUS_DRIFT")
    if product.get("release", {}).get("deployment") != "RHEL_SYSTEMD_CANDIDATE_NONFINAL":
        violations.append("PRODUCT_DEPLOYMENT_STATUS_DRIFT")

    command = "python3 scripts/validate_onsure_operational_boundary.py"
    if product.get("build", {}).get("operational_boundary_validation", {}).get(
        "command"
    ) != command:
        violations.append("PRODUCT_OPERATIONAL_COMMAND_DRIFT")
    if command not in obuilder.get("required_nonfinal_gates", []):
        violations.append("OBUILDER_OPERATIONAL_GATE_MISSING")
    if "DEPLOYMENT" not in obuilder.get("prohibited_actions", []):
        violations.append("OBUILDER_DEPLOYMENT_PROHIBITION")
    return violations


def validate() -> dict[str, object]:
    boundary = json.loads(CONTRACT.read_text(encoding="utf-8"))
    product = yaml.safe_load((ROOT / "product.yaml").read_text(encoding="utf-8"))
    obuilder = yaml.safe_load(
        (ROOT / ".obuilder/product-build.yaml").read_text(encoding="utf-8")
    )
    violations = validate_documents(boundary, product, obuilder)
    required_files = [
        "deploy/README.md",
        "deploy/deployment-plan.v1.json",
        "deploy/rhel/onsure.service",
        "deploy/rhel/onsure-migrate.service",
        "deploy/rhel/onsure.env.example",
        "deploy/rhel/onsure.sysusers.conf",
        "deploy/rhel/onsure.tmpfiles.conf",
        "deploy/rhel/README.md",
        "config/database-migration/README.md",
        "config/database-migration/migration-plan.v1.json",
        "docs/architecture/ONSURE_DEPLOYMENT_AND_DB_MIGRATION_DESIGN_v1.md",
        "docs/operations/ONSURE_BUBBLEWRAP_EXECUTION_ENVIRONMENT_v1.md",
        "scripts/onsure_bubblewrap_diagnostics.py",
        "scripts/package_onsure_rhel.sh",
        "modules/onsure-provider-openai/pom.xml",
        "modules/onsure-migration-postgresql/pom.xml",
        "config/provider/openai-request.example.json",
    ]
    missing = [path for path in required_files if not (ROOT / path).is_file()]
    if missing:
        violations.append("OPERATIONAL_DESIGN_FILE_MISSING:" + ",".join(missing))
    if not missing:
        from onsure_deploy_migration_skeleton import validate_plans

        deployment = json.loads((ROOT / "deploy/deployment-plan.v1.json").read_text(encoding="utf-8"))
        migration = json.loads((ROOT / "config/database-migration/migration-plan.v1.json").read_text(encoding="utf-8"))
        violations.extend("EXECUTION_SKELETON:" + item for item in validate_plans(deployment, migration))
        violations.extend(validate_rhel_candidate())
    return {
        "contract": "ONSURE_OPERATIONAL_BOUNDARY_VALIDATION_V1",
        "decision": "PASS_NONFINAL" if not violations else "FAIL",
        "violations": violations,
        "deployment_runtime_status": boundary["deployment"][
            "runtime_definition_status"
        ],
        "database_migration_component_status": boundary["database_migration"][
            "component_status"
        ],
        "github_actions_used": boundary["github_actions"]["used"],
        "required_human_decision_count": len(boundary["required_human_decisions"]),
        "final_claim_allowed": False,
    }


def main() -> int:
    result = validate()
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["decision"] == "PASS_NONFINAL" else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError, yaml.YAMLError) as error:
        print(f"ONSURE_OPERATIONAL_BOUNDARY_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
