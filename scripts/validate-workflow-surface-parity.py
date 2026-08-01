#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
AUTHORITY = "contracts/workflow-operation-registry.v1.json"
DISPATCHER = "src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java"
CLI = "src/main/java/io/onsure/platform/ONSureCli.java"
API = "src/main/java/io/onsure/platform/LocalAuthenticatedApiServer.java"
VSCODE = "vscode-extension/extension.js"
CASE_PATTERN = re.compile(r'case\s+"([a-z][a-z0-9.-]+)"\s*->')


def load_authority(root: pathlib.Path = ROOT) -> dict:
    return json.loads((root / AUTHORITY).read_text(encoding="utf-8"))


def expected_operations(root: pathlib.Path = ROOT) -> set[str]:
    body = load_authority(root)
    operations = body.get("operations", [])
    if body.get("contract") != "ONSURE_WORKFLOW_OPERATION_REGISTRY_V1":
        raise ValueError("WORKFLOW_AUTHORITY_CONTRACT_INVALID")
    if not isinstance(operations, list) or len(operations) != len(set(operations)):
        raise ValueError("WORKFLOW_AUTHORITY_OPERATION_LIST_INVALID")
    if body.get("operation_count") != len(operations):
        raise ValueError("WORKFLOW_AUTHORITY_COUNT_MISMATCH")
    return set(operations)


def read(root: pathlib.Path, relative: str) -> str:
    path = root / relative
    return path.read_text(encoding="utf-8", errors="strict") if path.is_file() else ""


def validate_texts(
        dispatcher: str, cli: str, api: str, vscode: str,
        expected: set[str]) -> list[str]:
    errors: list[str] = []
    operation_list = CASE_PATTERN.findall(dispatcher)
    operations = set(operation_list)
    if len(operation_list) != len(operations):
        errors.append("WORKFLOW_DISPATCHER_DUPLICATE_OPERATION")
    if operations != expected:
        errors.append(
            "WORKFLOW_OPERATION_SET_MISMATCH:"
            f"missing={sorted(expected-operations)}:"
            f"extra={sorted(operations-expected)}"
        )
    for operation in operations:
        if operation not in dispatcher:
            errors.append(f"WORKFLOW_OPERATION_UNREACHABLE_IN_DISPATCHER:{operation}")
    for token in ('"workflow".equals(args[0])', '.dispatch(operation, request)',
                  'ONSURE_WORKFLOW_COMPLETE_NONFINAL'):
        if token not in cli:
            errors.append(f"WORKFLOW_CLI_GENERIC_ROUTE_MISSING:{token}")
    for token in ('server.createContext("/v1/workflow"', '.dispatch(operation, request)',
                  'authenticated(this::workflow)'):
        if token not in api:
            errors.append(f"WORKFLOW_LOCAL_API_GENERIC_ROUTE_MISSING:{token}")
    for token in ('onsure.runWorkflowRequest', 'envelope.operation',
                  'executeWorkflow(envelope.operation, envelope.request'):
        if token not in vscode:
            errors.append(f"WORKFLOW_VSCODE_GENERIC_ROUTE_MISSING:{token}")
    for token in ('program.learn', 'validation.run'):
        if token not in vscode:
            errors.append(f"WORKFLOW_VSCODE_CORE_COMMAND_MISSING:{token}")
    return sorted(set(errors))


def validate(root: pathlib.Path = ROOT) -> list[str]:
    required = [AUTHORITY, DISPATCHER, CLI, API, VSCODE]
    errors = [f"WORKFLOW_SURFACE_FILE_MISSING:{item}" for item in required
              if not (root / item).is_file()]
    if errors:
        return errors
    try:
        expected = expected_operations(root)
    except ValueError as failure:
        return [str(failure)]
    return validate_texts(*(read(root, item) for item in (DISPATCHER, CLI, API, VSCODE)), expected)


def self_test(root: pathlib.Path = ROOT) -> list[str]:
    expected = expected_operations(root)
    dispatcher = '\n'.join(f'case "{item}" -> handler();' for item in sorted(expected))
    cli = '"workflow".equals(args[0]); dispatcher.dispatch(operation, request); ONSURE_WORKFLOW_COMPLETE_NONFINAL'
    api = 'server.createContext("/v1/workflow", authenticated(this::workflow)); dispatcher.dispatch(operation, request);'
    vscode = 'onsure.runWorkflowRequest; envelope.operation; executeWorkflow(envelope.operation, envelope.request); program.learn; validation.run'
    missed: list[str] = []

    def expect(name: str, values: tuple[str, str, str, str], prefix: str) -> None:
        violations = validate_texts(*values, expected)
        if not any(value.startswith(prefix) for value in violations):
            missed.append(f"WORKFLOW_SURFACE_SELF_TEST_MISSED:{name}:{prefix}:{violations}")

    expect("operation removed", (dispatcher.replace('case "plan.generate" -> handler();\n', ''), cli, api, vscode),
           "WORKFLOW_OPERATION_SET_MISMATCH")
    expect("duplicate operation", (dispatcher + '\ncase "program.learn" -> handler();', cli, api, vscode),
           "WORKFLOW_DISPATCHER_DUPLICATE_OPERATION")
    expect("CLI generic route removed", (dispatcher, cli.replace('.dispatch(operation, request)', ''), api, vscode),
           "WORKFLOW_CLI_GENERIC_ROUTE_MISSING")
    expect("API generic route removed", (dispatcher, cli, api.replace('server.createContext("/v1/workflow"', 'server.createContext("/v1/other"'), vscode),
           "WORKFLOW_LOCAL_API_GENERIC_ROUTE_MISSING")
    expect("VS Code workflow route removed", (dispatcher, cli, api, vscode.replace('envelope.operation', 'fixed.operation')),
           "WORKFLOW_VSCODE_GENERIC_ROUTE_MISSING")
    expect("VS Code validation command removed", (dispatcher, cli, api, vscode.replace('validation.run', 'validation.none')),
           "WORKFLOW_VSCODE_CORE_COMMAND_MISSING")
    return missed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    errors = validate()
    self_errors = self_test() if args.self_test else []
    try:
        operation_count = len(expected_operations())
    except ValueError:
        operation_count = -1
    report = {
        "contract": "ONSURE_WORKFLOW_SURFACE_PARITY_REPORT_V4",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "operation_authority": AUTHORITY,
        "dispatcher_operation_count": operation_count,
        "surfaces": ["CLI", "LOCAL_AUTHENTICATED_API", "VSCODE"],
        "failure_injection_count": 6 if args.self_test else 0,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_WORKFLOW_SURFACE_PARITY_PASS", file=__import__("sys").stderr)
        return 0
    print("ONSURE_WORKFLOW_SURFACE_PARITY_FAIL", file=__import__("sys").stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
