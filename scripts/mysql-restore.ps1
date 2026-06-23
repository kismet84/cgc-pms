# mysql-restore.ps1
# MySQL restore script for CGC-PMS (Windows PowerShell).
# Restores a gzipped or plain SQL dump to a target database.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-restore.ps1 -BackupFile "D:\backups\mysql\cgc_pms_full_20260624_020000.sql.gz"
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-restore.ps1 -BackupFile "backup.sql" -TargetDatabase "cgc_pms_restore_test"
#
# SAFETY: Default target is cgc_pms_restore_test (NOT cgc_pms).
#         To restore to production, explicitly set -TargetDatabase "cgc_pms".
#
# Exit 0 = success, Exit 1 = failure.
#
# Environment variables:
#   MYSQL_ROOT_PASSWORD — MySQL root password (required)
#   MYSQL_HOST          — MySQL host (default: 127.0.0.1)
#   MYSQL_PORT          — MySQL port (default: 3307)

param(
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$TargetDatabase = 'cgc_pms_restore_test',
    [string]$MysqlHost = "",
    [string]$MysqlPort = ""
)

$ErrorActionPreference = 'Stop'

# -------------------------------------------------------------------
# Load environment
# -------------------------------------------------------------------
if (-not $MysqlHost) { $MysqlHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { '127.0.0.1' } }
if (-not $MysqlPort) { $MysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3307' } }
$MysqlPassword = $env:MYSQL_ROOT_PASSWORD
if (-not $MysqlPassword) {
    Write-Error 'MYSQL_ROOT_PASSWORD environment variable is not set.'
    exit 1
}

if (-not (Test-Path $BackupFile)) {
    Write-Error "Backup file not found: $BackupFile"
    exit 1
}

# -------------------------------------------------------------------
# Safety confirmation for production restore
# -------------------------------------------------------------------
if ($TargetDatabase -ne 'cgc_pms_restore_test') {
    $productionDbs = @('cgc_pms', 'cgc_pms_prod', 'production')
    if ($TargetDatabase -in $productionDbs) {
        Write-Host ''
        Write-Host '==========================================================' -ForegroundColor Yellow
        Write-Host "  WARNING: Target database is '$TargetDatabase'" -ForegroundColor Yellow
        Write-Host '  This is a PRODUCTION restore.' -ForegroundColor Yellow
        Write-Host "  Source: $BackupFile" -ForegroundColor Yellow
        Write-Host '==========================================================' -ForegroundColor Yellow
        Write-Host ''
        $confirm = Read-Host "Type '$TargetDatabase' to confirm restore"
        if ($confirm -ne $TargetDatabase) {
            Write-Host 'Restore cancelled.'
            exit 0
        }
    }
}

# -------------------------------------------------------------------
# Create target database if not exists
# -------------------------------------------------------------------
Write-Host "Ensuring target database '$TargetDatabase' exists..."
$createDbSql = "CREATE DATABASE IF NOT EXISTS \`$TargetDatabase\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
$createArgs = @(
    'run', '--rm',
    '--network', 'host',
    '-e', "MYSQL_PWD=$MysqlPassword",
    'mysql:8.0',
    'mysql',
    '-h', $MysqlHost,
    '-P', $MysqlPort,
    '-u', 'root',
    '-e', $createDbSql
)

& docker @createArgs 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create target database '$TargetDatabase'"
    exit 1
}

# -------------------------------------------------------------------
# Restore
# -------------------------------------------------------------------
Write-Host "Restoring $BackupFile → ${TargetDatabase}@${MysqlHost}:${MysqlPort}..."

$extension = [System.IO.Path]::GetExtension($BackupFile).ToLower()

if ($extension -eq '.gz') {
    # Decompress and pipe into mysql
    $inStream = [System.IO.File]::OpenRead($BackupFile)
    $gzipStream = New-Object System.IO.Compression.GZipStream($inStream, [System.IO.Compression.CompressionMode]::Decompress)
    $reader = New-Object System.IO.StreamReader($gzipStream, [System.Text.Encoding]::UTF8)
    $sql = $reader.ReadToEnd()
    $reader.Close()
    $gzipStream.Close()
    $inStream.Close()

    # Pipe SQL via stdin to Docker mysql
    $sql | & docker run --rm -i --network host -e "MYSQL_PWD=$MysqlPassword" mysql:8.0 mysql -h $MysqlHost -P $MysqlPort -u root $TargetDatabase 2>&1
}
else {
    Get-Content $BackupFile -Raw |
        & docker run --rm -i --network host -e "MYSQL_PWD=$MysqlPassword" mysql:8.0 mysql -h $MysqlHost -P $MysqlPort -u root $TargetDatabase 2>&1
}

if ($LASTEXITCODE -ne 0) {
    Write-Error 'Restore failed.'
    exit 1
}

Write-Host 'Restore complete.'

# -------------------------------------------------------------------
# Minimal health check
# -------------------------------------------------------------------
Write-Host 'Running minimal health check...'
$checkSql = 'SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = \"' + $TargetDatabase + '\";'
$checkArgs = @(
    'run', '--rm',
    '--network', 'host',
    '-e', "MYSQL_PWD=$MysqlPassword",
    'mysql:8.0',
    'mysql',
    '-h', $MysqlHost,
    '-P', $MysqlPort,
    '-u', 'root',
    '-N',
    '-e', $checkSql
)

$tableCount = & docker @checkArgs 2>&1
if ($LASTEXITCODE -eq 0 -and $tableCount -match '\d+' -and [int]$tableCount -gt 0) {
    Write-Host "Health check PASS — $tableCount table(s) found in '$TargetDatabase'."
}
else {
    Write-Host "Health check WARNING — could not verify table count." -ForegroundColor Yellow
}

exit 0
