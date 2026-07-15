param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-execution-mode.ps1')

$default = Resolve-AutopilotExecutionMode
if ($default.mode -ne 'DRY_RUN' -or $default.canDispatch -or $default.canWriteState) { throw 'default invocation must be read-only DRY_RUN' }
$dry = Resolve-AutopilotExecutionMode -DryRun $true
if ($dry.mode -ne 'DRY_RUN' -or !$dry.isDryRun) { throw 'explicit DryRun did not resolve to DRY_RUN' }
$explain = Resolve-AutopilotExecutionMode -ExplainNextAction $true
if ($explain.mode -ne 'EXPLAIN' -or !$explain.isExplain -or $explain.canAcquireLock) { throw 'ExplainNextAction gained mutation authority' }
$apply = Resolve-AutopilotExecutionMode -ApplyBacklogSplit $true
if ($apply.mode -ne 'APPLY' -or !$apply.canDispatch -or !$apply.mustRecover -or !$apply.canAcquireLock) { throw 'ApplyBacklogSplit did not map to APPLY authority' }

foreach ($combination in @(
  @{DryRun=$true;ExplainNextAction=$true},
  @{DryRun=$true;ApplyBacklogSplit=$true},
  @{ExplainNextAction=$true;ApplyBacklogSplit=$true},
  @{DryRun=$true;ExplainNextAction=$true;ApplyBacklogSplit=$true}
)) {
  $rejected = $false
  try { Resolve-AutopilotExecutionMode @combination | Out-Null } catch { $rejected = $true }
  if (!$rejected) { throw 'conflicting execution mode switches were accepted' }
}

if (!(Assert-AutopilotDispatchAuthority -ExecutionMode $apply -LockOwned $true -RecoveryCheckedAfterLock $true -FenceValid $true)) { throw 'valid APPLY authority was rejected' }
foreach ($invalid in @(
  @{ExecutionMode=$default;LockOwned=$true;RecoveryCheckedAfterLock=$true;FenceValid=$true},
  @{ExecutionMode=$apply;LockOwned=$false;RecoveryCheckedAfterLock=$true;FenceValid=$true},
  @{ExecutionMode=$apply;LockOwned=$true;RecoveryCheckedAfterLock=$false;FenceValid=$true},
  @{ExecutionMode=$apply;LockOwned=$true;RecoveryCheckedAfterLock=$true;FenceValid=$false}
)) {
  $rejected = $false
  try { Assert-AutopilotDispatchAuthority @invalid | Out-Null } catch { $rejected = $true }
  if (!$rejected) { throw 'invalid dispatch authority was accepted' }
}

Write-Host 'execution mode matrix self-test passed'
