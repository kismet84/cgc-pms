param()
$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $scriptDir 'autopilot-report-projection.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-report-projection-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $evidencePath = Join-Path $root 'evidence.json'
  [IO.File]::WriteAllText($evidencePath, '{"schemaVersion":2,"evidenceId":"ev-1","sourceEvidenceId":null,"executionMode":"EXECUTED","evidenceCategory":"UNIT_BUILD","commandHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","diffHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","classification":"pass","exitCode":0}', [Text.UTF8Encoding]::new($false))
  $reportPath = Join-Path $root 'report.md'
  [IO.File]::WriteAllText($reportPath, "# Report`n`n人工裁决：保留。`n`n新增后续项=0`n关闭后续项=0`n后续项净变化=0`n", [Text.UTF8Encoding]::new($false))
  $metrics = [pscustomobject]@{ executorInvocationCount=1; reviewerInvocationCount=0; contextBaseBuildCount=1; contextDeltaBuildCount=2; validationExecutedCount=1; validationReusedCount=1; reportProjectionCount=1; implementationDispatchCount=1; validationDispatchCount=1; reviewDispatchCount=0; repairDispatchCount=0; closeoutDispatchCount=1; runResumeCount=0; phaseRestartCount=0; wallClockSeconds=2; phaseDurationsSeconds=[pscustomobject]@{}; tokenUsageStatus='not_available'; inputTokens=$null; outputTokens=$null; totalTokens=$null }
  $summary = New-AutopilotMetricsSummary -Metrics $metrics
  $issue = [pscustomobject]@{ issueId='ISSUE-TEST-1'; readyContentHash=('1'*64); archiveReport='docs/quality/report.md' }
  $followups = Get-AutopilotReportFollowupSummary -ReportPath $reportPath
  $facts = New-AutopilotPreCloseoutFacts -Issue $issue -ImplementationCommit ('2'*40) -EvidencePaths @($evidencePath) -ReviewRequired $false -ReviewDecision 'NOT_REQUIRED' -VerifiedDiffHash ('3'*64) -Followups $followups -MetricsSummary $summary -ControlPlaneFingerprint ('4'*64)
  $first = Set-AutopilotReportProjection -ReportPath $reportPath -Facts $facts
  $firstBytes = [IO.File]::ReadAllBytes($reportPath)
  $second = Set-AutopilotReportProjection -ReportPath $reportPath -Facts $facts
  $secondBytes = [IO.File]::ReadAllBytes($reportPath)
  if ([Convert]::ToBase64String($firstBytes) -ne [Convert]::ToBase64String($secondBytes)) { throw 'PreCloseout Facts projection is not byte stable' }
  $text = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8
  if ($text -notmatch '人工裁决：保留' -or $text -match '(?i)closeoutCommit|reportHash|resultHash|REGISTERED') { throw 'projection changed manual content or introduced self-referential fields' }
  $snapshotPath = Join-Path $root 'final-result.json'
  $snapshot = Write-AutopilotFrozenResultSnapshot -Path $snapshotPath -Result ([pscustomobject]@{ issueId='ISSUE-TEST-1'; outcome='PASSED' })
  $snapshotAgain = Write-AutopilotFrozenResultSnapshot -Path $snapshotPath -Result ([pscustomobject]@{ issueId='ISSUE-TEST-1'; outcome='PASSED' })
  if (!$snapshotAgain.idempotent -or $snapshot.resultHash -ne $snapshotAgain.resultHash) { throw 'frozen result is not idempotent' }
  Write-Host 'report projection self-test passed'
} finally { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
