[CmdletBinding()]
param(
    [string]$Database = 'cgc_pms_demo_ui_20260728',
    [string]$ConfirmDatabase = '',
    [string]$ExpectedFingerprint = '',
    [string]$MysqlHost = '127.0.0.1',
    [int]$MysqlPort = 3307,
    [string]$MysqlContainer = 'cgc-pms-mysql-dev',
    [string]$BackendContainer = 'cgc-pms-backend-dev',
    [string]$MinioContainer = 'cgc-pms-minio-dev',
    [string]$BackupRoot = 'D:\backups\cgc-pms\data-maintenance',
    [switch]$Execute,
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$policyPath = Join-Path $repoRoot 'backend/src/main/resources/data-maintenance-table-policy.json'
$markerPath = Join-Path $repoRoot '.codex-autopilot/ALLOW_TEST_DATA_RESET'
function Get-MySqlClientImage {
    # Match the explicitly selected server; never build or upgrade it in a preview.
    $image = (& docker inspect --format '{{.Image}}' $MysqlContainer).Trim()
    if ($LASTEXITCODE -ne 0 -or $image -cnotmatch '^sha256:[0-9a-f]{64}$') {
        throw 'Cannot resolve the selected MySQL container image ID'
    }
    return $image
}

function Get-Sha256Text([string]$Text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $hash = [Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($hash).ToLowerInvariant()
}

function Assert-Identifier([string]$Value, [string]$Label) {
    if ($Value -notmatch '^[A-Za-z0-9_]+$') {
        throw "$Label contains unsupported characters: $Value"
    }
}

function Get-ContainerEnvironment([string]$Container) {
    $lines = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $Container 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect container '$Container': $lines" }
    $values = @{}
    foreach ($line in $lines) {
        $parts = $line -split '=', 2
        if ($parts.Count -eq 2) { $values[$parts[0]] = $parts[1] }
    }
    return $values
}

function Invoke-MySql([string]$Sql, [string]$TargetDatabase = $Database) {
    Assert-Identifier $TargetDatabase 'Database'
    $rootPassword = (Get-ContainerEnvironment $MysqlContainer)['MYSQL_ROOT_PASSWORD']
    if ([string]::IsNullOrWhiteSpace($rootPassword)) {
        throw "MYSQL_ROOT_PASSWORD is unavailable in '$MysqlContainer'"
    }

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'docker'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Environment['MYSQL_PWD'] = $rootPassword
    foreach ($arg in @(
            'run', '--rm', '-i', '--network', 'host',
            '-e', 'MYSQL_PWD',
            (Get-MySqlClientImage), 'mysql',
            '-h', $MysqlHost, '-P', [string]$MysqlPort, '-uroot',
            '--batch', '--raw', '--skip-column-names', $TargetDatabase
        )) {
        [void]$startInfo.ArgumentList.Add($arg)
    }
    $process = [Diagnostics.Process]::Start($startInfo)
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    $output = $process.StandardOutput.ReadToEnd().Trim()
    $errorOutput = $process.StandardError.ReadToEnd().Trim()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "MySQL command failed: $errorOutput" }
    return $output
}

function Invoke-PwshScript([string]$Path, [string[]]$Arguments) {
    $output = & pwsh -NoProfile -File $Path @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Script failed: $Path`n$($output -join "`n")" }
    return $output
}

function Get-Policy {
    if (-not (Test-Path -LiteralPath $policyPath -PathType Leaf)) {
        throw "Missing table policy: $policyPath"
    }
    $policy = Get-Content -LiteralPath $policyPath -Raw | ConvertFrom-Json
    if ($policy.version -ne 1 -or -not $policy.groups) { throw 'Unsupported table policy format' }

    $tablePolicy = @{}
    foreach ($group in $policy.groups) {
        if ($group.disposition -notin @('RETAIN', 'CLEAR')) {
            throw "Unsupported policy disposition: $($group.disposition)"
        }
        foreach ($table in $group.tables) {
            Assert-Identifier $table 'Table policy entry'
            if ($tablePolicy.ContainsKey($table)) { throw "Duplicate table policy entry: $table" }
            $tablePolicy[$table] = [pscustomobject]@{
                group = [string]$group.code
                disposition = [string]$group.disposition
            }
        }
    }
    return [pscustomobject]@{
        document = $policy
        tables = $tablePolicy
        fingerprint = (Get-FileHash -LiteralPath $policyPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function Get-TableCounts([string[]]$Tables) {
    if ($Tables.Count -eq 0) { return @{} }
    $queries = foreach ($table in $Tables) {
        Assert-Identifier $table 'Table'
        "SELECT '$table', COUNT(*) FROM ``$table``"
    }
    $lines = (Invoke-MySql (($queries -join ' UNION ALL ') + ' ORDER BY 1;')) -split "`n"
    $counts = @{}
    foreach ($line in $lines) {
        if (-not $line) { continue }
        $parts = $line -split "`t", 2
        $counts[$parts[0]] = [long]$parts[1]
    }
    return $counts
}

function Get-FileInventory {
    $output = Invoke-MySql @'
SELECT id, bucket_name, storage_path, file_size
FROM sys_file
ORDER BY bucket_name, storage_path, id;
'@
    $files = @()
    foreach ($line in ($output -split "`n")) {
        if (-not $line) { continue }
        $parts = $line -split "`t", 4
        if ($parts.Count -ne 4) { throw "Invalid sys_file inventory row: $line" }
        if ($parts[1] -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$') { throw "Invalid bucket name: $($parts[1])" }
        if ([string]::IsNullOrWhiteSpace($parts[2]) -or $parts[2].StartsWith('/') -or
            ($parts[2] -split '/') -contains '..') {
            throw "Unsafe MinIO object path: $($parts[2])"
        }
        $files += [pscustomobject]@{
            id = [string]$parts[0]
            bucket = [string]$parts[1]
            path = [string]$parts[2]
            size = [long]$parts[3]
        }
    }
    return $files
}

function Get-TableDigest([string]$Table, [string]$TargetDatabase = $Database) {
    Assert-Identifier $Table 'Table'
    Assert-Identifier $TargetDatabase 'Database'
    $rootPassword = (Get-ContainerEnvironment $MysqlContainer)['MYSQL_ROOT_PASSWORD']
    $command = 'set -o pipefail; mysqldump -h 127.0.0.1 -P ' + $MysqlPort +
        ' -uroot --no-create-info --skip-comments --compact --skip-extended-insert --order-by-primary ' +
        '"$TARGET_DB" "$TARGET_TABLE" | sha256sum | cut -d" " -f1'
    $previousPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD')
    try {
        $env:MYSQL_PWD = $rootPassword
        $result = & docker run --rm --network host `
            -e MYSQL_PWD -e "TARGET_DB=$TargetDatabase" -e "TARGET_TABLE=$Table" `
            (Get-MySqlClientImage) bash -lc $command 2>&1
    }
    finally {
        if ($null -eq $previousPwd) { Remove-Item -LiteralPath Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable('MYSQL_PWD',$previousPwd) }
    }
    if ($LASTEXITCODE -ne 0 -or $result -notmatch '^[0-9a-f]{64}$') {
        throw "Cannot hash table '$TargetDatabase.$Table': $result"
    }
    return [string]$result
}

function Get-RetainedDigests([string[]]$Tables, [string]$TargetDatabase = $Database) {
    $digests = [ordered]@{}
    foreach ($table in ($Tables | Sort-Object)) {
        $digests[$table] = Get-TableDigest $table $TargetDatabase
    }
    return $digests
}

function Get-Preview {
    $policy = Get-Policy
    $tableRows = Invoke-MySql @'
SELECT TABLE_NAME, TABLE_TYPE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;
'@
    $baseTables = @()
    $views = @()
    foreach ($line in ($tableRows -split "`n")) {
        if (-not $line) { continue }
        $parts = $line -split "`t", 2
        if ($parts[1] -eq 'BASE TABLE') { $baseTables += $parts[0] }
        elseif ($parts[1] -eq 'VIEW') { $views += $parts[0] }
    }

    $declaredTables = @($policy.tables.Keys | Sort-Object)
    $unknownTables = @(Compare-Object $declaredTables ($baseTables | Sort-Object) |
        Where-Object SideIndicator -eq '=>' | ForEach-Object InputObject)
    $missingTables = @(Compare-Object $declaredTables ($baseTables | Sort-Object) |
        Where-Object SideIndicator -eq '<=' | ForEach-Object InputObject)
    $blockers = @()
    if ($unknownTables.Count) { $blockers += "Unknown base tables: $($unknownTables -join ',')" }
    if ($missingTables.Count) { $blockers += "Policy tables missing from database: $($missingTables -join ',')" }

    $counts = Get-TableCounts $baseTables
    $retainedTables = @($declaredTables | Where-Object { $policy.tables[$_].disposition -eq 'RETAIN' })
    $clearTables = @($declaredTables | Where-Object { $policy.tables[$_].disposition -eq 'CLEAR' })
    $retainedGroups = @()
    foreach ($group in $policy.document.groups | Where-Object disposition -eq 'RETAIN') {
        $rowCount = 0L
        foreach ($table in $group.tables) { $rowCount += [long]($counts[$table] ?? 0) }
        $retainedGroups += [pscustomobject]@{
            code = [string]$group.code
            tableCount = @($group.tables).Count
            rowCount = $rowCount
        }
    }
    $clearRows = 0L
    foreach ($table in $clearTables) { $clearRows += [long]($counts[$table] ?? 0) }
    $files = if ($baseTables -contains 'sys_file') { @(Get-FileInventory) } else { @() }

    $policyFingerprint = $policy.fingerprint
    $countLines = foreach ($table in ($baseTables | Sort-Object)) { "$table|$($counts[$table])" }
    $fileLines = foreach ($file in $files) { "$($file.id)|$($file.bucket)|$($file.path)|$($file.size)" }
    $fingerprint = Get-Sha256Text (($policyFingerprint, ($countLines -join "`n"), ($fileLines -join "`n")) -join "`n")

    return [pscustomobject]@{
        database = $Database
        policyFingerprint = $policyFingerprint
        fingerprint = $fingerprint
        eligible = $blockers.Count -eq 0
        blockers = $blockers
        retainedGroups = $retainedGroups
        retainedTables = $retainedTables
        clearTables = $clearTables
        retainedTableCount = $retainedTables.Count
        clearTableCount = $clearTables.Count
        clearRowCount = $clearRows
        sysFileCount = $files.Count
        ignoredViews = $views
        counts = $counts
        files = $files
    }
}

function Assert-LocalSafety {
    if ($MysqlHost -notin @('localhost', '127.0.0.1')) { throw 'MySQL host must be localhost or 127.0.0.1' }
    if ($MysqlPort -ne 3307) { throw 'This cleanup is restricted to the localhost development port 3307' }
    if ($Database -notmatch '^cgc_pms(?:_(?:dev|test|demo)(?:_[A-Za-z0-9]+)*)?$') {
        throw "Database '$Database' is outside the dev/test/demo allowlist"
    }
    Assert-Identifier $Database 'Database'
    if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) { throw "Missing reset marker: $markerPath" }

    $mysqlEvidence = & docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}|{{json .NetworkSettings.Ports}}' $MysqlContainer 2>&1
    if ($LASTEXITCODE -ne 0 -or $mysqlEvidence -notmatch '^mysql\|' -or
        $mysqlEvidence -notmatch '127\.0\.0\.1' -or $mysqlEvidence -notmatch '3307') {
        throw "Container '$MysqlContainer' is not the expected localhost-bound MySQL service"
    }
    $backendEnvironment = Get-ContainerEnvironment $BackendContainer
    if ($backendEnvironment['SPRING_PROFILES_ACTIVE'] -notin @('dev', 'test', 'demo')) {
        throw "Backend profile is not dev/test/demo: $($backendEnvironment['SPRING_PROFILES_ACTIVE'])"
    }
    [void](Invoke-MySql 'SELECT 1;')
}

function Backup-MinioObjects([object[]]$Files, [string]$RemoteRoot, [string]$HostRoot) {
    New-Item -ItemType Directory -Path $HostRoot -Force | Out-Null
    if ($Files.Count -eq 0) { return }
    $inventory = ($Files | ForEach-Object { "$($_.bucket)`t$($_.path)" }) -join "`n"
    $backupCommand = @'
set -eu
mc alias set cleanup http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mkdir -p "$BACKUP_ROOT/objects"
tab=$(printf '\t')
while IFS="$tab" read -r bucket object_path; do
  bucket=${bucket%$'\r'}
  object_path=${object_path%$'\r'}
  [ -n "$bucket" ] || continue
  target="$BACKUP_ROOT/objects/$bucket/$object_path"
  mkdir -p "$(dirname "$target")"
  mc cp --quiet "cleanup/$bucket/$object_path" "$target" >/dev/null
done
'@
    $output = $inventory | docker exec -i -e "BACKUP_ROOT=$RemoteRoot" $MinioContainer sh -lc $backupCommand 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MinIO backup failed: $output" }
    & docker cp "${MinioContainer}:${RemoteRoot}/objects/." $HostRoot 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy MinIO backup to host' }

    foreach ($file in $Files) {
        $relative = Join-Path $file.bucket ($file.path -replace '/', [IO.Path]::DirectorySeparatorChar)
        $backupFile = Join-Path $HostRoot $relative
        if (-not (Test-Path -LiteralPath $backupFile -PathType Leaf)) { throw "MinIO backup missing: $($file.bucket)/$($file.path)" }
        if ((Get-Item -LiteralPath $backupFile).Length -ne $file.size) { throw "MinIO backup size mismatch: $($file.id)" }
    }
}

function Remove-MinioObjects([object[]]$Files) {
    if ($Files.Count -eq 0) { return }
    $inventory = ($Files | ForEach-Object { "$($_.bucket)`t$($_.path)" }) -join "`n"
    $removeCommand = @'
set -eu
mc alias set cleanup http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
tab=$(printf '\t')
while IFS="$tab" read -r bucket object_path; do
  bucket=${bucket%$'\r'}
  object_path=${object_path%$'\r'}
  [ -n "$bucket" ] || continue
  mc rm --force "cleanup/$bucket/$object_path" >/dev/null
done
'@
    $output = $inventory | docker exec -i $MinioContainer sh -lc $removeCommand 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MinIO delete failed: $output" }
}

function Restore-MinioObjects([object[]]$Files, [string]$RemoteRoot) {
    if ($Files.Count -eq 0) { return }
    $inventory = ($Files | ForEach-Object { "$($_.bucket)`t$($_.path)" }) -join "`n"
    $restoreCommand = @'
set -eu
mc alias set cleanup http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
tab=$(printf '\t')
while IFS="$tab" read -r bucket object_path; do
  bucket=${bucket%$'\r'}
  object_path=${object_path%$'\r'}
  [ -n "$bucket" ] || continue
  mc cp --quiet "$BACKUP_ROOT/objects/$bucket/$object_path" "cleanup/$bucket/$object_path"
done
'@
    $output = $inventory | docker exec -i -e "BACKUP_ROOT=$RemoteRoot" $MinioContainer sh -lc $restoreCommand 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MinIO restore failed: $output" }
}

function Remove-RemoteBackup([string]$RemoteRoot) {
    if ($RemoteRoot -notmatch '^/tmp/cgc-data-maintenance-[0-9]{8}_[0-9]{6}$') {
        throw "Unsafe remote backup path: $RemoteRoot"
    }
    & docker exec -e "BACKUP_ROOT=$RemoteRoot" $MinioContainer sh -lc 'rm -rf -- "$BACKUP_ROOT"' 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Cannot remove temporary MinIO backup: $RemoteRoot" }
}

if ($SelfTest) {
    if ((Get-Sha256Text 'cgc-pms') -ne '22802ff6d4e4755fa8e0714adaa04d34c9f945b9a71042414097eda250d96e90') {
        throw 'SHA-256 self-test failed'
    }
    $policy = Get-Policy
    if ($policy.tables.Count -lt 25) { throw 'Table policy self-test failed' }
    if (@($policy.tables.Keys | Where-Object { $policy.tables[$_].disposition -eq 'RETAIN' }).Count -ne 25) {
        throw 'Retained table count self-test failed'
    }
    $probeStatements = @('DELETE FROM `one`;', 'DELETE FROM `two`;')
    $probeSql = (@('START TRANSACTION;') + $probeStatements + @('COMMIT;')) -join "`n"
    if ($probeSql -match 'System\.Object\[\]' -or ($probeSql -split "`n").Count -ne 4) {
        throw 'Delete statement flattening self-test failed'
    }
    Write-Host 'SelfTest PASS'
    exit 0
}

Assert-LocalSafety
$preview = Get-Preview
if (-not $Execute) {
    $preview | Select-Object database, policyFingerprint, fingerprint, eligible, blockers,
        retainedGroups, retainedTableCount, clearTableCount, clearRowCount, sysFileCount, ignoredViews |
        ConvertTo-Json -Depth 6
    Write-Host "Execute only after verified preview: pwsh -NoProfile -File scripts/database/clear-business-data.ps1 -Database $Database -Execute -ConfirmDatabase $Database -ExpectedFingerprint $($preview.fingerprint)"
    exit 0
}

if ($ConfirmDatabase -ne $Database) { throw 'ConfirmDatabase must exactly match Database' }
if ([string]::IsNullOrWhiteSpace($ExpectedFingerprint) -or $ExpectedFingerprint -ne $preview.fingerprint) {
    throw 'ExpectedFingerprint is missing or differs from the current preview'
}
if (-not $preview.eligible) { throw "Cleanup is blocked: $($preview.blockers -join '; ')" }

$backendWasRunning = ((& docker inspect --format '{{.State.Running}}' $BackendContainer 2>&1) -join '') -eq 'true'
if (-not $backendWasRunning) { throw "Backend '$BackendContainer' must be running before execution" }
$backendStopped = $false
$safeToRestart = $true
$runId = Get-Date -Format 'yyyyMMdd_HHmmss'
$runDirectory = Join-Path $BackupRoot $runId
$remoteBackupRoot = "/tmp/cgc-data-maintenance-$runId"
$minioBackupDirectory = Join-Path $runDirectory 'minio'
$runManifestPath = Join-Path $runDirectory 'run-manifest.json'

New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
try {
    & docker stop $BackendContainer 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Cannot stop backend '$BackendContainer'" }
    $backendStopped = $true

    $frozenPreview = Get-Preview
    if ($frozenPreview.fingerprint -ne $ExpectedFingerprint) {
        throw 'Data changed after preview; refresh preview and retry'
    }
    $beforeDigests = Get-RetainedDigests $frozenPreview.retainedTables

    $rootPassword = (Get-ContainerEnvironment $MysqlContainer)['MYSQL_ROOT_PASSWORD']
    $previousRootPassword = $env:MYSQL_ROOT_PASSWORD
    try {
        $env:MYSQL_ROOT_PASSWORD = $rootPassword
        [void](Invoke-PwshScript (Join-Path $repoRoot 'scripts/mysql-backup.ps1') @(
                    '-BackupDir', $runDirectory,
                    '-MysqlHost', $MysqlHost,
                    '-MysqlPort', [string]$MysqlPort,
                    '-MysqlDatabase', $Database,
                    '-SkipRetention'
                ))
        $databaseBackup = Get-ChildItem -LiteralPath $runDirectory -Filter "${Database}_full_*.sql.gz" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $databaseBackup) { throw 'MySQL backup file was not created' }
        $databaseBackupSha256 = (Get-FileHash -LiteralPath $databaseBackup.FullName -Algorithm SHA256).Hash.ToLowerInvariant()

        $verifyDatabase = "cgc_pms_restore_test_$runId"
        try {
            [void](Invoke-PwshScript (Join-Path $repoRoot 'scripts/mysql-restore.ps1') @(
                        '-BackupFile', $databaseBackup.FullName,
                        '-TargetDatabase', $verifyDatabase,
                        '-MysqlHost', $MysqlHost,
                        '-MysqlPort', [string]$MysqlPort
                    ))
            $verifyDigests = Get-RetainedDigests $frozenPreview.retainedTables $verifyDatabase
            foreach ($table in $frozenPreview.retainedTables) {
                if ($beforeDigests[$table] -ne $verifyDigests[$table]) {
                    throw "Restore verification mismatch for retained table: $table"
                }
            }
        }
        finally {
            if ($verifyDatabase) {
                [void](Invoke-MySql "DROP DATABASE IF EXISTS ``$verifyDatabase``;" 'information_schema')
            }
        }
    }
    finally {
        $env:MYSQL_ROOT_PASSWORD = $previousRootPassword
    }

    Backup-MinioObjects $frozenPreview.files $remoteBackupRoot $minioBackupDirectory

    $runManifest = [ordered]@{
        runId = $runId
        database = $Database
        host = $MysqlHost
        port = $MysqlPort
        policyFingerprint = $frozenPreview.policyFingerprint
        previewFingerprint = $frozenPreview.fingerprint
        databaseBackup = $databaseBackup.FullName
        databaseBackupSha256 = $databaseBackupSha256
        retainedTableDigestsBefore = $beforeDigests
        clearTableCount = $frozenPreview.clearTableCount
        clearRowCountBefore = $frozenPreview.clearRowCount
        fileCountBefore = $frozenPreview.sysFileCount
        files = $frozenPreview.files
        status = 'BACKUP_VERIFIED'
    }
    [IO.File]::WriteAllText($runManifestPath, ($runManifest | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))

    $safeToRestart = $false
    try {
        Remove-MinioObjects $frozenPreview.files
    }
    catch {
        Restore-MinioObjects $frozenPreview.files $remoteBackupRoot
        $safeToRestart = $true
        throw
    }

    $deleteStatements = foreach ($table in $frozenPreview.clearTables) {
        Assert-Identifier $table 'Clear table'
        "DELETE FROM ``$table``;"
    }
    $deleteSql = (@(
            'SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;',
            'START TRANSACTION;',
            'SET FOREIGN_KEY_CHECKS = 0;'
        ) + $deleteStatements + @(
            'SET FOREIGN_KEY_CHECKS = 1;',
            'COMMIT;'
        )) -join "`n"
    try {
        [void](Invoke-MySql $deleteSql)
    }
    catch {
        Restore-MinioObjects $frozenPreview.files $remoteBackupRoot
        $safeToRestart = $true
        throw
    }

    $afterPreview = Get-Preview
    $afterDigests = Get-RetainedDigests $afterPreview.retainedTables
    if (-not $afterPreview.eligible -or $afterPreview.clearRowCount -ne 0 -or $afterPreview.sysFileCount -ne 0) {
        throw 'Post-cleanup readback failed; backend remains stopped for recovery'
    }
    foreach ($table in $afterPreview.retainedTables) {
        if ($beforeDigests[$table] -ne $afterDigests[$table]) {
            throw "Retained table changed during cleanup: $table"
        }
    }
    if (@($afterPreview.ignoredViews).Count -ne @($frozenPreview.ignoredViews).Count) {
        throw 'View inventory changed during cleanup'
    }

    $runManifest.status = 'PASSED'
    $runManifest.retainedTableDigestsAfter = $afterDigests
    $runManifest.clearRowCountAfter = $afterPreview.clearRowCount
    $runManifest.fileCountAfter = $afterPreview.sysFileCount
    [IO.File]::WriteAllText($runManifestPath, ($runManifest | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    Remove-RemoteBackup $remoteBackupRoot
    $safeToRestart = $true
}
finally {
    if ($backendStopped -and $safeToRestart) {
        & docker start $BackendContainer 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Warning "Cannot restart backend '$BackendContainer'" }
    }
}

Write-Host "Execute PASS. Evidence: $runManifestPath"
