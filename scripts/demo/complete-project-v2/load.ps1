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
SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id AND t.tenant_id=d.tenant_id
 WHERE t.tenant_id=0 AND t.dict_code='project_type' AND d.dict_value='CONSTRUCTION' AND d.status='ENABLE';
SELECT COUNT(*) FROM sys_dict_data d JOIN sys_dict_type t ON t.id=d.dict_type_id AND t.tenant_id=d.tenant_id
 WHERE t.tenant_id=0 AND t.dict_code='partner_type' AND d.dict_value='CUSTOMER' AND d.status='ENABLE';
SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id=0 AND dict_code='cost_source_type' AND status='ENABLE';
'@
$preflight = Invoke-MySql -Sql $preflightSql -Capture
if ($preflight.Count -lt 6 -or $preflight[0] -ne '1' -or $preflight[1] -ne '1' -or $preflight[2] -ne '1') {
    throw 'DEMO_LOAD_PLATFORM_BOOTSTRAP_REQUIRED'
}
if ($preflight[3] -ne '1' -or $preflight[4] -ne '1' -or $preflight[5] -ne '1') {
    throw 'DEMO_LOAD_V216_DICTIONARY_REQUIRED'
}

$stages = @(
    [pscustomobject]@{ Id = 'CORE'; Files = @('scripts/demo/complete-project-v2/sql/10-core.sql') },
    [pscustomobject]@{ Id = 'PROCUREMENT'; Files = @('scripts/demo/complete-project-v2/sql/20-procurement.sql') },
    [pscustomobject]@{ Id = 'COMMERCIAL'; Files = @('scripts/demo/complete-project-v2/sql/30-commercial.sql') },
    [pscustomobject]@{ Id = 'GOVERNANCE'; Files = @('scripts/demo/complete-project-v2/sql/40-governance.sql') },
    [pscustomobject]@{ Id = 'FINANCE'; Files = @('scripts/demo/complete-project-v2/sql/50-finance.sql') },
    [pscustomobject]@{ Id = 'PRODUCTION_SITE'; Files = @('scripts/demo/complete-project-v2/sql/60-production-site.sql') },
    [pscustomobject]@{ Id = 'SOURCING_TECHNICAL'; Files = @('scripts/demo/complete-project-v2/sql/70-sourcing-technical.sql') },
    [pscustomobject]@{ Id = 'VARIATION_CLOSEOUT_SYSTEM'; Files = @('scripts/demo/complete-project-v2/sql/80-variation-closeout-system.sql') },
    [pscustomobject]@{ Id = 'CONTROL_CLOSURES'; Files = @('scripts/demo/complete-project-v2/sql/90-control-closures.sql') },
    [pscustomobject]@{ Id = 'FINANCE_OPERATIONS'; Files = @('scripts/demo/complete-project-v2/sql/100-finance-operations.sql') },
    [pscustomobject]@{ Id = 'INVENTORY_EXCEPTIONS'; Files = @('scripts/demo/complete-project-v2/sql/110-inventory-exceptions.sql') },
    [pscustomobject]@{ Id = 'DOCUMENT_SYSTEM'; Files = @('scripts/demo/complete-project-v2/sql/120-document-system.sql') },
    [pscustomobject]@{ Id = 'FORM_FIELD_COMPLETION'; Files = @('scripts/demo/complete-project-v2/sql/130-form-field-completion.sql') },
    [pscustomobject]@{ Id = 'DATA_QUALITY_NORMALIZATION'; Files = @('scripts/demo/complete-project-v2/sql/140-data-quality-normalization.sql') },
    [pscustomobject]@{ Id = 'ROLE_TEST_ACCOUNTS'; Files = @('scripts/demo/complete-project-v2/sql/150-role-test-accounts.sql') },
    [pscustomobject]@{ Id = 'ROLE_DASHBOARD_DATA'; Files = @('scripts/demo/complete-project-v2/sql/160-role-dashboard-data.sql') },
    [pscustomobject]@{ Id = 'DASHBOARD_RISK_LEVELS'; Files = @('scripts/demo/complete-project-v2/sql/170-dashboard-risk-levels.sql') },
    [pscustomobject]@{ Id = 'COST_BREAKDOWN_DATA'; Files = @('scripts/demo/complete-project-v2/sql/180-cost-breakdown-data.sql') },
    [pscustomobject]@{ Id = 'STANDARDIZE_BUSINESS_CODES'; Files = @('scripts/demo/complete-project-v2/sql/190-standardize-business-codes.sql') },
    [pscustomobject]@{ Id = 'ROLE_WORKFLOW_STATUS_DATA'; Files = @('scripts/demo/complete-project-v2/sql/200-role-workflow-status-data.sql') },
    [pscustomobject]@{ Id = 'M3_DOMAIN_PERMISSION_DATA'; Version = 10; AlwaysApply = $true; Files = @('scripts/demo/complete-project-v2/sql/210-m3-domain-permission-data.sql') }
)

foreach ($stage in $stages) {
    $key = "DEMO_CGC_V2_$($stage.Id)"
    $stageVersion = if ($null -ne $stage.Version) { [int]$stage.Version } else { 2 }
    $status = Invoke-MySql -Sql "SELECT COALESCE(MAX(status),'') FROM sys_bootstrap_state WHERE bootstrap_key='$key' AND bootstrap_version=$stageVersion;" -Capture
    if (($status | Select-Object -First 1) -eq 'COMPLETED' -and -not $stage.AlwaysApply) {
        continue
    }

    $body = [System.Text.StringBuilder]::new()
    [void]$body.AppendLine('START TRANSACTION;')
    [void]$body.AppendLine("INSERT INTO sys_bootstrap_state (bootstrap_key,bootstrap_version,status,completed_at) VALUES ('$key',$stageVersion,'PENDING',NULL) ON DUPLICATE KEY UPDATE bootstrap_version=VALUES(bootstrap_version),status='PENDING',completed_at=NULL;")
    foreach ($relativePath in $stage.Files) {
        $source = Join-Path $repoRoot $relativePath
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "DEMO_LOAD_SOURCE_MISSING:$relativePath" }
        [void]$body.AppendLine([System.IO.File]::ReadAllText($source))
    }
    [void]$body.AppendLine("UPDATE sys_bootstrap_state SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE bootstrap_key='$key' AND bootstrap_version=$stageVersion;")
    [void]$body.AppendLine('COMMIT;')
    Invoke-MySql -Sql $body.ToString()
}

& (Join-Path $packageRoot 'verify.ps1') -Environment $Environment -Database $Database -MySqlContainer $MySqlContainer
