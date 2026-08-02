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
  requireWorkflowBinding,
  verifiedIdentity,
  identityForWorkspace,
  isExistingRegistration
};
