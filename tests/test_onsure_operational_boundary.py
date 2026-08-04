from __future__ import annotations

import copy
import json
import pathlib
import sys
import unittest

import yaml


ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

import validate_onsure_operational_boundary as operational  # noqa: E402


class ONSureOperationalBoundaryTest(unittest.TestCase):
    def documents(self):
        boundary = json.loads(
            (ROOT / "contracts/onsure-operational-boundary.v1.json").read_text(
                encoding="utf-8"
            )
        )
        product = yaml.safe_load((ROOT / "product.yaml").read_text(encoding="utf-8"))
        obuilder = yaml.safe_load(
            (ROOT / ".obuilder/product-build.yaml").read_text(encoding="utf-8")
        )
        return boundary, product, obuilder

    def test_current_design_boundary_passes_nonfinal(self):
        result = operational.validate()
        self.assertEqual("PASS_NONFINAL", result["decision"])
        self.assertEqual(
            "RHEL_AND_UBUNTU_SYSTEMD_CANDIDATES_IMPLEMENTED",
            result["deployment_runtime_status"],
        )
        boundary, _, _ = self.documents()
        self.assertEqual("UBUNTU_24_04_LTS", boundary["deployment"]["target_os"])
        self.assertEqual("POSTGRESQL_FLYWAY_CANDIDATE_IMPLEMENTED", result["database_migration_component_status"])
        self.assertFalse(result["github_actions_used"])
        self.assertFalse(result["final_claim_allowed"])

    def test_deployment_authority_is_rejected(self):
        boundary, product, obuilder = self.documents()
        changed = copy.deepcopy(boundary)
        changed["deployment"]["deployment_authorized"] = True
        violations = operational.validate_documents(changed, product, obuilder)
        self.assertIn("DEPLOYMENT_AUTHORITY", violations)

    def test_database_tool_selection_drift_is_rejected(self):
        boundary, product, obuilder = self.documents()
        changed = copy.deepcopy(boundary)
        changed["database_migration"]["migration_tool"] = "LIQUIBASE"
        violations = operational.validate_documents(changed, product, obuilder)
        self.assertIn("DATABASE_POSTGRESQL_FLYWAY_SELECTION", violations)

    def test_rhel_systemd_candidate_has_fail_closed_runtime_controls(self):
        self.assertEqual([], operational.validate_rhel_candidate())

    def test_ubuntu_candidate_is_loopback_and_fail_closed(self):
        self.assertEqual([], operational.validate_ubuntu_candidate())

    def test_ubuntu_backup_timer_is_content_free_and_fail_closed(self):
        script = (ROOT / "deploy/ubuntu/onsure-postgresql-backup").read_text(encoding="utf-8")
        self.assertIn("ONSURE_BACKUP_NON_LOOPBACK_DATABASE_DENIED", script)
        self.assertIn("pg_restore --list", script)
        self.assertNotIn("echo ${ONSURE_DB_PASSWORD}", script)

    def test_postgresql_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_postgresql_evidence())

    def test_postgresql_rehearsal_rejects_environment_injection(self):
        evidence = json.loads(
            operational.POSTGRESQL_EVIDENCE.read_text(encoding="utf-8")
        )
        evidence["environment"]["ONSURE_DB_PASSWORD"] = "must-not-be-recorded"
        violations = operational.validate_postgresql_evidence_body(
            evidence, verify_repository=False,
        )
        self.assertIn("POSTGRESQL_EVIDENCE_ENVIRONMENT_FIELDS", violations)
        self.assertIn("POSTGRESQL_EVIDENCE_ENVIRONMENT_DIGEST", violations)

    def test_postgresql_rehearsal_rejects_tool_and_receipt_tampering(self):
        evidence = json.loads(
            operational.POSTGRESQL_EVIDENCE.read_text(encoding="utf-8")
        )
        evidence["environment"]["tools"]["psql"]["sha256"] = "0" * 64
        evidence["receipt_sha256"] = "f" * 64
        violations = operational.validate_postgresql_evidence_body(
            evidence, verify_repository=False,
        )
        self.assertIn("POSTGRESQL_EVIDENCE_ENVIRONMENT_DIGEST", violations)
        self.assertIn("POSTGRESQL_EVIDENCE_RECEIPT_DIGEST", violations)

    def test_postgresql_rehearsal_rejects_dirty_or_reversed_run(self):
        evidence = json.loads(
            operational.POSTGRESQL_EVIDENCE.read_text(encoding="utf-8")
        )
        evidence["source_code_dirty_paths"] = ["modules/changed.java"]
        evidence["completed_at"] = "2000-01-01T00:00:00Z"
        violations = operational.validate_postgresql_evidence_body(
            evidence, verify_repository=False,
        )
        self.assertIn("POSTGRESQL_EVIDENCE_SOURCE_DIRTY", violations)
        self.assertIn("POSTGRESQL_EVIDENCE_TIME_WINDOW", violations)

    def test_sandbox_rehearsal_is_digest_bound_and_fail_closed(self):
        self.assertEqual([], operational.validate_sandbox_evidence())

    def test_universal_java_python_node_and_self_evidence_is_digest_bound(self):
        self.assertEqual([], operational.validate_universal_evidence())

    def test_universal_evidence_rejects_false_pass_and_receipt_tampering(self):
        evidence = json.loads(operational.UNIVERSAL_EVIDENCE.read_text(encoding="utf-8"))
        evidence["runs"][0]["steps"][0]["outcome"] = "FAIL"
        violations = operational.validate_universal_evidence_body(
            evidence, verify_repository=False,
        )
        self.assertIn("UNIVERSAL_EVIDENCE_STEP_BINDING:self", violations)
        self.assertIn("UNIVERSAL_EVIDENCE_RECEIPT_DIGEST", violations)

    def test_sandbox_rehearsal_rejects_image_and_receipt_tampering(self):
        evidence = json.loads(
            operational.SANDBOX_EVIDENCE.read_text(encoding="utf-8")
        )
        evidence["oci"]["image_id"] = "sha256:" + "0" * 64
        violations = operational.validate_sandbox_evidence_body(
            evidence, verify_repository=False,
        )
        self.assertIn("SANDBOX_EVIDENCE_RECEIPT_DIGEST", violations)

    def test_sandbox_rehearsal_rejects_weakened_security_options(self):
        evidence = json.loads(
            operational.SANDBOX_EVIDENCE.read_text(encoding="utf-8")
        )
        evidence["oci"]["docker_security_options"] = ["name=cgroupns"]
        violations = operational.validate_sandbox_evidence_body(
            evidence, verify_repository=False,
        )
        self.assertIn("SANDBOX_EVIDENCE_OCI_SECURITY_OPTIONS", violations)
        self.assertIn("SANDBOX_EVIDENCE_RECEIPT_DIGEST", violations)

    def test_sandbox_rehearsal_rejects_source_binding_tampering(self):
        evidence = json.loads(
            operational.SANDBOX_EVIDENCE.read_text(encoding="utf-8")
        )
        evidence["source_bindings"]["contracts/sandbox-boundary.v1.json"] = "0" * 64
        violations = operational.validate_sandbox_evidence_body(
            evidence, verify_repository=False,
        )
        self.assertIn(
            "SANDBOX_EVIDENCE_SOURCE_BINDING:contracts/sandbox-boundary.v1.json",
            violations,
        )
        self.assertIn("SANDBOX_EVIDENCE_RECEIPT_DIGEST", violations)

    def test_systemd_security_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_systemd_evidence())

    def test_ubuntu_systemd_rehearsal_is_offline_and_digest_bound(self):
        self.assertEqual([], operational.validate_ubuntu_systemd_evidence())

    def test_rhel_package_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_rhel_package_evidence())

    def test_ubuntu_package_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_ubuntu_package_evidence())

    def test_ubuntu_lifecycle_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_ubuntu_lifecycle_evidence())

    def test_vscode_ubuntu_runtime_evidence_is_content_free_and_nonproduction(self):
        self.assertEqual([], operational.validate_vscode_runtime_evidence())

    def test_ubuntu_host_preflight_is_read_only_and_nonproduction(self):
        self.assertEqual([], operational.validate_ubuntu_host_preflight_evidence())

    def test_privileged_policy_observation_keeps_remediation_open(self):
        self.assertEqual([], operational.validate_ubuntu_privileged_policy_evidence())

    def test_apparmor_candidate_is_parse_only_and_not_enforced(self):
        self.assertEqual([], operational.validate_ubuntu_apparmor_evidence())

    def test_ufw_remediation_is_exact_but_not_executed(self):
        self.assertEqual([], operational.validate_ubuntu_network_policy())


if __name__ == "__main__":
    unittest.main()
