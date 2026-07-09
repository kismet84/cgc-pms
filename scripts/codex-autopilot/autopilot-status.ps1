param(
  [string]$Repo = "D:\projects-test\cgc-pms"
)

$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"
$LockPath = Join-Path $AutoDir "run.lock"
$ConfigPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "codex-autopilot.config.json"
$MaxRunMinutes = 120
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

$Summary | ConvertTo-Json
