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

ONSURE_SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ONSURE_BACKEND_HELPER="$ONSURE_SCRIPT_ROOT/onsure-sandbox-backend.sh"
[[ -f "$ONSURE_BACKEND_HELPER" && ! -L "$ONSURE_BACKEND_HELPER" ]] || {
  echo 'ONSURE_VALIDATION_SANDBOX_FAIL BACKEND_HELPER_MISSING' >&2
  exit 69
}
# shellcheck source=onsure-sandbox-backend.sh
source "$ONSURE_BACKEND_HELPER"

[[ "$ONSURE_TIMEOUT_SECONDS" =~ ^[0-9]+$ \
  && "$ONSURE_TIMEOUT_SECONDS" -ge 1 \
  && "$ONSURE_TIMEOUT_SECONDS" -le 7200 ]] || {
  echo 'ONSURE_VALIDATION_SANDBOX_FAIL INVALID_TIMEOUT' >&2
  exit 64
}

for required_command in prlimit timeout bash env mktemp readlink dirname; do
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
    if [[ "${ONSURE_SANDBOX_PROBE:-}" == '1' ]]; then
      [[ "${2:-}" == '.onsure/internal/environment-probe.sh' \
        && -f "$ONSURE_SNAPSHOT_ROOT/.onsure/internal/environment-probe.sh" \
        && ! -L "$ONSURE_SNAPSHOT_ROOT/.onsure/internal/environment-probe.sh" ]] || {
        echo 'ONSURE_VALIDATION_SANDBOX_FAIL ENVIRONMENT_PROBE_DENIED' >&2
        exit 65
      }
    else
      [[ "${2:-}" == 'gradlew' && -f "$ONSURE_SNAPSHOT_ROOT/gradlew" \
        && ! -L "$ONSURE_SNAPSHOT_ROOT/gradlew" && " $* " == *' --offline '* ]] || {
        echo 'ONSURE_VALIDATION_SANDBOX_FAIL GRADLE_COMMAND_DENIED' >&2
        exit 65
      }
    fi
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

ONSURE_REQUESTED_BACKEND="${ONSURE_VALIDATION_SANDBOX_BACKEND:-AUTO}"
onsure_select_sandbox_backend "$ONSURE_REQUESTED_BACKEND" || {
  onsure_sandbox_backend_cleanup
  echo "ONSURE_VALIDATION_SANDBOX_FAIL BACKEND_UNAVAILABLE_$ONSURE_REQUESTED_BACKEND" >&2
  exit 69
}
ONSURE_SELECTED_BACKEND="$ONSURE_SELECTED_SANDBOX_BACKEND"

if [[ "$ONSURE_SELECTED_BACKEND" == 'OCI_DOCKER' ]]; then
  trap onsure_sandbox_backend_cleanup EXIT
  for required_command in docker id; do
    command -v "$required_command" >/dev/null 2>&1 || {
      onsure_sandbox_backend_cleanup
      echo "ONSURE_VALIDATION_SANDBOX_FAIL MISSING_COMMAND_$required_command" >&2
      exit 69
    }
  done
  [[ "$ONSURE_SNAPSHOT_ROOT" != *','* && "$ONSURE_SNAPSHOT_ROOT" != *':'* \
      && "$ONSURE_SNAPSHOT_ROOT" != *$'\n'* ]] || {
    echo 'ONSURE_VALIDATION_SANDBOX_FAIL OCI_MOUNT_PATH_UNSUPPORTED' >&2
    exit 65
  }
  mkdir -p "$ONSURE_SNAPSHOT_ROOT/.onsure-sandbox-home"
  ONSURE_CONTAINER_NAME="onsure-validation-${UID}-$$-${RANDOM}"
  ONSURE_OCI_MOUNTS=(--mount "type=bind,src=$ONSURE_SNAPSHOT_ROOT,dst=$ONSURE_SANDBOX_WORKDIR")
  if [[ -n "${ONSURE_MAVEN_CACHE:-}" && -d "${ONSURE_MAVEN_CACHE}" \
      && ! -L "${ONSURE_MAVEN_CACHE}" && "${ONSURE_MAVEN_CACHE}" != *','* \
      && "${ONSURE_MAVEN_CACHE}" != *':'* ]]; then
    ONSURE_OCI_MOUNTS+=(--mount "type=bind,src=${ONSURE_MAVEN_CACHE},dst=/onsure-cache/m2,readonly")
  fi
  if [[ -n "${ONSURE_NPM_CACHE:-}" && -d "${ONSURE_NPM_CACHE}" \
      && ! -L "${ONSURE_NPM_CACHE}" && "${ONSURE_NPM_CACHE}" != *','* \
      && "${ONSURE_NPM_CACHE}" != *':'* ]]; then
    ONSURE_OCI_MOUNTS+=(--mount "type=bind,src=${ONSURE_NPM_CACHE},dst=/onsure-cache/npm,readonly")
  fi
  cleanup_oci() {
    docker rm -f "$ONSURE_CONTAINER_NAME" >/dev/null 2>&1 || true
    onsure_sandbox_backend_cleanup
  }
  trap cleanup_oci EXIT
  set +e
  timeout --signal=KILL --kill-after=2s "${ONSURE_TIMEOUT_SECONDS}s" \
    docker run --rm --pull never \
      --name "$ONSURE_CONTAINER_NAME" \
      --label io.onsure.sandbox=validation \
      --init \
      --network none \
      --read-only \
      --cap-drop ALL \
      --security-opt no-new-privileges:true \
      --security-opt apparmor=docker-default \
      --user "$(id -u):$(id -g)" \
      --pids-limit 128 \
      --memory 8589934592 --memory-swap 8589934592 \
      --cpus 4 \
      --ulimit nofile=512:512 \
      --ulimit fsize=536870912:536870912 \
      --tmpfs "/tmp:rw,noexec,nosuid,nodev,size=536870912,mode=1777,uid=$(id -u),gid=$(id -g)" \
      "${ONSURE_OCI_MOUNTS[@]}" \
      --workdir "$ONSURE_SANDBOX_WORKDIR" \
      --env PATH=/opt/java/openjdk/bin:/usr/local/bin:/usr/bin:/bin \
      --env "HOME=$ONSURE_SANDBOX_WORKDIR/.onsure-sandbox-home" \
      --env TMPDIR=/tmp \
      --env LANG=C.UTF-8 \
      --env LC_ALL=C.UTF-8 \
      --env MAVEN_OPTS=-Dmaven.repo.local=/onsure-cache/m2 \
      --env npm_config_cache=/onsure-cache/npm \
      --env npm_config_logs_dir=/tmp/npm-logs \
      --env npm_config_update_notifier=false \
      --env npm_config_audit=false \
      --env npm_config_fund=false \
      --entrypoint /usr/bin/prlimit \
      "$ONSURE_SANDBOX_OCI_IMAGE_ID" \
        --cpu="$ONSURE_TIMEOUT_SECONDS" \
        --as=8589934592 \
        --nofile=512 \
        --fsize=536870912 \
        -- \
        "$@"
  status=$?
  set -e
  if [[ $status -eq 0 && "${ONSURE_SANDBOX_PROBE:-}" == '1' ]]; then
    echo "ONSURE_VALIDATION_SANDBOX_BACKEND OCI_DOCKER $ONSURE_SANDBOX_OCI_IMAGE_ID"
  fi
  exit "$status"
fi

command -v bwrap >/dev/null 2>&1 || {
  echo 'ONSURE_VALIDATION_SANDBOX_FAIL MISSING_COMMAND_bwrap' >&2
  exit 69
}

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
if [[ $status -eq 0 && "${ONSURE_SANDBOX_PROBE:-}" == '1' ]]; then
  echo 'ONSURE_VALIDATION_SANDBOX_BACKEND ROOTLESS_BWRAP'
fi
exit "$status"
