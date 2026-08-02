'use strict';

function requireContract(value, expected, code) {
  if (!value || value.contract !== expected) throw new Error(code);
}

function uniqueStrings(value, code) {
  if (!Array.isArray(value) || value.length === 0 || value.some(item => typeof item !== 'string' || !item)) {
    throw new Error(code);
  }
  const unique = new Set(value);
  if (unique.size !== value.length) throw new Error(`${code}_DUPLICATE`);
  return unique;
}

function requireSafeFlags(receipt, fields, code) {
  if (fields.some(field => receipt[field] !== false)) throw new Error(code);
}

function reviewPlanApproval(plan, receipt) {
  requireContract(plan, 'ONSURE_EXECUTION_PLAN_V1', 'PLAN_CONTRACT_INVALID');
  requireContract(receipt, 'ONSURE_EXECUTION_PLAN_APPROVAL_V1', 'PLAN_APPROVAL_CONTRACT_INVALID');
  if (plan.plan_id !== receipt.plan_id) throw new Error('PLAN_APPROVAL_ID_MISMATCH');
  const planned = uniqueStrings(plan.allowed_actions, 'PLAN_ALLOWED_ACTIONS_INVALID');
  const approved = uniqueStrings(receipt.approved_actions, 'PLAN_APPROVED_ACTIONS_INVALID');
  for (const action of approved) if (!planned.has(action)) throw new Error(`PLAN_ACTION_OUTSIDE_SCOPE:${action}`);
  requireSafeFlags(receipt, ['allow_final_claim', 'allow_merge', 'allow_deploy'], 'PLAN_APPROVAL_UNSAFE_AUTHORITY');
  return Object.freeze({
    review_type: 'EXECUTION_PLAN_APPROVAL_REVIEW',
    scope: approved.size === planned.size ? 'FULL' : 'PARTIAL',
    plan_id: plan.plan_id,
    approved_actions: [...approved].sort(),
    skipped_actions: [...planned].filter(action => !approved.has(action)).sort(),
    approval_actor: String(receipt.actor || 'UNKNOWN'),
    expires_at: String(receipt.expires_at || 'UNKNOWN'),
    signature_verification: 'DEFERRED_TO_FIXED_CORE_AUTHORITY_AT_CONSUMPTION',
    final_claim_allowed: false
  });
}

function reviewHunkApproval(plan, receipt) {
  requireContract(plan, 'ONSURE_PATCH_PLAN_V1', 'PATCH_PLAN_CONTRACT_INVALID');
  requireContract(receipt, 'ONSURE_HUNK_APPROVAL_RECEIPT_V1', 'HUNK_APPROVAL_CONTRACT_INVALID');
  if (plan.patch_plan_id !== receipt.patch_plan_id) throw new Error('HUNK_APPROVAL_PLAN_ID_MISMATCH');
  const planned = new Set((plan.hunks || []).map(hunk => hunk && hunk.hunk_id));
  if (planned.size === 0 || planned.has(undefined)) throw new Error('PATCH_PLAN_HUNKS_INVALID');
  const approved = uniqueStrings(receipt.approved_hunk_ids, 'APPROVED_HUNK_IDS_INVALID');
  for (const hunk of approved) if (!planned.has(hunk)) throw new Error(`HUNK_OUTSIDE_PATCH_PLAN:${hunk}`);
  requireSafeFlags(receipt, ['allow_direct_main_write', 'allow_force_push', 'allow_merge'],
    'HUNK_APPROVAL_UNSAFE_AUTHORITY');
  const approvedFiles = [...new Set((plan.hunks || [])
    .filter(hunk => approved.has(hunk.hunk_id)).map(hunk => hunk.relative_path))].sort();
  return Object.freeze({
    review_type: 'PATCH_HUNK_APPROVAL_REVIEW',
    scope: approved.size === planned.size ? 'FULL' : 'PARTIAL',
    patch_plan_id: plan.patch_plan_id,
    approved_hunk_ids: [...approved].sort(),
    skipped_hunk_ids: [...planned].filter(hunk => !approved.has(hunk)).sort(),
    approved_files: approvedFiles,
    risk_level: String(plan.preapply_assessment?.risk_level || 'UNKNOWN'),
    rollback_method: String(plan.preapply_assessment?.rollback_preview?.method || 'UNKNOWN'),
    branch_name: String(receipt.branch_name || 'UNKNOWN'),
    approval_actor: String(receipt.actor || 'UNKNOWN'),
    signature_verification: 'DEFERRED_TO_FIXED_CORE_AUTHORITY_AT_CONSUMPTION',
    direct_main_write_allowed: false,
    force_push_allowed: false,
    merge_allowed: false,
    final_claim_allowed: false
  });
}

module.exports = { reviewPlanApproval, reviewHunkApproval };
