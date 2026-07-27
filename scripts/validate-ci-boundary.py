#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
WORKFLOW_ROOT = ROOT / ".github" / "workflows"

FORBIDDEN_TOKENS = (
    "contents: write",
    "git push",
    "git commit",
    "gh pr merge",
    "gh pr create",
    "pull-requests: write",
    "actions: write",
)

ALLOWED_WORKFLOWS = {"onsure-pr-validation.yml"}


def validate() -> list[str]:
    errors: list[str] = []
    workflows = sorted(WORKFLOW_ROOT.glob("*.yml")) + sorted(WORKFLOW_ROOT.glob("*.yaml"))
    names = {path.name for path in workflows}
    unexpected = sorted(names - ALLOWED_WORKFLOWS)
    for name in unexpected:
        errors.append(f"UNAPPROVED_WORKFLOW_PRESENT:{name}")

    required = WORKFLOW_ROOT / "onsure-pr-validation.yml"
    if not required.is_file():
        errors.append("PR_VALIDATION_WORKFLOW_MISSING")
        return errors

    for path in workflows:
        text = path.read_text(encoding="utf-8", errors="strict")
        lowered = text.lower()
        for token in FORBIDDEN_TOKENS:
            if token in lowered:
                errors.append(f"CI_MUTATION_TOKEN_FORBIDDEN:{path.name}:{token}")
        if re.search(r"(?m)^\s*permissions:\s*$", text) is None:
            errors.append(f"CI_PERMISSIONS_BLOCK_MISSING:{path.name}")
        if "contents: read" not in lowered:
            errors.append(f"CI_CONTENTS_READ_NOT_EXPLICIT:{path.name}")
        if "persist-credentials: false" not in lowered:
            errors.append(f"CI_CHECKOUT_CREDENTIALS_NOT_DISABLED:{path.name}")
        if "workflow_dispatch:" not in text:
            errors.append(f"CI_MANUAL_REPRODUCTION_TRIGGER_MISSING:{path.name}")
        if "set -o pipefail" in text and "set -euo pipefail" not in text:
            errors.append(f"CI_WEAK_FAILURE_PROPAGATION:{path.name}")
        if path.name == "onsure-pr-validation.yml":
            for required_token in (
                "run_step()",
                "scripts/test-fixture-sandbox-boundary.sh",
                "python scripts/validate-verification-claims.py",
                "python -m unittest tests.test_verification_claims -v",
                '[[ "$general_output" == "ALLOW" ]]',
                '[[ "$ai_output" == "ALLOW_TOOL" ]]',
                '[[ "$oruda_output" == "EXPECTED_PASS" ]]',
                "- main",
                "- 'feature/**'",
                "- 'audit/**'",
            ):
                if required_token not in text:
                    errors.append(f"CI_REQUIRED_FAIL_CLOSED_CONTROL_MISSING:{required_token}")

    boundary_test = ROOT / "scripts/test-fixture-sandbox-boundary.sh"
    if not boundary_test.is_file():
        errors.append("SANDBOX_BOUNDARY_TEST_MISSING")
    elif "ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12" not in boundary_test.read_text(encoding="utf-8"):
        errors.append("SANDBOX_BOUNDARY_TEST_COUNT_STALE")
    return sorted(set(errors))


def main() -> int:
    errors = validate()
    report = {
        "contract": "ONSURE_CI_BOUNDARY_REPORT_V3",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "workflow_count": len(list(WORKFLOW_ROOT.glob("*.yml"))) + len(list(WORKFLOW_ROOT.glob("*.yaml"))),
        "mutation_authority": "PROHIBITED",
        "failure_propagation": "FAIL_CLOSED_PER_COMMAND",
        "branch_scope": "MAIN_FEATURE_AUDIT",
        "verification_claim_audit": "REQUIRED",
        "sandbox_expected_output_assertions": "REQUIRED",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_CI_BOUNDARY_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_CI_BOUNDARY_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
