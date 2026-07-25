#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---core}"
cd "$ROOT"

command -v mvn >/dev/null || {
  echo "ONSURE_MANUAL_VERIFICATION_HOLD missing_command=mvn" >&2
  exit 69
}

mvn -B -ntp verify
python -m unittest discover -s tests -p "test_*.py" -v
bash scripts/preflight-local-assurance.sh
bash scripts/run-onsure-development-gate.sh
bash scripts/run-onguard-fixed-target-local.sh "$MODE"

if [[ "$MODE" == "--full" ]]; then
  echo "ONSURE_MANUAL_VERIFICATION_FULL_PASS_SELF_VALIDATION_NONFINAL"
else
  echo "ONSURE_MANUAL_VERIFICATION_CORE_PASS_INFRA_NOT_RUN_HOLD"
fi
