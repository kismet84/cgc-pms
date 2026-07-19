param(
    [string]$Database = 'cgc_pms_demo_v2',
    [string]$MySqlContainer = 'cgc-pms-mysql-dev',
    [string]$ApiBaseUrl = '',
    [string]$ApiUsername = 'admin'
)

$ErrorActionPreference = 'Stop'
$coverageRoot = $PSScriptRoot
$inventoryScript = Join-Path $coverageRoot 'build-form-field-inventory.ps1'
$inventoryPath = Join-Path $coverageRoot 'form-field-coverage.csv'
$mapPath = Join-Path $coverageRoot 'page-table-map.json'

function Invoke-MySql([string]$Sql) {
    $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("cgc-demo-coverage-verify-{0}.sql" -f [guid]::NewGuid().ToString('N'))
    $containerFile = "/tmp/cgc-demo-coverage-verify-$([guid]::NewGuid().ToString('N')).sql"
    try {
        [System.IO.File]::WriteAllText($tempFile, $Sql, [System.Text.UTF8Encoding]::new($false))
        docker cp $tempFile "${MySqlContainer}:$containerFile" 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'DEMO_COVERAGE_VERIFY_DOCKER_COPY_FAILED' }
        $result = docker exec $MySqlContainer sh -lc "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql --no-defaults -uroot --default-character-set=utf8mb4 -N -B $Database < $containerFile" 2>&1
        if ($LASTEXITCODE -ne 0) {
            $summary = (($result | Select-Object -Last 4) -join ' ') -replace '[\r\n]+', ' '
            throw "DEMO_COVERAGE_VERIFY_SQL_FAILED:$summary"
        }
        return @($result)
    } finally {
        docker exec $MySqlContainer rm -f $containerFile 2>$null | Out-Null
        if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
    }
}

$inventorySummary = & $inventoryScript -Database $Database -MySqlContainer $MySqlContainer -ApiBaseUrl $ApiBaseUrl -ApiUsername $ApiUsername | ConvertFrom-Json
$inventory = Import-Csv -LiteralPath $inventoryPath
$pageMap = Get-Content -LiteralPath $mapPath -Raw | ConvertFrom-Json -AsHashtable
$targetTables = @($pageMap.Values | ForEach-Object { $_ } | Sort-Object -Unique)

$columnRows = Invoke-MySql -Sql "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position;"
$columnsByTable = @{}
foreach ($row in $columnRows) {
    $parts = $row -split "`t", 2
    if ($parts.Count -ne 2) { continue }
    if (-not $columnsByTable.ContainsKey($parts[0])) { $columnsByTable[$parts[0]] = @() }
    $columnsByTable[$parts[0]] += $parts[1]
}
$demoIdStart = 520000000000000000L
$demoIdEnd = 520000000000009999L
function Get-DemoScopeSql([string]$Table) {
    $columns = @($columnsByTable[$Table])
    if ($columns.Count -eq 0) { throw "DEMO_COVERAGE_TABLE_NOT_FOUND:$Table" }
    $referenceScope = switch ($Table) {
        'cost_subject' { "id IN (SELECT cost_subject_id FROM cost_item WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND cost_subject_id IS NOT NULL)"; break }
        'md_material_category' { "id IN (SELECT category_id FROM md_material WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND category_id IS NOT NULL)"; break }
        'org_company' { "id IN (SELECT org_id FROM pm_project WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND org_id IS NOT NULL)"; break }
        'org_department' { "id IN (SELECT department_id FROM org_position WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND department_id IS NOT NULL)"; break }
        'sys_dict_type' { "tenant_id=0 AND dict_code IN ('project_type','project_status','approval_status','contract_type','contract_status','partner_type','cost_type','cost_source_type','cost_status')"; break }
        'sys_dict_data' { "tenant_id=0 AND dict_type_id IN (SELECT id FROM sys_dict_type WHERE tenant_id=0 AND dict_code IN ('project_type','project_status','approval_status','contract_type','contract_status','partner_type','cost_type','cost_source_type','cost_status'))"; break }
        'sys_role' { "id IN (SELECT role_id FROM sys_user_role WHERE tenant_id=0 AND user_id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        'sys_role_menu' { "role_id IN (SELECT role_id FROM sys_user_role WHERE tenant_id=0 AND user_id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        'sys_menu' { "id IN (SELECT menu_id FROM sys_role_menu WHERE role_id IN (SELECT role_id FROM sys_user_role WHERE tenant_id=0 AND user_id BETWEEN $demoIdStart AND $demoIdEnd))"; break }
        'wf_template' { "id IN (SELECT template_id FROM wf_instance WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        'wf_template_node' { "template_id IN (SELECT template_id FROM wf_instance WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        default { $null }
    }
    if ($referenceScope) { return $referenceScope }
    $conditions = @()
    if ($columns -contains 'tenant_id') { $conditions += 'tenant_id=0' }
    if ($columns -contains 'id') {
        $conditions += "id BETWEEN $demoIdStart AND $demoIdEnd"
    } elseif ($columns -contains 'project_id') {
        $conditions += "project_id BETWEEN $demoIdStart AND $demoIdEnd"
    } else {
        $ownedIdColumns = @($columns | Where-Object { $_ -match '_id$' })
        if ($ownedIdColumns.Count -eq 0) { throw "DEMO_COVERAGE_SCOPE_UNSUPPORTED:$Table" }
        $conditions += '(' + (($ownedIdColumns | ForEach-Object { "$_ BETWEEN $demoIdStart AND $demoIdEnd" }) -join ' OR ') + ')'
    }
    return $conditions -join ' AND '
}

$tableQueries = foreach ($table in $targetTables) {
    $scope = Get-DemoScopeSql -Table $table
    "SELECT '$table',COUNT(*) FROM ``$table`` WHERE $scope"
}
$zeroTables = @()
foreach ($row in Invoke-MySql -Sql (($tableQueries -join ' UNION ALL ') + ';')) {
    $parts = $row -split "`t", 2
    if ($parts.Count -eq 2 -and [int]$parts[1] -eq 0) { $zeroTables += $parts[0] }
}

$fkMetadataSql = @'
SELECT k.constraint_name,k.table_name,k.column_name,k.referenced_table_name,k.referenced_column_name,k.ordinal_position
FROM information_schema.key_column_usage k
WHERE k.table_schema=DATABASE() AND k.referenced_table_name IS NOT NULL
ORDER BY k.table_name,k.constraint_name,k.ordinal_position;
'@
$fkRows = Invoke-MySql -Sql $fkMetadataSql
$fkGroups = @{}
foreach ($row in $fkRows) {
    $parts = $row -split "`t"
    if ($parts.Count -lt 6) { continue }
    $key = "$($parts[1]).$($parts[0])"
    if (-not $fkGroups.ContainsKey($key)) { $fkGroups[$key] = @() }
    $fkGroups[$key] += [pscustomobject]@{ child=$parts[1]; childColumn=$parts[2]; parent=$parts[3]; parentColumn=$parts[4]; ordinal=[int]$parts[5] }
}

$orphanQueries = foreach ($key in $fkGroups.Keys | Sort-Object) {
    $columns = @($fkGroups[$key] | Sort-Object ordinal)
    $child = $columns[0].child
    $parent = $columns[0].parent
    $join = ($columns | ForEach-Object { "c.``$($_.childColumn)``=p.``$($_.parentColumn)``" }) -join ' AND '
    $nonnull = ($columns | ForEach-Object { "c.``$($_.childColumn)`` IS NOT NULL" }) -join ' AND '
    "SELECT '$key',COUNT(*) FROM ``$child`` c LEFT JOIN ``$parent`` p ON $join WHERE $nonnull AND p.``$($columns[0].parentColumn)`` IS NULL"
}
$orphanCounts = @()
if ($orphanQueries.Count -gt 0) {
    foreach ($row in Invoke-MySql -Sql (($orphanQueries -join ' UNION ALL ') + ';')) {
        $parts = $row -split "`t", 2
        if ($parts.Count -eq 2 -and [int]$parts[1] -gt 0) { $orphanCounts += "$($parts[0]):$($parts[1])" }
    }
}

$dispositions = @{}
$inventory | Group-Object disposition | ForEach-Object { $dispositions[$_.Name] = $_.Count }
$passed = $inventorySummary.form_items -eq 617 `
    -and $inventorySummary.data_empty -eq 0 `
    -and $inventorySummary.unresolved -eq 0 `
    -and $zeroTables.Count -eq 0 `
    -and $orphanCounts.Count -eq 0

$result = [pscustomobject]@{
    package = 'CGC-COMPLETE-PROJECT'
    version = 2
    database = $Database
    passed = $passed
    form_items = $inventorySummary.form_items
    bound_fields = $inventorySummary.bound_fields
    api_readback = $inventorySummary.api_readback
    dispositions = $dispositions
    target_tables = $targetTables.Count
    zero_target_tables = $zeroTables
    foreign_keys = $fkGroups.Count
    orphan_foreign_keys = $orphanCounts
}
$result | ConvertTo-Json -Depth 5
if (-not $passed) { exit 1 }
