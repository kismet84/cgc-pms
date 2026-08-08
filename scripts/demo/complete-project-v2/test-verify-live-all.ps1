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
$systemFixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/80-variation-closeout-system.sql'))
if (-not $systemFixture.Contains('ON DUPLICATE KEY UPDATE', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_USER_PREFERENCE_FIXTURE_NOT_REPEATABLE'
}
$roleFixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/150-role-test-accounts.sql'))
if ([regex]::Matches($roleFixture, "'ui26\.[a-z0-9]+01'").Count -lt 24) {
    throw 'LIVE_EVIDENCE_ROLE_SWITCHER_FIXTURE_INCOMPLETE'
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
$costFixture = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/180-cost-breakdown-data.sql'))
if (-not $costFixture.Contains('DELETE FROM cost_summary', [StringComparison]::Ordinal) `
    -or $costFixture.Contains("'2026-07-18',900030", [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_COST_FIXTURE_USES_RETIRED_SUBJECT'
}
$verifySource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'verify.ps1'))
if (-not $verifySource.Contains("'settlement_action_permission',COUNT(DISTINCT perms)", [StringComparison]::Ordinal) `
    -or -not $verifySource.Contains('$metrics.cost_breakdown_children -eq 3', [StringComparison]::Ordinal)) {
    throw 'LIVE_EVIDENCE_VERIFIER_CONTRACT_INVALID'
}
$loadSource = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'load.ps1'))
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
