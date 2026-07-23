#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() { echo "ASSURANCE_ENVIRONMENT_FAIL $1" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "MISSING_COMMAND_$1"; }

require java
require mvn
require git
require sha256sum
require cmp

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
[[ "$JAVA_MAJOR" == "17" ]] || fail "JDK_17_REQUIRED_FOUND_${JAVA_MAJOR:-unknown}"

MAVEN_VERSION="$(mvn -version 2>/dev/null | head -n 1)"
[[ -n "$MAVEN_VERSION" ]] || fail "MAVEN_VERSION_UNAVAILABLE"

COMMIT="$(git rev-parse HEAD 2>/dev/null)" || fail "NOT_A_GIT_REPOSITORY"
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || fail "INVALID_COMMIT_SHA"

mvn -B -ntp -DskipTests validate >/dev/null || fail "MAVEN_VALIDATE_FAILED"

printf 'ONSURE_ASSURANCE_ENVIRONMENT_READY commit=%s java=%s maven=%s\n' \
  "$COMMIT" "$JAVA_MAJOR" "$MAVEN_VERSION"
printf 'NEXT_COMMAND bash scripts/execute-issue-4-final-gate.sh\n'
