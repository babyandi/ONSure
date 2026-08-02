#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "vscode-extension"
REQUIRED_COMMANDS = {
    "onsure.configure",
    "onsure.registerWorkspaceTarget",
    "onsure.selectMode",
    "onsure.refresh",
    "onsure.learnProgram",
    "onsure.generatePlan",
    "onsure.approvePlan",
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
    parser.add_argument("--require-vsix", action="store_true")
    args = parser.parse_args()
    errors: list[str] = []

    package_file = EXTENSION / "package.json"
    source_file = EXTENSION / "extension.js"
    icon_file = EXTENSION / "media/onsure.svg"
    core_file = EXTENSION / "extension-core.js"
    test_file = EXTENSION / "test" / "extension-core.test.js"
    for path in (package_file, source_file, core_file, test_file, icon_file, EXTENSION / ".vscodeignore"):
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
    if package.get("scripts", {}).get("test") != "node --test test/*.test.js":
        errors.append("EXTENSION_NODE_TEST_SCRIPT_MISSING")
    if package.get("scripts", {}).get("package") != "python3 ../scripts/package_onsure_vsix.py":
        errors.append("EXTENSION_DETERMINISTIC_PACKAGE_SCRIPT_MISSING")

    source = source_file.read_text(encoding="utf-8")
    for token in (
        "context.secrets.get", "context.secrets.store", "context.secrets.delete",
        "Authorization", "127\\.0\\.0\\.1", "localhost", "/v1/workflow",
        "SELF_VALIDATION_NONFINAL", "independent_otester", "openLastArtifact",
        "runWorkflowRequest", "requireInsideWorkspace", "fs.readFileSync",
        "Workflow request file must be inside the active workspace",
        "WORK_MODES", "VIEW_IDS", "MODE_CHANGE", "onsure.selectMode",
        "onsure.registerWorkspaceTarget", "registrationRequests", "verifiedIdentity",
        "identityForWorkspace", "project.read-target",
        "plan.generate", "plan.approve", "approved_execution_plan_file",
        "original_execution_plan_file", "signed_approval_receipt",
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
    node_test_state = "NOT_RUN"
    if node:
        result = subprocess.run([node, "--check", str(source_file)],
                                text=True, capture_output=True, check=False)
        node_state = "PASS" if result.returncode == 0 else "FAIL"
        if result.returncode != 0:
            errors.append("NODE_SYNTAX_FAIL:" + result.stderr[-500:])
        tests = subprocess.run([node, "--test", str(test_file)], cwd=EXTENSION,
                               text=True, capture_output=True, check=False)
        node_test_state = "PASS" if tests.returncode == 0 else "FAIL"
        if tests.returncode != 0:
            errors.append("NODE_TEST_FAIL:" + (tests.stdout + tests.stderr)[-1000:])
    elif args.require_node:
        errors.append("NODE_REQUIRED_NOT_INSTALLED")

    vsix_state, vsix_evidence = validate_vsix(package, errors, args.require_vsix)
    return finish(errors, node_state, node_test_state, vsix_state, vsix_evidence)


def validate_vsix(package: dict, errors: list[str], required: bool,
                  extension: pathlib.Path = EXTENSION, root: pathlib.Path = ROOT) -> tuple[str, dict]:
    candidates = sorted(extension.glob("*.vsix"), key=lambda value: value.stat().st_mtime)
    if not candidates:
        if required:
            errors.append("VSIX_PACKAGE_REQUIRED_NOT_FOUND")
            return "FAIL", {}
        return "NOT_RUN", {}
    package_file = candidates[-1]
    try:
        reported_path = str(package_file.relative_to(root))
    except ValueError:
        reported_path = str(package_file)
    entries: list[tuple[str, bytes]] = []
    evidence = {
        "vsix_path": reported_path,
        "vsix_size_bytes": package_file.stat().st_size,
        "vsix_sha256": hashlib.sha256(package_file.read_bytes()).hexdigest(),
    }
    try:
        with zipfile.ZipFile(package_file) as archive:
            names = set(archive.namelist())
            entries = [(info.filename, archive.read(info.filename)) for info in archive.infolist()]
            required_entries = {
                "extension/package.json", "extension/extension.js",
                "extension/extension-core.js", "extension/readme.md",
                "extension/media/onsure.svg",
            }
            missing = sorted(required_entries - names)
            if missing:
                errors.append(f"VSIX_REQUIRED_ENTRY_MISSING:{missing}")
            if any(name.startswith("extension/node_modules/") for name in names):
                errors.append("VSIX_NODE_MODULES_UNEXPECTED")
            if any(name.startswith("extension/test/") for name in names):
                errors.append("VSIX_TEST_SOURCE_UNEXPECTED")
            manifest = json.loads(archive.read("extension/package.json"))
            if manifest.get("name") != package.get("name") or manifest.get("version") != package.get("version"):
                errors.append("VSIX_MANIFEST_IDENTITY_MISMATCH")
    except (OSError, zipfile.BadZipFile, KeyError, json.JSONDecodeError) as failure:
        errors.append(f"VSIX_PACKAGE_INVALID:{failure.__class__.__name__}")
    if entries:
        digest = hashlib.sha256()
        for name, data in sorted(entries):
            encoded = name.encode("utf-8")
            digest.update(len(encoded).to_bytes(8, "big"))
            digest.update(encoded)
            digest.update(len(data).to_bytes(8, "big"))
            digest.update(hashlib.sha256(data).digest())
        evidence["vsix_content_sha256"] = digest.hexdigest()
    return ("PASS" if not any(value.startswith("VSIX_") for value in errors) else "FAIL"), evidence


def finish(errors: list[str], node_state: str, node_test_state: str = "NOT_RUN",
           vsix_state: str = "NOT_RUN", vsix_evidence: dict | None = None) -> int:
    report = {
        "contract": "ONSURE_VSCODE_EXTENSION_STATIC_REPORT_V2",
        "decision": "PASS" if not errors else "FAIL",
        "errors": errors,
        "manifest_validation": "PASS" if not errors else "FAIL",
        "workflow_dispatcher_binding": "PASS" if not errors else "FAIL",
        "secret_storage": "PASS" if not errors else "FAIL",
        "workspace_path_boundary": "PASS" if not errors else "FAIL",
        "node_syntax": node_state,
        "node_tests": node_test_state,
        "extension_host_e2e": "NOT_RUN",
        "vsix_package": vsix_state,
        "final_claim_allowed": False,
    }
    report.update(vsix_evidence or {})
    print(json.dumps(report, indent=2, sort_keys=True))
    if errors:
        print("ONSURE_VSCODE_EXTENSION_STATIC_FAIL", file=sys.stderr)
        return 1
    print("ONSURE_VSCODE_EXTENSION_STATIC_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
