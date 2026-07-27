#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def load(relative: str) -> dict:
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def validate() -> list[str]:
    errors: list[str] = []
    status = load("status/verification-status.v1.json")
    sandbox = load("contracts/sandbox-boundary.v1.json")
    workflow_path = ROOT / ".github/workflows/onsure-pr-validation.yml"
    workflow = workflow_path.read_text(encoding="utf-8") if workflow_path.is_file() else ""

    if status.get("assessment_source_ref") != "main":
        errors.append("VERIFICATION_ASSESSMENT_SOURCE_NOT_MAIN")
    if status.get("runtime_source_commit") is not None:
        errors.append("STATIC_STATUS_MUST_NOT_EMBED_RUNTIME_SOURCE_COMMIT")
    if status.get("runtime_source_binding_state") != "PENDING_ONE_SHOT_RECEIPT":
        errors.append("CURRENT_SOURCE_ONE_SHOT_BINDING_OVERCLAIMED")
    if 23 in status.get("active_remediation_issues", []):
        errors.append("CLOSED_REMEDIATION_ISSUE_STILL_ACTIVE:23")

    historical = status.get("historical_ci_self_validation", {})
    if historical.get("binding_scope") != "PREVIOUS_PR_HEAD_ONLY":
        errors.append("HISTORICAL_CI_BINDING_SCOPE_INVALID")
    if historical.get("current_source_bound") is not False:
        errors.append("HISTORICAL_CI_FALSELY_BOUND_TO_CURRENT_SOURCE")
    for section in ("design_coverage", "product_process_lineage"):
        receipt = status.get(section, {}).get("source_bound_receipt")
        if receipt != "EXTERNAL_CI_QUERY_REQUIRED":
            errors.append(f"COMMITTED_DYNAMIC_RECEIPT_OVERCLAIM:{section}:{receipt}")

    required = set(sandbox.get("required_attack_fixtures", []))
    verified = set(sandbox.get("verified_attack_fixtures", []))
    unverified = set(sandbox.get("unverified_attack_fixtures", []))
    if verified & unverified:
        errors.append("SANDBOX_ATTACK_VERIFIED_UNVERIFIED_OVERLAP")
    if required != verified | unverified:
        errors.append(
            "SANDBOX_ATTACK_PARTITION_MISMATCH:"
            f"missing={sorted(required - verified - unverified)}:"
            f"extra={sorted((verified | unverified) - required)}"
        )
    sandbox_status = status.get("sandbox_attack_tests", {})
    if sandbox_status.get("verified_count") != len(verified):
        errors.append("SANDBOX_VERIFIED_COUNT_MISMATCH")
    if sandbox_status.get("required_count") != len(required):
        errors.append("SANDBOX_REQUIRED_COUNT_MISMATCH")
    if set(sandbox_status.get("unverified", [])) != unverified:
        errors.append("SANDBOX_UNVERIFIED_SET_MISMATCH")
    state = str(sandbox_status.get("state", ""))
    if unverified and not state.startswith("PARTIAL_"):
        errors.append("SANDBOX_PARTIAL_SCOPE_OVERCLAIMED_AS_PASS")
    if not unverified and not state.startswith("PASS_"):
        errors.append("SANDBOX_FULL_SCOPE_NOT_MARKED_PASS")

    for token in ("- main", "- 'feature/**'", "- 'audit/**'"):
        if token not in workflow:
            errors.append(f"CI_PUSH_SCOPE_MISSING:{token}")
    if "python scripts/validate-verification-claims.py" not in workflow:
        errors.append("CI_VERIFICATION_CLAIM_GATE_NOT_INVOKED")
    if "ONSURE_FIXTURE_SANDBOX_BOUNDARY_PASS 12" not in (
        ROOT / "scripts/test-fixture-sandbox-boundary.sh"
    ).read_text(encoding="utf-8"):
        errors.append("SANDBOX_EXPANDED_BOUNDARY_MARKER_MISSING")
    if not (ROOT / "src/test/java/io/onsure/platform/AdversarialConcurrencyAndOutputTest.java").is_file():
        errors.append("ADVERSARIAL_CONCURRENCY_OUTPUT_TEST_MISSING")

    return sorted(set(errors))


def main() -> int:
    errors = validate()
    report = {
        "contract": "ONSURE_VERIFICATION_CLAIM_AUDIT_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "committed_dynamic_ci_claims": "PROHIBITED",
        "current_head_ci_evidence": "EXTERNAL_QUERY_REQUIRED",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_VERIFICATION_CLAIM_AUDIT_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_VERIFICATION_CLAIM_AUDIT_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
