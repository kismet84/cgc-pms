# mysql-restore.ps1
# MySQL restore script for CGC-PMS (Windows PowerShell).
# Restores a gzipped or plain SQL dump to a target database.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-restore.ps1 -BackupFile "D:\backups\cgc-pms\mysql\cgc_pms_full_20260624_020000.sql.gz"
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-restore.ps1 -BackupFile "backup.sql" -TargetDatabase "cgc_pms_restore_test"
#   powershell -ExecutionPolicy Bypass -File scripts\mysql-restore.ps1 -BackupFile "backup.sql" -MysqlContainer "cgc-pms-mysql-dev"
# MysqlContainer uses that server's local socket/client; MysqlHost/MysqlPort are ignored.
#
# SAFETY: Default target is cgc_pms_restore_test (NOT cgc_pms).
#         Protected database names require an additional typed confirmation.
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
    [string]$MysqlPort = "",
    [string]$MysqlContainer = ""
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
$MysqlPassword = $env:MYSQL_ROOT_PASSWORD
if (-not $MysqlPassword) {
    Write-Error 'MYSQL_ROOT_PASSWORD environment variable is not set.'
    exit 1
}

if ($TargetDatabase -cnotmatch '^[A-Za-z0-9_]+$') {
    Write-Error "Invalid target database name: '$TargetDatabase'"
    exit 1
}

if (!$MysqlContainer -and ($MysqlPort -notmatch '^[0-9]+$' -or [int]$MysqlPort -lt 1 -or [int]$MysqlPort -gt 65535)) {
    Write-Error "MysqlPort must be an integer from 1 through 65535: $MysqlPort"
    exit 1
}

if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) {
    Write-Error "Backup file not found: $BackupFile"
    exit 1
}

# -------------------------------------------------------------------
# Safety confirmation for protected database names (not environment detection)
# -------------------------------------------------------------------
if ($TargetDatabase -ne 'cgc_pms_restore_test') {
    $protectedDbs = @('cgc_pms', 'cgc_pms_prod', 'production')
    if ($TargetDatabase -in $protectedDbs) {
        Write-Host ''
        Write-Host '==========================================================' -ForegroundColor Yellow
        Write-Host "  WARNING: Target database is '$TargetDatabase'" -ForegroundColor Yellow
        Write-Host '  Existing target data may be overwritten by this restore.' -ForegroundColor Yellow
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
# Restore with an immutable client and a process-environment-only password.
# -------------------------------------------------------------------
if ($MysqlContainer) {
    $mysqlClientArgs = @('exec', '-e', 'MYSQL_PWD', $MysqlContainer, 'mysql', '--protocol=SOCKET', '--host=localhost', '-u', 'root')
}
else {
    $mysqlImage = & (Join-Path $PSScriptRoot 'ci/build-mysql-runtime.ps1')
    $mysqlClientArgs = @('run', '--rm', '--network', 'host', '-e', 'MYSQL_PWD', $mysqlImage, 'mysql', '-h', $MysqlHost, '-P', $MysqlPort, '-u', 'root')
}
$mysqlTarget = if ($MysqlContainer) { "container:$MysqlContainer/local-socket" } else { "${MysqlHost}:${MysqlPort}" }
$extension = [System.IO.Path]::GetExtension($BackupFile).ToLowerInvariant()
$restoreInputFile = (Resolve-Path -LiteralPath $BackupFile).Path
$decompressedTempFile = $null
$restoreOutputFile = $null
$restoreErrorFile = $null
$restoreFailed = $false
$previousMysqlPwdExists = Test-Path -LiteralPath Env:MYSQL_PWD
$previousMysqlPwd = $env:MYSQL_PWD

try {
    $env:MYSQL_PWD = $MysqlPassword
    Write-Host "Ensuring target database '$TargetDatabase' exists..."
    $createDbSql = "CREATE DATABASE IF NOT EXISTS ``$TargetDatabase`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    $createArgs = $mysqlClientArgs + @('-e', $createDbSql)
    & docker @createArgs 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create target database '$TargetDatabase' (exit $LASTEXITCODE)"
    }

    Write-Host "Restoring $BackupFile -> ${TargetDatabase}@$mysqlTarget..."
    $restoreOutputFile = [System.IO.Path]::GetTempFileName()
    $restoreErrorFile = [System.IO.Path]::GetTempFileName()
    if ($extension -eq '.gz') {
        $decompressedTempFile = [System.IO.Path]::GetTempFileName()
        $inStream = $null
        $gzipStream = $null
        $outStream = $null
        try {
            $inStream = [System.IO.File]::OpenRead($restoreInputFile)
            $gzipStream = New-Object System.IO.Compression.GZipStream($inStream, [System.IO.Compression.CompressionMode]::Decompress)
            $outStream = [System.IO.File]::Open($decompressedTempFile, [System.IO.FileMode]::Create)
            $gzipStream.CopyTo($outStream)
        }
        finally {
            if ($outStream) { $outStream.Dispose() }
            if ($gzipStream) { $gzipStream.Dispose() }
            if ($inStream) { $inStream.Dispose() }
        }
        $restoreInputFile = $decompressedTempFile
    }

    $restoreArgs = @($mysqlClientArgs[0], '-i') + $mysqlClientArgs[1..($mysqlClientArgs.Count - 1)] + @('--default-character-set=utf8mb4', $TargetDatabase)
    $restoreProcess = Start-Process -FilePath 'docker' -ArgumentList $restoreArgs `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardInput $restoreInputFile `
        -RedirectStandardOutput $restoreOutputFile `
        -RedirectStandardError $restoreErrorFile
    if ($restoreProcess.ExitCode -ne 0) {
        $restoreError = Get-Content -LiteralPath $restoreErrorFile -Raw -ErrorAction SilentlyContinue
        throw "Restore failed (exit $($restoreProcess.ExitCode)): $restoreError"
    }

    Write-Host 'Running minimal health check...'
    $checkSql = "SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = '$TargetDatabase';"
    $checkArgs = $mysqlClientArgs + @('-N', '-B', '-e', $checkSql)
    $tableCountOutput = & docker @checkArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Restore health check failed (exit $LASTEXITCODE)" }
    $tableCount = ($tableCountOutput -join "`n").Trim()
    if ($tableCount -notmatch '^[0-9]+$' -or [long]$tableCount -le 0) {
        throw 'Restore health check did not verify a positive table count'
    }
    Write-Host "Health check PASS - $tableCount table(s) found in '$TargetDatabase'."
}
catch {
    $restoreFailed = $true
    Write-Error "Restore failed: $_" -ErrorAction Continue
}
finally {
    if ($decompressedTempFile) {
        Remove-Item -LiteralPath $decompressedTempFile -Force -ErrorAction SilentlyContinue
    }
    foreach ($tempFile in @($restoreOutputFile, $restoreErrorFile)) {
        if ($tempFile) { Remove-Item -LiteralPath $tempFile -Force -ErrorAction SilentlyContinue }
    }
    if ($previousMysqlPwdExists) { $env:MYSQL_PWD = $previousMysqlPwd }
    else { Remove-Item -LiteralPath Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}

if ($restoreFailed) { exit 1 }
Write-Host 'Restore complete.'
exit 0
