#!/usr/bin/env bash
# Publish one verified MySQL + MinIO backup batch atomically.
# Usage: ./backup-batch.sh [backup_root]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_ROOT="${1:-${BACKUP_ROOT:-/opt/cgc-pms/backups}}"
BATCH_ID="${BATCH_ID:-$(date +%Y%m%d_%H%M%S)}"
RETENTION_COUNT="${BACKUP_RETENTION_COUNT:-7}"
SOURCE_DATABASE="${MYSQL_DATABASE:-cgc_pms}"
SOURCE_BUCKET="${MINIO_BUCKET:?MINIO_BUCKET must be set}"

[[ "${BATCH_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
  echo "Invalid BATCH_ID: ${BATCH_ID}" >&2
  exit 1
}
[[ "${RETENTION_COUNT}" =~ ^[1-9][0-9]*$ ]] || {
  echo "BACKUP_RETENTION_COUNT must be a positive integer" >&2
  exit 1
}
[[ "${SOURCE_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]] || {
  echo "Invalid MYSQL_DATABASE: ${SOURCE_DATABASE}" >&2
  exit 1
}
[[ "${SOURCE_BUCKET}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
  echo "Invalid MINIO_BUCKET: ${SOURCE_BUCKET}" >&2
  exit 1
}

[[ ! -L "${BACKUP_ROOT}" ]] || {
  echo 'BACKUP_ROOT must not be a symbolic link' >&2
  exit 1
}
mkdir -p "${BACKUP_ROOT}"
BACKUP_ROOT="$(cd "${BACKUP_ROOT}" && pwd -P)"
[[ -n "${BACKUP_ROOT}" && "${BACKUP_ROOT}" != '/' ]] || {
  echo 'Refusing unsafe backup root' >&2
  exit 1
}

PARTIAL_ROOT="${BACKUP_ROOT}/.partial"
COMPLETE_ROOT="${BACKUP_ROOT}/complete"
PARTIAL_BATCH="${PARTIAL_ROOT}/${BATCH_ID}"
FINAL_BATCH="${COMPLETE_ROOT}/${BATCH_ID}"

cleanup_partial() {
  if [[ -n "${PARTIAL_BATCH:-}" && "${PARTIAL_BATCH}" == "${PARTIAL_ROOT}/"* ]]; then
    rm -rf -- "${PARTIAL_BATCH}"
  fi
}
trap cleanup_partial EXIT

for managed_root in "${PARTIAL_ROOT}" "${COMPLETE_ROOT}"; do
  [[ ! -L "${managed_root}" ]] || {
    echo "Managed backup directory must not be a symbolic link: ${managed_root}" >&2
    exit 1
  }
  mkdir -p "${managed_root}"
  [[ -d "${managed_root}" && ! -L "${managed_root}" ]] || {
    echo "Managed backup directory is unsafe: ${managed_root}" >&2
    exit 1
  }
done
partial_device="$(stat -c '%d' -- "${PARTIAL_ROOT}")"
complete_device="$(stat -c '%d' -- "${COMPLETE_ROOT}")"
[[ -n "${partial_device}" && "${partial_device}" == "${complete_device}" ]] || {
  echo 'Partial and complete backup directories must use the same filesystem' >&2
  exit 1
}
[[ ! -e "${PARTIAL_BATCH}" && ! -e "${FINAL_BATCH}" ]] || {
  echo "Backup batch already exists: ${BATCH_ID}" >&2
  exit 1
}
mkdir -p "${PARTIAL_BATCH}/mysql" "${PARTIAL_BATCH}/minio"

bash "${SCRIPT_DIR}/backup-mysql-full.sh" "${PARTIAL_BATCH}/mysql"
BACKUP_DIR="${PARTIAL_BATCH}/minio" bash "${SCRIPT_DIR}/backup-minio-mirror.sh"

mapfile -t mysql_archives < <(find "${PARTIAL_BATCH}/mysql" -type f -name '*.sql.gz' -print)
[[ "${#mysql_archives[@]}" -eq 1 ]] || {
  echo 'Backup batch must contain exactly one MySQL archive' >&2
  exit 1
}
mapfile -t minio_objects < <(find "${PARTIAL_BATCH}/minio" -type f -print)
printf 'object_count=%s\n' "${#minio_objects[@]}" > "${PARTIAL_BATCH}/minio.inventory"
MYSQL_TOOL_VERSION="$(docker exec "${MYSQL_CONTAINER:-cgc-pms-mysql}" mysqldump --version | head -n 1 | tr -d '\r\n')"
MINIO_TOOL_VERSION="$(mc --version | head -n 1 | tr -d '\r\n')"
[[ -n "${MYSQL_TOOL_VERSION}" && -n "${MINIO_TOOL_VERSION}" ]] || {
  echo 'Backup tool versions could not be determined' >&2
  exit 1
}
cat > "${PARTIAL_BATCH}/batch.metadata" <<EOF
batch_id=${BATCH_ID}
source_database=${SOURCE_DATABASE}
source_bucket=${SOURCE_BUCKET}
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
mysql_tool_version=${MYSQL_TOOL_VERSION}
minio_tool_version=${MINIO_TOOL_VERSION}
result=pending
EOF

write_manifest() {
  (
    cd "${PARTIAL_BATCH}"
    { find mysql minio -type f -print0; printf 'minio.inventory\0batch.metadata\0'; } \
      | LC_ALL=C sort -z \
      | xargs -0 sha256sum > manifest.sha256
    sha256sum -c manifest.sha256 >/dev/null
  )
}
write_manifest
bash "${SCRIPT_DIR}/backup-verify.sh" --staged "${PARTIAL_BATCH}" >/dev/null
sed -i 's/^result=pending$/result=verified/' "${PARTIAL_BATCH}/batch.metadata"
grep -qx 'result=verified' "${PARTIAL_BATCH}/batch.metadata" || {
  echo 'Batch metadata result transition failed' >&2
  exit 1
}
write_manifest
printf '%s\n' "verified_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${PARTIAL_BATCH}/VERIFIED"
printf '%s\n' "completed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${PARTIAL_BATCH}/COMPLETE"

# Recheck publication boundary immediately before the atomic rename.
[[ ! -L "${PARTIAL_ROOT}" && ! -L "${COMPLETE_ROOT}" ]] || {
  echo 'Managed backup directory changed to a symbolic link before publication' >&2
  exit 1
}
partial_device="$(stat -c '%d' -- "${PARTIAL_BATCH}")"
complete_device="$(stat -c '%d' -- "${COMPLETE_ROOT}")"
[[ -n "${partial_device}" && "${partial_device}" == "${complete_device}" ]] || {
  echo 'Backup batch cannot be atomically published across filesystems' >&2
  exit 1
}
mv -- "${PARTIAL_BATCH}" "${FINAL_BATCH}"
trap - EXIT

# Retention sees only atomically published directories carrying COMPLETE.
mapfile -t complete_batches < <(
  find "${COMPLETE_ROOT}" -mindepth 1 -maxdepth 1 -type d -print \
    | LC_ALL=C sort -r
)
if [[ "${#complete_batches[@]}" -gt "${RETENTION_COUNT}" ]]; then
  for old_batch in "${complete_batches[@]:${RETENTION_COUNT}}"; do
    [[ "${old_batch}" == "${COMPLETE_ROOT}/"* && -f "${old_batch}/COMPLETE" ]] || continue
    rm -rf -- "${old_batch}"
  done
fi

echo "[$(date)] Backup batch published: ${FINAL_BATCH}"
printf '%s\n' "${FINAL_BATCH}"
