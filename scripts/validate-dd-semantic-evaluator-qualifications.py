#!/usr/bin/env python3
import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

EXPECTED = {f"DD-{i:03d}" for i in range(1, 41)}
QUALIFIED = "QUALIFIED_NONFINAL"


def load(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def parse_dt(value: str):
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    return datetime.fromisoformat(value)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--registry", default="contracts/dd-semantic-evaluator-registry.candidate.v1.json")
    ap.add_argument("--status", default="contracts/dd-semantic-evaluator-qualification-status.candidate.v1.json")
    ap.add_argument("--receipts-dir", default="receipts/dd-semantic-evaluator-qualification")
    ap.add_argument("--require-all-qualified", action="store_true")
    args = ap.parse_args()

    registry = load(Path(args.registry))
    status = load(Path(args.status))
    errors = []

    reg_rows = registry.get("rows", [])
    reg_dd = {r.get("dd") for r in reg_rows}
    if reg_dd != EXPECTED:
        errors.append(f"registry DD population mismatch missing={sorted(EXPECTED-reg_dd)} extra={sorted(reg_dd-EXPECTED)}")
    if registry.get("final_claim_allowed") is not False:
        errors.append("registry final_claim_allowed must be false")

    rows = status.get("rows", [])
    status_dd = {r.get("dd_id") for r in rows}
    if status_dd != EXPECTED:
        errors.append(f"status DD population mismatch missing={sorted(EXPECTED-status_dd)} extra={sorted(status_dd-EXPECTED)}")
    if len(rows) != 40:
        errors.append(f"status rows must be exactly 40, got {len(rows)}")

    receipts_dir = Path(args.receipts_dir)
    now = datetime.now(timezone.utc)
    qualified = 0

    for row in rows:
        dd = row.get("dd_id")
        state = row.get("qualification_state")
        receipt_ref = row.get("qualification_receipt_ref")

        if state not in {"NOT_IMPLEMENTED", "IMPLEMENTED_UNQUALIFIED", "QUALIFICATION_HOLD", "QUALIFIED_NONFINAL"}:
            errors.append(f"{dd}: invalid qualification_state={state}")
            continue

        if state != QUALIFIED:
            if receipt_ref:
                errors.append(f"{dd}: non-qualified row must not present authoritative qualification_receipt_ref")
            continue

        qualified += 1
        if not receipt_ref:
            errors.append(f"{dd}: QUALIFIED_NONFINAL requires qualification_receipt_ref")
            continue

        receipt_path = Path(receipt_ref)
        if not receipt_path.is_absolute() and not receipt_path.exists():
            receipt_path = receipts_dir / Path(receipt_ref).name
        if not receipt_path.exists():
            errors.append(f"{dd}: receipt not found: {receipt_ref}")
            continue

        try:
            receipt = load(receipt_path)
        except Exception as exc:
            errors.append(f"{dd}: receipt unreadable: {exc}")
            continue

        if receipt.get("contract") != "ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_V1":
            errors.append(f"{dd}: wrong receipt contract")
        if receipt.get("dd_id") != dd:
            errors.append(f"{dd}: receipt dd_id mismatch")
        if receipt.get("decision") != QUALIFIED:
            errors.append(f"{dd}: receipt decision must be QUALIFIED_NONFINAL")
        if receipt.get("final_claim_allowed") is not False:
            errors.append(f"{dd}: receipt final_claim_allowed must be false")

        att = receipt.get("independence_attestation") or {}
        for key in ("independent_from_evaluator_authoring", "independent_from_target_claim_author", "common_control_disclosed"):
            if att.get(key) is not True:
                errors.append(f"{dd}: independence_attestation.{key} must be true")

        for klass in ("positive", "negative", "recovery", "adversarial"):
            fr = (receipt.get("fixture_results") or {}).get(klass) or {}
            executed = fr.get("executed_count", 0)
            passed = fr.get("passed_count", 0)
            failed = fr.get("failed_count", 0)
            evidence = fr.get("evidence_refs") or []
            if executed < 1:
                errors.append(f"{dd}: {klass} fixture executed_count must be >=1")
            if passed + failed != executed:
                errors.append(f"{dd}: {klass} fixture counts inconsistent")
            if failed != 0 or passed != executed:
                errors.append(f"{dd}: {klass} fixture qualification requires all executed fixtures pass")
            if not evidence:
                errors.append(f"{dd}: {klass} fixture evidence_refs required")

        if not receipt.get("positive_oracle_refs"):
            errors.append(f"{dd}: positive_oracle_refs required")
        if not receipt.get("policy_authority_digests"):
            errors.append(f"{dd}: policy_authority_digests required")

        try:
            qualified_at = parse_dt(receipt["qualified_at"])
            expires_at = parse_dt(receipt["expires_at"])
            if expires_at <= qualified_at:
                errors.append(f"{dd}: expires_at must be after qualified_at")
            if expires_at <= now:
                errors.append(f"{dd}: qualification receipt expired")
        except Exception as exc:
            errors.append(f"{dd}: invalid qualification timestamps: {exc}")

    summary = status.get("summary") or {}
    if summary.get("dd_count") != 40:
        errors.append("status summary.dd_count must be 40")
    if summary.get("qualified_nonfinal_count") != qualified:
        errors.append(f"status summary.qualified_nonfinal_count={summary.get('qualified_nonfinal_count')} actual={qualified}")
    if status.get("final_claim_allowed") is not False:
        errors.append("status final_claim_allowed must be false")

    if args.require_all_qualified and qualified != 40:
        errors.append(f"all-qualified gate requires 40/40; current={qualified}/40")

    result = {
        "contract": "ONSURE_DD_SEMANTIC_EVALUATOR_QUALIFICATION_VALIDATION_V1",
        "dd_count": 40,
        "qualified_nonfinal_count": qualified,
        "require_all_qualified": args.require_all_qualified,
        "errors": errors,
        "verdict": "PASS_NONFINAL" if not errors else "HOLD",
        "final_claim_allowed": False,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if not errors else 2


if __name__ == "__main__":
    sys.exit(main())
