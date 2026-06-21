#!/bin/bash
# CGC-PMS Backup Verification Script
# Usage: ./backup-verify.sh [backup_dir]
# M-020: Created per backup recovery plan documentation

set -euo pipefail

BACKUP_DIR="${1:-/opt/cgc-pms/backups}"
PASS=0
FAIL=0

echo "=== CGC-PMS Backup Verification ==="
echo "Date: $(date)"

# Check MySQL backup — verify gzip integrity AND SQL content
LATEST_MYSQL=$(find "${BACKUP_DIR}/mysql" -name "cgc_pms_full_*.sql.gz" -mtime -1 | sort | tail -1)
if [ -n "${LATEST_MYSQL}" ] && [ -s "${LATEST_MYSQL}" ]; then
    if gzip -t "${LATEST_MYSQL}" 2>/dev/null && \
       zcat "${LATEST_MYSQL}" 2>/dev/null | head -200 | grep -qE "(CREATE TABLE|INSERT INTO|DROP TABLE|CREATE DATABASE)"; then
        ROW_COUNT=$(zcat "${LATEST_MYSQL}" 2>/dev/null | grep -c "INSERT INTO" || echo "0")
        echo "[PASS] MySQL backup valid: ${LATEST_MYSQL} ($(du -h ${LATEST_MYSQL} | cut -f1), ~${ROW_COUNT} INSERT statements)"
        PASS=$((PASS + 1))
    else
        echo "[FAIL] MySQL backup integrity check failed (not valid SQL): ${LATEST_MYSQL}"
        FAIL=$((FAIL + 1))
    fi
else
    echo "[FAIL] No recent MySQL backup found"
    FAIL=$((FAIL + 1))
fi

# Check MinIO backup — verify data directory has actual content (>1KB)
LATEST_MINIO_COUNT=$(find "${BACKUP_DIR}/minio" -type f -mtime -1 2>/dev/null | wc -l)
LATEST_MINIO_SIZE=$(du -sb "${BACKUP_DIR}/minio" 2>/dev/null | cut -f1 || echo "0")
if [ "${LATEST_MINIO_COUNT}" -gt 0 ] && [ -n "${LATEST_MINIO_SIZE}" ] && [ "${LATEST_MINIO_SIZE}" -gt 1024 ]; then
    echo "[PASS] MinIO backup valid: ${LATEST_MINIO_COUNT} files, $(du -sh ${BACKUP_DIR}/minio 2>/dev/null | cut -f1)"
    PASS=$((PASS + 1))
elif [ "${LATEST_MINIO_COUNT}" -gt 0 ]; then
    echo "[FAIL] MinIO backup too small (${LATEST_MINIO_COUNT} files, ${LATEST_MINIO_SIZE} bytes) — may be corrupted"
    FAIL=$((FAIL + 1))
else
    echo "[WARN] No recent MinIO backup files found (OK if bucket is empty)"
fi

echo ""
echo "=== Result: ${PASS} passed, ${FAIL} failed ==="
[ ${FAIL} -eq 0 ] && exit 0 || exit 1
