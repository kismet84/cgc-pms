$ErrorActionPreference = 'Stop'

$script:AutopilotStatuses = @(
  'DISABLED','IDLE','CHECKPOINT','REFILLING','SELECTING','PLANNING','EXECUTING','VERIFYING',
  'REVIEWING','REPAIRING','CLOSING','COMMITTING','MERGING','PAUSED','BLOCKED',
  'RETROSPECTIVE_REQUIRED','LIMIT_REACHED','STOPPED','FAILED'
)

$script:AutopilotTransitions = @{
  DISABLED = @('IDLE')
  IDLE = @('CHECKPOINT','DISABLED','STOPPED')
  CHECKPOINT = @('REFILLING','SELECTING','PAUSED','RETROSPECTIVE_REQUIRED','STOPPED','LIMIT_REACHED','FAILED')
  REFILLING = @('CHECKPOINT','SELECTING','BLOCKED','PAUSED','STOPPED','FAILED')
  SELECTING = @('PLANNING','REFILLING','BLOCKED','PAUSED','STOPPED','LIMIT_REACHED','FAILED')
  PLANNING = @('EXECUTING','BLOCKED','PAUSED','STOPPED','FAILED')
  EXECUTING = @('VERIFYING','REPAIRING','BLOCKED','PAUSED','STOPPED','FAILED')
  VERIFYING = @('REVIEWING','REPAIRING','CLOSING','BLOCKED','PAUSED','STOPPED','FAILED')
  REVIEWING = @('REPAIRING','CLOSING','BLOCKED','PAUSED','STOPPED','FAILED')
  REPAIRING = @('VERIFYING','BLOCKED','PAUSED','STOPPED','FAILED')
  CLOSING = @('COMMITTING','BLOCKED','PAUSED','STOPPED','FAILED')
  COMMITTING = @('MERGING','CHECKPOINT','RETROSPECTIVE_REQUIRED','BLOCKED','FAILED')
  MERGING = @('CHECKPOINT','RETROSPECTIVE_REQUIRED','BLOCKED','FAILED')
  PAUSED = @('CHECKPOINT','STOPPED')
  BLOCKED = @('CHECKPOINT','STOPPED')
  LIMIT_REACHED = @('IDLE','STOPPED')
  STOPPED = @('IDLE','DISABLED')
  FAILED = @('CHECKPOINT','STOPPED')
  RETROSPECTIVE_REQUIRED = @('PAUSED','CHECKPOINT','STOPPED','FAILED')
}

function Set-AutopilotProperty {
  param([object]$Object, [string]$Name, [object]$Value)
  if ($Object -is [System.Collections.IDictionary]) {
    $Object[$Name] = $Value
  } else {
    $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
  }
}

function Resolve-AutopilotIssueStateBinding {
  param(
    [object]$Existing,
    [AllowEmptyString()][string]$CheckpointPath = '',
    [AllowEmptyString()][string]$Phase = '',
    [switch]$Clear
  )
  if ($Clear) { return [pscustomobject]@{ checkpointPath=''; phase='' } }
  return [pscustomobject]@{
    checkpointPath = if ($CheckpointPath) { $CheckpointPath } else { [string](Get-AutopilotStateProperty $Existing 'issueCheckpointPath' '') }
    phase = if ($Phase) { $Phase } else { [string](Get-AutopilotStateProperty $Existing 'currentIssuePhase' '') }
  }
}

function Get-AutopilotStateProperty {
  param([object]$Object, [string]$Name, [object]$Default = $null)
  if ($null -eq $Object) { return $Default }
  if ($Object -is [System.Collections.IDictionary] -and $Object.Contains($Name)) { return $Object[$Name] }
  if ($Object.PSObject.Properties.Name -contains $Name) { return $Object.$Name }
  return $Default
}

function ConvertTo-AutopilotStateV3 {
  param([Parameter(Mandatory)][object]$State)
  $version = 0
  if ($State -is [System.Collections.IDictionary] -and $State.Contains('schemaVersion')) { $version = [int]$State['schemaVersion'] }
  elseif ($State.PSObject.Properties.Name -contains 'schemaVersion') { $version = [int]$State.schemaVersion }
  if ($version -eq 3) {
    foreach ($entry in @(
      @('issueCheckpointPath',''), @('currentIssuePhase',''), @('lastCanaryFingerprint',''), @('lastCanaryReport','')
    )) {
      $name = [string]$entry[0]
      $present = ($State -is [System.Collections.IDictionary] -and $State.Contains($name)) -or ($State.PSObject.Properties.Name -contains $name)
      if (!$present) { Set-AutopilotProperty $State $name $entry[1] }
    }
    return $State
  }
  if ($version -ne 2) { throw "Unsupported AutoPilot state schemaVersion: $version" }

  Set-AutopilotProperty $State 'schemaVersion' 3
  Set-AutopilotProperty $State 'reviewCycleId' ''
  Set-AutopilotProperty $State 'reviewCycleStartedAt' $null
  Set-AutopilotProperty $State 'reviewCycleCompletedIssueIds' @()
  Set-AutopilotProperty $State 'reviewCycleScoreKeys' @()
  Set-AutopilotProperty $State 'reviewCycleCompletedCount' 0
  Set-AutopilotProperty $State 'retrospectiveDue' $false
  Set-AutopilotProperty $State 'retrospectiveStatus' 'IDLE'
  Set-AutopilotProperty $State 'retrospectivePhase' 'NONE'
  Set-AutopilotProperty $State 'retrospectiveRequiredAt' $null
  Set-AutopilotProperty $State 'retrospectiveReportCommit' $null
  Set-AutopilotProperty $State 'retrospectiveFactsCommit' $null
  Set-AutopilotProperty $State 'retrospectiveGraphGitCursor' $null
  Set-AutopilotProperty $State 'retrospectiveEpisodeId' $null
  Set-AutopilotProperty $State 'retrospectiveFailureCategory' $null
  Set-AutopilotProperty $State 'lastRetrospectiveAt' $null
  Set-AutopilotProperty $State 'lastRetrospectiveReport' $null
  Set-AutopilotProperty $State 'activeScoringVersion' $null
  Set-AutopilotProperty $State 'issueCheckpointPath' ''
  Set-AutopilotProperty $State 'currentIssuePhase' ''
  Set-AutopilotProperty $State 'lastCanaryFingerprint' ''
  Set-AutopilotProperty $State 'lastCanaryReport' ''
  return $State
}

function Assert-AutopilotState {
  param([object]$State)

  $required = @(
    'schemaVersion','runId','status','phase','currentIssue','attempt','startedAt','phaseStartedAt',
    'lastHeartbeatAt','iterationLimit','completedImplementationIssues','completedIssueIds','worktree',
    'branch','executorPid','lastCommit','failureFingerprint','reviewCycleId','reviewCycleStartedAt',
    'reviewCycleCompletedIssueIds','reviewCycleScoreKeys','reviewCycleCompletedCount','retrospectiveDue',
    'retrospectiveStatus','retrospectivePhase','retrospectiveRequiredAt','retrospectiveReportCommit',
    'retrospectiveFactsCommit','retrospectiveGraphGitCursor','retrospectiveEpisodeId',
    'retrospectiveFailureCategory','lastRetrospectiveAt','lastRetrospectiveReport','activeScoringVersion',
    'issueCheckpointPath','currentIssuePhase','lastCanaryFingerprint','lastCanaryReport'
  )
  foreach ($name in $required) {
    if ($State.PSObject.Properties.Name -notcontains $name -and !($State -is [System.Collections.IDictionary] -and $State.Contains($name))) {
      throw "AutoPilot state missing required property: $name"
    }
  }
  if ([int]$State.schemaVersion -ne 3) { throw "Unsupported AutoPilot state schemaVersion: $($State.schemaVersion)" }
  if ($script:AutopilotStatuses -notcontains [string]$State.status) { throw "Invalid AutoPilot state status: $($State.status)" }
  if ([int]$State.attempt -lt 0 -or [int]$State.completedImplementationIssues -lt 0) { throw 'AutoPilot counters cannot be negative' }
  foreach ($name in 'startedAt','phaseStartedAt','lastHeartbeatAt') {
    [datetimeoffset]$parsed = [datetimeoffset]::MinValue
    if (![datetimeoffset]::TryParse([string]$State.$name, [ref]$parsed)) { throw "Invalid AutoPilot timestamp: $name" }
  }
  foreach ($name in 'executorStartedAt','lastProgressAt','retiredAt') {
    if ($State.PSObject.Properties.Name -contains $name -and $State.$name) {
      [datetimeoffset]$parsed = [datetimeoffset]::MinValue
      if (![datetimeoffset]::TryParse([string]$State.$name, [ref]$parsed)) { throw "Invalid AutoPilot timestamp: $name" }
    }
  }
  if ($State.PSObject.Properties.Name -contains 'retryCount' -and [int]$State.retryCount -lt 0) { throw 'retryCount cannot be negative' }
  $ids = @($State.completedIssueIds)
  if (@($ids | Select-Object -Unique).Count -ne $ids.Count) { throw 'completedIssueIds must be unique' }
  if ([int]$State.completedImplementationIssues -ne $ids.Count) { throw 'completedImplementationIssues must equal completedIssueIds count' }
  $reviewIds = @($State.reviewCycleCompletedIssueIds)
  $scoreKeys = @($State.reviewCycleScoreKeys)
  if (@($reviewIds | Select-Object -Unique).Count -ne $reviewIds.Count) { throw 'reviewCycleCompletedIssueIds must be unique' }
  if (@($scoreKeys | Select-Object -Unique).Count -ne $scoreKeys.Count) { throw 'reviewCycleScoreKeys must be unique' }
  if ([int]$State.reviewCycleCompletedCount -ne $reviewIds.Count -or $reviewIds.Count -ne $scoreKeys.Count) { throw 'review cycle ids, score keys, and count must agree' }
  if ([string]$State.retrospectiveStatus -notin @('IDLE','ACCUMULATING','REQUIRED','RUNNING','COMPLETED','FAILED')) { throw 'invalid retrospectiveStatus' }
  if ([string]$State.retrospectivePhase -notin @('NONE','REPORT_COMMITTED','ISSUES_WRITTEN','GRAPH_REFRESHED','EPISODE_RECORDED','RESET_COMMITTED')) { throw 'invalid retrospectivePhase' }
  foreach ($name in 'reviewCycleStartedAt','retrospectiveRequiredAt','lastRetrospectiveAt') {
    if ($State.$name) {
      [datetimeoffset]$parsed = [datetimeoffset]::MinValue
      if (![datetimeoffset]::TryParse([string]$State.$name, [ref]$parsed)) { throw "Invalid AutoPilot timestamp: $name" }
    }
  }
  if ([bool]$State.retrospectiveDue -and [int]$State.reviewCycleCompletedCount -lt 1) { throw 'retrospectiveDue requires a non-empty review cycle' }
}

function Read-AutopilotState {
  param([Parameter(Mandatory)][string]$Path)
  if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "AutoPilot state not found: $Path" }
  try { $state = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json } catch { throw "AutoPilot state is invalid JSON: $Path" }
  $requiresShapeUpgrade = [int]$state.schemaVersion -eq 2 -or $state.PSObject.Properties.Name -notcontains 'issueCheckpointPath'
  if ($requiresShapeUpgrade) {
    $state = ConvertTo-AutopilotStateV3 $state
    Write-AutopilotStateAtomic -Path $Path -State $state | Out-Null
  }
  Assert-AutopilotState $state
  return $state
}

function Write-AutopilotStateAtomic {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][object]$State)

  $State = ConvertTo-AutopilotStateV3 $State
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

function Add-AutopilotReviewCycleIssue {
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$ScoreKey,
    [Parameter(Mandatory)][string]$ScoringVersion,
    [int]$Threshold = 20,
    [Nullable[int]]$BoundedBatchRemaining = $null
  )
  if ($Threshold -lt 1) { throw 'review cycle threshold must be positive' }
  $state = Read-AutopilotState $Path
  if (!$state.activeScoringVersion -or [string]$state.activeScoringVersion -ne $ScoringVersion) { throw 'review cycle count requires the approved activeScoringVersion' }
  $ids = @($state.reviewCycleCompletedIssueIds)
  $keys = @($state.reviewCycleScoreKeys)
  $existingIndex = [array]::IndexOf($ids, $IssueId)
  if ($existingIndex -ge 0) {
    if ($keys[$existingIndex] -ne $ScoreKey) { throw 'issueId is already bound to a different score key in this review cycle' }
    return $state
  }
  if ($keys -contains $ScoreKey) { throw 'score key is already bound to another issue in this review cycle' }
  $now = [datetimeoffset]::Now.ToString('o')
  if (!$state.reviewCycleId) {
    Set-AutopilotProperty $state 'reviewCycleId' ('review-' + [guid]::NewGuid().ToString('N'))
    Set-AutopilotProperty $state 'reviewCycleStartedAt' $now
  }
  $ids += $IssueId
  $keys += $ScoreKey
  Set-AutopilotProperty $state 'reviewCycleCompletedIssueIds' @($ids)
  Set-AutopilotProperty $state 'reviewCycleScoreKeys' @($keys)
  Set-AutopilotProperty $state 'reviewCycleCompletedCount' $ids.Count
  Set-AutopilotProperty $state 'retrospectiveStatus' 'ACCUMULATING'
  if ($null -ne $BoundedBatchRemaining) { Set-AutopilotProperty $state 'remainingIterations' ([Math]::Max(0, [int]$BoundedBatchRemaining)) }
  if ($ids.Count -ge $Threshold) {
    Set-AutopilotProperty $state 'retrospectiveDue' $true
    Set-AutopilotProperty $state 'retrospectiveStatus' 'REQUIRED'
    if (!$state.retrospectiveRequiredAt) { Set-AutopilotProperty $state 'retrospectiveRequiredAt' $now }
    if ($null -eq $BoundedBatchRemaining -or $BoundedBatchRemaining -le 0) {
      Set-AutopilotProperty $state 'status' 'RETROSPECTIVE_REQUIRED'
      Set-AutopilotProperty $state 'phase' 'retrospective_required'
      Set-AutopilotProperty $state 'phaseStartedAt' $now
    }
  }
  Set-AutopilotProperty $state 'lastHeartbeatAt' $now
  return Write-AutopilotStateAtomic -Path $Path -State $state
}

function Set-AutopilotRetrospectivePhase {
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][ValidateSet('REPORT_COMMITTED','ISSUES_WRITTEN','GRAPH_REFRESHED','EPISODE_RECORDED')][string]$Phase,
    [hashtable]$Evidence = @{}
  )
  $order = @('NONE','REPORT_COMMITTED','ISSUES_WRITTEN','GRAPH_REFRESHED','EPISODE_RECORDED','RESET_COMMITTED')
  $state = Read-AutopilotState $Path
  if (!$state.retrospectiveDue) { throw 'retrospective phase cannot advance when retrospectiveDue=false' }
  $current = [array]::IndexOf($order, [string]$state.retrospectivePhase)
  $target = [array]::IndexOf($order, $Phase)
  if ($target -gt ($current + 1)) { throw "retrospective phase cannot skip from $($state.retrospectivePhase) to $Phase" }
  if ($target -lt $current) { return $state }
  Set-AutopilotProperty $state 'retrospectivePhase' $Phase
  Set-AutopilotProperty $state 'retrospectiveStatus' 'RUNNING'
  foreach ($name in @('retrospectiveReportCommit','retrospectiveFactsCommit','retrospectiveGraphGitCursor','retrospectiveEpisodeId','retrospectiveFailureCategory','lastRetrospectiveReport')) {
    if ($Evidence.ContainsKey($name)) { Set-AutopilotProperty $state $name $Evidence[$name] }
  }
  Set-AutopilotProperty $state 'lastHeartbeatAt' ([datetimeoffset]::Now.ToString('o'))
  return Write-AutopilotStateAtomic -Path $Path -State $state
}

function Reset-AutopilotReviewCycle {
  param([Parameter(Mandatory)][string]$Path)
  $state = Read-AutopilotState $Path
  if ([string]$state.retrospectivePhase -ne 'EPISODE_RECORDED') { throw 'review cycle cannot reset before EPISODE_RECORDED' }
  $now = [datetimeoffset]::Now.ToString('o')
  Set-AutopilotProperty $state 'lastRetrospectiveAt' $now
  Set-AutopilotProperty $state 'reviewCycleId' ''
  Set-AutopilotProperty $state 'reviewCycleStartedAt' $null
  Set-AutopilotProperty $state 'reviewCycleCompletedIssueIds' @()
  Set-AutopilotProperty $state 'reviewCycleScoreKeys' @()
  Set-AutopilotProperty $state 'reviewCycleCompletedCount' 0
  Set-AutopilotProperty $state 'retrospectiveDue' $false
  Set-AutopilotProperty $state 'retrospectiveStatus' 'COMPLETED'
  Set-AutopilotProperty $state 'retrospectivePhase' 'RESET_COMMITTED'
  Set-AutopilotProperty $state 'retrospectiveRequiredAt' $null
  Set-AutopilotProperty $state 'status' 'PAUSED'
  Set-AutopilotProperty $state 'phase' 'retrospective_completed'
  Set-AutopilotProperty $state 'phaseStartedAt' $now
  Set-AutopilotProperty $state 'lastHeartbeatAt' $now
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
