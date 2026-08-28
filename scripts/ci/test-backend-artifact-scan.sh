#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
cd "$repo_root"
test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT
mkdir -p "$test_root/bin" "$test_root/output"
printf 'fixture' > "$test_root/backend.jar"
printf 'fixture' > "$test_root/empty.jar"

cat > "$test_root/bin/jar" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  tf)
    if [[ "${2:-}" != *empty.jar ]]; then echo 'BOOT-INF/lib/example.jar'; fi
    ;;
  xf)
    mkdir -p BOOT-INF/lib
    printf 'fixture' > BOOT-INF/lib/example.jar
    ;;
  *)
    exit 2
    ;;
esac
SH
chmod +x "$test_root/bin/jar"

cat > "$test_root/bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${!#}" != "/workspace/BOOT-INF/lib" ]]; then
  echo "unexpected scan target: ${!#}" >&2
  exit 2
fi
case "${FAKE_TRIVY_MODE:-success}" in
  success)
    printf '%s\n' '{"Results":[{"Class":"lang-pkgs","Type":"jar","Packages":[{"PkgName":"example","Version":"1.0.0"}]}]}'
    ;;
  empty)
    printf '%s\n' '{"Results":[{"Class":"lang-pkgs","Type":"jar","Packages":[]}]}'
    ;;
  warning)
    echo 'unable to detect version for dependency example' >&2
    printf '%s\n' '{"Results":[{"Class":"lang-pkgs","Type":"jar","Packages":[{"PkgName":"example","Version":"1.0.0"}]}]}'
    ;;
  failure)
    exit 1
    ;;
esac
SH
chmod +x "$test_root/bin/docker"

scan="$repo_root/scripts/ci/scan-backend-artifact.sh"
expect_failure() {
  if PATH="$test_root/bin:$PATH" SCAN_OUTPUT_DIR="$test_root/output" bash "$scan" "$@" >/dev/null 2>&1; then
    echo "artifact scan negative case unexpectedly passed: $*" >&2
    exit 1
  fi
}

expect_failure "$test_root/missing.jar"
expect_failure "$test_root/empty.jar"
EXPECTED_GIT_SHA=0000000000000000000000000000000000000000 expect_failure "$test_root/backend.jar"
FAKE_TRIVY_MODE=empty expect_failure "$test_root/backend.jar"
FAKE_TRIVY_MODE=warning expect_failure "$test_root/backend.jar"
FAKE_TRIVY_MODE=failure expect_failure "$test_root/backend.jar"

unset FAKE_TRIVY_MODE
PATH="$test_root/bin:$PATH" \
  EXPECTED_GIT_SHA=$(git -C "$repo_root" rev-parse HEAD) \
  SCAN_OUTPUT_DIR="$test_root/output" \
  bash "$scan" "$test_root/backend.jar" >/dev/null
grep -q '^backend_library_count=1$' "$test_root/output/backend-artifact-metadata.txt"
grep -q '^trivy_library_package_count=1$' "$test_root/output/backend-artifact-metadata.txt"
echo 'backend artifact scan contract passed'
