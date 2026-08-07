[CmdletBinding()]
param(
  [string]$RepoRoot,
  [string]$Repository = $env:GITHUB_REPOSITORY,
  [ValidatePattern('^[a-fA-F0-9]{40}$')][string]$MergeSha = $env:GITHUB_SHA
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
  $RepoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
}
. (Join-Path $RepoRoot 'scripts\codex-autopilot\verify-pre-pr-ci.ps1')

function Assert-RequiredCiJobs {
  param(
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Jobs,
    [Parameter(Mandatory)][string]$ErrorCode
  )
  $failed = @($script:PrePrRequiredJobs | Where-Object {
    $name = $_
    $matches = @($Jobs | Where-Object { [string]$_.name -eq $name })
    $matches.Count -ne 1 -or [string]$matches[0].status -ne 'completed' -or [string]$matches[0].conclusion -ne 'success'
  })
  if ($failed.Count -gt 0) { throw "${ErrorCode}: $($failed -join ', ')" }
}

function Test-PostMergeCiEvidence {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$ExpectedRepository,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$ExpectedMergeSha,
    [Parameter(Mandatory)]$MergedPull,
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$PushRuns,
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$PushJobs,
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$PullRequestRuns,
    [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$PullRequestJobs,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$MergeTreeSha,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$SourceTreeSha
  )

  $mergeSha = $ExpectedMergeSha.ToLowerInvariant()
  if (!$MergedPull.merged_at -or [string]$MergedPull.merge_commit_sha -ne $mergeSha) {
    throw "POST_MERGE_PR_EVIDENCE_MISSING: $mergeSha is not the merge commit of the selected PR"
  }
  if ([string]$MergedPull.base.ref -notin @('master', 'main')) {
    throw "POST_MERGE_INVALID_BASE: $($MergedPull.base.ref)"
  }
  if ($MergeTreeSha.ToLowerInvariant() -ne $SourceTreeSha.ToLowerInvariant()) {
    throw "POST_MERGE_TREE_MISMATCH: merge=$($MergeTreeSha.ToLowerInvariant()), source=$($SourceTreeSha.ToLowerInvariant())"
  }

  $sourceHeadSha = ([string]$MergedPull.head.sha).ToLowerInvariant()
  $sourceBranch = [string]$MergedPull.head.ref
  $internalPullRequest = [string]$MergedPull.head.repo.full_name -eq $ExpectedRepository
  $pushEvidence = $null
  if ($internalPullRequest) {
    $pushEvidence = Test-PrePrCiEvidence `
      -ExpectedHeadSha $sourceHeadSha `
      -ExpectedHeadBranch $sourceBranch `
      -Runs $PushRuns `
      -Jobs $PushJobs
  }

  $expectedTitle = "CI pull_request $([int]$MergedPull.number)"
  $prRuns = @($PullRequestRuns | Where-Object {
    ([string]$_.headSha).ToLowerInvariant() -eq $sourceHeadSha -and
      [string]$_.headBranch -eq $sourceBranch -and
      [string]$_.event -eq 'pull_request' -and
      [string]$_.displayTitle -eq $expectedTitle
  } | Sort-Object { [datetime]$_.createdAt } -Descending)
  if ($prRuns.Count -eq 0) {
    throw "POST_MERGE_PR_CI_EVIDENCE_MISSING: no pull_request CI run named '$expectedTitle' for $sourceBranch at $sourceHeadSha"
  }
  $prRun = $prRuns[0]
  if ([string]$prRun.status -ne 'completed' -or [string]$prRun.conclusion -ne 'success') {
    throw "POST_MERGE_PR_CI_NOT_GREEN: status=$($prRun.status), conclusion=$($prRun.conclusion)"
  }
  $evidenceJobs = @($PullRequestJobs | Where-Object { [string]$_.name -eq 'pr-push-evidence' })
  if ($evidenceJobs.Count -ne 1) {
    throw "POST_MERGE_PR_PUSH_EVIDENCE_MISSING: count=$($evidenceJobs.Count)"
  }
  if ([string]$evidenceJobs[0].status -ne 'completed' -or [string]$evidenceJobs[0].conclusion -ne 'success') {
    throw "POST_MERGE_PR_PUSH_EVIDENCE_NOT_GREEN: status=$($evidenceJobs[0].status), conclusion=$($evidenceJobs[0].conclusion)"
  }
  if (!$internalPullRequest) {
    Assert-RequiredCiJobs -Jobs $PullRequestJobs -ErrorCode 'POST_MERGE_FORK_FULL_CI_NOT_GREEN'
  }

  return [pscustomobject]@{
    status = 'PASS'
    mode = $(if ($internalPullRequest) { 'REUSED_PUSH_CI' } else { 'FORK_FULL_PR_CI' })
    mergeSha = $mergeSha
    treeSha = $MergeTreeSha.ToLowerInvariant()
    pullRequest = [int]$MergedPull.number
    sourceHeadSha = $sourceHeadSha
    sourceBranch = $sourceBranch
    pushRunId = $(if ($pushEvidence) { [long]$pushEvidence.runId } else { $null })
    pullRequestRunId = [long]$prRun.databaseId
    requiredJobs = @($script:PrePrRequiredJobs)
  }
}

function Invoke-PostMergeCiEvidenceGate {
  if ([string]::IsNullOrWhiteSpace($Repository)) {
    throw 'POST_MERGE_REPOSITORY_MISSING: pass -Repository or set GITHUB_REPOSITORY'
  }
  if ([string]::IsNullOrWhiteSpace($MergeSha)) {
    throw 'POST_MERGE_SHA_MISSING: pass -MergeSha or set GITHUB_SHA'
  }

  $mergeShaLower = $MergeSha.ToLowerInvariant()
  $pulls = @(Invoke-GhJson @(
    'api', "repos/$Repository/commits/$mergeShaLower/pulls?per_page=100",
    '-H', 'Accept: application/vnd.github+json'
  ))
  $mergedPull = $pulls |
    Where-Object {
      $_.merged_at -and
      [string]$_.merge_commit_sha -eq $mergeShaLower -and
      [string]$_.base.ref -in @('master', 'main')
    } |
    Sort-Object { [datetime]$_.merged_at } -Descending |
    Select-Object -First 1
  if (!$mergedPull) {
    throw "POST_MERGE_PR_EVIDENCE_MISSING: $mergeShaLower is not the merge commit of a merged PR targeting master/main"
  }

  $sourceHeadSha = ([string]$mergedPull.head.sha).ToLowerInvariant()
  $sourceBranch = [string]$mergedPull.head.ref
  $runFields = 'databaseId,displayTitle,headBranch,headSha,event,status,conclusion,url,createdAt'
  $pushRuns = @()
  $pushJobs = @()
  if ([string]$mergedPull.head.repo.full_name -eq $Repository) {
    $pushRuns = @(Invoke-GhJson @(
      'run', 'list', '--repo', $Repository, '--workflow', 'ci.yml', '--branch', $sourceBranch,
      '--commit', $sourceHeadSha, '--event', 'push', '--limit', '20', '--json', $runFields
    ))
    $pushRun = @($pushRuns | Where-Object {
      ([string]$_.headSha).ToLowerInvariant() -eq $sourceHeadSha -and [string]$_.event -eq 'push'
    } | Sort-Object { [datetime]$_.createdAt } -Descending | Select-Object -First 1)
    if ($pushRun.Count -eq 0) {
      throw "POST_MERGE_PUSH_CI_EVIDENCE_MISSING: no push run for $sourceHeadSha"
    }
    $pushView = Invoke-GhJson @('run', 'view', ([string]$pushRun[0].databaseId), '--repo', $Repository, '--json', 'jobs')
    $pushJobs = @($pushView.jobs)
  }

  $prRuns = @(Invoke-GhJson @(
    'run', 'list', '--repo', $Repository, '--workflow', 'ci.yml', '--branch', $sourceBranch,
    '--commit', $sourceHeadSha, '--event', 'pull_request', '--limit', '20', '--json', $runFields
  ))
  $prRun = @($prRuns | Where-Object {
    ([string]$_.headSha).ToLowerInvariant() -eq $sourceHeadSha -and
      [string]$_.event -eq 'pull_request' -and
      [string]$_.displayTitle -eq "CI pull_request $([int]$mergedPull.number)"
  } | Sort-Object { [datetime]$_.createdAt } -Descending | Select-Object -First 1)
  if ($prRun.Count -eq 0) {
    throw "POST_MERGE_PR_CI_EVIDENCE_MISSING: no pull_request run for PR #$($mergedPull.number) at $sourceHeadSha"
  }
  $prView = Invoke-GhJson @('run', 'view', ([string]$prRun[0].databaseId), '--repo', $Repository, '--json', 'jobs')
  $mergeCommit = Invoke-GhJson @('api', "repos/$Repository/git/commits/$mergeShaLower")
  $sourceCommit = Invoke-GhJson @('api', "repos/$Repository/git/commits/$sourceHeadSha")

  return Test-PostMergeCiEvidence `
    -ExpectedRepository $Repository `
    -ExpectedMergeSha $mergeShaLower `
    -MergedPull $mergedPull `
    -PushRuns $pushRuns `
    -PushJobs $pushJobs `
    -PullRequestRuns $prRuns `
    -PullRequestJobs @($prView.jobs) `
    -MergeTreeSha ([string]$mergeCommit.tree.sha) `
    -SourceTreeSha ([string]$sourceCommit.tree.sha)
}

if ($MyInvocation.InvocationName -ne '.') {
  Invoke-PostMergeCiEvidenceGate | ConvertTo-Json -Depth 4
}
