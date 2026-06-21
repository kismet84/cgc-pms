#!/bin/bash
# CGC-PMS MySQL Full Backup Script
# Usage: ./backup-mysql-full.sh [backup_dir]
# M-020: Created per backup recovery plan documentation

set -euo pipefail

# Scheduling (choose one):
#   crontab:  0 2 * * * /opt/cgc-pms/scripts/backup-mysql-full.sh
#   systemd:  See deploy/backup/cgc-pms-backup.service + .timer

BACKUP_DIR="${1:-/opt/cgc-pms/backups/mysql}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/cgc_pms_full_${TIMESTAMP}.sql.gz"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?MYSQL_PASSWORD must be set — source from deploy/.env}"
MYSQL_DATABASE="${MYSQL_DATABASE:-cgc_pms}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-cgc-pms-mysql}"

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Starting MySQL full backup..."

docker exec "${MYSQL_CONTAINER}" \
  mysqldump \
    --host="${MYSQL_HOST}" \
    --port="${MYSQL_PORT}" \
    --user="${MYSQL_USER}" \
    --password="${MYSQL_PASSWORD}" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --set-gtid-purged=OFF \
    "${MYSQL_DATABASE}" \
  | gzip > "${BACKUP_FILE}"

if [ $? -eq 0 ] && [ -s "${BACKUP_FILE}" ]; then
    echo "[$(date)] Backup successful: ${BACKUP_FILE} ($(du -h ${BACKUP_FILE} | cut -f1))"
else
    echo "[$(date)] Backup FAILED!"
    rm -f "${BACKUP_FILE}"
    exit 1
fi

# Cleanup: keep last 7 daily backups
find "${BACKUP_DIR}" -name "cgc_pms_full_*.sql.gz" -mtime +7 -delete
echo "[$(date)] Cleaned up backups older than 7 days"
