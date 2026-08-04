#!/usr/bin/env python3
"""Validate fail-closed ONSure deployment and database migration design boundaries."""

from __future__ import annotations

import json
import hashlib
import sys

import yaml

from onsure_product_root import resolve_product_root


ROOT = resolve_product_root()
CONTRACT = ROOT / "contracts/onsure-operational-boundary.v1.json"
POSTGRESQL_EVIDENCE = ROOT / "assurance/runtime/onsure-postgresql-flyway-rehearsal.v1.json"
POSTGRESQL_MIGRATION = ROOT / (
    "modules/onsure-migration-postgresql/src/main/resources/db/migration/postgresql/"
    "V1__create_assurance_event.sql"
)
SYSTEMD_EVIDENCE = ROOT / "assurance/runtime/onsure-rhel-systemd-security.v1.json"
UBUNTU_SYSTEMD_EVIDENCE = ROOT / "assurance/runtime/onsure-ubuntu-systemd-security.v1.json"
RHEL_PACKAGE_EVIDENCE = ROOT / "assurance/runtime/onsure-rhel-package-validation.v1.json"
UBUNTU_PACKAGE_EVIDENCE = ROOT / "assurance/runtime/onsure-ubuntu-package-validation.v1.json"
UBUNTU_LIFECYCLE_EVIDENCE = ROOT / "assurance/runtime/onsure-ubuntu-lifecycle-rehearsal.v1.json"
VSCODE_RUNTIME_EVIDENCE = ROOT / "assurance/runtime/onsure-vscode-ubuntu-runtime-rehearsal.v1.json"
UBUNTU_HOST_PREFLIGHT_EVIDENCE = ROOT / "assurance/runtime/onsure-ubuntu-host-preflight.v1.json"
SYSTEMD_UNITS = (
    ROOT / "deploy/rhel/onsure.service",
    ROOT / "deploy/rhel/onsure-llm-gateway.service",
    ROOT / "deploy/rhel/onsure-migrate.service",
    ROOT / "deploy/ubuntu/onsure-backup.service",
)


def validate_rhel_candidate() -> list[str]:
    violations: list[str] = []
    app_unit = (ROOT / "deploy/rhel/onsure.service").read_text(encoding="utf-8")
    gateway_unit = (ROOT / "deploy/rhel/onsure-llm-gateway.service").read_text(encoding="utf-8")
    migration_unit = (ROOT / "deploy/rhel/onsure-migrate.service").read_text(encoding="utf-8")
    environment = (ROOT / "deploy/rhel/onsure.env.example").read_text(encoding="utf-8")
    required_app = (
        "User=onsure", "Group=onsure", "NoNewPrivileges=yes", "ProtectSystem=strict",
        "ProtectHome=yes", "PrivateDevices=yes", "CapabilityBoundingSet=",
        "RemoveIPC=yes", "KeyringMode=private", "ProtectHostname=yes",
        "ProtectProc=invisible", "ProcSubset=pid", "RestrictNamespaces=yes",
        "SystemCallArchitectures=native",
        "ReadWritePaths=/var/lib/onsure /var/log/onsure",
        "io.onsure.localapi.LocalApiMain",
    )
    required_migration = (
        "User=onsure", "NoNewPrivileges=yes", "ProtectSystem=strict",
        "RemoveIPC=yes", "ProtectProc=invisible", "RestrictNamespaces=yes",
        "IPAddressDeny=any", "IPAddressAllow=localhost",
        "io.onsure.migration.postgresql.PostgresqlMigrationMain migrate",
    )
    required_gateway = (
        "User=onsure", "Group=onsure", "NoNewPrivileges=yes", "ProtectSystem=strict",
        "ProtectHome=yes", "PrivateDevices=yes", "CapabilityBoundingSet=",
        "RemoveIPC=yes", "ProtectProc=invisible", "RestrictNamespaces=yes",
        "ReadWritePaths=/var/lib/onsure /var/log/onsure",
        "io.onsure.gateway.llm.LlmGatewayMain",
    )
    for value in required_app:
        if value not in app_unit:
            violations.append("RHEL_APP_UNIT_MISSING:" + value)
    for value in required_migration:
        if value not in migration_unit:
            violations.append("RHEL_MIGRATION_UNIT_MISSING:" + value)
    for value in required_gateway:
        if value not in gateway_unit:
            violations.append("RHEL_GATEWAY_UNIT_MISSING:" + value)
    if "ONSURE_WORKSPACE_ROOT=/var/lib/onsure/workspace" not in environment \
            or "ONSURE_DB_URL=jdbc:postgresql://127.0.0.1:5432/onsure" not in environment \
            or "ONSURE_MIGRATION_AUTHORIZED=false" not in environment \
            or "ONSURE_LLM_GATEWAY_PORT=47312" not in environment \
            or "ONSURE_LLM_PROVIDER=local-mock" not in environment:
        violations.append("RHEL_ENVIRONMENT_FAIL_CLOSED_DEFAULTS")
    for secret in ("OPENAI_API_KEY", "ONSURE_DB_PASSWORD", "ONSURE_LOCAL_API_TOKEN", "ONSURE_LLM_GATEWAY_TOKEN"):
        for line in environment.splitlines():
            if not line.lstrip().startswith("#") and line.startswith(secret + "="):
                violations.append("RHEL_ENVIRONMENT_SECRET_VALUE_SLOT:" + secret)
    if "0.0.0.0" in app_unit + gateway_unit or "User=root" in app_unit + gateway_unit + migration_unit:
        violations.append("RHEL_UNIT_UNSAFE_RUNTIME")
    return violations


def validate_ubuntu_candidate() -> list[str]:
    violations: list[str] = []
    plan = json.loads(
        (ROOT / "deploy/ubuntu/deployment-plan.v1.json").read_text(encoding="utf-8")
    )
    expected = {
        "contract": "ONSURE_UBUNTU_DEPLOYMENT_CANDIDATE_V1",
        "state": "PREFLIGHT_ONLY_NOT_AUTHORIZED",
        "artifact": "target/onsure-ubuntu-candidate.tar.gz",
        "target_os": "UBUNTU_24_04_LTS",
        "topology": "SINGLE_STANDALONE_SERVER",
        "container_image": "NOT_USED",
        "orchestrator": "SYSTEMD",
        "package_command": "bash scripts/package_onsure_ubuntu.sh",
        "package_validation": "python3 scripts/validate_onsure_ubuntu_package.py",
        "package_validation_evidence": (
            "assurance/runtime/onsure-ubuntu-package-validation.v1.json"
        ),
        "systemd_security_validation": "python3 scripts/onsure_ubuntu_systemd_security.py",
        "systemd_security_evidence": (
            "assurance/runtime/onsure-ubuntu-systemd-security.v1.json"
        ),
        "shared_systemd_definition_root": "deploy/rhel",
        "api_bind": "127.0.0.1",
        "postgresql_bind": "127.0.0.1",
        "apparmor_execution": "NOT_RUN",
        "ufw_execution": "NOT_RUN",
        "install_command": "NOT_AUTHORIZED",
        "rollback_command": "NOT_AUTHORIZED",
        "public_network_exposure": False,
        "deployment_authorized": False,
        "production_go": False,
        "final_claim_allowed": False,
    }
    for field, value in expected.items():
        if plan.get(field) != value:
            violations.append("UBUNTU_DEPLOYMENT_PLAN_" + field.upper())
    readme = (ROOT / "deploy/ubuntu/README.md").read_text(encoding="utf-8")
    normalized_readme = " ".join(readme.split())
    for required in (
        "Ubuntu 24.04 LTS", "127.0.0.1:47311", "AppArmor", "UFW",
        "deploy/rhel/", "NOT_RUN", "not authorized",
    ):
        if required not in normalized_readme:
            violations.append("UBUNTU_README_MISSING:" + required)
    backup_service = (ROOT / "deploy/ubuntu/onsure-backup.service").read_text(encoding="utf-8")
    backup_timer = (ROOT / "deploy/ubuntu/onsure-backup.timer").read_text(encoding="utf-8")
    backup_script = (ROOT / "deploy/ubuntu/onsure-postgresql-backup").read_text(encoding="utf-8")
    for required in (
        "User=onsure", "NoNewPrivileges=yes", "ProtectSystem=strict",
        "ReadWritePaths=/var/lib/onsure/backups",
        "ExecStart=/usr/bin/bash /opt/onsure/bin/onsure-postgresql-backup",
    ):
        if required not in backup_service:
            violations.append("UBUNTU_BACKUP_SERVICE_MISSING:" + required)
    for required in ("OnCalendar=", "RandomizedDelaySec=", "Persistent=true"):
        if required not in backup_timer:
            violations.append("UBUNTU_BACKUP_TIMER_MISSING:" + required)
    for required in (
        "umask 077", "pg_dump", "pg_restore --list", "flock -n",
        "ONSURE_BACKUP_NON_LOOPBACK_DATABASE_DENIED", "chmod 0600",
    ):
        if required not in backup_script:
            violations.append("UBUNTU_BACKUP_SCRIPT_MISSING:" + required)
    return violations


def validate_postgresql_evidence() -> list[str]:
    violations: list[str] = []
    evidence = json.loads(POSTGRESQL_EVIDENCE.read_text(encoding="utf-8"))
    expected = {
        "contract": "ONSURE_POSTGRESQL_FLYWAY_REHEARSAL_V1",
        "decision": "PASS_NONFINAL",
        "migration_first_executed": 1,
        "migration_second_executed": 0,
        "pending_after_migration": 0,
        "restored_event_count": 1,
        "restored_history_count": 1,
        "restored_schema_validation": "PASS_NONFINAL",
        "concurrent_migration_executed_counts": [0, 1],
        "concurrent_migration_history_count": 1,
        "customer_data_used": False,
        "system_postgresql_service_modified": False,
        "production_migration": "NOT_RUN",
        "rhel_runtime": "NOT_RUN_HOST_IS_NOT_RHEL",
        "final_claim_allowed": False,
    }
    for field, value in expected.items():
        if evidence.get(field) != value:
            violations.append("POSTGRESQL_EVIDENCE_" + field.upper())
    migration_path = POSTGRESQL_MIGRATION.relative_to(ROOT).as_posix()
    migration_sha = hashlib.sha256(POSTGRESQL_MIGRATION.read_bytes()).hexdigest()
    if evidence.get("migration") != migration_path \
            or evidence.get("migration_sha256") != migration_sha:
        violations.append("POSTGRESQL_EVIDENCE_MIGRATION_BINDING")
    if not str(evidence.get("postgresql_version", "")).startswith("16."):
        violations.append("POSTGRESQL_EVIDENCE_VERSION")
    if not str(evidence.get("package_sha256", "")).isalnum() \
            or len(str(evidence.get("package_sha256", ""))) != 64:
        violations.append("POSTGRESQL_EVIDENCE_PACKAGE_DIGEST")
    return violations


def validate_systemd_evidence() -> list[str]:
    violations: list[str] = []
    evidence = json.loads(SYSTEMD_EVIDENCE.read_text(encoding="utf-8"))
    if evidence.get("contract") != "ONSURE_RHEL_SYSTEMD_SECURITY_REHEARSAL_V1" \
            or evidence.get("decision") != "PASS_NONFINAL" \
            or evidence.get("rhel_runtime_execution") != "NOT_RUN_HOST_IS_NOT_RHEL" \
            or evidence.get("service_enable_start") != "NOT_RUN" \
            or evidence.get("final_claim_allowed") is not False:
        violations.append("SYSTEMD_EVIDENCE_CONTRACT")
    expected = {
        path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in SYSTEMD_UNITS
    }
    actual = {str(item.get("path")): item for item in evidence.get("units", [])}
    if set(actual) != set(expected):
        violations.append("SYSTEMD_EVIDENCE_UNIT_SET")
    for path, digest in expected.items():
        item = actual.get(path, {})
        score = item.get("exposure_score")
        if item.get("sha256") != digest or item.get("decision") != "PASS_NONFINAL" \
                or not isinstance(score, (int, float)) or score > 4.0:
            violations.append("SYSTEMD_EVIDENCE_UNIT_BINDING:" + path)
    return violations


def validate_ubuntu_systemd_evidence() -> list[str]:
    violations: list[str] = []
    evidence = json.loads(UBUNTU_SYSTEMD_EVIDENCE.read_text(encoding="utf-8"))
    if evidence.get("contract") != "ONSURE_UBUNTU_SYSTEMD_SECURITY_REHEARSAL_V1" \
            or evidence.get("decision") != "PASS_NONFINAL" \
            or evidence.get("platform") != "UBUNTU" \
            or not str(evidence.get("host_os", "")).startswith("UBUNTU_24_04") \
            or evidence.get("ubuntu_runtime_execution") != "OFFLINE_ANALYSIS_ONLY" \
            or evidence.get("service_enable_start") != "NOT_RUN" \
            or evidence.get("apparmor_or_selinux_execution") != "NOT_RUN" \
            or evidence.get("firewall_execution") != "NOT_RUN" \
            or evidence.get("final_claim_allowed") is not False:
        violations.append("UBUNTU_SYSTEMD_EVIDENCE_CONTRACT")
    expected = {
        path.relative_to(ROOT).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in SYSTEMD_UNITS
    }
    actual = {str(item.get("path")): item for item in evidence.get("units", [])}
    if set(actual) != set(expected):
        violations.append("UBUNTU_SYSTEMD_EVIDENCE_UNIT_SET")
    for path, digest in expected.items():
        item = actual.get(path, {})
        score = item.get("exposure_score")
        if item.get("sha256") != digest or item.get("decision") != "PASS_NONFINAL" \
                or not isinstance(score, (int, float)) or score > 4.0:
            violations.append("UBUNTU_SYSTEMD_EVIDENCE_UNIT_BINDING:" + path)
    return violations


def validate_rhel_package_evidence() -> list[str]:
    violations: list[str] = []
    evidence = json.loads(RHEL_PACKAGE_EVIDENCE.read_text(encoding="utf-8"))
    if evidence.get("contract") != "ONSURE_RHEL_PACKAGE_VALIDATION_V1" \
            or evidence.get("decision") != "PASS_NONFINAL" \
            or evidence.get("secret_values_present") is not False \
            or evidence.get("path_escape_or_nonregular_entry_count") != 0 \
            or evidence.get("install_execution") != "NOT_RUN" \
            or evidence.get("platform") != "RHEL" \
            or evidence.get("runtime_execution") != "NOT_RUN" \
            or evidence.get("final_claim_allowed") is not False:
        violations.append("RHEL_PACKAGE_EVIDENCE_CONTRACT")
    source_bindings = evidence.get("source_bindings", {})
    for relative in (
        "scripts/package_onsure_rhel.sh", "scripts/package_onsure_systemd.sh",
        "deploy/rhel/onsure.service",
        "deploy/rhel/onsure-llm-gateway.service",
        "deploy/rhel/onsure-migrate.service", "deploy/rhel/onsure.env.example",
        "deploy/rhel/onsure.sysusers.conf", "deploy/rhel/onsure.tmpfiles.conf",
        "deploy/rhel/README.md",
    ):
        digest = hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()
        if source_bindings.get(relative) != digest:
            violations.append("RHEL_PACKAGE_EVIDENCE_SOURCE_BINDING:" + relative)
    package = ROOT / "target/onsure-rhel-candidate.tar.gz"
    if package.is_file() and evidence.get("package_sha256") \
            != hashlib.sha256(package.read_bytes()).hexdigest():
        violations.append("RHEL_PACKAGE_EVIDENCE_ARTIFACT_BINDING")
    postgresql = json.loads(POSTGRESQL_EVIDENCE.read_text(encoding="utf-8"))
    if postgresql.get("package_sha256") != evidence.get("package_sha256"):
        violations.append("RHEL_PACKAGE_POSTGRESQL_EVIDENCE_BINDING")
    return violations


def validate_ubuntu_package_evidence() -> list[str]:
    violations: list[str] = []
    evidence = json.loads(UBUNTU_PACKAGE_EVIDENCE.read_text(encoding="utf-8"))
    if evidence.get("contract") != "ONSURE_UBUNTU_PACKAGE_VALIDATION_V1" \
            or evidence.get("decision") != "PASS_NONFINAL" \
            or evidence.get("platform") != "UBUNTU" \
            or evidence.get("secret_values_present") is not False \
            or evidence.get("path_escape_or_nonregular_entry_count") != 0 \
            or evidence.get("install_execution") != "NOT_RUN" \
            or evidence.get("runtime_execution") != "NOT_RUN" \
            or evidence.get("final_claim_allowed") is not False:
        violations.append("UBUNTU_PACKAGE_EVIDENCE_CONTRACT")
    source_bindings = evidence.get("source_bindings", {})
    for relative in (
        "scripts/package_onsure_ubuntu.sh", "scripts/package_onsure_systemd.sh",
        "deploy/rhel/onsure.service", "deploy/rhel/onsure-llm-gateway.service",
        "deploy/rhel/onsure-migrate.service",
        "deploy/rhel/onsure.env.example", "deploy/rhel/onsure.sysusers.conf",
        "deploy/rhel/onsure.tmpfiles.conf", "deploy/ubuntu/README.md",
    ):
        digest = hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()
        if source_bindings.get(relative) != digest:
            violations.append("UBUNTU_PACKAGE_EVIDENCE_SOURCE_BINDING:" + relative)
    package = ROOT / "target/onsure-ubuntu-candidate.tar.gz"
    if package.is_file() and evidence.get("package_sha256") \
            != hashlib.sha256(package.read_bytes()).hexdigest():
        violations.append("UBUNTU_PACKAGE_EVIDENCE_ARTIFACT_BINDING")
    return violations


def validate_ubuntu_lifecycle_evidence() -> list[str]:
    violations: list[str] = []
    evidence = json.loads(UBUNTU_LIFECYCLE_EVIDENCE.read_text(encoding="utf-8"))
    package = json.loads(UBUNTU_PACKAGE_EVIDENCE.read_text(encoding="utf-8"))
    if evidence.get("contract") != "ONSURE_UBUNTU_LIFECYCLE_REHEARSAL_V1" \
            or evidence.get("decision") != "PASS_NONFINAL" \
            or evidence.get("idempotent_reinstall") is not True \
            or evidence.get("host_filesystem_modified") is not False \
            or evidence.get("production_execution") != "NOT_RUN" \
            or evidence.get("final_claim_allowed") is not False:
        violations.append("UBUNTU_LIFECYCLE_EVIDENCE_CONTRACT")
    if evidence.get("package_sha256") != package.get("package_sha256"):
        violations.append("UBUNTU_LIFECYCLE_PACKAGE_BINDING")
    if evidence.get("rollback_restored_release") != evidence.get("install_release"):
        violations.append("UBUNTU_LIFECYCLE_ROLLBACK_BINDING")
    return violations


def validate_vscode_runtime_evidence() -> list[str]:
    evidence = json.loads(VSCODE_RUNTIME_EVIDENCE.read_text(encoding="utf-8"))
    if evidence.get("contract") != "ONSURE_VSCODE_UBUNTU_RUNTIME_REHEARSAL_V1" \
            or evidence.get("decision") != "PASS_NONFINAL" \
            or evidence.get("local_api_state") != "RUNNING" \
            or evidence.get("llm_gateway_state") != "RUNNING" \
            or evidence.get("chain_valid") is not True \
            or evidence.get("content_recorded") is not False \
            or evidence.get("tokens_disclosed") is not False \
            or evidence.get("source_mutation") is not False \
            or evidence.get("production_acceptance") != "NOT_RUN" \
            or evidence.get("final_claim_allowed") is not False:
        return ["VSCODE_UBUNTU_RUNTIME_EVIDENCE_CONTRACT"]
    return []


def validate_ubuntu_host_preflight_evidence() -> list[str]:
    evidence = json.loads(UBUNTU_HOST_PREFLIGHT_EVIDENCE.read_text(encoding="utf-8"))
    services = evidence.get("services", {})
    listeners = evidence.get("listeners", {})
    systemd_security = evidence.get("systemd_security", {})
    if evidence.get("contract") != "ONSURE_UBUNTU_HOST_PREFLIGHT_V1" \
            or evidence.get("decision") != "PASS_NONFINAL" \
            or evidence.get("host_os") != "UBUNTU_24_04" \
            or any(services.get(name) != {"active": True, "enabled": True}
                   for name in ("onsure-runtime.service", "onsure-llm-gateway.service")) \
            or any(listeners.get(str(port), {}).get("loopback_only") is not True
                   for port in (47311, 47312, 5432)) \
            or any(not isinstance(systemd_security.get(name, {}).get("exposure_score"), (int, float))
                   for name in ("onsure-runtime.service", "onsure-llm-gateway.service")) \
            or evidence.get("runtime_config", {}).get("mode") != "0600" \
            or evidence.get("runtime_config", {}).get("secret_values_read") is not False \
            or evidence.get("runtime_config", {}).get("path_disclosed") is not False \
            or evidence.get("host_modified") is not False \
            or evidence.get("production_acceptance") != "NOT_RUN" \
            or evidence.get("final_claim_allowed") is not False:
        return ["UBUNTU_HOST_PREFLIGHT_EVIDENCE_CONTRACT"]
    return []


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
    if deployment.get("runtime_definition_status") \
            != "RHEL_AND_UBUNTU_SYSTEMD_CANDIDATES_IMPLEMENTED":
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
    if deployment.get("target_os") != "UBUNTU_24_04_LTS" \
            or deployment.get("container_image") != "NOT_USED" \
            or deployment.get("orchestrator") != "SYSTEMD":
        violations.append("DEPLOYMENT_UBUNTU_SYSTEMD_SELECTION")
    if deployment.get("supported_candidate_operating_systems") \
            != ["UBUNTU_24_04_LTS", "RHEL_FAMILY"]:
        violations.append("DEPLOYMENT_SUPPORTED_OS_SELECTION")

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
    if migration.get("development_rehearsal_status") != "PASS_POSTGRESQL_16_14_NONFINAL" \
            or migration.get("development_rehearsal_evidence") \
            != "assurance/runtime/onsure-postgresql-flyway-rehearsal.v1.json" \
            or migration.get("rhel_production_rehearsal_status") != "NOT_RUN" \
            or migration.get("ubuntu_production_rehearsal_status") != "NOT_RUN":
        violations.append("DATABASE_MIGRATION_REHEARSAL_STATUS")
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
    if product.get("release", {}).get("deployment") \
            != "UBUNTU_SYSTEMD_PRIMARY_RHEL_COMPATIBILITY_CANDIDATE_NONFINAL":
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
        "deploy/rhel/onsure-llm-gateway.service",
        "deploy/rhel/onsure-migrate.service",
        "deploy/rhel/onsure.env.example",
        "deploy/rhel/onsure.sysusers.conf",
        "deploy/rhel/onsure.tmpfiles.conf",
        "deploy/rhel/README.md",
        "deploy/ubuntu/README.md",
        "deploy/ubuntu/deployment-plan.v1.json",
        "deploy/ubuntu/onsure-backup.service",
        "deploy/ubuntu/onsure-backup.timer",
        "deploy/ubuntu/onsure-postgresql-backup",
        "config/database-migration/README.md",
        "config/database-migration/migration-plan.v1.json",
        "docs/architecture/ONSURE_DEPLOYMENT_AND_DB_MIGRATION_DESIGN_v1.md",
        "docs/operations/ONSURE_BUBBLEWRAP_EXECUTION_ENVIRONMENT_v1.md",
        "scripts/onsure_bubblewrap_diagnostics.py",
        "scripts/package_onsure_rhel.sh",
        "scripts/package_onsure_systemd.sh",
        "scripts/package_onsure_ubuntu.sh",
        "modules/onsure-provider-openai/pom.xml",
        "modules/onsure-llm-gateway/pom.xml",
        "modules/onsure-migration-postgresql/pom.xml",
        "config/provider/openai-request.example.json",
        "scripts/rehearse_onsure_postgresql.py",
        "assurance/runtime/onsure-postgresql-flyway-rehearsal.v1.json",
        "scripts/onsure_systemd_security.py",
        "assurance/runtime/onsure-rhel-systemd-security.v1.json",
        "scripts/validate_onsure_rhel_package.py",
        "assurance/runtime/onsure-rhel-package-validation.v1.json",
        "scripts/validate_onsure_ubuntu_package.py",
        "assurance/runtime/onsure-ubuntu-package-validation.v1.json",
        "scripts/onsure_ubuntu_systemd_security.py",
        "assurance/runtime/onsure-ubuntu-systemd-security.v1.json",
        "scripts/onsure_ubuntu_lifecycle.py",
        "assurance/runtime/onsure-ubuntu-lifecycle-rehearsal.v1.json",
        "scripts/rehearse_onsure_vscode_runtime.py",
        "assurance/runtime/onsure-vscode-ubuntu-runtime-rehearsal.v1.json",
        "scripts/onsure_ubuntu_host_preflight.py",
        "assurance/runtime/onsure-ubuntu-host-preflight.v1.json",
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
        violations.extend(validate_ubuntu_candidate())
        violations.extend(validate_postgresql_evidence())
        violations.extend(validate_systemd_evidence())
        violations.extend(validate_ubuntu_systemd_evidence())
        violations.extend(validate_rhel_package_evidence())
        violations.extend(validate_ubuntu_package_evidence())
        violations.extend(validate_ubuntu_lifecycle_evidence())
        violations.extend(validate_vscode_runtime_evidence())
        violations.extend(validate_ubuntu_host_preflight_evidence())
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
