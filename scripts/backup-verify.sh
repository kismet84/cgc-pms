#!/usr/bin/env bash
# Verify one staged or atomically published backup batch.
# Usage: ./backup-verify.sh [--staged] <batch_dir>

set -euo pipefail

STAGED=0
if [[ "${1:-}" == '--staged' ]]; then
  STAGED=1
  shift
fi

BATCH_DIR="${1:-}"
[[ -n "${BATCH_DIR}" && -d "${BATCH_DIR}" ]] || {
  echo 'Usage: backup-verify.sh [--staged] <batch_dir>' >&2
  exit 1
}
BATCH_DIR="$(cd "${BATCH_DIR}" && pwd -P)"

if [[ "${STAGED}" -eq 1 ]]; then
  [[ ! -e "${BATCH_DIR}/VERIFIED" && ! -e "${BATCH_DIR}/COMPLETE" ]] || {
    echo 'Staged batch must not claim VERIFIED or COMPLETE before validation' >&2
    exit 1
  }
else
  [[ -f "${BATCH_DIR}/VERIFIED" ]] || { echo 'Published batch is missing VERIFIED marker' >&2; exit 1; }
  [[ -f "${BATCH_DIR}/COMPLETE" ]] || { echo 'Published batch is missing COMPLETE marker' >&2; exit 1; }
fi
[[ -s "${BATCH_DIR}/manifest.sha256" ]] || { echo 'Batch manifest is missing or empty' >&2; exit 1; }
[[ -f "${BATCH_DIR}/minio.inventory" ]] || { echo 'Batch MinIO inventory is missing' >&2; exit 1; }
[[ -f "${BATCH_DIR}/batch.metadata" ]] || { echo 'Batch metadata is missing' >&2; exit 1; }
[[ -d "${BATCH_DIR}/mysql" && -d "${BATCH_DIR}/minio" ]] || {
  echo 'Batch payload directories are missing' >&2
  exit 1
}

manifest_entries=0
while read -r digest relative_path; do
  relative_path="${relative_path#\*}"
  [[ "${digest}" =~ ^[0-9a-fA-F]{64}$ ]] || { echo 'Manifest contains an invalid digest' >&2; exit 1; }
  case "${relative_path}" in
    mysql/*|minio/*|minio.inventory|batch.metadata) ;;
    *) echo "Manifest path escapes backup payload: ${relative_path}" >&2; exit 1 ;;
  esac
  case "/${relative_path}/" in
    */../*|*/./*) echo "Manifest path contains unsafe traversal: ${relative_path}" >&2; exit 1 ;;
  esac
  [[ -f "${BATCH_DIR}/${relative_path}" ]] || { echo "Manifest file is missing: ${relative_path}" >&2; exit 1; }
  manifest_entries=$((manifest_entries + 1))
done < "${BATCH_DIR}/manifest.sha256"

payload_entries="$(find "${BATCH_DIR}/mysql" "${BATCH_DIR}/minio" -type f -print | wc -l | tr -d '[:space:]')"
payload_entries=$((payload_entries + 2))
[[ "${manifest_entries}" -eq "${payload_entries}" ]] || {
  echo "Manifest coverage mismatch: manifest=${manifest_entries}, payload=${payload_entries}" >&2
  exit 1
}
(
  cd "${BATCH_DIR}"
  sha256sum -c manifest.sha256 >/dev/null
)

mapfile -t mysql_archives < <(find "${BATCH_DIR}/mysql" -type f -name '*.sql.gz' -print)
[[ "${#mysql_archives[@]}" -eq 1 ]] || { echo 'Batch must contain exactly one MySQL archive' >&2; exit 1; }
mapfile -t minio_objects < <(find "${BATCH_DIR}/minio" -type f -print)
inventory_count="$(sed -n 's/^object_count=\([0-9][0-9]*\)$/\1/p' "${BATCH_DIR}/minio.inventory")"
[[ -n "${inventory_count}" && "${inventory_count}" -eq "${#minio_objects[@]}" ]] || {
  echo "MinIO inventory mismatch: inventory=${inventory_count:-invalid}, payload=${#minio_objects[@]}" >&2
  exit 1
}

metadata_value() {
  local key="$1"
  sed -n "s/^${key}=//p" "${BATCH_DIR}/batch.metadata"
}
[[ "$(wc -l < "${BATCH_DIR}/batch.metadata" | tr -d '[:space:]')" -eq 7 ]] || {
  echo 'Batch metadata must contain exactly seven allowlisted fields' >&2
  exit 1
}
if grep -qEv '^(batch_id|source_database|source_bucket|created_at|mysql_tool_version|minio_tool_version|result)=.+' "${BATCH_DIR}/batch.metadata"; then
  echo 'Batch metadata contains an unknown or empty field' >&2
  exit 1
fi
for key in batch_id source_database source_bucket created_at mysql_tool_version minio_tool_version result; do
  value="$(metadata_value "${key}")"
  [[ -n "${value}" && "$(grep -c "^${key}=" "${BATCH_DIR}/batch.metadata")" -eq 1 ]] || {
    echo "Batch metadata field is missing or duplicated: ${key}" >&2
    exit 1
  }
done
[[ "$(metadata_value batch_id)" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || { echo 'Invalid metadata batch_id' >&2; exit 1; }
[[ "$(metadata_value batch_id)" == "$(basename "${BATCH_DIR}")" ]] || { echo 'Metadata batch_id does not match batch directory' >&2; exit 1; }
[[ "$(metadata_value source_database)" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'Invalid metadata source_database' >&2; exit 1; }
[[ "$(metadata_value source_bucket)" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || { echo 'Invalid metadata source_bucket' >&2; exit 1; }
[[ "$(metadata_value created_at)" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || { echo 'Invalid metadata created_at' >&2; exit 1; }
expected_result='verified'
[[ "${STAGED}" -eq 0 ]] || expected_result='pending'
[[ "$(metadata_value result)" == "${expected_result}" ]] || {
  echo "Batch metadata result must be ${expected_result}" >&2
  exit 1
}

VALIDATION_SQL="$(mktemp "${TMPDIR:-/tmp}/cgc-pms-backup-verify.XXXXXX.sql")"
trap 'rm -f -- "${VALIDATION_SQL}"' EXIT
gzip -t "${mysql_archives[0]}"
gzip -dc "${mysql_archives[0]}" > "${VALIDATION_SQL}"
grep -qE '(CREATE TABLE|INSERT INTO|DROP TABLE|CREATE DATABASE)' "${VALIDATION_SQL}" || {
  echo 'MySQL archive does not contain a valid SQL dump signature' >&2
  exit 1
}
rm -f -- "${VALIDATION_SQL}"
trap - EXIT

echo "[PASS] Backup batch verified: ${BATCH_DIR}"
