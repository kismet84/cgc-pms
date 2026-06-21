#!/bin/bash
# CGC-PMS Daily Backup Scheduler
# Usage: ./backup-scheduler.sh [mysql|minio|all]
# M-020: Orchestrates daily backup via systemd timer or cron
#
# Scheduling options:
#   systemd timer: see docs/10-部署运维手册.md for setup instructions
#   cron:         0 2 * * * /opt/cgc-pms/scripts/backup-scheduler.sh all >> /var/log/cgc-pms-backup.log 2>&1

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="${LOG_FILE:-/var/log/cgc-pms-backup.log}"
TARGET="${1:-all}"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

run_mysql_backup() {
    log "=== Starting MySQL backup ==="
    if [ -x "${SCRIPT_DIR}/backup-mysql-full.sh" ]; then
        "${SCRIPT_DIR}/backup-mysql-full.sh" >> "${LOG_FILE}" 2>&1 && \
            log "MySQL backup completed successfully" || \
            log "MySQL backup FAILED (exit code: $?)"
    else
        log "WARN: backup-mysql-full.sh not found or not executable"
    fi
}

run_minio_backup() {
    log "=== Starting MinIO backup ==="
    if [ -x "${SCRIPT_DIR}/backup-minio-mirror.sh" ]; then
        "${SCRIPT_DIR}/backup-minio-mirror.sh" >> "${LOG_FILE}" 2>&1 && \
            log "MinIO backup completed successfully" || \
            log "MinIO backup FAILED (exit code: $?)"
    else
        log "WARN: backup-minio-mirror.sh not found or not executable"
    fi
}

run_verify() {
    log "=== Running backup verification ==="
    if [ -x "${SCRIPT_DIR}/backup-verify.sh" ]; then
        "${SCRIPT_DIR}/backup-verify.sh" >> "${LOG_FILE}" 2>&1 && \
            log "Backup verification passed" || \
            log "Backup verification FAILED (exit code: $?)"
    else
        log "WARN: backup-verify.sh not found or not executable"
    fi
}

mkdir -p "$(dirname "${LOG_FILE}")"

log "CGC-PMS Backup Scheduler started — target: ${TARGET}"

case "${TARGET}" in
    mysql)
        run_mysql_backup
        ;;
    minio)
        run_minio_backup
        ;;
    all)
        run_mysql_backup
        run_minio_backup
        run_verify
        ;;
    *)
        log "ERROR: Unknown target '${TARGET}'. Use: mysql|minio|all"
        exit 1
        ;;
esac

log "CGC-PMS Backup Scheduler finished"
