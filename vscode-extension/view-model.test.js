'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { rowsForView, budgetRowsFromExecutionPlan, providerRowsFromStatusAndUsage, VIEW_IDS } = require('./view-model');

test('rowsForView resolves $-prefixed keys from state and status keys from state.status', () => {
  const rows = rowsForView('onsure.workspace', { status: {}, mode: 'ACT' });
  assert.equal(rows.find(row => row.label === 'Mode').description, 'ACT');
});

test('rowsForView throws for an unknown view id', () => {
  assert.throws(() => rowsForView('onsure.does-not-exist', { status: {} }), /Unknown ONSure view/);
});

test('every declared VIEW_IDS entry resolves without throwing', () => {
  for (const viewId of VIEW_IDS) {
    assert.doesNotThrow(() => rowsForView(viewId, { status: {} }));
  }
});

test('budgetRowsFromExecutionPlan returns no rows for a non-execution-plan document', () => {
  assert.deepEqual(budgetRowsFromExecutionPlan(null), []);
  assert.deepEqual(budgetRowsFromExecutionPlan({}), []);
  assert.deepEqual(budgetRowsFromExecutionPlan({ contract: 'SOMETHING_ELSE' }), []);
});

test('budgetRowsFromExecutionPlan surfaces token estimate, cost ceiling, data transfer scope and scenario count', () => {
  const plan = {
    contract: 'ONSURE_EXECUTION_PLAN_V1',
    execution_budget: {
      expected_result: 'Execute 3 allowed action(s)...',
      token_estimate: 1250,
      cost_ceiling_micros: 0,
      data_transfer_scope: 'LOCAL_ONLY'
    },
    scenario_expectations: [
      { scenario_class: 'NORMAL', expected_result: '...' },
      { scenario_class: 'FAILURE', expected_result: '...' }
    ]
  };

  const rows = budgetRowsFromExecutionPlan(plan);
  const byLabel = Object.fromEntries(rows.map(row => [row.label, row.description]));
  assert.equal(byLabel['Token Estimate'], '1250');
  assert.equal(byLabel['Cost Ceiling (micros)'], '0');
  assert.equal(byLabel['Data Transfer Scope'], 'LOCAL_ONLY');
  assert.equal(byLabel['Scenario Expectations'], '2 registered');
});

test('budgetRowsFromExecutionPlan attaches one child per scenario expectation with scenario_class/expected_result mapped to label/description', () => {
  const plan = {
    contract: 'ONSURE_EXECUTION_PLAN_V1',
    execution_budget: {
      token_estimate: 1250,
      cost_ceiling_micros: 0,
      data_transfer_scope: 'LOCAL_ONLY'
    },
    scenario_expectations: [
      { scenario_class: 'NORMAL', expected_result: 'Executes the happy path.' },
      { scenario_class: 'FAILURE', expected_result: 'Rejects malformed input.' }
    ]
  };

  const rows = budgetRowsFromExecutionPlan(plan);
  const scenarioRow = rows.find(row => row.label === 'Scenario Expectations');
  assert.ok(Array.isArray(scenarioRow.children));
  assert.equal(scenarioRow.children.length, 2);
  assert.deepEqual(scenarioRow.children[0], {
    label: 'NORMAL', description: 'Executes the happy path.', icon: 'symbol-event'
  });
  assert.deepEqual(scenarioRow.children[1], {
    label: 'FAILURE', description: 'Rejects malformed input.', icon: 'symbol-event'
  });

  // The other three rows are unaffected -- no children attached.
  for (const label of ['Token Estimate', 'Cost Ceiling (micros)', 'Data Transfer Scope']) {
    assert.equal(rows.find(row => row.label === label).children, undefined);
  }
});

test('budgetRowsFromExecutionPlan gives Scenario Expectations an empty children array (not undefined) when there are zero scenarios', () => {
  const plan = {
    contract: 'ONSURE_EXECUTION_PLAN_V1',
    execution_budget: { token_estimate: 10, cost_ceiling_micros: 0, data_transfer_scope: 'LOCAL_ONLY' },
    scenario_expectations: []
  };
  const scenarioRow = budgetRowsFromExecutionPlan(plan).find(row => row.label === 'Scenario Expectations');
  assert.deepEqual(scenarioRow.children, []);
  assert.equal(scenarioRow.description, '0 registered');
});

test('budgetRowsFromExecutionPlan gives Scenario Expectations an empty children array when scenario_expectations is missing entirely', () => {
  const scenarioRow = budgetRowsFromExecutionPlan({ contract: 'ONSURE_EXECUTION_PLAN_V1' })
    .find(row => row.label === 'Scenario Expectations');
  assert.deepEqual(scenarioRow.children, []);
  assert.equal(scenarioRow.description, '0 registered');
});

test('budgetRowsFromExecutionPlan still returns no rows at all for a non-execution-plan document (guard unaffected by children change)', () => {
  assert.deepEqual(budgetRowsFromExecutionPlan(null), []);
  assert.deepEqual(budgetRowsFromExecutionPlan({}), []);
  assert.deepEqual(budgetRowsFromExecutionPlan({ contract: 'SOMETHING_ELSE' }), []);
});

test('budgetRowsFromExecutionPlan degrades to NOT_AVAILABLE instead of throwing when fields are missing', () => {
  const rows = budgetRowsFromExecutionPlan({ contract: 'ONSURE_EXECUTION_PLAN_V1' });
  const byLabel = Object.fromEntries(rows.map(row => [row.label, row.description]));
  assert.equal(byLabel['Token Estimate'], 'NOT_AVAILABLE');
  assert.equal(byLabel['Cost Ceiling (micros)'], 'NOT_AVAILABLE');
  assert.equal(byLabel['Data Transfer Scope'], 'NOT_AVAILABLE');
  assert.equal(byLabel['Scenario Expectations'], '0 registered');
});

test('providerRowsFromStatusAndUsage lists registered providers with usage figures per provider', () => {
  const status = {
    state: 'PROVIDERS_REGISTERED',
    providers: [
      { provider_id: 'fake-alpha', declared_model_ids: ['alpha-v1'], supported_task_classes: ['REVIEW'] },
      { provider_id: 'fake-beta', declared_model_ids: ['beta-v1', 'beta-v2'], supported_task_classes: ['REVIEW', 'PLANNING'] }
    ]
  };
  const usage = {
    overall: { invocationCount: 3, totalInputTokens: 350, totalOutputTokens: 140, totalCostMicros: 35 },
    providers: {
      'fake-alpha': { invocationCount: 2, totalInputTokens: 300, totalOutputTokens: 125, totalCostMicros: 30 },
      'fake-beta': { invocationCount: 1, totalInputTokens: 50, totalOutputTokens: 15, totalCostMicros: 5 }
    }
  };

  const rows = providerRowsFromStatusAndUsage(status, usage);
  assert.equal(rows.length, 2);

  const providersRow = rows.find(row => row.label === 'Model Providers');
  assert.equal(providersRow.description, '2 registered');
  assert.equal(providersRow.children.length, 2);
  assert.deepEqual(providersRow.children[0], {
    label: 'fake-alpha', description: 'models: alpha-v1 · task classes: REVIEW', icon: 'server-process'
  });
  assert.deepEqual(providersRow.children[1], {
    label: 'fake-beta', description: 'models: beta-v1, beta-v2 · task classes: REVIEW, PLANNING', icon: 'server-process'
  });

  const usageRow = rows.find(row => row.label === 'Token Usage');
  assert.equal(usageRow.description, '3 call(s) · 350 in / 140 out tokens · 35 micros');
  assert.equal(usageRow.children.length, 2);
  assert.deepEqual(usageRow.children[0], {
    label: 'fake-alpha', description: '2 call(s) · 300 in / 125 out tokens · 30 micros', icon: 'symbol-number'
  });
});

test('providerRowsFromStatusAndUsage reports no providers registered yet for the NO_PROVIDER_REGISTERED degrade state', () => {
  const status = { state: 'NO_PROVIDER_REGISTERED', providers: [] };
  const usage = { overall: { invocationCount: 0, totalInputTokens: 0, totalOutputTokens: 0, totalCostMicros: 0 }, providers: {} };

  const rows = providerRowsFromStatusAndUsage(status, usage);
  const providersRow = rows.find(row => row.label === 'Model Providers');
  assert.equal(providersRow.description, 'No providers registered yet');
  assert.deepEqual(providersRow.children, []);

  const usageRow = rows.find(row => row.label === 'Token Usage');
  assert.equal(usageRow.description, 'No usage recorded yet');
  assert.deepEqual(usageRow.children, []);
});

test('providerRowsFromStatusAndUsage reports no usage recorded yet when providers are registered but the ledger is empty', () => {
  const status = {
    state: 'PROVIDERS_REGISTERED',
    providers: [{ provider_id: 'fake-alpha', declared_model_ids: ['alpha-v1'], supported_task_classes: ['REVIEW'] }]
  };
  const usage = { overall: { invocationCount: 0, totalInputTokens: 0, totalOutputTokens: 0, totalCostMicros: 0 }, providers: {} };

  const rows = providerRowsFromStatusAndUsage(status, usage);
  const providersRow = rows.find(row => row.label === 'Model Providers');
  assert.equal(providersRow.description, '1 registered');
  assert.equal(providersRow.children.length, 1);

  const usageRow = rows.find(row => row.label === 'Token Usage');
  assert.equal(usageRow.description, 'No usage recorded yet');
  assert.deepEqual(usageRow.children, []);
});

test('providerRowsFromStatusAndUsage handles a mix: some providers registered with usage, some registered with none yet', () => {
  const status = {
    state: 'PROVIDERS_REGISTERED',
    providers: [
      { provider_id: 'fake-alpha', declared_model_ids: ['alpha-v1'], supported_task_classes: ['REVIEW'] },
      { provider_id: 'fake-beta', declared_model_ids: [], supported_task_classes: [] }
    ]
  };
  const usage = {
    overall: { invocationCount: 1, totalInputTokens: 100, totalOutputTokens: 40, totalCostMicros: 10 },
    providers: { 'fake-alpha': { invocationCount: 1, totalInputTokens: 100, totalOutputTokens: 40, totalCostMicros: 10 } }
  };

  const rows = providerRowsFromStatusAndUsage(status, usage);
  const providersRow = rows.find(row => row.label === 'Model Providers');
  assert.equal(providersRow.children.length, 2);
  assert.equal(providersRow.children[1].description, 'models: NONE · task classes: NONE');

  const usageRow = rows.find(row => row.label === 'Token Usage');
  assert.equal(usageRow.description, '1 call(s) · 100 in / 40 out tokens · 10 micros');
  assert.equal(usageRow.children.length, 1);
  assert.equal(usageRow.children[0].label, 'fake-alpha');
});

test('providerRowsFromStatusAndUsage degrades gracefully instead of throwing when status/usage are missing entirely', () => {
  const rows = providerRowsFromStatusAndUsage(undefined, undefined);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].description, 'No providers registered yet');
  assert.equal(rows[1].description, 'No usage recorded yet');
});
