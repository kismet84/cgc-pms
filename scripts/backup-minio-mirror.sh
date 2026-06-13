#!/bin/bash
# CGC-PMS MinIO Mirror Backup Script
# Usage: ./backup-minio-mirror.sh
# M-020: Created per backup recovery plan documentation

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/cgc-pms/backups/minio}"
MINIO_ALIAS="${MINIO_ALIAS:-cgc-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-cgc-pms}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Starting MinIO mirror backup..."

# Configure mc alias if not exists
mc alias set "${MINIO_ALIAS}" "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" --api S3v4 2>/dev/null || true

mc mirror --overwrite --remove "${MINIO_ALIAS}/${MINIO_BUCKET}" "${BACKUP_DIR}/"

echo "[$(date)] MinIO mirror backup completed"
