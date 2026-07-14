function Read-RunLock {
  param([string]$LockPath)
  try { return Read-AutopilotRunLockFile -LockPath $LockPath -AllowMissing }
  catch {
    return [pscustomobject]@{
      owner = "unknown"
      pid = $null
      startedAt = $null
      heartbeatAt = $null
      mode = "unknown"
      issueId = ""
      unreadable = $true
    }
  }
}

function Test-RunLockStale {
  param(
    [object]$Lock,
    [int]$MaxRunMinutes
  )

  if (!$Lock) { return $false }
  if ($Lock.unreadable) { return $true }
  return Test-AutopilotRunLockStale -Lock $Lock -MaxRunMinutes $MaxRunMinutes
}

function Write-RunLock {
  param(
    [string]$LockPath,
    [object]$Lock
  )

  Update-AutopilotRunLock -LockPath $LockPath -Lock $Lock | Out-Null
}

function New-RunLock {
  param(
    [string]$AutoDir,
    [int]$MaxRunMinutes,
    [string]$Mode
  )

  try {
    $lock = New-AutopilotRunLock -AutoDir $AutoDir -RepoRoot $RepoRoot -RunId $(if ($script:RunContext) { $script:RunContext.id } else { 'run-' + [guid]::NewGuid().ToString('N') }) -Mode $Mode -ControlPlaneFingerprint ([string]$script:ControlPlaneFingerprint) -ExecutionHost $(if ($script:ExecutionHost) { [string]$script:ExecutionHost } else { 'cli-legacy' }) -MaxRunMinutes $MaxRunMinutes
    if ($lock.tookOverStale) { Write-Host 'STALE_RUN_LOCK_REMOVED'; Write-Host 'STALE_RUN_LOCK_TAKEN_OVER' }
    Write-Host "RUN_LOCK_ACQUIRED"
    return $lock
  } catch {
    if ($_.Exception.Message -match 'Another AutoPilot run is active') { Write-Host 'RUN_LOCK_ACTIVE' }
    throw "Failed to acquire run.lock: $($_.Exception.Message)"
  }
}

function Remove-RunLock {
  param(
    [string]$AutoDir,
    [object]$Lock
  )

  if (!$Lock) {
    return
  }

  $lockPath = Join-Path $AutoDir "run.lock"
  if (Remove-AutopilotRunLock -LockPath $lockPath -Lock $Lock) {
    Write-Host "RUN_LOCK_RELEASED"
  }
}

function Assert-CurrentControlPlaneFence {
  if (!$script:RunLock) { return $true }
  $lockPath = Join-Path $autoDir 'run.lock'
  if (!(Test-AutopilotRunFence -LockPath $lockPath -RunInstanceId ([string]$script:RunLock.runInstanceId) -LeaseEpoch ([string]$script:RunLock.leaseEpoch))) {
    $script:FenceValid = $false
    throw 'AUTOPILOT_FENCE_REJECTED: current process no longer owns run.lock.'
  }
  if ($controlPlaneCanaryEnabled) {
    $currentFingerprint = Get-AutopilotControlPlaneFingerprint -RepoRoot $RepoRoot -Paths @($config.controlPlaneCanary.fingerprintPaths)
    if ($currentFingerprint -ne $script:ControlPlaneFingerprint) {
      $script:FenceValid = $false
      throw 'AUTOPILOT_CONTROL_PLANE_GENERATION_CHANGED: stale process cannot write, dispatch, merge, or close out.'
    }
  }
  $script:FenceValid = $true
  return $true
}

$script:AutopilotIssuePhaseTransitions = @{
  IMPLEMENTING = @('IMPLEMENTED','REPAIRING','QUARANTINED')
  IMPLEMENTED = @('VALIDATING','REPAIRING','QUARANTINED')
  VALIDATING = @('VALIDATED','IMPLEMENTED','REPAIRING','QUARANTINED')
  VALIDATED = @('REVIEWING','CLOSING','REPAIRING','QUARANTINED')
  REVIEWING = @('REVIEWED','REVIEW_TOOL_BLOCKED','REPAIRING','QUARANTINED')
  REVIEW_TOOL_BLOCKED = @('REVIEWING','REVIEWED','QUARANTINED')
  REVIEWED = @('CLOSING','REPAIRING','QUARANTINED')
  REPAIRING = @('IMPLEMENTED','VALIDATING','QUARANTINED')
  CLOSING = @('IMPLEMENTATION_COMMITTED','CLOSEOUT_COMMITTED','REPAIRING','QUARANTINED')
  IMPLEMENTATION_COMMITTED = @('CLOSEOUT_COMMITTED','QUARANTINED')
  CLOSEOUT_COMMITTED = @('REGISTERED','QUARANTINED')
  REGISTERED = @('CLOSED','QUARANTINED')
  CLOSED = @()
  QUARANTINED = @()
}

function Move-AutopilotRunPhase {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][ValidateSet('DISABLED','IDLE','CHECKPOINT','REFILLING','SELECTING','PLANNING','EXECUTING','VERIFYING','REVIEWING','REPAIRING','CLOSING','COMMITTING','MERGING','PAUSED','BLOCKED','RETROSPECTIVE_REQUIRED','LIMIT_REACHED','STOPPED','FAILED')][string]$Status,
    [Parameter(Mandatory)][string]$Phase,
    [string]$Reason = ''
  )
  $before = Read-AutopilotState -Path $Path
  $from = [string]$before.status
  $written = Move-AutopilotState -Path $Path -ToStatus $Status -Phase $Phase -Reason $Reason
  $readBack = Read-AutopilotState -Path $Path
  if ([string]$readBack.status -ne $Status -or [string]$readBack.phase -ne $Phase -or [string]::IsNullOrWhiteSpace([string]$readBack.transitionId) -or [int]$readBack.generation -le [int]$before.generation) {
    throw "AutoPilot Run transition read-back failed: $from -> $Status"
  }
  if ([string]$written.transitionId -ne [string]$readBack.transitionId -or [int]$written.generation -ne [int]$readBack.generation) {
    throw "AutoPilot Run transition identity mismatch: $from -> $Status"
  }
  return $readBack
}

function Move-AutopilotIssuePhase {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][ValidateSet('IMPLEMENTING','IMPLEMENTED','VALIDATING','VALIDATED','REVIEWING','REVIEW_TOOL_BLOCKED','REVIEWED','REPAIRING','CLOSING','IMPLEMENTATION_COMMITTED','CLOSEOUT_COMMITTED','REGISTERED','CLOSED','QUARANTINED')][string]$Phase,
    [hashtable]$Artifacts = @{},
    [hashtable]$Evidence = @{},
    [ValidateSet('','implementation','validation','review','repair','closeout')][string]$IncrementDispatch = '',
    [switch]$IncrementPhaseRestart,
    [switch]$IncrementRunResume,
    [switch]$IncrementManualRecovery,
    [switch]$IncrementToolConfigBlock,
    [switch]$IncrementEnvironmentRetry,
    [string]$QuarantineReason = ''
  )

  $before = Read-AutopilotIssueCheckpoint -Path $Path
  $from = [string]$before.phase
  if ($from -ne $Phase -and $script:AutopilotIssuePhaseTransitions[$from] -notcontains $Phase) {
    throw "Illegal AutoPilot Issue phase transition: $from -> $Phase"
  }
  $arguments = @{
    Path = $Path
    Phase = $Phase
    Artifacts = $Artifacts
    Evidence = $Evidence
    IncrementDispatch = $IncrementDispatch
    QuarantineReason = $QuarantineReason
  }
  foreach ($switchName in @('IncrementPhaseRestart','IncrementRunResume','IncrementManualRecovery','IncrementToolConfigBlock','IncrementEnvironmentRetry')) {
    if ($PSBoundParameters[$switchName]) { $arguments[$switchName] = $true }
  }
  $written = Set-AutopilotIssueCheckpointPhase @arguments
  $readBack = Read-AutopilotIssueCheckpoint -Path $Path
  if ([string]$readBack.phase -ne $Phase -or [string]::IsNullOrWhiteSpace([string]$readBack.transitionId) -or [int]$readBack.generation -le [int]$before.generation) {
    throw "AutoPilot Issue transition read-back failed: $from -> $Phase"
  }
  if ([string]$written.transitionId -ne [string]$readBack.transitionId -or [int]$written.generation -ne [int]$readBack.generation) {
    throw "AutoPilot Issue transition identity mismatch: $from -> $Phase"
  }
  return $readBack
}

function Write-State {
  param(
    [string]$AutoDir,
    [string]$Status,
    [bool]$DryRunMode,
    [string]$LastAction = $Status,
    [string]$LastIssue = "",
    [string]$LastReason = $Status,
    [string]$StopReason = ""
  )

  if ($DryRunMode) {
    return
  }

  if ($script:RunLock) {
    Assert-CurrentControlPlaneFence | Out-Null
    Write-RunLock (Join-Path $AutoDir "run.lock") $script:RunLock
  }

  $statePath = Join-Path $AutoDir 'state.json'
  $now = [datetimeoffset]::Now.ToString('o')
  $existing = $null
  if (Test-Path -LiteralPath $statePath) {
    try { $existing = Read-AutopilotState -Path $statePath } catch { throw "AutoPilot state cannot be updated safely: $($_.Exception.Message)" }
  }
  $completedIds = @()
  if ($null -ne $existing -and $existing.PSObject.Properties.Name -contains 'completedIssueIds') { $completedIds = @($existing.completedIssueIds) }
  while ($completedIds.Count -lt $script:IterationCompleted) { $completedIds += "legacy-completed-$($completedIds.Count + 1)" }
  $stateStatus = if ($script:AutopilotStatuses -contains $Status) { $Status } elseif ($Status -eq 'STOP_ITERATION_LIMIT_REACHED') { 'LIMIT_REACHED' } elseif ($Status -eq 'STOP_RETROSPECTIVE_REQUIRED') { 'RETROSPECTIVE_REQUIRED' } elseif ($Status -eq 'STOP_PAUSE_FLAG') { 'PAUSED' } elseif ($Status -eq 'STOP_DISABLED') { 'DISABLED' } elseif ($Status -like 'STOP*') { 'STOPPED' } elseif ($Status -like 'READY_ISSUE*') { 'PLANNING' } elseif ($Status -like '*SPLIT*') { 'REFILLING' } else { 'CHECKPOINT' }
  $phase = $stateStatus.ToLowerInvariant()
  $issueStateBinding = Resolve-AutopilotIssueStateBinding -Existing $existing -CheckpointPath ([string]$script:IssueCheckpointPath) -Phase ([string]$script:IssuePhase) -Clear:($LastAction -eq 'ISSUE_CLOSED')
  $state = [ordered]@{
    schemaVersion = 3
    runId = if ($script:RunContext) { $script:RunContext.id } else { 'run-' + [datetimeoffset]::Now.ToString('yyyyMMdd-HHmmss-fff') }
    status = $stateStatus
    phase = $phase
    currentIssue = $LastIssue
    attempt = $script:Attempt
    startedAt = if ($null -ne $existing -and $existing.PSObject.Properties.Name -contains 'startedAt' -and $existing.startedAt) { [string]$existing.startedAt } else { $now }
    phaseStartedAt = $now
    lastHeartbeatAt = $now
    iterationLimit = $script:IterationLimit
    completedImplementationIssues = $script:IterationCompleted
    completedIssueIds = @($completedIds)
    worktree = if ($script:CurrentWorktree) { $script:CurrentWorktree } else { '' }
    branch = if ($script:CurrentBranch) { $script:CurrentBranch } else { (Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('branch','--show-current') -ThrowOnFailure).stdout.Trim() }
    executorPid = $script:ExecutorPid
    executorStartedAt = $script:ExecutorStartedAt
    lastProgressAt = $script:LastProgressAt
    retryCount = $script:Attempt
    timeoutReason = $script:TimeoutReason
    retiredAt = $script:RetiredAt
    retiredStatus = $script:RetiredStatus
    retiredExecutors = @($script:RetiredExecutors)
    lastCommit = $script:LastCommit
    failureFingerprint = $script:FailureFingerprint
    reviewCycleId = [string](Get-AutopilotStateProperty $existing 'reviewCycleId' '')
    reviewCycleStartedAt = Get-AutopilotStateProperty $existing 'reviewCycleStartedAt'
    reviewCycleCompletedIssueIds = @(Get-AutopilotStateProperty $existing 'reviewCycleCompletedIssueIds' @())
    reviewCycleScoreKeys = @(Get-AutopilotStateProperty $existing 'reviewCycleScoreKeys' @())
    reviewCycleCompletedCount = [int](Get-AutopilotStateProperty $existing 'reviewCycleCompletedCount' 0)
    retrospectiveDue = [bool](Get-AutopilotStateProperty $existing 'retrospectiveDue' $false)
    retrospectiveStatus = [string](Get-AutopilotStateProperty $existing 'retrospectiveStatus' 'IDLE')
    retrospectivePhase = [string](Get-AutopilotStateProperty $existing 'retrospectivePhase' 'NONE')
    retrospectiveRequiredAt = Get-AutopilotStateProperty $existing 'retrospectiveRequiredAt'
    retrospectiveReportCommit = Get-AutopilotStateProperty $existing 'retrospectiveReportCommit'
    retrospectiveFactsCommit = Get-AutopilotStateProperty $existing 'retrospectiveFactsCommit'
    retrospectiveGraphGitCursor = Get-AutopilotStateProperty $existing 'retrospectiveGraphGitCursor'
    retrospectiveEpisodeId = Get-AutopilotStateProperty $existing 'retrospectiveEpisodeId'
    retrospectiveFailureCategory = Get-AutopilotStateProperty $existing 'retrospectiveFailureCategory'
    lastRetrospectiveAt = Get-AutopilotStateProperty $existing 'lastRetrospectiveAt'
    lastRetrospectiveReport = Get-AutopilotStateProperty $existing 'lastRetrospectiveReport'
    activeScoringVersion = if ($script:TaskScoringActive) { [string]$config.taskScoring.activeVersion } else { $null }
    issueCheckpointPath = $issueStateBinding.checkpointPath
    currentIssuePhase = $issueStateBinding.phase
    lastCanaryFingerprint = if ($script:LastCanaryFingerprint) { [string]$script:LastCanaryFingerprint } else { [string](Get-AutopilotStateProperty $existing 'lastCanaryFingerprint' '') }
    lastCanaryReport = if ($script:LastCanaryReport) { [string]$script:LastCanaryReport } else { [string](Get-AutopilotStateProperty $existing 'lastCanaryReport' '') }
    enabled = Test-Path (Join-Path $AutoDir 'enabled.flag')
    mode = 'continuous-runner'
    iterationCompleted = $script:IterationCompleted
    remainingIterations = $script:RemainingIterations
    iterationLastCountedIssue = $script:IterationLastCountedIssue
    lastAction = $LastAction
    lastIssue = $LastIssue
    lastReason = $LastReason
    stopReason = $StopReason
    stopRequested = Test-Path (Join-Path $AutoDir 'stop.flag')
    autoMerge = if ($config.PSObject.Properties.Name -contains 'autoMerge') { [bool]$config.autoMerge } else { $true }
    autoPush = $false
    allowTestDataReset = Test-Path (Join-Path $AutoDir 'ALLOW_TEST_DATA_RESET')
    executionHost = if ($script:ExecutionHost) { [string]$script:ExecutionHost } else { [string](Get-AutopilotStateProperty $existing 'executionHost' 'cli-legacy') }
  }
  $transitionId = ''
  if ($issueStateBinding.checkpointPath -and (Test-Path -LiteralPath $issueStateBinding.checkpointPath -PathType Leaf)) {
    $phaseCheckpoint = Read-AutopilotIssueCheckpoint -Path $issueStateBinding.checkpointPath
    if (!$script:RunLock -or ([string]$phaseCheckpoint.runInstanceId -eq [string]$script:RunLock.runInstanceId -and [string]$phaseCheckpoint.leaseEpoch -eq [string]$script:RunLock.leaseEpoch)) {
      $transitionId = [string]$phaseCheckpoint.transitionId
    }
  }
  Write-AutopilotStateAtomic -Path $statePath -State $state -TransitionId $transitionId -ExecutionHost $state.executionHost | Out-Null
}
