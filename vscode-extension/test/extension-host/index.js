'use strict';

const assert = require('node:assert/strict');
const vscode = require('vscode');
const { conversationResponse } = require('../../extension-core');

async function run() {
  const extension = vscode.extensions.getExtension('babyandi.onsure');
  assert.ok(extension, 'babyandi.onsure extension must be installed in the test host');
  await extension.activate();
  assert.equal(extension.isActive, true);
  const commands = new Set(await vscode.commands.getCommands(true));
  for (const command of [
    'onsure.configure', 'onsure.clearToken', 'onsure.selectMode', 'onsure.askOrPlan', 'onsure.runValidation',
    'onsure.autopilotPause', 'onsure.autopilotResume', 'onsure.autopilotCancel'
  ]) assert.ok(commands.has(command), `${command} must be registered`);
  const configuration = vscode.workspace.getConfiguration('onsure');
  assert.equal(configuration.get('localApiUrl'), 'http://127.0.0.1:47311');
  assert.equal(configuration.get('llmGatewayUrl'), 'http://127.0.0.1:47312');

  const response = conversationResponse('ASK', 'Extension Host smoke test', {});
  assert.equal(response.provider_invoked, false);
  assert.equal(response.source_mutation_allowed, false);
  assert.equal(response.final_claim_allowed, false);
}

module.exports = { run };
