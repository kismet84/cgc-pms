#!/usr/bin/env bash
# CGC-PMS daily backup scheduler.
# Usage: ./backup-scheduler.sh [all|mysql|minio]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/cgc-pms-backup.log}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/cgc-pms/backups}"
TARGET="${1:-all}"

mkdir -p "$(dirname "${LOG_FILE}")"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

run_logged() {
  local name="$1"
  shift
  log "=== Starting ${name} ==="
  if "$@" >> "${LOG_FILE}" 2>&1; then
    log "${name} completed successfully"
  else
    local exit_code=$?
    log "${name} FAILED (exit code: ${exit_code})"
    return "${exit_code}"
  fi
}

log "CGC-PMS backup scheduler started - target: ${TARGET}"
case "${TARGET}" in
  all)
    # Only this path is scheduled: one verified batch becomes visible atomically.
    run_logged 'atomic MySQL + MinIO backup batch' \
      bash "${SCRIPT_DIR}/backup-batch.sh" "${BACKUP_ROOT}"
    ;;
  mysql)
    # Manual component diagnostic. It is not a COMPLETE batch and has no retention.
    run_logged 'standalone MySQL backup diagnostic' \
      bash "${SCRIPT_DIR}/backup-mysql-full.sh" "${BACKUP_ROOT}/mysql-diagnostics"
    ;;
  minio)
    # Manual component diagnostic. It is not a COMPLETE batch and has no retention.
    run_logged 'standalone MinIO backup diagnostic' \
      env BACKUP_DIR="${BACKUP_ROOT}/minio-diagnostics" bash "${SCRIPT_DIR}/backup-minio-mirror.sh"
    ;;
  *)
    log "ERROR: Unknown target '${TARGET}'. Use: all|mysql|minio"
    exit 1
    ;;
esac

log 'CGC-PMS backup scheduler finished'
