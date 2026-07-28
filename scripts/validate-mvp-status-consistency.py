#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import sys
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]
COUNT_AUTHORITY = "contracts/omission-failure-injection-counts.v1.json"


def load(relative: str):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


def main() -> int:
    errors: list[str] = []
    register = load("status/mvp-acceptance-coverage.v1.json")
    verification = load("status/verification-status.v1.json")
    omission = load("status/omission-detection-status.v1.json")
    remaining = load("status/remaining-work-register.v1.json")
    counts = load(COUNT_AUTHORITY)

    items = register.get("acceptance_items", [])
    ids = [item.get("id") for item in items if isinstance(item, dict)]
    if len(items) != 11 or len(ids) != 11 or len(set(ids)) != 11:
        errors.append("MVP_STATUS_ACCEPTANCE_ITEM_COUNT_OR_DUPLICATE")
    implementation = Counter(item.get("implementation_status") for item in items)
    verification_counts = Counter(item.get("verification_state") for item in items)
    expected_summary = {
        "total": len(items),
        "implemented": implementation.get("IMPLEMENTED", 0),
        "partial": implementation.get("PARTIAL", 0),
        "stub": implementation.get("STUB", 0),
        "design_only": implementation.get("DESIGN_ONLY", 0),
        "verification_not_run": verification_counts.get("NOT_RUN", 0),
    }
    for field, expected in expected_summary.items():
        if register.get("summary", {}).get(field) != expected:
            errors.append(f"MVP_STATUS_REGISTER_SUMMARY_MISMATCH:{field}")

    status = verification.get("mvp_acceptance_coverage", {})
    if status.get("acceptance_item_count") != len(items):
        errors.append("MVP_STATUS_VERIFICATION_ITEM_COUNT_MISMATCH")
    for field in ("implemented", "partial", "stub", "design_only"):
        if status.get(field) != expected_summary[field]:
            errors.append(f"MVP_STATUS_VERIFICATION_SUMMARY_MISMATCH:{field}")
    if status.get("runner") != "python3 scripts/validate-mvp-acceptance-coverage.py --self-test":
        errors.append("MVP_STATUS_RUNNER_MISSING")
    if status.get("two_consecutive_real_repository_runs") != "NOT_RUN":
        errors.append("MVP_STATUS_REPEAT_OVERCLAIMED")
    if status.get("source_bound_receipt") != "LOCAL_RECEIPT_REQUIRED":
        errors.append("MVP_STATUS_RECEIPT_OVERCLAIMED")

    coverage = omission.get("coverage", {})
    if coverage.get("mvp_acceptance_items") != len(items):
        errors.append("MVP_STATUS_OMISSION_COVERAGE_MISMATCH")
    if omission.get("authorities", {}).get("mvp_acceptance_coverage") \
            != "status/mvp-acceptance-coverage.v1.json":
        errors.append("MVP_STATUS_OMISSION_AUTHORITY_MISSING")

    count_values = counts.get("counts", {})
    if count_values.get("mvp_acceptance_cases") != 8:
        errors.append("MVP_STATUS_FAILURE_COUNT_MISMATCH")
    if counts.get("total") != sum(count_values.values()):
        errors.append("MVP_STATUS_FAILURE_TOTAL_MISMATCH")
    additional = omission.get("additional_failure_injection", {})
    if additional.get("mvp_acceptance_cases") != count_values.get("mvp_acceptance_cases"):
        errors.append("MVP_STATUS_OMISSION_FAILURE_COUNT_STALE")
    if additional.get("all_registered_cases") != counts.get("total"):
        errors.append("MVP_STATUS_OMISSION_FAILURE_TOTAL_STALE")
    status_failure = verification.get("omission_failure_injection", {})
    if status_failure.get("mvp_acceptance_cases") != count_values.get("mvp_acceptance_cases"):
        errors.append("MVP_STATUS_VERIFICATION_FAILURE_COUNT_STALE")
    if status_failure.get("all_registered_failure_injections") != counts.get("total"):
        errors.append("MVP_STATUS_VERIFICATION_FAILURE_TOTAL_STALE")

    if remaining.get("mvp_acceptance_authority") != "status/mvp-acceptance-coverage.v1.json":
        errors.append("MVP_STATUS_REMAINING_AUTHORITY_MISSING")
    remaining_ids = {item.get("id") for item in remaining.get("items", [])}
    if "P0-MVP-ACCEPTANCE-COVERAGE" not in remaining_ids:
        errors.append("MVP_STATUS_REMAINING_ITEM_MISSING")
    assurance = register.get("assurance", {})
    if assurance.get("mvp_full_chain") != "NOT_RUN" \
            or assurance.get("two_consecutive_real_repository_runs") != "NOT_RUN" \
            or assurance.get("final_claim_allowed") is not False:
        errors.append("MVP_STATUS_REGISTER_ASSURANCE_UNSAFE")

    report = {
        "contract": "ONSURE_MVP_STATUS_CONSISTENCY_REPORT_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": sorted(set(errors)),
        "acceptance_items": len(items),
        "mvp_acceptance_failure_injections": count_values.get("mvp_acceptance_cases"),
        "mvp_full_chain": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_MVP_STATUS_CONSISTENCY_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_MVP_STATUS_CONSISTENCY_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
