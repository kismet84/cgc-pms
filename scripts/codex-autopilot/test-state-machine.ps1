param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-state.ps1')

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('autopilot-state-test-' + [guid]::NewGuid().ToString('N'))
$statePath = Join-Path $tempRoot 'state.json'
$eventsPath = Join-Path $tempRoot 'events.ndjson'
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

function New-TestState {
  [ordered]@{
    schemaVersion = 2
    runId = 'run-test'
    status = 'IDLE'
    phase = 'idle'
    currentIssue = ''
    attempt = 0
    startedAt = '2026-07-11T12:00:00+08:00'
    phaseStartedAt = '2026-07-11T12:00:00+08:00'
    lastHeartbeatAt = '2026-07-11T12:00:00+08:00'
    iterationLimit = 3
    completedImplementationIssues = 0
    completedIssueIds = @()
    worktree = ''
    branch = 'codex/test'
    executorPid = $null
    lastCommit = $null
    failureFingerprint = $null
  }
}

try {
  $state = New-TestState
  Write-AutopilotStateAtomic -Path $statePath -State $state | Out-Null
  $read = Read-AutopilotState -Path $statePath
  if ($read.schemaVersion -ne 3 -or $read.status -ne 'IDLE') { throw 'state v2 to v3 round-trip migration failed' }
  if ($read.reviewCycleCompletedCount -ne 0 -or @($read.reviewCycleCompletedIssueIds).Count -ne 0 -or $read.activeScoringVersion) { throw 'state migration fabricated historical scoring data' }
  if ($read.issueCheckpointPath -ne '' -or $read.currentIssuePhase -ne '' -or $read.lastCanaryFingerprint -ne '') { throw 'state migration fabricated recovery or canary evidence' }

  $bound = Resolve-AutopilotIssueStateBinding -Existing ([pscustomobject]@{issueCheckpointPath='old.json';currentIssuePhase='REGISTERED'})
  if ($bound.checkpointPath -ne 'old.json' -or $bound.phase -ne 'REGISTERED') { throw 'active Issue state binding was not preserved' }
  $cleared = Resolve-AutopilotIssueStateBinding -Existing ([pscustomobject]@{issueCheckpointPath='old.json';currentIssuePhase='REGISTERED'}) -Clear
  if ($cleared.checkpointPath -ne '' -or $cleared.phase -ne '') { throw 'closed Issue state binding was restored from stale state' }

  $oldHeartbeat = [datetimeoffset]$read.lastHeartbeatAt
  Start-Sleep -Milliseconds 20
  $moved = Move-AutopilotState -Path $statePath -ToStatus 'CHECKPOINT' -Phase 'checkpoint' -Reason 'test'
  if ($moved.status -ne 'CHECKPOINT') { throw 'legal transition failed' }
  if ([datetimeoffset]$moved.lastHeartbeatAt -le $oldHeartbeat) { throw 'heartbeat was not refreshed' }

  $illegalRejected = $false
  try { Move-AutopilotState -Path $statePath -ToStatus 'MERGING' -Phase 'merge' -Reason 'illegal' | Out-Null } catch { $illegalRejected = $true }
  if (!$illegalRejected) { throw 'illegal transition was accepted' }

  Add-AutopilotCompletedIssue -Path $statePath -IssueId 'ISSUE-TEST-001' | Out-Null
  $deduplicated = Add-AutopilotCompletedIssue -Path $statePath -IssueId 'ISSUE-TEST-001'
  if ($deduplicated.completedImplementationIssues -ne 1) { throw 'duplicate issue incremented completion count' }
  if ($deduplicated.reviewCycleCompletedCount -ne 0) { throw 'legacy completion count leaked into the review cycle' }

  $beforeInvalidWrite = Get-Content -Encoding UTF8 -LiteralPath $statePath -Raw
  $invalid = New-TestState
  $invalid.schemaVersion = 99
  $invalidRejected = $false
  try { Write-AutopilotStateAtomic -Path $statePath -State $invalid } catch { $invalidRejected = $true }
  if (!$invalidRejected) { throw 'unknown schemaVersion was accepted' }
  if ((Get-Content -Encoding UTF8 -LiteralPath $statePath -Raw) -ne $beforeInvalidWrite) { throw 'rejected write replaced valid state' }

  Write-AutopilotEvent -Path $eventsPath -RunId 'run-test' -IssueId 'ISSUE-TEST-001' -From 'IDLE' -To 'CHECKPOINT' -Reason 'test' -EvidencePath 'evidence.json' | Out-Null
  $event = (Get-Content -Encoding UTF8 -LiteralPath $eventsPath -Tail 1) | ConvertFrom-Json
  foreach ($field in 'runId','issueId','from','to','timestamp','reason','evidencePath') {
    if ($event.PSObject.Properties.Name -notcontains $field) { throw "event missing $field" }
  }

  Write-Host 'state machine self-test passed'
} finally {
  Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
