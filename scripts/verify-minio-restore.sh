#!/bin/bash
# Restore one MinIO mirror backup into a disposable isolated bucket and verify every object.

set -euo pipefail

BACKUP_DIR="${1:?usage: verify-minio-restore.sh BACKUP_DIR RESTORE_BUCKET METADATA_MANIFEST}"
RESTORE_BUCKET="${2:?usage: verify-minio-restore.sh BACKUP_DIR RESTORE_BUCKET METADATA_MANIFEST}"
METADATA_MANIFEST="${3:?usage: verify-minio-restore.sh BACKUP_DIR RESTORE_BUCKET METADATA_MANIFEST}"
MINIO_ALIAS="${MINIO_ALIAS:-cgc-minio}"

[[ -d "${BACKUP_DIR}" ]] || { echo "backup directory not found" >&2; exit 2; }
[[ -f "${METADATA_MANIFEST}" ]] || { echo "metadata manifest not found" >&2; exit 2; }
[[ "${RESTORE_BUCKET}" =~ ^[a-z0-9][a-z0-9-]*-restore-drill-[a-z0-9-]+$ ]] || {
    echo "restore bucket must be a dedicated *-restore-drill-* bucket" >&2
    exit 2
}

WORK_DIR="$(mktemp -d)"
CREATED=0
cleanup() {
    rm -rf -- "${WORK_DIR}"
    if [[ "${CREATED}" -eq 1 ]]; then
        mc rb --force "${MINIO_ALIAS}/${RESTORE_BUCKET}" >/dev/null
    fi
}
trap cleanup EXIT

manifest() {
    local root="$1"
    (
        cd "${root}"
        find . -type f -print0 | sort -z | xargs -0 -r sha256sum
    )
}

mc mb "${MINIO_ALIAS}/${RESTORE_BUCKET}" >/dev/null
CREATED=1
mc mirror --overwrite "${BACKUP_DIR}/" "${MINIO_ALIAS}/${RESTORE_BUCKET}" >/dev/null
mc mirror --overwrite "${MINIO_ALIAS}/${RESTORE_BUCKET}" "${WORK_DIR}/restored" >/dev/null

manifest "${BACKUP_DIR}" > "${WORK_DIR}/expected.sha256"
manifest "${WORK_DIR}/restored" > "${WORK_DIR}/actual.sha256"
diff -u "${WORK_DIR}/expected.sha256" "${WORK_DIR}/actual.sha256" >/dev/null

# TSV columns: file_id, tenant_id, bucket_name, storage_path, file_size,
# content_sha256, business_type, business_id. Every DB mapping is retained;
# multiple rows may reference one object, while unique object paths must cover the backup exactly.
: > "${WORK_DIR}/metadata.paths"
declare -A SEEN_FILE_IDS=()
SOURCE_BUCKET=""
MAPPED_COUNT=0
while IFS=$'\t' read -r file_id tenant_id bucket_name object_path expected_size expected_sha business_type business_id extra; do
    [[ -n "${business_id}" && -z "${extra:-}" ]] || { echo "invalid metadata manifest row" >&2; exit 3; }
    [[ "${file_id}" =~ ^[1-9][0-9]*$ && "${tenant_id}" =~ ^[0-9]+$ && "${business_id}" =~ ^[1-9][0-9]*$ ]] || {
        echo "invalid metadata identity" >&2; exit 3
    }
    [[ -z "${SEEN_FILE_IDS[${file_id}]:-}" ]] || { echo "duplicate file_id in metadata manifest" >&2; exit 3; }
    SEEN_FILE_IDS[${file_id}]=1
    [[ "${bucket_name}" =~ ^[a-z0-9][a-z0-9.-]+$ ]] || { echo "invalid source bucket" >&2; exit 3; }
    [[ -z "${SOURCE_BUCKET}" || "${SOURCE_BUCKET}" = "${bucket_name}" ]] || {
        echo "metadata manifest spans multiple source buckets" >&2; exit 3
    }
    SOURCE_BUCKET="${bucket_name}"
    [[ "${object_path}" != /* && "${object_path}" != *".."* ]] || { echo "unsafe metadata path" >&2; exit 3; }
    [[ "${expected_size}" =~ ^[0-9]+$ && "${expected_sha}" =~ ^[0-9a-fA-F]{64}$ ]] || {
        echo "invalid metadata size or SHA-256" >&2; exit 3
    }
    [[ "${business_type}" =~ ^[A-Z][A-Z0-9_]*$ ]] || { echo "invalid business_type" >&2; exit 3; }
    printf './%s\n' "${object_path}" >> "${WORK_DIR}/metadata.paths"
    for root in "${BACKUP_DIR}" "${WORK_DIR}/restored"; do
        file="${root}/${object_path}"
        [[ -f "${file}" ]] || { echo "metadata object missing: ${object_path}" >&2; exit 4; }
        actual_size=$(wc -c < "${file}" | tr -d '[:space:]')
        actual_sha=$(sha256sum "${file}" | awk '{print $1}')
        [[ "${actual_size}" = "${expected_size}" ]] || { echo "metadata size mismatch: ${object_path}" >&2; exit 4; }
        [[ "${actual_sha}" = "${expected_sha,,}" ]] || { echo "metadata hash mismatch: ${object_path}" >&2; exit 4; }
    done
    MAPPED_COUNT=$((MAPPED_COUNT + 1))
done < "${METADATA_MANIFEST}"

[[ "${MAPPED_COUNT}" -gt 0 ]] || { echo "metadata manifest is empty" >&2; exit 3; }

find "${BACKUP_DIR}" -type f -printf './%P\n' | sort > "${WORK_DIR}/backup.paths"
sort -u "${WORK_DIR}/metadata.paths" > "${WORK_DIR}/metadata.paths.sorted"
diff -u "${WORK_DIR}/backup.paths" "${WORK_DIR}/metadata.paths.sorted" >/dev/null || {
    echo "metadata manifest does not exactly cover backup objects" >&2; exit 4
}

FILE_COUNT=$(find "${BACKUP_DIR}" -type f | wc -l)
TOTAL_BYTES=$(find "${BACKUP_DIR}" -type f -exec wc -c {} \; | awk '{total += $1} END {print total + 0}')
echo "MinIO restore verified: ${FILE_COUNT} objects, ${TOTAL_BYTES} bytes, ${MAPPED_COUNT} metadata mappings, all SHA-256 hashes match"
