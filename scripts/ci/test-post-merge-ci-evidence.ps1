param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'verify-post-merge-ci.ps1')

function New-Run([string]$Sha,[string]$Event,[long]$Id,[string]$Title='') {
  return [pscustomobject]@{
    databaseId=$Id; headBranch='codex/example'; headSha=$Sha; event=$Event
    status='completed'; conclusion='success'; createdAt='2026-08-07T00:00:00Z'
    url="https://example.invalid/run/$Id"; displayTitle=$Title
  }
}

function New-PushJobs {
  return @($script:PrePrRequiredJobs | ForEach-Object { [pscustomobject]@{ name=$_; status='completed'; conclusion='success' } })
}

function Assert-Rejected([scriptblock]$Action,[string]$Pattern) {
  $rejected = $false
  try { & $Action | Out-Null } catch { $rejected = $_.Exception.Message -match $Pattern }
  if (!$rejected) { throw "post-merge evidence fixture was not rejected: $Pattern" }
}

$headSha = 'a' * 40
$mergeSha = 'b' * 40
$pull = [pscustomobject]@{
  number=9; merged_at='2026-08-07T00:00:00Z'; merge_commit_sha=$mergeSha
  head=[pscustomobject]@{ sha=$headSha; ref='codex/example'; repo=[pscustomobject]@{ full_name='kismet84/cgc-pms' } }
  base=[pscustomobject]@{ ref='master' }
}
$arguments = @{
  ExpectedRepository='kismet84/cgc-pms'
  ExpectedMergeSha=$mergeSha
  MergedPull=$pull
  PushRuns=@((New-Run $headSha 'push' 101))
  PushJobs=New-PushJobs
  PullRequestRuns=@((New-Run $headSha 'pull_request' 202 'CI pull_request 9'))
  PullRequestJobs=@([pscustomobject]@{ name='pr-push-evidence'; status='completed'; conclusion='success' })
  MergeTreeSha='c' * 40
  SourceTreeSha='c' * 40
}

$result = Test-PostMergeCiEvidence @arguments
if ($result.status -ne 'PASS' -or $result.pushRunId -ne 101 -or $result.pullRequestRunId -ne 202) {
  throw 'valid post-merge evidence was rejected or incomplete'
}

$missingPrEvidence = $arguments.Clone(); $missingPrEvidence.PullRequestJobs = @()
Assert-Rejected { Test-PostMergeCiEvidence @missingPrEvidence } 'POST_MERGE_PR_PUSH_EVIDENCE_MISSING'
$failedPr = $arguments.Clone(); $failedPr.PullRequestRuns = @((New-Run $headSha 'pull_request' 202 'CI pull_request 9')); $failedPr.PullRequestRuns[0].conclusion = 'failure'
Assert-Rejected { Test-PostMergeCiEvidence @failedPr } 'POST_MERGE_PR_CI_NOT_GREEN'
$missingPushJob = $arguments.Clone(); $missingPushJob.PushJobs = @(New-PushJobs | Where-Object { $_.name -ne 'backend-order-sensitive' })
Assert-Rejected { Test-PostMergeCiEvidence @missingPushJob } 'PRE_PR_CI_JOB_EVIDENCE_MISSING.*backend-order-sensitive'
$wrongPr = $arguments.Clone(); $wrongPr.PullRequestRuns = @((New-Run $headSha 'pull_request' 202 'CI pull_request 10'))
Assert-Rejected { Test-PostMergeCiEvidence @wrongPr } 'POST_MERGE_PR_CI_EVIDENCE_MISSING'
$wrongTree = $arguments.Clone(); $wrongTree.SourceTreeSha = 'd' * 40
Assert-Rejected { Test-PostMergeCiEvidence @wrongTree } 'POST_MERGE_TREE_MISMATCH'
$fork = $arguments.Clone(); $fork.MergedPull = $pull.PSObject.Copy(); $fork.MergedPull.head = $pull.head.PSObject.Copy(); $fork.MergedPull.head.repo = [pscustomobject]@{ full_name='outside/fork' }; $fork.PushRuns=@(); $fork.PushJobs=@(); $fork.PullRequestJobs=@(New-PushJobs)
$forkResult = Test-PostMergeCiEvidence @fork
if ($forkResult.mode -ne 'FORK_FULL_PR_CI' -or $null -ne $forkResult.pushRunId) { throw 'fork full PR CI evidence was rejected' }

Write-Host 'post-merge CI evidence self-test passed'
