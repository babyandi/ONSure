#!/usr/bin/env bash
# Shared fail-closed backend selection for ONSure validation sandboxes.

ONSURE_SANDBOX_DEFAULT_OCI_IMAGE='onsure-validation-runtime:java17-node20-v1'

onsure_bwrap_probe() {
  command -v bwrap >/dev/null 2>&1 || return 1
  bwrap \
    --die-with-parent \
    --new-session \
    --unshare-user --uid 0 --gid 0 \
    --unshare-net --unshare-pid --unshare-ipc --unshare-uts \
    --cap-drop ALL \
    --ro-bind / / \
    --proc /proc \
    --dev /dev \
    /bin/true >/dev/null 2>&1
}

onsure_oci_image_id() {
  command -v docker >/dev/null 2>&1 || return 1
  local image_ref="${ONSURE_VALIDATION_OCI_IMAGE:-$ONSURE_SANDBOX_DEFAULT_OCI_IMAGE}"
  [[ "$image_ref" =~ ^[A-Za-z0-9][A-Za-z0-9._/:@-]{0,254}$ ]] || return 1
  if [[ -z "${ONSURE_SANDBOX_DOCKER_CONFIG:-}" ]]; then
    local temp_root="${ONSURE_TEMP_ROOT:-${TMPDIR:-/tmp}}"
    [[ "$temp_root" == /* && -d "$temp_root" && ! -L "$temp_root" && -w "$temp_root" ]] || return 1
    ONSURE_SANDBOX_DOCKER_CONFIG="$(mktemp -d "$temp_root/onsure-docker-config.XXXXXX")" \
      || return 1
    ONSURE_SANDBOX_DOCKER_CONFIG_OWNED='true'
  fi
  export DOCKER_CONFIG="$ONSURE_SANDBOX_DOCKER_CONFIG"
  export DOCKER_HOST='unix:///var/run/docker.sock'
  unset DOCKER_CONTEXT DOCKER_TLS_VERIFY DOCKER_CERT_PATH
  docker version --format '{{.Server.Version}}' >/dev/null 2>&1 || return 1
  local image_id
  image_id="$(docker image inspect --format '{{.Id}}' -- "$image_ref" 2>/dev/null)" || return 1
  [[ "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]] || return 1
  printf '%s\n' "$image_id"
}

onsure_sandbox_backend_cleanup() {
  if [[ "${ONSURE_SANDBOX_DOCKER_CONFIG_OWNED:-false}" == 'true' \
      && -n "${ONSURE_SANDBOX_DOCKER_CONFIG:-}" ]]; then
    case "$ONSURE_SANDBOX_DOCKER_CONFIG" in
      /*/onsure-docker-config.??????)
        rm -rf -- "$ONSURE_SANDBOX_DOCKER_CONFIG"
        ;;
    esac
  fi
  unset ONSURE_SANDBOX_DOCKER_CONFIG ONSURE_SANDBOX_DOCKER_CONFIG_OWNED DOCKER_CONFIG
}

onsure_select_sandbox_backend() {
  local requested="${1:-AUTO}"
  case "$requested" in
    AUTO)
      if onsure_bwrap_probe; then
        ONSURE_SELECTED_SANDBOX_BACKEND='ROOTLESS_BWRAP'
      elif ONSURE_SANDBOX_OCI_IMAGE_ID="$(onsure_oci_image_id)"; then
        ONSURE_SELECTED_SANDBOX_BACKEND='OCI_DOCKER'
      else
        return 1
      fi
      ;;
    ROOTLESS_BWRAP)
      ONSURE_SELECTED_SANDBOX_BACKEND='ROOTLESS_BWRAP'
      ;;
    OCI_DOCKER)
      ONSURE_SANDBOX_OCI_IMAGE_ID="$(onsure_oci_image_id)" || return 1
      ONSURE_SELECTED_SANDBOX_BACKEND='OCI_DOCKER'
      ;;
    *)
      return 1
      ;;
  esac
  export ONSURE_SELECTED_SANDBOX_BACKEND ONSURE_SANDBOX_OCI_IMAGE_ID
}
