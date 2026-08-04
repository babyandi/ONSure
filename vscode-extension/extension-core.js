'use strict';

const path = require('path');
const { fileURLToPath } = require('url');
const { createHash } = require('crypto');

const ID_PATTERN = /^[A-Za-z0-9._-]{1,128}$/;
const EXISTING_REGISTRATION_CODES = Object.freeze(new Set([
  'WORKSPACE_EXISTS', 'PROJECT_EXISTS', 'TARGET_EXISTS'
]));
const WORK_MODES = Object.freeze(['ASK', 'PLAN', 'ACT', 'VERIFY', 'IMPROVE', 'AUTOPILOT', 'AUDIT', 'OFFLINE']);
const MODE_CAPABILITIES = Object.freeze({
  ASK: Object.freeze(['READ', 'REGISTER']),
  PLAN: Object.freeze(['READ', 'REGISTER', 'LEARN', 'PLAN']),
  ACT: Object.freeze(['READ', 'REGISTER', 'LEARN', 'PLAN', 'VERIFY', 'IMPROVE', 'BUSINESS']),
  VERIFY: Object.freeze(['READ', 'VERIFY']),
  IMPROVE: Object.freeze(['READ', 'VERIFY', 'IMPROVE', 'DELIVER']),
  AUTOPILOT: Object.freeze(['READ', 'AUTOPILOT_CONTROL']),
  AUDIT: Object.freeze(['READ', 'VERIFY']),
  OFFLINE: Object.freeze(['READ', 'REGISTER', 'LEARN', 'PLAN', 'VERIFY'])
});
const OPERATION_CAPABILITIES = Object.freeze({
  'project.register-workspace': 'REGISTER',
  'project.register': 'REGISTER',
  'project.register-target': 'REGISTER',
  'project.read-target': 'READ',
  'project.list-targets': 'READ',
  'program.learn': 'LEARN',
  'plan.generate': 'PLAN',
  'plan.approve': 'PLAN',
  'validation.run': 'VERIFY',
  'validation.resume': 'VERIFY',
  'knowledge.anonymize': 'VERIFY',
  'patch.verify-approval': 'IMPROVE',
  'patch.apply': 'IMPROVE',
  'patch.rollback': 'IMPROVE',
  'improvement.prove': 'IMPROVE',
  'git.commit': 'DELIVER',
  'git.draft-pr': 'DELIVER',
  'license.issue': 'BUSINESS',
  'license.activate': 'BUSINESS',
  'license.validate': 'VERIFY',
  'license.authorize': 'VERIFY',
  'license.reserve': 'BUSINESS',
  'license.commit-reservation': 'BUSINESS',
  'license.release-reservation': 'BUSINESS',
  'license.suspend': 'BUSINESS',
  'license.revoke': 'BUSINESS',
  'license.read': 'READ',
  'case.open': 'BUSINESS',
  'case.preflight': 'VERIFY',
  'case.quote': 'BUSINESS',
  'case.accept-order': 'BUSINESS',
  'case.record-payment': 'BUSINESS',
  'case.verify-payment': 'VERIFY',
  'case.start-work': 'BUSINESS',
  'case.deliver': 'BUSINESS',
  'case.accept-delivery': 'BUSINESS',
  'case.request-refund': 'BUSINESS',
  'case.record-refund': 'BUSINESS',
  'case.verify-refund': 'VERIFY',
  'case.legal-hold': 'BUSINESS',
  'case.delete': 'BUSINESS',
  'case.cancel': 'BUSINESS',
  'case.read': 'READ'
});

function requireWorkMode(value) {
  const mode = String(value || '').toUpperCase();
  if (!WORK_MODES.includes(mode)) throw new Error('ONSure work mode is invalid.');
  return mode;
}

function modePolicy(value) {
  const mode = requireWorkMode(value);
  return Object.freeze({
    mode,
    capabilities: MODE_CAPABILITIES[mode],
    externalNetworkAllowed: !['OFFLINE', 'ASK', 'PLAN', 'VERIFY', 'AUTOPILOT', 'AUDIT'].includes(mode),
    deliveryAllowed: MODE_CAPABILITIES[mode].includes('DELIVER'),
    sourceMutationAllowed: ['ACT', 'IMPROVE'].includes(mode),
    finalClaimAllowed: false,
    mergeAllowed: false
  });
}

function requireModeCapability(modeValue, capability) {
  const policy = modePolicy(modeValue);
  if (!policy.capabilities.includes(capability)) {
    throw new Error(`ONSURE_MODE_CAPABILITY_DENIED:${policy.mode}:${capability}`);
  }
  return policy;
}

function workflowCapability(operation) {
  const capability = OPERATION_CAPABILITIES[String(operation || '')];
  if (!capability) throw new Error(`ONSURE_WORKFLOW_OPERATION_NOT_MODE_CLASSIFIED:${operation}`);
  return capability;
}

function requireModeWorkflow(mode, operation) {
  return requireModeCapability(mode, workflowCapability(operation));
}

function conversationResponse(modeValue, promptValue, model = {}) {
  const mode = requireWorkMode(modeValue);
  if (!['ASK', 'PLAN'].includes(mode)) {
    throw new Error('ONSURE_CONVERSATION_MODE_REQUIRES_ASK_OR_PLAN');
  }
  const prompt = String(promptValue || '').trim();
  if (!prompt || prompt.length > 4000 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(prompt)) {
    throw new Error('ONSURE conversation prompt must contain 1-4000 safe text characters.');
  }
  const snapshot = model.snapshot || {};
  const local = model.local || {};
  const profile = snapshot.profile || {};
  const plan = snapshot.plan || {};
  const latest = snapshot.latest_run || {};
  const evidence = [];
  if (profile.path) evidence.push(profile.path);
  if (plan.path) evidence.push(plan.path);
  if (latest.run_root) evidence.push(latest.run_root);
  const identity = local.identity || {};
  const lines = [
    `# ONSure ${mode} response`, '',
    `- Prompt: ${prompt}`,
    `- Project: ${identity.projectId || snapshot.project_id || 'NOT_REGISTERED'}`,
    `- Target: ${identity.targetId || snapshot.target_id || 'NOT_REGISTERED'}`,
    `- Program profile: ${profile.state || 'NOT_RUN'}`,
    `- Execution plan: ${plan.state || plan.body?.approval?.state || 'NOT_RUN'}`,
    `- Latest validation: ${latest.decision || 'NOT_RUN'}`,
    `- Open findings: ${latest.finding_count ?? 'NOT_RUN'}`,
    '',
  ];
  if (mode === 'ASK') {
    lines.push('## Evidence-bound answer', '');
    lines.push(latest.run_root
      ? 'The current answer is limited to the registered snapshot and latest persisted validation run.'
      : 'No persisted validation run is available, so factual conclusions about program quality are NOT_RUN.');
    lines.push('', 'This response is read-only, workspace-local, and never a final assurance decision.');
  } else {
    const steps = [];
    if (!identity.projectId && !snapshot.project_id) steps.push('Register the active workspace, project, and target.');
    if (!profile.path) steps.push('Learn and review a candidate program profile.');
    if (!plan.path) steps.push('Generate a risk- and resource-bounded execution plan.');
    if (plan.body?.approval?.state !== 'APPROVED') steps.push('Obtain and verify explicit plan approval.');
    if (!latest.run_root) steps.push('Run validation after approval and preserve evidence receipts.');
    if (latest.finding_count > 0) steps.push('Review findings, RCA, and approval-required remediation hunks.');
    steps.push('Run independent verification and audit before any release claim.');
    lines.push('## Proposed non-executing plan', '');
    steps.forEach((step, index) => lines.push(`${index + 1}. ${step}`));
    lines.push('', 'No step was executed by this PLAN response.');
  }
  return Object.freeze({
    contract: 'ONSURE_LOCAL_CONVERSATION_RESPONSE_V1',
    mode,
    prompt_sha256: createHash('sha256').update(Buffer.from(prompt, 'utf8')).digest('hex'),
    response_markdown: lines.join('\n'),
    evidence_references: Object.freeze([...new Set(evidence)]),
    deterministic_local_response: true,
    provider_invoked: false,
    external_network_allowed: false,
    source_mutation_allowed: false,
    final_claim_allowed: false
  });
}

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

function universalValidationRequest(identity, runId, runRoot, environmentProfileFile) {
  const request = {
    ...validationRequest(identity),
    validation_mode: 'UNIVERSAL',
    run_id: requireId(runId, 'Run ID'),
    run_root: String(runRoot || '')
  };
  if (!request.run_root) throw new Error('Run root is required.');
  if (environmentProfileFile) request.environment_profile_file = String(environmentProfileFile);
  return request;
}

function workflowRunRoot(result) {
  const candidate = result?.run_root || result?.run?.runRoot;
  return typeof candidate === 'string' && candidate.length ? candidate : undefined;
}

function snapshotRequest(identity) {
  return {
    project_id: requireId(identity.projectId, 'Project ID'),
    target_id: requireId(identity.targetId, 'Target ID')
  };
}

function autopilotControlRequest(action) {
  const value = String(action || '').toUpperCase();
  if (!['PAUSE', 'RESUME', 'CANCEL'].includes(value)) {
    throw new Error('Autopilot control action is invalid.');
  }
  return { action: value };
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

function patchApplyRequest(workspaceRoot, runRoot, approvalRequestFile, approvalReceiptFile) {
  const run = requireAbsolute(runRoot, 'Run root');
  return {
    repository_root: requireAbsolute(workspaceRoot, 'Workspace root'),
    patch_plan_file: path.join(run, 'patch-plan.json'),
    approval_request_file: requireAbsolute(approvalRequestFile, 'Patch approval request'),
    approval_receipt_file: requireAbsolute(approvalReceiptFile, 'Patch approval receipt')
  };
}

function patchReview(plan, artifact, runRoot) {
  if (!plan || plan.contract !== 'ONSURE_PATCH_PLAN_V1'
      || !artifact || artifact.name !== 'patch-plan.json'
      || !/^[0-9a-f]{64}$/.test(String(artifact.sha256 || ''))) {
    throw new Error('Patch plan artifact is invalid or unbound.');
  }
  if (!plan.patch_plan_id || !plan.target_id
      || plan.default_approval !== 'DENY' || plan.worktree_required !== true
      || plan.direct_main_write_allowed !== false || plan.force_push_allowed !== false
      || plan.merge_allowed !== false || plan.final_claim_allowed !== false) {
    throw new Error('Patch plan safety boundary is invalid.');
  }
  if (!Array.isArray(plan.hunks) || plan.hunks.length < 1 || plan.hunks.length > 200) {
    throw new Error('Patch plan must contain 1-200 reviewable hunks.');
  }
  const ids = new Set();
  const hunks = plan.hunks.map(value => {
    const hunk = { ...value };
    if (!ID_PATTERN.test(String(hunk.hunk_id || '')) || ids.has(hunk.hunk_id)
        || !ID_PATTERN.test(String(hunk.finding_id || ''))
        || hunk.occurrence !== 1 || hunk.change_class !== 'APPROVAL_REQUIRED'
        || hunk.approval_state !== 'PENDING') {
      throw new Error('Patch plan hunk identity or approval state is invalid.');
    }
    ids.add(hunk.hunk_id);
    hunk.relative_path = requireRelativePatchPath(hunk.relative_path);
    for (const field of ['preimage_sha256']) {
      if (!/^[0-9a-f]{64}$/.test(String(hunk[field] || ''))) {
        throw new Error(`Patch hunk ${field} is invalid.`);
      }
    }
    for (const field of ['match_text', 'replacement_text']) {
      if (typeof hunk[field] !== 'string' || !hunk[field] || hunk[field].length > 65536) {
        throw new Error(`Patch hunk ${field} is invalid.`);
      }
    }
    return Object.freeze(hunk);
  });
  const root = requireAbsolute(runRoot, 'Run root');
  return Object.freeze({
    runRoot: root,
    planFile: path.join(root, 'patch-plan.json'),
    planFileSha256: artifact.sha256,
    patchPlanId: String(plan.patch_plan_id),
    targetId: String(plan.target_id),
    hunks: Object.freeze(hunks)
  });
}

function requireRelativePatchPath(value) {
  const candidate = String(value || '');
  const normalized = path.posix.normalize(candidate);
  if (!candidate || candidate.includes('\\') || candidate.includes('\0')
      || path.posix.isAbsolute(candidate) || normalized !== candidate
      || normalized === '..' || normalized.startsWith('../')) {
    throw new Error('Patch hunk path must be a normalized workspace-relative POSIX path.');
  }
  return candidate;
}

function previewHunk(source, hunk) {
  if (typeof source !== 'string') throw new Error('Patch preview source must be UTF-8 text.');
  const digest = createHash('sha256').update(Buffer.from(source, 'utf8')).digest('hex');
  if (digest !== hunk.preimage_sha256) throw new Error('Patch preview source digest has drifted.');
  const count = source.split(hunk.match_text).length - 1;
  if (count !== 1) throw new Error('Patch preview match is not unique.');
  return source.replace(hunk.match_text, hunk.replacement_text);
}

function hunkApprovalRequest(
  review, selectedHunkIds, branchName, createdAt = new Date().toISOString(), selectionScope = 'HUNK') {
  const branch = requireBranch(branchName, 'Patch branch');
  if (branch.length < 3 || ['main', 'master', 'production', 'release'].includes(branch.toLowerCase())) {
    throw new Error('Patch branch must be a non-protected branch with at least 3 characters.');
  }
  const selected = [...new Set(selectedHunkIds || [])];
  const declared = new Set(review?.hunks?.map(value => value.hunk_id) || []);
  if (!selected.length || selected.length > 200 || selected.some(value => !declared.has(value))) {
    throw new Error('Select one or more declared patch hunks.');
  }
  if (Number.isNaN(Date.parse(createdAt))) throw new Error('Approval request timestamp is invalid.');
  if (!['HUNK', 'FILE'].includes(selectionScope)) throw new Error('Approval selection scope is invalid.');
  const selectedHunks = review.hunks.filter(value => selected.includes(value.hunk_id));
  const selectedFiles = [...new Set(selectedHunks.map(value => value.relative_path))].sort();
  const riskLevel = selected.length > 10 || selectedFiles.length > 5 ? 'HIGH'
    : selected.length > 3 || selectedFiles.length > 1 ? 'MEDIUM' : 'LOW';
  return {
    contract: 'ONSURE_HUNK_APPROVAL_REQUEST_V1',
    request_id: `REQUEST-${review.patchPlanId}`,
    request_state: 'AWAITING_EXTERNAL_SIGNATURE',
    receipt_contract: 'ONSURE_HUNK_APPROVAL_RECEIPT_V1',
    approval_purpose: 'PATCH_HUNK_APPROVAL',
    patch_plan_id: review.patchPlanId,
    patch_plan_file: review.planFile,
    patch_plan_file_sha256: review.planFileSha256,
    selection_scope: selectionScope,
    selected_hunk_ids: selected.sort(),
    selected_files: selectedFiles,
    branch_name: branch,
    risk_preview: {
      classification: 'BOUNDED_CHANGE_SURFACE_CANDIDATE',
      level: riskLevel,
      factors: [`HUNKS_${selected.length}`, `FILES_${selectedFiles.length}`],
      independent_risk_review: 'NOT_RUN'
    },
    impact_scope: {
      file_count: selectedFiles.length,
      hunk_count: selected.length,
      finding_ids: [...new Set(selectedHunks.map(value => value.finding_id))].sort()
    },
    rollback_preview: {
      method: 'PATCH_APPLY_RECEIPT_BACKUP_AND_ISOLATED_GIT_WORKTREE_REMOVAL',
      source_workspace_write_allowed: false,
      automatic_rollback_executed: false
    },
    allow_direct_main_write: false,
    allow_force_push: false,
    allow_merge: false,
    created_at: createdAt,
    signer_must_supply: ['approval_id', 'nonce', 'actor', 'key_id', 'signature_algorithm',
      'signature', 'approved_at', 'expires_at'],
    final_claim_allowed: false
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
  const gateway = model.gateway || { state: 'NOT_CONFIGURED', metrics: {} };
  const gatewayMetrics = gateway.metrics || {};
  if (model.error) return [
    row('Local API', 'Unavailable', 'error', 'onsure.configure'),
    row('LLM Gateway', model.gateway?.state || 'NOT_CONFIGURED',
      model.gateway?.state === 'RUNNING' ? 'server-process' : 'warning'),
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
        row('Review Patch Hunks', patch ? 'DIGEST_BOUND_PREVIEW' : 'NOT_AVAILABLE', 'diff',
          patch ? 'onsure.reviewPatchPlan' : undefined),
        row('External Signing Request', local.patchApprovalRequest || 'NOT_CREATED', 'edit',
          patch ? 'onsure.createHunkApprovalRequest' : undefined),
        row('Whole-file Signing Request', patch ? 'EXPLICIT_FILE_SELECTION' : 'NOT_AVAILABLE', 'files',
          patch ? 'onsure.createFileApprovalRequest' : undefined),
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
        row('Hunk Signing Request', local.patchApprovalRequest || 'NOT_CREATED', 'edit'),
        row('Patch Approval', local.patchApproval || 'NOT_PRESENT', 'diff'),
        row('Git Delivery Approval', local.deliveryApproval || 'NOT_PRESENT', 'git-commit')
      ];
    case 'onsure.runtime':
      {
        const checkpoint = snapshot.autopilot?.checkpoint?.body || {};
        const control = snapshot.autopilot?.control?.body || {};
        const validationCheckpoint = latest.stage_checkpoint?.body || {};
      return [
        row('Local API', status.state || 'UNKNOWN', 'server-process'),
        row('LLM Gateway', gateway.state || 'UNKNOWN', 'server-process'),
        row('Autopilot State', checkpoint.state || 'NOT_STARTED', 'debug-pause'),
        row('Autopilot Control', control.desired_state || 'NOT_REQUESTED', 'settings'),
        row('Validation Stage', validationCheckpoint.current_stage_id || 'NOT_RUN', 'pulse'),
        row('Validation Checkpoint', validationCheckpoint.state || 'NOT_PRESENT', 'save'),
        row('Validation Store Revision', snapshot.validation_store?.body?.revision || 0, 'database'),
        row('Run Count', snapshot.run_count || 0, 'run-all'),
        row('Token Estimate', `${plan.resource_budget?.estimated_input_tokens ?? 0} in / ${plan.resource_budget?.estimated_output_tokens ?? 0} out`, 'symbol-number'),
        row('Data Transfer', plan.resource_budget?.data_transfer_scope || 'NOT_PLANNED', 'arrow-swap'),
        row('External Transfer', `${plan.resource_budget?.estimated_external_transfer_bytes ?? 0} bytes`, 'cloud-upload'),
        row('Paid Service', plan.resource_budget?.paid_service_allowed ?? false, 'credit-card'),
        row('Network', plan.resource_budget?.network_egress || 'DENY_BY_DEFAULT', 'globe'),
        row('Gateway Tokens', gatewayMetrics.total_tokens || 0, 'symbol-number'),
        row('Gateway Cost', `${gatewayMetrics.actual_cost_micros || 0} µ`, 'credit-card'),
        row('Gateway Receipt Chain', gatewayMetrics.chain_valid === true ? 'VALID' : 'NOT_VERIFIED', 'verified')
      ];
      }
    case 'onsure.admin':
      return [
        row('Workspace', identity?.workspaceId || 'NOT_REGISTERED', 'workspace-trusted'),
        row('Project', identity?.projectId || 'NOT_REGISTERED', 'project'),
        row('Target', identity?.targetId || 'NOT_REGISTERED', 'target'),
        row('LLM Gateway', gateway.state || 'NOT_CONFIGURED', 'server-process'),
        row('LLM Requests', `${gatewayMetrics.success_count || 0} success / ${gatewayMetrics.failure_count || 0} failure`, 'pulse'),
        row('LLM Evidence', gatewayMetrics.prompt_or_completion_content_recorded === false
          ? 'CONTENT_FREE' : 'NOT_VERIFIED', 'shield'),
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
  WORK_MODES,
  MODE_CAPABILITIES,
  LocalApiError,
  requireWorkMode,
  modePolicy,
  requireModeCapability,
  workflowCapability,
  requireModeWorkflow,
  conversationResponse,
  normalizeBaseUrl,
  registrationRequests,
  learnRequest,
  validationRequest,
  universalValidationRequest,
  workflowRunRoot,
  snapshotRequest,
  autopilotControlRequest,
  requireSnapshotBinding,
  patchApplyRequest,
  patchReview,
  previewHunk,
  hunkApprovalRequest,
  gitCommitRequest,
  gitDraftPrRequest,
  surfaceRows,
  requireWorkflowBinding,
  verifiedIdentity,
  identityForWorkspace,
  isExistingRegistration
};
