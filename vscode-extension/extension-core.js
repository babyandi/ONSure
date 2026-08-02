'use strict';

const path = require('path');
const { fileURLToPath } = require('url');

const ID_PATTERN = /^[A-Za-z0-9._-]{1,128}$/;
const EXISTING_REGISTRATION_CODES = Object.freeze(new Set([
  'WORKSPACE_EXISTS', 'PROJECT_EXISTS', 'TARGET_EXISTS'
]));

class LocalApiError extends Error {
  constructor(code, message, status) {
    super(message || code || `Local API returned ${status}.`);
    this.name = 'LocalApiError';
    this.code = code || 'LOCAL_API_ERROR';
    this.status = status;
  }
}

function normalizeBaseUrl(value) {
  let parsed;
  try { parsed = new URL(String(value || '')); }
  catch { throw new Error('ONSure Local API must use an explicit loopback HTTP URL.'); }
  const hosts = new Set(['127.0.0.1', 'localhost', '[::1]']);
  const port = Number(parsed.port);
  if (parsed.protocol !== 'http:' || !hosts.has(parsed.hostname)
      || !Number.isInteger(port) || port < 1024 || port > 65535
      || parsed.username || parsed.password || parsed.search || parsed.hash
      || (parsed.pathname !== '' && parsed.pathname !== '/')) {
    throw new Error('ONSure Local API must use an explicit loopback HTTP URL.');
  }
  return `${parsed.protocol}//${parsed.host}`;
}

function requireId(value, label) {
  if (!ID_PATTERN.test(String(value || ''))) throw new Error(`${label} is invalid.`);
  return String(value);
}

function normalizedRoot(value) {
  let candidate = value;
  if (typeof candidate === 'string' && candidate.startsWith('file:')) {
    try { candidate = fileURLToPath(candidate); }
    catch { throw new Error('Workspace root file URI is invalid.'); }
  }
  if (!candidate || !path.isAbsolute(candidate)) throw new Error('Workspace root must be absolute.');
  return path.resolve(candidate);
}

function registrationRequests(identity) {
  const sourceRoot = normalizedRoot(identity.sourceRoot);
  const workspaceId = requireId(identity.workspaceId, 'Workspace ID');
  const projectId = requireId(identity.projectId, 'Project ID');
  const targetId = requireId(identity.targetId, 'Target ID');
  const targetType = String(identity.targetType || 'GENERAL_SOFTWARE');
  if (!['GENERAL_SOFTWARE', 'AI_APPLICATION'].includes(targetType)) {
    throw new Error('Target type is invalid.');
  }
  return [
    ['project.register-workspace', {
      workspace_id: workspaceId,
      workspace_name: String(identity.workspaceName || workspaceId)
    }],
    ['project.register', {
      workspace_id: workspaceId,
      project_id: projectId,
      project_name: String(identity.projectName || projectId)
    }],
    ['project.register-target', {
      project_id: projectId,
      target_id: targetId,
      target_name: String(identity.targetName || targetId),
      target_type: targetType,
      source_root: sourceRoot
    }]
  ];
}

function learnRequest(identity) {
  return {
    project_id: requireId(identity.projectId, 'Project ID'),
    target_id: requireId(identity.targetId, 'Target ID'),
    program_id: requireId(identity.targetId, 'Target ID')
  };
}

function validationRequest(identity) {
  return {
    project_id: requireId(identity.projectId, 'Project ID'),
    target_id: requireId(identity.targetId, 'Target ID')
  };
}

function snapshotRequest(identity) {
  return {
    project_id: requireId(identity.projectId, 'Project ID'),
    target_id: requireId(identity.targetId, 'Target ID')
  };
}

function requireSnapshotBinding(response, identity) {
  const snapshot = response?.snapshot;
  if (!snapshot || snapshot.contract !== 'ONSURE_LOCAL_WORKSPACE_SNAPSHOT_V1'
      || snapshot.project_id !== identity.projectId || snapshot.target_id !== identity.targetId) {
    throw new Error('Local API snapshot is not bound to the registered workspace identity.');
  }
  const registered = snapshot.registered_target;
  const target = registered?.target;
  const actualRoot = target?.sourceRoot ?? target?.source_root;
  if (!target || normalizedRoot(actualRoot) !== normalizedRoot(identity.sourceRoot)) {
    throw new Error('Local API snapshot target does not match the active workspace.');
  }
  return snapshot;
}

function requireAbsolute(value, label) {
  try { return normalizedRoot(value); }
  catch { throw new Error(`${label} must be an absolute local path.`); }
}

function patchApplyRequest(workspaceRoot, runRoot, approvalReceiptFile) {
  const run = requireAbsolute(runRoot, 'Run root');
  return {
    repository_root: requireAbsolute(workspaceRoot, 'Workspace root'),
    patch_plan_file: path.join(run, 'patch-plan.json'),
    approval_receipt_file: requireAbsolute(approvalReceiptFile, 'Patch approval receipt')
  };
}

function gitCommitRequest(value) {
  const message = String(value.commitMessage || '');
  if (!message || message.length > 200 || /[\r\n]/.test(message)) {
    throw new Error('Commit message must contain 1-200 characters on one line.');
  }
  return {
    worktree_root: requireAbsolute(value.worktreeRoot, 'Approved worktree'),
    patch_apply_receipt_file: requireAbsolute(value.patchReceipt, 'Patch receipt'),
    improvement_proof_file: requireAbsolute(value.improvementProof, 'Improvement proof'),
    delivery_approval_file: requireAbsolute(value.deliveryApproval, 'Delivery approval'),
    commit_message: message
  };
}

function gitDraftPrRequest(value) {
  const base = requireBranch(value.baseBranch, 'Base branch');
  const title = String(value.title || '');
  if (!title || title.length > 250 || /[\r\n]/.test(title)) {
    throw new Error('Draft PR title must contain 1-250 characters on one line.');
  }
  return {
    worktree_root: requireAbsolute(value.worktreeRoot, 'Approved worktree'),
    change_set_file: requireAbsolute(value.changeSet, 'Change set'),
    delivery_approval_file: requireAbsolute(value.deliveryApproval, 'Delivery approval'),
    base_branch: base,
    title,
    body_file: requireAbsolute(value.bodyFile, 'Draft PR body')
  };
}

function requireBranch(value, label) {
  const branch = String(value || '');
  if (!/^[A-Za-z0-9][A-Za-z0-9._/-]{0,120}$/.test(branch)
      || branch.includes('..') || branch.endsWith('/') || branch.startsWith('-')) {
    throw new Error(`${label} is invalid.`);
  }
  return branch;
}

function row(label, description, icon = 'circle-outline', command, args, children) {
  const result = { label: String(label), description: compact(description), icon };
  if (command) result.command = command;
  if (args) result.args = args;
  if (children?.length) result.children = children;
  return result;
}

function compact(value) {
  const text = String(value ?? 'NOT_AVAILABLE').replace(/\s+/g, ' ').trim();
  return text.length > 180 ? `${text.slice(0, 177)}...` : text;
}

function artifactRow(runRoot, artifact) {
  return row(artifact.name, `${artifact.size_bytes || 0} bytes`, 'json',
    'onsure.openArtifact', [runRoot, artifact.name]);
}

function surfaceRows(viewId, model = {}) {
  const status = model.status || {};
  const snapshot = model.snapshot || {};
  const local = model.local || {};
  if (model.error) return [
    row('Local API', 'Unavailable', 'error', 'onsure.configure'),
    row('Reason', model.error, 'warning')
  ];
  const profile = snapshot.profile?.body || {};
  const plan = snapshot.plan?.body || {};
  const latest = snapshot.latest_run?.state === 'NOT_PRESENT' ? {} : (snapshot.latest_run || {});
  const findings = Array.isArray(latest.findings) ? latest.findings : [];
  const evidence = Array.isArray(latest.evidence) ? latest.evidence : [];
  const artifacts = Array.isArray(latest.artifacts) ? latest.artifacts : [];
  const identity = local.identity;
  switch (viewId) {
    case 'onsure.workspace':
      return [
        row('Runtime', status.state || 'UNKNOWN', 'server-process'),
        row('Work Mode', local.workMode || 'ASK', 'symbol-enum', 'onsure.selectMode'),
        row('Registered Target', identity ? `${identity.projectId}/${identity.targetId}` : 'NOT_REGISTERED',
          identity ? 'workspace-trusted' : 'warning', identity ? undefined : 'onsure.registerWorkspaceTarget'),
        row('Last Workflow', local.lastWorkflow || 'NOT_RUN', 'run-all'),
        row('Assurance', 'SELF_VALIDATION_NONFINAL', 'shield')
      ];
    case 'onsure.profile':
      return snapshot.profile?.state === 'AVAILABLE' ? [
        row('Profile', profile.profile_id || 'UNKNOWN', 'symbol-class', 'onsure.openDocument', [snapshot.profile.path]),
        row('State', profile.state || 'UNKNOWN', 'pulse'),
        row('Purpose', profile.purpose || 'NOT_DISCOVERED', 'comment-discussion'),
        row('Components', (profile.components || []).length, 'symbol-namespace'),
        row('Dependencies', (profile.dependencies || []).length, 'references')
      ] : [row('Program Profile', 'NOT_PRESENT', 'warning', 'onsure.learnProgram')];
    case 'onsure.inventory':
      return [
        row('Components', (profile.components || []).length, 'symbol-namespace'),
        row('Dependencies', (profile.dependencies || []).length, 'references'),
        row('AI Components', (profile.ai_components || []).length, 'hubot'),
        row('Data Flows', (profile.data_flows || []).length, 'type-hierarchy'),
        row('Languages', Object.keys(profile.language_inventory || {}).length, 'code')
      ];
    case 'onsure.requirements':
      return [
        row('Unknowns', (profile.unknowns || []).length, 'question'),
        row('Conflicts', (profile.conflicts || []).length, 'warning'),
        row('Requirements Trace', artifacts.some(value => value.name === 'stage-results.json') ? 'AVAILABLE_IN_RUN' : 'NOT_RUN', 'list-tree')
      ];
    case 'onsure.threats': {
      const severe = findings.filter(value => ['CRITICAL', 'HIGH'].includes(value.severity)).length;
      return [
        row('Critical / High Findings', severe, severe ? 'flame' : 'pass-filled'),
        row('Policy Profile', snapshot.registered_target?.target?.policyProfile || 'UNKNOWN', 'law'),
        row('Network Egress', plan.resource_budget?.network_egress || 'DENY_BY_DEFAULT', 'lock')
      ];
    }
    case 'onsure.plan':
      return snapshot.plan?.state === 'AVAILABLE' ? [
        row('Plan', plan.plan_id || 'UNKNOWN', 'checklist', 'onsure.openDocument', [snapshot.plan.path]),
        row('Approval', plan.approval?.state || 'UNKNOWN', 'verified'),
        row('Risk', `${plan.risk?.level || 'UNKNOWN'} / ${plan.risk?.score ?? '?'}`, 'graph'),
        row('Allowed Actions', (plan.allowed_actions || []).length, 'key'),
        row('Approved Plan', snapshot.approved_plan?.state || 'NOT_PRESENT', 'shield')
      ] : [row('Execution Plan', 'NOT_PRESENT', 'warning', 'onsure.generatePlan')];
    case 'onsure.runs':
      return (snapshot.runs || []).length ? snapshot.runs.map(run => row(
        run.job_id || run.run_id, `${run.decision} / ${run.job_status}`, 'run-all', undefined, undefined,
        (run.artifacts || []).map(value => artifactRow(run.run_root, value))))
        : [row('Validation Runs', 'NOT_RUN', 'beaker', 'onsure.runValidation')];
    case 'onsure.findings':
      return findings.length ? findings.map(value => row(
        value.title || value.findingId || 'Finding', `${value.severity || 'UNKNOWN'} / ${value.status || 'UNKNOWN'}`,
        ['CRITICAL', 'HIGH'].includes(value.severity) ? 'error' : 'warning',
        'onsure.openArtifact', [latest.run_root, 'findings.json']))
        : [row('Findings', latest.run_root ? '0' : 'NOT_RUN', 'search')];
    case 'onsure.improvement': {
      const patch = artifacts.find(value => value.name === 'patch-plan.json');
      return [
        row('Patch Plan', patch ? 'AVAILABLE' : 'NOT_PRESENT', 'diff', patch ? 'onsure.openArtifact' : undefined,
          patch ? [latest.run_root, patch.name] : undefined),
        row('Apply Approved Patch', local.patchReceipt ? 'APPLIED' : 'SIGNED_APPROVAL_REQUIRED', 'tools', 'onsure.applyApprovedPatch'),
        row('Improvement Proof', local.improvementProof ? 'AVAILABLE' : 'NOT_RUN', 'verified-filled', 'onsure.proveImprovement')
      ];
    }
    case 'onsure.evidence':
      return evidence.length ? evidence.map(value => row(
        value.evidenceId || 'Evidence', `${value.type || 'UNKNOWN'} / ${value.subject || 'UNKNOWN'}`,
        'verified', 'onsure.openArtifact', [latest.run_root, 'evidence.json']))
        : [row('Evidence', latest.run_root ? '0' : 'NOT_RUN', 'verified')];
    case 'onsure.git':
      return [
        row('Approved Worktree', local.worktreeRoot || 'NOT_PRESENT', 'repo'),
        row('Change Set', local.changeSet || 'NOT_RUN', 'git-commit', 'onsure.gitCommit'),
        row('Draft PR Receipt', local.draftPrReceipt || 'NOT_RUN', 'git-pull-request', 'onsure.gitDraftPr'),
        row('Merge', 'PROHIBITED', 'lock')
      ];
    case 'onsure.approvals':
      return [
        row('Execution Plan', plan.approval?.state || 'NOT_PRESENT', 'verified'),
        row('Approved Plan Artifact', snapshot.approved_plan?.state || 'NOT_PRESENT', 'file-symlink-file'),
        row('Patch Approval', local.patchApproval || 'NOT_PRESENT', 'diff'),
        row('Git Delivery Approval', local.deliveryApproval || 'NOT_PRESENT', 'git-commit')
      ];
    case 'onsure.runtime':
      return [
        row('Local API', status.state || 'UNKNOWN', 'server-process'),
        row('Validation Store Revision', snapshot.validation_store?.body?.revision || 0, 'database'),
        row('Run Count', snapshot.run_count || 0, 'run-all'),
        row('Paid Service', plan.resource_budget?.paid_service_allowed ?? false, 'credit-card'),
        row('Network', plan.resource_budget?.network_egress || 'DENY_BY_DEFAULT', 'globe')
      ];
    case 'onsure.admin':
      return [
        row('Workspace', identity?.workspaceId || 'NOT_REGISTERED', 'workspace-trusted'),
        row('Project', identity?.projectId || 'NOT_REGISTERED', 'project'),
        row('Target', identity?.targetId || 'NOT_REGISTERED', 'target'),
        row('Independent OTester', status.independent_otester || 'NOT_RUN', 'shield'),
        row('Independent OAudit', status.independent_oaudit || 'NOT_RUN', 'verified')
      ];
    default:
      return [row('Surface', 'NOT_IMPLEMENTED', 'warning')];
  }
}

function requireWorkflowBinding(response, operation) {
  if (!response || !response.workflow || response.workflow.operation !== operation) {
    throw new Error('Local API workflow response is not bound to the requested operation.');
  }
  return response.workflow;
}

function verifiedIdentity(workflow, expected) {
  const registered = workflow?.result?.registered_target;
  const target = registered?.target;
  if (!registered || !target) throw new Error('Registered target response is incomplete.');
  const actualProject = registered.projectId ?? registered.project_id;
  const actualTarget = target.targetId ?? target.target_id;
  const actualType = target.targetType ?? target.target_type;
  const actualRoot = target.sourceRoot ?? target.source_root;
  if (actualProject !== expected.projectId || actualTarget !== expected.targetId
      || actualType !== expected.targetType
      || normalizedRoot(actualRoot) !== normalizedRoot(expected.sourceRoot)) {
    throw new Error('Registered target does not match the active workspace identity.');
  }
  return Object.freeze({
    workspaceId: expected.workspaceId,
    projectId: expected.projectId,
    targetId: expected.targetId,
    targetType: expected.targetType,
    sourceRoot: normalizedRoot(expected.sourceRoot)
  });
}

function identityForWorkspace(stored, workspaceRoot) {
  if (!stored || normalizedRoot(stored.sourceRoot) !== normalizedRoot(workspaceRoot)) {
    throw new Error('Register the active workspace and target with ONSure first.');
  }
  requireId(stored.projectId, 'Project ID');
  requireId(stored.targetId, 'Target ID');
  return stored;
}

function isExistingRegistration(error) {
  return error instanceof LocalApiError && EXISTING_REGISTRATION_CODES.has(error.code);
}

module.exports = {
  ID_PATTERN,
  LocalApiError,
  normalizeBaseUrl,
  registrationRequests,
  learnRequest,
  validationRequest,
  snapshotRequest,
  requireSnapshotBinding,
  patchApplyRequest,
  gitCommitRequest,
  gitDraftPrRequest,
  surfaceRows,
  requireWorkflowBinding,
  verifiedIdentity,
  identityForWorkspace,
  isExistingRegistration
};
