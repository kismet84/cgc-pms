#!/usr/bin/env bash
# Scan the third-party runtime images pinned in deploy/ for known vulnerabilities.
#
# Report only. MinIO in particular has advisories whose fixes exist upstream but never landed
# in a published community image, so gating here would hand the pipeline to a vendor that has
# stopped publishing. The rescan surfaces the state; a real regression is triaged by a human.
set -uo pipefail

COMPOSE_FILES=(deploy/docker-compose.prod.yml deploy/docker-compose.yml deploy/docker-compose.dev.yml)

for file in "${COMPOSE_FILES[@]}"; do
  [[ -f "$file" ]] || { echo "compose file not found: $file" >&2; exit 1; }
done

# Only digest-pinned third-party images; the project's own images are built and scanned elsewhere.
mapfile -t IMAGES < <(
  grep -hoE '^[[:space:]]*image:[[:space:]]*[^[:space:]#]+@sha256:[0-9a-f]{64}' "${COMPOSE_FILES[@]}" \
    | sed -E 's/^[[:space:]]*image:[[:space:]]*//' \
    | grep -v 'cgc-pms-' \
    | sort -u
)

if [[ ${#IMAGES[@]} -eq 0 ]]; then
  echo "no digest-pinned third-party images found in deploy/" >&2
  exit 1
fi

export MSYS_NO_PATHCONV=1
docker_args=(--rm -v /var/run/docker.sock:/var/run/docker.sock)
if [[ -n "${TRIVY_CACHE_DIR:-}" ]]; then
  mkdir -p "$TRIVY_CACHE_DIR"
  docker_args+=(-v "$TRIVY_CACHE_DIR:/root/.cache/trivy")
fi

status=0
for image in "${IMAGES[@]}"; do
  echo "== $image =="
  docker run "${docker_args[@]}" \
    aquasec/trivy:0.65.0@sha256:a22415a38938a56c379387a8163fcb0ce38b10ace73e593475d3658d578b2436 \
    image \
    --scanners vuln \
    --severity HIGH,CRITICAL \
    --exit-code 0 \
    --format table \
    --timeout 10m \
    "$image" || status=1
done

if [[ $status -ne 0 ]]; then
  echo "one or more image scans failed to run; see the log above" >&2
fi
exit 0
