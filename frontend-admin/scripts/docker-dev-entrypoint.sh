#!/bin/sh
set -eu

cd /app

corepack enable
corepack prepare pnpm@11.0.9 --activate
pnpm config set registry https://registry.npmmirror.com

STAMP_FILE="/app/node_modules/.pnpm-install.stamp"
LOCK_HASH_FILE="/tmp/pnpm-install.hash"

if [ -f pnpm-lock.yaml ]; then
  sha256sum package.json pnpm-lock.yaml | sha256sum | awk '{print $1}' > "$LOCK_HASH_FILE"
else
  echo "pnpm-lock.yaml not found" >&2
  exit 1
fi

NEED_INSTALL=1
if [ -f "$STAMP_FILE" ]; then
  if cmp -s "$LOCK_HASH_FILE" "$STAMP_FILE"; then
    NEED_INSTALL=0
  fi
fi

if [ "$NEED_INSTALL" -eq 1 ]; then
  pnpm install --ignore-scripts --frozen-lockfile
  cp "$LOCK_HASH_FILE" "$STAMP_FILE"
fi

exec pnpm dev --host 0.0.0.0 --port 5173
