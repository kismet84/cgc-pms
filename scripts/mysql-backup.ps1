# mysql-backup.ps1
# MySQL full backup script for CGC-PMS (Windows PowerShell).
# Generates timestamped gzipped SQL dump files.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-backup.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-backup.ps1 -BackupDir "D:\backups\cgc-pms\mysql"
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-backup.ps1 -MysqlContainer "cgc-pms-mysql-dev"
# MysqlContainer uses that server's local socket/client; MysqlHost/MysqlPort are ignored.
#
# Exit 0 = success, Exit 1 = failure.
#
# Environment variables (from deploy\.env or direct):
#   MYSQL_ROOT_PASSWORD — MySQL root password (required)
#   MYSQL_DATABASE      — database name (default: cgc_pms)
#   MYSQL_HOST          — MySQL host (default: 127.0.0.1)
#   MYSQL_PORT          — MySQL port (default: 3307)
#
# Retention: keeps the latest 7 full backups by default.
# -RetentionDays remains a compatibility alias; its value is a backup count, not calendar days.

param(
    [string]$BackupDir = "",
    [Alias('RetentionDays')]
    [ValidateRange(1, 3650)]
    [int]$RetentionCount = 7,
    [string]$MysqlHost = "",
    [string]$MysqlPort = "",
    [string]$MysqlDatabase = "",
    [string]$MysqlContainer = "",
    [switch]$SkipRetention = $false
)

$ErrorActionPreference = 'Stop'

# -------------------------------------------------------------------
# Load environment
# -------------------------------------------------------------------
if ($MysqlContainer -and $MysqlContainer -cnotmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$') {
    throw 'MysqlContainer must be a valid explicit container name or ID'
}
if (!$MysqlContainer) {
    if (-not $MysqlHost) { $MysqlHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { '127.0.0.1' } }
    if (-not $MysqlPort) { $MysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3307' } }
}
if (-not $MysqlDatabase) { $MysqlDatabase = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'cgc_pms' } }
if ($MysqlDatabase -cnotmatch '^[A-Za-z0-9_]+$') {
    Write-Error "MysqlDatabase must contain only ASCII letters, digits, and underscore: $MysqlDatabase"
    exit 1
}
if (!$MysqlContainer -and ($MysqlPort -notmatch '^[0-9]+$' -or [int]$MysqlPort -lt 1 -or [int]$MysqlPort -gt 65535)) {
    Write-Error "MysqlPort must be an integer from 1 through 65535: $MysqlPort"
    exit 1
}
$MysqlPassword = $env:MYSQL_ROOT_PASSWORD
if (-not $MysqlPassword) {
    Write-Error 'MYSQL_ROOT_PASSWORD environment variable is not set. Set it or source deploy\.env first.'
    exit 1
}

if (-not $BackupDir) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $repoDrive = [IO.Path]::GetPathRoot([IO.Path]::GetFullPath($repoRoot))
    $BackupDir = Join-Path $repoDrive 'backups\cgc-pms\mysql'
}
$BackupDir = [IO.Path]::GetFullPath($BackupDir)
$pathSeparators = [char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$volumeRoot = [IO.Path]::GetPathRoot($BackupDir)
if ([string]::Equals($BackupDir.TrimEnd($pathSeparators), $volumeRoot.TrimEnd($pathSeparators), [StringComparison]::OrdinalIgnoreCase)) {
    Write-Error "BackupDir must not be a filesystem root: $BackupDir"
    exit 1
}
$backupDirPrefix = $BackupDir.TrimEnd($pathSeparators) + [IO.Path]::DirectorySeparatorChar

function Get-BackupChildPath([string]$Name) {
    $candidate = [IO.Path]::GetFullPath((Join-Path $BackupDir $Name))
    if (-not $candidate.StartsWith($backupDirPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Backup path escapes BackupDir: $candidate"
    }
    return $candidate
}

# Ensure backup directory exists
if (-not (Test-Path -LiteralPath $BackupDir)) {
    [IO.Directory]::CreateDirectory($BackupDir) | Out-Null
}
$backupDirItem = Get-Item -LiteralPath $BackupDir
if (-not $backupDirItem.PSIsContainer -or ($backupDirItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    Write-Error "BackupDir must be a real directory, not a file, symbolic link, or junction: $BackupDir"
    exit 1
}

# -------------------------------------------------------------------
# Timestamp
# -------------------------------------------------------------------
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backupFile = Get-BackupChildPath "${MysqlDatabase}_full_${timestamp}.sql.gz"
$partialBackupFile = "$backupFile.partial"

# -------------------------------------------------------------------
# Run mysqldump via Docker (always available; no local MySQL client needed)
# -------------------------------------------------------------------
$mysqlTarget = if ($MysqlContainer) { "container:$MysqlContainer/local-socket" } else { "${MysqlHost}:${MysqlPort}" }
Write-Host "Backing up ${MysqlDatabase}@$mysqlTarget -> $backupFile"

if ($MysqlContainer) {
    $mysqldumpArgs = @('exec', '-e', 'MYSQL_PWD', $MysqlContainer, 'mysqldump', '--protocol=SOCKET', '--host=localhost')
}
else {
    $mysqlImage = & (Join-Path $PSScriptRoot 'ci/build-mysql-runtime.ps1')
    $mysqldumpArgs = @('run', '--rm', '--network', 'host', '-e', 'MYSQL_PWD', $mysqlImage, 'mysqldump', '-h', $MysqlHost, '-P', $MysqlPort)
}
$mysqldumpArgs += @(
    '-u', 'root',
    '--single-transaction',
    '--routines',
    '--triggers',
    '--events',
    '--hex-blob',
    '--default-character-set=utf8mb4',
    $MysqlDatabase
)

$tempSqlFile = Get-BackupChildPath ".${MysqlDatabase}_${timestamp}.sql.partial"
$tempValidationFile = Get-BackupChildPath ".${MysqlDatabase}_${timestamp}.validation.sql.partial"
$tempErrorFile = Get-BackupChildPath ".${MysqlDatabase}_${timestamp}.err.partial"
$backupFailed = $false
$previousMysqlPwdExists = Test-Path -LiteralPath Env:MYSQL_PWD
$previousMysqlPwd = $env:MYSQL_PWD

try {
    if (Test-Path -LiteralPath $backupFile) { throw "Backup target already exists: $backupFile" }
    if (Test-Path -LiteralPath $partialBackupFile) { throw "Partial backup target already exists: $partialBackupFile" }

    # Docker copies MYSQL_PWD by name. Secret value remains out of process args.
    $env:MYSQL_PWD = $MysqlPassword
    $dumpProcess = Start-Process -FilePath 'docker' -ArgumentList $mysqldumpArgs `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $tempSqlFile `
        -RedirectStandardError $tempErrorFile
    if ($dumpProcess.ExitCode -ne 0) {
        $dumpError = Get-Content -LiteralPath $tempErrorFile -Raw -ErrorAction SilentlyContinue
        throw "mysqldump failed (exit $($dumpProcess.ExitCode)): $dumpError"
    }
    if (-not (Test-Path -LiteralPath $tempSqlFile -PathType Leaf) -or (Get-Item -LiteralPath $tempSqlFile -Force).Length -eq 0) {
        throw 'mysqldump produced an empty SQL file'
    }
    if (-not (Select-String -LiteralPath $tempSqlFile -Pattern '(CREATE TABLE|INSERT INTO|DROP TABLE|CREATE DATABASE)' -Quiet)) {
        throw 'mysqldump output does not contain a valid SQL dump signature'
    }

    # Keep the dump byte-for-byte intact. PowerShell text capture corrupts
    # non-ASCII SQL before it is recompressed.
    $inStream = $null
    $outStream = $null
    $gzipStream = $null
    try {
        $inStream = [System.IO.File]::OpenRead($tempSqlFile)
        $outStream = [System.IO.File]::Open($partialBackupFile, [System.IO.FileMode]::CreateNew)
        $gzipStream = New-Object System.IO.Compression.GZipStream($outStream, [System.IO.Compression.CompressionMode]::Compress)
        $inStream.CopyTo($gzipStream)
    }
    finally {
        if ($gzipStream) { $gzipStream.Dispose() }
        if ($outStream) { $outStream.Dispose() }
        if ($inStream) { $inStream.Dispose() }
    }

    if ((Get-Item -LiteralPath $partialBackupFile).Length -eq 0) { throw 'Compressed backup is empty' }

    # Read the partial gzip back before publication. Truncation or CRC failure
    # must fail while only the task-owned .partial files are visible.
    $compressedStream = $null
    $decompressionStream = $null
    $validationStream = $null
    try {
        $compressedStream = [System.IO.File]::OpenRead($partialBackupFile)
        $decompressionStream = New-Object System.IO.Compression.GZipStream($compressedStream, [System.IO.Compression.CompressionMode]::Decompress)
        $validationStream = [System.IO.File]::Open($tempValidationFile, [System.IO.FileMode]::CreateNew)
        $decompressionStream.CopyTo($validationStream)
    }
    finally {
        if ($validationStream) { $validationStream.Dispose() }
        if ($decompressionStream) { $decompressionStream.Dispose() }
        if ($compressedStream) { $compressedStream.Dispose() }
    }
    if (-not (Select-String -LiteralPath $tempValidationFile -Pattern '(CREATE TABLE|INSERT INTO|DROP TABLE|CREATE DATABASE)' -Quiet)) {
        throw 'Compressed backup validation did not recover a valid SQL dump'
    }

    # Source and destination share BackupDir, so Move-Item is the publication point.
    Move-Item -LiteralPath $partialBackupFile -Destination $backupFile

    $fileSize = (Get-Item $backupFile).Length
    Write-Host "Backup complete: $backupFile ($([math]::Round($fileSize/1KB, 1)) KB)"
}
catch {
    $backupFailed = $true
    Write-Error "Backup failed: $_" -ErrorAction Continue
}
finally {
    Remove-Item -LiteralPath $partialBackupFile, $tempSqlFile, $tempValidationFile, $tempErrorFile -Force -ErrorAction SilentlyContinue
    if ($previousMysqlPwdExists) { $env:MYSQL_PWD = $previousMysqlPwd }
    else { Remove-Item -LiteralPath Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}

if ($backupFailed) { exit 1 }

# -------------------------------------------------------------------
# Retention cleanup
# -------------------------------------------------------------------
if (-not $SkipRetention) {
    $oldFiles = Get-ChildItem -LiteralPath $BackupDir -Filter "${MysqlDatabase}_full_*.sql.gz" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip $RetentionCount

    foreach ($f in $oldFiles) {
        Write-Host "Removing expired backup: $($f.Name)"
        Remove-Item -LiteralPath $f.FullName -Force
    }

    if ($oldFiles.Count -gt 0) {
        Write-Host "Retention cleanup: removed $($oldFiles.Count) older backup(s), keeping latest $RetentionCount."
    }
}

exit 0
