#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
: "${ONSURE_DD_QUALIFICATION_RECEIPTS_DIR:?required}"
python3 scripts/validate-dd-semantic-evaluator-qualifications-successor.py
python3 scripts/validate-dd-denominator-42.py
# Base runtime 40 evidence remains required and is validated by the existing runner/validator.
bash scripts/run-dd-semantic-runtime-evidence.sh
# DD-041/042 target runtime evidence materializer is not yet implemented; fail closed rather than reuse 40/40.
echo "ONSURE_SUCCESSOR_RUNTIME_HOLD DD_041_042_TARGET_RUNTIME_EVIDENCE_MATERIALIZER_NOT_YET_IMPLEMENTED" >&2
exit 43
