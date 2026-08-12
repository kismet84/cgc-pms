#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BATCH_RUNNER="${REPO_ROOT}/scripts/backup-batch.sh"
BACKUP_VERIFY="${REPO_ROOT}/scripts/backup-verify.sh"
MYSQL_BACKUP="${REPO_ROOT}/scripts/backup-mysql-full.sh"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

[[ -f "${BATCH_RUNNER}" ]] || fail "batch backup entry is missing: scripts/backup-batch.sh"
[[ -f "${BACKUP_VERIFY}" ]] || fail "backup verifier is missing: scripts/backup-verify.sh"

REAL_GZIP="$(command -v gzip || true)"
[[ -n "${REAL_GZIP}" ]] || fail 'real gzip is required for the backup contract test'
REAL_STAT="$(command -v stat || true)"
[[ -n "${REAL_STAT}" ]] || fail 'stat is required for the backup contract test'

TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/cgc-pms-backup-contract.XXXXXX")"
FAKE_BIN="${TEST_ROOT}/fake-bin"
mkdir -p "${FAKE_BIN}"
trap 'rm -rf "${TEST_ROOT}"' EXIT

cat > "${FAKE_BIN}/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${FAKE_DOCKER_MODE:-ok}" == 'upstream-fail' ]]; then
  echo 'simulated mysqldump failure' >&2
  exit 42
fi
if [[ "${FAKE_DOCKER_MODE:-ok}" == 'invalid-sql' ]]; then
  printf '%s\n' 'this is not a SQL dump'
  exit 0
fi
if [[ " $* " == *' mysqldump --version '* ]]; then
  printf '%s\n' 'mysqldump  Ver 8.0.0 contract'
  exit 0
fi

cat <<'SQL'
-- CGC-PMS contract fixture
CREATE TABLE `contract_probe` (`id` bigint NOT NULL PRIMARY KEY);
INSERT INTO `contract_probe` VALUES (1);
SQL
FAKE_DOCKER

cat > "${FAKE_BIN}/mc" <<'FAKE_MC'
#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${FAKE_MC_ARGV_LOG:-}" ]]; then
  printf '%s\n' "$@" >> "${FAKE_MC_ARGV_LOG}"
fi
if [[ "${1:-}" == '--version' ]]; then
  printf '%s\n' 'mc version RELEASE.contract'
  exit 0
fi
if [[ "${1:-}" == '--config-dir' ]]; then shift 2; fi
command="${1:-}"
if [[ "${command}" == 'alias' && "${FAKE_MC_MODE:-ok}" == 'alias-fail' ]]; then
  echo 'simulated MinIO alias failure' >&2
  exit 43
fi
if [[ "${command}" == 'alias' && "${2:-}" == 'import' ]]; then
  cat >/dev/null
  exit 0
fi
if [[ "${command}" == 'mirror' && "${FAKE_MC_MODE:-ok}" == 'mirror-fail' ]]; then
  echo 'simulated MinIO mirror failure' >&2
  exit 44
fi
if [[ "${command}" == 'mirror' ]]; then
  destination="${!#}"
  mkdir -p "${destination}/contracts"
  if [[ "${FAKE_MC_MODE:-ok}" != 'empty' ]]; then
    head -c 2048 /dev/zero > "${destination}/contracts/object.bin"
  fi
fi
exit 0
FAKE_MC

cat > "${FAKE_BIN}/stat" <<'FAKE_STAT'
#!/usr/bin/env bash
set -euo pipefail

target="${!#}"
if [[ "${FAKE_STAT_MODE:-ok}" == 'cross-device' ]]; then
  case "${target}" in
    */.partial|*/.partial/*) printf '%s\n' 100 ;;
    */complete|*/complete/*) printf '%s\n' 200 ;;
    *) exec "${REAL_STAT:?REAL_STAT must be set}" "$@" ;;
  esac
else
  exec "${REAL_STAT:?REAL_STAT must be set}" "$@"
fi
FAKE_STAT

cat > "${FAKE_BIN}/gzip" <<'FAKE_GZIP'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${FAKE_GZIP_MODE:-ok}" == 'verifier-fail' ]]; then
  for argument in "$@"; do
    if [[ "${argument}" == '-t' || "${argument}" == '--test' ]]; then
      count=0
      [[ ! -f "${FAKE_GZIP_STATE_FILE:?}" ]] || count="$(cat "${FAKE_GZIP_STATE_FILE}")"
      count=$((count + 1))
      printf '%s\n' "${count}" > "${FAKE_GZIP_STATE_FILE}"
      if [[ "${count}" -eq 2 ]]; then
        archive="${!#}"
        batch_dir="$(dirname "$(dirname "${archive}")")"
        [[ ! -e "${batch_dir}/VERIFIED" ]] || printf '%s\n' present > "${FAKE_VERIFIED_OBSERVATION:?}"
        echo 'simulated staged verifier interruption' >&2
        exit 19
      fi
    fi
  done
  exec "${REAL_GZIP:?REAL_GZIP must be set}" "$@"
fi

for argument in "$@"; do
  case "${argument}" in
    -t|--test|-d|--decompress|-dc|-cd)
      exec "${REAL_GZIP:?REAL_GZIP must be set}" "$@"
      ;;
  esac
done

case "${FAKE_GZIP_MODE:-ok}" in
  fail)
    echo 'simulated gzip failure' >&2
    exit 17
    ;;
  disk-full)
    cat >/dev/null
    printf '\037\213disk-full'
    echo 'simulated disk full' >&2
    exit 28
    ;;
  truncate)
    if [[ "$#" -eq 0 || " $* " == *' -c '* ]]; then cat >/dev/null; fi
    printf '\037\213truncated'
    exit 0
    ;;
  verifier-fail)
    exec "${REAL_GZIP:?REAL_GZIP must be set}" "$@"
    ;;
  ok)
    exec "${REAL_GZIP:?REAL_GZIP must be set}" "$@"
    ;;
  *)
    echo "unknown FAKE_GZIP_MODE: ${FAKE_GZIP_MODE}" >&2
    exit 64
    ;;
esac
FAKE_GZIP

chmod +x "${FAKE_BIN}/docker" "${FAKE_BIN}/mc" "${FAKE_BIN}/gzip" "${FAKE_BIN}/stat"

LAST_ROOT=''
LAST_LOG=''
LAST_EXIT=0

run_batch() {
  local scenario="$1"
  local docker_mode="${2:-ok}"
  local gzip_mode="${3:-ok}"
  local mc_mode="${4:-ok}"
  local stat_mode="${5:-ok}"
  local root="${TEST_ROOT}/${scenario}"
  local log="${TEST_ROOT}/${scenario}.log"

  mkdir -p "${root}" "${root}/tmp"
  set +e
  env \
    PATH="${FAKE_BIN}:${PATH}" \
    REAL_GZIP="${REAL_GZIP}" \
    REAL_STAT="${REAL_STAT}" \
    FAKE_DOCKER_MODE="${docker_mode}" \
    FAKE_GZIP_MODE="${gzip_mode}" \
    FAKE_MC_MODE="${mc_mode}" \
    FAKE_MC_ARGV_LOG="${root}/mc.argv" \
    FAKE_STAT_MODE="${stat_mode}" \
    FAKE_GZIP_STATE_FILE="${root}/gzip.state" \
    FAKE_VERIFIED_OBSERVATION="${root}/verified-before-validation" \
    BACKUP_ROOT="${root}" \
    BACKUP_DIR="${root}" \
    BATCH_ID="contract-${scenario}" \
    TMPDIR="${root}/tmp" \
    LOG_FILE="${root}/backup.log" \
    MYSQL_PASSWORD='contract-password' \
    MINIO_ACCESS_KEY='contract-access' \
    MINIO_SECRET_KEY='contract-secret' \
    MINIO_ENDPOINT='http://contract-minio:9000' \
    MINIO_BUCKET='contract-bucket' \
    bash "${BATCH_RUNNER}" "${root}" >"${log}" 2>&1
  LAST_EXIT=$?
  set -e

  LAST_ROOT="${root}"
  LAST_LOG="${log}"
}

complete_markers() {
  find "$1" -type f -name COMPLETE -print
}

assert_no_partial_payload() {
  local root="$1"
  local leftovers
  leftovers="$(find "${root}" -type f \( -path '*/.partial/*' -o -path '*.partial/*' \) -print)"
  [[ -z "${leftovers}" ]] || fail "partial payload survived failure: ${leftovers}"

  leftovers="$(find "${root}" -type d -name '*.partial' ! -empty -print)"
  [[ -z "${leftovers}" ]] || fail "non-empty .partial directory survived failure: ${leftovers}"
}

assert_failure_is_atomic() {
  local scenario="$1"
  local docker_mode="${2:-ok}"
  local gzip_mode="${3:-ok}"
  local mc_mode="${4:-ok}"
  local stat_mode="${5:-ok}"
  local published

  run_batch "${scenario}" "${docker_mode}" "${gzip_mode}" "${mc_mode}" "${stat_mode}"
  [[ "${LAST_EXIT}" -ne 0 ]] || fail "${scenario} must fail"
  published="$(complete_markers "${LAST_ROOT}")"
  [[ -z "${published}" ]] || fail "${scenario} published a COMPLETE batch: ${published}"
  assert_no_partial_payload "${LAST_ROOT}"
}

run_batch 'success'
[[ "${LAST_EXIT}" -eq 0 ]] || fail "success batch failed; log=${LAST_LOG}"
mapfile -t success_markers < <(complete_markers "${LAST_ROOT}")
[[ "${#success_markers[@]}" -eq 1 ]] || fail "success must atomically publish exactly one COMPLETE batch"
SUCCESS_BATCH="$(dirname "${success_markers[0]}")"
assert_no_partial_payload "${LAST_ROOT}"

mapfile -t mysql_archives < <(find "${SUCCESS_BATCH}" -type f -name '*.sql.gz' -print)
[[ "${#mysql_archives[@]}" -eq 1 ]] || fail 'COMPLETE batch must contain exactly one MySQL .sql.gz archive'
mapfile -t minio_objects < <(find "${SUCCESS_BATCH}" -type f -path '*/minio/*' -print)
[[ "${#minio_objects[@]}" -gt 0 ]] || fail 'COMPLETE batch must contain mirrored MinIO objects'
[[ -s "${SUCCESS_BATCH}/manifest.sha256" ]] || fail 'COMPLETE batch must contain manifest.sha256'
[[ "$(cat "${SUCCESS_BATCH}/minio.inventory")" == 'object_count=1' ]] || fail 'MinIO inventory must record object count'
[[ -s "${SUCCESS_BATCH}/batch.metadata" ]] || fail 'COMPLETE batch must contain batch.metadata'
grep -qx 'batch_id=contract-success' "${SUCCESS_BATCH}/batch.metadata" || fail 'metadata must record batch ID'
grep -qx 'source_database=cgc_pms' "${SUCCESS_BATCH}/batch.metadata" || fail 'metadata must record source database'
grep -qx 'source_bucket=contract-bucket' "${SUCCESS_BATCH}/batch.metadata" || fail 'metadata must record source bucket'
grep -qE '^created_at=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' "${SUCCESS_BATCH}/batch.metadata" || fail 'metadata must record UTC creation time'
grep -q '^mysql_tool_version=mysqldump  Ver 8.0.0 contract$' "${SUCCESS_BATCH}/batch.metadata" || fail 'metadata must record MySQL tool version'
grep -q '^minio_tool_version=mc version RELEASE.contract$' "${SUCCESS_BATCH}/batch.metadata" || fail 'metadata must record MinIO tool version'
grep -qx 'result=verified' "${SUCCESS_BATCH}/batch.metadata" || fail 'metadata must record verified result'
grep -qE '[.]sql[.]gz$' "${SUCCESS_BATCH}/manifest.sha256" || fail 'manifest must cover MySQL archive'
grep -qE 'minio/' "${SUCCESS_BATCH}/manifest.sha256" || fail 'manifest must cover MinIO objects'
grep -qE 'batch[.]metadata$' "${SUCCESS_BATCH}/manifest.sha256" || fail 'manifest must cover batch metadata'
(
  cd "${SUCCESS_BATCH}"
  sha256sum -c manifest.sha256 >/dev/null
) || fail 'published manifest hashes must verify'
bash "${BACKUP_VERIFY}" "${SUCCESS_BATCH}" >/dev/null || fail 'backup-verify.sh must accept and verify one COMPLETE batch directory'
if grep -Fq 'contract-access' "${LAST_ROOT}/mc.argv" || grep -Fq 'contract-secret' "${LAST_ROOT}/mc.argv"; then
  fail 'MinIO credentials must not enter mc process arguments'
fi
if grep -Fq 'contract-access' "${SUCCESS_BATCH}/batch.metadata" || grep -Fq 'contract-secret' "${SUCCESS_BATCH}/batch.metadata"; then
  fail 'batch metadata must not contain credentials'
fi

assert_failure_is_atomic 'mysql-upstream-fail' 'upstream-fail'
assert_failure_is_atomic 'minio-alias-fail' 'ok' 'ok' 'alias-fail'
assert_failure_is_atomic 'minio-upstream-fail' 'ok' 'ok' 'mirror-fail'
assert_failure_is_atomic 'gzip-fail' 'ok' 'fail'
assert_failure_is_atomic 'disk-full' 'ok' 'disk-full'
assert_failure_is_atomic 'invalid-sql' 'invalid-sql'
assert_failure_is_atomic 'truncated-gzip' 'ok' 'truncate'
assert_failure_is_atomic 'verifier-fail' 'ok' 'verifier-fail'
grep -q 'simulated staged verifier interruption' "${LAST_LOG}" || fail 'verifier-fail did not reach staged verification'
[[ ! -e "${LAST_ROOT}/verified-before-validation" ]] || fail 'VERIFIED existed before staged validation succeeded'
assert_failure_is_atomic 'cross-device' 'ok' 'ok' 'ok' 'cross-device'

verify_line="$(grep -n 'backup-verify[.]sh.*--staged' "${BATCH_RUNNER}" | head -n 1 | cut -d: -f1)"
verified_line="$(grep -n 'PARTIAL_BATCH}/VERIFIED' "${BATCH_RUNNER}" | head -n 1 | cut -d: -f1)"
[[ -n "${verify_line}" && -n "${verified_line}" && "${verify_line}" -lt "${verified_line}" ]] || fail 'staged verifier must complete before VERIFIED is written'

run_batch 'empty-minio' 'ok' 'ok' 'empty'
[[ "${LAST_EXIT}" -eq 0 ]] || fail "empty MinIO bucket must produce a valid complete batch; log=${LAST_LOG}"
empty_batch="$(dirname "$(complete_markers "${LAST_ROOT}")")"
[[ "$(cat "${empty_batch}/minio.inventory")" == 'object_count=0' ]] || fail 'empty MinIO inventory must record zero objects'
bash "${BACKUP_VERIFY}" "${empty_batch}" >/dev/null || fail 'backup verifier must accept an explicit empty MinIO inventory'

timestamp_root="${TEST_ROOT}/unsafe-timestamp"
mkdir -p "${timestamp_root}"
set +e
env PATH="${FAKE_BIN}:${PATH}" MYSQL_PASSWORD='contract-password' BACKUP_TIMESTAMP='../escape' \
  bash "${MYSQL_BACKUP}" "${timestamp_root}" >/dev/null 2>&1
timestamp_exit=$?
set -e
[[ "${timestamp_exit}" -ne 0 ]] || fail 'unsafe BACKUP_TIMESTAMP must be rejected before backup execution'
[[ ! -e "${TEST_ROOT}/escape.sql.gz" ]] || fail 'unsafe timestamp escaped the backup directory'

if grep -q -- '--password=' "${MYSQL_BACKUP}"; then
  fail 'MySQL secret must not be passed in process arguments'
fi
grep -q 'docker exec -e MYSQL_PWD' "${MYSQL_BACKUP}" || fail 'MySQL password must use inherited environment forwarding'

if find "${TEST_ROOT}" -type d -name '.mc-config.*.partial' -print -quit | grep -q .; then
  fail 'task-owned mc configuration containing credentials survived execution'
fi

symlink_root="${TEST_ROOT}/symlink-boundary"
symlink_target="${TEST_ROOT}/symlink-target"
mkdir -p "${symlink_root}" "${symlink_target}"
if ln -s "${symlink_target}" "${symlink_root}/.partial" 2>/dev/null && [[ -L "${symlink_root}/.partial" ]]; then
  set +e
  env PATH="${FAKE_BIN}:${PATH}" REAL_GZIP="${REAL_GZIP}" REAL_STAT="${REAL_STAT}" \
    MYSQL_PASSWORD='contract-password' MINIO_ACCESS_KEY='contract-access' MINIO_SECRET_KEY='contract-secret' \
    BATCH_ID='contract-symlink' bash "${BATCH_RUNNER}" "${symlink_root}" >/dev/null 2>&1
  symlink_exit=$?
  set -e
  [[ "${symlink_exit}" -ne 0 ]] || fail 'symbolic-link managed root must be rejected'
  [[ -z "$(find "${symlink_target}" -mindepth 1 -print -quit)" ]] || fail 'symbolic-link target was modified'
fi

manifest_entry="$(awk 'NF >= 2 { path=$2; sub(/^\*/, "", path); print path; exit }' "${SUCCESS_BATCH}/manifest.sha256")"
[[ -n "${manifest_entry}" && -f "${SUCCESS_BATCH}/${manifest_entry}" ]] || fail 'manifest must use batch-relative paths'
printf 'tamper' >> "${SUCCESS_BATCH}/${manifest_entry}"
if bash "${BACKUP_VERIFY}" "${SUCCESS_BATCH}" >/dev/null 2>&1; then
  fail 'backup-verify.sh must reject hash-tampered COMPLETE batches'
fi

echo '[PASS] backup batch atomicity and integrity fault-injection contract'
