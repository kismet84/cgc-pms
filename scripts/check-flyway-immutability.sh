#!/usr/bin/env bash
# check-flyway-immutability.sh
# Blocks changes to already-committed Flyway migrations.
# Local mode checks staged changes. CI mode checks an explicit base...HEAD range.
# Usage: bash scripts/check-flyway-immutability.sh [--base <sha-or-ref>]

set -euo pipefail

# [BV] rather than V: the baseline B215__cgc_pms_baseline.sql is exactly the file whose
# checksum mismatch would break every existing database, and a V-only glob never saw it.
MIGRATION_PATHS=(
  "backend/src/main/resources/db/migration/[BV]*.sql"
  "backend/src/main/resources/db/migration-legacy/[BV]*.sql"
  "backend/src/main/resources/db/migration-h2/[BV]*.sql"
  "backend/src/main/resources/db/migration-h2-legacy/[BV]*.sql"
)

for path in "${MIGRATION_PATHS[@]}"; do
  dir="${path%/\[BV\]*.sql}"
  [ -d "$dir" ] || { echo "ERROR: Missing migration directory: $dir" >&2; exit 2; }
done

if [ "$#" -eq 0 ]; then
  DIFF_ARGS=(--cached)
elif [ "$#" -eq 2 ] && [ "$1" = "--base" ] && [ -n "$2" ]; then
  git rev-parse --verify "$2^{commit}" >/dev/null 2>&1 || {
    echo "ERROR: Unknown Flyway immutability base: $2" >&2
    exit 2
  }
  DIFF_ARGS=("$2...HEAD")
else
  echo "Usage: $0 [--base <sha-or-ref>]" >&2
  exit 2
fi

if ! MODIFIED_MIGRATIONS=$(git diff "${DIFF_ARGS[@]}" --name-only --diff-filter=MDR -- "${MIGRATION_PATHS[@]}"); then
  echo "ERROR: Unable to inspect Flyway migration changes." >&2
  exit 2
fi

if [ -n "$MODIFIED_MIGRATIONS" ]; then
  echo ""
  echo "============================================================"
  echo "ERROR: Already-committed Flyway migrations changed."
  echo "============================================================"
  echo "Modified migration files:"
  echo "$MODIFIED_MIGRATIONS" | while IFS= read -r file; do
    echo "  - $file"
  done
  echo "============================================================"
  echo "Already-applied migrations should NEVER be modified in-place."
  echo "Create a new V{next}__description.sql migration instead."
  echo "See: docs/standards/07-数据库与迁移规范.md"
  echo "============================================================"
  echo ""
  exit 1
fi

echo "Flyway immutability verified."
exit 0
