$ErrorActionPreference = 'Stop'

$script:AutopilotIssueCheckpointVersion = 1
$script:AutopilotIssuePhases = @(
  'IMPLEMENTING','IMPLEMENTED','VALIDATING','VALIDATED','REVIEWING','REVIEW_TOOL_BLOCKED',
  'REVIEWED','REPAIRING','CLOSING','IMPLEMENTATION_COMMITTED','CLOSEOUT_COMMITTED',
  'REGISTERED','CLOSED','QUARANTINED'
)

function Get-AutopilotCheckpointProperty {
  param([object]$Object, [string]$Name, [object]$Default = $null)
  if ($null -eq $Object) { return $Default }
  if ($Object -is [System.Collections.IDictionary] -and $Object.Contains($Name)) { return $Object[$Name] }
  if ($Object.PSObject.Properties.Name -contains $Name) { return $Object.$Name }
  return $Default
}

function Set-AutopilotCheckpointProperty {
  param([object]$Object, [string]$Name, [object]$Value)
  if ($Object -is [System.Collections.IDictionary]) { $Object[$Name] = $Value }
  else { $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force }
}

function Get-AutopilotCheckpointSha256 {
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '').ToLowerInvariant() }
  finally { $sha.Dispose() }
}

function Get-AutopilotReadyContractHash {
  param(
    [Parameter(Mandatory)][string]$ReadyPath,
    [Parameter(Mandatory)][string]$IssueId,
    [switch]$NormalizeDoneStatus
  )
  if (!(Test-Path -LiteralPath $ReadyPath -PathType Leaf)) { throw "Ready file is missing: $ReadyPath" }
  $text = Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8
  $match = [regex]::Match($text, '(?ms)^###\s+' + [regex]::Escape($IssueId) + '(?=[^0-9-]).*?(?=^###\s+ISSUE-|\z)')
  if (!$match.Success) { throw "Ready contract is missing: $IssueId" }
  $contract = $match.Value
  if ($NormalizeDoneStatus) {
    if ($contract -notmatch '(?m)^状态：Done[ \t]*(?=\r?$)') { throw "terminal Ready contract is not Done: $IssueId" }
    $contract = [regex]::Replace($contract, '(?m)^状态：Done[ \t]*(?=\r?$)', '状态：Ready')
  }
  return Get-AutopilotCheckpointSha256 ($contract -replace "`r`n", "`n")
}

function Get-AutopilotScopeHash {
  param([string[]]$Paths)
  $normalized = @($Paths | ForEach-Object { ([string]$_).Replace('\','/').Trim() } | Where-Object { $_ } | Sort-Object -Unique)
  return Get-AutopilotCheckpointSha256 ($normalized -join "`n")
}

function Get-AutopilotRecoveryDiffHash {
  param([Parameter(Mandatory)][string]$Worktree, [Parameter(Mandatory)][string]$BaseCommit)
  if (Get-Command Get-AutopilotDiffHash -ErrorAction SilentlyContinue) {
    return Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $BaseCommit
  }
  $diff = @(& git -c core.autocrlf=false -c core.quotePath=false -C $Worktree diff --binary $BaseCommit -- 2>$null) -join "`n"
  if ($LASTEXITCODE -ne 0) { throw 'failed to calculate recovery diff' }
  $untracked = @(& git -c core.quotePath=false -C $Worktree ls-files --others --exclude-standard 2>$null | Sort-Object)
  $untrackedEvidence = foreach ($relative in $untracked) {
    $full = Join-Path $Worktree $relative
    $hash = if (Test-Path -LiteralPath $full -PathType Leaf) { (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant() } else { '' }
    "$relative|$hash"
  }
  return Get-AutopilotCheckpointSha256 ($diff + "`n--untracked--`n" + (@($untrackedEvidence) -join "`n"))
}

function Get-AutopilotIssueCheckpointPath {
  param([Parameter(Mandatory)][string]$AutoDir, [Parameter(Mandatory)][string]$IssueId)
  if ($IssueId -notmatch '^ISSUE-[0-9-]+$') { throw "invalid checkpoint Issue ID: $IssueId" }
  return Join-Path (Join-Path $AutoDir 'checkpoints') ($IssueId.ToLowerInvariant() + '.json')
}

function Assert-AutopilotIssueCheckpoint {
  param([Parameter(Mandatory)][object]$Checkpoint)
  $required = @(
    'schemaVersion','issueId','readyPath','readyContentHash','baseCommit','worktree','branch',
    'allowedPathsHash','forbiddenPathsHash','phase','createdAt','updatedAt','phaseStartedAt',
    'lastHeartbeatAt','artifacts','evidence','metrics','quarantineReason'
  )
  foreach ($name in $required) {
    $present = ($Checkpoint -is [System.Collections.IDictionary] -and $Checkpoint.Contains($name)) -or ($Checkpoint.PSObject.Properties.Name -contains $name)
    if (!$present) { throw "Issue checkpoint missing required property: $name" }
  }
  if ([int]$Checkpoint.schemaVersion -ne $script:AutopilotIssueCheckpointVersion) { throw "Unsupported Issue checkpoint schemaVersion: $($Checkpoint.schemaVersion)" }
  if ([string]$Checkpoint.issueId -notmatch '^ISSUE-[0-9-]+$') { throw 'Issue checkpoint has invalid issueId' }
  if ([string]$Checkpoint.readyContentHash -notmatch '^[a-f0-9]{64}$') { throw 'Issue checkpoint has invalid readyContentHash' }
  if ([string]$Checkpoint.baseCommit -notmatch '^[a-f0-9]{40}$') { throw 'Issue checkpoint has invalid baseCommit' }
  if ([string]$Checkpoint.phase -notin $script:AutopilotIssuePhases) { throw "Issue checkpoint has invalid phase: $($Checkpoint.phase)" }
  foreach ($name in @('allowedPathsHash','forbiddenPathsHash')) {
    if ([string](Get-AutopilotCheckpointProperty $Checkpoint $name '') -notmatch '^[a-f0-9]{64}$') { throw "Issue checkpoint has invalid $name" }
  }
  foreach ($name in @('createdAt','updatedAt','phaseStartedAt','lastHeartbeatAt')) {
    [datetimeoffset]$parsed = [datetimeoffset]::MinValue
    if (![datetimeoffset]::TryParse([string](Get-AutopilotCheckpointProperty $Checkpoint $name ''), [ref]$parsed)) { throw "Issue checkpoint has invalid timestamp: $name" }
  }
  $metrics = $Checkpoint.metrics
  foreach ($name in @('implementationDispatchCount','validationDispatchCount','reviewDispatchCount','repairDispatchCount','closeoutDispatchCount','runResumeCount','phaseRestartCount','manualRecoveryCount','toolConfigBlockCount','environmentRetryCount','duplicateDispatchBlockedCount')) {
    if ([int](Get-AutopilotCheckpointProperty $metrics $name -1) -lt 0) { throw "Issue checkpoint has invalid metric: $name" }
  }
  return $true
}

function Write-AutopilotIssueCheckpointAtomic {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][object]$Checkpoint)
  $now = [datetimeoffset]::Now.ToString('o')
  Set-AutopilotCheckpointProperty $Checkpoint 'updatedAt' $now
  Set-AutopilotCheckpointProperty $Checkpoint 'lastHeartbeatAt' $now
  $json = $Checkpoint | ConvertTo-Json -Depth 16
  $parsed = $json | ConvertFrom-Json
  Assert-AutopilotIssueCheckpoint $parsed | Out-Null
  $parent = Split-Path -Parent $Path
  if (!(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  $temp = "$Path.$([guid]::NewGuid().ToString('N')).tmp"
  $backup = "$Path.$([guid]::NewGuid().ToString('N')).bak"
  try {
    [IO.File]::WriteAllText($temp, $json, [Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $Path) { [IO.File]::Replace($temp, $Path, $backup, $true) }
    else { [IO.File]::Move($temp, $Path) }
  } finally {
    Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $backup -Force -ErrorAction SilentlyContinue
  }
  return $parsed
}

function Read-AutopilotIssueCheckpoint {
  param([Parameter(Mandatory)][string]$Path)
  if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Issue checkpoint is missing: $Path" }
  try { $checkpoint = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json }
  catch { throw "Issue checkpoint is invalid JSON: $Path" }
  Assert-AutopilotIssueCheckpoint $checkpoint | Out-Null
  return $checkpoint
}

function New-AutopilotIssueCheckpoint {
  param(
    [Parameter(Mandatory)][string]$AutoDir,
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$ReadyPath,
    [Parameter(Mandatory)][string]$BaseCommit,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$Branch,
    [string[]]$AllowedPaths = @(),
    [string[]]$ForbiddenPaths = @(),
    [string]$ArtifactDirectory = ''
  )
  $path = Get-AutopilotIssueCheckpointPath -AutoDir $AutoDir -IssueId $IssueId
  if (Test-Path -LiteralPath $path) { throw "Issue checkpoint already exists; fresh implementation dispatch refused: $IssueId" }
  $repoRoot = Split-Path -Parent $AutoDir
  $readyFull = [IO.Path]::GetFullPath($ReadyPath)
  $repoPrefix = [IO.Path]::GetFullPath($repoRoot).TrimEnd('\') + '\'
  $readyRelative = if ($readyFull.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) { $readyFull.Substring($repoPrefix.Length).Replace('\','/') } else { $ReadyPath.Replace('\','/') }
  $now = [datetimeoffset]::Now.ToString('o')
  $checkpoint = [ordered]@{
    schemaVersion = 1
    issueId = $IssueId
    readyPath = $readyRelative
    readyContentHash = Get-AutopilotReadyContractHash -ReadyPath $ReadyPath -IssueId $IssueId
    baseCommit = $BaseCommit.ToLowerInvariant()
    worktree = [IO.Path]::GetFullPath($Worktree)
    branch = $Branch
    allowedPathsHash = Get-AutopilotScopeHash $AllowedPaths
    forbiddenPathsHash = Get-AutopilotScopeHash $ForbiddenPaths
    phase = 'IMPLEMENTING'
    createdAt = $now
    updatedAt = $now
    phaseStartedAt = $now
    lastHeartbeatAt = $now
    artifacts = [ordered]@{ issueDirectory=$ArtifactDirectory; resultPath=''; evidencePaths=@(); reviewRequestPath=''; reviewResultPath=''; archiveReport='' }
    evidence = [ordered]@{ diffHash=''; verificationDiffHash=''; reviewDiffHash=''; implementationCommit=''; closeoutCommit='' }
    metrics = [ordered]@{
      implementationDispatchCount=0; validationDispatchCount=0; reviewDispatchCount=0; repairDispatchCount=0; closeoutDispatchCount=0
      runResumeCount=0; phaseRestartCount=0; manualRecoveryCount=0; toolConfigBlockCount=0; environmentRetryCount=0; duplicateDispatchBlockedCount=0
      wallClockSeconds=0; phaseDurationsSeconds=[ordered]@{}
    }
    quarantineReason = $null
  }
  return Write-AutopilotIssueCheckpointAtomic -Path $path -Checkpoint $checkpoint
}

function Set-AutopilotIssueCheckpointPhase {
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
  $checkpoint = Read-AutopilotIssueCheckpoint $Path
  $previousPhase = [string]$checkpoint.phase
  $now = [datetimeoffset]::Now
  $phaseStarted = [datetimeoffset]$checkpoint.phaseStartedAt
  $durations = $checkpoint.metrics.phaseDurationsSeconds
  $elapsed = [Math]::Max(0, [int][Math]::Round(($now - $phaseStarted).TotalSeconds))
  $prior = [int](Get-AutopilotCheckpointProperty $durations $previousPhase 0)
  Set-AutopilotCheckpointProperty $durations $previousPhase ($prior + $elapsed)
  if ($IncrementDispatch) {
    $metricName = $IncrementDispatch + 'DispatchCount'
    $current = [int](Get-AutopilotCheckpointProperty $checkpoint.metrics $metricName 0)
    if ($IncrementDispatch -eq 'implementation' -and $current -ge 1) {
      Set-AutopilotCheckpointProperty $checkpoint.metrics 'duplicateDispatchBlockedCount' ([int]$checkpoint.metrics.duplicateDispatchBlockedCount + 1)
      Write-AutopilotIssueCheckpointAtomic -Path $Path -Checkpoint $checkpoint | Out-Null
      throw 'duplicate implementation dispatch blocked by Issue checkpoint'
    }
    Set-AutopilotCheckpointProperty $checkpoint.metrics $metricName ($current + 1)
  }
  if ($IncrementPhaseRestart) { Set-AutopilotCheckpointProperty $checkpoint.metrics 'phaseRestartCount' ([int]$checkpoint.metrics.phaseRestartCount + 1) }
  if ($IncrementRunResume) { Set-AutopilotCheckpointProperty $checkpoint.metrics 'runResumeCount' ([int]$checkpoint.metrics.runResumeCount + 1) }
  if ($IncrementManualRecovery) { Set-AutopilotCheckpointProperty $checkpoint.metrics 'manualRecoveryCount' ([int]$checkpoint.metrics.manualRecoveryCount + 1) }
  if ($IncrementToolConfigBlock) { Set-AutopilotCheckpointProperty $checkpoint.metrics 'toolConfigBlockCount' ([int]$checkpoint.metrics.toolConfigBlockCount + 1) }
  if ($IncrementEnvironmentRetry) { Set-AutopilotCheckpointProperty $checkpoint.metrics 'environmentRetryCount' ([int]$checkpoint.metrics.environmentRetryCount + 1) }
  foreach ($name in $Artifacts.Keys) { Set-AutopilotCheckpointProperty $checkpoint.artifacts $name $Artifacts[$name] }
  foreach ($name in $Evidence.Keys) { Set-AutopilotCheckpointProperty $checkpoint.evidence $name $Evidence[$name] }
  if ($Phase -eq 'QUARANTINED') { Set-AutopilotCheckpointProperty $checkpoint 'quarantineReason' $QuarantineReason }
  Set-AutopilotCheckpointProperty $checkpoint 'phase' $Phase
  Set-AutopilotCheckpointProperty $checkpoint 'phaseStartedAt' $now.ToString('o')
  Set-AutopilotCheckpointProperty $checkpoint.metrics 'wallClockSeconds' ([Math]::Max(0, [int][Math]::Round(($now - [datetimeoffset]$checkpoint.createdAt).TotalSeconds)))
  return Write-AutopilotIssueCheckpointAtomic -Path $Path -Checkpoint $checkpoint
}

function Move-AutopilotIssueCheckpointBaseForward {
  param(
    [Parameter(Mandatory)][string]$Path,
    [Parameter(Mandatory)][string]$BaseCommit,
    [Parameter(Mandatory)][string]$DiffHash
  )
  if ($BaseCommit -notmatch '^[a-f0-9]{40}$') { throw 'forwarded Issue checkpoint has invalid base commit' }
  if ($DiffHash -notmatch '^[a-f0-9]{64}$') { throw 'forwarded Issue checkpoint has invalid diff hash' }
  $checkpoint = Read-AutopilotIssueCheckpoint $Path
  $now = [datetimeoffset]::Now
  $phaseStarted = [datetimeoffset]$checkpoint.phaseStartedAt
  $durations = $checkpoint.metrics.phaseDurationsSeconds
  $previousPhase = [string]$checkpoint.phase
  $prior = [int](Get-AutopilotCheckpointProperty $durations $previousPhase 0)
  Set-AutopilotCheckpointProperty $durations $previousPhase ($prior + [Math]::Max(0, [int][Math]::Round(($now - $phaseStarted).TotalSeconds)))
  Set-AutopilotCheckpointProperty $checkpoint 'baseCommit' $BaseCommit.ToLowerInvariant()
  Set-AutopilotCheckpointProperty $checkpoint 'phase' 'IMPLEMENTED'
  Set-AutopilotCheckpointProperty $checkpoint 'phaseStartedAt' $now.ToString('o')
  Set-AutopilotCheckpointProperty $checkpoint 'quarantineReason' $null
  Set-AutopilotCheckpointProperty $checkpoint.artifacts 'evidencePaths' @()
  Set-AutopilotCheckpointProperty $checkpoint.artifacts 'reviewRequestPath' ''
  Set-AutopilotCheckpointProperty $checkpoint.artifacts 'reviewResultPath' ''
  Set-AutopilotCheckpointProperty $checkpoint.evidence 'diffHash' $DiffHash
  Set-AutopilotCheckpointProperty $checkpoint.evidence 'verificationDiffHash' ''
  Set-AutopilotCheckpointProperty $checkpoint.evidence 'reviewDiffHash' ''
  Set-AutopilotCheckpointProperty $checkpoint.metrics 'manualRecoveryCount' ([int]$checkpoint.metrics.manualRecoveryCount + 1)
  Set-AutopilotCheckpointProperty $checkpoint.metrics 'wallClockSeconds' ([Math]::Max(0, [int][Math]::Round(($now - [datetimeoffset]$checkpoint.createdAt).TotalSeconds)))
  return Write-AutopilotIssueCheckpointAtomic -Path $Path -Checkpoint $checkpoint
}

function Remove-AutopilotIssueCheckpoint {
  param([Parameter(Mandatory)][string]$Path, [switch]$Closed)
  $checkpoint = Read-AutopilotIssueCheckpoint $Path
  if (!$Closed -and [string]$checkpoint.phase -ne 'CLOSED') { throw 'active Issue checkpoint cannot be removed before formal closeout' }
  Remove-Item -LiteralPath $Path -Force
}
