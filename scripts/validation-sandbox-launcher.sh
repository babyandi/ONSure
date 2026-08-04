#!/usr/bin/env bash
set -euo pipefail
PATH='/usr/sbin:/usr/bin:/sbin:/bin'
export PATH

if [[ $# -lt 3 ]]; then
  echo 'usage: validation-sandbox-launcher.sh <snapshot-root> <timeout-seconds> <command...>' >&2
  exit 64
fi

ONSURE_SNAPSHOT_ROOT="$(cd "$1" && pwd -P)"
ONSURE_TIMEOUT_SECONDS="$2"
ONSURE_SANDBOX_WORKDIR="/""workspace"
shift 2

[[ "$ONSURE_TIMEOUT_SECONDS" =~ ^[0-9]+$ \
  && "$ONSURE_TIMEOUT_SECONDS" -ge 1 \
  && "$ONSURE_TIMEOUT_SECONDS" -le 7200 ]] || {
  echo 'ONSURE_VALIDATION_SANDBOX_FAIL INVALID_TIMEOUT' >&2
  exit 64
}

for required_command in bwrap prlimit timeout bash env mktemp readlink dirname; do
  command -v "$required_command" >/dev/null 2>&1 || {
    echo "ONSURE_VALIDATION_SANDBOX_FAIL MISSING_COMMAND_${required_command}" >&2
    exit 69
  }
done

case "${1:-}" in
  true)
    [[ "${ONSURE_SANDBOX_PROBE:-}" == '1' && "$#" -eq 1 ]] || {
      echo 'ONSURE_VALIDATION_SANDBOX_FAIL PROBE_AUTHORITY_REQUIRED' >&2
      exit 65
    }
    ;;
  mvn)
    [[ " $* " == *' -o '* ]] || {
      echo 'ONSURE_VALIDATION_SANDBOX_FAIL MAVEN_OFFLINE_REQUIRED' >&2
      exit 65
    }
    ;;
  python3)
    [[ "${2:-}" == '-m' && ( "${3:-}" == 'pytest' || "${3:-}" == 'unittest' ) ]] || {
      echo 'ONSURE_VALIDATION_SANDBOX_FAIL PYTHON_MODULE_DENIED' >&2
      exit 65
    }
    ;;
  npm)
    [[ " $* " == *' --offline '* ]] || {
      echo 'ONSURE_VALIDATION_SANDBOX_FAIL NPM_OFFLINE_REQUIRED' >&2
      exit 65
    }
    ;;
  bash)
    [[ "${2:-}" == 'gradlew' && -f "$ONSURE_SNAPSHOT_ROOT/gradlew" \
      && ! -L "$ONSURE_SNAPSHOT_ROOT/gradlew" && " $* " == *' --offline '* ]] || {
      echo 'ONSURE_VALIDATION_SANDBOX_FAIL GRADLE_COMMAND_DENIED' >&2
      exit 65
    }
    ;;
  *)
    echo "ONSURE_VALIDATION_SANDBOX_FAIL EXECUTABLE_DENIED_${1:-EMPTY}" >&2
    exit 65
    ;;
esac

for argument in "$@"; do
  [[ "$argument" != *$'\n'* && "$argument" != *$'\r'* \
    && "$argument" != '..' && "$argument" != ../* && "$argument" != */../* ]] || {
    echo 'ONSURE_VALIDATION_SANDBOX_FAIL ARGUMENT_ESCAPE' >&2
    exit 65
  }
done

ONSURE_EMPTY_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/onsure-validation-root.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$ONSURE_EMPTY_ROOT" 2>/dev/null || true
  rm -rf "$ONSURE_EMPTY_ROOT"
}
trap cleanup EXIT

for directory in bin usr lib lib64 etc etc/ssl/certs opt workspace tmp proc dev onsure-cache/m2 onsure-cache/npm; do
  mkdir -p "$ONSURE_EMPTY_ROOT/$directory"
done
chmod 0555 "$ONSURE_EMPTY_ROOT"

ONSURE_BINDINGS=(--ro-bind "$ONSURE_EMPTY_ROOT" /)
for host_path in /bin /usr /lib /lib64 /etc/ld.so.cache /etc/alternatives /etc/ssl/certs; do
  if [[ -e "$host_path" ]]; then
    ONSURE_BINDINGS+=(--ro-bind "$host_path" "$host_path")
  fi
done

if [[ -n "${ONSURE_MAVEN_CACHE:-}" && -d "${ONSURE_MAVEN_CACHE}" \
  && ! -L "${ONSURE_MAVEN_CACHE}" ]]; then
  ONSURE_BINDINGS+=(--ro-bind "${ONSURE_MAVEN_CACHE}" /onsure-cache/m2)
fi
if [[ -n "${ONSURE_NPM_CACHE:-}" && -d "${ONSURE_NPM_CACHE}" \
  && ! -L "${ONSURE_NPM_CACHE}" ]]; then
  ONSURE_BINDINGS+=(--ro-bind "${ONSURE_NPM_CACHE}" /onsure-cache/npm)
fi

ONSURE_JDK_BINDINGS=()
ONSURE_SANDBOX_PATH='/usr/bin:/bin'
if command -v java >/dev/null 2>&1 && command -v javac >/dev/null 2>&1; then
  ONSURE_JAVA_BIN="$(readlink -f "$(command -v java)")"
  ONSURE_JAVAC_BIN="$(readlink -f "$(command -v javac)")"
  ONSURE_JDK_ROOT="$(dirname "$(dirname "$ONSURE_JAVA_BIN")")"
  [[ "$ONSURE_JAVAC_BIN" == "$ONSURE_JDK_ROOT/bin/javac" ]] || {
    echo 'ONSURE_VALIDATION_SANDBOX_FAIL JAVA_JAVAC_ROOT_MISMATCH' >&2
    exit 69
  }
  ONSURE_JDK_BINDINGS=(--ro-bind "$ONSURE_JDK_ROOT" /opt/onsure-jdk)
  ONSURE_SANDBOX_PATH="/opt/onsure-jdk/bin:$ONSURE_SANDBOX_PATH"
fi

mkdir -p "$ONSURE_SNAPSHOT_ROOT/.onsure-sandbox-home"

set +e
timeout --signal=KILL --kill-after=2s "${ONSURE_TIMEOUT_SECONDS}s" \
  bwrap \
    --die-with-parent \
    --new-session \
    --unshare-user --uid 0 --gid 0 \
    --unshare-net --unshare-pid --unshare-ipc --unshare-uts \
    --cap-drop ALL \
    --clearenv \
    --setenv PATH "$ONSURE_SANDBOX_PATH" \
    --setenv HOME "$ONSURE_SANDBOX_WORKDIR/.onsure-sandbox-home" \
    --setenv TMPDIR /tmp \
    --setenv LANG C.UTF-8 \
    --setenv LC_ALL C.UTF-8 \
    --setenv MAVEN_OPTS -Dmaven.repo.local=/onsure-cache/m2 \
    --setenv npm_config_cache /onsure-cache/npm \
    --setenv npm_config_logs_dir /tmp/npm-logs \
    --setenv npm_config_update_notifier false \
    --setenv npm_config_audit false \
    --setenv npm_config_fund false \
    "${ONSURE_BINDINGS[@]}" \
    "${ONSURE_JDK_BINDINGS[@]}" \
    --bind "$ONSURE_SNAPSHOT_ROOT" "$ONSURE_SANDBOX_WORKDIR" \
    --tmpfs /tmp \
    --proc /proc \
    --dev /dev \
    --chdir "$ONSURE_SANDBOX_WORKDIR" \
    prlimit \
      --cpu="$ONSURE_TIMEOUT_SECONDS" \
      --as=8589934592 \
      --nproc=128 \
      --nofile=512 \
      --fsize=536870912 \
      -- \
      "$@"
status=$?
set -e
exit "$status"
