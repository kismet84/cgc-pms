#!/usr/bin/env bash
set -euo pipefail

artifact=${1:-}
if [[ -z "$artifact" || ! -f "$artifact" ]]; then
  echo "backend artifact not found: ${artifact:-<missing>}" >&2
  exit 1
fi

if [[ -n "${EXPECTED_GIT_SHA:-}" ]]; then
  actual_git_sha=$(git rev-parse HEAD)
  if [[ "$actual_git_sha" != "$EXPECTED_GIT_SHA" ]]; then
    echo "checked-out HEAD does not match expected SHA: actual=$actual_git_sha expected=$EXPECTED_GIT_SHA" >&2
    exit 1
  fi
fi

backend_library_count=$(jar tf "$artifact" | awk '/^BOOT-INF\/lib\/[^/]+\.jar$/ { count++ } END { print count + 0 }')
if [[ "$backend_library_count" -le 0 ]]; then
  echo "backend artifact contains no BOOT-INF/lib libraries" >&2
  exit 1
fi

scan_output_dir=${SCAN_OUTPUT_DIR:-artifacts}
mkdir -p "$scan_output_dir"
scan_json="$scan_output_dir/backend-artifact-trivy.json"
scan_diagnostics="$scan_output_dir/backend-artifact-trivy.stderr.log"
scan_metadata="$scan_output_dir/backend-artifact-metadata.txt"
artifact_sha256=$(sha256sum "$artifact" | awk '{ print $1 }')
artifact_dir=$(cd "$(dirname "$artifact")" && pwd -P)
artifact_name=$(basename "$artifact")

export MSYS_NO_PATHCONV=1
docker_args=(--rm)
if [[ -n "${TRIVY_CACHE_DIR:-}" ]]; then
  mkdir -p "$TRIVY_CACHE_DIR"
  trivy_cache_dir=$(cd "$TRIVY_CACHE_DIR" && pwd -P)
  docker_args+=(-v "$trivy_cache_dir:/root/.cache/trivy")
fi

if ! docker run "${docker_args[@]}" \
  -v "$artifact_dir:/workspace:ro" \
  aquasec/trivy:0.65.0@sha256:a22415a38938a56c379387a8163fcb0ce38b10ace73e593475d3658d578b2436 \
  rootfs \
  --scanners vuln \
  --pkg-types library \
  --list-all-pkgs \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  --format json \
  --timeout 10m \
  "/workspace/$artifact_name" > "$scan_json" 2> "$scan_diagnostics"; then
  cat "$scan_diagnostics" >&2
  echo "Trivy backend artifact scan failed" >&2
  exit 1
fi

cat "$scan_diagnostics" >&2
if grep -Eiq '(failed to (analy[sz]e|detect)|unable to (analy[sz]e|detect)|unknown version|version (is )?unresolved)' \
  "$scan_diagnostics" "$scan_json"; then
  echo "Trivy reported an unresolved backend dependency or version" >&2
  exit 1
fi

unset MSYS_NO_PATHCONV
python_command=python3
if ! "$python_command" --version >/dev/null 2>&1; then
  python_command=python
fi
if ! "$python_command" --version >/dev/null 2>&1; then
  echo "Python 3 is required to validate Trivy JSON" >&2
  exit 1
fi
trivy_library_package_count=$("$python_command" - "$scan_json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as stream:
    report = json.load(stream)
count = sum(
    1
    for result in report.get("Results") or []
    for package in result.get("Packages") or []
    if package.get("Name") and package.get("Version")
)
print(count)
PY
)
if [[ "$trivy_library_package_count" -le 0 ]]; then
  echo "Trivy did not identify any versioned library packages in backend artifact" >&2
  exit 1
fi

{
  echo "artifact=$artifact"
  echo "artifact_sha256=$artifact_sha256"
  echo "backend_library_count=$backend_library_count"
  echo "trivy_library_package_count=$trivy_library_package_count"
  echo "git_sha=$(git rev-parse HEAD)"
} | tee "$scan_metadata"
