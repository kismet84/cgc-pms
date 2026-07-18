param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('dev', 'test', 'demo')]
    [string]$Environment,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$Database,

    [string]$MySqlContainer = 'cgc-pms-mysql-dev'
)

$ErrorActionPreference = 'Stop'
$packageRoot = $PSScriptRoot
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $packageRoot '../../..'))
$marker = Join-Path $repoRoot '.codex-autopilot/ALLOW_TEST_DATA_RESET'
if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) {
    throw 'DEMO_LOAD_RESET_MARKER_REQUIRED'
}

$portBinding = docker port $MySqlContainer 3306/tcp 2>$null
if ($LASTEXITCODE -ne 0 -or -not (($portBinding -join "`n") -match '127\.0\.0\.1:')) {
    throw 'DEMO_LOAD_LOCALHOST_BINDING_REQUIRED'
}

function Invoke-MySql([string]$Sql, [switch]$Capture) {
    $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("cgc-demo-{0}.sql" -f [guid]::NewGuid().ToString('N'))
    $containerFile = "/tmp/cgc-demo-$([guid]::NewGuid().ToString('N')).sql"
    try {
        [System.IO.File]::WriteAllText($tempFile, $Sql, [System.Text.UTF8Encoding]::new($false))
        docker cp $tempFile "${MySqlContainer}:$containerFile" 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'DEMO_LOAD_DOCKER_COPY_FAILED' }
        $result = docker exec $MySqlContainer sh -lc "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql --no-defaults -uroot --default-character-set=utf8mb4 -N $Database < $containerFile" 2>&1
        if ($LASTEXITCODE -ne 0) {
            $summary = (($result | Select-Object -Last 4) -join ' ') -replace '[\r\n]+', ' '
            throw "DEMO_LOAD_SQL_FAILED:$summary"
        }
        if ($Capture) { return @($result) }
    } finally {
        docker exec $MySqlContainer rm -f $containerFile 2>$null | Out-Null
        if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
    }
}

$preflightSql = @'
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_bootstrap_state';
SELECT COUNT(*) FROM sys_bootstrap_state WHERE bootstrap_key='PLATFORM_ADMIN' AND status='COMPLETED';
SELECT COUNT(*) FROM sys_role WHERE tenant_id=0 AND role_code='SUPER_ADMIN' AND status='ENABLE' AND deleted_flag=0;
'@
$preflight = Invoke-MySql -Sql $preflightSql -Capture
if ($preflight.Count -lt 3 -or $preflight[0] -ne '1' -or $preflight[1] -ne '1' -or $preflight[2] -ne '1') {
    throw 'DEMO_LOAD_PLATFORM_BOOTSTRAP_REQUIRED'
}

$stages = @(
    [pscustomobject]@{ Id = 'CORE'; Files = @('scripts/demo/complete-project-v1/sql/10-core.sql') },
    [pscustomobject]@{ Id = 'PROCUREMENT'; Files = @('scripts/demo/complete-project-v1/sql/20-procurement.sql') },
    [pscustomobject]@{ Id = 'COMMERCIAL'; Files = @('scripts/demo/complete-project-v1/sql/30-commercial.sql') },
    [pscustomobject]@{ Id = 'GOVERNANCE'; Files = @('scripts/demo/complete-project-v1/sql/40-governance.sql') }
)

foreach ($stage in $stages) {
    $key = "DEMO_CGC_V1_$($stage.Id)"
    $status = Invoke-MySql -Sql "SELECT COALESCE(MAX(status),'') FROM sys_bootstrap_state WHERE bootstrap_key='$key';" -Capture
    if (($status | Select-Object -First 1) -eq 'COMPLETED') {
        continue
    }

    $body = [System.Text.StringBuilder]::new()
    [void]$body.AppendLine('START TRANSACTION;')
    [void]$body.AppendLine("INSERT INTO sys_bootstrap_state (bootstrap_key,bootstrap_version,status,completed_at) VALUES ('$key',1,'PENDING',NULL) ON DUPLICATE KEY UPDATE bootstrap_version=VALUES(bootstrap_version);")
    foreach ($relativePath in $stage.Files) {
        $source = Join-Path $repoRoot $relativePath
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "DEMO_LOAD_SOURCE_MISSING:$relativePath" }
        [void]$body.AppendLine([System.IO.File]::ReadAllText($source))
    }
    [void]$body.AppendLine("UPDATE sys_bootstrap_state SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE bootstrap_key='$key' AND bootstrap_version=1;")
    [void]$body.AppendLine('COMMIT;')
    Invoke-MySql -Sql $body.ToString()
}

& (Join-Path $packageRoot 'verify.ps1') -Environment $Environment -Database $Database -MySqlContainer $MySqlContainer
