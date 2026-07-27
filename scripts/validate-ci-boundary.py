#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKFLOW_ROOT = ROOT / ".github" / "workflows"

REQUIRED_LOCAL_RUNNERS = {
    "scripts/onsure-local-gate.sh": (
        "scripts/validate-verification-claims.py",
        "scripts/test-fixture-sandbox-boundary.sh",
        "mvn -B -ntp -q test",
        "pom-modular.xml",
        "vscode-extension-build",
        '"github_actions": "DISABLED"',
    ),
    "scripts/onsure-one-shot.sh": ("--static-only", "--profile"),
    "scripts/onsure-final-stage.sh": ("ONSURE_FINAL_STAGE", "onsure-one-shot.sh"),
}

FORBIDDEN_ACTIVE_TOKENS = (
    "uses: actions/",
    "github.run_id",
    "github.run_number",
    "github.workflow",
    "GITHUB_ACTIONS_COMMIT_OR_PR_RUN",
    "EXTERNAL_CI_QUERY_REQUIRED",
)


def validate() -> list[str]:
    errors: list[str] = []
    workflows = []
    if WORKFLOW_ROOT.exists():
        workflows = sorted(WORKFLOW_ROOT.glob("*.yml")) + sorted(WORKFLOW_ROOT.glob("*.yaml"))
    for path in workflows:
        errors.append(f"GITHUB_ACTIONS_WORKFLOW_FORBIDDEN:{path.name}")

    for relative, tokens in REQUIRED_LOCAL_RUNNERS.items():
        path = ROOT / relative
        if not path.is_file():
            errors.append(f"LOCAL_VALIDATION_RUNNER_MISSING:{relative}")
            continue
        text = path.read_text(encoding="utf-8", errors="strict")
        for token in tokens:
            if token not in text:
                errors.append(f"LOCAL_VALIDATION_CONTROL_MISSING:{relative}:{token}")
        for token in FORBIDDEN_ACTIVE_TOKENS:
            if token in text:
                errors.append(f"LOCAL_RUNNER_ACTIONS_DEPENDENCY_FORBIDDEN:{relative}:{token}")

    for relative in (
        "status/verification-status.v1.json",
        "status/remaining-work-register.v1.json",
        "status/omission-detection-status.v1.json",
    ):
        path = ROOT / relative
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="strict")
        for token in FORBIDDEN_ACTIVE_TOKENS:
            if token in text:
                errors.append(f"ACTIVE_STATUS_ACTIONS_DEPENDENCY_FORBIDDEN:{relative}:{token}")

    boundary_test = ROOT / "scripts/test-fixture-sandbox-boundary.sh"
    if not boundary_test.is_file():
        errors.append("SANDBOX_BOUNDARY_TEST_MISSING")
    elif "ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12" not in boundary_test.read_text(encoding="utf-8"):
        errors.append("SANDBOX_BOUNDARY_TEST_COUNT_STALE")
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    report = {
        "contract": "ONSURE_AUTOMATION_BOUNDARY_REPORT_V4",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "github_actions": "DISABLED_AND_FORBIDDEN",
        "workflow_count": len(list(WORKFLOW_ROOT.glob("*.yml"))) + len(list(WORKFLOW_ROOT.glob("*.yaml")))
        if WORKFLOW_ROOT.exists() else 0,
        "allowed_validation_execution": [
            "LOCAL_STATIC_ONE_SHOT",
            "LOCAL_FULL_GATE",
            "LOCAL_FINAL_STAGE",
        ],
        "mutation_authority": "APPROVED_LOCAL_WORKFLOW_ONLY",
        "sandbox_expected_output_assertions": "REQUIRED",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_AUTOMATION_BOUNDARY_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_AUTOMATION_BOUNDARY_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
