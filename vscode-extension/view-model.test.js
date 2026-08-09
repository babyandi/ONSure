'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { rowsForView, budgetRowsFromExecutionPlan, VIEW_IDS } = require('./view-model');

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
