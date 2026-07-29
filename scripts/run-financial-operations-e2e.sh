#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${ROOT}"
repeat=2
if [[ "${1:-}" == "--repeat" ]]; then repeat="${2:-}"; fi
case "${repeat}" in 1|2|3|4|5) ;; *) exit 64 ;; esac
for command in java javac mvn git sha256sum; do
  command -v "${command}" >/dev/null 2>&1 || { echo "ONSURE_FINANCIAL_OPERATIONS_E2E_BLOCKED MISSING_${command}" >&2; exit 69; }
done
source_commit="$(git rev-parse HEAD)"
out="${ONSURE_FINANCIAL_OUTPUT:-${ROOT}/.onsure/financial-operations/${source_commit}}"
mkdir -p "${out}"
for iteration in $(seq 1 "${repeat}"); do
  run="${out}/run-${iteration}"
  mkdir -p "${run}"
  mvn -B -ntp -Dtest=EnterpriseCapabilityRuntimeTest test | tee "${run}/maven.log"
  test -s target/surefire-reports/io.onsure.platform.EnterpriseCapabilityRuntimeTest.txt
  cp target/surefire-reports/io.onsure.platform.EnterpriseCapabilityRuntimeTest.txt "${run}/summary.txt"
  grep -Fq 'Failures: 0' "${run}/summary.txt"
  grep -Fq 'Errors: 0' "${run}/summary.txt"
  sha256sum "${run}/maven.log" "${run}/summary.txt" > "${run}/evidence.sha256"
done
for iteration in $(seq 2 "${repeat}"); do
  cmp "${out}/run-1/summary.txt" "${out}/run-${iteration}/summary.txt"
done
printf '%s\n' \
  "ONSURE_FINANCIAL_OPERATIONS_E2E_PASS_NONFINAL ${out}" \
  "EXTERNAL_FINANCIAL_CUSTOMER_SCENARIOS=NOT_RUN" \
  "INDEPENDENT_VERIFICATION=NOT_RUN" \
  "FINAL_CLAIM_ALLOWED=false"
