#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

bash "$ROOT/scripts/prepare-assurance-environment.sh"

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "ISSUE4_FINAL_GATE_FAIL TRACKED_WORKTREE_DIRTY" >&2
  exit 72
fi

STAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
EVIDENCE_DIR="$ROOT/receipts/local/final-gate-$STAMP"
mkdir -p "$EVIDENCE_DIR"
EXECUTION_LOG="$EVIDENCE_DIR/whole-run-twice.log"

bash "$ROOT/scripts/run-local-assurance-twice.sh" | tee "$EXECUTION_LOG"

FINAL_LINE="$(grep '^LOCAL_ASSURANCE_TWICE_PASS ' "$EXECUTION_LOG" | tail -n 1 || true)"
if [[ -z "$FINAL_LINE" ]]; then
  echo "ISSUE4_FINAL_GATE_FAIL TWICE_PASS_NOT_REPORTED" >&2
  exit 86
fi

read -r _ RUN1 RUN2 <<< "$FINAL_LINE"
if [[ ! -d "$RUN1" || ! -d "$RUN2" ]]; then
  echo "ISSUE4_FINAL_GATE_FAIL RUN_ROOT_MISSING" >&2
  exit 86
fi

bash "$ROOT/scripts/summarize-local-assurance.sh" --verify "$RUN1" \
  > "$EVIDENCE_DIR/run-1-summary.md"
bash "$ROOT/scripts/summarize-local-assurance.sh" --verify "$RUN2" \
  > "$EVIDENCE_DIR/run-2-summary.md"

{
  printf 'contract=ONSURE_ISSUE4_FINAL_GATE_EVIDENCE_V1\n'
  printf 'source_commit=%s\n' "$(git rev-parse HEAD)"
  printf 'run_root_1=%s\n' "$RUN1"
  printf 'run_root_2=%s\n' "$RUN2"
  printf 'completed_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$EVIDENCE_DIR/final-gate-result.txt"

sha256sum \
  "$EXECUTION_LOG" \
  "$EVIDENCE_DIR/run-1-summary.md" \
  "$EVIDENCE_DIR/run-2-summary.md" \
  "$EVIDENCE_DIR/final-gate-result.txt" \
  > "$EVIDENCE_DIR/evidence.sha256"

printf 'ISSUE4_FINAL_GATE_EVIDENCE_READY %s %s %s\n' "$EVIDENCE_DIR" "$RUN1" "$RUN2"
printf 'NEXT_DECISION close Issue #4, mark PR #2 Ready, and merge only after independent review of these files\n'
