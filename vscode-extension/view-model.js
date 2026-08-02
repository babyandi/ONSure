'use strict';

const VIEW_IDS = Object.freeze([
  'onsure.workspace', 'onsure.profile', 'onsure.inventory', 'onsure.requirements',
  'onsure.threats', 'onsure.plan', 'onsure.runs', 'onsure.findings',
  'onsure.improvement', 'onsure.evidence', 'onsure.git', 'onsure.approvals',
  'onsure.runtime', 'onsure.admin'
]);

const VIEW_MODELS = Object.freeze({
  'onsure.workspace': [['Session', 'LOCAL_API_BOUND', 'comment-discussion'], ['Mode', '$mode', 'symbol-enum']],
  'onsure.profile': [['Program Learning', 'program_learning', 'symbol-class'], ['Last Profile', '$profile', 'json']],
  'onsure.inventory': [['Inventory', 'SOURCE_BOUND_PENDING_REFRESH', 'list-tree'], ['Data Egress', 'LOOPBACK_ONLY', 'lock']],
  'onsure.requirements': [['Traceability', 'planning_review_rca', 'references'], ['Authority', 'REGISTER_BOUND', 'law']],
  'onsure.threats': [['Threat Controls', 'planning_review_rca', 'shield'], ['Financial Controls', 'FAIL_CLOSED', 'lock']],
  'onsure.plan': [['Verification Plan', 'planning_review_rca', 'checklist'], ['Approval', 'SIGNED_BUNDLE_REQUIRED', 'signature']],
  'onsure.runs': [['Validation', 'validation', 'beaker'], ['Last Run', '$run', 'folder-opened'], ['Restart Recovery', '$restore', 'debug-restart']],
  'onsure.findings': [['Findings / RCA', 'planning_review_rca', 'search-fuzzy'], ['Independent OTester', 'independent_otester', 'shield']],
  'onsure.improvement': [['Patch Application', 'patch_application', 'diff'], ['Improvement Proof', 'improvement_proof', 'verified-filled']],
  'onsure.evidence': [['Evidence', 'APPEND_ONLY', 'archive'], ['Independent OAudit', 'independent_oaudit', 'verified']],
  'onsure.git': [['Git Delivery', 'git_delivery', 'git-pull-request'], ['Final Claim', 'PROHIBITED', 'circle-slash']],
  'onsure.approvals': [['Approval Policy', 'SIGNED_AND_CONSUMED', 'key'], ['Last Workflow', '$workflow', 'run-all']],
  'onsure.runtime': [['Runtime', 'state', 'server-process'], ['OLicense', 'license', 'key'], ['Service Case', 'service_case', 'briefcase']],
  'onsure.admin': [['Identity / Policy', 'LOCAL_GATEWAY', 'organization'], ['API Boundary', 'LOOPBACK_ONLY', 'lock']]
});

function rowsForView(viewId, state) {
  const model = VIEW_MODELS[viewId];
  if (!model) throw new Error(`Unknown ONSure view: ${viewId}`);
  return model.map(([label, key, icon]) => {
    let description = key;
    if (key.startsWith('$')) description = state[key.slice(1)] || 'NOT_AVAILABLE';
    else if (Object.prototype.hasOwnProperty.call(state.status || {}, key)) description = state.status[key] || 'UNKNOWN';
    return { label, description: String(description), icon };
  });
}

module.exports = { VIEW_IDS, VIEW_MODELS, rowsForView };
