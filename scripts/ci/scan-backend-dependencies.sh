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

trivy() {
  docker run "${docker_args[@]}" \
    -v "$PWD:/workspace" \
    aquasec/trivy:0.65.0@sha256:a22415a38938a56c379387a8163fcb0ce38b10ace73e593475d3658d578b2436 \
    fs \
    --scanners vuln \
    --pkg-types library \
    --skip-dirs /workspace/backend/target \
    --format table \
    --timeout 10m \
    "$@" \
    /workspace/backend
}

# Visibility-only pass. MEDIUM findings often have no fixed release yet, so gating on them would
# hand the whole pipeline to upstream release timing; they still have to be visible to be triaged.
echo "== MEDIUM advisories (report only, does not gate) =="
trivy --severity MEDIUM --exit-code 0 || echo "MEDIUM report pass failed; continuing to the gate"

echo "== HIGH/CRITICAL advisories (gate) =="
trivy --severity HIGH,CRITICAL --exit-code 1
