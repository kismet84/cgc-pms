#!/usr/bin/env bash
set -euo pipefail

if [[ ! -d frontend-admin ]]; then
  echo "frontend-admin directory not found" >&2
  exit 1
fi

cd frontend-admin
mkdir -p .ci-artifacts
if [[ ! -d node_modules ]]; then
  pnpm install --frozen-lockfile
fi
set -o pipefail
pnpm lint:check 2>&1 | tee .ci-artifacts/lint-check.txt
