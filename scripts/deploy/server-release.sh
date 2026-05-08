#!/usr/bin/env bash
set -euo pipefail

MUTATION_STARTED=0
ROLLBACK_ATTEMPTED=0
HAD_LIVE_BINARY=0
HAD_LIVE_CONFIG=0

require_env() {
  local name="$1"

  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

validate_archive_paths() {
  local line
  local typeflag
  local entry

  while IFS= read -r line; do
    [[ -z "$line" ]] && continue

    typeflag="${line%% *}"
    entry="${line#* }"
    [[ -z "$entry" ]] && continue

    if [[ "$typeflag" != "-" && "$typeflag" != "d" ]]; then
      echo "Unsafe archive entry (unsupported type $typeflag): $entry" >&2
      return 1
    fi

    if [[ "$entry" = /* ]]; then
      echo "Unsafe archive entry (absolute path): $entry" >&2
      return 1
    fi

    if [[ "$entry" == ".." || "$entry" == ../* || "$entry" == */../* || "$entry" == */.. ]]; then
      echo "Unsafe archive entry (path traversal): $entry" >&2
      return 1
    fi
  done < <(tar -tvzf "$DEPLOY_ARCHIVE" | awk '{print substr($0,1,1) " " substr($0,index($0,$6))}')
}

restore_backups() {
  local attempted=0

  if [[ "$HAD_LIVE_BINARY" -eq 1 ]]; then
    attempted=1
    install -m 0755 "$BACKUP_BINARY" "$LIVE_BINARY"
  elif [[ -e "$LIVE_BINARY" ]]; then
    attempted=1
    rm -f "$LIVE_BINARY"
  fi

  if [[ "$HAD_LIVE_CONFIG" -eq 1 ]]; then
    attempted=1
    install -m 0644 "$BACKUP_CONFIG" "$LIVE_CONFIG"
  elif [[ -e "$LIVE_CONFIG" ]]; then
    attempted=1
    rm -f "$LIVE_CONFIG"
  fi

  if [[ "$attempted" -eq 0 ]]; then
    return 1
  fi

  return 0
}

rollback_on_error() {
  local exit_code="$1"

  if [[ "$MUTATION_STARTED" -ne 1 || "$ROLLBACK_ATTEMPTED" -eq 1 ]]; then
    return "$exit_code"
  fi

  ROLLBACK_ATTEMPTED=1
  echo "Deployment failed during mutation; attempting to restore backups" >&2
  restore_backups
  return "$exit_code"
}

wait_for_health() {
  local response
  local attempt

  for attempt in $(seq 1 10); do
    response="$(curl --fail --silent --show-error --connect-timeout 3 --max-time 10 "$DEPLOY_HEALTH_URL" || true)"

    if [[ -n "$response" ]] && grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"' <<<"$response"; then
      echo "Health check passed on attempt $attempt"
      return 0
    fi

    sleep 3
  done

  return 1
}

rollback_release() {
  echo "Attempting rollback using available backup artifacts"
  ROLLBACK_ATTEMPTED=1
  restore_backups

  if ! systemctl restart "$DEPLOY_SERVICE_NAME"; then
    echo "Rollback restart failed for service: $DEPLOY_SERVICE_NAME" >&2
    return 1
  fi

  if ! wait_for_health; then
    echo "Rollback health validation failed" >&2
    return 1
  fi

  echo "Rollback completed successfully"
  return 0
}

resolve_release_root() {
  if [[ -d "$STAGING_DIR/clipSync-server/bin" && -d "$STAGING_DIR/clipSync-server/configs" ]]; then
    printf '%s\n' "$STAGING_DIR/clipSync-server"
    return 0
  fi

  if [[ -d "$STAGING_DIR/bin" && -d "$STAGING_DIR/configs" ]]; then
    printf '%s\n' "$STAGING_DIR"
    return 0
  fi

  echo "Unable to locate release root in extracted archive" >&2
  return 1
}

require_env DEPLOY_ARCHIVE
require_env DEPLOY_PATH
require_env DEPLOY_SERVICE_NAME

DEPLOY_HEALTH_URL="${DEPLOY_HEALTH_URL:-http://127.0.0.1:8081/api/v1/health}"

if [[ ! -f "$DEPLOY_ARCHIVE" ]]; then
  echo "Deploy archive not found: $DEPLOY_ARCHIVE" >&2
  exit 1
fi

validate_archive_paths

STAGING_DIR="$(mktemp -d /tmp/clipsync-release.XXXXXX)"
trap 'rm -rf "$STAGING_DIR"' EXIT

tar -xzf "$DEPLOY_ARCHIVE" -C "$STAGING_DIR"

RELEASE_ROOT="$(resolve_release_root)"
NEW_BINARY="$RELEASE_ROOT/bin/clipsync-server-linux"
NEW_CONFIG="$RELEASE_ROOT/configs/config.yaml"
LIVE_BINARY="$DEPLOY_PATH/bin/clipsync-server-linux"
BACKUP_BINARY="$DEPLOY_PATH/bin/clipsync-server-linux.prev"
TEMP_BINARY="$DEPLOY_PATH/bin/clipsync-server-linux.new"
LIVE_CONFIG="$DEPLOY_PATH/configs/config.yaml"
BACKUP_CONFIG="$DEPLOY_PATH/configs/config.yaml.prev"

if [[ ! -f "$NEW_BINARY" || -L "$NEW_BINARY" ]]; then
  echo "Release binary missing from archive: $NEW_BINARY" >&2
  exit 1
fi

if [[ ! -f "$NEW_CONFIG" || -L "$NEW_CONFIG" ]]; then
  echo "Release config missing from archive: $NEW_CONFIG" >&2
  exit 1
fi

install -d "$DEPLOY_PATH/bin" "$DEPLOY_PATH/configs" "$DEPLOY_PATH/data"

if [[ -f "$LIVE_BINARY" ]]; then
  HAD_LIVE_BINARY=1
  install -m 0755 "$LIVE_BINARY" "$BACKUP_BINARY"
fi

if [[ -f "$LIVE_CONFIG" ]]; then
  HAD_LIVE_CONFIG=1
  install -m 0644 "$LIVE_CONFIG" "$BACKUP_CONFIG"
fi

MUTATION_STARTED=1
trap 'rollback_on_error "$?"' ERR

install -m 0755 "$NEW_BINARY" "$TEMP_BINARY"
mv -f "$TEMP_BINARY" "$LIVE_BINARY"
install -m 0644 "$NEW_CONFIG" "$LIVE_CONFIG"

if ! systemctl restart "$DEPLOY_SERVICE_NAME"; then
  echo "Service restart failed: $DEPLOY_SERVICE_NAME" >&2
  rollback_release
  exit 1
fi

if ! wait_for_health; then
  echo "Post-deploy health check failed" >&2
  rollback_release
  exit 1
fi

trap - ERR
rm -f "$DEPLOY_ARCHIVE"
echo "Deployment completed successfully"
