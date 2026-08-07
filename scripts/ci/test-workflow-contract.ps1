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

function Assert-ImmutableActionRefs([string]$Text,[string]$Name) {
  foreach ($match in [regex]::Matches($Text,'(?m)^\s*(?:-\s*)?uses:\s*(?<ref>[^#\s]+)')) {
    $ref = $match.Groups['ref'].Value
    if (!$ref.StartsWith('./') -and $ref -notmatch '^[^@\s]+@[0-9a-f]{40}$') {
      throw "$Name contains mutable action reference: $ref"
    }
  }
}

function Assert-ImmutableImageRefs([string]$Text,[string]$Name) {
  foreach ($match in [regex]::Matches($Text,'(?m)^\s*image:\s*(?<ref>[^\s#]+)')) {
    $ref = $match.Groups['ref'].Value
    if ($ref -notmatch '@sha256:[0-9a-f]{64}$') {
      throw "$Name contains mutable container reference: $ref"
    }
  }

  $logicalText = [regex]::Replace($Text,'\\\r?\n\s*',' ')
  foreach ($match in [regex]::Matches($logicalText,'(?m)^\s*docker\s+(?:run|pull)\s+(?<command>.+)$')) {
    if ($match.Groups['command'].Value -notmatch '(?:^|\s)[^\s]+@sha256:[0-9a-f]{64}(?:\s|$)') {
      throw "$Name contains docker execution without immutable image digest"
    }
  }
}

function Get-JobBlock([string]$Workflow,[string]$JobName) {
  $match = [regex]::Match($Workflow,"(?ms)^  $([regex]::Escape($JobName)):\r?\n(?<body>.*?)(?=^  [a-z0-9][a-z0-9-]*:\r?$|\z)")
  if (!$match.Success) { throw "workflow job block is missing: $JobName" }
  return $match.Value
}

$workflow = Read-RepoText '.github\workflows\ci.yml'
$postMergeWorkflow = Read-RepoText '.github\workflows\post-merge.yml'
$postMergeVerifier = Read-RepoText 'scripts\ci\verify-post-merge-ci.ps1'
$codeOwners = Read-RepoText '.github\CODEOWNERS'
$backendAction = Read-RepoText '.github\actions\setup-backend\action.yml'
$frontendAction = Read-RepoText '.github\actions\setup-frontend\action.yml'
$dependencyScanScript = Read-RepoText 'scripts\ci\scan-backend-dependencies.sh'
$minioScript = Read-RepoText 'scripts\ci\start-e2e-minio.sh'
foreach ($mutableImageSample in @("services:`n  db:`n    image: postgres:latest", 'docker run --rm postgres:latest')) {
  $mutableImageRejected = $false
  try { Assert-ImmutableImageRefs $mutableImageSample 'contract self-check' } catch { $mutableImageRejected = $true }
  if (!$mutableImageRejected) { throw 'immutable image contract must reject unknown mutable images' }
}
Assert-ImmutableActionRefs $workflow 'CI workflow'
Assert-ImmutableActionRefs $postMergeWorkflow 'post-merge workflow'
Assert-ImmutableActionRefs $backendAction 'backend setup action'
Assert-ImmutableActionRefs $frontendAction 'frontend setup action'
Assert-ImmutableImageRefs "$workflow`n$dependencyScanScript`n$minioScript" 'CI execution inputs'
Assert-Contains $workflow @(
  'actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1',
  'actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a',
  'actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c',
  'anchore/sbom-action@e22c389904149dbc22b58101806040fa8d37a610',
  'actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6'
) 'pinned CI actions'
Assert-Contains $postMergeWorkflow @('actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1') 'pinned post-merge action'
$jobsMatch = [regex]::Match($workflow,'(?m)^jobs:\r?$')
if (!$jobsMatch.Success) { throw 'workflow jobs mapping is missing' }
$jobsText = $workflow.Substring($jobsMatch.Index + $jobsMatch.Length)
$actualJobs = @([regex]::Matches($jobsText,'(?m)^  ([a-z0-9][a-z0-9-]*):\r?$') | ForEach-Object { $_.Groups[1].Value })

$prePrGatePath = Join-Path $RepoRoot 'scripts\codex-autopilot\verify-pre-pr-ci.ps1'
. $prePrGatePath
$requiredJobs = @($script:PrePrRequiredJobs)
Assert-SetEqual $actualJobs @($requiredJobs + 'build-summary') 'workflow jobs versus pre-PR evidence jobs'

$summary = Get-JobBlock $workflow 'build-summary'
$summaryNeeds = @([regex]::Matches($summary,'(?m)^      - ([a-z0-9][a-z0-9-]*)\r?$') | ForEach-Object { $_.Groups[1].Value })
Assert-SetEqual $summaryNeeds $requiredJobs 'build-summary needs versus gate jobs'
Assert-Contains $summary @('if: always()','## CI Build Summary','needs.backend-test.result','needs.sql-safety-scan.result','PR push evidence: ${{ needs.pr-push-evidence.result }}') 'build-summary'

$prPushEvidence = Get-JobBlock $workflow 'pr-push-evidence'
$backendTest = Get-JobBlock $workflow 'backend-test'
$backendOrder = Get-JobBlock $workflow 'backend-order-sensitive'
$backendMySql = Get-JobBlock $workflow 'backend-test-mysql'
$backendDependency = Get-JobBlock $workflow 'backend-dependency-scan'
$frontendBuild = Get-JobBlock $workflow 'frontend-build'
$frontendV2 = Get-JobBlock $workflow 'frontend-v2-gate'
$supplyChain = Get-JobBlock $workflow 'supply-chain-security'
$e2e = Get-JobBlock $workflow 'e2e'
$sqlSafety = Get-JobBlock $workflow 'sql-safety-scan'

Assert-Contains $workflow @('branches-ignore: [master, main]','pull_request:','branches: [master, main]','workflow_dispatch:') 'workflow triggers'
Assert-Contains $workflow @('run-name: CI ${{ github.event_name }} ${{ github.event.pull_request.number || github.sha }}') 'workflow run identity'
Assert-Contains $workflow @(
  'concurrency:','group: ci-${{ github.workflow }}-${{ github.event_name }}-${{ github.ref }}','cancel-in-progress: true'
) 'workflow concurrency cancellation'
Assert-Contains $prPushEvidence @(
  'actions: read','contents: read','pull-requests: read',
  'if: github.event_name == ''pull_request'' && github.event.pull_request.head.repo.full_name == github.repository',
  'continue-on-error: true','ref: ${{ github.event.pull_request.base.sha }}','persist-credentials: false',
  'scripts/ci/verify-pr-push-evidence.ps1','run-full: ${{ steps.mode.outputs.run-full }}',
  'REUSE_ENABLED: ${{ vars.CI_PR_PUSH_REUSE_ENABLED }}',
  "`$mode = 'bootstrap-full'","`$mode = 'reuse-disabled-full'","`$mode = 'evidence-failed-full'",
  "`$mode = 'fork-full'","`$mode = 'reused-push-ci'"
) 'PR push evidence reuse'
Assert-Contains $workflow @(
  'trivyColdCache:','description: Run supply-chain scan with an isolated empty Trivy cache',
  'required: false','default: false','type: boolean'
) 'workflow_dispatch Trivy cold-cache input'
if ($workflow.Contains("branches: ['**']")) { throw 'full CI must not rerun after protected default-branch merges' }
Assert-Contains $postMergeWorkflow @(
  'name: Post-merge verification','branches: [master, main]','actions: read','contents: read','pull-requests: read',
  'post-merge-verification:','./scripts/ci/verify-post-merge-ci.ps1',
  './scripts/ci/test-workflow-contract.ps1','./scripts/codex-autopilot/test-codex-task-execution-policy.ps1'
) 'post-merge workflow'
Assert-Contains $postMergeVerifier @(
  "'--event', 'push'","'--event', 'pull_request'",'Test-PrePrCiEvidence',
  'displayTitle','pr-push-evidence','POST_MERGE_PR_CI_EVIDENCE_MISSING',
  'POST_MERGE_PR_PUSH_EVIDENCE_MISSING','POST_MERGE_TREE_MISMATCH','FORK_FULL_PR_CI'
) 'post-merge exact-run evidence'
Assert-Contains $codeOwners @(
  '/.github/CODEOWNERS @kismet84','/.github/workflows/ @kismet84',
  '/.github/actions/ @kismet84','/scripts/ci/ @kismet84',
  '/scripts/codex-autopilot/verify-pre-pr-ci.ps1 @kismet84'
) 'CI control-plane CODEOWNERS'
$postMergeJobsMatch = [regex]::Match($postMergeWorkflow,'(?m)^jobs:\r?$')
if (!$postMergeJobsMatch.Success) { throw 'post-merge workflow jobs mapping is missing' }
$postMergeJobsText = $postMergeWorkflow.Substring($postMergeJobsMatch.Index + $postMergeJobsMatch.Length)
$postMergeJobs = @([regex]::Matches($postMergeJobsText,'(?m)^  ([a-z0-9][a-z0-9-]*):\r?$') | ForEach-Object { $_.Groups[1].Value })
Assert-SetEqual $postMergeJobs @('post-merge-verification') 'post-merge lightweight jobs'
$postMergeStepCount = [regex]::Matches($postMergeWorkflow,'(?m)^      - (?:name|uses):').Count
if ($postMergeStepCount -ne 4) { throw "post-merge workflow must remain lightweight: steps=$postMergeStepCount" }
if ([regex]::IsMatch($postMergeWorkflow,'(?m)^\s+[a-z-]+: write\s*$')) { throw 'post-merge workflow permissions must remain read-only' }
if ([regex]::IsMatch($workflow.Substring(0,$jobsMatch.Index),'(?m)^permissions:')) { throw 'workflow added global permissions' }
if ([regex]::Matches($workflow,'(?m)^    permissions:\r?$').Count -ne 3) { throw 'job-level permissions declaration count changed' }
if ([regex]::IsMatch($workflow,'(?m)^    name:')) { throw 'job display names must remain implicit job ids for check-context compatibility' }

foreach ($jobName in @($requiredJobs | Where-Object { $_ -notin @('pr-push-evidence','supply-chain-security','e2e') })) {
  $job = Get-JobBlock $workflow $jobName
  Assert-Contains $job @(
    'needs: pr-push-evidence',
    "if: needs.pr-push-evidence.outputs.run-full == 'true'"
  ) "$jobName evidence reuse"
}
Assert-Contains $supplyChain @(
  '[pr-push-evidence, backend-test, backend-dependency-scan, frontend-build]',
  "if: needs.pr-push-evidence.outputs.run-full == 'true'"
) 'supply-chain evidence reuse'
Assert-Contains $e2e @(
  '[pr-push-evidence, frontend-build]',
  "if: needs.pr-push-evidence.outputs.run-full == 'true'"
) 'e2e evidence reuse'

Assert-Contains $backendTest @(
  'Install CJK font for PDF tests','fonts-arphic-gbsn00lp','test -r /usr/share/fonts/truetype/arphic-gbsn00lp/gbsn00lp.ttf',
  './mvnw -C verify','./scripts/ci/summarize-surefire.ps1','retention-days: 7',
  'name: ${{ env.BACKEND_JAR_ARTIFACT }}','path: backend/target/cgc-pms-backend.jar',
  'name: ${{ env.BACKEND_COVERAGE_ARTIFACT }}','path: backend/target/site/jacoco'
) 'backend-test'
if ($backendTest.Contains('-Ptest-order-independence')) { throw 'backend-test must not serialize the order-sensitive profile' }
Assert-Contains $backendOrder @(
  'needs: pr-push-evidence',"if: needs.pr-push-evidence.outputs.run-full == 'true'",
  'Run historically order-sensitive classes under reverse class order',
  './mvnw -C -Ptest-order-independence -Djacoco.skip=true test'
) 'backend-order-sensitive'
Assert-Contains $backendMySql @(
  'mysql:','image: mysql:8.0@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b',
  'redis:','image: redis:7-alpine@sha256:e7723ff73d963f5cc6d9c4643ea3d989527a402a319239054e9472a7fb9219a2',
  'bash ./scripts/ci/verify-mysql-grants.sh "${{ job.services.mysql.id }}"',
  '-Dtest=FlywayMySqlSmokeTest,BaselineMySqlSmokeTest,BidProjectScopeMySqlTest,PaymentMySqlConcurrencyTest,MdMaterialDeleteMySqlConcurrencyTest',
  'CGCPMS_M52_MYSQL_BASELINE: "true"','CGCPMS_M70_MYSQL_CONCURRENCY: "true"',
  'CGCPMS_MATERIAL_DELETE_MYSQL_CONCURRENCY: "true"'
) 'backend-test-mysql'
Assert-Contains $backendDependency @('permissions:','contents: read','bash ./scripts/ci/scan-backend-dependencies.sh') 'backend-dependency-scan'
Assert-Contains $frontendBuild @('name: ${{ env.FRONTEND_DIST_ARTIFACT }}','path: frontend-admin-v2/dist','if: always()') 'frontend-build'
Assert-Contains $frontendV2 @(
  'working-directory: frontend-admin-v2',
  'pnpm check:boundary','pnpm check:route-ledger','pnpm check:design-system'
) 'frontend-v2-gate'
Assert-Contains $supplyChain @(
  '[pr-push-evidence, backend-test, backend-dependency-scan, frontend-build]',
  'contents: read','id-token: write','attestations: write',
  'run: echo "TRIVY_CACHE_DATE=$(date -u +%Y-%m-%d)" >> "$GITHUB_ENV"',
  'run: |','mkdir -p .trivy-cache',
  'name: ${{ env.BACKEND_JAR_ARTIFACT }}','path: artifacts/backend',
  'name: ${{ env.FRONTEND_DIST_ARTIFACT }}','path: artifacts/frontend-dist',
  'subject-path: artifacts/backend/cgc-pms-backend.jar',
  'sbom-path: artifacts/backend/cgc-pms-backend.spdx.json',
  'subject-path: artifacts/frontend-dist.tar.gz',
  'sbom-path: artifacts/frontend-dist.spdx.json',
  'aquasec/trivy:0.65.0@sha256:a22415a38938a56c379387a8163fcb0ce38b10ace73e593475d3658d578b2436',
  'artifacts/backend:/workspace:ro'
) 'supply-chain-security'
$trivyCacheStep = [regex]::Match(
  $supplyChain,
  '(?ms)^      - name: Restore Trivy vulnerability databases\r?\n(?<body>.*?)(?=^      - (?:name|uses):|\z)'
)
if (!$trivyCacheStep.Success) { throw 'Trivy shared-cache restore step is missing' }
Assert-Contains $trivyCacheStep.Value @(
  'if: ${{ github.event_name != ''workflow_dispatch'' || inputs.trivyColdCache != true }}',
  'uses: actions/cache@55cc8345863c7cc4c66a329aec7e433d2d1c52a9','path: .trivy-cache',
  'key: trivy-java-db-${{ runner.os }}-${{ env.TRIVY_CACHE_DATE }}','restore-keys:'
) 'conditional Trivy shared-cache restore'
if ([regex]::Matches($supplyChain,'uses: actions/cache@[0-9a-f]{40}').Count -ne 1) {
  throw 'supply-chain-security must have exactly one conditional shared-cache restore'
}
Assert-Contains $e2e @(
  '[pr-push-evidence, frontend-build]',
  'name: ${{ env.FRONTEND_DIST_ARTIFACT }}','path: frontend-admin-v2/dist',
  'pnpm test:e2e:contract','PLAYWRIGHT_BASE_URL: http://127.0.0.1:4173',
  'PLAYWRIGHT_WEB_SERVER_COMMAND: pnpm preview --host 127.0.0.1 --port 4173','if: failure()'
) 'e2e'
foreach ($forbidden in @('services:','setup-backend','start-e2e-minio.sh','start-e2e-backend.sh','PLAYWRIGHT_USE_DEV_LOGIN','PLAYWRIGHT_DEV_LOGIN_PATH')) {
  if ($e2e.Contains($forbidden)) { throw "browser contract job contains obsolete runtime dependency: $forbidden" }
}
Assert-Contains $sqlSafety @(
  './scripts/ci/test-workflow-contract.ps1',
  './scripts/ci/test-pr-push-evidence.ps1',
  './scripts/check-sql-safety.ps1'
) 'sql-safety-scan'

if ([regex]::Matches($workflow,'uses: actions/upload-artifact@[0-9a-f]{40}').Count -ne 9) { throw 'artifact upload count changed' }
if ([regex]::Matches($workflow,'uses: actions/download-artifact@[0-9a-f]{40}').Count -ne 3) { throw 'artifact download count changed' }
if ([regex]::Matches($workflow,'uses: \./\.github/actions/setup-backend').Count -ne 3) { throw 'backend setup composite usage count changed' }
if ([regex]::Matches($workflow,'uses: \./\.github/actions/setup-frontend').Count -ne 7) { throw 'frontend setup composite usage count changed' }
if ($workflow.Contains('uses: ./.github/workflows/')) { throw 'reusable workflow split would change the current check boundary' }

Assert-Contains $backendAction @('using: composite','actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961','java-version: ''21''','distribution: temurin','cache: maven') 'backend setup action'
Assert-Contains $frontendAction @(
  'using: composite','working-directory:',
  'pnpm/action-setup@f520eceda224fe1a4aed5a2a27a194379a409996',
  'actions/setup-node@249970729cb0ef3589644e2896645e5dc5ba9c38',
  'node-version: ''22''','pnpm install --frozen-lockfile'
) 'frontend setup action'

foreach ($scriptName in @(
  'verify-mysql-grants.sh','run-frontend-lint.sh','scan-backend-dependencies.sh',
  'start-e2e-minio.sh','start-e2e-backend.sh'
)) {
  $scriptText = Read-RepoText "scripts\ci\$scriptName"
  Assert-Contains $scriptText @('#!/usr/bin/env bash','set -euo pipefail') $scriptName
}
Assert-Contains $minioScript @(
  'minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e',
  'minio/mc@sha256:a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727',
  'mc mb --ignore-existing e2e/cgc-pms-e2e'
) 'MinIO E2E bucket bootstrap'
Assert-Contains (Read-RepoText 'scripts\ci\verify-mysql-grants.sh') @('normalized_grants','GRANT USAGE ON \*\.\*','MySQL migration user has global privileges') 'MySQL grant script'
Assert-Contains $dependencyScanScript @(
  'MSYS_NO_PATHCONV=1','TRIVY_CACHE_DIR',
  'aquasec/trivy:0.65.0@sha256:a22415a38938a56c379387a8163fcb0ce38b10ace73e593475d3658d578b2436',
  '--pkg-types library','--skip-dirs /workspace/backend/target','/workspace/backend'
) 'backend dependency scan script'

$backendPom = Read-RepoText 'backend\pom.xml'
Assert-Contains $backendPom @('<id>test-order-independence</id>') 'backend test order profile'
$frontendPackage = Read-RepoText 'frontend-admin-v2\package.json' | ConvertFrom-Json
foreach ($name in @('check:boundary','check:route-ledger','check:design-system','lint:check','test:unit','test:ci','type-check:contracts','type-check','build','check:bundle-size','test:e2e:contract','test:e2e:migration-gate','check:pre-push')) {
  if ($frontendPackage.scripts.PSObject.Properties.Name -notcontains $name) { throw "frontend-admin-v2 script is missing: $name" }
}

$pushGate = Read-RepoText 'frontend-admin-v2\scripts\run-push-quality-gate.mjs'
Assert-Contains $pushGate @(
  "id: 'diff-check:branch'",
  "id: 'diff-check:staged'",
  "args: ['diff', '--check', '--cached']",
  "id: 'diff-check:unstaged'",
  "id: 'dependency-audit'",
  "'--registry=https://registry.npmjs.org'",
  "'lint:check'",
  "'test:unit'",
  "'build'",
  "'test:e2e:contract'",
  "startsWith('frontend-admin-v2/src/pages/')",
  "startsWith('frontend-admin-v2/')",
  "startsWith('frontend-admin-v2/patches/')",
  "startsWith('patches/')",
  'dependencyFiles.has(file)',
  "startsWith('.github/workflows/')",
  'pre-push-quality-gate-v1.json',
  "['origin/master', 'origin/main']",
  'core.quotepath=false',
  '`${baseRef}...HEAD`',
  'nodeVersion: process.version',
  'process.env.npm_execpath',
  'executable: process.execPath',
  'pnpmVersion',
  'gateEnvironment:',
  "captureRaw('git', ['diff', '--binary'",
  'P:${pathBytes.length}:',
  'C:${content.length}:',
  'untrackedFilesAreRegular',
  'cache write skipped:'
) 'frontend pre-push quality gate'
if ($pushGate.Contains("'type-check'")) { throw 'frontend pre-push gate must not repeat the build type check' }
if ($pushGate.Contains('shell:')) { throw 'frontend pre-push gate must not invoke pnpm through a shell' }
$prePushHook = Read-RepoText '.githooks\pre-push'
Assert-Contains $prePushHook @('set -eu','pnpm --dir frontend-admin-v2 check:pre-push') 'versioned pre-push hook'

& (Join-Path $RepoRoot 'scripts\ci\test-pr-push-evidence.ps1')
& (Join-Path $RepoRoot 'scripts\ci\test-post-merge-ci-evidence.ps1')
& (Join-Path $RepoRoot 'scripts\ci\test-surefire-summary.ps1')

[pscustomobject]@{
  ok = $true
  jobs = @($actualJobs)
  requiredJobCount = $requiredJobs.Count
  artifactUploads = 9
  artifactDownloads = 3
  permissionBlocks = 3
  postMergeJobs = $postMergeJobs.Count
  postMergeSteps = $postMergeStepCount
} | ConvertTo-Json -Depth 4
