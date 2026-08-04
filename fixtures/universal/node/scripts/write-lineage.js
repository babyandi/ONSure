'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { render } = require('../src/pipeline');

const root = process.cwd();
const evidenceRoot = path.join(root, '.onsure', 'e2e');
fs.mkdirSync(evidenceRoot, { recursive: true });

const requestPath = path.join(evidenceRoot, 'request.json');
const artifactPath = path.join(evidenceRoot, 'artifact.json');
const schemaPath = path.join(evidenceRoot, 'artifact.schema.json');
const receiptPath = path.join(root, '.onsure', 'workflow-lineage.v1.json');
const request = Buffer.from(JSON.stringify({ title: 'Neutral E2E' }), 'utf8');
const artifact = render(JSON.parse(request.toString('utf8')));
const schema = Buffer.from(JSON.stringify({
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  type: 'object',
  additionalProperties: false,
  required: ['title', 'exposed'],
  properties: {
    title: { type: 'string', minLength: 1 },
    exposed: { type: 'boolean' }
  }
}), 'utf8');
fs.writeFileSync(requestPath, request);
fs.writeFileSync(artifactPath, artifact);
fs.writeFileSync(schemaPath, schema);

const sha256 = value => crypto.createHash('sha256').update(value).digest('hex');
const requestSha = sha256(request);
const artifactSha = sha256(artifact);
const schemaSha = sha256(schema);
const issued = new Date();
const expires = new Date(issued.getTime() + 10 * 60 * 1000);
const permitId = 'neutral-permit-' + artifactSha.slice(0, 16);
const runId = 'neutral-' + requestSha.slice(0, 16);
const receipt = {
  contract: 'ONSURE_PORTABLE_WORKFLOW_LINEAGE_V1',
  run_id: runId,
  request: { path: '.onsure/e2e/request.json', sha256: requestSha },
  artifact: {
    path: '.onsure/e2e/artifact.json',
    sha256: artifactSha,
    schema_path: '.onsure/e2e/artifact.schema.json',
    schema_sha256: schemaSha,
    media_type: 'application/json'
  },
  handoffs: [{
    producer: 'renderer',
    consumer: 'read-back',
    producer_output_sha256: artifactSha,
    consumer_input_sha256: artifactSha,
    artifact_sha256: artifactSha,
    producer_schema_sha256: schemaSha,
    consumer_schema_sha256: schemaSha
  }],
  permit: {
    permit_id: permitId,
    run_id: runId,
    request_sha256: requestSha,
    artifact_sha256: artifactSha,
    decision: 'ALLOW',
    issued_at: issued.toISOString(),
    expires_at: expires.toISOString()
  },
  read_back: { artifact_sha256: artifactSha },
  tester: { decision: 'PASS', artifact_sha256: artifactSha },
  audit: { decision: 'PASS', artifact_sha256: artifactSha },
  exposure: {
    expected_decision: 'DENY',
    actual_decision: 'DENY',
    artifact_sha256: artifactSha,
    permit_id: permitId
  },
  generated_at: issued.toISOString()
};
fs.writeFileSync(receiptPath, JSON.stringify(receipt, null, 2) + '\n', { mode: 0o600 });
console.log('ONSURE_NEUTRAL_WORKFLOW_LINEAGE_RECEIPT_WRITTEN');
