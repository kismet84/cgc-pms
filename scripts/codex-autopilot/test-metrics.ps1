param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-metrics.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-metrics-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $events = Join-Path $root 'events.ndjson'
  @(
    @{runId='run-a';issueId='ISSUE-1';to='SELECTING';timestamp='2026-07-11T12:00:00+08:00';reason='select'},
    @{runId='run-a';issueId='ISSUE-1';to='EXECUTING';timestamp='2026-07-11T12:00:10+08:00';reason='execute'},
    @{runId='run-a';issueId='ISSUE-1';to='VERIFYING';timestamp='2026-07-11T12:00:40+08:00';reason='verify'},
    @{runId='run-a';issueId='ISSUE-1';to='CLOSING';timestamp='2026-07-11T12:01:00+08:00';reason='close'},
    @{runId='run-a';issueId='ISSUE-1';to='CLOSING';timestamp='2026-07-11T12:01:00+08:00';reason='close'},
    @{runId='run-b';issueId='ISSUE-2';to='EXECUTING';timestamp='2026-07-11T13:00:00+08:00';reason='other'}
  ) | ForEach-Object { $_ | ConvertTo-Json -Compress } | Set-Content -LiteralPath $events -Encoding UTF8
  $metrics = Get-AutopilotRunMetrics -EventPath $events -RunId 'run-a'
  if ($metrics.eventCount -ne 4) { throw 'duplicate or cross-run events were counted' }
  if ($metrics.activeSeconds -ne 30 -or $metrics.verifySeconds -ne 20) { throw 'phase durations are wrong' }
  if ($null -ne $metrics.reviewSeconds) { throw 'missing review phase was fabricated as zero' }

  @(
    @{runId='run-c';issueId='ISSUE-3';to='EXECUTING';timestamp='2026-07-11T12:10:00+08:00';reason='execute'},
    @{runId='run-c';issueId='ISSUE-3';to='VERIFYING';timestamp='2026-07-11T12:09:00+08:00';reason='clock-regressed'}
  ) | ForEach-Object { $_ | ConvertTo-Json -Compress } | Set-Content -LiteralPath $events -Encoding UTF8
  $invalid = Get-AutopilotRunMetrics -EventPath $events -RunId 'run-c'
  if ($invalid.valid) { throw 'negative event duration produced valid metrics' }

  $runs = Join-Path $root 'runs'
  1..20 | ForEach-Object {
    $runDir = Join-Path $runs ("run-{0:00}" -f $_)
    New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    $issueId = "ISSUE-$_"
    $evidencePath = Join-Path $runDir 'evidence.json'
    $baseCommit = "base-$_"; $diffHash = "diff-$_"
    [ordered]@{issueId=$issueId;exitCode=0;classification='pass';baseCommit=$baseCommit;diffHash=$diffHash} | ConvertTo-Json | Set-Content -LiteralPath $evidencePath -Encoding UTF8
    [ordered]@{
      issueId = $issueId
      createdAt = [datetimeoffset]::Now.AddMinutes(-$_).ToString('o')
      status = 'done'
      firstPassSuccess = ($_ -le 18)
      manualInterventionCount = 0
      scopeViolationCount = 0
      evidencePaths = @($evidencePath)
      verificationBaseCommit = $baseCommit
      verifiedDiffHash = $diffHash
      reviewRequired = $false
      gitSummary = @{ commit = "commit-$_" }
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runDir 'result.json') -Encoding UTF8
  }
  $qualification = Get-AutopilotQualification -RunsDir $runs -WindowSize 20
  if (!$qualification.qualified -or $qualification.firstPassRate -ne 0.9) { throw 'valid qualification window was rejected' }
  $failedResultPath = Join-Path $runs 'run-20\result.json'
  $failedResult = Get-Content -Encoding UTF8 -LiteralPath $failedResultPath -Raw | ConvertFrom-Json
  $failedResult.manualInterventionCount = 1
  $failedResult | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $failedResultPath -Encoding UTF8
  $rejected = Get-AutopilotQualification -RunsDir $runs -WindowSize 20
  if ($rejected.qualified -or $rejected.reasons -notcontains 'manual intervention count is not zero') { throw 'manual intervention did not fail qualification' }
  $failedResult.manualInterventionCount = 0
  $failedResult.createdAt = 'not-a-time'
  $failedResult | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $failedResultPath -Encoding UTF8
  $invalidTimestamp = Get-AutopilotQualification -RunsDir $runs -WindowSize 20
  if ($invalidTimestamp.qualified -or $invalidTimestamp.reasons -notcontains 'qualification window contains invalid result timestamp') { throw 'invalid result timestamp was silently excluded' }

  Write-Host 'metrics self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
