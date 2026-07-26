#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

count=0
while IFS= read -r -d '' script; do
  [[ "$script" == *.sh ]] || continue
  bash -n "$script"
  count=$((count + 1))
done < <(git ls-files -z -- scripts)

[[ $count -gt 0 ]] || {
  echo "SHELL_SYNTAX_FAIL NO_TRACKED_SHELL_SCRIPTS" >&2
  exit 1
}

echo "SHELL_SYNTAX_PASS $count"
