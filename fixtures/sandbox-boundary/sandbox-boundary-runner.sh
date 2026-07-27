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
    if printf 'ESCAPE\n' > /onsure-host-escape-probe 2>/dev/null; then
      echo 'FILESYSTEM_ESCAPE_ALLOWED'
      exit 1
    fi
    if printf 'ESCAPE\n' > /etc/onsure-host-escape-probe 2>/dev/null; then
      echo 'FILESYSTEM_ESCAPE_ALLOWED'
      exit 1
    fi
    echo 'FILESYSTEM_ESCAPE_BLOCKED'
    ;;
  symlink-escape)
    [[ -L escape-link ]] || {
      echo 'SYMLINK_ESCAPE_FIXTURE_MISSING'
      exit 1
    }
    if cat escape-link >/dev/null 2>&1; then
      echo 'SYMLINK_ESCAPE_ALLOWED'
      exit 1
    fi
    echo 'SYMLINK_ESCAPE_BLOCKED'
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
    vmem="$(ulimit -v)"
    cpu="$(ulimit -t)"
    [[ "$nofile" -le 256 ]]
    [[ "$fsize" -le 2048 ]]
    [[ "$nproc" -le 64 ]]
    [[ "$vmem" -le 8388608 ]]
    [[ "$cpu" -le 300 ]]
    echo "RESOURCE_LIMITS_ENFORCED nofile=$nofile fsize=$fsize nproc=$nproc vmem=$vmem cpu=$cpu"
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
