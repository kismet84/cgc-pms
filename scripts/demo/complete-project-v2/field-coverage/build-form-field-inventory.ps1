param(
    [string]$Database = 'cgc_pms_demo_v2',
    [string]$MySqlContainer = 'cgc-pms-mysql-dev',
    [string]$ApiBaseUrl = '',
    [string]$ApiUsername = 'admin',
    [string]$Output = (Join-Path $PSScriptRoot 'form-field-coverage.csv')
)

$ErrorActionPreference = 'Stop'
$packageRoot = Split-Path $PSScriptRoot -Parent
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $packageRoot '../../..'))
$pagesRoot = Join-Path $repoRoot 'frontend-admin/src/pages'
$mapPath = Join-Path $PSScriptRoot 'page-table-map.json'
$apiMapPath = Join-Path $PSScriptRoot 'api-readback-map.json'

$portBinding = docker port $MySqlContainer 3306/tcp 2>$null
if ($LASTEXITCODE -ne 0 -or -not (($portBinding -join "`n") -match '127\.0\.0\.1:')) {
    throw 'DEMO_COVERAGE_LOCALHOST_BINDING_REQUIRED'
}

function Invoke-MySql([string]$Sql) {
    $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("cgc-demo-coverage-{0}.sql" -f [guid]::NewGuid().ToString('N'))
    $containerFile = "/tmp/cgc-demo-coverage-$([guid]::NewGuid().ToString('N')).sql"
    try {
        [System.IO.File]::WriteAllText($tempFile, $Sql, [System.Text.UTF8Encoding]::new($false))
        docker cp $tempFile "${MySqlContainer}:$containerFile" 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'DEMO_COVERAGE_DOCKER_COPY_FAILED' }
        $result = docker exec $MySqlContainer sh -lc "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql --no-defaults -uroot --default-character-set=utf8mb4 -N -B $Database < $containerFile" 2>&1
        if ($LASTEXITCODE -ne 0) {
            $summary = (($result | Select-Object -Last 4) -join ' ') -replace '[\r\n]+', ' '
            throw "DEMO_COVERAGE_SQL_FAILED:$summary"
        }
        return @($result)
    } finally {
        docker exec $MySqlContainer rm -f $containerFile 2>$null | Out-Null
        if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
    }
}

function ConvertTo-SnakeCase([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return '' }
    $result = [regex]::Replace($Value, '([A-Z]+)([A-Z][a-z])', '$1_$2')
    $result = [regex]::Replace($result, '([a-z0-9])([A-Z])', '$1_$2')
    $result = [regex]::Replace($result, '[^a-zA-Z0-9_]+', '_')
    return $result.Trim('_').ToLowerInvariant()
}

function New-ApiReadbackSession([string]$BaseUrl, [string]$Username) {
    $baseUri = [uri]$BaseUrl
    if ($baseUri.Scheme -ne 'http' -or $baseUri.Host -notin @('127.0.0.1','localhost')) {
        throw 'DEMO_API_READBACK_LOOPBACK_REQUIRED'
    }
    $loginUrl = "$($BaseUrl.TrimEnd('/'))/auth/dev-login?username=$([uri]::EscapeDataString($Username))"
    $response = Invoke-WebRequest -Uri $loginUrl -UseBasicParsing -TimeoutSec 10
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $origin = [uri]$baseUri.GetLeftPart([System.UriPartial]::Authority)
    foreach ($piece in (@($response.Headers['Set-Cookie']) -split ', (?=(?:XSRF-TOKEN|access_token|refresh_token)=)')) {
        $pair = ($piece -split ';', 2)[0]
        $name, $value = $pair -split '=', 2
        $path = if ($name -eq 'access_token') { '/api' } elseif ($name -eq 'refresh_token') { '/api/auth/refresh' } else { '/' }
        $session.Cookies.Add($origin, [System.Net.Cookie]::new($name, $value, $path))
    }
    return $session
}

function Find-JsonPropertyValues($Value, [string]$PropertyName) {
    $found = @()
    if ($null -eq $Value) { return $found }
    if ($Value -is [pscustomobject]) {
        foreach ($property in $Value.PSObject.Properties) {
            if ($property.Name -ceq $PropertyName) { $found += $property.Value }
            $found += @(Find-JsonPropertyValues -Value $property.Value -PropertyName $PropertyName)
        }
    } elseif ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string]) {
        foreach ($item in $Value) { $found += @(Find-JsonPropertyValues -Value $item -PropertyName $PropertyName) }
    }
    return $found
}

$pageTableMap = Get-Content -LiteralPath $mapPath -Raw | ConvertFrom-Json -AsHashtable
$columnRows = Invoke-MySql -Sql "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position;"
$columnsByTable = @{}
foreach ($row in $columnRows) {
    $parts = $row -split "`t", 2
    if ($parts.Count -ne 2) { continue }
    if (-not $columnsByTable.ContainsKey($parts[0])) {
        $columnsByTable[$parts[0]] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    }
    [void]$columnsByTable[$parts[0]].Add($parts[1])
}

$demoIdStart = 520000000000000000L
$demoIdEnd = 520000000000009999L
function Get-DemoScopeSql([string]$Table, [string]$Alias = '') {
    if (-not $columnsByTable.ContainsKey($Table)) { throw "DEMO_COVERAGE_TABLE_NOT_FOUND:$Table" }
    $prefix = if ($Alias) { "$Alias." } else { '' }
    $columns = $columnsByTable[$Table]
    $referenceScope = switch ($Table) {
        'cost_subject' { "${prefix}``id`` IN (SELECT cost_subject_id FROM cost_item WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND cost_subject_id IS NOT NULL)"; break }
        'md_material_category' { "${prefix}``id`` IN (SELECT category_id FROM md_material WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND category_id IS NOT NULL)"; break }
        'org_company' { "${prefix}``id`` IN (SELECT org_id FROM pm_project WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND org_id IS NOT NULL)"; break }
        'org_department' { "${prefix}``id`` IN (SELECT department_id FROM org_position WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd AND department_id IS NOT NULL)"; break }
        'sys_dict_type' { "${prefix}``tenant_id``=0 AND ${prefix}``dict_code`` IN ('project_type','project_status','approval_status','contract_type','contract_status','partner_type','cost_type','cost_source_type','cost_status')"; break }
        'sys_dict_data' { "${prefix}``tenant_id``=0 AND ${prefix}``dict_type_id`` IN (SELECT id FROM sys_dict_type WHERE tenant_id=0 AND dict_code IN ('project_type','project_status','approval_status','contract_type','contract_status','partner_type','cost_type','cost_source_type','cost_status'))"; break }
        'sys_role' { "${prefix}``id`` IN (SELECT role_id FROM sys_user_role WHERE tenant_id=0 AND user_id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        'sys_role_menu' { "${prefix}``role_id`` IN (SELECT role_id FROM sys_user_role WHERE tenant_id=0 AND user_id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        'sys_menu' { "${prefix}``id`` IN (SELECT menu_id FROM sys_role_menu WHERE role_id IN (SELECT role_id FROM sys_user_role WHERE tenant_id=0 AND user_id BETWEEN $demoIdStart AND $demoIdEnd))"; break }
        'wf_template' { "${prefix}``id`` IN (SELECT template_id FROM wf_instance WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        'wf_template_node' { "${prefix}``template_id`` IN (SELECT template_id FROM wf_instance WHERE tenant_id=0 AND id BETWEEN $demoIdStart AND $demoIdEnd)"; break }
        default { $null }
    }
    if ($referenceScope) { return $referenceScope }
    $conditions = @()
    if ($columns.Contains('tenant_id')) { $conditions += "${prefix}``tenant_id``=0" }
    if ($columns.Contains('id')) {
        $conditions += "${prefix}``id`` BETWEEN $demoIdStart AND $demoIdEnd"
    } elseif ($columns.Contains('project_id')) {
        $conditions += "${prefix}``project_id`` BETWEEN $demoIdStart AND $demoIdEnd"
    } else {
        $ownedIdColumns = @($columns | Where-Object { $_ -match '_id$' })
        if ($ownedIdColumns.Count -eq 0) { throw "DEMO_COVERAGE_SCOPE_UNSUPPORTED:$Table" }
        $conditions += '(' + (($ownedIdColumns | ForEach-Object { "${prefix}``$_`` BETWEEN $demoIdStart AND $demoIdEnd" }) -join ' OR ') + ')'
    }
    return $conditions -join ' AND '
}

$inventory = @()
Get-ChildItem -LiteralPath $pagesRoot -Recurse -Filter '*.vue' | Sort-Object FullName | ForEach-Object {
    $file = $_.FullName
    $relative = $file.Substring($pagesRoot.Length + 1).Replace('\', '/')
    $area = ($relative -split '/')[0]
    $raw = [System.IO.File]::ReadAllText($file)
    $apiModules = @([regex]::Matches($raw, "@/api/modules/([a-zA-Z0-9_-]+)") | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    $targetTables = if ($pageTableMap.ContainsKey($area)) { @($pageTableMap[$area]) } else { @() }

    foreach ($match in [regex]::Matches($raw, '(?is)<a-form-item\b(?<attrs>[^>]*)>')) {
        $attrs = $match.Groups['attrs'].Value
        $bodyStart = $match.Index + $match.Length
        $closeIndex = $raw.IndexOf('</a-form-item>', $bodyStart, [System.StringComparison]::OrdinalIgnoreCase)
        if ($closeIndex -lt 0) { $closeIndex = [Math]::Min($raw.Length, $bodyStart + 4000) }
        $bodyLength = [Math]::Min(4000, [Math]::Max(0, $closeIndex - $bodyStart))
        $body = $raw.Substring($bodyStart, $bodyLength)
        $label = ([regex]::Match($attrs, '\blabel\s*=\s*["'']([^"'']+)["'']')).Groups[1].Value
        $name = ([regex]::Match($attrs, '\bname\s*=\s*["'']([^"'']+)["'']')).Groups[1].Value
        $dynamicName = ([regex]::Match($attrs, ':name\s*=\s*["'']([^"'']+)["'']')).Groups[1].Value
        $model = ([regex]::Match($body, 'v-model(?::[a-zA-Z-]+)?\s*=\s*["'']([^"'']+)["'']')).Groups[1].Value
        $field = $name
        if (-not $field -and $model) {
            $field = ($model -split '\.')[-1]
            $field = $field -replace '\[.*$', ''
        }
        $snake = ConvertTo-SnakeCase $field
        $candidateTables = @()
        foreach ($table in $targetTables) {
            if ($columnsByTable.ContainsKey($table) -and $snake -and $columnsByTable[$table].Contains($snake)) {
                $candidateTables += $table
            }
        }
        $line = 1 + ([regex]::Matches($raw.Substring(0, $match.Index), "`n")).Count
        $disposition = if (-not $field) { 'NO_BINDING' } elseif ($candidateTables.Count -gt 0) { 'AUTO_COLUMN' } else { 'UNMAPPED' }
        $inventory += [pscustomobject]@{
            area = $area
            file = $relative
            line = $line
            label = $label
            field = $field
            dynamic_name = $dynamicName
            column = $snake
            declared_required = [bool]($attrs -match '\brequired\b')
            named = [bool]$name
            api_modules = ($apiModules -join ';')
            target_tables = ($targetTables -join ';')
            candidate_tables = (($candidateTables | Sort-Object -Unique) -join ';')
            disposition = $disposition
            candidate_nonempty_rows = 0
            scenario = ''
            evidence_key = ''
            note = ''
        }
    }
}

$pairSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($row in $inventory | Where-Object { $_.disposition -eq 'AUTO_COLUMN' }) {
    foreach ($table in @($row.candidate_tables -split ';' | Where-Object { $_ })) {
        [void]$pairSet.Add("$table.$($row.column)")
    }
}
$valueCountByPair = @{}
if ($pairSet.Count -gt 0) {
    $valueQueries = foreach ($pair in $pairSet | Sort-Object) {
        $table, $column = $pair -split '\.', 2
        $scope = Get-DemoScopeSql -Table $table
        "SELECT '$pair',COALESCE(SUM(CASE WHEN ``$column`` IS NOT NULL AND TRIM(CAST(``$column`` AS CHAR))<>'' THEN 1 ELSE 0 END),0) FROM ``$table`` WHERE $scope"
    }
    foreach ($valueRow in Invoke-MySql -Sql (($valueQueries -join ' UNION ALL ') + ';')) {
        $parts = $valueRow -split "`t", 2
        if ($parts.Count -eq 2) { $valueCountByPair[$parts[0]] = [int]$parts[1] }
    }
}

$explicitCommandFields = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@(
    'bid-cost/index.vue|selectedProjectId',
    'cost-subject/index.vue|allocationBasis',
    'cost-subject/index.vue|accountingPeriod',
    'cost/ledger.vue|allocationMonth',
    'finance-operations/index.vue|reversedAt',
    'financial-close/index.vue|debitAccountName',
    'financial-close/index.vue|creditAccountName'
) | ForEach-Object { [void]$explicitCommandFields.Add($_) }
$explicitCompositeControls = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
@('project-closeout/index.vue|质保金应收') | ForEach-Object { [void]$explicitCompositeControls.Add($_) }

foreach ($row in $inventory) {
    if ($explicitCompositeControls.Contains("$($row.file)|$($row.label)")) {
        $row.disposition = 'COMPOSITE_CONTROL'
        $row.scenario = 'SEEDED_REFERENCE'
        $row.evidence_key = 'api:project-closeouts/overview#receivables'
        $row.note = '只读组合选择器；值由应收账款 API 选项提供。'
        continue
    }
    if ($row.disposition -eq 'AUTO_COLUMN') {
        $evidence = @()
        $nonempty = 0
        foreach ($table in @($row.candidate_tables -split ';' | Where-Object { $_ })) {
            $key = "$table.$($row.column)"
            $count = if ($valueCountByPair.ContainsKey($key)) { [int]$valueCountByPair[$key] } else { 0 }
            $nonempty += $count
            if ($count -gt 0) { $evidence += "${key}:$count" }
        }
        $row.candidate_nonempty_rows = $nonempty
        $row.disposition = if ($nonempty -gt 0) { 'DATA_VALUE' } else { 'DATA_EMPTY' }
        $row.scenario = if ($nonempty -gt 0) { 'SEEDED' } else { 'NEEDS_SEED_OR_CLASSIFICATION' }
        $row.evidence_key = ($evidence -join ';')
        if ($nonempty -eq 0) { $row.note = '候选列存在，但当前演示库无非空值。' }
        continue
    }

    if ($row.disposition -eq 'NO_BINDING') {
        if ($row.label -match '附件|证据|凭证') {
            $row.disposition = 'FILE_UPLOAD'
            $row.scenario = 'USER_ENTERED'
            $row.evidence_key = 'sys_file:runtime-upload'
            $row.note = '独立文件组件；文件元数据不写入当前表单模型。'
        } else {
            $row.disposition = 'COMPOSITE_CONTROL'
            $row.scenario = 'SEEDED_REFERENCE'
            $row.evidence_key = 'target-table:nonempty'
            $row.note = '组合选择器或展示控件；值由内部子组件或关联对象提供。'
        }
        continue
    }

    if ($row.field -match '(?i)password|callbackSecret') {
        $row.disposition = 'SENSITIVE_INPUT'
        $row.scenario = 'USER_ENTERED'
        $row.evidence_key = 'security:no-fixed-secret'
        $row.note = '敏感输入禁止写入固定演示数据。'
    } elseif ($row.field -eq 'uploadFileList' -or $row.label -match '附件|证据|凭证') {
        $row.disposition = 'FILE_UPLOAD'
        $row.scenario = 'USER_ENTERED'
        $row.evidence_key = 'sys_file:runtime-upload'
        $row.note = '文件由统一附件接口独立持久化。'
    } elseif ($row.field -match '^(remember)$') {
        $row.disposition = 'CLIENT_STATE'
        $row.scenario = 'USER_PREFERENCE'
        $row.evidence_key = 'browser-storage'
        $row.note = '仅浏览器本地状态，不属于业务表字段。'
    } elseif ($row.field -match '^(theme|sidebarCollapsed|tableDensity|notificationEnabled)$') {
        $row.disposition = 'JSON_VALUE'
        $row.scenario = 'SEEDED'
        $row.evidence_key = 'sys_user_preference.preferences'
        $row.note = '字段持久化在用户偏好 JSON 中。'
    } elseif ($explicitCommandFields.Contains("$($row.file)|$($row.field)") -or $row.field -match '^(transferTargetUserId|transferComment|addSignUserIds|addSignComment|decision|comment|reason|result|idempotencyKey|externalTxnNo|reversalType|payRecordId|sourceId|targetId|bidCostId|ownerSettlementId|factId|inspectionId|deleteTargetId|taskIds|transferReason|transferQuantity)$') {
        $row.disposition = 'COMMAND_INPUT'
        $row.scenario = 'USER_ENTERED'
        $row.evidence_key = if ($row.api_modules) { "api:$($row.api_modules)" } else { 'api:command' }
        $row.note = '操作命令参数；成功执行后转化为状态、记录或关联行，不按同名列持久化。'
    } else {
        $row.disposition = 'UNVERIFIED_API_ALIAS'
        $row.scenario = 'NEEDS_API_READBACK'
        $row.evidence_key = ''
        $row.note = '没有 API 回读证据；不得计入字段有效覆盖。'
    }
}

if ($ApiBaseUrl) {
    $apiChecks = Get-Content -LiteralPath $apiMapPath -Raw | ConvertFrom-Json
    $checksByKey = @{}
    foreach ($check in $apiChecks) { $checksByKey["$($check.file)|$($check.label)|$($check.field)"] = $check }
    $apiSession = New-ApiReadbackSession -BaseUrl $ApiBaseUrl -Username $ApiUsername
    $apiResponseCache = @{}
    foreach ($row in $inventory | Where-Object { $_.disposition -eq 'UNVERIFIED_API_ALIAS' }) {
        $key = "$($row.file)|$($row.label)|$($row.field)"
        if (-not $checksByKey.ContainsKey($key)) { continue }
        $check = $checksByKey[$key]
        if (-not $apiResponseCache.ContainsKey($check.endpoint)) {
            $url = "$($ApiBaseUrl.TrimEnd('/'))$($check.endpoint)"
            $response = Invoke-RestMethod -Uri $url -WebSession $apiSession -TimeoutSec 15
            if ($response.code -ne '0') { throw "DEMO_API_READBACK_FAILED:$($check.endpoint):$($response.code)" }
            $apiResponseCache[$check.endpoint] = $response.data
        }
        $values = @(Find-JsonPropertyValues -Value $apiResponseCache[$check.endpoint] -PropertyName $check.response_property |
            Where-Object { $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_) })
        if ($check.expected_regex) { $values = @($values | Where-Object { [string]$_ -match $check.expected_regex }) }
        if ($values.Count -eq 0) { continue }
        $row.disposition = 'API_READBACK'
        $row.scenario = 'SEEDED'
        $row.evidence_key = "api:$($check.endpoint)#$($check.response_property)"
        $row.note = '已从隔离演示库对应 API 响应回读非空值。'
    }
}

$outputDirectory = Split-Path $Output -Parent
if (-not (Test-Path -LiteralPath $outputDirectory)) { New-Item -ItemType Directory -Path $outputDirectory | Out-Null }
$inventory | Export-Csv -LiteralPath $Output -NoTypeInformation -Encoding utf8NoBOM

$summary = [pscustomobject]@{
    database = $Database
    form_items = $inventory.Count
    bound_fields = @($inventory | Where-Object { $_.field }).Count
    data_value = @($inventory | Where-Object { $_.disposition -eq 'DATA_VALUE' }).Count
    data_empty = @($inventory | Where-Object { $_.disposition -eq 'DATA_EMPTY' }).Count
    api_readback = @($inventory | Where-Object { $_.disposition -eq 'API_READBACK' }).Count
    unresolved = @($inventory | Where-Object { $_.disposition -in @('AUTO_COLUMN','UNMAPPED','NO_BINDING','UNVERIFIED_API_ALIAS') }).Count
    output = [System.IO.Path]::GetFullPath($Output)
}
$summary | ConvertTo-Json
