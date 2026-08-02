'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const { pathToFileURL } = require('url');
const {
  LocalApiError,
  normalizeBaseUrl,
  registrationRequests,
  learnRequest,
  validationRequest,
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
