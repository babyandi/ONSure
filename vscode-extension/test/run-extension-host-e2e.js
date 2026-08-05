'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const extensionRoot = path.resolve(__dirname, '..');
const workspaceRoot = path.resolve(extensionRoot, '..');
const testEntry = path.resolve(__dirname, 'extension-host', 'index.js');
const vscodeVersion = process.env.ONSURE_VSCODE_TEST_VERSION || '1.95.3';

function commandAvailable(command) {
  const result = spawnSync(command, ['--version'], { encoding: 'utf8' });
  return !result.error;
}

function preflight() {
  let testElectronAvailable = true;
  try { require.resolve('@vscode/test-electron'); }
  catch { testElectronAvailable = false; }
  const displayAvailable = Boolean(process.env.DISPLAY || process.env.WAYLAND_DISPLAY);
  const xvfbAvailable = commandAvailable('xvfb-run');
  const blockers = [];
  if (!testElectronAvailable) blockers.push('NPM_CI_REQUIRED');
  if (process.platform === 'linux' && !displayAvailable && !xvfbAvailable) {
    blockers.push('DISPLAY_OR_XVFB_REQUIRED');
  }
  return {
    contract: 'ONSURE_VSCODE_EXTENSION_HOST_E2E_PREFLIGHT_V1',
    state: blockers.length ? 'NOT_RUN' : 'READY',
    vscode_version: vscodeVersion,
    test_electron_available: testElectronAvailable,
    display_available: displayAvailable,
    xvfb_available: xvfbAvailable,
    blockers,
    external_network_may_be_required_for_first_vscode_download: true,
    github_actions_used: false,
    final_claim_allowed: false
  };
}

async function directRun() {
  const { runTests } = require('@vscode/test-electron');
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'onsure-vscode-e2e-'));
  try {
    await runTests({
      version: vscodeVersion,
      extensionDevelopmentPath: extensionRoot,
      extensionTestsPath: testEntry,
      launchArgs: [
        workspaceRoot,
        '--disable-gpu', '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes',
        '--user-data-dir', path.join(temporary, 'user-data'),
        '--extensions-dir', path.join(temporary, 'extensions')
      ],
      extensionTestsEnv: {
        ONSURE_EXTENSION_HOST_E2E: '1',
        ONSURE_EXTERNAL_NETWORK_ALLOWED: '0',
        ONSURE_FINAL_CLAIM_ALLOWED: 'false'
      }
    });
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
}

async function main() {
  const status = preflight();
  if (process.argv.includes('--preflight')) {
    process.stdout.write(`${JSON.stringify(status, null, 2)}\n`);
    return;
  }
  if (process.argv.includes('--direct')) {
    await directRun();
    return;
  }
  if (status.state !== 'READY') {
    process.stdout.write(`${JSON.stringify(status, null, 2)}\n`);
    process.exitCode = 77;
    return;
  }
  if (process.platform === 'linux' && !process.env.DISPLAY && commandAvailable('xvfb-run')) {
    const result = spawnSync('xvfb-run', ['-a', process.execPath, __filename, '--direct'], {
      cwd: extensionRoot, stdio: 'inherit', env: process.env
    });
    process.exitCode = result.status ?? 1;
    return;
  }
  await directRun();
}

main().catch(error => {
  process.stderr.write(`ONSURE_VSCODE_EXTENSION_HOST_E2E_FAIL ${error.stack || error}\n`);
  process.exitCode = 1;
});
