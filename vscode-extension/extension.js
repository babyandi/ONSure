'use strict';

const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const { WORK_MODES, classifyOperation, authorize } = require('./work-mode-policy');
const { VIEW_IDS, rowsForView } = require('./view-model');
const { reviewPlanApproval, reviewHunkApproval } = require('./approval-review');

const TOKEN_KEY = 'onsure.localApiToken';
const LAST_RUN_KEY = 'onsure.lastRunRoot';
const LAST_PROFILE_KEY = 'onsure.lastProgramProfile';
const LAST_WORKFLOW_KEY = 'onsure.lastWorkflowOperation';
const LAST_RESTORE_KEY = 'onsure.lastRestartRestore';
const WORK_MODE_KEY = 'onsure.workMode';

class ApiClient {
  constructor(context) { this.context = context; }

  get baseUrl() {
    const configured = vscode.workspace.getConfiguration('onsure').get('localApiUrl');
    const value = String(configured || '').replace(/\/$/, '');
    if (!/^http:\/\/(?:127\.0\.0\.1|localhost|\[::1\]):\d{2,5}$/.test(value)) {
      throw new Error('ONSure Local API must use an explicit loopback HTTP URL.');
    }
    return value;
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
        throw new Error(payload.message || payload.error || `Local API returned ${response.status}.`);
      }
      return payload;
    } finally {
      clearTimeout(timeout);
    }
  }

  async workflow(operation, request) {
    const response = await this.request('/v1/workflow', 'POST', { operation, request });
    if (!response.workflow || response.workflow.operation !== operation) {
      throw new Error('Local API workflow response is not bound to the requested operation.');
    }
    return response.workflow;
  }
}

class AssuranceTreeProvider {
  constructor(context, client, viewId) {
    this.context = context;
    this.client = client;
    this.viewId = viewId;
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
    }
    const lastProfile = this.context.workspaceState.get(LAST_PROFILE_KEY);
    const lastRun = this.context.workspaceState.get(LAST_RUN_KEY);
    const lastWorkflow = this.context.workspaceState.get(LAST_WORKFLOW_KEY);
    const lastRestore = this.context.workspaceState.get(LAST_RESTORE_KEY);
    const workMode = this.context.workspaceState.get(WORK_MODE_KEY)
      || vscode.workspace.getConfiguration('onsure').get('defaultWorkMode') || 'ASK';
    const state = {
      status: this.status || {}, mode: workMode, profile: lastProfile, run: lastRun,
      workflow: lastWorkflow,
      restore: lastRestore ? `${lastRestore.recovered_count || 0} job(s) paused` : undefined
    };
    for (const row of rowsForView(this.viewId, state)) {
      const command = row.label === 'Mode' ? 'onsure.selectMode'
        : row.label === 'Last Run' && lastRun ? 'onsure.openLastArtifact' : undefined;
      items.push(item(row.label, row.description, row.icon, command));
    }
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

async function selectJsonFile(title) {
  const selected = await vscode.window.showOpenDialog({
    title, canSelectMany: false, canSelectFiles: true, canSelectFolders: false,
    filters: { JSON: ['json'] }, defaultUri: vscode.Uri.file(workspaceRoot())
  });
  if (!selected?.length) return undefined;
  return requireInsideWorkspace(selected[0].fsPath);
}

function readBoundedJson(file) {
  const raw = fs.readFileSync(file, 'utf8');
  if (raw.length > 1024 * 1024) throw new Error('Approval input exceeds 1 MiB.');
  const value = JSON.parse(raw);
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Approval input must be a JSON object.');
  }
  return value;
}

async function confirmApprovalReview(review) {
  await showJson(`${review.review_type} — SIGNATURE NOT YET CONSUMED`, review);
  const count = review.approved_actions?.length || review.approved_hunk_ids?.length || 0;
  const answer = await vscode.window.showWarningMessage(
    `Consume ${review.scope.toLowerCase()} approval for ${count} item(s)? ` +
      'The Core will verify the signature, fixed trust root, expiry and replay state.',
    { modal: true, detail: 'This action consumes the signed receipt. It cannot authorize Merge, Final or GO.' },
    'Consume Signed Approval');
  return answer === 'Consume Signed Approval';
}

async function activate(context) {
  const client = new ApiClient(context);
  const providers = VIEW_IDS.map(viewId => new AssuranceTreeProvider(context, client, viewId));
  const views = providers.map((provider, index) =>
    vscode.window.createTreeView(VIEW_IDS[index], { treeDataProvider: provider }));
  const refreshViews = () => providers.forEach(provider => provider.refresh());
  const output = vscode.window.createOutputChannel('ONSure');
  const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  statusBar.text = '$(shield) ONSure: NON_FINAL';
  statusBar.tooltip = 'ONSure self-validation is nonfinal until independent gates pass.';
  statusBar.command = 'onsure.refresh';
  const existingToken = await context.secrets.get(TOKEN_KEY);
  if (existingToken) {
    try {
      const restored = await client.workflow('job.recover', {
        actor: 'vscode-extension-restart-controller'
      });
      await context.workspaceState.update(LAST_RESTORE_KEY, restored.result);
      output.appendLine(`Restart recovery: ${JSON.stringify(restored.result)}`);
    } catch (error) {
      output.appendLine(`Restart recovery deferred (nonfinal): ${error.message}`);
    }
  }
  statusBar.show();

  async function executeWorkflow(operation, request, title) {
    const mode = context.workspaceState.get(WORK_MODE_KEY)
      || vscode.workspace.getConfiguration('onsure').get('defaultWorkMode') || 'ASK';
    const decision = authorize(mode, classifyOperation(operation), operation);
    if (!decision.allowed) throw new Error(`Work mode policy denied ${operation}: ${decision.reason}`);
    const workflow = await vscode.window.withProgress({
      location: vscode.ProgressLocation.Notification,
      title: `ONSure: ${title}`,
      cancellable: false
    }, () => client.workflow(operation, request));
    await context.workspaceState.update(LAST_WORKFLOW_KEY, operation);
    const result = workflow.result || {};
    if (result.run_root) await context.workspaceState.update(LAST_RUN_KEY, result.run_root);
    if (result.output_file) await context.workspaceState.update(LAST_PROFILE_KEY, result.output_file);
    output.appendLine(`[${new Date().toISOString()}] ${operation}: ${JSON.stringify(result)}`);
    refreshViews();
    await showJson(`${title} — SELF_VALIDATION_NONFINAL`, workflow);
    return workflow;
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
      refreshViews();
    }),
    vscode.commands.registerCommand('onsure.clearToken', async () => {
      await context.secrets.delete(TOKEN_KEY);
      vscode.window.showInformationMessage('ONSure Local API token cleared.');
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
      refreshViews();
    }),
    vscode.commands.registerCommand('onsure.refresh', async () => refreshViews()),
    vscode.commands.registerCommand('onsure.learnProgram', async () => {
      try {
        const root = workspaceRoot();
        const projectId = await vscode.window.showInputBox({
          title: 'Project ID', value: path.basename(root),
          validateInput: value => /^[A-Za-z0-9._:-]{1,160}$/.test(value) ? undefined : 'Invalid project ID.'
        });
        if (!projectId) return;
        const targetId = await vscode.window.showInputBox({
          title: 'Registered Target ID', value: path.basename(root),
          validateInput: value => /^[A-Za-z0-9._:-]{1,160}$/.test(value) ? undefined : 'Invalid target ID.'
        });
        if (!targetId) return;
        const outputFile = path.join(root, '.onsure', 'profiles', targetId, 'program-profile.json');
        await executeWorkflow('program.learn', {
          project_id: projectId, target_id: targetId, program_id: targetId
        }, 'Learning program');
        await context.workspaceState.update(LAST_PROFILE_KEY, outputFile);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure program learning failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.runValidation', async () => {
      try {
        const root = workspaceRoot();
        const projectId = await vscode.window.showInputBox({
          title: 'Project ID', value: path.basename(root),
          validateInput: value => /^[A-Za-z0-9._:-]{1,160}$/.test(value) ? undefined : 'Invalid project ID.'
        });
        if (!projectId) return;
        const targetId = await vscode.window.showInputBox({
          title: 'Registered Target ID', value: path.basename(root),
          validateInput: value => /^[A-Za-z0-9._:-]{1,160}$/.test(value) ? undefined : 'Invalid target ID.'
        });
        if (!targetId) return;
        await executeWorkflow('validation.run', {
          project_id: projectId,
          target_id: targetId
        }, 'Running validation');
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
    vscode.commands.registerCommand('onsure.approvePlan', async () => {
      try {
        const planFile = await selectJsonFile('Select original Execution Plan JSON');
        if (!planFile) return;
        const receiptFile = await selectJsonFile('Select signed Execution Plan Approval Receipt JSON');
        if (!receiptFile) return;
        const review = reviewPlanApproval(readBoundedJson(planFile), readBoundedJson(receiptFile));
        if (!await confirmApprovalReview(review)) return;
        await executeWorkflow('plan.approve', {
          plan_file: planFile, signed_approval_receipt: receiptFile
        }, `${review.scope} plan approval`);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure plan approval failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.applyApprovedPatch', async () => {
      try {
        const planFile = await selectJsonFile('Select Patch Plan JSON');
        if (!planFile) return;
        const receiptFile = await selectJsonFile('Select signed Hunk Approval Receipt JSON');
        if (!receiptFile) return;
        const review = reviewHunkApproval(readBoundedJson(planFile), readBoundedJson(receiptFile));
        if (!await confirmApprovalReview(review)) return;
        await executeWorkflow('patch.apply', {
          repository_root: workspaceRoot(), patch_plan_file: planFile,
          approval_receipt_file: receiptFile
        }, `${review.scope} hunk approval`);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure approved patch failed: ${error.message}`);
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
  refreshViews();
}

function deactivate() {}

module.exports = { activate, deactivate };
