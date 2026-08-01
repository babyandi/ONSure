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


LOCAL_GATE = """#!/usr/bin/env bash
set -euo pipefail
python3 scripts/validate-verification-claims.py
bash scripts/test-fixture-sandbox-boundary.sh
mvn -B -ntp -q test
mvn -B -ntp -q -f pom-modular.xml test
EXT_BUILD=vscode-extension-build
printf '%s' '\"github_actions\":\"DISABLED\"'
"""


def populate(root: pathlib.Path) -> pathlib.Path:
    workflow_root = root / ".github" / "workflows"
    workflow_root.mkdir(parents=True)
    (root / "scripts").mkdir(parents=True)
    (root / "status").mkdir(parents=True)
    (root / "scripts/onsure-local-gate.sh").write_text(LOCAL_GATE, encoding="utf-8")
    (root / "scripts/onsure-one-shot.sh").write_text(
        "# --static-only --profile\n", encoding="utf-8"
    )
    (root / "scripts/onsure-final-stage.sh").write_text(
        "# ONSURE_FINAL_STAGE onsure-one-shot.sh\n", encoding="utf-8"
    )
    (root / "scripts/test-fixture-sandbox-boundary.sh").write_text(
        "echo ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12\n", encoding="utf-8"
    )
    for name in (
        "verification-status.v1.json",
        "remaining-work-register.v1.json",
        "omission-detection-status.v1.json",
    ):
        (root / "status" / name).write_text("{}\n", encoding="utf-8")
    return workflow_root


class AutomationBoundaryFailureInjectionTest(unittest.TestCase):
    def run_case(self, mutate=None) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            workflow_root = populate(root)
            if mutate:
                mutate(root, workflow_root)
            with mock.patch.object(MODULE, "ROOT", root), mock.patch.object(
                MODULE, "WORKFLOW_ROOT", workflow_root
            ):
                return MODULE.validate()

    def test_local_only_validation_model_passes(self):
        self.assertEqual([], self.run_case())

    def test_any_github_actions_workflow_is_detected(self):
        errors = self.run_case(
            lambda _root, workflows: (workflows / "build.yml").write_text(
                "on: [push]\njobs: {}\n", encoding="utf-8"
            )
        )
        self.assertIn("GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:build.yml", errors)

    def test_actions_dependency_in_local_runner_is_detected(self):
        def mutate(root, _workflows):
            path = root / "scripts/onsure-local-gate.sh"
            path.write_text(path.read_text() + "\nuses: actions/checkout@v4\n", encoding="utf-8")

        errors = self.run_case(mutate)
        self.assertTrue(any("LOCAL_RUNNER_ACTIONS_DEPENDENCY_FORBIDDEN" in item for item in errors))

    def test_missing_local_gate_is_detected(self):
        errors = self.run_case(
            lambda root, _workflows: (root / "scripts/onsure-local-gate.sh").unlink()
        )
        self.assertIn(
            "LOCAL_VALIDATION_RUNNER_MISSING:scripts/onsure-local-gate.sh", errors
        )

    def test_missing_local_verification_claim_gate_is_detected(self):
        def mutate(root, _workflows):
            path = root / "scripts/onsure-local-gate.sh"
            path.write_text(
                path.read_text().replace("scripts/validate-verification-claims.py", ""),
                encoding="utf-8",
            )

        errors = self.run_case(mutate)
        self.assertTrue(any("LOCAL_VALIDATION_CONTROL_MISSING" in item for item in errors))

    def test_active_status_cannot_require_actions(self):
        def mutate(root, _workflows):
            (root / "status/verification-status.v1.json").write_text(
                '{"current_head_evidence_query":"GITHUB_ACTIONS_COMMIT_OR_PR_RUN"}\n',
                encoding="utf-8",
            )

        errors = self.run_case(mutate)
        self.assertTrue(any("ACTIVE_STATUS_ACTIONS_DEPENDENCY_FORBIDDEN" in item for item in errors))

    def test_stale_sandbox_boundary_count_is_detected(self):
        def mutate(root, _workflows):
            (root / "scripts/test-fixture-sandbox-boundary.sh").write_text(
                "echo ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 7\n", encoding="utf-8"
            )

        errors = self.run_case(mutate)
        self.assertIn("SANDBOX_BOUNDARY_TEST_COUNT_STALE", errors)


if __name__ == "__main__":
    unittest.main()
