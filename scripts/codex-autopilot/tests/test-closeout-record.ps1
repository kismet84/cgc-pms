param()
$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $scriptDir 'autopilot-recover.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-closeout-record-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $ledger = Join-Path $root 'closeouts.ndjson'
  $reportPath = 'docs/quality/issue.md'; $commit = '1' * 40
  $key = Get-AutopilotCloseoutKey -IssueId 'ISSUE-TEST-2' -Commit $commit -ReportPath $reportPath
  $metrics = [pscustomobject]@{ executorInvocationCount=1; reviewerInvocationCount=0; plannerInvocationCount=0; contextBaseBuildCount=1; contextDeltaBuildCount=2; validationExecutedCount=2; validationReusedCount=1; reportProjectionCount=1; wallClockSeconds=5 }
  $record = [pscustomobject][ordered]@{ schemaVersion=2; key=$key; issueId='ISSUE-TEST-2'; readyContentHash=('2'*64); outcome='PASSED'; implementationCommit=('3'*40); closeoutCommit=$commit; reportPath=$reportPath; reportHash=('4'*64); resultHash=('5'*64); preCloseoutFactsHash=('6'*64); evidenceManifestHash=('7'*64); reviewRequired=$false; reviewDecision='NOT_REQUIRED'; verifiedDiffHash=('8'*64); followupsAdded=0; followupsClosed=0; followupsNetChange=0; metricsSummary=$metrics; metricsHash=('9'*64); controlPlaneFingerprint=('a'*64); graphCursorRequired=$false; graphGitCursor='' }
  $first = Register-AutopilotCloseout -LedgerPath $ledger -Record $record
  $again = Register-AutopilotCloseout -LedgerPath $ledger -Record $record
  if ($first.idempotent -or !$again.idempotent -or $again.record.registeredAt -ne $first.record.registeredAt) { throw 'v2 registration is not idempotent' }
  $conflicting = $record.PSObject.Copy(); $conflicting.reportHash = 'b' * 64
  $conflictRejected = $false
  try { Register-AutopilotCloseout -LedgerPath $ledger -Record $conflicting | Out-Null } catch { $conflictRejected = $_.Exception.Message -match 'integrity_conflict' }
  if (!$conflictRejected) { throw 'same key with different payload was not rejected' }
  $recent = Get-AutopilotRecentCloseoutMetrics -LedgerPath $ledger
  if ($recent.actualSampleCount -ne 1 -or $recent.status -ne 'insufficient_sample' -or $recent.aggregate.metrics.executorInvocationCount.p50 -ne 1) { throw 'recent closeout metrics aggregation failed' }
  $emptyRecent = Get-AutopilotRecentCloseoutMetrics -LedgerPath (Join-Path $root 'missing.ndjson')
  if ($emptyRecent.actualSampleCount -ne 0 -or $emptyRecent.status -ne 'insufficient_sample' -or $emptyRecent.aggregate.metrics.executorInvocationCount.sampleCount -ne 0) { throw 'empty recent closeout metrics aggregation failed' }
  $legacyLedger = Join-Path $root 'legacy.ndjson'; [IO.File]::WriteAllText($legacyLedger, ('{"key":"legacy","registeredAt":"2026-01-01T00:00:00Z"}' + [Environment]::NewLine), [Text.UTF8Encoding]::new($false))
  $legacy = Register-AutopilotCloseout -LedgerPath $legacyLedger -Key legacy
  if ($legacy.status -ne 'LEGACY_REGISTERED' -or @(Get-Content -LiteralPath $legacyLedger).Count -ne 1) { throw 'legacy closeout compatibility failed' }
  Write-Host 'closeout record self-test passed'
} finally { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
