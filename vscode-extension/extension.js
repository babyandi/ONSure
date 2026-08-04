'use strict';

const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');
const {
  ID_PATTERN,
  WORK_MODES,
  LocalApiError,
  requireWorkMode,
  requireModeCapability,
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
} = require('./extension-core');

const TOKEN_KEY = 'onsure.localApiToken';
const GATEWAY_TOKEN_KEY = 'onsure.llmGatewayToken';
const LAST_RUN_KEY = 'onsure.lastRunRoot';
const LAST_PROFILE_KEY = 'onsure.lastProgramProfile';
const LAST_PLAN_KEY = 'onsure.lastExecutionPlan';
const LAST_APPROVED_PLAN_KEY = 'onsure.lastApprovedExecutionPlan';
const LAST_APPROVAL_RECEIPT_KEY = 'onsure.lastExecutionPlanApprovalReceipt';
const LAST_WORKFLOW_KEY = 'onsure.lastWorkflowOperation';
const LAST_WORKTREE_KEY = 'onsure.lastApprovedWorktree';
const LAST_PATCH_APPROVAL_KEY = 'onsure.lastPatchApprovalReceipt';
const LAST_PATCH_APPROVAL_REQUEST_KEY = 'onsure.lastPatchApprovalRequest';
const LAST_PATCH_RECEIPT_KEY = 'onsure.lastPatchApplyReceipt';
const LAST_IMPROVEMENT_PROOF_KEY = 'onsure.lastImprovementProof';
const LAST_DELIVERY_APPROVAL_KEY = 'onsure.lastDeliveryApproval';
const LAST_CHANGE_SET_KEY = 'onsure.lastGitChangeSet';
const LAST_DRAFT_PR_RECEIPT_KEY = 'onsure.lastDraftPrReceipt';
const REGISTERED_IDENTITY_KEY = 'onsure.registeredIdentity';
const WORK_MODE_KEY = 'onsure.workMode';
const VIEW_IDS = Object.freeze([
  'onsure.workspace', 'onsure.profile', 'onsure.inventory', 'onsure.requirements',
  'onsure.threats', 'onsure.plan', 'onsure.runs', 'onsure.findings',
  'onsure.improvement', 'onsure.evidence', 'onsure.git', 'onsure.approvals',
  'onsure.runtime', 'onsure.admin'
]);

class ApiClient {
  constructor(context) { this.context = context; }

  get baseUrl() {
    const configured = vscode.workspace.getConfiguration('onsure').get('localApiUrl');
    return normalizeBaseUrl(configured);
  }

  async token() {
    let token = await this.context.secrets.get(TOKEN_KEY);
    if (!token) {
      token = await vscode.window.showInputBox({
        title: 'ONSure Local API Token',
        prompt: 'Enter the token from ONSURE_LOCAL_API_TOKEN.',
        password: true,
        ignoreFocusOut: true,
        validateInput: value => value.length >= 32 ? undefined : 'Token must contain at least 32 characters.'
      });
      if (!token) throw new Error('ONSure Local API token is required.');
      await this.context.secrets.store(TOKEN_KEY, token);
    }
    return token;
  }

  async request(route, method = 'GET', body) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 180000);
    try {
      const token = await this.token();
      const response = await fetch(`${this.baseUrl}${route}`, {
        method,
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal
      });
      const text = await response.text();
      let payload;
      try { payload = text ? JSON.parse(text) : {}; }
      catch { throw new Error(`Invalid JSON from Local API (${response.status}).`); }
      if (!response.ok) {
        throw new LocalApiError(payload.error, payload.message, response.status);
      }
      return payload;
    } finally {
      clearTimeout(timeout);
    }
  }

  async workflow(operation, request) {
    const response = await this.request('/v1/workflow', 'POST', { operation, request });
    return requireWorkflowBinding(response, operation);
  }
}

class GatewayClient {
  constructor(context) { this.context = context; }

  get baseUrl() {
    const configured = vscode.workspace.getConfiguration('onsure').get('llmGatewayUrl');
    return normalizeBaseUrl(configured || 'http://127.0.0.1:47312');
  }

  async request(route, token) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(`${this.baseUrl}${route}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Accept': 'application/json'
        },
        signal: controller.signal
      });
      const text = await response.text();
      let payload;
      try { payload = text ? JSON.parse(text) : {}; }
      catch { throw new Error(`Invalid JSON from LLM Gateway (${response.status}).`); }
      if (!response.ok) throw new Error(`LLM Gateway returned HTTP ${response.status}.`);
      return payload;
    } finally {
      clearTimeout(timeout);
    }
  }

  async snapshot() {
    const token = await this.context.secrets.get(GATEWAY_TOKEN_KEY);
    if (!token) return { state: 'NOT_CONFIGURED', metrics: {} };
    try {
      const [health, metrics] = await Promise.all([
        this.request('/v1/health', token), this.request('/v1/metrics', token)
      ]);
      return { state: health.state || 'UNKNOWN', health, metrics };
    } catch (error) {
      return { state: 'UNAVAILABLE', error: error.message, metrics: {} };
    }
  }
}

class PatchPreviewProvider {
  constructor() { this.documents = new Map(); }

  provideTextDocumentContent(uri) {
    return this.documents.get(uri.toString()) || '';
  }

  document(relativePath, content) {
    const uri = vscode.Uri.parse(
      `onsure-patch-preview:/${encodeURIComponent(relativePath)}?id=${randomUUID()}`);
    this.documents.set(uri.toString(), content);
    return uri;
  }
}

class WorkspaceModel {
  constructor(context, client, gateway) {
    this.context = context;
    this.client = client;
    this.gateway = gateway;
    this.value = {};
    this.pending = null;
  }

  local() {
    return {
      identity: this.context.workspaceState.get(REGISTERED_IDENTITY_KEY),
      workMode: this.context.workspaceState.get(WORK_MODE_KEY)
        || vscode.workspace.getConfiguration('onsure').get('defaultWorkMode') || 'ASK',
      lastWorkflow: this.context.workspaceState.get(LAST_WORKFLOW_KEY),
      worktreeRoot: this.context.workspaceState.get(LAST_WORKTREE_KEY),
      patchApproval: this.context.workspaceState.get(LAST_PATCH_APPROVAL_KEY),
      patchApprovalRequest: this.context.workspaceState.get(LAST_PATCH_APPROVAL_REQUEST_KEY),
      patchReceipt: this.context.workspaceState.get(LAST_PATCH_RECEIPT_KEY),
      improvementProof: this.context.workspaceState.get(LAST_IMPROVEMENT_PROOF_KEY),
      deliveryApproval: this.context.workspaceState.get(LAST_DELIVERY_APPROVAL_KEY),
      changeSet: this.context.workspaceState.get(LAST_CHANGE_SET_KEY),
      draftPrReceipt: this.context.workspaceState.get(LAST_DRAFT_PR_RECEIPT_KEY)
    };
  }

  async load(force = false) {
    if (!force && this.pending) return this.pending;
    if (!force && this.value.loaded) return this.value;
    this.pending = this.fetch();
    try { this.value = await this.pending; }
    finally { this.pending = null; }
    return this.value;
  }

  async fetch() {
    const local = this.local();
    const gateway = await this.gateway.snapshot();
    try {
      const status = await this.client.request('/v1/status');
      let snapshot;
      if (local.identity) {
        const identity = identityForWorkspace(local.identity, workspaceRoot());
        const response = await this.client.request(
          '/v1/workspace-snapshot', 'POST', snapshotRequest(identity));
        snapshot = requireSnapshotBinding(response, identity);
        await restoreSnapshotState(this.context, snapshot);
      }
      return { loaded: true, status, snapshot, gateway, local: this.local() };
    } catch (error) {
      return { loaded: true, error: error.message, gateway, local: this.local() };
    }
  }

  invalidate() { this.value = {}; }
}

class AssuranceTreeProvider {
  constructor(viewId, model) {
    this.viewId = viewId;
    this.model = model;
    this._onDidChangeTreeData = new vscode.EventEmitter();
    this.onDidChangeTreeData = this._onDidChangeTreeData.event;
  }

  refresh() { this._onDidChangeTreeData.fire(undefined); }
  getTreeItem(element) { return treeItem(element); }

  async getChildren(element) {
    if (element) return element.children || [];
    return surfaceRows(this.viewId, await this.model.load());
  }
}

function treeItem(element) {
  const collapsible = element.children?.length
    ? vscode.TreeItemCollapsibleState.Collapsed : vscode.TreeItemCollapsibleState.None;
  const value = new vscode.TreeItem(element.label, collapsible);
  value.description = element.description;
  value.tooltip = `${element.label}: ${element.description}`;
  value.iconPath = new vscode.ThemeIcon(element.icon);
  if (element.command) value.command = {
    command: element.command, title: element.label, arguments: element.args || []
  };
  return value;
}

function workspaceRoot() {
  const folder = vscode.workspace.workspaceFolders?.[0];
  if (!folder) throw new Error('Open a workspace folder before using ONSure.');
  return folder.uri.fsPath;
}

function requireInsideWorkspace(file) {
  const root = path.resolve(workspaceRoot());
  const candidate = path.resolve(file);
  const relative = path.relative(root, candidate);
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error('Workflow request file must be inside the active workspace.');
  }
  return candidate;
}

async function showJson(title, value) {
  const document = await vscode.workspace.openTextDocument({
    language: 'json',
    content: JSON.stringify(value, null, 2)
  });
  await vscode.window.showTextDocument(document, { preview: false });
  vscode.window.setStatusBarMessage(`ONSure: ${title}`, 5000);
}

async function showMarkdown(title, value) {
  const document = await vscode.workspace.openTextDocument({
    language: 'markdown', content: value
  });
  await vscode.window.showTextDocument(document, { preview: false });
  vscode.window.setStatusBarMessage(`ONSure: ${title}`, 5000);
}

async function restoreSnapshotState(context, snapshot) {
  const updates = [
    [LAST_PROFILE_KEY, snapshot.profile?.path],
    [LAST_PLAN_KEY, snapshot.plan?.path],
    [LAST_APPROVED_PLAN_KEY, snapshot.approved_plan?.path],
    [LAST_RUN_KEY, snapshot.latest_run?.run_root],
    [LAST_PATCH_RECEIPT_KEY, snapshot.delivery?.patch_apply_receipt?.path],
    [LAST_IMPROVEMENT_PROOF_KEY, snapshot.delivery?.improvement_proof?.path],
    [LAST_CHANGE_SET_KEY, snapshot.delivery?.change_set?.path],
    [LAST_DRAFT_PR_RECEIPT_KEY, snapshot.delivery?.draft_pr_receipt?.path]
  ];
  const hasAppliedPatch = snapshot.delivery?.patch_apply_receipt?.state === 'AVAILABLE';
  updates.push([LAST_WORKTREE_KEY, hasAppliedPatch
    ? path.join(workspaceRoot(), '.onsure', 'worktrees', 'approved-patch') : undefined]);
  for (const [key, value] of updates) {
    await context.workspaceState.update(key, value ? requireInsideWorkspace(value) : undefined);
  }
}

async function selectWorkspaceFile(title, filters, defaultValue) {
  const root = workspaceRoot();
  const selected = await vscode.window.showOpenDialog({
    title, canSelectMany: false, canSelectFiles: true, canSelectFolders: false,
    filters, defaultUri: vscode.Uri.file(defaultValue || root)
  });
  return selected?.length ? requireInsideWorkspace(selected[0].fsPath) : undefined;
}

function atomicWriteNewJson(file, value) {
  const destination = requireInsideWorkspace(file);
  if (fs.existsSync(destination)) throw new Error('Approval request output already exists.');
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  const temporary = `${destination}.${randomUUID()}.tmp`;
  try {
    fs.writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`, {
      encoding: 'utf8', flag: 'wx', mode: 0o600
    });
    fs.renameSync(temporary, destination);
  } finally {
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
  }
  return destination;
}

async function activate(context) {
  const client = new ApiClient(context);
  const gateway = new GatewayClient(context);
  const patchPreviews = new PatchPreviewProvider();
  const model = new WorkspaceModel(context, client, gateway);
  const providers = VIEW_IDS.map(viewId => new AssuranceTreeProvider(viewId, model));
  const views = VIEW_IDS.map((viewId, index) =>
    vscode.window.createTreeView(viewId, { treeDataProvider: providers[index] }));
  const output = vscode.window.createOutputChannel('ONSure');
  const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  statusBar.text = '$(shield) ONSure: NON_FINAL';
  statusBar.tooltip = 'ONSure self-validation is nonfinal until independent gates pass.';
  statusBar.command = 'onsure.refresh';
  statusBar.show();

  function refreshAll() {
    model.invalidate();
    providers.forEach(provider => provider.refresh());
  }

  function currentWorkMode() {
    return requireWorkMode(context.workspaceState.get(WORK_MODE_KEY)
      || vscode.workspace.getConfiguration('onsure').get('defaultWorkMode') || 'ASK');
  }

  function requireCapability(capability) {
    return requireModeCapability(currentWorkMode(), capability);
  }

  async function executeWorkflow(operation, request, title) {
    requireModeWorkflow(currentWorkMode(), operation);
    const workflow = await vscode.window.withProgress({
      location: vscode.ProgressLocation.Notification,
      title: `ONSure: ${title}`,
      cancellable: false
    }, () => client.workflow(operation, request));
    await context.workspaceState.update(LAST_WORKFLOW_KEY, operation);
    const result = workflow.result || {};
    const runRoot = workflowRunRoot(result);
    if (runRoot) await context.workspaceState.update(LAST_RUN_KEY, requireInsideWorkspace(runRoot));
    output.appendLine(`[${new Date().toISOString()}] ${operation}: ${JSON.stringify(result)}`);
    refreshAll();
    await showJson(`${title} — SELF_VALIDATION_NONFINAL`, workflow);
    return workflow;
  }

  async function registerActiveWorkspace() {
    requireCapability('REGISTER');
    const root = workspaceRoot();
    const defaultId = path.basename(root).replace(/[^A-Za-z0-9._-]/g, '-').slice(0, 128) || 'onsure-project';
    const workspaceId = await vscode.window.showInputBox({
      title: 'ONSure Workspace ID', value: defaultId,
      validateInput: value => ID_PATTERN.test(value) ? undefined : 'Use 1-128 letters, numbers, dot, underscore or hyphen.'
    });
    if (!workspaceId) return;
    const projectId = await vscode.window.showInputBox({
      title: 'ONSure Project ID', value: defaultId,
      validateInput: value => ID_PATTERN.test(value) ? undefined : 'Use 1-128 letters, numbers, dot, underscore or hyphen.'
    });
    if (!projectId) return;
    const targetId = await vscode.window.showInputBox({
      title: 'ONSure Target ID', value: defaultId,
      validateInput: value => ID_PATTERN.test(value) ? undefined : 'Use 1-128 letters, numbers, dot, underscore or hyphen.'
    });
    if (!targetId) return;
    const targetType = await vscode.window.showQuickPick(
      ['GENERAL_SOFTWARE', 'AI_APPLICATION'],
      { title: 'ONSure Target Type', placeHolder: 'Select the registered target type.' });
    if (!targetType) return;
    const candidate = {
      workspaceId, workspaceName: workspaceId,
      projectId, projectName: projectId,
      targetId, targetName: targetId,
      targetType, sourceRoot: root
    };
    const steps = registrationRequests(candidate);
    await vscode.window.withProgress({
      location: vscode.ProgressLocation.Notification,
      title: 'ONSure: Registering workspace and target', cancellable: false
    }, async progress => {
      for (let index = 0; index < steps.length; index += 1) {
        const [operation, request] = steps[index];
        progress.report({ message: operation, increment: 100 / (steps.length + 1) });
        try {
          await client.workflow(operation, request);
        } catch (error) {
          if (!isExistingRegistration(error)) throw error;
          output.appendLine(`[${new Date().toISOString()}] ${operation}: ${error.code}; verifying existing identity`);
        }
      }
    });
    const read = await client.workflow('project.read-target', {
      project_id: projectId, target_id: targetId
    });
    const identity = verifiedIdentity(read, candidate);
    await context.workspaceState.update(REGISTERED_IDENTITY_KEY, identity);
    await context.workspaceState.update(LAST_WORKFLOW_KEY, 'project.read-target');
    output.appendLine(`[${new Date().toISOString()}] REGISTERED_TARGET:${projectId}/${targetId}:SELF_VALIDATION_NONFINAL`);
    refreshAll();
    await showJson('Registered target — SELF_VALIDATION_NONFINAL', read);
  }

  async function loadLatestPatchReview() {
    requireCapability('READ');
    const root = workspaceRoot();
    identityForWorkspace(context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
    const current = await model.load(true);
    if (current.error) throw new Error(current.error);
    const latest = current.snapshot?.latest_run;
    if (!latest?.run_root) throw new Error('No validation run is available for patch review.');
    const artifact = latest.artifacts?.find(value => value.name === 'patch-plan.json');
    if (!artifact) throw new Error('The latest validation run has no patch plan.');
    const response = await client.request('/v1/run-artifact', 'POST', {
      run_root: requireInsideWorkspace(latest.run_root), artifact: 'patch-plan.json'
    });
    if (response.sha256 !== artifact.sha256) {
      throw new Error('Patch plan changed between snapshot and artifact read.');
    }
    return patchReview(response.body, artifact, latest.run_root);
  }

  async function showPatchHunk(review, hunk) {
    const sourceFile = requireInsideWorkspace(path.join(workspaceRoot(), hunk.relative_path));
    const stat = fs.lstatSync(sourceFile);
    if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 2 * 1024 * 1024) {
      throw new Error('Patch preview source must be a non-symlink regular file up to 2 MiB.');
    }
    const source = fs.readFileSync(sourceFile, 'utf8');
    const preview = previewHunk(source, hunk);
    const previewUri = patchPreviews.document(hunk.relative_path, preview);
    await vscode.commands.executeCommand('vscode.diff', vscode.Uri.file(sourceFile), previewUri,
      `ONSure ${hunk.hunk_id} — ${hunk.relative_path} — NONFINAL`, { preview: false });
    output.appendLine(`[${new Date().toISOString()}] PATCH_PREVIEW:${review.patchPlanId}:${hunk.hunk_id}:NONFINAL`);
  }

  async function savePatchSigningRequest(review, selectedHunkIds, selectionScope) {
    requireCapability('IMPROVE');
    const branch = await vscode.window.showInputBox({
      title: 'Requested Isolated Patch Branch',
      value: `codex/${review.targetId}-approved-patch`,
      prompt: 'A non-protected branch; the external signer must approve the same branch.'
    });
    if (!branch) return;
    const request = hunkApprovalRequest(
      review, selectedHunkIds, branch, new Date().toISOString(), selectionScope);
    const safeId = review.patchPlanId.replace(/[^A-Za-z0-9._-]/g, '-');
    const selectedFile = await vscode.window.showSaveDialog({
      title: 'Save Unsigned Patch Approval Request',
      filters: { JSON: ['json'] },
      defaultUri: vscode.Uri.file(path.join(workspaceRoot(), '.onsure', 'approval-requests',
        `${safeId}-${selectionScope.toLowerCase()}-approval-request.json`))
    });
    if (!selectedFile) return;
    const saved = atomicWriteNewJson(selectedFile.fsPath, request);
    await context.workspaceState.update(LAST_PATCH_APPROVAL_REQUEST_KEY, saved);
    refreshAll();
    await showJson('Unsigned patch approval request — EXTERNAL_SIGNATURE_REQUIRED', request);
    vscode.window.showWarningMessage(
      `ONSure created an unsigned ${selectionScope} request only. An external trusted approver must issue the signed receipt.`);
  }

  async function controlAutopilot(action) {
    requireCapability('AUTOPILOT_CONTROL');
    const root = workspaceRoot();
    identityForWorkspace(context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
    const response = await client.request(
      '/v1/autopilot-control', 'POST', autopilotControlRequest(action));
    output.appendLine(`[${new Date().toISOString()}] AUTOPILOT_CONTROL:${action}:NONFINAL`);
    refreshAll();
    await showJson(`Autopilot ${action} control`, response);
  }

  context.subscriptions.push(...views, output, statusBar,
    vscode.workspace.registerTextDocumentContentProvider('onsure-patch-preview', patchPreviews),
    vscode.commands.registerCommand('onsure.configure', async () => {
      const current = vscode.workspace.getConfiguration('onsure').get('localApiUrl') || 'http://127.0.0.1:47311';
      const url = await vscode.window.showInputBox({
        title: 'ONSure Local API URL',
        value: String(current),
        validateInput: value => /^http:\/\/(?:127\.0\.0\.1|localhost|\[::1\]):\d{2,5}\/?$/.test(value)
          ? undefined : 'Use a loopback URL such as http://127.0.0.1:47311.'
      });
      if (url) await vscode.workspace.getConfiguration('onsure').update(
        'localApiUrl', url.replace(/\/$/, ''), vscode.ConfigurationTarget.Workspace);
      const token = await vscode.window.showInputBox({
        title: 'ONSure Local API Token', password: true, ignoreFocusOut: true,
        validateInput: value => value.length >= 32 ? undefined : 'Token must contain at least 32 characters.'
      });
      if (token) await context.secrets.store(TOKEN_KEY, token);
      const currentGateway = vscode.workspace.getConfiguration('onsure').get('llmGatewayUrl')
        || 'http://127.0.0.1:47312';
      const gatewayUrl = await vscode.window.showInputBox({
        title: 'ONSure LLM Gateway URL', value: String(currentGateway),
        validateInput: value => /^http:\/\/(?:127\.0\.0\.1|localhost|\[::1\]):\d{2,5}\/?$/.test(value)
          ? undefined : 'Use a loopback URL such as http://127.0.0.1:47312.'
      });
      if (gatewayUrl) await vscode.workspace.getConfiguration('onsure').update(
        'llmGatewayUrl', gatewayUrl.replace(/\/$/, ''), vscode.ConfigurationTarget.Workspace);
      const gatewayToken = await vscode.window.showInputBox({
        title: 'ONSure LLM Gateway Token', password: true, ignoreFocusOut: true,
        prompt: 'Optional. Stored only in VS Code SecretStorage.',
        validateInput: value => !value || value.length >= 32
          ? undefined : 'Token must contain at least 32 characters.'
      });
      if (gatewayToken) await context.secrets.store(GATEWAY_TOKEN_KEY, gatewayToken);
      refreshAll();
    }),
    vscode.commands.registerCommand('onsure.clearToken', async () => {
      await context.secrets.delete(TOKEN_KEY);
      await context.secrets.delete(GATEWAY_TOKEN_KEY);
      vscode.window.showInformationMessage('ONSure Local API and LLM Gateway tokens cleared.');
    }),
    vscode.commands.registerCommand('onsure.registerWorkspaceTarget', async () => {
      try {
        await registerActiveWorkspace();
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure registration failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.selectMode', async () => {
      const current = context.workspaceState.get(WORK_MODE_KEY)
        || vscode.workspace.getConfiguration('onsure').get('defaultWorkMode') || 'ASK';
      const selected = await vscode.window.showQuickPick(
        WORK_MODES.map(mode => ({
          label: mode,
          description: mode === current ? 'Current mode' : mode === 'OFFLINE'
            ? 'Network and external providers prohibited' : 'Policy and approval gated'
        })),
        { title: 'ONSure Work Mode', placeHolder: 'Select a fail-closed work mode.' });
      if (!selected) return;
      await context.workspaceState.update(WORK_MODE_KEY, selected.label);
      statusBar.text = `$(shield) ONSure: ${selected.label} / NON_FINAL`;
      output.appendLine(`[${new Date().toISOString()}] MODE_CHANGE:${current}->${selected.label}:SELF_VALIDATION_NONFINAL`);
      refreshAll();
    }),
    vscode.commands.registerCommand('onsure.askOrPlan', async () => {
      const mode = currentWorkMode();
      if (!['ASK', 'PLAN'].includes(mode)) {
        throw new Error('Select ASK or PLAN work mode before starting a conversation.');
      }
      const prompt = await vscode.window.showInputBox({
        title: `ONSure ${mode}`,
        prompt: mode === 'ASK'
          ? 'Ask about the registered project and persisted validation evidence.'
          : 'Describe the outcome to plan without executing it.',
        ignoreFocusOut: true,
        validateInput: value => value.trim().length && value.length <= 4000
          ? undefined : 'Enter 1-4000 characters.'
      });
      if (!prompt) return;
      const response = conversationResponse(mode, prompt, await model.load(true));
      output.appendLine(`[${new Date().toISOString()}] ${mode}: ${response.prompt_sha256}`);
      await showMarkdown(`${mode} — LOCAL NONFINAL`, response.response_markdown);
    }),
    vscode.commands.registerCommand('onsure.refresh', async () => refreshAll()),
    vscode.commands.registerCommand('onsure.autopilotStatus', async () => {
      try {
        requireCapability('READ');
        const current = await model.load(true);
        if (current.error) throw new Error(current.error);
        await showJson('Autopilot checkpoint — NONFINAL',
          current.snapshot?.autopilot || { checkpoint: { state: 'NOT_PRESENT' } });
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure Autopilot status failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.autopilotPause', async () => {
      try { await controlAutopilot('PAUSE'); }
      catch (error) { vscode.window.showErrorMessage(`ONSure Autopilot pause failed: ${error.message}`); }
    }),
    vscode.commands.registerCommand('onsure.autopilotResume', async () => {
      try { await controlAutopilot('RESUME'); }
      catch (error) { vscode.window.showErrorMessage(`ONSure Autopilot resume failed: ${error.message}`); }
    }),
    vscode.commands.registerCommand('onsure.autopilotCancel', async () => {
      try {
        const confirmed = await vscode.window.showWarningMessage(
          'Cancel the running ONSure Autopilot subprocess group? This does not roll back completed stages.',
          { modal: true }, 'Request Cancellation');
        if (confirmed !== 'Request Cancellation') return;
        await controlAutopilot('CANCEL');
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure Autopilot cancel failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.learnProgram', async () => {
      try {
        const root = workspaceRoot();
        const identity = identityForWorkspace(
          context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
        const outputFile = path.join(root, '.onsure', 'profiles', identity.targetId, 'program-profile.json');
        await executeWorkflow(
          'program.learn', learnRequest(identity), 'Learning registered program');
        await context.workspaceState.update(LAST_PROFILE_KEY, outputFile);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure program learning failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.generatePlan', async () => {
      try {
        const root = workspaceRoot();
        const identity = identityForWorkspace(
          context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
        const profileFile = context.workspaceState.get(LAST_PROFILE_KEY);
        if (!profileFile) throw new Error('Learn the registered program before generating a plan.');
        const planFile = path.join(root, '.onsure', 'plans', `${identity.targetId}-execution-plan.json`);
        await executeWorkflow('plan.generate', {
          project_id: identity.projectId,
          target_id: identity.targetId,
          program_profile_file: requireInsideWorkspace(profileFile)
        }, 'Generating execution plan');
        await context.workspaceState.update(LAST_PLAN_KEY, planFile);
        await context.workspaceState.update(LAST_APPROVED_PLAN_KEY, undefined);
        await context.workspaceState.update(LAST_APPROVAL_RECEIPT_KEY, undefined);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure plan generation failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.approvePlan', async () => {
      try {
        const root = workspaceRoot();
        identityForWorkspace(context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
        const planFile = context.workspaceState.get(LAST_PLAN_KEY);
        if (!planFile) throw new Error('Generate an execution plan before recording approval.');
        const selected = await vscode.window.showOpenDialog({
          title: 'Select Signed ONSure Execution Plan Approval Receipt',
          canSelectMany: false, canSelectFiles: true, canSelectFolders: false,
          filters: { JSON: ['json'] }, defaultUri: vscode.Uri.file(root)
        });
        if (!selected?.length) return;
        const receiptFile = requireInsideWorkspace(selected[0].fsPath);
        await executeWorkflow('plan.approve', {
          plan_file: requireInsideWorkspace(planFile),
          signed_approval_receipt: receiptFile
        }, 'Verifying signed plan approval');
        await context.workspaceState.update(
          LAST_APPROVED_PLAN_KEY, path.join(root, '.onsure', 'plans', 'approved-execution-plan.json'));
        await context.workspaceState.update(LAST_APPROVAL_RECEIPT_KEY, receiptFile);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure plan approval failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.runValidation', async () => {
      try {
        const root = workspaceRoot();
        const identity = identityForWorkspace(
          context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
        const originalPlan = context.workspaceState.get(LAST_PLAN_KEY);
        const approvedPlan = context.workspaceState.get(LAST_APPROVED_PLAN_KEY);
        const approvalReceipt = context.workspaceState.get(LAST_APPROVAL_RECEIPT_KEY);
        if (!originalPlan || !approvedPlan || !approvalReceipt) {
          throw new Error('Generate and approve an execution plan before validation.');
        }
        await executeWorkflow('validation.run', {
          ...validationRequest(identity),
          original_execution_plan_file: requireInsideWorkspace(originalPlan),
          approved_execution_plan_file: requireInsideWorkspace(approvedPlan),
          signed_approval_receipt: requireInsideWorkspace(approvalReceipt)
        }, 'Validating registered target with approved plan');
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure validation failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.runUniversalValidation', async () => {
      try {
        const root = workspaceRoot();
        const identity = identityForWorkspace(
          context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
        const requirementMode = await vscode.window.showQuickPick([
          {label: 'Built-in environment requirements', value: 'BUILT_IN'},
          {label: 'Select environment requirement profile', value: 'SELECT'}
        ], {
          title: 'ONSure Universal Validation',
          placeHolder: 'Choose the group-1 environment/dependency preflight input.'
        });
        if (!requirementMode) return;
        let environmentProfileFile;
        if (requirementMode.value === 'SELECT') {
          const selected = await vscode.window.showOpenDialog({
            title: 'Select ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1 JSON',
            canSelectMany: false, canSelectFiles: true, canSelectFolders: false,
            filters: { JSON: ['json'] }, defaultUri: vscode.Uri.file(root)
          });
          if (!selected?.length) return;
          environmentProfileFile = requireInsideWorkspace(selected[0].fsPath);
        }
        const runId = `universal-${Date.now()}-${randomUUID()}`;
        const runRoot = path.join(root, '.onsure', 'universal-validation', identity.targetId, runId);
        await executeWorkflow('validation.run', universalValidationRequest(
          identity, runId, runRoot, environmentProfileFile),
        'Running seven-group universal validation');
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure universal validation failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.runWorkflowRequest', async () => {
      try {
        const selected = await vscode.window.showOpenDialog({
          title: 'Select ONSure Workflow Request JSON',
          canSelectMany: false,
          canSelectFiles: true,
          canSelectFolders: false,
          filters: { JSON: ['json'] },
          defaultUri: vscode.Uri.file(workspaceRoot())
        });
        if (!selected?.length) return;
        const requestFile = requireInsideWorkspace(selected[0].fsPath);
        const raw = fs.readFileSync(requestFile, 'utf8');
        if (raw.length > 1024 * 1024) throw new Error('Workflow request file exceeds 1 MiB.');
        const envelope = JSON.parse(raw);
        if (!envelope || typeof envelope.operation !== 'string' || typeof envelope.request !== 'object') {
          throw new Error('Workflow file requires {"operation":"...","request":{...}}.');
        }
        await executeWorkflow(envelope.operation, envelope.request, envelope.operation);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure workflow failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.openLastArtifact', async () => {
      try {
        const runRoot = context.workspaceState.get(LAST_RUN_KEY);
        if (!runRoot) throw new Error('No completed run is recorded in this workspace.');
        const artifact = await vscode.window.showQuickPick([
          'universal-validation-result.json', 'validation-report.json',
          'program-profile.json', 'execution-plan.json',
          'behavior-profile.json', 'review-result.json', 'evidence-based-rca.json', 'patch-plan.json'
        ], { title: 'ONSure Run Artifact' });
        if (!artifact) return;
        const result = await client.request('/v1/run-artifact', 'POST', { run_root: runRoot, artifact });
        await showJson(artifact, result.body);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure artifact open failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.openArtifact', async (runRoot, artifact) => {
      try {
        const result = await client.request('/v1/run-artifact', 'POST', {
          run_root: requireInsideWorkspace(runRoot), artifact: String(artifact)
        });
        await showJson(String(artifact), result.body);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure artifact open failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.openDocument', async file => {
      try {
        const document = await vscode.workspace.openTextDocument(
          vscode.Uri.file(requireInsideWorkspace(file)));
        await vscode.window.showTextDocument(document, { preview: false });
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure document open failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.reviewPatchPlan', async () => {
      try {
        const review = await loadLatestPatchReview();
        const selected = await vscode.window.showQuickPick(review.hunks.map(hunk => ({
          label: hunk.hunk_id,
          description: hunk.relative_path,
          detail: `${hunk.finding_id} — ${hunk.expected_effect || 'Approval-required change'}`,
          hunk
        })), {
          title: `Review Patch Hunks — ${review.patchPlanId}`,
          placeHolder: 'Select one hunk to open a digest-verified diff preview.'
        });
        if (!selected) return;
        await showPatchHunk(review, selected.hunk);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure patch review failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.createHunkApprovalRequest', async () => {
      try {
        const review = await loadLatestPatchReview();
        const selected = await vscode.window.showQuickPick(review.hunks.map(hunk => ({
          label: hunk.hunk_id,
          description: hunk.relative_path,
          detail: `${hunk.finding_id} — ${hunk.expected_effect || 'Approval-required change'}`,
          hunk
        })), {
          title: `Select Hunks for External Signature — ${review.patchPlanId}`,
          canPickMany: true,
          placeHolder: 'Default deny: only explicitly selected hunks enter the signing request.'
        });
        if (!selected?.length) return;
        await savePatchSigningRequest(
          review, selected.map(value => value.hunk.hunk_id), 'HUNK');
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure approval request failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.createFileApprovalRequest', async () => {
      try {
        const review = await loadLatestPatchReview();
        const files = [...new Set(review.hunks.map(hunk => hunk.relative_path))].sort();
        const selected = await vscode.window.showQuickPick(files.map(file => {
          const hunks = review.hunks.filter(hunk => hunk.relative_path === file);
          return {
            label: file,
            description: `${hunks.length} declared hunk(s)`,
            detail: 'Selecting a file expands to every declared hunk in that file.',
            hunks
          };
        }), {
          title: `Select Whole Files for External Signature — ${review.patchPlanId}`,
          canPickMany: true,
          placeHolder: 'Default deny: unselected files and hunks remain excluded.'
        });
        if (!selected?.length) return;
        await savePatchSigningRequest(
          review, selected.flatMap(value => value.hunks.map(hunk => hunk.hunk_id)), 'FILE');
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure file approval request failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.applyApprovedPatch', async () => {
      try {
        requireCapability('IMPROVE');
        const root = workspaceRoot();
        identityForWorkspace(context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
        const runRoot = context.workspaceState.get(LAST_RUN_KEY);
        if (!runRoot) throw new Error('Run validation and generate a patch plan first.');
        const approvalRequest = context.workspaceState.get(LAST_PATCH_APPROVAL_REQUEST_KEY);
        if (!approvalRequest) throw new Error('Create and save the bound patch approval request first.');
        const approval = await selectWorkspaceFile(
          'Select Signed ONSure Patch Approval Receipt', { JSON: ['json'] });
        if (!approval) return;
        const workflow = await executeWorkflow('patch.apply', patchApplyRequest(
          root, requireInsideWorkspace(runRoot), requireInsideWorkspace(approvalRequest), approval),
        'Applying approved patch in isolated worktree');
        const worktree = requireInsideWorkspace(workflow.result?.worktree);
        const receipt = path.join(root, '.onsure', 'improvement-evidence', 'patch-apply-receipt.json');
        await context.workspaceState.update(LAST_PATCH_APPROVAL_KEY, approval);
        await context.workspaceState.update(LAST_WORKTREE_KEY, worktree);
        await context.workspaceState.update(LAST_PATCH_RECEIPT_KEY, receipt);
        refreshAll();
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure approved patch failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.proveImprovement', async () => {
      try {
        requireCapability('IMPROVE');
        const root = workspaceRoot();
        identityForWorkspace(context.workspaceState.get(REGISTERED_IDENTITY_KEY), root);
        const patchReceipt = context.workspaceState.get(LAST_PATCH_RECEIPT_KEY);
        if (!patchReceipt) throw new Error('Apply an approved patch before proving improvement.');
        const baseline = await selectWorkspaceFile(
          'Select Baseline Validation Report', { JSON: ['json'] }, context.workspaceState.get(LAST_RUN_KEY));
        if (!baseline) return;
        const current = await selectWorkspaceFile(
          'Select Current Validation Report from Approved Worktree', { JSON: ['json'] },
          context.workspaceState.get(LAST_WORKTREE_KEY));
        if (!current) return;
        await executeWorkflow('improvement.prove', {
          baseline_report_file: baseline,
          current_report_file: current,
          patch_apply_receipt_file: requireInsideWorkspace(patchReceipt)
        }, 'Proving improvement against regression evidence');
        await context.workspaceState.update(LAST_IMPROVEMENT_PROOF_KEY,
          path.join(root, '.onsure', 'improvement-evidence', 'improvement-proof.json'));
        refreshAll();
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure improvement proof failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.gitCommit', async () => {
      try {
        requireCapability('DELIVER');
        const worktree = context.workspaceState.get(LAST_WORKTREE_KEY);
        const patchReceipt = context.workspaceState.get(LAST_PATCH_RECEIPT_KEY);
        const proof = context.workspaceState.get(LAST_IMPROVEMENT_PROOF_KEY);
        if (!worktree || !patchReceipt || !proof) {
          throw new Error('Approved worktree, patch receipt, and improvement proof are required.');
        }
        const deliveryApproval = await selectWorkspaceFile(
          'Select Signed ONSure Git Delivery Approval', { JSON: ['json'] });
        if (!deliveryApproval) return;
        const commitMessage = await vscode.window.showInputBox({
          title: 'Approved Commit Message', prompt: 'One line, up to 200 characters.',
          validateInput: value => value && value.length <= 200 && !/[\r\n]/.test(value)
            ? undefined : 'Enter 1-200 characters on one line.'
        });
        if (!commitMessage) return;
        await executeWorkflow('git.commit', gitCommitRequest({
          worktreeRoot: worktree, patchReceipt, improvementProof: proof,
          deliveryApproval, commitMessage
        }), 'Committing approved worktree');
        await context.workspaceState.update(LAST_DELIVERY_APPROVAL_KEY, deliveryApproval);
        await context.workspaceState.update(LAST_CHANGE_SET_KEY,
          path.join(workspaceRoot(), '.onsure', 'git', 'change-set.json'));
        refreshAll();
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure Git commit failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.gitDraftPr', async () => {
      try {
        requireCapability('DELIVER');
        const worktree = context.workspaceState.get(LAST_WORKTREE_KEY);
        const changeSet = context.workspaceState.get(LAST_CHANGE_SET_KEY);
        if (!worktree || !changeSet) {
          throw new Error('Approved commit and change set are required.');
        }
        const deliveryApproval = context.workspaceState.get(LAST_DELIVERY_APPROVAL_KEY)
          || await selectWorkspaceFile(
            'Select Consumed ONSure Git Delivery Approval', { JSON: ['json'] });
        if (!deliveryApproval) return;
        const baseBranch = await vscode.window.showInputBox({
          title: 'Approved Base Branch', value: 'main', prompt: 'Must match the signed delivery approval.'
        });
        if (!baseBranch) return;
        const title = await vscode.window.showInputBox({
          title: 'Draft PR Title', validateInput: value => value && value.length <= 250 && !/[\r\n]/.test(value)
            ? undefined : 'Enter 1-250 characters on one line.'
        });
        if (!title) return;
        const bodyFile = await selectWorkspaceFile(
          'Select Draft PR Body Markdown', { Markdown: ['md', 'markdown'], Text: ['txt'] });
        if (!bodyFile) return;
        await executeWorkflow('git.draft-pr', gitDraftPrRequest({
          worktreeRoot: worktree, changeSet, deliveryApproval, baseBranch, title, bodyFile
        }), 'Pushing approved branch and opening Draft PR');
        await context.workspaceState.update(LAST_DRAFT_PR_RECEIPT_KEY,
          path.join(workspaceRoot(), '.onsure', 'git', 'draft-pr-receipt.json'));
        refreshAll();
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure Draft PR failed: ${error.message}`);
      }
    })
  );
  refreshAll();
}

function deactivate() {}

module.exports = { activate, deactivate };
