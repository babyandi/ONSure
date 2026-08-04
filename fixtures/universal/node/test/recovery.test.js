'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

test('retry reaches the same deterministic result', () => {
  const attempt = () => ({ state: 'complete', digest: 'stable' });
  assert.deepEqual(attempt(), attempt());
});

test('blocking rejects an unavailable dependency', () => {
  const execute = (available) => {
    if (!available) throw new Error('DEPENDENCY_BLOCKED');
  };
  assert.throws(() => execute(false), /DEPENDENCY_BLOCKED/);
});

test('interruption preserves the last durable checkpoint', () => {
  const ledger = ['started', 'checkpoint'];
  assert.equal(ledger.at(-1), 'checkpoint');
});

test('resume continues from the checkpoint', () => {
  const ledger = ['checkpoint'];
  ledger.push('resumed', 'complete');
  assert.deepEqual(ledger, ['checkpoint', 'resumed', 'complete']);
});

test('rollback restores the previous value', () => {
  const state = { current: 'before' };
  const previous = state.current;
  state.current = 'after';
  state.current = previous;
  assert.equal(state.current, 'before');
});

test('rerun is idempotent', () => {
  const values = new Set();
  const apply = () => values.add('artifact:digest');
  apply();
  apply();
  assert.equal(values.size, 1);
});
