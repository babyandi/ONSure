'use strict';

const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const {
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
} = require('./extension-core');

const TOKEN_KEY = 'onsure.localApiToken';
const LAST_RUN_KEY = 'onsure.lastRunRoot';
const LAST_PROFILE_KEY = 'onsure.lastProgramProfile';
const LAST_PLAN_KEY = 'onsure.lastExecutionPlan';
const LAST_APPROVED_PLAN_KEY = 'onsure.lastApprovedExecutionPlan';
const LAST_APPROVAL_RECEIPT_KEY = 'onsure.lastExecutionPlanApprovalReceipt';
const LAST_WORKFLOW_KEY = 'onsure.lastWorkflowOperation';
const REGISTERED_IDENTITY_KEY = 'onsure.registeredIdentity';
const WORK_MODE_KEY = 'onsure.workMode';
const WORK_MODES = Object.freeze(['ASK', 'PLAN', 'ACT', 'VERIFY', 'IMPROVE', 'AUTOPILOT', 'AUDIT', 'OFFLINE']);
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

class AssuranceTreeProvider {
  constructor(context, client) {
    this.context = context;
    this.client = client;
    this._onDidChangeTreeData = new vscode.EventEmitter();
    this.onDidChangeTreeData = this._onDidChangeTreeData.event;
    this.status = null;
    this.error = null;
  }

  refresh() { this._onDidChangeTreeData.fire(undefined); }
  getTreeItem(element) { return element; }

  async getChildren(element) {
    if (element) return element.children || [];
    try {
      this.status = await this.client.request('/v1/status');
      this.error = null;
    } catch (error) {
      this.error = error.message;
    }
    const items = [];
    if (this.error) {
      items.push(item('Local API', 'Unavailable', 'error', 'onsure.configure'));
      items.push(item('Reason', this.error, 'warning'));
    } else if (this.status) {
      items.push(item('Runtime', this.status.state || 'UNKNOWN', 'server-process'));
      items.push(item('Program Learning', this.status.program_learning || 'UNKNOWN', 'symbol-class'));
      items.push(item('Behavior Learning', this.status.behavior_learning || 'UNKNOWN', 'pulse'));
      items.push(item('Planning / Review / RCA', this.status.planning_review_rca || 'UNKNOWN', 'checklist'));
      items.push(item('Validation', this.status.validation || 'UNKNOWN', 'beaker'));
      items.push(item('Improvement', this.status.patch_application || 'UNKNOWN', 'diff'));
      items.push(item('Improvement Proof', this.status.improvement_proof || 'UNKNOWN', 'verified-filled'));
      items.push(item('Git Delivery', this.status.git_delivery || 'UNKNOWN', 'git-pull-request'));
      items.push(item('OLicense', this.status.license || 'UNKNOWN', 'key'));
      items.push(item('Service Case', this.status.service_case || 'UNKNOWN', 'briefcase'));
      items.push(item('Independent OTester', this.status.independent_otester || 'NOT_RUN', 'shield'));
      items.push(item('Independent OAudit', this.status.independent_oaudit || 'NOT_RUN', 'verified'));
    }
    const lastProfile = this.context.workspaceState.get(LAST_PROFILE_KEY);
    const lastRun = this.context.workspaceState.get(LAST_RUN_KEY);
    const lastWorkflow = this.context.workspaceState.get(LAST_WORKFLOW_KEY);
    const identity = this.context.workspaceState.get(REGISTERED_IDENTITY_KEY);
    const workMode = this.context.workspaceState.get(WORK_MODE_KEY)
      || vscode.workspace.getConfiguration('onsure').get('defaultWorkMode') || 'ASK';
    items.push(item('Work Mode', workMode, 'symbol-enum', 'onsure.selectMode'));
    if (identity) items.push(item(
      'Registered Target', `${identity.projectId}/${identity.targetId}`, 'workspace-trusted'));
    if (lastProfile) items.push(item('Last Program Profile', lastProfile, 'json'));
    if (lastRun) items.push(item('Last Run', lastRun, 'folder-opened', 'onsure.openLastArtifact'));
    if (lastWorkflow) items.push(item('Last Workflow', lastWorkflow, 'run-all'));
    return items;
  }
}

function item(label, description, icon, command) {
  const value = new vscode.TreeItem(label, vscode.TreeItemCollapsibleState.None);
  value.description = String(description);
  value.tooltip = `${label}: ${description}`;
  value.iconPath = new vscode.ThemeIcon(icon);
  if (command) value.command = { command, title: label };
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

async function activate(context) {
  const client = new ApiClient(context);
  const provider = new AssuranceTreeProvider(context, client);
  const views = VIEW_IDS.map(viewId =>
    vscode.window.createTreeView(viewId, { treeDataProvider: provider }));
  const output = vscode.window.createOutputChannel('ONSure');
  const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  statusBar.text = '$(shield) ONSure: NON_FINAL';
  statusBar.tooltip = 'ONSure self-validation is nonfinal until independent gates pass.';
  statusBar.command = 'onsure.refresh';
  statusBar.show();

  async function executeWorkflow(operation, request, title) {
    const workflow = await vscode.window.withProgress({
      location: vscode.ProgressLocation.Notification,
      title: `ONSure: ${title}`,
      cancellable: false
    }, () => client.workflow(operation, request));
    await context.workspaceState.update(LAST_WORKFLOW_KEY, operation);
    const result = workflow.result || {};
    if (result.run_root) await context.workspaceState.update(LAST_RUN_KEY, result.run_root);
    output.appendLine(`[${new Date().toISOString()}] ${operation}: ${JSON.stringify(result)}`);
    provider.refresh();
    await showJson(`${title} — SELF_VALIDATION_NONFINAL`, workflow);
    return workflow;
  }

  async function registerActiveWorkspace() {
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
    provider.refresh();
    await showJson('Registered target — SELF_VALIDATION_NONFINAL', read);
  }

  context.subscriptions.push(...views, output, statusBar,
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
      provider.refresh();
    }),
    vscode.commands.registerCommand('onsure.clearToken', async () => {
      await context.secrets.delete(TOKEN_KEY);
      vscode.window.showInformationMessage('ONSure Local API token cleared.');
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
      provider.refresh();
    }),
    vscode.commands.registerCommand('onsure.refresh', async () => provider.refresh()),
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
          'validation-report.json', 'program-profile.json', 'execution-plan.json',
          'behavior-profile.json', 'review-result.json', 'evidence-based-rca.json', 'patch-plan.json'
        ], { title: 'ONSure Run Artifact' });
        if (!artifact) return;
        const result = await client.request('/v1/run-artifact', 'POST', { run_root: runRoot, artifact });
        await showJson(artifact, result.body);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure artifact open failed: ${error.message}`);
      }
    })
  );
  provider.refresh();
}

function deactivate() {}

module.exports = { activate, deactivate };
