'use strict';

const WORK_MODES = Object.freeze([
  'ASK', 'PLAN', 'ACT', 'VERIFY', 'IMPROVE', 'AUTOPILOT', 'AUDIT', 'OFFLINE'
]);

const MODE_CAPABILITIES = Object.freeze({
  ASK: ['READ'],
  PLAN: ['READ', 'PLAN'],
  ACT: ['READ', 'PLAN', 'VERIFY', 'ACT'],
  VERIFY: ['READ', 'VERIFY'],
  IMPROVE: ['READ', 'PLAN', 'VERIFY', 'IMPROVE'],
  AUTOPILOT: ['READ', 'PLAN', 'VERIFY', 'ACT', 'IMPROVE', 'AUTOPILOT'],
  AUDIT: ['READ', 'VERIFY'],
  OFFLINE: ['READ', 'PLAN', 'VERIFY', 'ACT', 'IMPROVE']
});

const PROHIBITED_OPERATION = /(?:merge|final(?:lock)?|production|commercial[._-]?go|force[._-]?push)/i;

function classifyOperation(operation) {
  const value = String(operation || '');
  if (value === 'plan.approve') return 'ACT';
  if (/^(?:status|artifact|job\.read)/.test(value)) return 'READ';
  if (/^(?:program\.learn|plan\.)/.test(value)) return 'PLAN';
  if (/^(?:validation\.|review\.|rca\.)/.test(value)) return 'VERIFY';
  if (/^(?:improvement\.|patch\.)/.test(value)) return 'IMPROVE';
  if (/^job\.(?:create|control|recover)/.test(value)) return 'AUTOPILOT';
  return 'ACT';
}

function authorize(mode, capability, operation = '') {
  if (!WORK_MODES.includes(mode)) return { allowed: false, reason: 'UNKNOWN_WORK_MODE' };
  if (PROHIBITED_OPERATION.test(operation)) {
    return { allowed: false, reason: 'FINAL_MERGE_OR_PRODUCTION_OPERATION_PROHIBITED' };
  }
  if (!MODE_CAPABILITIES[mode].includes(capability)) {
    return { allowed: false, reason: `${mode}_MODE_DENIES_${capability}` };
  }
  if (mode === 'OFFLINE' && /(?:remote|push|provider|network|draft[._-]?pr)/i.test(operation)) {
    return { allowed: false, reason: 'OFFLINE_MODE_DENIES_EXTERNAL_OPERATION' };
  }
  if (mode === 'AUDIT' && capability !== 'READ' && capability !== 'VERIFY') {
    return { allowed: false, reason: 'AUDIT_MODE_IS_NON_MUTATING' };
  }
  return { allowed: true, reason: 'MODE_POLICY_ALLOWED' };
}

module.exports = { WORK_MODES, MODE_CAPABILITIES, classifyOperation, authorize };
