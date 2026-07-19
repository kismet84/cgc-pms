[CmdletBinding()]
param([string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)

$ErrorActionPreference = 'Stop'

function Read-RepoText([string]$RelativePath) {
  $path = Join-Path $RepoRoot $RelativePath
  if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "CI contract file is missing: $RelativePath" }
  return Get-Content -LiteralPath $path -Raw -Encoding UTF8
}

function Assert-Contains([string]$Text,[string[]]$Patterns,[string]$Name) {
  foreach ($pattern in $Patterns) {
    if (!$Text.Contains($pattern)) { throw "$Name is missing required contract text: $pattern" }
  }
}

function Assert-SetEqual([string[]]$Actual,[string[]]$Expected,[string]$Name) {
  $actualSorted = @($Actual | Sort-Object -Unique)
  $expectedSorted = @($Expected | Sort-Object -Unique)
  $missing = @($expectedSorted | Where-Object { $actualSorted -notcontains $_ })
  $unexpected = @($actualSorted | Where-Object { $expectedSorted -notcontains $_ })
  if ($missing.Count -gt 0 -or $unexpected.Count -gt 0) {
    throw "$Name differs: missing=$($missing -join ','), unexpected=$($unexpected -join ',')"
  }
}

function Get-JobBlock([string]$Workflow,[string]$JobName) {
  $match = [regex]::Match($Workflow,"(?ms)^  $([regex]::Escape($JobName)):\r?\n(?<body>.*?)(?=^  [a-z0-9][a-z0-9-]*:\r?$|\z)")
  if (!$match.Success) { throw "workflow job block is missing: $JobName" }
  return $match.Value
}

$workflow = Read-RepoText '.github\workflows\ci.yml'
$postMergeWorkflow = Read-RepoText '.github\workflows\post-merge.yml'
$jobsMatch = [regex]::Match($workflow,'(?m)^jobs:\r?$')
if (!$jobsMatch.Success) { throw 'workflow jobs mapping is missing' }
$jobsText = $workflow.Substring($jobsMatch.Index + $jobsMatch.Length)
$actualJobs = @([regex]::Matches($jobsText,'(?m)^  ([a-z0-9][a-z0-9-]*):\r?$') | ForEach-Object { $_.Groups[1].Value })

$prePrGatePath = Join-Path $RepoRoot 'scripts\codex-autopilot\verify-pre-pr-ci.ps1'
. $prePrGatePath
$requiredJobs = @($script:PrePrRequiredJobs)
Assert-SetEqual $actualJobs $requiredJobs 'workflow jobs versus pre-PR evidence jobs'

$summary = Get-JobBlock $workflow 'build-summary'
$summaryNeeds = @([regex]::Matches($summary,'(?m)^      - ([a-z0-9][a-z0-9-]*)\r?$') | ForEach-Object { $_.Groups[1].Value })
Assert-SetEqual $summaryNeeds @($requiredJobs | Where-Object { $_ -ne 'build-summary' }) 'build-summary needs versus gate jobs'
Assert-Contains $summary @('if: always()','## CI Build Summary','needs.backend-test.result','needs.sql-safety-scan.result') 'build-summary'

$backendTest = Get-JobBlock $workflow 'backend-test'
$backendMySql = Get-JobBlock $workflow 'backend-test-mysql'
$backendDependency = Get-JobBlock $workflow 'backend-dependency-scan'
$frontendBuild = Get-JobBlock $workflow 'frontend-build'
$frontendV2 = Get-JobBlock $workflow 'frontend-v2-gate'
$supplyChain = Get-JobBlock $workflow 'supply-chain-security'
$e2e = Get-JobBlock $workflow 'e2e'
$sqlSafety = Get-JobBlock $workflow 'sql-safety-scan'

Assert-Contains $workflow @('branches-ignore: [master, main]','pull_request:','branches: [master, main]','workflow_dispatch:') 'workflow triggers'
if ($workflow.Contains("branches: ['**']")) { throw 'full CI must not rerun after protected default-branch merges' }
Assert-Contains $postMergeWorkflow @(
  'name: Post-merge verification','branches: [master, main]','contents: read','checks: read','pull-requests: read',
  'post-merge-verification:','./scripts/ci/verify-post-merge-ci.ps1',
  './scripts/ci/test-workflow-contract.ps1','./scripts/codex-autopilot/test-codex-task-execution-policy.ps1'
) 'post-merge workflow'
$postMergeJobsMatch = [regex]::Match($postMergeWorkflow,'(?m)^jobs:\r?$')
if (!$postMergeJobsMatch.Success) { throw 'post-merge workflow jobs mapping is missing' }
$postMergeJobsText = $postMergeWorkflow.Substring($postMergeJobsMatch.Index + $postMergeJobsMatch.Length)
$postMergeJobs = @([regex]::Matches($postMergeJobsText,'(?m)^  ([a-z0-9][a-z0-9-]*):\r?$') | ForEach-Object { $_.Groups[1].Value })
Assert-SetEqual $postMergeJobs @('post-merge-verification') 'post-merge lightweight jobs'
$postMergeStepCount = [regex]::Matches($postMergeWorkflow,'(?m)^      - (?:name|uses):').Count
if ($postMergeStepCount -ne 4) { throw "post-merge workflow must remain lightweight: steps=$postMergeStepCount" }
if ([regex]::IsMatch($postMergeWorkflow,'(?m)^\s+[a-z-]+: write\s*$')) { throw 'post-merge workflow permissions must remain read-only' }
if ([regex]::IsMatch($workflow.Substring(0,$jobsMatch.Index),'(?m)^permissions:')) { throw 'workflow added global permissions' }
if ([regex]::Matches($workflow,'(?m)^    permissions:\r?$').Count -ne 2) { throw 'job-level permissions declaration count changed' }
if ([regex]::IsMatch($workflow,'(?m)^    name:')) { throw 'job display names must remain implicit job ids for check-context compatibility' }

Assert-Contains $backendTest @(
  './mvnw -C -Ptest-order-independence test','./mvnw -C verify',
  'name: ${{ env.BACKEND_JAR_ARTIFACT }}','path: backend/target/cgc-pms-backend.jar',
  'name: ${{ env.BACKEND_COVERAGE_ARTIFACT }}','path: backend/target/site/jacoco'
) 'backend-test'
Assert-Contains $backendMySql @(
  'mysql:','image: mysql:8.0','redis:','image: redis:7-alpine',
  'bash ./scripts/ci/verify-mysql-grants.sh "${{ job.services.mysql.id }}"',
  '-Dtest=FlywayMySqlSmokeTest,BaselineMySqlSmokeTest','CGCPMS_M52_MYSQL_BASELINE: ''true'''
) 'backend-test-mysql'
Assert-Contains $backendDependency @('permissions:','contents: read','bash ./scripts/ci/scan-backend-dependencies.sh') 'backend-dependency-scan'
Assert-Contains $frontendBuild @('name: ${{ env.FRONTEND_DIST_ARTIFACT }}','path: frontend-admin/dist','if: always()') 'frontend-build'
Assert-Contains $frontendV2 @('pnpm check:boundary','pnpm check:route-ledger','pnpm lint:check','pnpm test:unit','pnpm type-check:contracts','pnpm type-check','pnpm build','pnpm check:bundle-size','pnpm audit --audit-level high') 'frontend-v2-gate'
Assert-Contains $supplyChain @(
  'needs: [backend-test, backend-dependency-scan, frontend-build]',
  'contents: read','id-token: write','attestations: write',
  'name: ${{ env.BACKEND_JAR_ARTIFACT }}','path: artifacts/backend',
  'name: ${{ env.FRONTEND_DIST_ARTIFACT }}','path: artifacts/frontend-dist',
  'subject-path: artifacts/backend/cgc-pms-backend.jar',
  'sbom-path: artifacts/backend/cgc-pms-backend.spdx.json',
  'subject-path: artifacts/frontend-dist.tar.gz',
  'sbom-path: artifacts/frontend-dist.spdx.json',
  'aquasec/trivy:0.65.0','artifacts/backend:/workspace:ro'
) 'supply-chain-security'
Assert-Contains $e2e @(
  'needs: [backend-test-mysql, frontend-build]','image: mysql:8.0','image: redis:7-alpine',
  'name: ${{ env.FRONTEND_DIST_ARTIFACT }}','path: frontend-admin/dist',
  'bash ./scripts/ci/start-e2e-minio.sh','bash ./scripts/ci/start-e2e-backend.sh',
  'pnpm test:e2e:ui','if: failure()'
) 'e2e'
Assert-Contains $sqlSafety @('./scripts/ci/test-workflow-contract.ps1','./scripts/check-sql-safety.ps1') 'sql-safety-scan'

if ([regex]::Matches($workflow,'uses: actions/upload-artifact@v7').Count -ne 9) { throw 'artifact upload count changed' }
if ([regex]::Matches($workflow,'uses: actions/download-artifact@v8').Count -ne 3) { throw 'artifact download count changed' }
if ([regex]::Matches($workflow,'uses: \./\.github/actions/setup-backend').Count -ne 3) { throw 'backend setup composite usage count changed' }
if ([regex]::Matches($workflow,'uses: \./\.github/actions/setup-frontend').Count -ne 7) { throw 'frontend setup composite usage count changed' }
if ($workflow.Contains('uses: ./.github/workflows/')) { throw 'reusable workflow split would change the current check boundary' }

$backendAction = Read-RepoText '.github\actions\setup-backend\action.yml'
$frontendAction = Read-RepoText '.github\actions\setup-frontend\action.yml'
Assert-Contains $backendAction @('using: composite','actions/setup-java@v5','java-version: ''21''','distribution: temurin','cache: maven') 'backend setup action'
Assert-Contains $frontendAction @('using: composite','working-directory:','pnpm/action-setup@v6','actions/setup-node@v6','node-version: ''22''','pnpm install --frozen-lockfile') 'frontend setup action'

foreach ($scriptName in @(
  'verify-mysql-grants.sh','run-frontend-lint.sh','scan-backend-dependencies.sh',
  'start-e2e-minio.sh','start-e2e-backend.sh'
)) {
  $scriptText = Read-RepoText "scripts\ci\$scriptName"
  Assert-Contains $scriptText @('#!/usr/bin/env bash','set -euo pipefail') $scriptName
}
Assert-Contains (Read-RepoText 'scripts\ci\verify-mysql-grants.sh') @('normalized_grants','GRANT USAGE ON \*\.\*','MySQL migration user has global privileges') 'MySQL grant script'
Assert-Contains (Read-RepoText 'scripts\ci\scan-backend-dependencies.sh') @('MSYS_NO_PATHCONV=1','TRIVY_CACHE_DIR','aquasec/trivy:0.65.0','--pkg-types library','--skip-dirs /workspace/backend/target','/workspace/backend') 'backend dependency scan script'

$backendPom = Read-RepoText 'backend\pom.xml'
Assert-Contains $backendPom @('<id>test-order-independence</id>') 'backend test order profile'
$frontendPackage = Read-RepoText 'frontend-admin\package.json' | ConvertFrom-Json
$v2Package = Read-RepoText 'frontend-admin-v2\package.json' | ConvertFrom-Json
foreach ($name in @('lint:check','type-check','build','test:coverage','test:e2e:ui','check:bundle-size')) {
  if ($frontendPackage.scripts.PSObject.Properties.Name -notcontains $name) { throw "frontend-admin script is missing: $name" }
}
foreach ($name in @('check:boundary','check:route-ledger','lint:check','test:unit','type-check:contracts','type-check','build','check:bundle-size')) {
  if ($v2Package.scripts.PSObject.Properties.Name -notcontains $name) { throw "frontend-admin-v2 script is missing: $name" }
}

[pscustomobject]@{
  ok = $true
  jobs = @($actualJobs)
  requiredJobCount = $requiredJobs.Count
  artifactUploads = 9
  artifactDownloads = 3
  permissionBlocks = 2
  postMergeJobs = $postMergeJobs.Count
  postMergeSteps = $postMergeStepCount
} | ConvertTo-Json -Depth 4
