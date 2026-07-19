param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'verify-pre-pr-ci.ps1')

function New-Run([string]$Sha,[string]$Status='completed',[string]$Conclusion='success',[string]$CreatedAt='2026-07-19T00:00:00Z',[string]$Event='push',[string]$Branch='codex/example') {
  return [pscustomobject]@{ databaseId=123; headBranch=$Branch; headSha=$Sha; event=$Event; status=$Status; conclusion=$Conclusion; url='https://example.invalid/run/123'; createdAt=$CreatedAt }
}

function New-Jobs {
  return @($script:PrePrRequiredJobs | ForEach-Object { [pscustomobject]@{ name=$_; status='completed'; conclusion='success' } })
}

function Assert-Rejected([scriptblock]$Action,[string]$Pattern) {
  $rejected = $false
  try { & $Action | Out-Null } catch { $rejected = $_.Exception.Message -match $Pattern }
  if (!$rejected) { throw "pre-PR CI evidence fixture was not rejected: $Pattern" }
}

$sha = 'a' * 40
$branch = 'codex/example'
$result = Test-PrePrCiEvidence -ExpectedHeadSha $sha -ExpectedHeadBranch $branch -Runs @((New-Run $sha)) -Jobs (New-Jobs)
if ($result.status -ne 'PASS' -or $result.headSha -ne $sha -or @($result.requiredJobs).Count -ne 13) { throw 'valid pre-PR CI evidence was rejected or incomplete' }

$missingJobs = @(New-Jobs | Where-Object { $_.name -ne 'e2e' })
Assert-Rejected { Test-PrePrCiEvidence -ExpectedHeadSha $sha -ExpectedHeadBranch $branch -Runs @((New-Run $sha)) -Jobs $missingJobs } 'PRE_PR_CI_JOB_EVIDENCE_MISSING.*e2e'

$failedJobs = @(New-Jobs)
($failedJobs | Where-Object { $_.name -eq 'frontend-v2-gate' }).conclusion = 'failure'
Assert-Rejected { Test-PrePrCiEvidence -ExpectedHeadSha $sha -ExpectedHeadBranch $branch -Runs @((New-Run $sha)) -Jobs $failedJobs } 'PRE_PR_CI_JOB_NOT_GREEN.*frontend-v2-gate'

Assert-Rejected { Test-PrePrCiEvidence -ExpectedHeadSha $sha -ExpectedHeadBranch $branch -Runs @((New-Run $sha 'completed' 'failure')) -Jobs (New-Jobs) } 'PRE_PR_CI_NOT_GREEN'
Assert-Rejected { Test-PrePrCiEvidence -ExpectedHeadSha $sha -ExpectedHeadBranch $branch -Runs @((New-Run ('b' * 40))) -Jobs (New-Jobs) } 'PRE_PR_CI_EVIDENCE_MISSING'
Assert-Rejected { Test-PrePrCiEvidence -ExpectedHeadSha $sha -ExpectedHeadBranch $branch -Runs @((New-Run $sha 'completed' 'success' '2026-07-19T00:00:00Z' 'pull_request')) -Jobs (New-Jobs) } 'PRE_PR_CI_EVIDENCE_MISSING'
Assert-Rejected { Test-PrePrCiEvidence -ExpectedHeadSha $sha -ExpectedHeadBranch $branch -Runs @((New-Run $sha 'completed' 'success' '2026-07-19T00:00:00Z' 'push' 'master')) -Jobs (New-Jobs) } 'PRE_PR_CI_EVIDENCE_MISSING'

Write-Host 'pre-PR CI evidence gate self-test passed'
