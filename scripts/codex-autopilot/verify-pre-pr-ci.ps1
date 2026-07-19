[CmdletBinding()]
param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [ValidatePattern('^[a-fA-F0-9]{40}$')][string]$HeadSha
)

$ErrorActionPreference = 'Stop'

$script:PrePrRequiredJobs = @(
  'backend-test',
  'backend-dependency-scan',
  'backend-test-mysql',
  'frontend-lint',
  'type-check',
  'frontend-build',
  'frontend-test',
  'frontend-dependency-audit',
  'frontend-v2-gate',
  'sql-safety-scan',
  'supply-chain-security',
  'e2e',
  'build-summary'
)

function Test-PrePrCiEvidence {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ExpectedHeadSha,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$ExpectedHeadBranch,
    [Parameter(Mandatory)][object[]]$Runs,
    [Parameter(Mandatory)][object[]]$Jobs
  )

  $expected = $ExpectedHeadSha.ToLowerInvariant()
  $matchingRuns = @($Runs | Where-Object {
    ([string]$_.headSha).ToLowerInvariant() -eq $expected -and
      [string]$_.headBranch -eq $ExpectedHeadBranch -and
      [string]$_.event -eq 'push'
  } | Sort-Object { [datetime]$_.createdAt } -Descending)
  if ($matchingRuns.Count -eq 0) { throw "PRE_PR_CI_EVIDENCE_MISSING: no push CI run found for branch $ExpectedHeadBranch at HEAD $expected" }

  $run = $matchingRuns[0]
  if ([string]$run.status -ne 'completed' -or [string]$run.conclusion -ne 'success') {
    throw "PRE_PR_CI_NOT_GREEN: latest push CI for HEAD $expected is status=$($run.status), conclusion=$($run.conclusion)"
  }

  $jobsByName = @{}
  foreach ($job in $Jobs) { $jobsByName[[string]$job.name] = $job }
  $missing = [System.Collections.Generic.List[string]]::new()
  $failed = [System.Collections.Generic.List[string]]::new()
  foreach ($name in $script:PrePrRequiredJobs) {
    if (!$jobsByName.ContainsKey($name)) {
      $missing.Add($name)
      continue
    }
    $job = $jobsByName[$name]
    if ([string]$job.status -ne 'completed' -or [string]$job.conclusion -ne 'success') {
      $failed.Add("$name(status=$($job.status),conclusion=$($job.conclusion))")
    }
  }
  if ($missing.Count -gt 0) { throw "PRE_PR_CI_JOB_EVIDENCE_MISSING: $($missing -join ', ')" }
  if ($failed.Count -gt 0) { throw "PRE_PR_CI_JOB_NOT_GREEN: $($failed -join ', ')" }

  return [pscustomobject]@{
    status = 'PASS'
    headSha = $expected
    headBranch = $ExpectedHeadBranch
    event = 'push'
    runId = [long]$run.databaseId
    url = [string]$run.url
    requiredJobs = @($script:PrePrRequiredJobs)
  }
}

function Invoke-GhJson {
  param([Parameter(Mandatory)][string[]]$Command)
  $output = @(& gh @Command 2>&1)
  $exitCode = $LASTEXITCODE
  if ($exitCode -ne 0) { throw "PRE_PR_CI_GH_FAILED: gh $($Command -join ' ') exited $exitCode`n$($output -join "`n")" }
  return (($output -join "`n") | ConvertFrom-Json)
}

function Invoke-PrePrCiEvidenceGate {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$RepositoryRoot,
    [string]$ExpectedHeadSha
  )

  $resolvedRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
  Push-Location $resolvedRoot
  try {
    $currentHeadOutput = @(& git rev-parse HEAD 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "PRE_PR_CI_GIT_FAILED: $($currentHeadOutput -join "`n")" }
    $currentHead = ([string]($currentHeadOutput -join '')).Trim().ToLowerInvariant()
    if ($currentHead -notmatch '^[a-f0-9]{40}$') { throw "PRE_PR_CI_INVALID_HEAD: $currentHead" }
    if ($ExpectedHeadSha -and $ExpectedHeadSha.ToLowerInvariant() -ne $currentHead) {
      throw "PRE_PR_CI_HEAD_MISMATCH: expected=$($ExpectedHeadSha.ToLowerInvariant()), current=$currentHead"
    }

    $currentBranchOutput = @(& git branch --show-current 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "PRE_PR_CI_GIT_FAILED: $($currentBranchOutput -join "`n")" }
    $currentBranch = ([string]($currentBranchOutput -join '')).Trim()
    if (!$currentBranch -or $currentBranch -in @('master','main')) { throw "PRE_PR_CI_INVALID_FEATURE_BRANCH: $currentBranch" }

    & git diff --quiet --exit-code
    if ($LASTEXITCODE -ne 0) { throw 'PRE_PR_CI_TRACKED_WORKTREE_DIRTY: unstaged tracked changes exist' }
    & git diff --cached --quiet --exit-code
    if ($LASTEXITCODE -ne 0) { throw 'PRE_PR_CI_TRACKED_WORKTREE_DIRTY: staged changes exist' }

    $runs = @(Invoke-GhJson @('run','list','--workflow','ci.yml','--branch',$currentBranch,'--commit',$currentHead,'--event','push','--limit','20','--json','databaseId,headBranch,headSha,event,status,conclusion,url,createdAt'))
    $matchingRuns = @($runs | Where-Object {
      ([string]$_.headSha).ToLowerInvariant() -eq $currentHead -and
        [string]$_.headBranch -eq $currentBranch -and
        [string]$_.event -eq 'push'
    } | Sort-Object { [datetime]$_.createdAt } -Descending)
    if ($matchingRuns.Count -eq 0) { throw "PRE_PR_CI_EVIDENCE_MISSING: no push CI run found for branch $currentBranch at HEAD $currentHead" }

    $latestRun = $matchingRuns[0]
    $view = Invoke-GhJson @('run','view',([string]$latestRun.databaseId),'--json','jobs')
    return Test-PrePrCiEvidence -ExpectedHeadSha $currentHead -ExpectedHeadBranch $currentBranch -Runs $runs -Jobs @($view.jobs)
  } finally {
    Pop-Location
  }
}

if ($MyInvocation.InvocationName -ne '.') {
  Invoke-PrePrCiEvidenceGate -RepositoryRoot $RepoRoot -ExpectedHeadSha $HeadSha | ConvertTo-Json -Depth 4
}
