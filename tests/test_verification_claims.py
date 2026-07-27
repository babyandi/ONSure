from __future__ import annotations

import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_verification_claims", ROOT / "scripts" / "validate-verification-claims.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


def safe_status() -> dict:
    return {
        "assessment_source_ref": "main",
        "runtime_source_commit": None,
        "runtime_source_binding_state": "PENDING_ONE_SHOT_RECEIPT",
        "active_remediation_issues": [20],
        "historical_ci_self_validation": {
            "binding_scope": "PREVIOUS_PR_HEAD_ONLY",
            "current_source_bound": False,
        },
        "design_coverage": {"source_bound_receipt": "EXTERNAL_CI_QUERY_REQUIRED"},
        "product_process_lineage": {"source_bound_receipt": "EXTERNAL_CI_QUERY_REQUIRED"},
        "sandbox_attack_tests": {
            "state": "PARTIAL_1_OF_2_CURRENT_HEAD_CI_REQUIRED",
            "verified_count": 1,
            "required_count": 2,
            "unverified": ["CROSS_TENANT_READ"],
        },
    }


def safe_sandbox() -> dict:
    return {
        "required_attack_fixtures": ["NETWORK_EGRESS", "CROSS_TENANT_READ"],
        "verified_attack_fixtures": ["NETWORK_EGRESS"],
        "unverified_attack_fixtures": ["CROSS_TENANT_READ"],
    }


def safe_workflow() -> str:
    return """on:
  push:
    branches:
      - main
      - 'feature/**'
      - 'audit/**'
jobs:
  static:
    steps:
      - run: python scripts/validate-verification-claims.py
"""


class VerificationClaimFailureInjectionTest(unittest.TestCase):
    def run_case(self, status: dict | None = None, sandbox: dict | None = None,
                 workflow: str | None = None) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / "status").mkdir(parents=True)
            (root / "contracts").mkdir(parents=True)
            (root / ".github/workflows").mkdir(parents=True)
            (root / "scripts").mkdir(parents=True)
            (root / "src/test/java/io/onsure/platform").mkdir(parents=True)
            (root / "status/verification-status.v1.json").write_text(
                json.dumps(status or safe_status()), encoding="utf-8"
            )
            (root / "contracts/sandbox-boundary.v1.json").write_text(
                json.dumps(sandbox or safe_sandbox()), encoding="utf-8"
            )
            (root / ".github/workflows/onsure-pr-validation.yml").write_text(
                workflow or safe_workflow(), encoding="utf-8"
            )
            (root / "scripts/test-fixture-sandbox-boundary.sh").write_text(
                "echo ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12\n", encoding="utf-8"
            )
            (root / "src/test/java/io/onsure/platform/AdversarialConcurrencyAndOutputTest.java").write_text(
                "class AdversarialConcurrencyAndOutputTest {}\n", encoding="utf-8"
            )
            with mock.patch.object(MODULE, "ROOT", root):
                return MODULE.validate()

    def test_safe_nonfinal_claim_model_passes(self):
        self.assertEqual([], self.run_case())

    def test_historical_run_cannot_claim_current_source_binding(self):
        status = safe_status()
        status["historical_ci_self_validation"]["current_source_bound"] = True
        self.assertIn("HISTORICAL_CI_FALSELY_BOUND_TO_CURRENT_SOURCE", self.run_case(status=status))

    def test_committed_github_run_cannot_be_current_source_receipt(self):
        status = safe_status()
        status["design_coverage"]["source_bound_receipt"] = "GITHUB_ACTIONS_RUN_123"
        self.assertTrue(any(value.startswith("COMMITTED_DYNAMIC_RECEIPT_OVERCLAIM")
                            for value in self.run_case(status=status)))

    def test_closed_umbrella_issue_cannot_remain_active(self):
        status = safe_status()
        status["active_remediation_issues"] = [20, 23]
        self.assertIn("CLOSED_REMEDIATION_ISSUE_STILL_ACTIVE:23", self.run_case(status=status))

    def test_sandbox_partial_scope_cannot_be_marked_pass(self):
        status = safe_status()
        status["sandbox_attack_tests"]["state"] = "PASS_CI_SELF_VALIDATION_NONFINAL"
        self.assertIn("SANDBOX_PARTIAL_SCOPE_OVERCLAIMED_AS_PASS", self.run_case(status=status))

    def test_sandbox_attack_partition_mismatch_is_detected(self):
        sandbox = safe_sandbox()
        sandbox["unverified_attack_fixtures"] = []
        self.assertTrue(any(value.startswith("SANDBOX_ATTACK_PARTITION_MISMATCH")
                            for value in self.run_case(sandbox=sandbox)))

    def test_main_push_validation_scope_is_required(self):
        workflow = safe_workflow().replace("      - main\n", "")
        self.assertIn("CI_PUSH_SCOPE_MISSING:- main", self.run_case(workflow=workflow))

    def test_claim_gate_must_be_invoked_by_ci(self):
        workflow = safe_workflow().replace(
            "      - run: python scripts/validate-verification-claims.py\n", ""
        )
        self.assertIn("CI_VERIFICATION_CLAIM_GATE_NOT_INVOKED", self.run_case(workflow=workflow))


if __name__ == "__main__":
    unittest.main()
