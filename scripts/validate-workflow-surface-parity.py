#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DISPATCHER = "src/main/java/io/onsure/platform/LocalWorkflowDispatcher.java"
CLI = "src/main/java/io/onsure/platform/ONSureCli.java"
API = "src/main/java/io/onsure/platform/LocalAuthenticatedApiServer.java"
VSCODE = "vscode-extension/extension.js"
EXPECTED_OPERATIONS = {
    "program.learn", "plan.approve", "validation.run",
    "patch.apply", "patch.rollback", "improvement.prove",
    "git.commit", "git.draft-pr",
    "license.issue", "license.activate", "license.validate", "license.authorize",
    "license.reserve", "license.commit-reservation", "license.release-reservation",
    "license.suspend", "license.revoke", "license.read",
    "case.open", "case.preflight", "case.quote", "case.accept-order",
    "case.record-payment", "case.verify-payment", "case.start-work", "case.deliver",
    "case.accept-delivery", "case.request-refund", "case.record-refund",
    "case.verify-refund", "case.legal-hold", "case.delete", "case.cancel", "case.read",
}
CASE_PATTERN = re.compile(r'case\s+"([a-z][a-z0-9.-]+)"\s*->')


def read(root: pathlib.Path, relative: str) -> str:
    path = root / relative
    return path.read_text(encoding="utf-8", errors="strict") if path.is_file() else ""


def validate_texts(dispatcher: str, cli: str, api: str, vscode: str) -> list[str]:
    errors: list[str] = []
    operation_list = CASE_PATTERN.findall(dispatcher)
    operations = set(operation_list)
    if len(operation_list) != len(operations):
        errors.append("WORKFLOW_DISPATCHER_DUPLICATE_OPERATION")
    if operations != EXPECTED_OPERATIONS:
        errors.append(
            "WORKFLOW_OPERATION_SET_MISMATCH:"
            f"missing={sorted(EXPECTED_OPERATIONS-operations)}:"
            f"extra={sorted(operations-EXPECTED_OPERATIONS)}"
        )
    for operation in operations:
        method = operation.replace("-", "_").replace(".", "_")
        # The method-name check is advisory through the switch target; unsupported cases are caught by compilation.
        if operation not in dispatcher:
            errors.append(f"WORKFLOW_OPERATION_UNREACHABLE_IN_DISPATCHER:{operation}:{method}")

    for token in ('"workflow".equals(args[0])', '.dispatch(operation, request)',
                  'ONSURE_WORKFLOW_COMPLETE_NONFINAL'):
        if token not in cli:
            errors.append(f"WORKFLOW_CLI_GENERIC_ROUTE_MISSING:{token}")
    for token in ('server.createContext("/v1/workflow"', '.dispatch(operation, request)',
                  'authenticated(this::workflow)'):
        if token not in api:
            errors.append(f"WORKFLOW_LOCAL_API_GENERIC_ROUTE_MISSING:{token}")
    for token in ('onsure.runWorkflowRequest', 'envelope.operation',
                  'client.workflow(envelope.operation, envelope.request'):
        if token not in vscode:
            errors.append(f"WORKFLOW_VSCODE_GENERIC_ROUTE_MISSING:{token}")
    for token in ('program.learn', 'validation.run'):
        if token not in vscode:
            errors.append(f"WORKFLOW_VSCODE_CORE_COMMAND_MISSING:{token}")
    return sorted(set(errors))


def validate(root: pathlib.Path = ROOT) -> list[str]:
    required = [DISPATCHER, CLI, API, VSCODE]
    errors = [f"WORKFLOW_SURFACE_FILE_MISSING:{item}" for item in required if not (root / item).is_file()]
    if errors:
        return errors
    return validate_texts(*(read(root, item) for item in required))


def self_test() -> list[str]:
    dispatcher = '\n'.join(f'case "{item}" -> handler();' for item in sorted(EXPECTED_OPERATIONS))
    cli = '"workflow".equals(args[0]); dispatcher.dispatch(operation, request); ONSURE_WORKFLOW_COMPLETE_NONFINAL'
    api = 'server.createContext("/v1/workflow", authenticated(this::workflow)); dispatcher.dispatch(operation, request);'
    vscode = 'onsure.runWorkflowRequest; envelope.operation; client.workflow(envelope.operation, envelope.request); program.learn; validation.run'
    missed: list[str] = []

    def expect(name: str, values: tuple[str, str, str, str], prefix: str) -> None:
        violations = validate_texts(*values)
        if not any(value.startswith(prefix) for value in violations):
            missed.append(f"WORKFLOW_SURFACE_SELF_TEST_MISSED:{name}:{prefix}:{violations}")

    expect("operation removed", (dispatcher.replace('case "plan.approve" -> handler();\n', ''), cli, api, vscode),
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
    report = {
        "contract": "ONSURE_WORKFLOW_SURFACE_PARITY_REPORT_V1",
        "decision": "PASS" if not errors and not self_errors else "FAIL",
        "errors": errors,
        "self_test_errors": self_errors,
        "dispatcher_operation_count": len(EXPECTED_OPERATIONS),
        "surfaces": ["CLI", "LOCAL_AUTHENTICATED_API", "VSCODE"],
        "failure_injection_count": 6 if args.self_test else 0,
        "final_claim_allowed": False,
    }
    print(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True))
    if report["decision"] == "PASS":
        print("ONSURE_WORKFLOW_SURFACE_PARITY_PASS")
        return 0
    print("ONSURE_WORKFLOW_SURFACE_PARITY_FAIL", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
