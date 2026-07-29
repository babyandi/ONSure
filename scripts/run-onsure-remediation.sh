#!/usr/bin/env bash
set -Eeuo pipefail

readonly EXPECTED_BRANCH="agent/onsure-final-remediation-20260729"
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(git -C "${SCRIPT_DIR}" rev-parse --show-toplevel)"
readonly INTEGRATED_RUN="${REPO_ROOT}/scripts/onsure-integrated-run.py"

profile="full"
repeat="2"
run_codespace_final="false"

usage() {
  printf '%s\n' \
    "Usage: ./scripts/run-onsure-remediation.sh [--profile core|oruda|full] [--repeat 1-5] [--codespace-final]" \
    "" \
    "Default: run every currently executable remediation gate and defer Codespace." \
    "Final/GO/Merge remain blocked until independent ONTester, ONAudit, and human approvals exist."
}

while (($#)); do
  case "$1" in
    --profile)
      (($# >= 2)) || { usage >&2; exit 64; }
      profile="$2"
      shift 2
      ;;
    --repeat)
      (($# >= 2)) || { usage >&2; exit 64; }
      repeat="$2"
      shift 2
      ;;
    --codespace-final)
      run_codespace_final="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'ONSURE_REMEDIATION_INVALID_ARGUMENT %s\n' "$1" >&2
      usage >&2
      exit 64
      ;;
  esac
done

case "${profile}" in
  core|oruda|full) ;;
  *) printf 'ONSURE_REMEDIATION_INVALID_PROFILE %s\n' "${profile}" >&2; exit 64 ;;
esac
case "${repeat}" in
  1|2|3|4|5) ;;
  *) printf 'ONSURE_REMEDIATION_INVALID_REPEAT %s\n' "${repeat}" >&2; exit 64 ;;
esac

[[ -f "${INTEGRATED_RUN}" ]] || {
  printf 'ONSURE_REMEDIATION_BLOCKED MISSING_INTEGRATED_RUNNER\n' >&2
  exit 69
}

current_branch="$(git -C "${REPO_ROOT}" branch --show-current)"
[[ "${current_branch}" == "${EXPECTED_BRANCH}" ]] || {
  printf 'ONSURE_REMEDIATION_BLOCKED WRONG_BRANCH expected=%s actual=%s\n' \
    "${EXPECTED_BRANCH}" "${current_branch:-DETACHED}" >&2
  exit 69
}

if [[ -n "$(git -C "${REPO_ROOT}" status --porcelain)" ]]; then
  printf 'ONSURE_REMEDIATION_BLOCKED WORKTREE_DIRTY_OR_UNTRACKED\n' >&2
  exit 72
fi

source_commit="$(git -C "${REPO_ROOT}" rev-parse HEAD)"
printf 'ONSURE_REMEDIATION_START source=%s profile=%s repeat=%s\n' \
  "${source_commit}" "${profile}" "${repeat}"

python3 "${INTEGRATED_RUN}" \
  --profile "${profile}" \
  --stage prepare \
  --repeat "${repeat}"

end_commit="$(git -C "${REPO_ROOT}" rev-parse HEAD)"
[[ "${end_commit}" == "${source_commit}" ]] || {
  printf 'ONSURE_REMEDIATION_FAIL SOURCE_COMMIT_DRIFT start=%s end=%s\n' \
    "${source_commit}" "${end_commit}" >&2
  exit 73
}

if [[ "${run_codespace_final}" == "true" ]]; then
  printf 'ONSURE_REMEDIATION_CODESPACE_FINAL_START source=%s\n' "${source_commit}"
  python3 "${INTEGRATED_RUN}" \
    --profile "${profile}" \
    --stage codespace-final \
    --repeat "${repeat}"
else
  printf 'ONSURE_REMEDIATION_CODESPACE_FINAL DEFERRED_FINAL_STEP\n'
fi

printf '%s\n' \
  "ONSURE_REMEDIATION_HOLD independent ONTester 2x CLEAN, ONAudit 2x CLEAN," \
  "server protection read-back, and required human approvals are not produced by this product-team runner." \
  "FinalLock=false Production_GO=false Commercial_GO=false Merge=BLOCKED" >&2
exit 75
