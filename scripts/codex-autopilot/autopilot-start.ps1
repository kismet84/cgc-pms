param(
  [string]$Repo = "D:\projects-test\cgc-pms",
  [Nullable[int]]$MaxIterations = $null
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-state.ps1')

if ($null -ne $MaxIterations -and ($MaxIterations -lt 1 -or $MaxIterations -gt 50)) {
  throw 'MaxIterations must be a positive integer between 1 and 50.'
}

$autoDir = Join-Path $Repo '.codex-autopilot'
$statePath = Join-Path $autoDir 'state.json'
$now = [datetimeoffset]::Now.ToString('o')
New-Item -ItemType Directory -Path $autoDir -Force | Out-Null

$existing = $null
if (Test-Path -LiteralPath $statePath) {
  try { $existing = Read-AutopilotState -Path $statePath } catch { throw "AutoPilot state cannot be resumed safely: $($_.Exception.Message)" }
}
if ($existing -and [bool]$existing.retrospectiveDue) {
  throw 'RETROSPECTIVE_REQUIRED: complete the pending retrospective before starting a new iteration batch'
}
Remove-Item (Join-Path $autoDir 'stop.flag') -ErrorAction SilentlyContinue
Remove-Item (Join-Path $autoDir 'pause.flag') -ErrorAction SilentlyContinue
"started at $now" | Out-File -Encoding utf8 (Join-Path $autoDir 'start.flag')
'enabled' | Out-File -Encoding utf8 (Join-Path $autoDir 'enabled.flag')

function Get-ExistingStateValue {
  param([string]$Name, [object]$Default = $null)
  if ($null -ne $existing -and $existing.PSObject.Properties.Name -contains $Name) { return $existing.$Name }
  return $Default
}

function Get-ExistingTimestampString {
  param([string]$Name, [string]$Default)
  $value = Get-ExistingStateValue $Name $Default
  if ($value -is [datetimeoffset]) { return $value.ToString('o') }
  if ($value -is [datetime]) { return $value.ToString('yyyy-MM-ddTHH:mm:ssK') }
  return [string]$value
}

$reset = $null -ne $MaxIterations
$existingLimit = Get-ExistingStateValue 'iterationLimit'
$existingCompletedV2 = Get-ExistingStateValue 'completedImplementationIssues'
$existingCompletedLegacy = Get-ExistingStateValue 'iterationCompleted'
$limit = if ($reset) { [int]$MaxIterations } elseif ($null -ne $existingLimit) { [int]$existingLimit } else { $null }
$completed = if ($reset) { 0 } elseif ($null -ne $existingCompletedV2) { [int]$existingCompletedV2 } elseif ($null -ne $existingCompletedLegacy) { [int]$existingCompletedLegacy } else { 0 }
$completedIds = @(if (!$reset -and $null -ne $existing -and $existing.PSObject.Properties.Name -contains 'completedIssueIds') { @($existing.completedIssueIds) })
while ($completedIds.Count -lt $completed) { $completedIds += "legacy-completed-$($completedIds.Count + 1)" }
$remaining = if ($null -eq $limit) { $null } else { [Math]::Max(0, $limit - $completed) }

$state = [ordered]@{
  schemaVersion = 3
  runId = if (!$reset -and (Get-ExistingStateValue 'runId')) { [string](Get-ExistingStateValue 'runId') } else { 'run-' + [datetimeoffset]::Now.ToString('yyyyMMdd-HHmmss-fff') }
  status = 'IDLE'
  phase = 'idle'
  currentIssue = ''
  attempt = 0
  startedAt = if (!$reset -and (Get-ExistingStateValue 'startedAt')) { Get-ExistingTimestampString 'startedAt' $now } else { $now }
  phaseStartedAt = $now
  lastHeartbeatAt = $now
  iterationLimit = $limit
  completedImplementationIssues = $completed
  completedIssueIds = @($completedIds)
  worktree = ''
  branch = ''
  executorPid = $null
  lastCommit = $null
  failureFingerprint = $null
  reviewCycleId = [string](Get-ExistingStateValue 'reviewCycleId' '')
  reviewCycleStartedAt = Get-ExistingStateValue 'reviewCycleStartedAt'
  reviewCycleCompletedIssueIds = @(Get-ExistingStateValue 'reviewCycleCompletedIssueIds' @())
  reviewCycleScoreKeys = @(Get-ExistingStateValue 'reviewCycleScoreKeys' @())
  reviewCycleCompletedCount = [int](Get-ExistingStateValue 'reviewCycleCompletedCount' 0)
  retrospectiveDue = [bool](Get-ExistingStateValue 'retrospectiveDue' $false)
  retrospectiveStatus = [string](Get-ExistingStateValue 'retrospectiveStatus' 'IDLE')
  retrospectivePhase = [string](Get-ExistingStateValue 'retrospectivePhase' 'NONE')
  retrospectiveRequiredAt = Get-ExistingStateValue 'retrospectiveRequiredAt'
  retrospectiveReportCommit = Get-ExistingStateValue 'retrospectiveReportCommit'
  retrospectiveFactsCommit = Get-ExistingStateValue 'retrospectiveFactsCommit'
  retrospectiveGraphGitCursor = Get-ExistingStateValue 'retrospectiveGraphGitCursor'
  retrospectiveEpisodeId = Get-ExistingStateValue 'retrospectiveEpisodeId'
  retrospectiveFailureCategory = Get-ExistingStateValue 'retrospectiveFailureCategory'
  lastRetrospectiveAt = Get-ExistingStateValue 'lastRetrospectiveAt'
  lastRetrospectiveReport = Get-ExistingStateValue 'lastRetrospectiveReport'
  activeScoringVersion = Get-ExistingStateValue 'activeScoringVersion'
  lastCanaryFingerprint = [string](Get-ExistingStateValue 'lastCanaryFingerprint' '')
  lastCanaryReport = [string](Get-ExistingStateValue 'lastCanaryReport' '')
  controlPlaneFingerprint = [string](Get-ExistingStateValue 'controlPlaneFingerprint' '')
  executionHost = [string](Get-ExistingStateValue 'executionHost' 'cli-legacy')
  enabled = $true
  mode = 'continuous-runner'
  iterationCompleted = $completed
  remainingIterations = $remaining
  iterationLastCountedIssue = if ($reset) { '' } else { [string](Get-ExistingStateValue 'iterationLastCountedIssue' '') }
  lastAction = 'STARTED'
  lastReason = if ($reset) { 'NEW_ITERATION' } else { 'RESUMED' }
  stopReason = ''
  stopRequested = $false
  autoMerge = [bool](Get-ExistingStateValue 'autoMerge' $true)
  autoPush = $false
  allowTestDataReset = [bool](Get-ExistingStateValue 'allowTestDataReset' $true)
}

Write-AutopilotStateAtomic -Path $statePath -State $state | Out-Null
Write-Host 'CGC-PMS Codex AutoPilot started.'
