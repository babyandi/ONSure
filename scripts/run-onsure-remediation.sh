#!/usr/bin/env bash
set -Eeuo pipefail

readonly EXPECTED_BRANCH="agent/onsure-final-remediation-20260729"
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(git -C "${SCRIPT_DIR}" rev-parse --show-toplevel)"
readonly PYTHON="${ONSURE_PYTHON:-python3}"

profile="full"
repeat="2"
run_codespace_final="false"
resume="true"

usage() {
  printf '%s\n' \
    "Usage: ./scripts/run-onsure-remediation.sh [--profile core|oruda|full] [--repeat 1-5] [--codespace-final] [--no-resume]" \
    "" \
    "Runs every automated remediation phase in order. Codespace is opt-in and always last." \
    "External independent authorities and human approvals must provide real runners/receipts."
}

while (($#)); do
  case "$1" in
    --profile) (($# >= 2)) || { usage >&2; exit 64; }; profile="$2"; shift 2 ;;
    --repeat) (($# >= 2)) || { usage >&2; exit 64; }; repeat="$2"; shift 2 ;;
    --codespace-final) run_codespace_final="true"; shift ;;
    --no-resume) resume="false"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'ONSURE_REMEDIATION_INVALID_ARGUMENT %s\n' "$1" >&2; usage >&2; exit 64 ;;
  esac
done

case "${profile}" in core|oruda|full) ;; *) exit 64 ;; esac
case "${repeat}" in 1|2|3|4|5) ;; *) exit 64 ;; esac

cd "${REPO_ROOT}"
current_branch="$(git branch --show-current)"
[[ "${current_branch}" == "${EXPECTED_BRANCH}" ]] || {
  printf 'ONSURE_REMEDIATION_BLOCKED WRONG_BRANCH expected=%s actual=%s\n' \
    "${EXPECTED_BRANCH}" "${current_branch:-DETACHED}" >&2
  exit 69
}
[[ -z "$(git status --porcelain)" ]] || {
  printf 'ONSURE_REMEDIATION_BLOCKED WORKTREE_DIRTY_OR_UNTRACKED\n' >&2
  exit 72
}

source_commit="$(git rev-parse HEAD)"
readonly RUN_ROOT="${ONSURE_REMEDIATION_OUTPUT:-${REPO_ROOT}/.onsure/remediation/${source_commit}}"
readonly STATE="${RUN_ROOT}/state.tsv"
readonly LOG_DIR="${RUN_ROOT}/logs"
mkdir -p "${LOG_DIR}"
touch "${STATE}"

state_of() {
  awk -F '\t' -v id="$1" '$1 == id {value=$2} END {print value}' "${STATE}"
}

record() {
  local id="$1" state="$2" exit_code="$3" log="$4"
  local log_hash="-"
  [[ -f "${log}" ]] && log_hash="$(sha256sum "${log}" | awk '{print $1}')"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "${id}" "${state}" "${exit_code}" "${source_commit}" "${log_hash}" \
    "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "${STATE}"
}

run_stage() {
  local id="$1" marker="$2"; shift 2
  local log="${LOG_DIR}/${id}.log"
  if [[ "${resume}" == "true" && "$(state_of "${id}")" == "PASS" ]]; then
    printf 'ONSURE_REMEDIATION_RESUME_SKIP %s\n' "${id}"
    return 0
  fi
  [[ "$(git rev-parse HEAD)" == "${source_commit}" ]] || {
    record "${id}" "BLOCKED_SOURCE_DRIFT" 73 "${log}"
    exit 73
  }
  printf 'ONSURE_REMEDIATION_STAGE_START %s\n' "${id}"
  set +e
  "$@" > >(tee "${log}") 2>&1
  local rc=$?
  set -e
  marker_missing="false"
  if [[ -n "${marker}" ]] && { [[ ! -f "${log}" ]] || ! grep -Fq "${marker}" "${log}"; }; then
    marker_missing="true"
  fi
  if [[ ${rc} -ne 0 || "${marker_missing}" == "true" ]]; then
    record "${id}" "FAIL" "${rc}" "${log}"
    printf 'ONSURE_REMEDIATION_STAGE_BLOCKED %s exit=%s\n' "${id}" "${rc}" >&2
    [[ ${rc} -ne 0 ]] || rc=74
    exit "${rc}"
  fi
  record "${id}" "PASS" 0 "${log}"
}

execution_profile="${profile}"
[[ "${profile}" == "full" ]] && execution_profile="oruda"

run_stage prepare "ONSURE_INTEGRATED_RUN_PASS_NONFINAL" \
  "${PYTHON}" scripts/onsure-integrated-run.py \
  --profile "${profile}" --stage prepare --repeat "${repeat}"

run_stage product_runtime_e2e "ONSURE_VALIDATOR_FIXTURE_E2E_PASS" \
  bash scripts/run-product-platform-e2e.sh

if [[ -x scripts/run-financial-operations-e2e.sh ]]; then
  run_stage financial_operations "ONSURE_FINANCIAL_OPERATIONS_E2E_PASS" \
    bash scripts/run-financial-operations-e2e.sh --repeat "${repeat}"
else
  record financial_operations "BLOCKED_MISSING_RUNNER" 69 "${LOG_DIR}/financial_operations.log"
  printf 'ONSURE_REMEDIATION_BLOCKED MISSING_FINANCIAL_OPERATIONS_RUNNER\n' >&2
  exit 69
fi

if [[ -x scripts/run-install-rollback-dr-performance.sh ]]; then
  run_stage install_rollback_dr_performance "ONSURE_OPERATIONS_FULL_PASS" \
    bash scripts/run-install-rollback-dr-performance.sh --repeat "${repeat}"
else
  record install_rollback_dr_performance "BLOCKED_MISSING_RUNNER" 69 \
    "${LOG_DIR}/install_rollback_dr_performance.log"
  printf 'ONSURE_REMEDIATION_BLOCKED MISSING_INSTALL_ROLLBACK_DR_PERFORMANCE_RUNNER\n' >&2
  exit 69
fi

run_stage main_protection_contract "ONSURE_MAIN_BRANCH_PROTECTION_PASS" \
  "${PYTHON}" scripts/validate-main-branch-protection.py

for authority in ontester onaudit; do
  runner_var="ONSURE_${authority^^}_RUNNER"
  runner="${!runner_var:-}"
  if [[ -z "${runner}" || ! -x "${runner}" ]]; then
    record "${authority}_2x" "WAITING_INDEPENDENT_AUTHORITY" 75 "${LOG_DIR}/${authority}_2x.log"
    printf 'ONSURE_REMEDIATION_WAITING %s runner via %s\n' "${authority}" "${runner_var}" >&2
    exit 75
  fi
  run_stage "${authority}_2x" "ONSURE_${authority^^}_2X_CLEAN" \
    "${runner}" --source "${source_commit}" --repeat 2 --evidence-root "${RUN_ROOT}"
done

if [[ "${run_codespace_final}" == "true" ]]; then
  run_stage codespace_final "ONSURE_INTEGRATED_RUN_PASS_NONFINAL" \
    "${PYTHON}" scripts/onsure-integrated-run.py \
    --profile "${profile}" --stage codespace-final --repeat "${repeat}"
else
  record codespace_final "DEFERRED_FINAL_STEP" 0 "${LOG_DIR}/codespace_final.log"
fi

[[ "$(git rev-parse HEAD)" == "${source_commit}" ]] || exit 73
printf 'ONSURE_REMEDIATION_READY_FOR_HUMAN_APPROVAL source=%s evidence=%s\n' \
  "${source_commit}" "${RUN_ROOT}"
printf 'FinalLock=false Production_GO=false Commercial_GO=false Merge=BLOCKED\n'
exit 75
