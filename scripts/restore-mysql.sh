#!/bin/bash
# CGC-PMS MySQL Restore Script
# Usage: ./restore-mysql.sh <backup_file.sql[.gz]>
# M-020: Restore MySQL from mysqldump backup
#
# IMPORTANT: This is a DESTRUCTIVE operation — it drops and recreates the database.
# Ensure you have a current backup before running this script.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <backup_file.sql[.gz]>"
    echo ""
    echo "Restore MySQL database from a mysqldump backup file."
    echo "Supports .sql (plain) and .sql.gz (gzip-compressed) backups."
    echo ""
    echo "Environment variables (optional):"
    echo "  MYSQL_HOST       — MySQL host (default: mysql)"
    echo "  MYSQL_PORT       — MySQL port (default: 3306)"
    echo "  MYSQL_USER       — MySQL user (default: root)"
    echo "  MYSQL_PASSWORD   — MySQL password (REQUIRED — source from deploy/.env)"
    echo "  MYSQL_DATABASE   — Database name (default: cgc_pms)"
    echo "  MYSQL_CONTAINER  — Docker container name (default: cgc-pms-mysql)"
    exit 1
fi

BACKUP_FILE="$1"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?MYSQL_PASSWORD must be set — source from deploy/.env}"
MYSQL_DATABASE="${MYSQL_DATABASE:-cgc_pms}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-cgc-pms-mysql}"

echo "========================================"
echo "  CGC-PMS MySQL Restore"
echo "========================================"
echo "Backup file:  ${BACKUP_FILE}"
echo "Target DB:    ${MYSQL_DATABASE}"
echo "Container:    ${MYSQL_CONTAINER}"
echo ""

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

# Check Docker container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    echo "ERROR: MySQL container '${MYSQL_CONTAINER}' is not running"
    echo "Running containers:"
    docker ps --format '{{.Names}}'
    exit 1
fi

# Confirmation
echo "WARNING: This will DROP database '${MYSQL_DATABASE}' and restore from backup."
echo "All current data will be LOST."
read -rp "Type 'yes' to confirm: " CONFIRM
if [ "${CONFIRM}" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "[$(date)] Starting restore..."

# Drop and recreate database
echo "[$(date)] Dropping and recreating database..."
docker exec "${MYSQL_CONTAINER}" \
    mysql \
        --host="${MYSQL_HOST}" \
        --port="${MYSQL_PORT}" \
        --user="${MYSQL_USER}" \
        --password="${MYSQL_PASSWORD}" \
        -e "DROP DATABASE IF EXISTS \`${MYSQL_DATABASE}\`; CREATE DATABASE \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Restore from backup
echo "[$(date)] Importing backup data..."
if [[ "${BACKUP_FILE}" == *.gz ]]; then
    # gzip-compressed backup
    zcat "${BACKUP_FILE}" | docker exec -i "${MYSQL_CONTAINER}" \
        mysql \
            --host="${MYSQL_HOST}" \
            --port="${MYSQL_PORT}" \
            --user="${MYSQL_USER}" \
            --password="${MYSQL_PASSWORD}" \
            "${MYSQL_DATABASE}"
else
    # Plain SQL backup
    docker exec -i "${MYSQL_CONTAINER}" \
        mysql \
            --host="${MYSQL_HOST}" \
            --port="${MYSQL_PORT}" \
            --user="${MYSQL_USER}" \
            --password="${MYSQL_PASSWORD}" \
            "${MYSQL_DATABASE}" < "${BACKUP_FILE}"
fi

RESTORE_EXIT=$?

if [ ${RESTORE_EXIT} -eq 0 ]; then
    echo "[$(date)] Restore completed successfully"
    echo ""
    echo "Next steps:"
    echo "  1. Flyway migrations will run on next backend startup"
    echo "  2. Restart backend: docker restart cgc-pms-backend-dev"
    echo "  3. Verify: curl http://localhost:8080/api/actuator/health"
else
    echo "[$(date)] Restore FAILED (exit code: ${RESTORE_EXIT})"
    echo "Database '${MYSQL_DATABASE}' has been dropped. Restore manually or from another backup."
    exit ${RESTORE_EXIT}
fi
