#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <mysql-container-id>" >&2
  exit 1
fi

container_id="$1"

require_env CI_MYSQL_DATABASE
require_env CI_MYSQL_USER
require_env CI_MYSQL_PASSWORD

grants="$(docker exec -e "MYSQL_PWD=${CI_MYSQL_PASSWORD}" "$container_id" \
  mysql -u"${CI_MYSQL_USER}" -Nse 'SHOW GRANTS FOR CURRENT_USER')"
printf '%s\n' "$grants"
normalized_grants="$(printf '%s\n' "$grants" | sed 's/\\//g')"
if ! printf '%s\n' "$normalized_grants" | grep -Fq "ON \`${CI_MYSQL_DATABASE}\`.*"; then
  echo "MySQL migration user is not scoped to ${CI_MYSQL_DATABASE}.*" >&2
  exit 1
fi
if printf '%s\n' "$normalized_grants" | grep -Ev '^GRANT USAGE ON \*\.\*' | grep -Eq ' ON \*\.\* '; then
  echo 'MySQL migration user has global privileges' >&2
  exit 1
fi
