#!/usr/bin/env bash
set -euo pipefail

if [[ ! -d backend ]]; then
  echo "backend directory not found" >&2
  exit 1
fi

# Git Bash rewrites container paths unless Docker argument conversion is disabled.
export MSYS_NO_PATHCONV=1
docker_args=(--rm)
if [[ -n "${TRIVY_CACHE_DIR:-}" ]]; then
  mkdir -p "$TRIVY_CACHE_DIR"
  docker_args+=(-v "$TRIVY_CACHE_DIR:/root/.cache/trivy")
fi

docker run "${docker_args[@]}" \
  -v "$PWD:/workspace" \
  aquasec/trivy:0.65.0 \
  fs \
  --scanners vuln \
  --pkg-types library \
  --skip-dirs /workspace/backend/target \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  --format table \
  --timeout 10m \
  /workspace/backend
