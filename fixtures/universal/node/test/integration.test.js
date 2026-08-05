'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { render } = require('../src/pipeline');

test('request through artifact read-back and exposure decision', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'onsure-neutral-'));
  try {
    const artifact = path.join(directory, 'artifact.json');
    fs.writeFileSync(artifact, render({ title: 'Neutral E2E' }));
    const readBack = JSON.parse(fs.readFileSync(artifact, 'utf8'));
    assert.deepEqual(readBack, { title: 'Neutral E2E', exposed: false });
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
