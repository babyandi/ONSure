#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: fixture-sandbox-launcher.sh <target-root> <timeout-seconds> <command...>" >&2
  exit 64
fi

ROOT="$(cd "$1" && pwd -P)"
TIMEOUT_SECONDS="$2"
shift 2

for command in bwrap prlimit timeout bash env mktemp readlink dirname; do
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

BACKEND="${ONSURE_FIXTURE_SANDBOX_BACKEND:-ROOTLESS_BWRAP}"
[[ "$BACKEND" == "ROOTLESS_BWRAP" ]] || {
  echo "ONSURE_FIXTURE_SANDBOX_FAIL NON_LOCAL_BACKEND_FORBIDDEN_$BACKEND" >&2
  exit 64
}
BWRAP_COMMAND=(bwrap)
IDENTITY_ARGS=(--unshare-user --uid 0 --gid 0)
NETWORK_ARGS=(--unshare-net)

EMPTY_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/onsure-sandbox-root.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$EMPTY_ROOT" 2>/dev/null || true
  rm -rf "$EMPTY_ROOT"
}
trap cleanup EXIT

for directory in bin usr lib lib64 etc etc/alternatives opt opt/onsure-jdk workspace tmp proc dev; do
  mkdir -p "$EMPTY_ROOT/$directory"
done
if [[ -e /etc/ld.so.cache ]]; then
  : > "$EMPTY_ROOT/etc/ld.so.cache"
fi
chmod 0555 "$EMPTY_ROOT"

BINDINGS=(--ro-bind "$EMPTY_ROOT" /)
for path in /bin /usr /lib /lib64 /etc/ld.so.cache /etc/alternatives; do
  if [[ -e "$path" ]]; then
    BINDINGS+=(--ro-bind "$path" "$path")
  fi
done

SANDBOX_PATH="/usr/bin:/bin"
JDK_BINDINGS=()
if command -v java >/dev/null 2>&1 && command -v javac >/dev/null 2>&1; then
  JAVA_BIN="$(readlink -f "$(command -v java)")"
  JAVAC_BIN="$(readlink -f "$(command -v javac)")"
  JDK_ROOT="$(dirname "$(dirname "$JAVA_BIN")")"
  [[ "$JAVAC_BIN" == "$JDK_ROOT/bin/javac" ]] || {
    echo "ONSURE_FIXTURE_SANDBOX_FAIL JAVA_JAVAC_ROOT_MISMATCH" >&2
    exit 69
  }
  JDK_BINDINGS=(--ro-bind "$JDK_ROOT" /opt/onsure-jdk)
  SANDBOX_PATH="/opt/onsure-jdk/bin:$SANDBOX_PATH"
fi

SANDBOX_ENV=(
  --setenv PATH "$SANDBOX_PATH"
  --setenv HOME /nonexistent
  --setenv TMPDIR /tmp
  --setenv LANG C.UTF-8
  --setenv LC_ALL C.UTF-8
  --setenv USER onsure-sandbox
  --setenv LOGNAME onsure-sandbox
)
while IFS='=' read -r key value; do
  if [[ "$key" =~ ^ONSURE_FIXTURE_[A-Z0-9_]{1,64}$ \
      && "$key" != "ONSURE_FIXTURE_SANDBOX_MODE" \
      && "$key" != "ONSURE_FIXTURE_SANDBOX_BACKEND" ]]; then
    SANDBOX_ENV+=(--setenv "$key" "$value")
  fi
done < <(env)

set +e
timeout --signal=KILL --kill-after=2s "${TIMEOUT_SECONDS}s" \
  "${BWRAP_COMMAND[@]}" \
    --die-with-parent \
    --new-session \
    "${IDENTITY_ARGS[@]}" \
    "${NETWORK_ARGS[@]}" \
    --unshare-pid \
    --unshare-ipc \
    --unshare-uts \
    --cap-drop ALL \
    --clearenv \
    "${SANDBOX_ENV[@]}" \
    "${BINDINGS[@]}" \
    "${JDK_BINDINGS[@]}" \
    --ro-bind "$ROOT" /workspace \
    --tmpfs /tmp \
    --proc /proc \
    --dev /dev \
    --chdir /workspace \
    prlimit \
      --cpu="$TIMEOUT_SECONDS" \
      --as=8589934592 \
      --nproc=64 \
      --nofile=256 \
      --fsize=1048576 \
      -- \
      "$@"
status=$?
set -e
exit "$status"
