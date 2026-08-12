[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
& (Join-Path $PSScriptRoot 'test-windows-backup-contract.ps1') -RepoRoot $repoRoot | Out-Null
$resetMarker = Join-Path $repoRoot '.codex-autopilot\ALLOW_TEST_DATA_RESET'
if (!(Test-Path -LiteralPath $resetMarker -PathType Leaf)) {
  throw 'Local test-data reset marker is required'
}

$bashPath = if ($IsWindows) { 'C:\Program Files\Git\bin\bash.exe' } else { (Get-Command bash).Source }
if (!(Test-Path -LiteralPath $bashPath -PathType Leaf)) { throw 'Bash is required' }

$suffix = "{0}-{1}" -f $PID, ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
$sourceContainer = "cgc-pms-m92-backup-source-$suffix"
$restoreContainer = "cgc-pms-m92-backup-restore-$suffix"
$minioContainer = "cgc-pms-m92-backup-minio-$suffix"
$batchId = "m92-restore-$suffix"
$sourceDatabase = 'cgc_pms_m92_source'
$sourceBucket = 'cgc-pms-m92-source'
$restoreBucket = 'cgc-pms-m92-restore'
$objectKey = 'evidence.txt'
$mysqlPassword = 'm92-local-restore-password'
$minioUser = 'm92localroot'
$minioPassword = 'm92-local-minio-restore-password'
$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$backupRoot = [IO.Path]::GetFullPath((Join-Path $tempBase "cgc-pms-m92-backup-$suffix"))
if (!$backupRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase) -or $backupRoot -eq $tempBase) {
  throw "Unsafe temporary backup root: $backupRoot"
}

function Invoke-Docker([string[]]$Arguments) {
  & docker @Arguments
  if ($LASTEXITCODE -ne 0) { throw "docker command failed: $($Arguments[0])" }
}

function Invoke-DockerText([string[]]$Arguments) {
  $output = & docker @Arguments
  if ($LASTEXITCODE -ne 0) { throw "docker command failed: $($Arguments[0])" }
  return ($output -join "`n").Trim()
}

function Wait-MySql([string]$Container) {
  foreach ($attempt in 1..60) {
    & docker exec -e "MYSQL_PWD=$mysqlPassword" $Container mysql --host=127.0.0.1 -uroot -Nse 'SELECT 1' *> $null
    if ($LASTEXITCODE -eq 0) { return }
    Start-Sleep -Seconds 1
  }
  throw "MySQL container did not become ready: $Container"
}

function Wait-Minio([string]$Container) {
  foreach ($attempt in 1..60) {
    & docker exec $Container mc alias set m92local http://127.0.0.1:9000 $minioUser $minioPassword --api S3v4 *> $null
    if ($LASTEXITCODE -eq 0) { return }
    Start-Sleep -Seconds 1
  }
  throw "MinIO container did not become ready: $Container"
}

$startedAt = Get-Date
New-Item -ItemType Directory -Path $backupRoot | Out-Null

try {
  Invoke-Docker @(
    'run', '-d', '--name', $sourceContainer,
    '-e', "MYSQL_ROOT_PASSWORD=$mysqlPassword",
    '-e', "MYSQL_DATABASE=$sourceDatabase",
    'mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b'
  ) | Out-Null
  Wait-MySql $sourceContainer
  Invoke-Docker @(
    'run', '-d', '--name', $minioContainer,
    '-e', "MINIO_ROOT_USER=$minioUser",
    '-e', "MINIO_ROOT_PASSWORD=$minioPassword",
    '--mount', "type=bind,source=$backupRoot,target=/backup",
    'minio/minio@sha256:9535594ad4122b7a78c6632788a989b96d9199b483d3bd71a5ceae73a922cdfa',
    'server', '/data'
  ) | Out-Null
  Wait-Minio $minioContainer
  Invoke-Docker @('exec', $minioContainer, 'mc', 'mb', "m92local/$sourceBucket")
  Invoke-Docker @(
    'exec', $minioContainer, 'sh', '-c',
    "printf '%s' 'm92-object-content' | mc pipe m92local/$sourceBucket/$objectKey"
  )
  $sourceObjectHash = Invoke-DockerText @(
    'exec', $minioContainer, 'sh', '-c',
    "mc cat m92local/$sourceBucket/$objectKey | sha256sum | cut -d' ' -f1"
  )
  Invoke-Docker @(
    'exec', '-e', "MYSQL_PWD=$mysqlPassword", $sourceContainer,
    'mysql', '--host=127.0.0.1', '-uroot', $sourceDatabase, '-e',
    "CREATE TABLE m92_restore_probe(id BIGINT PRIMARY KEY, payload VARCHAR(64) NOT NULL, bucket_name VARCHAR(128) NOT NULL, object_key VARCHAR(255) NOT NULL, object_sha256 CHAR(64) NOT NULL); INSERT INTO m92_restore_probe VALUES(920001,'m92-restore-ok','$sourceBucket','$objectKey','$sourceObjectHash');"
  )

  $env:M92_REPO_ROOT = $repoRoot.Replace('\', '/')
  $env:M92_BACKUP_ROOT = $backupRoot.Replace('\', '/')
  $env:M92_MINIO_CONTAINER = $minioContainer
  $env:MYSQL_CONTAINER = $sourceContainer
  $env:MYSQL_HOST = '127.0.0.1'
  $env:MYSQL_PORT = '3306'
  $env:MYSQL_USER = 'root'
  $env:MYSQL_PASSWORD = $mysqlPassword
  $env:MYSQL_DATABASE = $sourceDatabase
  $env:MINIO_ALIAS = 'm92local'
  $env:MINIO_BUCKET = $sourceBucket
  $env:MINIO_ACCESS_KEY = $minioUser
  $env:MINIO_SECRET_KEY = $minioPassword
  $env:MINIO_ENDPOINT = 'http://127.0.0.1:9000'
  $env:BATCH_ID = $batchId
  $env:BACKUP_RETENTION_COUNT = '2'

  $backupCommand = @'
set -euo pipefail
cd "$M92_REPO_ROOT"
if command -v cygpath >/dev/null 2>&1; then M92_BACKUP_ROOT="$(cygpath -u "$M92_BACKUP_ROOT")"; fi
export M92_BACKUP_ROOT
mc() {
  local rewritten=()
  local argument
  for argument in "$@"; do
    case "$argument" in
      "$M92_BACKUP_ROOT"*) rewritten+=("/backup${argument#"$M92_BACKUP_ROOT"}") ;;
      *) rewritten+=("$argument") ;;
    esac
  done
  MSYS_NO_PATHCONV=1 docker exec -i "$M92_MINIO_CONTAINER" mc "${rewritten[@]}"
}
export -f mc
bash scripts/backup-batch.sh "$M92_BACKUP_ROOT"
'@
  & $bashPath -lc $backupCommand
  if ($LASTEXITCODE -ne 0) { throw 'Atomic backup batch failed' }

  $batchPath = Join-Path $backupRoot "complete\$batchId"
  if (!(Test-Path -LiteralPath (Join-Path $batchPath 'COMPLETE') -PathType Leaf)) {
    throw 'Published batch is missing COMPLETE marker'
  }
  $minioInventory = Get-Content -LiteralPath (Join-Path $batchPath 'minio.inventory') -Raw
  if ($minioInventory.Trim() -ne 'object_count=1') { throw "Published MinIO inventory mismatch: $minioInventory" }
  $metadataPath = Join-Path $batchPath 'batch.metadata'
  $metadataText = Get-Content -LiteralPath $metadataPath -Raw
  $metadata = @{}
  foreach ($line in @($metadataText -split "`r?`n" | Where-Object { $_ })) {
    $parts = $line -split '=', 2
    if ($parts.Count -ne 2 -or $metadata.ContainsKey($parts[0])) { throw "Invalid batch metadata line: $line" }
    $metadata[$parts[0]] = $parts[1]
  }
  if ($metadata.Count -ne 7) { throw "Batch metadata field count mismatch: $($metadata.Count)" }
  if ($metadata.batch_id -ne $batchId) { throw "Batch metadata ID mismatch: $($metadata.batch_id)" }
  if ($metadata.source_database -ne $sourceDatabase) { throw "Batch metadata database mismatch: $($metadata.source_database)" }
  if ($metadata.source_bucket -ne $sourceBucket) { throw "Batch metadata bucket mismatch: $($metadata.source_bucket)" }
  if ($metadata.created_at -notmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$') { throw "Batch metadata UTC time is invalid: $($metadata.created_at)" }
  if (!$metadata.mysql_tool_version -or !$metadata.minio_tool_version) { throw 'Batch metadata tool versions are missing' }
  if ($metadata.result -ne 'verified') { throw "Batch metadata result mismatch: $($metadata.result)" }
  foreach ($secret in @($mysqlPassword, $minioUser, $minioPassword)) {
    if ($metadataText.Contains($secret, [StringComparison]::Ordinal)) { throw 'Batch metadata contains credentials' }
  }
  $manifestText = Get-Content -LiteralPath (Join-Path $batchPath 'manifest.sha256') -Raw
  $metadataHash = (Get-FileHash -LiteralPath $metadataPath -Algorithm SHA256).Hash.ToLowerInvariant()
  $escapedMetadataHash = [regex]::Escape($metadataHash)
  if ($manifestText -notmatch "(?m)^$escapedMetadataHash [ *]batch[.]metadata`r?$") { throw 'Batch metadata is not hash-covered by manifest' }
  $archives = @(Get-ChildItem -LiteralPath (Join-Path $batchPath 'mysql') -Filter '*.sql.gz' -File)
  if ($archives.Count -ne 1) { throw 'Published batch must contain one MySQL archive' }

  Invoke-Docker @(
    'run', '-d', '--name', $restoreContainer,
    '-e', "MYSQL_ROOT_PASSWORD=$mysqlPassword",
    'mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b'
  ) | Out-Null
  Wait-MySql $restoreContainer
  Invoke-Docker @(
    'exec', '-e', "MYSQL_PWD=$mysqlPassword", $restoreContainer,
    'mysql', '--host=127.0.0.1', '-uroot', '-e', "CREATE DATABASE ``$sourceDatabase``"
  )

  $env:M92_MYSQL_ARCHIVE = $archives[0].FullName.Replace('\', '/')
  $env:M92_RESTORE_CONTAINER = $restoreContainer
  $env:M92_MYSQL_PASSWORD = $mysqlPassword
  $restoreCommand = @'
set -euo pipefail
if command -v cygpath >/dev/null 2>&1; then M92_MYSQL_ARCHIVE="$(cygpath -u "$M92_MYSQL_ARCHIVE")"; fi
gzip -dc "$M92_MYSQL_ARCHIVE" | MSYS_NO_PATHCONV=1 docker exec -i -e "MYSQL_PWD=$M92_MYSQL_PASSWORD" "$M92_RESTORE_CONTAINER" mysql --host=127.0.0.1 -uroot "$MYSQL_DATABASE"
'@
  & $bashPath -lc $restoreCommand
  if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL restore failed' }
  $restoredRows = Invoke-DockerText @(
    'exec', '-e', "MYSQL_PWD=$mysqlPassword", $restoreContainer,
    'mysql', '--host=127.0.0.1', '-uroot', '-Nse',
    "SELECT COUNT(*) FROM $sourceDatabase.m92_restore_probe WHERE id=920001 AND payload='m92-restore-ok'"
  )
  if ($restoredRows -ne '1') { throw "Restored MySQL probe mismatch: $restoredRows" }
  $restoredReference = Invoke-DockerText @(
    'exec', '-e', "MYSQL_PWD=$mysqlPassword", $restoreContainer,
    'mysql', '--host=127.0.0.1', '-uroot', '-Nse',
    "SELECT bucket_name, object_key, object_sha256 FROM $sourceDatabase.m92_restore_probe WHERE id=920001"
  )
  $expectedReference = "$sourceBucket`t$objectKey`t$sourceObjectHash"
  if ($restoredReference -ne $expectedReference) { throw "Restored DB-to-object reference mismatch: $restoredReference" }

  Invoke-Docker @('exec', $minioContainer, 'mc', 'mb', "m92local/$restoreBucket")
  Invoke-Docker @(
    'exec', $minioContainer, 'mc', 'mirror', '--overwrite',
    "/backup/complete/$batchId/minio", "m92local/$restoreBucket"
  )
  $restoredObjectHash = Invoke-DockerText @(
    'exec', $minioContainer, 'sh', '-c',
    "mc cat m92local/$restoreBucket/$objectKey | sha256sum | cut -d' ' -f1"
  )
  if ($restoredObjectHash -ne $sourceObjectHash) { throw 'Restored MinIO object hash mismatch' }
  $restoredObjects = Invoke-DockerText @(
    'exec', $minioContainer, 'sh', '-c',
    "mc find m92local/$restoreBucket --print '{{key}}' | wc -l | tr -d '[:space:]'"
  )
  if ($restoredObjects -ne '1') { throw "Restored MinIO object count mismatch: $restoredObjects" }

  [ordered]@{
    ok = $true
    batchId = $batchId
    mysqlRows = [int]$restoredRows
    minioObjects = [int]$restoredObjects
    minioSha256 = $restoredObjectHash
    linkedReference = $true
    elapsedSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
  } | ConvertTo-Json
}
finally {
  foreach ($container in @($restoreContainer, $sourceContainer, $minioContainer)) {
    $exists = & docker ps -a --filter "name=^/$container$" --format '{{.Names}}'
    if ($exists -eq $container) { & docker rm -f $container *> $null }
  }
  $resolvedBackupRoot = [IO.Path]::GetFullPath($backupRoot)
  if (
    (Test-Path -LiteralPath $resolvedBackupRoot) -and
    $resolvedBackupRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase) -and
    $resolvedBackupRoot -ne $tempBase
  ) {
    Remove-Item -LiteralPath $resolvedBackupRoot -Recurse -Force
  }
}
