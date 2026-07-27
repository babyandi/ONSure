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

    def test_unapproved_second_workflow_is_detected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            workflow_root = root / ".github" / "workflows"
            workflow_root.mkdir(parents=True)
            safe = safe_workflow()
            (workflow_root / "onsure-pr-validation.yml").write_text(safe, encoding="utf-8")
            (workflow_root / "autofix.yml").write_text(safe, encoding="utf-8")
            with mock.patch.object(MODULE, "ROOT", root), mock.patch.object(
                MODULE, "WORKFLOW_ROOT", workflow_root
            ):
                errors = MODULE.validate()
            self.assertIn("UNAPPROVED_WORKFLOW_PRESENT:autofix.yml", errors)


if __name__ == "__main__":
    unittest.main()
