# Local Backup Batch Contract

Repository provides local backup scripts; it does not claim external or production backup storage exists.

Run scheduled/full backup through one entry:

```bash
MYSQL_PASSWORD=... MINIO_ACCESS_KEY=... MINIO_SECRET_KEY=... \
  bash scripts/backup-scheduler.sh all
```

`BACKUP_ROOT` defaults to `/opt/cgc-pms/backups`. It must be one filesystem so directory rename remains atomic:

```text
BACKUP_ROOT/
  .partial/                 # writer-only staging; removed on every failure
  complete/
    <batch-id>/
      mysql/*.sql.gz
      minio/**
      minio.inventory        # hashed object_count, including valid zero-object buckets
      batch.metadata         # hashed batch/source/time/tool-version/result provenance; no secrets
      manifest.sha256
      VERIFIED
      COMPLETE
```

Consumers and restore jobs may use only directories under `complete/` carrying `COMPLETE`. Before publication, runner verifies dump process exit, gzip integrity, SQL signature, MinIO inventory/payload, metadata, manifest coverage, and every SHA-256 hash; only then does it write `VERIFIED` followed by `COMPLETE`. `batch.metadata` records batch ID, source database/bucket, UTC creation time, MySQL/MinIO tool versions, and verified result without credentials. An empty source bucket is represented by the hashed `object_count=0` inventory. Runner rejects symbolic-link or cross-filesystem publication roots, then renames staged directory on the same filesystem. Failed MySQL, MinIO, compression, storage, SQL, or hash checks remove only the task-owned partial directory and never change prior complete batches.

`BACKUP_RETENTION_COUNT` defaults to `7`. Retention runs only after successful publication and deletes only older directories under `complete/` carrying `COMPLETE`; partial and unrelated paths are never retention targets.

Manual component diagnostics remain available through scheduler targets `mysql` and `minio`. They are not complete batches and are never restoration inputs. Verify a published batch explicitly:

```bash
bash scripts/backup-verify.sh /opt/cgc-pms/backups/complete/<batch-id>
```
