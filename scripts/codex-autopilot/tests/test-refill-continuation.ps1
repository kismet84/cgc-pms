param()

$ErrorActionPreference = 'Stop'
$autoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $autoRoot 'autopilot-state.ps1')
. (Join-Path $autoRoot 'autopilot-transition.ps1')
. (Join-Path $autoRoot 'autopilot-stage-result.ps1')
. (Join-Path $autoRoot 'autopilot-run-coordinator-support.ps1')

$coordinatorText = Get-Content -LiteralPath (Join-Path $autoRoot 'autopilot-run-coordinator.ps1') -Raw -Encoding UTF8
if ($coordinatorText -notmatch '(?s)Move-AutopilotRunPhase.+?-Status CHECKPOINT.+?REFILL_CONTINUE_SAME_RUN.+?continue') { throw 'coordinator does not continue after a read-back Run transition' }
if ($coordinatorText -match 'REFILL_ROUND_COMPLETE') { throw 'legacy refill exit marker is still present' }

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-refill-continuation-' + [guid]::NewGuid().ToString('N'))
$autoDir = Join-Path $root '.codex-autopilot'
$statePath = Join-Path $autoDir 'state.json'
New-Item -ItemType Directory -Path $autoDir -Force | Out-Null
try {
  [ordered]@{
    schemaVersion=2;runId='run-refill-test';status='REFILLING';phase='refill';currentIssue='';attempt=0
    startedAt='2026-07-14T12:00:00+08:00';phaseStartedAt='2026-07-14T12:00:00+08:00';lastHeartbeatAt='2026-07-14T12:00:00+08:00'
    iterationLimit=2;completedImplementationIssues=0;completedIssueIds=@();worktree='';branch='develop/1.5';executorPid=$null;lastCommit=$null;failureFingerprint=$null
  } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
  $migrated = Read-AutopilotState -Path $statePath
  Write-AutopilotStateAtomic -Path $statePath -State $migrated | Out-Null
  $continued = Move-AutopilotRunPhase -Path $statePath -Status CHECKPOINT -Phase 'refill-complete' -Reason 'Ready created'
  if ($continued.status -ne 'CHECKPOINT' -or $continued.phase -ne 'refill-complete' -or [int]$continued.generation -le [int]$migrated.generation) { throw 'same-run refill transition is not durable' }

  New-Item -ItemType File -Path (Join-Path $autoDir 'enabled.flag') -Force | Out-Null
  if ((Test-Checkpoint -AutoDir $autoDir) -ne 'CONTINUE') { throw 'fresh refill transition did not permit same-run selection' }
  New-Item -ItemType File -Path (Join-Path $autoDir 'pause.flag') -Force | Out-Null
  if ((Test-Checkpoint -AutoDir $autoDir) -ne 'STOP_PAUSE_FLAG') { throw 'pause flag did not stop post-refill selection' }
  Remove-Item -LiteralPath (Join-Path $autoDir 'pause.flag') -Force
  New-Item -ItemType File -Path (Join-Path $autoDir 'stop.flag') -Force | Out-Null
  if ((Test-Checkpoint -AutoDir $autoDir) -ne 'STOP_STOP_FLAG') { throw 'stop flag did not stop post-refill selection' }

  $runResult = New-AutopilotStageResult -Scope RUN -SubjectId 'run-refill-test' -Stage REFILL -Outcome SUCCEEDED -NextStage SELECT -SemanticProgress $true -TransitionIntent SELECT
  if ($runResult.scope -ne 'RUN' -or $runResult.nextStage -ne 'SELECT') { throw 'post-refill RUN StageResult is invalid' }
  Write-Host 'refill continuation self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
