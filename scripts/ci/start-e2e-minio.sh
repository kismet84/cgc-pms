#!/usr/bin/env bash
set -euo pipefail

docker run -d --name cgc-pms-e2e-minio \
  -p 9000:9000 \
  -e MINIO_ROOT_USER=cgcpmsci \
  -e MINIO_ROOT_PASSWORD=cgcpmsci123456 \
  minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e server /data --console-address ":9001"
for _attempt in {1..30}; do
  if curl -fsS http://127.0.0.1:9000/minio/health/live; then
    docker run --rm --network container:cgc-pms-e2e-minio --entrypoint /bin/sh minio/mc@sha256:a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727 -c \
      'mc alias set e2e http://127.0.0.1:9000 cgcpmsci cgcpmsci123456 >/dev/null && mc mb --ignore-existing e2e/cgc-pms-e2e'
    exit 0
  fi
  sleep 2
done
docker logs cgc-pms-e2e-minio
exit 1
