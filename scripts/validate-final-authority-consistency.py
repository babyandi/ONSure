#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def validate(root: pathlib.Path) -> list[str]:
    errors: list[str] = []
    registry = load(root / "contracts/final-acceptance-source-registry.v1.json")
    acceptance = load(root / "status/final-acceptance-coverage.v1.json")
    requirements = load(root / "status/final-product-requirement-coverage.v1.json")

    source_total = sum(
        item.get("expected_count", 0)
        for item in registry.get("sources", [])
        if isinstance(item, dict)
    )
    authority_total = registry.get("total_expected_items")
    coverage_total = acceptance.get("summary", {}).get("registered_items")
    group_total = sum(
        item.get("registered_items", 0)
        for item in acceptance.get("groups", [])
        if isinstance(item, dict)
    )
    if not isinstance(authority_total, int) or authority_total <= 0:
        errors.append("FINAL_ACCEPTANCE_AUTHORITY_TOTAL_INVALID")
    if authority_total != source_total:
        errors.append(f"FINAL_ACCEPTANCE_AUTHORITY_SOURCE_SUM_MISMATCH:{authority_total}:{source_total}")
    if authority_total != coverage_total:
        errors.append(f"FINAL_ACCEPTANCE_AUTHORITY_COVERAGE_MISMATCH:{authority_total}:{coverage_total}")
    if authority_total != group_total:
        errors.append(f"FINAL_ACCEPTANCE_AUTHORITY_GROUP_SUM_MISMATCH:{authority_total}:{group_total}")
    if len(requirements.get("requirements", [])) <= 0:
        errors.append("FINAL_REQUIREMENT_AUTHORITY_EMPTY")

    literal_acceptance = re.compile(
        r'"final_acceptance_coverage"\s*:\s*\{\s*"registered"\s*:\s*\d+'
    )
    literal_requirement = re.compile(
        r'"final_product_requirement_coverage"\s*:\s*\{\s*"registered"\s*:\s*\d+'
    )
    for relative in ("scripts/onsure-one-shot.sh", "scripts/onsure-local-gate.sh"):
        body = (root / relative).read_text(encoding="utf-8")
        if literal_acceptance.search(body):
            errors.append(f"FINAL_ACCEPTANCE_RUNNER_HARDCODED:{relative}")
        if literal_requirement.search(body):
            errors.append(f"FINAL_REQUIREMENT_RUNNER_HARDCODED:{relative}")
        if "FINAL_ACCEPTANCE_TOTAL" not in body:
            errors.append(f"FINAL_ACCEPTANCE_AUTHORITY_NOT_CONSUMED:{relative}")
        if "FINAL_REQUIREMENT_TOTAL" not in body:
            errors.append(f"FINAL_REQUIREMENT_AUTHORITY_NOT_CONSUMED:{relative}")
    return sorted(set(errors))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=ROOT)
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    report = {
        "contract": "ONSURE_FINAL_AUTHORITY_CONSISTENCY_REPORT_V1",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_FINAL_AUTHORITY_CONSISTENCY_FAIL", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
