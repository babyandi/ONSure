'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const { createHash } = require('crypto');
const { pathToFileURL } = require('url');
const {
  LocalApiError,
  normalizeBaseUrl,
  registrationRequests,
  learnRequest,
  validationRequest,
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
} = require('../extension-core');

const root = path.resolve('/tmp/onsure-workspace');
const identity = Object.freeze({
  workspaceId: 'workspace-001',
  projectId: 'project-001',
  targetId: 'target-001',
  targetType: 'GENERAL_SOFTWARE',
  sourceRoot: root
});

test('snapshot and controlled delivery requests stay identity and approval bound', () => {
  assert.deepEqual(snapshotRequest(identity), {
    project_id: 'project-001', target_id: 'target-001'
  });
  assert.deepEqual(autopilotControlRequest('pause'), { action: 'PAUSE' });
  assert.deepEqual(autopilotControlRequest('RESUME'), { action: 'RESUME' });
  assert.throws(() => autopilotControlRequest('kill'), /invalid/);
  const snapshot = {
    contract: 'ONSURE_LOCAL_WORKSPACE_SNAPSHOT_V1',
    project_id: identity.projectId,
    target_id: identity.targetId,
    registered_target: { target: { sourceRoot: pathToFileURL(root).href } }
  };
  assert.equal(requireSnapshotBinding({ snapshot }, identity), snapshot);
  assert.throws(() => requireSnapshotBinding({ snapshot: { ...snapshot, target_id: 'other' } }, identity), /not bound/);
  assert.deepEqual(patchApplyRequest(root, path.join(root, '.onsure/runs/R1'), path.join(root, 'approval.json')), {
    repository_root: root,
    patch_plan_file: path.join(root, '.onsure/runs/R1/patch-plan.json'),
    approval_receipt_file: path.join(root, 'approval.json')
  });
  assert.deepEqual(gitCommitRequest({
    worktreeRoot: path.join(root, '.onsure/worktree'), patchReceipt: path.join(root, 'patch.json'),
    improvementProof: path.join(root, 'proof.json'), deliveryApproval: path.join(root, 'delivery.json'),
    commitMessage: 'fix approved finding'
  }).commit_message, 'fix approved finding');
  assert.deepEqual(gitDraftPrRequest({
    worktreeRoot: path.join(root, '.onsure/worktree'), changeSet: path.join(root, 'change.json'),
    deliveryApproval: path.join(root, 'delivery.json'), baseBranch: 'release/2026.08',
    title: 'Approved improvement', bodyFile: path.join(root, 'body.md')
  }).base_branch, 'release/2026.08');
  assert.throws(() => gitCommitRequest({ ...identity, commitMessage: 'bad\nmessage' }), /Commit message/);
  assert.throws(() => gitDraftPrRequest({
    worktreeRoot: root, changeSet: root, deliveryApproval: root, baseBranch: '../main',
    title: 'Unsafe branch', bodyFile: root
  }), /Base branch/);
});

test('patch preview and signing request remain digest hunk and safety bound', () => {
  const source = 'policy=ALLOW_UNTRUSTED_TOOL\n';
  const hunk = {
    hunk_id: 'HUNK-aaaaaaaaaaaaaaaa', finding_id: 'FINDING-001',
    relative_path: 'src/policy.txt',
    preimage_sha256: createHash('sha256').update(source).digest('hex'),
    match_text: 'ALLOW_UNTRUSTED_TOOL', replacement_text: 'DENY_UNTRUSTED_TOOL',
    occurrence: 1, change_class: 'APPROVAL_REQUIRED', approval_state: 'PENDING'
  };
  const plan = {
    contract: 'ONSURE_PATCH_PLAN_V1', patch_plan_id: 'PATCH-job-001', target_id: 'target-001',
    hunks: [hunk], default_approval: 'DENY', worktree_required: true,
    direct_main_write_allowed: false, force_push_allowed: false, merge_allowed: false,
    final_claim_allowed: false
  };
  const review = patchReview(plan, { name: 'patch-plan.json', sha256: 'a'.repeat(64) },
    path.join(root, '.onsure/validation-data/target-001/JOB-001'));
  assert.equal(previewHunk(source, review.hunks[0]), 'policy=DENY_UNTRUSTED_TOOL\n');
  const request = hunkApprovalRequest(
    review, ['HUNK-aaaaaaaaaaaaaaaa'], 'fix/approved-patch', '2026-08-02T00:00:00Z');
  assert.equal(request.contract, 'ONSURE_HUNK_APPROVAL_REQUEST_V1');
  assert.equal(request.approval_purpose, 'PATCH_HUNK_APPROVAL');
  assert.equal(request.selection_scope, 'HUNK');
  assert.deepEqual(request.selected_hunk_ids, ['HUNK-aaaaaaaaaaaaaaaa']);
  assert.deepEqual(request.selected_files, ['src/policy.txt']);
  assert.equal(request.risk_preview.level, 'LOW');
  assert.equal(request.impact_scope.file_count, 1);
  assert.equal(request.rollback_preview.source_workspace_write_allowed, false);
  assert.equal(request.allow_merge, false);
  const fileRequest = hunkApprovalRequest(review, [hunk.hunk_id], 'fix/file-approved',
    '2026-08-02T00:00:00Z', 'FILE');
  assert.equal(fileRequest.selection_scope, 'FILE');
  assert.throws(() => previewHunk(`${source}drift`, review.hunks[0]), /digest has drifted/);
  assert.throws(() => hunkApprovalRequest(review, ['unknown'], 'fix/branch'), /declared/);
  assert.throws(() => hunkApprovalRequest(review, [hunk.hunk_id], 'main'), /non-protected/);
  assert.throws(() => patchReview({ ...plan, merge_allowed: true },
    { name: 'patch-plan.json', sha256: 'a'.repeat(64) }, root), /safety boundary/);
  assert.throws(() => patchReview({ ...plan, hunks: [{ ...hunk, relative_path: '../escape' }] },
    { name: 'patch-plan.json', sha256: 'a'.repeat(64) }, root), /workspace-relative/);
});

test('dedicated surfaces render profile plan run finding evidence and git state', () => {
  const model = {
    status: { state: 'RUNNING', independent_otester: 'NOT_RUN', independent_oaudit: 'NOT_RUN' },
    local: { identity, workMode: 'PLAN', changeSet: '/tmp/change.json' },
    snapshot: {
      profile: { state: 'AVAILABLE', path: '/tmp/profile.json', body: {
        profile_id: 'PROFILE-1', state: 'PROFILE_CANDIDATE', purpose: 'Validate software',
        components: [{}], dependencies: [{}, {}], ai_components: [], data_flows: [], language_inventory: { Java: 1 }
      } },
      plan: { state: 'AVAILABLE', path: '/tmp/plan.json', body: {
        plan_id: 'PLAN-1', approval: { state: 'AWAITING_USER_APPROVAL' }, risk: { level: 'HIGH', score: 70 },
        allowed_actions: ['REVIEW'], resource_budget: { network_egress: 'DENY_BY_DEFAULT' }
      } },
      approved_plan: { state: 'NOT_PRESENT' }, validation_store: { body: { revision: 1 } }, run_count: 1,
      runs: [{ run_root: '/tmp/run', job_id: 'JOB-1', decision: 'FAIL', job_status: 'COMPLETED', artifacts: [
        { name: 'findings.json', size_bytes: 10 }, { name: 'patch-plan.json', size_bytes: 20 }
      ] }],
      latest_run: { run_root: '/tmp/run', finding_count: 1, evidence_count: 1,
        findings: [{ findingId: 'F-1', title: 'Unsafe', severity: 'HIGH', status: 'OPEN' }],
        evidence: [{ evidenceId: 'E-1', type: 'SOURCE', subject: 'Main.java' }],
        artifacts: [{ name: 'patch-plan.json', size_bytes: 20 }] }
    }
  };
  for (const view of [
    'onsure.workspace', 'onsure.profile', 'onsure.inventory', 'onsure.requirements',
    'onsure.threats', 'onsure.plan', 'onsure.runs', 'onsure.findings',
    'onsure.improvement', 'onsure.evidence', 'onsure.git', 'onsure.approvals',
    'onsure.runtime', 'onsure.admin'
  ]) assert.ok(surfaceRows(view, model).length > 0, view);
  assert.equal(surfaceRows('onsure.findings', model)[0].label, 'Unsafe');
  assert.equal(surfaceRows('onsure.evidence', model)[0].label, 'E-1');
  assert.equal(surfaceRows('onsure.runs', model)[0].children[0].command, 'onsure.openArtifact');
});

test('local API URL accepts only explicit loopback HTTP ports', () => {
  assert.equal(normalizeBaseUrl('http://127.0.0.1:47311/'), 'http://127.0.0.1:47311');
  assert.equal(normalizeBaseUrl('http://localhost:47311'), 'http://localhost:47311');
  assert.equal(normalizeBaseUrl('http://[::1]:47311'), 'http://[::1]:47311');
  for (const value of [
    'https://127.0.0.1:47311', 'http://0.0.0.0:47311', 'http://example.com:47311',
    'http://127.0.0.1', 'http://127.0.0.1:80', 'http://127.0.0.1:47311/v1',
    'http://user:password@127.0.0.1:47311'
  ]) assert.throws(() => normalizeBaseUrl(value), /loopback HTTP URL/);
});

test('registration precedes registered-identity learn and validation requests', () => {
  const steps = registrationRequests(identity);
  assert.deepEqual(steps.map(([operation]) => operation), [
    'project.register-workspace', 'project.register', 'project.register-target'
  ]);
  assert.equal(steps[2][1].source_root, root);
  assert.deepEqual(learnRequest(identity), {
    project_id: 'project-001', target_id: 'target-001', program_id: 'target-001'
  });
  assert.deepEqual(validationRequest(identity), {
    project_id: 'project-001', target_id: 'target-001'
  });
  for (const request of [learnRequest(identity), validationRequest(identity)]) {
    for (const prohibited of [
      'source_root', 'target_name', 'target_type', 'adapter_id',
      'immutable_source_reference', 'policy_profile', 'execution_profile'
    ]) assert.equal(Object.hasOwn(request, prohibited), false);
  }
});

test('registered target response is strongly bound to the active workspace', () => {
  const workflow = {
    operation: 'project.read-target',
    result: { registered_target: {
      projectId: identity.projectId,
      target: {
        targetId: identity.targetId,
        targetType: identity.targetType,
        sourceRoot: identity.sourceRoot
      }
    } }
  };
  assert.deepEqual(verifiedIdentity(workflow, identity), identity);
  assert.deepEqual(verifiedIdentity({
    ...workflow,
    result: { registered_target: {
      ...workflow.result.registered_target,
      target: { ...workflow.result.registered_target.target, sourceRoot: pathToFileURL(root).href }
    } }
  }, identity), identity);
  assert.equal(identityForWorkspace(identity, root), identity);
  assert.throws(() => verifiedIdentity(workflow, {
    ...identity, sourceRoot: path.resolve('/tmp/other')
  }), /does not match/);
  assert.throws(() => identityForWorkspace(identity, path.resolve('/tmp/other')), /Register/);
});

test('workflow binding and duplicate registration classification fail closed', () => {
  const response = { workflow: { operation: 'program.learn', result: {} } };
  assert.equal(requireWorkflowBinding(response, 'program.learn'), response.workflow);
  assert.throws(() => requireWorkflowBinding(response, 'validation.run'), /not bound/);
  assert.equal(isExistingRegistration(new LocalApiError('TARGET_EXISTS', 'exists', 400)), true);
  assert.equal(isExistingRegistration(new LocalApiError('UNKNOWN_PROJECT', 'missing', 400)), false);
  assert.equal(isExistingRegistration(new Error('TARGET_EXISTS')), false);
});
