$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'verify-live-all.ps1')

function Assert-ThrowsCode([scriptblock]$Action, [string]$Expected) {
    try {
        & $Action
        throw "EXPECTED_FAILURE_NOT_THROWN:$Expected"
    } catch {
        if (-not $_.Exception.Message.StartsWith($Expected, [StringComparison]::Ordinal)) {
            throw "UNEXPECTED_FAILURE:$($_.Exception.Message)"
        }
    }
}

Assert-ThrowsCode { Assert-LoopbackUri 'https://example.com' 'frontend' } 'LIVE_EVIDENCE_LOOPBACK_REQUIRED'
Assert-ThrowsCode { Assert-LoopbackUri 'file:///tmp/demo' 'backend' } 'LIVE_EVIDENCE_LOOPBACK_REQUIRED'
Assert-ThrowsCode { Assert-DemoDatabase 'cgc_pms_dev' } 'LIVE_EVIDENCE_DEDICATED_DATABASE_REQUIRED'
Assert-ThrowsCode { Get-BackendProxyTarget 'http://127.0.0.1:8080/wrong' } 'LIVE_EVIDENCE_BACKEND_URL_INVALID'
Assert-ThrowsCode { Get-DatabaseNameFromJdbcUrl 'jdbc:postgresql://127.0.0.1/demo' } 'LIVE_EVIDENCE_BACKEND_DATASOURCE_INVALID'
Assert-ThrowsCode { Assert-ResetMarker (Join-Path $PSScriptRoot 'missing-repository') } 'LIVE_EVIDENCE_RESET_MARKER_REQUIRED'
Assert-ThrowsCode { Assert-RequiredUsersCovered @('admin', 'demo.missing') "'admin'" } 'LIVE_EVIDENCE_USER_FIXTURE_MISSING'

$source = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'verify-live-all.ps1'))
$preflightIndex = $source.IndexOf('$identity = Assert-LivePreflight', [StringComparison]::Ordinal)
$loadIndex = $source.IndexOf('& pwsh -NoProfile -File $loadScript', [StringComparison]::Ordinal)
if ($preflightIndex -lt 0 -or $loadIndex -lt 0 -or $preflightIndex -ge $loadIndex) {
    throw 'LIVE_EVIDENCE_PREFLIGHT_MUST_PRECEDE_LOAD'
}
$identityIndex = $source.IndexOf('$identity = Assert-LiveIdentity', [StringComparison]::Ordinal)
if ($identityIndex -le $loadIndex `
    -or -not $source.Contains('Assert-BackendContainerBinding -Container $BackendContainerName', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_WRITE_FREE_PREFLIGHT_INVALID'
}
if (-not $source.Contains("[string]`$FrontendUrl = 'http://127.0.0.1:4173'", [StringComparison]::Ordinal) `
    -or -not $source.Contains('Start-LiveFrontend', [StringComparison]::Ordinal) `
    -or -not $source.Contains("SetEnvironmentVariable('VITE_API_TARGET', `$BackendProxyTarget", [StringComparison]::Ordinal) `
    -or -not $source.Contains("@(`$viteEntry, '--host'", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_ISOLATED_FRONTEND_CONTRACT_INVALID'
}
if (-not $source.Contains("node_modules/@playwright/test/cli.js", [StringComparison]::Ordinal) `
    -or $source.Contains('& pnpm exec playwright', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_PLAYWRIGHT_CLI_CONTRACT_INVALID'
}
if ([regex]::Matches($source, "V2_LIVE_[A-Z]+ = '1'").Count -ne 9) {
    throw 'LIVE_EVIDENCE_FLAG_COUNT_INVALID'
}
if (-not $source.Contains('Assert-CleanWorkingTree $repoRoot', [StringComparison]::Ordinal) `
    -or -not $source.Contains('local-evidence/live-e2e/live-evidence-', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_SHA_BINDING_CONTRACT_INVALID'
}
if (-not $source.Contains("PSObject.Properties['suites']", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_REPORTER_LEAF_SUITE_UNSAFE'
}
$frontendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../frontend-admin-v2'))
foreach ($spec in Get-LiveSpecs $frontendRoot) {
    $specSource = [IO.File]::ReadAllText((Join-Path $frontendRoot "e2e/$spec"))
    if (-not $specSource.Contains("from './live-test'", [StringComparison]::Ordinal)) {
        throw "LIVE_EVIDENCE_STREAM_CLIENT_FIXTURE_MISSING:$spec"
    }
    if ($specSource.Contains('[data-value', [StringComparison]::Ordinal) `
        -or $specSource.Contains("toHaveAttribute('aria-disabled', 'false')", [StringComparison]::Ordinal) `
        -or $specSource.Contains("name: '日报状态：全部状态'", [StringComparison]::Ordinal)) {
        throw "LIVE_EVIDENCE_NATIVE_SELECT_CONTRACT_STALE:$spec"
    }
}
$liveTestFixture = [IO.File]::ReadAllText((Join-Path $frontendRoot 'e2e/live-test.ts'))
$shellLiveSpec = [IO.File]::ReadAllText((Join-Path $frontendRoot 'e2e/shell-live.spec.ts'))
if (-not $liveTestFixture.Contains('class LiveTestEventSource', [StringComparison]::Ordinal) `
    -or -not $shellLiveSpec.Contains("streamTest('opens and closes the real notification", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_STREAM_ISOLATION_CONTRACT_INVALID'
}
$dashboardLiveSpec = [IO.File]::ReadAllText((Join-Path $frontendRoot 'e2e/m2-dashboard-live.spec.ts'))
if (-not $dashboardLiveSpec.Contains('const rolePage = await context.newPage()', [StringComparison]::Ordinal) `
    -or -not $dashboardLiveSpec.Contains('await rolePage.close()', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_ROLE_PAGE_ISOLATION_CONTRACT_INVALID'
}
$systemFixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/80-variation-closeout-system.sql'))
if (-not $systemFixture.Contains('ON DUPLICATE KEY UPDATE', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_USER_PREFERENCE_FIXTURE_NOT_REPEATABLE'
}
$roleFixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/150-role-test-accounts.sql'))
foreach ($roleAccount in @('pm','bm','cost','pur','prod','chief','fin','mgmt','staff','gm','mat')) {
    if ([regex]::Matches($roleFixture, "'ui26\.$roleAccount" + "01'").Count -lt 2) {
        throw "LIVE_EVIDENCE_ROLE_SWITCHER_FIXTURE_INCOMPLETE:$roleAccount"
    }
}
$businessCodeFixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/190-standardize-business-codes.sql'))
if ([regex]::Matches($businessCodeFixture, "SET t\.[a-z_]+=CONCAT\('TMP',t\.id\)").Count -ne 20) {
    throw 'LIVE_EVIDENCE_BUSINESS_CODE_FIXTURE_NOT_TWO_PHASE'
}
if ([regex]::Matches($businessCodeFixture, 'WHERE NOT \(t\.record_code <=> n\.new_code\)').Count -ne 2) {
    throw 'LIVE_EVIDENCE_PAY_RECORD_NULL_CODE_NOT_STANDARDIZED'
}
$m3Fixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/210-m3-domain-permission-data.sql'))
foreach ($status in @('PREPARING', 'COMPLETION', 'WARRANTY')) {
    if (-not $m3Fixture.Contains("THEN '$status'", [StringComparison]::Ordinal)) {
        throw "LIVE_EVIDENCE_PROJECT_STATUS_FIXTURE_MISSING:$status"
    }
}
foreach ($queryOnlyAccount in @('demo.schedule.query', 'demo.member-readonly')) {
    if (-not $m3Fixture.Contains("u.username='$queryOnlyAccount'", [StringComparison]::Ordinal)) {
        throw "LIVE_EVIDENCE_QUERY_ONLY_ROLE_RESET_MISSING:$queryOnlyAccount"
    }
}
if ([regex]::Matches($m3Fixture, 'DELETE ur FROM sys_user_role ur').Count -lt 2) {
    throw 'LIVE_EVIDENCE_QUERY_ONLY_ROLE_RESET_INCOMPLETE'
}
$costFixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/180-cost-breakdown-data.sql'))
if (-not $costFixture.Contains('DELETE FROM cost_summary', [StringComparison]::Ordinal) `
    -or $costFixture.Contains("'2026-07-18',900030", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_COST_FIXTURE_USES_RETIRED_SUBJECT'
}
$verifySource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'verify.ps1'))
if (-not $verifySource.Contains("'settlement_action_permission',COUNT(DISTINCT perms)", [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains("'settlement_project_manager_permission'", [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains('$metrics.settlement_fixture_grant_overflow -eq 0', [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains("template_code='M89-SETTLEMENT'", [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains("COALESCE(JSON_UNQUOTE(JSON_EXTRACT(n.approver_config,'$.type')),'')='ROLE'", [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains('$metrics.project_manager_variation_permissions -eq 1', [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains('$metrics.role_workflow_action_permissions -eq 36', [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains("'m3_daily_self_account'", [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains('$metrics.m3_daily_self_account -eq 1', [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains("'m3_query_only_role_leak'", [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains('$metrics.m3_query_only_role_leak -eq 0', [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains('$metrics.cost_breakdown_children -eq 3', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_VERIFIER_CONTRACT_INVALID'
}
$loadSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'load.ps1'))
$fixtureVersions = @(
    "Id = 'ROLE_TEST_ACCOUNTS'; Version = 6",
    "Id = 'ROLE_WORKFLOW_STATUS_DATA'; Version = 4",
    "Id = 'SETTLEMENT_SOURCE_DATA'; Version = 7"
)
foreach ($fixtureVersion in $fixtureVersions) {
    if (-not $loadSource.Contains($fixtureVersion, [StringComparison]::Ordinal)) {
        throw "LIVE_EVIDENCE_FIXTURE_VERSION_INVALID:$fixtureVersion"
    }
}
if (-not $loadSource.Contains("Id = 'ROLE_DASHBOARD_DATA'; Version = 4; AlwaysApply = `$true", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_RELATIVE_DASHBOARD_FIXTURE_MUST_REPLAY'
}
$roleFixtureSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/150-role-test-accounts.sql'))
foreach ($canonicalBinding in @(
    "username='demo.business' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='PROJECT_ACCOUNTANT'",
    "username='demo.purchase' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='PROCUREMENT_LEAD'",
    "username='demo.production' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='CONSTRUCTION_LEAD'",
    "username='demo.chief' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='TECHNICAL_LEAD'",
    "username='demo.finance' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='COMPANY_FINANCE'",
    "username='ui26.bm01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='SAFETY_LEAD'",
    "username='ui26.staff01' AND deleted_flag=0),(SELECT id FROM sys_role WHERE tenant_id=0 AND role_code='EMPLOYEE'"
)) {
    if (-not $roleFixtureSource.Contains($canonicalBinding, [StringComparison]::Ordinal)) {
        throw "LIVE_EVIDENCE_CANONICAL_ROLE_FIXTURE_MISSING:$canonicalBinding"
    }
}
if (-not $roleFixtureSource.Contains("520000000000008657,0,520000000000009002,(SELECT id FROM sys_user WHERE tenant_id=0 AND username='ui26.staff01'", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_DAILY_SELF_PROJECT_MEMBER_MISSING'
}
if ($roleFixtureSource.Contains('INSERT IGNORE INTO sys_role_menu', [StringComparison]::Ordinal) `
    -or -not $roleFixtureSource.Contains("SET status='DISABLE'", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_ROLE_FIXTURE_M89_CONTRACT_INVALID'
}
$workflowFixtureSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/200-role-workflow-status-data.sql'))
if ($workflowFixtureSource.Contains('INSERT IGNORE INTO sys_role_menu', [StringComparison]::Ordinal) `
    -or -not $workflowFixtureSource.Contains('Mainline 89 owns the least-privilege role matrix', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_WORKFLOW_FIXTURE_M89_CONTRACT_INVALID'
}
$settlementFixtureSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/230-settlement-source-data.sql'))
if (-not $settlementFixtureSource.Contains("template_code='M89-SETTLEMENT'", [StringComparison]::Ordinal) `
    -or -not $settlementFixtureSource.Contains('m.id IN (945,607,520000000000014001,520000000000014002,520000000000014003)', [StringComparison]::Ordinal) `
    -or -not $settlementFixtureSource.Contains("JSON_OBJECT('type','ROLE','roleCode'", [StringComparison]::Ordinal) `
    -or $settlementFixtureSource.Contains("JSON_OBJECT('type','USER'", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_SETTLEMENT_FIXTURE_M89_CONTRACT_INVALID'
}
$settlementStageIndex = $loadSource.IndexOf("Id = 'SETTLEMENT_SOURCE_DATA'", [StringComparison]::Ordinal)
$standardizeStageIndex = $loadSource.IndexOf("Id = 'STANDARDIZE_BUSINESS_CODES'; Version = 4", [StringComparison]::Ordinal)
if ($settlementStageIndex -lt 0 -or $standardizeStageIndex -le $settlementStageIndex) {
    throw 'LIVE_EVIDENCE_BUSINESS_CODE_FIXTURE_MUST_RUN_LAST'
}

[pscustomobject]@{
    ok = $true
    negativePreconditions = 7
    liveFlags = 9
    isolatedRealtimeSmoke = $true
    isolatedFrontend = $true
    loadGuardedByPreflight = $true
    repeatableUserPreferenceFixture = $true
    twoPhaseBusinessCodeTables = 20
    businessCodeStageRunsLast = $true
} | ConvertTo-Json
