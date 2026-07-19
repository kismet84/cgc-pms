[CmdletBinding()]
param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [string]$Repository = $env:GITHUB_REPOSITORY,
  [ValidatePattern('^[a-fA-F0-9]{40}$')][string]$MergeSha = $env:GITHUB_SHA
)

$ErrorActionPreference = 'Stop'

function Invoke-GhJson([string[]]$Arguments) {
  $output = @(& gh @Arguments)
  if ($LASTEXITCODE -ne 0) {
    throw "gh command failed: gh $($Arguments -join ' ')"
  }
  $text = $output -join "`n"
  if ([string]::IsNullOrWhiteSpace($text)) { return $null }
  return $text | ConvertFrom-Json
}

if ([string]::IsNullOrWhiteSpace($Repository)) {
  throw 'POST_MERGE_REPOSITORY_MISSING: pass -Repository or set GITHUB_REPOSITORY'
}
if ([string]::IsNullOrWhiteSpace($MergeSha)) {
  throw 'POST_MERGE_SHA_MISSING: pass -MergeSha or set GITHUB_SHA'
}

$prePrGatePath = Join-Path $RepoRoot 'scripts\codex-autopilot\verify-pre-pr-ci.ps1'
. $prePrGatePath
$requiredJobs = @($script:PrePrRequiredJobs)

$pulls = @(Invoke-GhJson @(
  'api', "repos/$Repository/commits/$MergeSha/pulls?per_page=100",
  '-H', 'Accept: application/vnd.github+json'
))
$mergedPull = $pulls |
  Where-Object {
    $_.merged_at -and
    [string]$_.merge_commit_sha -eq $MergeSha -and
    [string]$_.base.ref -in @('master', 'main')
  } |
  Sort-Object { [datetime]$_.merged_at } -Descending |
  Select-Object -First 1

if (!$mergedPull) {
  throw "POST_MERGE_PR_EVIDENCE_MISSING: $MergeSha is not the merge commit of a merged PR targeting master/main"
}

$sourceHeadSha = [string]$mergedPull.head.sha
$checkResponse = Invoke-GhJson @(
  'api', "repos/$Repository/commits/$sourceHeadSha/check-runs?filter=latest&per_page=100",
  '-H', 'Accept: application/vnd.github+json'
)
$successfulJobs = @($checkResponse.check_runs |
  Where-Object { [string]$_.status -eq 'completed' -and [string]$_.conclusion -eq 'success' } |
  ForEach-Object { [string]$_.name } |
  Sort-Object -Unique)
$missingJobs = @($requiredJobs | Where-Object { $successfulJobs -notcontains $_ })
if ($missingJobs.Count -gt 0) {
  throw "POST_MERGE_CI_EVIDENCE_MISSING: PR #$($mergedPull.number) source HEAD $sourceHeadSha lacks successful jobs: $($missingJobs -join ', ')"
}

[pscustomobject]@{
  status = 'PASS'
  mergeSha = $MergeSha
  pullRequest = [int]$mergedPull.number
  sourceHeadSha = $sourceHeadSha
  requiredJobs = $requiredJobs
} | ConvertTo-Json -Depth 4
