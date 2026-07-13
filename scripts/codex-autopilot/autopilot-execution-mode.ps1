$ErrorActionPreference = 'Stop'

function Resolve-AutopilotExecutionMode {
  param(
    [bool]$DryRun = $false,
    [bool]$ExplainNextAction = $false,
    [bool]$ApplyBacklogSplit = $false
  )

  $selected = @($DryRun, $ExplainNextAction, $ApplyBacklogSplit | Where-Object { $_ }).Count
  if ($selected -gt 1) {
    throw 'AutoPilot execution mode switches conflict; choose exactly one of DryRun, ExplainNextAction, or ApplyBacklogSplit.'
  }

  $mode = if ($ApplyBacklogSplit) { 'APPLY' } elseif ($ExplainNextAction) { 'EXPLAIN' } else { 'DRY_RUN' }
  return [pscustomobject]@{
    mode = $mode
    compatibilityEntryPoint = if ($ApplyBacklogSplit) { 'ApplyBacklogSplit' } elseif ($ExplainNextAction) { 'ExplainNextAction' } elseif ($DryRun) { 'DryRun' } else { 'DefaultDryRun' }
    canWriteState = $mode -eq 'APPLY'
    canAcquireLock = $mode -eq 'APPLY'
    mustRecover = $mode -eq 'APPLY'
    canDispatch = $mode -eq 'APPLY'
    isDryRun = $mode -eq 'DRY_RUN'
    isExplain = $mode -eq 'EXPLAIN'
  }
}

function Assert-AutopilotDispatchAuthority {
  param(
    [Parameter(Mandatory)][object]$ExecutionMode,
    [bool]$LockOwned,
    [bool]$RecoveryCheckedAfterLock,
    [bool]$FenceValid
  )
  if ([string]$ExecutionMode.mode -ne 'APPLY' -or !$ExecutionMode.canDispatch) { throw 'AutoPilot dispatch requires APPLY mode.' }
  if (!$LockOwned) { throw 'AutoPilot dispatch requires an owned run.lock.' }
  if (!$RecoveryCheckedAfterLock) { throw 'AutoPilot dispatch requires recovery reconciliation after lock acquisition.' }
  if (!$FenceValid) { throw 'AutoPilot dispatch requires a valid fencing token.' }
  return $true
}
