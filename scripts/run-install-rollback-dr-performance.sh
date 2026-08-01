#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${ROOT}"
repeat=2
if [[ "${1:-}" == "--repeat" ]]; then repeat="${2:-}"; fi
case "${repeat}" in 1|2|3|4|5) ;; *) exit 64 ;; esac
for command in java javac mvn git python3 sha256sum; do
  command -v "${command}" >/dev/null 2>&1 || { echo "ONSURE_OPERATIONS_BLOCKED MISSING_${command}" >&2; exit 69; }
done
source_commit="$(git rev-parse HEAD)"
out="${ONSURE_OPERATIONS_OUTPUT:-${ROOT}/.onsure/install-rollback-dr-performance/${source_commit}}"
mkdir -p "${out}"
tests="EnterpriseCapabilityRuntimeTest,OrudaExecutionPackageCatalogTest,OrudaPackageExecutionRegistryTest,ProductExecutionBoundaryTest"
for iteration in $(seq 1 "${repeat}"); do
  run="${out}/run-${iteration}"
  mkdir -p "${run}"
  mvn -B -ntp -Dtest="${tests}" test | tee "${run}/maven.log"
  grep -h '^Tests run:' target/surefire-reports/*.txt \
    | python3 "${ROOT}/scripts/normalize-surefire-summary.py" \
    | LC_ALL=C sort > "${run}/summary.txt"
  test -s "${run}/summary.txt"
  if grep -Eq 'Failures: [1-9]|Errors: [1-9]|Skipped: [1-9]' "${run}/summary.txt"; then
    echo "ONSURE_OPERATIONS_BLOCKED TEST_FAILURE_OR_SKIP" >&2
    exit 1
  fi
  sha256sum "${run}/maven.log" "${run}/summary.txt" > "${run}/evidence.sha256"
done
for iteration in $(seq 2 "${repeat}"); do
  cmp "${out}/run-1/summary.txt" "${out}/run-${iteration}/summary.txt"
done
printf '%s\n' \
  "ONSURE_OPERATIONS_IMPLEMENTED_LANES_PASS_NONFINAL ${out}" \
  "REAL_INSTALL_UPGRADE=NOT_RUN" \
  "REAL_BACKUP_RESTORE_DR=NOT_RUN" \
  "PERFORMANCE_LONG_RUN=NOT_RUN" \
  "FINAL_CLAIM_ALLOWED=false"
