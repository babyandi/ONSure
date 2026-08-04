#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  source-read-only)
    if printf 'TAMPER\n' >> protected.txt 2>/dev/null; then
      echo 'SOURCE_WRITE_ALLOWED'
      exit 1
    fi
    grep -qx 'ONSURE_SANDBOX_PROTECTED_BASELINE' protected.txt
    echo 'SOURCE_WRITE_BLOCKED'
    ;;
  tmp-writable)
    printf 'TMP_OK\n' > /tmp/onsure-sandbox-probe
    grep -qx 'TMP_OK' /tmp/onsure-sandbox-probe
    echo 'TMP_WRITE_ALLOWED'
    ;;
  network-egress)
    if grep -Eq '^[^[:space:]]+[[:space:]]+00000000[[:space:]]' /proc/net/route 2>/dev/null; then
      echo 'NETWORK_DEFAULT_ROUTE_PRESENT'
      exit 1
    fi
    if timeout 2 bash -c 'exec 3<>/dev/tcp/1.1.1.1/80' 2>/dev/null; then
      echo 'NETWORK_EGRESS_ALLOWED'
      exit 1
    fi
    echo 'NETWORK_EGRESS_BLOCKED'
    ;;
  filesystem-escape)
    set +e
    printf 'ESCAPE\n' > /onsure-host-escape-probe 2>/dev/null
    root_status=$?
    printf 'ESCAPE\n' > /etc/onsure-host-escape-probe 2>/dev/null
    etc_status=$?
    set -e
    if [[ $root_status -eq 0 || $etc_status -eq 0 ]]; then
      echo "FILESYSTEM_ESCAPE_ALLOWED root=$root_status etc=$etc_status"
      exit 1
    fi
    echo "FILESYSTEM_ESCAPE_BLOCKED root=$root_status etc=$etc_status"
    ;;
  symlink-escape)
    [[ -L escape-link ]] || {
      echo 'SYMLINK_ESCAPE_FIXTURE_MISSING'
      exit 1
    }
    set +e
    cat escape-link >/dev/null 2>&1
    link_status=$?
    set -e
    if [[ $link_status -eq 0 ]]; then
      if [[ "${ONSURE_SANDBOX_BACKEND_ACTUAL:-}" == 'OCI_DOCKER' \
          && -n "${ONSURE_HOST_PASSWD_SHA256:-}" \
          && "$(sha256sum escape-link | awk '{print $1}')" != "$ONSURE_HOST_PASSWD_SHA256" ]]; then
        echo 'SYMLINK_ESCAPE_BLOCKED runtime_root_isolated=true'
        exit 0
      fi
      echo 'SYMLINK_ESCAPE_ALLOWED'
      exit 1
    fi
    echo "SYMLINK_ESCAPE_BLOCKED status=$link_status"
    ;;
  child-process)
    setsid bash -c 'exec -a onsure-sandbox-child-probe sleep 30' >/dev/null 2>&1 &
    echo 'CHILD_STARTED'
    ;;
  cpu-exhaustion)
    while :; do :; done
    ;;
  memory-exhaustion)
    python3 -c 'bytearray(9 * 1024 * 1024 * 1024)'
    echo 'MEMORY_LIMIT_NOT_ENFORCED'
    ;;
  capabilities)
    cap_eff="$(awk '/^CapEff:/ {print $2}' /proc/self/status)"
    [[ "$cap_eff" == '0000000000000000' ]]
    echo 'CAPABILITIES_DROPPED'
    ;;
  environment)
    [[ -z "${UNRELATED_SECRET_FOR_ONSURE_TEST:-}" ]]
    [[ "${ONSURE_FIXTURE_TEST_MARKER:-}" == 'ALLOWED_MARKER' ]]
    echo 'ENVIRONMENT_FILTERED'
    ;;
  limits)
    nofile="$(ulimit -n)"
    fsize="$(ulimit -f)"
    nproc="$(ulimit -u)"
    pids_max="$(cat /sys/fs/cgroup/pids.max 2>/dev/null || printf 'max')"
    vmem="$(ulimit -v)"
    memory_max="$(cat /sys/fs/cgroup/memory.max 2>/dev/null || printf 'max')"
    cpu="$(ulimit -t)"
    [[ "$nofile" -le 256 ]]
    [[ "$fsize" -le 2048 ]]
    if [[ "$nproc" =~ ^[0-9]+$ ]]; then
      (( nproc <= 64 ))
    else
      [[ "$pids_max" =~ ^[0-9]+$ ]]
      (( pids_max <= 64 ))
    fi
    if [[ "$vmem" =~ ^[0-9]+$ ]]; then
      (( vmem <= 8388608 ))
    else
      [[ "$memory_max" =~ ^[0-9]+$ ]]
      (( memory_max <= 8589934592 ))
    fi
    [[ "$cpu" -le 300 ]]
    echo "RESOURCE_LIMITS_ENFORCED nofile=$nofile fsize=$fsize nproc=$nproc pids_max=$pids_max vmem=$vmem memory_max=$memory_max cpu=$cpu"
    ;;
  timeout)
    sleep 30
    echo 'TIMEOUT_NOT_ENFORCED'
    ;;
  *)
    echo 'UNKNOWN_SANDBOX_BOUNDARY_PROBE' >&2
    exit 64
    ;;
esac
