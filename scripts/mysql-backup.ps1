# mysql-backup.ps1
# MySQL full backup script for CGC-PMS (Windows PowerShell).
# Generates timestamped gzipped SQL dump files.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-backup.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-backup.ps1 -BackupDir "D:\backups\mysql"
#
# Exit 0 = success, Exit 1 = failure.
#
# Environment variables (from deploy\.env or direct):
#   MYSQL_ROOT_PASSWORD — MySQL root password (required)
#   MYSQL_DATABASE      — database name (default: cgc_pms)
#   MYSQL_HOST          — MySQL host (default: 127.0.0.1)
#   MYSQL_PORT          — MySQL port (default: 3307)
#
# Retention: keeps last 7 daily full backups by default (override with -RetentionDays).

param(
    [string]$BackupDir = "",
    [int]$RetentionDays = 7,
    [string]$MysqlHost = "",
    [string]$MysqlPort = "",
    [string]$MysqlDatabase = "",
    [switch]$SkipRetention = $false
)

$ErrorActionPreference = 'Stop'

# -------------------------------------------------------------------
# Load environment
# -------------------------------------------------------------------
if (-not $MysqlHost) { $MysqlHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { '127.0.0.1' } }
if (-not $MysqlPort) { $MysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3307' } }
if (-not $MysqlDatabase) { $MysqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'cgc_pms' } }
$MysqlPassword = $env:MYSQL_ROOT_PASSWORD
if (-not $MysqlPassword) {
    Write-Error 'MYSQL_ROOT_PASSWORD environment variable is not set. Set it or source deploy\.env first.'
    exit 1
}

if (-not $BackupDir) {
    $BackupDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'backups\mysql'
}

# Ensure backup directory exists
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

# -------------------------------------------------------------------
# Timestamp
# -------------------------------------------------------------------
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupFile = Join-Path $BackupDir "${MysqlDatabase}_full_${timestamp}.sql.gz"

# -------------------------------------------------------------------
# Run mysqldump via Docker (always available; no local MySQL client needed)
# -------------------------------------------------------------------
Write-Host "Backing up ${MysqlDatabase}@${MysqlHost}:${MysqlPort} → $backupFile"

$mysqldumpArgs = @(
    'run', '--rm',
    '--network', 'host',
    '-e', "MYSQL_PWD=$MysqlPassword",
    'mysql:8.0',
    'mysqldump',
    '-h', $MysqlHost,
    '-P', $MysqlPort,
    '-u', 'root',
    '--single-transaction',
    '--routines',
    '--triggers',
    '--events',
    '--hex-blob',
    '--default-character-set=utf8mb4',
    $MysqlDatabase
)

 $tempSqlFile = Join-Path $BackupDir ".${MysqlDatabase}_${timestamp}.sql.tmp"
 $tempErrorFile = Join-Path $BackupDir ".${MysqlDatabase}_${timestamp}.err.tmp"

try {
    $dumpProcess = Start-Process -FilePath 'docker' -ArgumentList $mysqldumpArgs `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $tempSqlFile `
        -RedirectStandardError $tempErrorFile
    if ($dumpProcess.ExitCode -ne 0) {
        $dumpError = Get-Content -LiteralPath $tempErrorFile -Raw -ErrorAction SilentlyContinue
        throw "mysqldump failed (exit $($dumpProcess.ExitCode)): $dumpError"
    }

    # Keep the dump byte-for-byte intact. PowerShell text capture corrupts
    # non-ASCII SQL before it is recompressed.
    $inStream = [System.IO.File]::OpenRead($tempSqlFile)
    $outStream = [System.IO.File]::Open($backupFile, [System.IO.FileMode]::Create)
    $gzipStream = New-Object System.IO.Compression.GZipStream($outStream, [System.IO.Compression.CompressionMode]::Compress)
    $inStream.CopyTo($gzipStream)
    $gzipStream.Close()
    $outStream.Close()
    $inStream.Close()

    $fileSize = (Get-Item $backupFile).Length
    Write-Host "Backup complete: $backupFile ($([math]::Round($fileSize/1KB, 1)) KB)"
}
catch {
    Write-Error "Backup failed: $_"
    exit 1
}
finally {
    Remove-Item -LiteralPath $tempSqlFile, $tempErrorFile -Force -ErrorAction SilentlyContinue
}

# -------------------------------------------------------------------
# Retention cleanup
# -------------------------------------------------------------------
if (-not $SkipRetention) {
    $oldFiles = Get-ChildItem -Path $BackupDir -Filter "${MysqlDatabase}_full_*.sql.gz" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $RetentionDays

    foreach ($f in $oldFiles) {
        Write-Host "Removing expired backup: $($f.Name)"
        Remove-Item $f.FullName -Force
    }

    if ($oldFiles.Count -gt 0) {
        Write-Host "Retention cleanup: removed $($oldFiles.Count) expired backup(s), keeping last $RetentionDays."
    }
}

exit 0
