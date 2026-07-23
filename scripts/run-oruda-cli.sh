#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ $# -lt 1 ]]; then
  echo "usage: run-oruda-cli.sh <main-class> [args...]" >&2
  exit 64
fi
MAIN_CLASS="$1"
shift

bash "$ROOT/scripts/preflight-local-assurance.sh"
mkdir -p "$ROOT/target"
CLASSPATH_FILE="$ROOT/target/oruda-cli-classpath.txt"
mvn -B -ntp -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile="$CLASSPATH_FILE" >/dev/null
CP="$ROOT/target/classes:$(cat "$CLASSPATH_FILE")"
exec java -cp "$CP" "$MAIN_CLASS" "$@"
