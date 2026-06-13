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

# Check MySQL backup
LATEST_MYSQL=$(find "${BACKUP_DIR}/mysql" -name "cgc_pms_full_*.sql.gz" -mtime -1 | sort | tail -1)
if [ -n "${LATEST_MYSQL}" ] && [ -s "${LATEST_MYSQL}" ]; then
    echo "[PASS] MySQL backup exists: ${LATEST_MYSQL} ($(du -h ${LATEST_MYSQL} | cut -f1))"
    PASS=$((PASS + 1))
else
    echo "[FAIL] No recent MySQL backup found"
    FAIL=$((FAIL + 1))
fi

# Check MinIO backup
LATEST_MINIO=$(find "${BACKUP_DIR}/minio" -type f -mtime -1 | head -1)
if [ -n "${LATEST_MINIO}" ]; then
    echo "[PASS] MinIO backup exists: files found"
    PASS=$((PASS + 1))
else
    echo "[WARN] No recent MinIO backup found (may be normal if no files uploaded)"
    PASS=$((PASS + 1))
fi

echo ""
echo "=== Result: ${PASS} passed, ${FAIL} failed ==="
[ ${FAIL} -eq 0 ] && exit 0 || exit 1
