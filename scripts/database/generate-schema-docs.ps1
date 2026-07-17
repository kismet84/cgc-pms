param(
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'cgc_pms' }),
    [string]$User = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }),
    [string]$Password = $env:DB_PASSWORD,
    [string]$DockerContainer,
    [string]$OutputDirectory = 'docs/database/generated'
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$output = [System.IO.Path]::GetFullPath((Join-Path $workspace $OutputDirectory))
if (-not $output.StartsWith($workspace, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "OutputDirectory 必须位于项目工作区内: $workspace"
}
$mysql = if ($DockerContainer) { $null } else { Get-Command mysql -ErrorAction Stop }
$docker = if ($DockerContainer) { Get-Command docker -ErrorAction Stop } else { $null }
if ($DockerContainer -and $Database -notmatch '^[A-Za-z0-9_]+$') {
    throw 'Docker 模式下 Database 仅允许字母、数字和下划线'
}
New-Item -ItemType Directory -Force -Path $output | Out-Null
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password

function Invoke-InfoSchemaQuery([string]$Sql) {
    if ($DockerContainer) {
        $command = 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --batch --raw --skip-column-names --default-character-set=utf8mb4 ' + $Database
        $rows = $Sql | & $docker.Source exec -i $DockerContainer sh -lc $command
    } else {
        $rows = & $mysql.Source --batch --raw --skip-column-names --default-character-set=utf8mb4 `
            -h $HostName -P $Port -u $User $Database -e $Sql
    }
    if ($LASTEXITCODE -ne 0) { throw "MySQL information_schema 查询失败，退出码 $LASTEXITCODE" }
    return @($rows)
}

try {
    $tables = Invoke-InfoSchemaQuery @"
SELECT table_name, COALESCE(table_comment,''), table_rows
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'
ORDER BY table_name;
"@
    $columns = Invoke-InfoSchemaQuery @"
SELECT table_name, ordinal_position, column_name, column_type, is_nullable,
       COALESCE(column_default,'∅'), column_key, extra, COALESCE(column_comment,'')
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'
ORDER BY table_name, ordinal_position;
"@
    $foreignKeys = Invoke-InfoSchemaQuery @"
SELECT table_name, column_name, referenced_table_name, referenced_column_name, constraint_name
FROM information_schema.key_column_usage
WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name, ordinal_position;
"@

    $dictionary = [System.Text.StringBuilder]::new()
    [void]$dictionary.AppendLine('# CGC-PMS 数据库结构字典（自动生成）')
    [void]$dictionary.AppendLine()
    [void]$dictionary.AppendLine("> 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')；来源：MySQL information_schema / 数据库 ``$Database``。请勿手工修改。")
    [void]$dictionary.AppendLine()
    [void]$dictionary.AppendLine("- 业务表数量：$($tables.Count)")
    [void]$dictionary.AppendLine("- 字段数量：$($columns.Count)")
    [void]$dictionary.AppendLine("- 外键列数量：$($foreignKeys.Count)")
    $columnGroups = $columns | Group-Object { ($_ -split "`t", 2)[0] }
    foreach ($tableLine in $tables) {
        $table = $tableLine -split "`t", 3
        [void]$dictionary.AppendLine()
        [void]$dictionary.AppendLine("## $($table[0])")
        [void]$dictionary.AppendLine()
        [void]$dictionary.AppendLine("- 表注释：$(if ($table[1]) { $table[1] } else { '未定义（需要人工确认）' })")
        [void]$dictionary.AppendLine("- information_schema 估算行数：$($table[2])")
        [void]$dictionary.AppendLine()
        [void]$dictionary.AppendLine('|序号|字段|类型|可空|默认值|键|附加属性|注释|')
        [void]$dictionary.AppendLine('|---:|---|---|---|---|---|---|---|')
        $group = $columnGroups | Where-Object Name -eq $table[0]
        foreach ($line in $group.Group) {
            $c = $line -split "`t", 9
            $comment = if ($c[8]) { $c[8] } else { '未定义（需要人工确认）' }
            [void]$dictionary.AppendLine("|$($c[1])|``$($c[2])``|``$($c[3])``|$($c[4])|``$($c[5])``|$($c[6])|$($c[7])|$comment|")
        }
    }
    Set-Content -LiteralPath (Join-Path $output 'schema-dictionary.md') -Value $dictionary.ToString() -Encoding utf8

    $relationships = [System.Text.StringBuilder]::new()
    [void]$relationships.AppendLine('# CGC-PMS 数据库 ER 关系（自动生成）')
    [void]$relationships.AppendLine()
    [void]$relationships.AppendLine("> 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')；仅展示数据库显式外键，请勿手工修改。")
    [void]$relationships.AppendLine()
    [void]$relationships.AppendLine('```mermaid')
    [void]$relationships.AppendLine('flowchart LR')
    foreach ($line in $foreignKeys) {
        $fk = $line -split "`t", 5
        $from = $fk[0] -replace '[^A-Za-z0-9_]', '_'
        $to = $fk[2] -replace '[^A-Za-z0-9_]', '_'
        $edge = '    {0}["{1}"] -->|"{2} → {3} / {4}"| {5}["{6}"]' -f `
            $from, $fk[0], $fk[1], $fk[3], $fk[4], $to, $fk[2]
        [void]$relationships.AppendLine($edge)
    }
    [void]$relationships.AppendLine('```')
    Set-Content -LiteralPath (Join-Path $output 'schema-relationships.md') -Value $relationships.ToString() -Encoding utf8
} finally {
    $env:MYSQL_PWD = $previousPassword
}

Write-Host "已生成: $output\schema-dictionary.md"
Write-Host "已生成: $output\schema-relationships.md"
