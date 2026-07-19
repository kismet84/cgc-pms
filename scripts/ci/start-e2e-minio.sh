#!/usr/bin/env bash
set -euo pipefail

docker run -d --name cgc-pms-e2e-minio \
  -p 9000:9000 \
  -e MINIO_ROOT_USER=cgcpmsci \
  -e MINIO_ROOT_PASSWORD=cgcpmsci123456 \
  minio/minio:latest server /data --console-address ":9001"
for _attempt in {1..30}; do
  if curl -fsS http://127.0.0.1:9000/minio/health/live; then
    exit 0
  fi
  sleep 2
done
docker logs cgc-pms-e2e-minio
exit 1
