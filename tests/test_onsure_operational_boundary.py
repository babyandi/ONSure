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

    def test_postgresql_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_postgresql_evidence())

    def test_systemd_security_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_systemd_evidence())

    def test_ubuntu_systemd_rehearsal_is_offline_and_digest_bound(self):
        self.assertEqual([], operational.validate_ubuntu_systemd_evidence())

    def test_rhel_package_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_rhel_package_evidence())

    def test_ubuntu_package_rehearsal_is_digest_bound_and_nonproduction(self):
        self.assertEqual([], operational.validate_ubuntu_package_evidence())


if __name__ == "__main__":
    unittest.main()
