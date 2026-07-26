#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: fixture-sandbox-launcher.sh <target-root> <timeout-seconds> <command...>" >&2
  exit 64
fi

ROOT="$(cd "$1" && pwd -P)"
TIMEOUT_SECONDS="$2"
shift 2

for command in bwrap prlimit timeout bash env; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "ONSURE_FIXTURE_SANDBOX_FAIL MISSING_COMMAND_$command" >&2
    exit 69
  }
done

[[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ && "$TIMEOUT_SECONDS" -ge 1 && "$TIMEOUT_SECONDS" -le 300 ]] || {
  echo "ONSURE_FIXTURE_SANDBOX_FAIL INVALID_TIMEOUT" >&2
  exit 64
}

SCRIPT="${2:-}"
[[ -n "$SCRIPT" && "$SCRIPT" != /* && "$SCRIPT" != *".."* ]] || {
  echo "ONSURE_FIXTURE_SANDBOX_FAIL INVALID_SCRIPT_PATH" >&2
  exit 65
}
[[ -f "$ROOT/$SCRIPT" && ! -L "$ROOT/$SCRIPT" ]] || {
  echo "ONSURE_FIXTURE_SANDBOX_FAIL SCRIPT_OUTSIDE_TARGET" >&2
  exit 65
}

BINDINGS=()
for path in /bin /usr /lib /lib64 /etc/ld.so.cache /etc/alternatives; do
  if [[ -e "$path" ]]; then
    BINDINGS+=(--ro-bind "$path" "$path")
  fi
done

SANDBOX_ENV=(
  --setenv PATH /usr/bin:/bin
  --setenv HOME /nonexistent
  --setenv TMPDIR /tmp
  --setenv LANG C.UTF-8
  --setenv LC_ALL C.UTF-8
)
while IFS='=' read -r key value; do
  if [[ "$key" =~ ^ONSURE_FIXTURE_[A-Z0-9_]{1,64}$ ]]; then
    SANDBOX_ENV+=(--setenv "$key" "$value")
  fi
done < <(env)

exec timeout --signal=KILL --kill-after=2s "${TIMEOUT_SECONDS}s" \
  bwrap \
    --die-with-parent \
    --new-session \
    --unshare-net \
    --unshare-pid \
    --unshare-ipc \
    --unshare-uts \
    --clearenv \
    "${SANDBOX_ENV[@]}" \
    "${BINDINGS[@]}" \
    --ro-bind "$ROOT" /workspace \
    --tmpfs /tmp \
    --proc /proc \
    --dev /dev \
    --chdir /workspace \
    prlimit \
      --cpu="$TIMEOUT_SECONDS" \
      --as=1073741824 \
      --nproc=64 \
      --nofile=256 \
      --fsize=1048576 \
      -- \
      "$@"
