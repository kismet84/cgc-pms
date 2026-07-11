param(
  [string]$Repo = "D:\projects-test\cgc-pms"
)

$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"
$LockPath = Join-Path $AutoDir "run.lock"
$RunsDir = Join-Path $AutoDir "runs"
$ConfigPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "codex-autopilot.config.json"
$MaxRunMinutes = 120
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-recover.ps1')
. (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-metrics.ps1')
if (Test-Path $ConfigPath) {
  $Config = Get-Content -Raw $ConfigPath | ConvertFrom-Json
  if ($Config.maxRunMinutes) {
    $MaxRunMinutes = [int]$Config.maxRunMinutes
  }
}

function Read-RunLock {
  param([string]$Path)

  if (!(Test-Path $Path)) {
    return $null
  }
  try {
    return Get-Content -Raw $Path | ConvertFrom-Json
  } catch {
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
  param([object]$Lock, [int]$MaxRunMinutes = 120)

  if (!$Lock) {
    return $false
  }
  if ($Lock.unreadable) {
    return $true
  }
  [datetime]$heartbeat = [datetime]::MinValue
  if ($Lock.heartbeatAt -and [datetime]::TryParse([string]$Lock.heartbeatAt, [ref]$heartbeat)) {
    if (((Get-Date) - $heartbeat).TotalMinutes -gt $MaxRunMinutes) {
      return $true
    }
  }
  if ($Lock.pid) {
    return !(Get-Process -Id ([int]$Lock.pid) -ErrorAction SilentlyContinue)
  }
  return $false
}

$Summary = [ordered]@{
  repo = $Repo
  enabledFlag = Test-Path (Join-Path $AutoDir "enabled.flag")
  stopFlag = Test-Path (Join-Path $AutoDir "stop.flag")
  pauseFlag = Test-Path (Join-Path $AutoDir "pause.flag")
  startFlag = Test-Path (Join-Path $AutoDir "start.flag")
}

$Lock = Read-RunLock $LockPath
if ($Lock) {
  $Summary.lockExists = $true
  $Summary.lockOwner = $Lock.owner
  $Summary.lockPid = $Lock.pid
  $Summary.lockStartedAt = $Lock.startedAt
  $Summary.lockHeartbeatAt = $Lock.heartbeatAt
  $Summary.lockMode = $Lock.mode
  $Summary.lockIssueId = $Lock.issueId
  $Summary.lockStale = Test-RunLockStale $Lock $MaxRunMinutes
  $Summary.lockMaxRunMinutes = $MaxRunMinutes
} else {
  $Summary.lockExists = $false
}

if (Test-Path $StatePath) {
  $State = Get-Content -Raw $StatePath | ConvertFrom-Json
  $Summary.stateExists = $true
  $Summary.status = $State.status
  $Summary.schemaVersion = $State.schemaVersion
  $Summary.runId = $State.runId
  $Summary.phase = $State.phase
  $Summary.currentIssue = $State.currentIssue
  $Summary.attempt = $State.attempt
  $Summary.phaseStartedAt = $State.phaseStartedAt
  $Summary.completedImplementationIssues = $State.completedImplementationIssues
  $Summary.worktree = $State.worktree
  $Summary.branch = $State.branch
  $Summary.executorPid = $State.executorPid
  $Summary.lastCommit = $State.lastCommit
  $Summary.failureFingerprint = $State.failureFingerprint
  $Summary.mode = $State.mode
  $Summary.lastAction = $State.lastAction
  $Summary.lastIssue = $State.lastIssue
  $Summary.lastReason = $State.lastReason
  $Summary.stopReason = $State.stopReason
  $Summary.iterationLimit = $State.iterationLimit
  $Summary.iterationCompleted = $State.iterationCompleted
  $Summary.remainingIterations = $State.remainingIterations
  $Summary.enabled = $State.enabled
  $Summary.stopRequested = $State.stopRequested
  $Summary.autoMerge = $State.autoMerge
  $Summary.autoPush = $State.autoPush
  $Summary.allowTestDataReset = $State.allowTestDataReset
  $Summary.startedAt = $State.startedAt
  $Summary.lastHeartbeatAt = $State.lastHeartbeatAt
} else {
  $Summary.stateExists = $false
  $Summary.status = "STOPPED"
  $Summary.message = "No AutoPilot state found."
}

if (Test-Path $RunsDir) {
  $LatestRun = Get-ChildItem -Path $RunsDir -Directory -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if ($LatestRun) {
    $Summary.latestRunId = $LatestRun.Name
    $LatestEventPath = Join-Path $LatestRun.FullName "events.jsonl"
    if (Test-Path $LatestEventPath) {
      $Summary.latestEventPath = $LatestEventPath
      $LatestEventLine = Get-Content -LiteralPath $LatestEventPath -Tail 1
      if ($LatestEventLine) {
        try {
          $LatestEvent = $LatestEventLine | ConvertFrom-Json
          $Summary.latestEvent = $LatestEvent.event
          $Summary.latestEventStatus = $LatestEvent.status
          $Summary.latestEventDecision = $LatestEvent.decision
          $Summary.latestStopReason = $LatestEvent.stopReason
          $Summary.latestEventAt = $LatestEvent.timestamp
          $Summary.latestEventIssueId = $LatestEvent.issueId
          $Summary.latestEventTitle = $LatestEvent.title
        } catch {
          $Summary.latestEventUnreadable = $true
        }
      }
      try { $Summary.latestRunMetrics = Get-AutopilotRunMetrics -EventPath $LatestEventPath -RunId $LatestRun.Name } catch { $Summary.latestRunMetricsError = $_.Exception.Message }
    }
  }
  $LatestResult = Get-ChildItem -Path $RunsDir -Filter result.json -Recurse -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if ($LatestResult) {
    try {
      $Result = Get-Content -Raw $LatestResult.FullName | ConvertFrom-Json
      $Summary.latestResultPath = $LatestResult.FullName
      $Summary.latestResultIssueId = $Result.issueId
      $Summary.latestResultTitle = $Result.title
      $Summary.latestResultStatus = $Result.status
      $Summary.latestResultFailureCategory = $Result.failureCategory
      $Summary.latestResultNextAction = $Result.nextAction
      $Summary.latestResultStopReason = $Result.stopReason
      $Summary.latestResultCreatedAt = $Result.createdAt
    } catch {
      $Summary.latestResultPath = $LatestResult.FullName
      $Summary.latestResultUnreadable = $true
    }
  }
}

$Recovery = Get-AutopilotRecoveryDecision -AutoDir $AutoDir
$Summary.recoveryAction = $Recovery.action
$Summary.recoveryReason = $Recovery.reason

$Summary | ConvertTo-Json
