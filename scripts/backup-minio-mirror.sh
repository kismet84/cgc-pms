#!/bin/bash
# CGC-PMS MinIO Mirror Backup Script
# Usage: ./backup-minio-mirror.sh
# M-020: Created per backup recovery plan documentation

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/cgc-pms/backups/minio}"
MINIO_ALIAS="${MINIO_ALIAS:-cgc-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-cgc-pms}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:?MINIO_ACCESS_KEY must be set}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:?MINIO_SECRET_KEY must be set}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Starting MinIO mirror backup..."

# Configure mc alias if not exists
mc alias set "${MINIO_ALIAS}" "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}" --api S3v4 2>/dev/null || true

# NOTE: Do NOT use --remove — it deletes destination files not present in source,
# which can cause data loss if the source bucket is misconfigured or accidentally emptied.
mc mirror --overwrite "${MINIO_ALIAS}/${MINIO_BUCKET}" "${BACKUP_DIR}/"

echo "[$(date)] MinIO mirror backup completed"
FILE_COUNT=$(find "${BACKUP_DIR}" -type f 2>/dev/null | wc -l)
TOTAL_SIZE=$(du -sh "${BACKUP_DIR}" 2>/dev/null | cut -f1)
echo "[$(date)] MinIO mirror backed up ${FILE_COUNT} files, total: ${TOTAL_SIZE}"
