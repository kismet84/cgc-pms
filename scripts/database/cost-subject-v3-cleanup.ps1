param(
    [ValidateSet('Preview', 'Execute', 'SelfTest')]
    [string]$Mode = 'Preview',
    [string]$Container = 'cgc-pms-mysql-dev',
    [string]$Database = 'cgc_pms',
    [string]$ExpectedFingerprint = '',
    [string]$BackupFile = '',
    [string]$BackupSha256 = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$marker = Join-Path $repoRoot '.codex-autopilot/ALLOW_TEST_DATA_RESET'
$doomedSubjectFilter = "(subject_code = '5401.02' OR subject_code LIKE '5401.02.%' OR (subject_code LIKE '5401.03.%' AND id NOT IN (901001,901002,901003,901004,901005,901006,901007,901008,901009,901010)))"
$doomed = "cost_subject_id IN (SELECT id FROM cost_subject WHERE deleted_flag = 0 AND $doomedSubjectFilter)"
$references = [ordered]@{
    accounting_entry_line = @('cost_subject_id')
    bid_cost_target_transfer_line = @('source_cost_subject_id', 'target_cost_subject_id')
    cost_forecast_item = @('cost_subject_id')
    cost_item = @('cost_subject_id')
    cost_subject_assignment_rule = @('cost_subject_id')
    cost_subject_mapping_item = @('source_cost_subject_id', 'target_cost_subject_id')
    cost_summary = @('cost_subject_id')
    cost_target_item = @('cost_subject_id')
    expense_application = @('cost_subject_id')
    finance_cost_allocation_batch = @('cost_subject_id')
    overhead_allocation_record = @('cost_subject_id')
    overhead_allocation_rule = @('cost_subject_id')
    pay_application = @('cost_subject_id')
    project_budget_line = @('cost_subject_id')
    project_cost_subject_scope = @('cost_subject_id')
    qs_consequence = @('cost_subject_id')
    stl_settlement_item = @('cost_subject_id')
    var_order_item = @('cost_subject_id')
}

function Invoke-MySql([string]$Sql) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'docker'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($arg in @('exec', '-i', '-e', "TARGET_DB=$Database", $Container, 'sh', '-lc', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names "$TARGET_DB"')) {
        [void]$startInfo.ArgumentList.Add($arg)
    }
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    $output = $process.StandardOutput.ReadToEnd().Trim()
    $errorOutput = $process.StandardError.ReadToEnd().Trim()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "MySQL command failed: $errorOutput" }
    return $output
}

function Get-Sha256([string]$Text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $hash = [Security.Cryptography.SHA256]::HashData($bytes)
    return [Convert]::ToHexString($hash).ToLowerInvariant()
}

function Test-ReferenceRowPresent([string]$Line) {
    $parts = $Line -split "`t"
    return $parts.Count -lt 2 -or $parts[1] -ne '0'
}

function Test-NonAccountingReference([string]$Line) {
    $parts = $Line -split "`t"
    return $parts[0] -ne 'accounting_entry_line.cost_subject_id' -and (Test-ReferenceRowPresent $Line)
}

if ($Mode -eq 'SelfTest') {
    if ((Get-Sha256 'cgc-pms') -ne '22802ff6d4e4755fa8e0714adaa04d34c9f945b9a71042414097eda250d96e90') {
        throw 'SHA-256 self-test failed'
    }
    if ($references.Count -ne 18) { throw 'Reference inventory self-test failed' }
    if ((Test-NonAccountingReference "var_order_item.cost_subject_id`t0") -or
        -not (Test-NonAccountingReference "cost_item.cost_subject_id`t1`t42") -or
        (Test-ReferenceRowPresent "var_order_item.cost_subject_id`t0")) {
        throw 'Reference parser self-test failed'
    }
    Write-Host 'SelfTest PASS'
    exit 0
}

if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) { throw "Missing local reset marker: $marker" }
$containerEvidence = (& docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}|{{json .NetworkSettings.Ports}}' $Container 2>&1)
if ($LASTEXITCODE -ne 0 -or $containerEvidence -notmatch '^mysql\|' -or $containerEvidence -notmatch '127\.0\.0\.1') {
    throw "Container '$Container' is not the localhost-bound compose mysql service"
}
if ($Database -notmatch '^cgc_pms(?:_(?:dev|test|demo|restore_test))?$') { throw "Database '$Database' is outside the local allowlist" }

$actualColumns = (Invoke-MySql @"
SELECT CONCAT(table_name, '.', column_name)
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND column_name IN ('cost_subject_id', 'source_cost_subject_id', 'target_cost_subject_id')
ORDER BY table_name, column_name;
"@) -split "`n" | Where-Object { $_ }
$expectedColumns = foreach ($table in $references.Keys) { foreach ($column in $references[$table]) { "$table.$column" } }
$columnDifferences = Compare-Object ($expectedColumns | Sort-Object) ($actualColumns | Sort-Object)
if ($columnDifferences) { throw "Cost-subject reference inventory mismatch:`n$($columnDifferences | Out-String)" }

$countSql = foreach ($reference in $actualColumns) {
    $table, $column = $reference -split '\.', 2
    "SELECT '$reference', COUNT(*), COALESCE(GROUP_CONCAT(id ORDER BY id SEPARATOR ','), '') FROM $table WHERE $($doomed.Replace('cost_subject_id', $column))"
}
$referenceLines = Invoke-MySql (($countSql -join " UNION ALL ") + " ORDER BY 1;")
$subjectLines = Invoke-MySql @"
SELECT CONCAT(id, '|', tenant_id, '|', subject_code)
FROM cost_subject
WHERE deleted_flag = 0
  AND $doomedSubjectFilter
ORDER BY tenant_id, subject_code, id;
"@
$accountingState = Invoke-MySql @"
SELECT CONCAT(COUNT(*), '|', COALESCE(SUM(CASE WHEN e.id IS NULL THEN 1 ELSE 0 END), 0), '|', COALESCE(SUM(CASE WHEN e.id IS NOT NULL THEN 1 ELSE 0 END), 0))
FROM accounting_entry_line l
LEFT JOIN accounting_entry e ON e.id = l.entry_id AND e.tenant_id = l.tenant_id
WHERE l.$doomed;
"@
$fingerprint = Get-Sha256 (($subjectLines, $referenceLines, $accountingState) -join "`n")

$preview = [ordered]@{
    mode = $Mode
    database = $Database
    doomedSubjectCount = if ($subjectLines) { ($subjectLines -split "`n").Count } else { 0 }
    references = $referenceLines -split "`n"
    accountingEntryLineState = $accountingState
    fingerprint = $fingerprint
}

if ($Mode -eq 'Preview') {
    $preview | ConvertTo-Json -Depth 4
    exit 0
}

if (-not $ExpectedFingerprint -or $ExpectedFingerprint -ne $fingerprint) { throw 'Preview fingerprint missing or changed' }
if (-not $BackupFile -or -not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) { throw 'Verified full backup file is required' }
$actualBackupSha256 = (Get-FileHash -LiteralPath $BackupFile -Algorithm SHA256).Hash.ToLowerInvariant()
if (-not $BackupSha256 -or $actualBackupSha256 -ne $BackupSha256.ToLowerInvariant()) { throw 'Backup SHA-256 mismatch' }

$nonAccountingReferences = $preview.references | Where-Object {
    Test-NonAccountingReference $_
}
$accountingParts = $accountingState -split '\|'
if ($nonAccountingReferences -or $accountingParts.Count -ne 3 -or $accountingParts[0] -ne $accountingParts[1] -or $accountingParts[2] -ne '0') {
    throw 'Live or unsupported business references exist; cleanup refused'
}

$deleted = Invoke-MySql @"
START TRANSACTION;
DELETE l
FROM accounting_entry_line l
LEFT JOIN accounting_entry e ON e.id = l.entry_id AND e.tenant_id = l.tenant_id
WHERE l.$doomed AND e.id IS NULL;
SELECT ROW_COUNT();
COMMIT;
"@
$afterLines = (Invoke-MySql (($countSql -join " UNION ALL ") + " ORDER BY 1;")) -split "`n"
$remaining = $afterLines | Where-Object { $_ -and (Test-ReferenceRowPresent $_) }
if ($remaining) { throw "Cleanup readback failed; references remain:`n$remaining" }
Write-Host "Execute PASS; deleted orphan accounting_entry_line rows: $deleted"
