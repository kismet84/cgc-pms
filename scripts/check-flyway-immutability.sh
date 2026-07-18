#!/usr/bin/env bash
# check-flyway-immutability.sh
# Pre-commit hook: warns if already-committed Flyway migration files are being modified.
# Exit code 0 (warning only) — does not block commits.
#
# Usage: Add to .git/hooks/pre-commit or call from CI:
#   bash scripts/check-flyway-immutability.sh

set -euo pipefail

MIGRATION_DIR="backend/src/main/resources/db/migration"
LEGACY_DIR="backend/src/main/resources/db/migration-legacy"
WARNING="WARNING: Modifying already-applied Flyway migrations. Use new V90+ migrations instead."

# Only check if the migration directory exists
if [ ! -d "$MIGRATION_DIR" ]; then
  exit 0
fi

# Find V*.sql files that are both:
#   a) staged (git diff --cached) AND
#   b) already exist in HEAD (not new files)
MODIFIED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=M -- \
  "$MIGRATION_DIR"/V*.sql "$LEGACY_DIR"/V*.sql 2>/dev/null || true)

if [ -n "$MODIFIED_MIGRATIONS" ]; then
  echo ""
  echo "============================================================"
  echo "$WARNING"
  echo "============================================================"
  echo "Modified migration files:"
  echo "$MODIFIED_MIGRATIONS" | while IFS= read -r file; do
    echo "  - $file"
  done
  echo "============================================================"
  echo "Already-applied migrations should NEVER be modified in-place."
  echo "Instead: create a new V{next}__description.sql migration."
  echo "See: docs/standards/07-数据库与迁移规范.md"
  echo "============================================================"
  echo ""
fi

exit 0
