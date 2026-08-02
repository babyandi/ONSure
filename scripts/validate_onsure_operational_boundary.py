#!/usr/bin/env python3
"""Validate fail-closed ONSure deployment and database migration design boundaries."""

from __future__ import annotations

import json
import sys

import yaml

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
CONTRACT = ROOT / "contracts/onsure-operational-boundary.v1.json"


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
    if deployment.get("runtime_definition_status") != "NOT_IMPLEMENTED":
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
    for field in ("container_image", "orchestrator"):
        if deployment.get(field) != "NOT_SELECTED":
            violations.append(f"PREMATURE_DEPLOYMENT_SELECTION:{field}")

    migration = boundary.get("database_migration", {})
    if migration.get("component_status") != "NOT_PRESENT":
        violations.append("DATABASE_MIGRATION_COMPONENT_STATUS")
    if migration.get("migration_authorized") is not False:
        violations.append("DATABASE_MIGRATION_AUTHORITY")
    for field in ("database_engine", "migration_tool"):
        if migration.get(field) != "NOT_SELECTED":
            violations.append(f"PREMATURE_DATABASE_SELECTION:{field}")
    if migration.get("current_command") != "NOT_RUN_NOT_APPLICABLE":
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
    if product_components.get("migration", {}).get("status") != "NOT_PRESENT":
        violations.append("PRODUCT_MIGRATION_STATUS_DRIFT")
    if product.get("release", {}).get("deployment") != "DESIGN_ONLY_NONFINAL":
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
        "config/database-migration/README.md",
        "docs/architecture/ONSURE_DEPLOYMENT_AND_DB_MIGRATION_DESIGN_v1.md",
        "docs/operations/ONSURE_BUBBLEWRAP_EXECUTION_ENVIRONMENT_v1.md",
        "scripts/onsure_bubblewrap_diagnostics.py",
    ]
    missing = [path for path in required_files if not (ROOT / path).is_file()]
    if missing:
        violations.append("OPERATIONAL_DESIGN_FILE_MISSING:" + ",".join(missing))
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
