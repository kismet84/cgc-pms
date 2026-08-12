#!/usr/bin/env bash
# Create one verified MySQL archive inside the supplied directory.
# Usage: ./backup-mysql-full.sh [backup_dir]

set -euo pipefail

BACKUP_DIR="${1:-/opt/cgc-pms/backups/mysql}"
TIMESTAMP="${BACKUP_TIMESTAMP:-$(date +%Y%m%d_%H%M%S)}"
BACKUP_FILE="${BACKUP_DIR}/cgc_pms_full_${TIMESTAMP}.sql.gz"
PARTIAL_FILE="${BACKUP_FILE}.partial"
VALIDATION_SQL="${BACKUP_DIR}/.cgc_pms_full_${TIMESTAMP}.validation.sql.partial"
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?MYSQL_PASSWORD must be set - source from deploy/.env}"
MYSQL_DATABASE="${MYSQL_DATABASE:-cgc_pms}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-cgc-pms-mysql}"

[[ "${TIMESTAMP}" =~ ^[0-9]{8}_[0-9]{6}$ ]] || {
  echo "Invalid BACKUP_TIMESTAMP: ${TIMESTAMP}" >&2
  exit 1
}
[[ "${MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || {
  echo "Invalid MYSQL_DATABASE: ${MYSQL_DATABASE}" >&2
  exit 1
}

cleanup() {
  rm -f -- "${PARTIAL_FILE}" "${VALIDATION_SQL}"
}
trap cleanup EXIT

mkdir -p "${BACKUP_DIR}"
if [[ -e "${BACKUP_FILE}" || -e "${PARTIAL_FILE}" ]]; then
  echo "Backup target already exists: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "[$(date)] Starting MySQL full backup..."
MYSQL_PWD="${MYSQL_PASSWORD}" docker exec -e MYSQL_PWD "${MYSQL_CONTAINER}" \
  mysqldump \
    --host="${MYSQL_HOST}" \
    --port="${MYSQL_PORT}" \
    --user="${MYSQL_USER}" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --set-gtid-purged=OFF \
    "${MYSQL_DATABASE}" \
  | gzip > "${PARTIAL_FILE}"

[[ -s "${PARTIAL_FILE}" ]] || { echo 'MySQL backup is empty' >&2; exit 1; }
gzip -t "${PARTIAL_FILE}"
gzip -dc "${PARTIAL_FILE}" > "${VALIDATION_SQL}"
grep -qE '(CREATE TABLE|INSERT INTO|DROP TABLE|CREATE DATABASE)' "${VALIDATION_SQL}" || {
  echo 'MySQL backup does not contain a valid SQL dump signature' >&2
  exit 1
}

mv -- "${PARTIAL_FILE}" "${BACKUP_FILE}"
rm -f -- "${VALIDATION_SQL}"
trap - EXIT

echo "[$(date)] MySQL backup verified: ${BACKUP_FILE}"
printf '%s\n' "${BACKUP_FILE}"
