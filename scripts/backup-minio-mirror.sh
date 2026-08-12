#!/usr/bin/env bash
# Mirror one MinIO bucket into a task-owned staging directory.

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/cgc-pms/backups/minio}"
MINIO_ALIAS="${MINIO_ALIAS:-cgc-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-cgc-pms}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:?MINIO_ACCESS_KEY must be set}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:?MINIO_SECRET_KEY must be set}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"

mkdir -p "${BACKUP_DIR}"
BACKUP_DIR="$(cd "${BACKUP_DIR}" && pwd -P)"
[[ -n "${BACKUP_DIR}" && "${BACKUP_DIR}" != '/' ]] || {
  echo 'Refusing unsafe MinIO backup directory' >&2
  exit 1
}

# Never reuse the caller's global mc aliases. The config can contain credentials,
# so create it beside (not inside) the payload and remove it on every exit.
MC_CONFIG_PARENT="$(dirname "${BACKUP_DIR}")"
MC_CONFIG_DIR="$(mktemp -d "${MC_CONFIG_PARENT}/.mc-config.XXXXXX.partial")"
[[ "${MC_CONFIG_DIR}" == "${MC_CONFIG_PARENT}/"* ]] || {
  echo 'Unsafe task-owned mc configuration path' >&2
  exit 1
}
cleanup_mc_config() {
  rm -rf -- "${MC_CONFIG_DIR}"
}
trap cleanup_mc_config EXIT

echo "[$(date)] Starting MinIO mirror backup..."
for value in "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"; do
  [[ ! "${value}" =~ [[:cntrl:]] ]] || {
    echo 'MinIO connection values must not contain control characters' >&2
    exit 1
  }
done
json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '%s' "${value}"
}
MC_ALIAS_CONFIG="${MC_CONFIG_DIR}/alias.json"
umask 077
printf '{"url":"%s","accessKey":"%s","secretKey":"%s","api":"s3v4","path":"auto"}\n' \
  "$(json_escape "${MINIO_ENDPOINT}")" \
  "$(json_escape "${MINIO_ACCESS_KEY}")" \
  "$(json_escape "${MINIO_SECRET_KEY}")" > "${MC_ALIAS_CONFIG}"
# Import through stdin: credentials never enter process arguments.
mc --config-dir "${MC_CONFIG_DIR}" alias import "${MINIO_ALIAS}" < "${MC_ALIAS_CONFIG}" >/dev/null
rm -f -- "${MC_ALIAS_CONFIG}"

# Do not use --remove: an empty or misconfigured source must never delete an
# existing destination. Batch staging is new, so overwrite is deterministic.
mc --config-dir "${MC_CONFIG_DIR}" mirror --overwrite \
  "${MINIO_ALIAS}/${MINIO_BUCKET}" "${BACKUP_DIR}/"

FILE_COUNT="$(find "${BACKUP_DIR}" -type f -print | wc -l | tr -d '[:space:]')"
TOTAL_SIZE="$(du -sh "${BACKUP_DIR}" 2>/dev/null | cut -f1)"
echo "[$(date)] MinIO mirror backed up ${FILE_COUNT} objects, total: ${TOTAL_SIZE}"
