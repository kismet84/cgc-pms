#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: $name" >&2
    exit 1
  fi
}

require_env CI_MYSQL_DATABASE
require_env CI_MYSQL_USER
require_env CI_MYSQL_PASSWORD
require_env TEST_JWT_SECRET

if [[ ! -d backend ]]; then
  echo "backend directory not found" >&2
  exit 1
fi

bootstrap_password="Aa9!$(openssl rand -hex 16)"
echo "::add-mask::$bootstrap_password"
export CGCPMS_BOOTSTRAP_ENABLED=true
export CGCPMS_BOOTSTRAP_ADMIN_USERNAME=admin
export CGCPMS_BOOTSTRAP_ADMIN_PASSWORD="$bootstrap_password"
cd backend
nohup ./mvnw -C spring-boot:run -Dspring-boot.run.profiles=test,dev \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3306/${CI_MYSQL_DATABASE} --spring.datasource.username=${CI_MYSQL_USER} --spring.datasource.password=${CI_MYSQL_PASSWORD} --spring.redis.host=localhost --spring.redis.port=6379 --jwt.secret=${TEST_JWT_SECRET} --cors.allowed-origins=http://127.0.0.1:4173,http://localhost:4173 --minio.endpoint=http://localhost:9000 --minio.access-key=cgcpmsci --minio.secret-key=cgcpmsci123456 --minio.bucket=cgc-pms-e2e --auth.dev-login.enabled=true --auth.dev-login.default-username=admin" \
  > ../backend.log 2>&1 &
backend_pid=$!
cd ..
for _attempt in {1..90}; do
  if curl -fsS http://127.0.0.1:8080/api/actuator/health | grep -q '"status":"UP"'; then
    exit 0
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    cat backend.log
    exit 1
  fi
  sleep 2
done
cat backend.log
exit 1
