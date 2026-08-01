#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import pathlib
import re
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "vscode-extension"
REQUIRED_COMMANDS = {
    "onsure.configure",
    "onsure.selectMode",
    "onsure.refresh",
    "onsure.learnProgram",
    "onsure.runValidation",
    "onsure.runWorkflowRequest",
    "onsure.openLastArtifact",
    "onsure.clearToken",
}
REQUIRED_VIEWS = {
    "onsure.workspace", "onsure.profile", "onsure.inventory", "onsure.requirements",
    "onsure.threats", "onsure.plan", "onsure.runs", "onsure.findings",
    "onsure.improvement", "onsure.evidence", "onsure.git", "onsure.approvals",
    "onsure.runtime", "onsure.admin",
}
REQUIRED_MODES = {"ASK", "PLAN", "ACT", "VERIFY", "IMPROVE", "AUTOPILOT", "AUDIT", "OFFLINE"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-node", action="store_true")
    args = parser.parse_args()
    errors: list[str] = []

    package_file = EXTENSION / "package.json"
    source_file = EXTENSION / "extension.js"
    icon_file = EXTENSION / "media/onsure.svg"
    for path in (package_file, source_file, icon_file, EXTENSION / ".vscodeignore"):
        if not path.is_file():
            errors.append(f"MISSING:{path.relative_to(ROOT)}")
    if errors:
        return finish(errors, "NOT_RUN")

    package = json.loads(package_file.read_text(encoding="utf-8"))
    commands = {item.get("command") for item in package.get("contributes", {}).get("commands", [])}
    if not REQUIRED_COMMANDS.issubset(commands):
        errors.append(f"COMMAND_SET_MISSING:{sorted(REQUIRED_COMMANDS - commands)}")
    activation = set(package.get("activationEvents", []))
    required_activation = {f"onCommand:{command}" for command in REQUIRED_COMMANDS}
    if not required_activation.issubset(activation):
        errors.append(f"ACTIVATION_EVENT_MISSING:{sorted(required_activation - activation)}")
    views = package.get("contributes", {}).get("views", {}).get("onsure", [])
    view_ids = {item.get("id") for item in views}
    if not REQUIRED_VIEWS.issubset(view_ids):
        errors.append(f"ONSURE_VIEW_SET_MISSING:{sorted(REQUIRED_VIEWS - view_ids)}")
    mode_property = (package.get("contributes", {}).get("configuration", {})
                     .get("properties", {}).get("onsure.defaultWorkMode", {}))
    if set(mode_property.get("enum", [])) != REQUIRED_MODES:
        errors.append("ONSURE_WORK_MODE_SET_INVALID")
    if package.get("main") != "./extension.js":
        errors.append("EXTENSION_MAIN_INVALID")
    if package.get("engines", {}).get("vscode") is None:
        errors.append("VSCODE_ENGINE_MISSING")
    if package.get("scripts", {}).get("check") != "node --check extension.js":
        errors.append("EXTENSION_NODE_CHECK_SCRIPT_MISSING")

    source = source_file.read_text(encoding="utf-8")
    for token in (
        "context.secrets.get", "context.secrets.store", "context.secrets.delete",
        "Authorization", "127\\.0\\.0\\.1", "localhost", "/v1/workflow",
        "SELF_VALIDATION_NONFINAL", "independent_otester", "openLastArtifact",
        "runWorkflowRequest", "requireInsideWorkspace", "fs.readFileSync",
        "Workflow request file must be inside the active workspace",
        "WORK_MODES", "VIEW_IDS", "MODE_CHANGE", "onsure.selectMode",
    ):
        if token not in source:
            errors.append(f"SOURCE_TOKEN_MISSING:{token}")
    if re.search(r"https://(?!127\.0\.0\.1|localhost|\[::1\])", source):
        errors.append("NON_LOOPBACK_URL_LITERAL_FOUND")
    if re.search(r"(?:console|output\.appendLine)\([^\n]*(?:token|Authorization)", source, re.IGNORECASE):
        errors.append("TOKEN_LOGGING_RISK")
    if "eval(" in source or "new Function(" in source:
        errors.append("DYNAMIC_JAVASCRIPT_EXECUTION_PROHIBITED")

    node = shutil.which("node")
    node_state = "NOT_RUN"
    if node:
        result = subprocess.run([node, "--check", str(source_file)],
                                text=True, capture_output=True, check=False)
        node_state = "PASS" if result.returncode == 0 else "FAIL"
        if result.returncode != 0:
            errors.append("NODE_SYNTAX_FAIL:" + result.stderr[-500:])
    elif args.require_node:
        errors.append("NODE_REQUIRED_NOT_INSTALLED")

    return finish(errors, node_state)


def finish(errors: list[str], node_state: str) -> int:
    report = {
        "contract": "ONSURE_VSCODE_EXTENSION_STATIC_REPORT_V2",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "manifest_validation": "PASS" if not errors else "FAIL",
        "workflow_dispatcher_binding": "PASS" if not errors else "FAIL",
        "secret_storage": "PASS" if not errors else "FAIL",
        "workspace_path_boundary": "PASS" if not errors else "FAIL",
        "node_syntax": node_state,
        "extension_host_e2e": "NOT_RUN",
        "vsix_package": "NOT_RUN",
        "final_claim_allowed": False,
    }
    print(json.dumps(report, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_VSCODE_EXTENSION_STATIC_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_VSCODE_EXTENSION_STATIC_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
