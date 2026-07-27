from __future__ import annotations

import importlib.util
import pathlib
import tempfile
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "validate_ci_boundary", ROOT / "scripts" / "validate-ci-boundary.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


def safe_workflow() -> str:
    return """name: test
on:
  workflow_dispatch:
  push:
    branches:
      - main
      - 'feature/**'
      - 'audit/**'
permissions:
  contents: read
jobs:
  test:
    steps:
      - uses: actions/checkout@v4
        with:
          persist-credentials: false
      - run: |
          set -euo pipefail
          run_step() { "$@"; }
          python scripts/validate-verification-claims.py
          python -m unittest tests.test_verification_claims -v
          bash scripts/test-fixture-sandbox-boundary.sh
          general_output="ALLOW"
          ai_output="ALLOW_TOOL"
          oruda_output="EXPECTED_PASS"
          [[ "$general_output" == "ALLOW" ]]
          [[ "$ai_output" == "ALLOW_TOOL" ]]
          [[ "$oruda_output" == "EXPECTED_PASS" ]]
"""


class CiBoundaryFailureInjectionTest(unittest.TestCase):
    def run_case(self, workflow: str) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            workflow_root = root / ".github" / "workflows"
            workflow_root.mkdir(parents=True)
            (workflow_root / "onsure-pr-validation.yml").write_text(workflow, encoding="utf-8")
            (root / "scripts").mkdir(parents=True)
            (root / "scripts/test-fixture-sandbox-boundary.sh").write_text(
                "echo ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12\n", encoding="utf-8"
            )
            with mock.patch.object(MODULE, "ROOT", root), mock.patch.object(
                MODULE, "WORKFLOW_ROOT", workflow_root
            ):
                return MODULE.validate()

    def test_read_only_fail_closed_workflow_passes(self):
        self.assertEqual([], self.run_case(safe_workflow()))

    def test_contents_write_is_detected(self):
        errors = self.run_case(safe_workflow().replace("contents: read", "contents: write"))
        self.assertTrue(any(value.startswith("CI_MUTATION_TOKEN_FORBIDDEN") for value in errors))

    def test_push_command_is_detected(self):
        errors = self.run_case(safe_workflow() + "      - run: git push origin HEAD:main\n")
        self.assertTrue(any("git push" in value for value in errors))

    def test_persisted_checkout_credentials_are_detected(self):
        errors = self.run_case(safe_workflow().replace("          persist-credentials: false\n", ""))
        self.assertIn("CI_CHECKOUT_CREDENTIALS_NOT_DISABLED:onsure-pr-validation.yml", errors)

    def test_weak_pipefail_only_block_is_detected(self):
        errors = self.run_case(safe_workflow().replace("set -euo pipefail", "set -o pipefail"))
        self.assertIn("CI_WEAK_FAILURE_PROPAGATION:onsure-pr-validation.yml", errors)

    def test_missing_sandbox_expected_output_assertion_is_detected(self):
        errors = self.run_case(
            safe_workflow().replace('          [[ "$ai_output" == "ALLOW_TOOL" ]]\n', "")
        )
        self.assertTrue(any("CI_REQUIRED_FAIL_CLOSED_CONTROL_MISSING" in value for value in errors))

    def test_main_push_scope_is_detected_when_missing(self):
        errors = self.run_case(safe_workflow().replace("      - main\n", ""))
        self.assertIn("CI_REQUIRED_FAIL_CLOSED_CONTROL_MISSING:- main", errors)

    def test_verification_claim_gate_is_detected_when_missing(self):
        errors = self.run_case(
            safe_workflow().replace("          python scripts/validate-verification-claims.py\n", "")
        )
        self.assertIn(
            "CI_REQUIRED_FAIL_CLOSED_CONTROL_MISSING:python scripts/validate-verification-claims.py",
            errors,
        )

    def test_stale_sandbox_boundary_count_is_detected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            workflow_root = root / ".github" / "workflows"
            workflow_root.mkdir(parents=True)
            (workflow_root / "onsure-pr-validation.yml").write_text(safe_workflow(), encoding="utf-8")
            (root / "scripts").mkdir(parents=True)
            (root / "scripts/test-fixture-sandbox-boundary.sh").write_text(
                "echo ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 7\n", encoding="utf-8"
            )
            with mock.patch.object(MODULE, "ROOT", root), mock.patch.object(
                MODULE, "WORKFLOW_ROOT", workflow_root
            ):
                errors = MODULE.validate()
            self.assertIn("SANDBOX_BOUNDARY_TEST_COUNT_STALE", errors)

    def test_unapproved_second_workflow_is_detected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            workflow_root = root / ".github" / "workflows"
            workflow_root.mkdir(parents=True)
            safe = safe_workflow()
            (workflow_root / "onsure-pr-validation.yml").write_text(safe, encoding="utf-8")
            (workflow_root / "autofix.yml").write_text(safe, encoding="utf-8")
            (root / "scripts").mkdir(parents=True)
            (root / "scripts/test-fixture-sandbox-boundary.sh").write_text(
                "echo ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12\n", encoding="utf-8"
            )
            with mock.patch.object(MODULE, "ROOT", root), mock.patch.object(
                MODULE, "WORKFLOW_ROOT", workflow_root
            ):
                errors = MODULE.validate()
            self.assertIn("UNAPPROVED_WORKFLOW_PRESENT:autofix.yml", errors)


if __name__ == "__main__":
    unittest.main()
