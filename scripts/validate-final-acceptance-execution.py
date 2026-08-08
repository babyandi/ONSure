#!/usr/bin/env python3
from __future__ import annotations

import copy
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
NUMBERED = re.compile(r"^\d+\.\s+(.+)$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def extract(document: str, anchor: str, style: str) -> list[str]:
    lines = document.splitlines()
    start = next((i for i, line in enumerate(lines) if line.strip() == anchor), -1)
    if start < 0:
        raise ValueError(f"SECTION_MISSING:{anchor}")
    values: list[str] = []
    started = False
    for raw in lines[start + 1:]:
        line = raw.strip()
        if line.startswith("## ") and started:
            break
        if style == "BULLET" and line.startswith("- "):
            started = True
            values.append(line[2:].strip())
        elif style == "NUMBERED":
            match = NUMBERED.match(line)
            if match:
                started = True
                values.append(match.group(1).strip())
        elif started and line and not line.startswith(("### ", "- ")):
            break
    return values


def expand_cases(root: pathlib.Path, registry: dict, contract: dict) -> list[dict]:
    groups = {item["id"]: item for item in contract.get("groups", [])}
    cases: list[dict] = []
    for source in registry.get("sources", []):
        source_id = source["id"]
        if source_id not in groups:
            raise ValueError(f"EXECUTION_GROUP_MISSING:{source_id}")
        document = (root / source["document"]).read_text(encoding="utf-8")
        values = extract(document, source["section_anchor"], source["item_style"])
        group = groups[source_id]
        for index, criterion in enumerate(values, start=1):
            source_sha = hashlib.sha256(criterion.encode("utf-8")).hexdigest()
            cases.append({
                "case_id": f"{source_id}-{index:02d}",
                "source_sha256": source_sha,
                "executor": group["executor"],
                "oracle": group["oracle"],
                "negative_oracle": contract["negative_oracle"],
                "evidence_contract": contract["evidence_contract"],
                "receipt_contract": contract["receipt_contract"],
            })
    return cases


def validate(root: pathlib.Path, registry: dict, contract: dict) -> tuple[list[str], list[dict]]:
    errors: list[str] = []
    if contract.get("contract") != "ONSURE_FINAL_ACCEPTANCE_EXECUTION_V1":
        errors.append("EXECUTION_CONTRACT_INVALID")
    if contract.get("source_registry") != "contracts/final-acceptance-source-registry.v1.json":
        errors.append("EXECUTION_SOURCE_REGISTRY_INVALID")
    source_ids = [item.get("id") for item in registry.get("sources", [])]
    group_ids = [item.get("id") for item in contract.get("groups", [])]
    if len(group_ids) != len(set(group_ids)) or set(group_ids) != set(source_ids):
        errors.append("EXECUTION_GROUP_SET_MISMATCH")
    if contract.get("runtime_execution") != "NOT_RUN":
        errors.append("EXECUTION_STATE_OVERCLAIMED")
    if contract.get("final_claim_allowed") is not False:
        errors.append("EXECUTION_FINAL_CLAIM_UNSAFE")
    try:
        cases = expand_cases(root, registry, contract)
    except (KeyError, OSError, ValueError) as failure:
        return sorted(set(errors + [str(failure)])), []
    case_ids = [item["case_id"] for item in cases]
    if len(case_ids) != len(set(case_ids)):
        errors.append("EXECUTION_CASE_ID_DUPLICATE")
    if len(cases) != registry.get("total_expected_items"):
        errors.append("EXECUTION_CASE_TOTAL_MISMATCH")
    required = set(contract.get("required_case_fields", []))
    for case in cases:
        if set(case) != required:
            errors.append(f"EXECUTION_CASE_FIELDS_INVALID:{case.get('case_id')}")
        if not SHA256.fullmatch(case.get("source_sha256", "")):
            errors.append(f"EXECUTION_SOURCE_SHA_INVALID:{case.get('case_id')}")
        for field in ("executor", "oracle", "negative_oracle", "evidence_contract", "receipt_contract"):
            if not case.get(field):
                errors.append(f"EXECUTION_CASE_VALUE_MISSING:{case.get('case_id')}:{field}")
    return sorted(set(errors)), cases


def self_test(root: pathlib.Path, registry: dict, contract: dict) -> list[str]:
    missed: list[str] = []
    mutations = [
        ("group removed", lambda value: value["groups"].pop(), "EXECUTION_GROUP_SET_MISMATCH"),
        ("duplicate group", lambda value: value["groups"].append(copy.deepcopy(value["groups"][0])), "EXECUTION_GROUP_SET_MISMATCH"),
        ("state overclaim", lambda value: value.update(runtime_execution="PASS"), "EXECUTION_STATE_OVERCLAIMED"),
        ("final overclaim", lambda value: value.update(final_claim_allowed=True), "EXECUTION_FINAL_CLAIM_UNSAFE"),
        ("executor missing", lambda value: value["groups"][0].update(executor=""), "EXECUTION_CASE_VALUE_MISSING"),
    ]
    for name, mutate, expected in mutations:
        candidate = copy.deepcopy(contract)
        mutate(candidate)
        violations, _ = validate(root, registry, candidate)
        if not any(item.startswith(expected) for item in violations):
            missed.append(f"EXECUTION_SELF_TEST_MISSED:{name}:{expected}")
    return missed


def main() -> int:
    registry = load(ROOT / "contracts/final-acceptance-source-registry.v1.json")
    contract = load(ROOT / "contracts/final-acceptance-execution.v1.json")
    errors, cases = validate(ROOT, registry, contract)
    self_errors = self_test(ROOT, registry, contract)
    body = {
        "contract": "ONSURE_FINAL_ACCEPTANCE_EXECUTION_REPORT_V1",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "registered_cases": len(cases),
        "case_manifest_sha256": hashlib.sha256(
            json.dumps(cases, sort_keys=True, separators=(",", ":")).encode()
        ).hexdigest(),
        "errors": errors,
        "self_test_errors": self_errors,
        "runtime_execution": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(body, ensure_ascii=False, indent=2, sort_keys=True))
    return 0 if body["decision"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
