'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { render } = require('../src/pipeline');

test('normal render path', () => {
  assert.deepEqual(JSON.parse(render({ title: 'Neutral' })), { title: 'Neutral', exposed: false });
});

test('invalid request is blocked', () => {
  assert.throws(() => render({ title: '' }), /TITLE_REQUIRED/);
});
