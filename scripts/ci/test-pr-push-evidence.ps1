param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'verify-pr-push-evidence.ps1')

function New-PullRequest([string]$HeadSha,[string]$BaseSha,[string]$Repository='kismet84/cgc-pms',[string]$Branch='codex/example') {
  return [pscustomobject]@{
    number = 7
    state = 'open'
    head = [pscustomobject]@{ sha=$HeadSha; ref=$Branch; repo=[pscustomobject]@{ full_name=$Repository } }
    base = [pscustomobject]@{ sha=$BaseSha; ref='master' }
  }
}

function New-Run([string]$Sha,[string]$Branch='codex/example',[string]$Event='push') {
  return [pscustomobject]@{
    databaseId=123; headBranch=$Branch; headSha=$Sha; event=$Event
    status='completed'; conclusion='success'; url='https://example.invalid/run/123'; createdAt='2026-08-07T00:00:00Z'
  }
}

function New-Jobs {
  return @($script:PrePrRequiredJobs | ForEach-Object { [pscustomobject]@{ name=$_; status='completed'; conclusion='success' } })
}

function Assert-Rejected([scriptblock]$Action,[string]$Pattern) {
  $rejected = $false
  try { & $Action | Out-Null } catch { $rejected = $_.Exception.Message -match $Pattern }
  if (!$rejected) { throw "PR push evidence fixture was not rejected: $Pattern" }
}

$headSha = 'a' * 40
$baseSha = 'b' * 40
$repository = 'kismet84/cgc-pms'
$arguments = @{
  ExpectedRepository = $repository
  ExpectedPullRequestNumber = 7
  ExpectedHeadSha = $headSha
  ExpectedBaseSha = $baseSha
  PullRequest = New-PullRequest $headSha $baseSha
  Comparison = [pscustomobject]@{ behind_by=0 }
  ChangedFiles = @([pscustomobject]@{ filename='frontend-admin-v2/src/pages/ExamplePage.vue' })
  Runs = @((New-Run $headSha))
  Jobs = New-Jobs
}

$result = Test-PrPushCiEvidence @arguments
if ($result.status -ne 'PASS' -or $result.mode -ne 'REUSED_PUSH_CI' -or $result.runId -ne 123) {
  throw 'valid PR push evidence was rejected or incomplete'
}

$fork = $arguments.Clone(); $fork.PullRequest = New-PullRequest $headSha $baseSha 'outside/fork'
Assert-Rejected { Test-PrPushCiEvidence @fork } 'PR_PUSH_CI_FORK_REQUIRES_FULL'
$behind = $arguments.Clone(); $behind.Comparison = [pscustomobject]@{ behind_by=1 }
Assert-Rejected { Test-PrPushCiEvidence @behind } 'PR_PUSH_CI_HEAD_BEHIND_BASE'
$controlPlane = $arguments.Clone(); $controlPlane.ChangedFiles = @([pscustomobject]@{ filename='.github/workflows/ci.yml' })
Assert-Rejected { Test-PrPushCiEvidence @controlPlane } 'PR_PUSH_CI_CONTROL_PLANE_CHANGED'
$selectorControlPlane = $arguments.Clone(); $selectorControlPlane.ChangedFiles = @([pscustomobject]@{ filename='frontend-admin-v2/scripts/e2e-spec-groups.mjs' })
Assert-Rejected { Test-PrPushCiEvidence @selectorControlPlane } 'PR_PUSH_CI_CONTROL_PLANE_CHANGED'
$frontendScriptControlPlane = $arguments.Clone(); $frontendScriptControlPlane.ChangedFiles = @([pscustomobject]@{ filename='frontend-admin-v2/scripts/generate-route-ledger.mjs' })
Assert-Rejected { Test-PrPushCiEvidence @frontendScriptControlPlane } 'PR_PUSH_CI_CONTROL_PLANE_CHANGED'
$wrongHead = $arguments.Clone(); $wrongHead.PullRequest = New-PullRequest ('c' * 40) $baseSha
Assert-Rejected { Test-PrPushCiEvidence @wrongHead } 'PR_PUSH_CI_HEAD_MISMATCH'
$wrongBase = $arguments.Clone(); $wrongBase.PullRequest = New-PullRequest $headSha ('d' * 40)
Assert-Rejected { Test-PrPushCiEvidence @wrongBase } 'PR_PUSH_CI_BASE_MISMATCH'
$wrongEvent = $arguments.Clone(); $wrongEvent.Runs = @((New-Run $headSha 'codex/example' 'pull_request'))
Assert-Rejected { Test-PrPushCiEvidence @wrongEvent } 'PRE_PR_CI_EVIDENCE_MISSING'
$missingJob = $arguments.Clone(); $missingJob.Jobs = @(New-Jobs | Where-Object { $_.name -ne 'backend-order-sensitive' })
Assert-Rejected { Test-PrPushCiEvidence @missingJob } 'PRE_PR_CI_JOB_EVIDENCE_MISSING.*backend-order-sensitive'

Write-Host 'PR push CI evidence self-test passed'
