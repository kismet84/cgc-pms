[CmdletBinding()]
param(
  [string]$RepoRoot,
  [string]$Repository = $env:GITHUB_REPOSITORY,
  [ValidateRange(1, [int]::MaxValue)][int]$PullRequestNumber,
  [ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ExpectedHeadSha,
  [ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ExpectedBaseSha
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
  $RepoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
}
. (Join-Path $RepoRoot 'scripts\codex-autopilot\verify-pre-pr-ci.ps1')

function Test-PrPushCiEvidence {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$ExpectedRepository,
    [Parameter(Mandatory)][ValidateRange(1, [int]::MaxValue)][int]$ExpectedPullRequestNumber,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ExpectedHeadSha,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ExpectedBaseSha,
    [Parameter(Mandatory)]$PullRequest,
    [Parameter(Mandatory)]$Comparison,
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$ChangedFiles,
    [Parameter(Mandatory)][object[]]$Runs,
    [Parameter(Mandatory)][object[]]$Jobs
  )

  $headSha = $ExpectedHeadSha.ToLowerInvariant()
  $baseSha = $ExpectedBaseSha.ToLowerInvariant()
  if ([int]$PullRequest.number -ne $ExpectedPullRequestNumber -or [string]$PullRequest.state -ne 'open') {
    throw "PR_PUSH_CI_INVALID_PR: expected open PR #$ExpectedPullRequestNumber"
  }
  if ([string]$PullRequest.base.ref -notin @('master', 'main')) {
    throw "PR_PUSH_CI_INVALID_BASE: $($PullRequest.base.ref)"
  }
  if ([string]$PullRequest.base.sha -ne $baseSha) {
    throw "PR_PUSH_CI_BASE_MISMATCH: expected=$baseSha, actual=$($PullRequest.base.sha)"
  }
  if ([string]$PullRequest.head.sha -ne $headSha) {
    throw "PR_PUSH_CI_HEAD_MISMATCH: expected=$headSha, actual=$($PullRequest.head.sha)"
  }
  if ([string]$PullRequest.head.repo.full_name -ne $ExpectedRepository) {
    throw "PR_PUSH_CI_FORK_REQUIRES_FULL: $($PullRequest.head.repo.full_name)"
  }
  $controlPlaneChanges = @($ChangedFiles | Where-Object {
    $path = [string]$_.filename
    $path.StartsWith('.github/workflows/') -or
      $path.StartsWith('.github/actions/') -or
      $path -eq '.github/CODEOWNERS' -or
      $path -eq 'backend/pom.xml' -or
      $path -eq 'frontend-admin-v2/package.json' -or
      $path -eq 'frontend-admin-v2/playwright.config.ts' -or
      $path -eq 'frontend-admin-v2/scripts/e2e-spec-groups.mjs' -or
      $path -eq 'frontend-admin-v2/scripts/run-migration-ui-gate.mjs' -or
      $path -eq 'frontend-admin-v2/scripts/run-push-quality-gate.mjs' -or
      $path.StartsWith('scripts/ci/') -or
      $path -eq 'scripts/codex-autopilot/verify-pre-pr-ci.ps1' -or
      $path -eq 'scripts/codex-autopilot/codex-autopilot.config.json' -or
      $path -eq '.agents/skills/cgc-pms-ci-gate-triage/SKILL.md' -or
      $path -eq 'scripts/check-sql-safety.ps1'
  } | ForEach-Object { [string]$_.filename })
  if ($controlPlaneChanges.Count -gt 0) {
    throw "PR_PUSH_CI_CONTROL_PLANE_CHANGED: $($controlPlaneChanges -join ', ')"
  }
  if ([int]$Comparison.behind_by -ne 0) {
    throw "PR_PUSH_CI_HEAD_BEHIND_BASE: behind_by=$($Comparison.behind_by)"
  }

  $evidence = Test-PrePrCiEvidence `
    -ExpectedHeadSha $headSha `
    -ExpectedHeadBranch ([string]$PullRequest.head.ref) `
    -Runs $Runs `
    -Jobs $Jobs
  return [pscustomobject]@{
    status = 'PASS'
    mode = 'REUSED_PUSH_CI'
    pullRequest = $ExpectedPullRequestNumber
    headSha = $headSha
    baseSha = $baseSha
    headBranch = [string]$PullRequest.head.ref
    runId = [long]$evidence.runId
    url = [string]$evidence.url
    requiredJobs = @($evidence.requiredJobs)
  }
}

function Invoke-PrPushCiEvidenceGate {
  if ([string]::IsNullOrWhiteSpace($Repository)) {
    throw 'PR_PUSH_CI_REPOSITORY_MISSING: pass -Repository or set GITHUB_REPOSITORY'
  }
  if ($PullRequestNumber -lt 1 -or !$ExpectedHeadSha -or !$ExpectedBaseSha) {
    throw 'PR_PUSH_CI_ARGUMENT_MISSING: pull request number, head SHA, and base SHA are required'
  }

  $headSha = $ExpectedHeadSha.ToLowerInvariant()
  $baseSha = $ExpectedBaseSha.ToLowerInvariant()
  $pullRequest = Invoke-GhJson @('api', "repos/$Repository/pulls/$PullRequestNumber")
  $headBranch = [string]$pullRequest.head.ref
  $comparison = Invoke-GhJson @('api', "repos/$Repository/compare/$baseSha...$headSha")
  $changedFiles = [System.Collections.Generic.List[object]]::new()
  $page = 1
  do {
    $pageFiles = @(Invoke-GhJson @('api', "repos/$Repository/pulls/$PullRequestNumber/files?per_page=100&page=$page"))
    foreach ($file in $pageFiles) { $changedFiles.Add($file) }
    $page++
  } while ($pageFiles.Count -eq 100)
  $runs = @(Invoke-GhJson @(
    'run', 'list', '--repo', $Repository, '--workflow', 'ci.yml', '--branch', $headBranch,
    '--commit', $headSha, '--event', 'push', '--limit', '20',
    '--json', 'databaseId,headBranch,headSha,event,status,conclusion,url,createdAt'
  ))
  $matchingRuns = @($runs | Where-Object {
    ([string]$_.headSha).ToLowerInvariant() -eq $headSha -and
      [string]$_.headBranch -eq $headBranch -and
      [string]$_.event -eq 'push'
  } | Sort-Object { [datetime]$_.createdAt } -Descending)
  if ($matchingRuns.Count -eq 0) {
    throw "PR_PUSH_CI_EVIDENCE_MISSING: no push CI run found for $headBranch at $headSha"
  }
  $run = $matchingRuns[0]
  $view = Invoke-GhJson @('run', 'view', ([string]$run.databaseId), '--repo', $Repository, '--json', 'jobs')
  return Test-PrPushCiEvidence `
    -ExpectedRepository $Repository `
    -ExpectedPullRequestNumber $PullRequestNumber `
    -ExpectedHeadSha $headSha `
    -ExpectedBaseSha $baseSha `
    -PullRequest $pullRequest `
    -Comparison $comparison `
    -ChangedFiles @($changedFiles) `
    -Runs $runs `
    -Jobs @($view.jobs)
}

if ($MyInvocation.InvocationName -ne '.') {
  Invoke-PrPushCiEvidenceGate | ConvertTo-Json -Depth 4
}
