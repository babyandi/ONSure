#!/usr/bin/env python3
from __future__ import annotations

import datetime as dt
import hashlib
import importlib.util
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
SHA256 = re.compile(r"^[0-9a-f]{64}$")
GIT_COMMIT = re.compile(r"^[0-9a-f]{40,64}$")
RUNTIME_STATES = {"NOT_RUN", "PARTIAL", "COMPLETE"}
RESULT_STATES = {"PASS", "FAIL", "BLOCKED"}
REQUIRED_RESULT_FIELDS = {
    "case_id", "source_sha256", "source_commit", "execution_id", "executor",
    "started_at", "finished_at", "exit_code", "result", "oracle_result",
    "negative_oracle_result", "input_sha256", "output_sha256",
    "environment_sha256", "evidence_sha256", "parent_receipt_sha256",
    "replay_nonce", "receipt_sha256",
}


def load(path: pathlib.Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def acceptance_cases(root: pathlib.Path) -> list[dict]:
    script = root / "scripts/validate-final-acceptance-execution.py"
    spec = importlib.util.spec_from_file_location("final_acceptance_execution", script)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    registry = load(root / "contracts/final-acceptance-source-registry.v1.json")
    contract = load(root / "contracts/final-acceptance-execution.v1.json")
    errors, cases = module.validate(root, registry, contract)
    if errors:
        raise ValueError("CASE_AUTHORITY_INVALID:" + ",".join(errors))
    return cases


def parse_time(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("TIMESTAMP_NOT_UTC")
    return parsed.astimezone(dt.timezone.utc)


def result_receipt_payload(result: dict) -> dict:
    return {key: value for key, value in result.items() if key != "receipt_sha256"}


def validate(root: pathlib.Path, status: dict, cases: list[dict]) -> list[str]:
    errors: list[str] = []
    if status.get("contract") != "ONSURE_ACCEPTANCE_RUNTIME_RESULTS_V1":
        errors.append("RUNTIME_RESULTS_CONTRACT_INVALID")
    if status.get("case_contract") != "contracts/final-acceptance-execution.v1.json":
        errors.append("RUNTIME_CASE_CONTRACT_INVALID")
    state = status.get("runtime_execution")
    if state not in RUNTIME_STATES:
        errors.append("RUNTIME_EXECUTION_STATE_INVALID")
    if status.get("final_claim_allowed") is not False:
        errors.append("RUNTIME_FINAL_CLAIM_UNSAFE")
    executions = status.get("executions")
    if not isinstance(executions, list):
        return sorted(set(errors + ["RUNTIME_EXECUTIONS_INVALID"]))
    case_map = {case["case_id"]: case for case in cases}
    seen_cases: set[str] = set()
    seen_execution_ids: set[str] = set()
    seen_nonces: set[str] = set()
    source_commits: set[str] = set()
    for result in executions:
        if not isinstance(result, dict) or set(result) != REQUIRED_RESULT_FIELDS:
            errors.append("RUNTIME_RESULT_FIELDS_INVALID")
            continue
        case_id = result["case_id"]
        if case_id not in case_map:
            errors.append(f"RUNTIME_CASE_UNKNOWN:{case_id}")
            continue
        if case_id in seen_cases:
            errors.append(f"RUNTIME_CASE_DUPLICATE:{case_id}")
        seen_cases.add(case_id)
        execution_id = result["execution_id"]
        if execution_id in seen_execution_ids:
            errors.append(f"RUNTIME_EXECUTION_ID_REPLAY:{execution_id}")
        seen_execution_ids.add(execution_id)
        nonce = result["replay_nonce"]
        if nonce in seen_nonces:
            errors.append(f"RUNTIME_NONCE_REPLAY:{case_id}")
        seen_nonces.add(nonce)
        expected = case_map[case_id]
        if result["source_sha256"] != expected["source_sha256"]:
            errors.append(f"RUNTIME_SOURCE_MISMATCH:{case_id}")
        if result["executor"] != expected["executor"]:
            errors.append(f"RUNTIME_EXECUTOR_MISMATCH:{case_id}")
        if not GIT_COMMIT.fullmatch(result["source_commit"]):
            errors.append(f"RUNTIME_SOURCE_COMMIT_INVALID:{case_id}")
        else:
            source_commits.add(result["source_commit"])
        for field in (
            "input_sha256", "output_sha256", "environment_sha256",
            "parent_receipt_sha256", "receipt_sha256",
        ):
            if not SHA256.fullmatch(result[field]):
                errors.append(f"RUNTIME_HASH_INVALID:{case_id}:{field}")
        evidence = result["evidence_sha256"]
        if not isinstance(evidence, list) or not evidence or any(
            not isinstance(value, str) or not SHA256.fullmatch(value) for value in evidence
        ):
            errors.append(f"RUNTIME_EVIDENCE_INVALID:{case_id}")
        try:
            if parse_time(result["finished_at"]) < parse_time(result["started_at"]):
                errors.append(f"RUNTIME_TIME_ORDER_INVALID:{case_id}")
        except (TypeError, ValueError):
            errors.append(f"RUNTIME_TIMESTAMP_INVALID:{case_id}")
        if result["result"] not in RESULT_STATES:
            errors.append(f"RUNTIME_RESULT_INVALID:{case_id}")
        if result["result"] == "PASS" and (
            result["exit_code"] != 0
            or result["oracle_result"] != "PASS"
            or result["negative_oracle_result"] != "PASS"
        ):
            errors.append(f"RUNTIME_PASS_WITHOUT_ORACLES:{case_id}")
        receipt = hashlib.sha256(
            json.dumps(
                result_receipt_payload(result), sort_keys=True, separators=(",", ":")
            ).encode("utf-8")
        ).hexdigest()
        if result["receipt_sha256"] != receipt:
            errors.append(f"RUNTIME_RECEIPT_MISMATCH:{case_id}")
    if len(source_commits) > 1:
        errors.append("RUNTIME_SOURCE_COMMIT_DRIFT")
    expected_ids = set(case_map)
    if state == "NOT_RUN" and executions:
        errors.append("RUNTIME_NOT_RUN_WITH_EXECUTIONS")
    if state == "PARTIAL" and (not executions or seen_cases == expected_ids):
        errors.append("RUNTIME_PARTIAL_COUNT_INVALID")
    if state == "COMPLETE" and seen_cases != expected_ids:
        errors.append("RUNTIME_COMPLETE_CASE_SET_MISMATCH")
    if state == "COMPLETE" and any(item.get("result") != "PASS" for item in executions):
        errors.append("RUNTIME_COMPLETE_WITH_NONPASS")
    return sorted(set(errors))


def main() -> int:
    status = load(ROOT / "contracts/acceptance-runtime-results.v1.json")
    try:
        cases = acceptance_cases(ROOT)
        errors = validate(ROOT, status, cases)
    except (OSError, ValueError, KeyError) as failure:
        errors = [str(failure)]
        cases = []
    report = {
        "contract": "ONSURE_ACCEPTANCE_RUNTIME_VALIDATION_REPORT_V1",
        "decision": "PASS_NONFINAL" if not errors else "FAIL",
        "registered_cases": len(cases),
        "executed_cases": len(status.get("executions", [])),
        "runtime_execution": status.get("runtime_execution", "INVALID"),
        "errors": errors,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
