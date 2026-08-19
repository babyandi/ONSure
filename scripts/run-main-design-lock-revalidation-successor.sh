#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MODE="${1:-PRE_CLEAN}"
BRANCH="$(git branch --show-current)"; HEAD_SHA="$(git rev-parse HEAD)"; TREE_SHA="$(git rev-parse 'HEAD^{tree}')"
[[ "$BRANCH" == "main" ]] || { echo "ONSURE_MAIN_SUCCESSOR_HOLD NOT_ON_MAIN" >&2; exit 83; }
if [[ "$MODE" == "PRE_CLEAN" ]]; then
  python3 scripts/validate-dd-denominator-42.py
  python3 scripts/validate-dd-semantic-evaluator-qualifications-successor.py
  python3 scripts/reconcile-design-discovery-waves-v2.py
  python3 scripts/validate-human-design-authority-successor.py
  bash scripts/run-dd-semantic-runtime-evidence-successor.sh
  echo "ONSURE_MAIN_SUCCESSOR_HOLD MAIN_PRECLEAN_42_INTEGRATION_PENDING_AFTER_RUNTIME" >&2
  exit 82
fi
if [[ "$MODE" == "FINALIZE_LOCK" ]]; then
  echo "ONSURE_MAIN_SUCCESSOR_HOLD FINALIZE_LOCK_DISABLED_UNTIL_MAIN_42_RUNTIME_AND_NEW_MAIN_CLEAN_A_B_EXIST" >&2
  exit 82
fi
echo "Usage: $0 PRE_CLEAN|FINALIZE_LOCK" >&2; exit 2
