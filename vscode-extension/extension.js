'use strict';

const vscode = require('vscode');
const crypto = require('crypto');
const path = require('path');

const TOKEN_KEY = 'onsure.localApiToken';
const LAST_RUN_KEY = 'onsure.lastRunRoot';
const LAST_PROFILE_KEY = 'onsure.lastProgramProfile';

class ApiClient {
  constructor(context) {
    this.context = context;
  }

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
    const timeout = setTimeout(() => controller.abort(), 120000);
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

  refresh() {
    this._onDidChangeTreeData.fire(undefined);
  }

  getTreeItem(element) {
    return element;
  }

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
      items.push(item('Validation', this.status.validation || 'UNKNOWN', 'beaker'));
      items.push(item('Improvement', this.status.patch_application || 'UNKNOWN', 'diff'));
      items.push(item('Git Delivery', this.status.git_delivery || 'UNKNOWN', 'git-pull-request'));
      items.push(item('Independent OTester', this.status.independent_otester || 'NOT_RUN', 'shield'));
      items.push(item('Independent OAudit', this.status.independent_oaudit || 'NOT_RUN', 'verified'));
    }
    const lastRun = this.context.workspaceState.get(LAST_RUN_KEY);
    const lastProfile = this.context.workspaceState.get(LAST_PROFILE_KEY);
    if (lastProfile) items.push(item('Last Program Profile', lastProfile, 'json'));
    if (lastRun) items.push(item('Last Run', lastRun, 'folder-opened', 'onsure.openLastArtifact'));
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
  const view = vscode.window.createTreeView('onsure.workspace', { treeDataProvider: provider });
  const output = vscode.window.createOutputChannel('ONSure');
  const statusBar = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  statusBar.text = '$(shield) ONSure: NON_FINAL';
  statusBar.tooltip = 'ONSure self-validation is nonfinal until independent gates pass.';
  statusBar.command = 'onsure.refresh';
  statusBar.show();

  context.subscriptions.push(view, output, statusBar,
    vscode.commands.registerCommand('onsure.configure', async () => {
      const url = await vscode.window.showInputBox({
        title: 'ONSure Local API URL',
        value: client.baseUrl,
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
    vscode.commands.registerCommand('onsure.refresh', async () => provider.refresh()),
    vscode.commands.registerCommand('onsure.learnProgram', async () => {
      try {
        const root = workspaceRoot();
        const projectId = await vscode.window.showInputBox({
          title: 'Project ID', value: path.basename(root),
          validateInput: value => /^[A-Za-z0-9._:-]{1,160}$/.test(value) ? undefined : 'Invalid project ID.'
        });
        if (!projectId) return;
        const programId = await vscode.window.showInputBox({
          title: 'Program ID', value: path.basename(root),
          validateInput: value => /^[A-Za-z0-9._:-]{1,160}$/.test(value) ? undefined : 'Invalid program ID.'
        });
        if (!programId) return;
        const outputFile = path.join(root, '.onsure', 'profiles', 'program-profile.json');
        const result = await vscode.window.withProgress({
          location: vscode.ProgressLocation.Notification,
          title: 'ONSure: Learning program', cancellable: false
        }, () => client.request('/v1/program-profile', 'POST', {
          source_root: root, project_id: projectId, program_id: programId, output_file: outputFile
        }));
        await context.workspaceState.update(LAST_PROFILE_KEY, result.output_file);
        output.appendLine(`[${new Date().toISOString()}] Program profile: ${result.output_file}`);
        provider.refresh();
        await showJson('Program Profile Candidate', result);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure program learning failed: ${error.message}`);
      }
    }),
    vscode.commands.registerCommand('onsure.runValidation', async () => {
      try {
        const root = workspaceRoot();
        const targetType = await vscode.window.showQuickPick(
          ['GENERAL_SOFTWARE', 'AI_APPLICATION'],
          { title: 'ONSure Target Type', placeHolder: 'Select the registered target type.' });
        if (!targetType) return;
        const targetId = await vscode.window.showInputBox({
          title: 'Target ID', value: path.basename(root),
          validateInput: value => /^[A-Za-z0-9._:-]{1,160}$/.test(value) ? undefined : 'Invalid target ID.'
        });
        if (!targetId) return;
        const result = await vscode.window.withProgress({
          location: vscode.ProgressLocation.Notification,
          title: 'ONSure: Running self-validation (nonfinal)', cancellable: false
        }, () => client.request('/v1/validate', 'POST', {
          source_root: root,
          store_root: path.join(root, '.onsure', 'validation-data'),
          target_id: targetId,
          target_name: targetId,
          target_type: targetType,
          adapter_id: 'ONSURE_GENERIC_MANIFEST_V1',
          policy_profile: 'ONSURE_DEFAULT_POLICY_V1',
          execution_profile: vscode.workspace.getConfiguration('onsure').get('defaultExecutionProfile')
        }));
        await context.workspaceState.update(LAST_RUN_KEY, result.run_root);
        output.appendLine(`[${new Date().toISOString()}] Validation ${result.decision}: ${result.run_root}`);
        provider.refresh();
        await showJson(`Validation ${result.decision}`, result);
      } catch (error) {
        vscode.window.showErrorMessage(`ONSure validation failed: ${error.message}`);
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
        const result = await client.request('/v1/run-artifact', 'POST', {
          run_root: runRoot, artifact
        });
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
