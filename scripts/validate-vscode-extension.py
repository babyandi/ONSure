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
    "onsure.approvePlan",
    "onsure.applyApprovedPatch",
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
    policy_file = EXTENSION / "work-mode-policy.js"
    view_model_file = EXTENSION / "view-model.js"
    approval_review_file = EXTENSION / "approval-review.js"
    for path in (package_file, source_file, policy_file, view_model_file, approval_review_file,
                 icon_file, EXTENSION / ".vscodeignore"):
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
    required_view_activation = {f"onView:{view_id}" for view_id in REQUIRED_VIEWS}
    if not required_view_activation.issubset(activation):
        errors.append(f"VIEW_ACTIVATION_EVENT_MISSING:{sorted(required_view_activation - activation)}")
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

    source = "\n".join(path.read_text(encoding="utf-8") for path in
                       (source_file, policy_file, view_model_file, approval_review_file))
    for token in (
        "context.secrets.get", "context.secrets.store", "context.secrets.delete",
        "Authorization", "127\\.0\\.0\\.1", "localhost", "/v1/workflow",
        "SELF_VALIDATION_NONFINAL", "independent_otester", "openLastArtifact",
        "runWorkflowRequest", "requireInsideWorkspace", "fs.readFileSync",
        "Workflow request file must be inside the active workspace",
        "WORK_MODES", "VIEW_IDS", "MODE_CHANGE", "onsure.selectMode",
        "authorize(mode, classifyOperation(operation), operation)", "rowsForView",
        "reviewPlanApproval", "reviewHunkApproval", "confirmApprovalReview",
        "Consume Signed Approval", "signature_verification",
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
        behavior_script = r'''
const policy = require('./work-mode-policy');
const views = require('./view-model');
const approval = require('./approval-review');
function check(value, message) { if (!value) throw new Error(message); }
check(policy.WORK_MODES.length === 8, 'MODE_COUNT');
check(!policy.authorize('ASK', 'ACT', 'patch.apply').allowed, 'ASK_MUTATION_ALLOWED');
check(policy.authorize('PLAN', 'PLAN', 'plan.create').allowed, 'PLAN_DENIED');
check(!policy.authorize('PLAN', 'ACT', 'git.commit').allowed, 'PLAN_ACT_ALLOWED');
check(policy.authorize('ACT', 'ACT', 'git.commit').allowed, 'ACT_DENIED');
check(policy.authorize('AUTOPILOT', 'AUTOPILOT', 'job.control').allowed, 'AUTOPILOT_DENIED');
check(!policy.authorize('AUTOPILOT', 'ACT', 'git.merge').allowed, 'AUTOPILOT_MERGE_ALLOWED');
check(!policy.authorize('OFFLINE', 'ACT', 'git.push').allowed, 'OFFLINE_PUSH_ALLOWED');
check(policy.classifyOperation('plan.approve') === 'ACT', 'PLAN_APPROVAL_NOT_ACT');
check(views.VIEW_IDS.length === 14, 'VIEW_COUNT');
const rendered = views.VIEW_IDS.map(id => JSON.stringify(views.rowsForView(id, {status:{}, mode:'ASK'})));
check(new Set(rendered).size === 14, 'VIEW_MODELS_NOT_DISTINCT');
const plan = {contract:'ONSURE_EXECUTION_PLAN_V1', plan_id:'p1', allowed_actions:['A','B']};
const planReceipt = {contract:'ONSURE_EXECUTION_PLAN_APPROVAL_V1', plan_id:'p1',
  approved_actions:['A'], actor:'human', expires_at:'later', allow_final_claim:false,
  allow_merge:false, allow_deploy:false};
check(approval.reviewPlanApproval(plan, planReceipt).scope === 'PARTIAL', 'PLAN_PARTIAL_REVIEW');
let unsafePlanBlocked = false;
try { approval.reviewPlanApproval(plan, {...planReceipt, allow_merge:true}); }
catch { unsafePlanBlocked = true; }
check(unsafePlanBlocked, 'UNSAFE_PLAN_APPROVAL_ALLOWED');
const patch = {contract:'ONSURE_PATCH_PLAN_V1', patch_plan_id:'x',
  hunks:[{hunk_id:'h1',relative_path:'a'},{hunk_id:'h2',relative_path:'b'}],
  preapply_assessment:{risk_level:'HIGH',rollback_preview:{method:'BYTE_EXACT_PREIMAGE_BACKUP'}}};
const hunkReceipt = {contract:'ONSURE_HUNK_APPROVAL_RECEIPT_V1', patch_plan_id:'x',
  approved_hunk_ids:['h2'], actor:'human', branch_name:'fix/x', allow_direct_main_write:false,
  allow_force_push:false, allow_merge:false};
check(approval.reviewHunkApproval(patch, hunkReceipt).scope === 'PARTIAL', 'HUNK_PARTIAL_REVIEW');
'''
        behavior = subprocess.run(
            [node, "-e", behavior_script], cwd=EXTENSION,
            text=True, capture_output=True, check=False)
        if behavior.returncode != 0:
            errors.append("VSCODE_BEHAVIOR_MODEL_FAIL:" + behavior.stderr[-500:])
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
