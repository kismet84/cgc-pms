$ErrorActionPreference = 'Stop'

$script:AutopilotStatuses = @(
  'DISABLED','IDLE','CHECKPOINT','REFILLING','SELECTING','PLANNING','EXECUTING','VERIFYING',
  'REVIEWING','REPAIRING','CLOSING','COMMITTING','MERGING','PAUSED','BLOCKED',
  'LIMIT_REACHED','STOPPED','FAILED'
)

$script:AutopilotTransitions = @{
  DISABLED = @('IDLE')
  IDLE = @('CHECKPOINT','DISABLED','STOPPED')
  CHECKPOINT = @('REFILLING','SELECTING','PAUSED','STOPPED','LIMIT_REACHED','FAILED')
  REFILLING = @('CHECKPOINT','SELECTING','BLOCKED','PAUSED','STOPPED','FAILED')
  SELECTING = @('PLANNING','REFILLING','BLOCKED','PAUSED','STOPPED','LIMIT_REACHED','FAILED')
  PLANNING = @('EXECUTING','BLOCKED','PAUSED','STOPPED','FAILED')
  EXECUTING = @('VERIFYING','REPAIRING','BLOCKED','PAUSED','STOPPED','FAILED')
  VERIFYING = @('REVIEWING','REPAIRING','CLOSING','BLOCKED','PAUSED','STOPPED','FAILED')
  REVIEWING = @('REPAIRING','CLOSING','BLOCKED','PAUSED','STOPPED','FAILED')
  REPAIRING = @('VERIFYING','BLOCKED','PAUSED','STOPPED','FAILED')
  CLOSING = @('COMMITTING','BLOCKED','PAUSED','STOPPED','FAILED')
  COMMITTING = @('MERGING','CHECKPOINT','BLOCKED','FAILED')
  MERGING = @('CHECKPOINT','BLOCKED','FAILED')
  PAUSED = @('CHECKPOINT','STOPPED')
  BLOCKED = @('CHECKPOINT','STOPPED')
  LIMIT_REACHED = @('IDLE','STOPPED')
  STOPPED = @('IDLE','DISABLED')
  FAILED = @('CHECKPOINT','STOPPED')
}

function Set-AutopilotProperty {
  param([object]$Object, [string]$Name, [object]$Value)
  if ($Object -is [System.Collections.IDictionary]) {
    $Object[$Name] = $Value
  } else {
    $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
  }
}

function Assert-AutopilotState {
  param([object]$State)

  $required = @(
    'schemaVersion','runId','status','phase','currentIssue','attempt','startedAt','phaseStartedAt',
    'lastHeartbeatAt','iterationLimit','completedImplementationIssues','completedIssueIds','worktree',
    'branch','executorPid','lastCommit','failureFingerprint'
  )
  foreach ($name in $required) {
    if ($State.PSObject.Properties.Name -notcontains $name -and !($State -is [System.Collections.IDictionary] -and $State.Contains($name))) {
      throw "AutoPilot state missing required property: $name"
    }
  }
  if ([int]$State.schemaVersion -ne 2) { throw "Unsupported AutoPilot state schemaVersion: $($State.schemaVersion)" }
  if ($script:AutopilotStatuses -notcontains [string]$State.status) { throw "Invalid AutoPilot state status: $($State.status)" }
  if ([int]$State.attempt -lt 0 -or [int]$State.completedImplementationIssues -lt 0) { throw 'AutoPilot counters cannot be negative' }
  foreach ($name in 'startedAt','phaseStartedAt','lastHeartbeatAt') {
    [datetimeoffset]$parsed = [datetimeoffset]::MinValue
    if (![datetimeoffset]::TryParse([string]$State.$name, [ref]$parsed)) { throw "Invalid AutoPilot timestamp: $name" }
  }
  $ids = @($State.completedIssueIds)
  if (@($ids | Select-Object -Unique).Count -ne $ids.Count) { throw 'completedIssueIds must be unique' }
  if ([int]$State.completedImplementationIssues -ne $ids.Count) { throw 'completedImplementationIssues must equal completedIssueIds count' }
}

function Read-AutopilotState {
  param([Parameter(Mandatory)][string]$Path)
  if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "AutoPilot state not found: $Path" }
  try { $state = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json } catch { throw "AutoPilot state is invalid JSON: $Path" }
  Assert-AutopilotState $state
  return $state
}

function Write-AutopilotStateAtomic {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][object]$State)

  $json = $State | ConvertTo-Json -Depth 12
  try { $parsed = $json | ConvertFrom-Json } catch { throw 'AutoPilot state cannot be serialized as JSON' }
  Assert-AutopilotState $parsed

  $parent = Split-Path -Parent $Path
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  $tempPath = "$Path.$([guid]::NewGuid().ToString('N')).tmp"
  $backupPath = "$Path.$([guid]::NewGuid().ToString('N')).bak"
  try {
    [System.IO.File]::WriteAllText($tempPath, $json, [System.Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $Path) {
      [System.IO.File]::Replace($tempPath, $Path, $backupPath, $true)
    } else {
      [System.IO.File]::Move($tempPath, $Path)
    }
  } finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
  }
  return $parsed
}

function Move-AutopilotState {
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][string]$ToStatus,
    [Parameter(Mandatory)][string]$Phase,
    [string]$Reason = ''
  )

  $state = Read-AutopilotState $Path
  $from = [string]$state.status
  if ($script:AutopilotStatuses -notcontains $ToStatus) { throw "Invalid AutoPilot target status: $ToStatus" }
  if ($from -ne $ToStatus -and $script:AutopilotTransitions[$from] -notcontains $ToStatus) {
    throw "Illegal AutoPilot state transition: $from -> $ToStatus"
  }
  $now = [datetimeoffset]::Now.ToString('o')
  Set-AutopilotProperty $state 'status' $ToStatus
  Set-AutopilotProperty $state 'phase' $Phase
  Set-AutopilotProperty $state 'phaseStartedAt' $now
  Set-AutopilotProperty $state 'lastHeartbeatAt' $now
  return Write-AutopilotStateAtomic -Path $Path -State $state
}

function Add-AutopilotCompletedIssue {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$IssueId)

  $state = Read-AutopilotState $Path
  $ids = @($state.completedIssueIds)
  if ($ids -notcontains $IssueId) { $ids += $IssueId }
  Set-AutopilotProperty $state 'completedIssueIds' @($ids)
  Set-AutopilotProperty $state 'completedImplementationIssues' $ids.Count
  Set-AutopilotProperty $state 'lastHeartbeatAt' ([datetimeoffset]::Now.ToString('o'))
  return Write-AutopilotStateAtomic -Path $Path -State $state
}

function Write-AutopilotEvent {
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][string]$RunId,
    [string]$IssueId = '',
    [Parameter(Mandatory)][string]$From,
    [Parameter(Mandatory)][string]$To,
    [Parameter(Mandatory)][string]$Reason,
    [string]$EvidencePath = ''
  )

  $parent = Split-Path -Parent $Path
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  $event = [ordered]@{
    runId = $RunId
    issueId = $IssueId
    from = $From
    to = $To
    timestamp = [datetimeoffset]::Now.ToString('o')
    reason = $Reason
    evidencePath = $EvidencePath
  }
  [System.IO.File]::AppendAllText($Path, (($event | ConvertTo-Json -Compress) + [Environment]::NewLine), [System.Text.UTF8Encoding]::new($false))
  return [pscustomobject]$event
}
