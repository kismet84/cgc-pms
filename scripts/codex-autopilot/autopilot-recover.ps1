$ErrorActionPreference = 'Stop'

$checkpointLibrary = Join-Path $PSScriptRoot 'autopilot-issue-checkpoint.ps1'
if (!(Get-Command Read-AutopilotIssueCheckpoint -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $checkpointLibrary)) {
  . $checkpointLibrary
}

function Get-AutopilotStallLevel {
  param([Parameter(Mandatory)][datetimeoffset]$LastProgressAt)
  $minutes = ([datetimeoffset]::Now - $LastProgressAt).TotalMinutes
  if ($minutes -ge 10) { return 'TERMINATE' }
  if ($minutes -ge 5) { return 'INSPECT' }
  return 'HEALTHY'
}

function Get-AutopilotRecoveryDecision {
  param([Parameter(Mandatory)][string]$AutoDir)
  $lockPath = Join-Path $AutoDir 'run.lock'
  $statePath = Join-Path $AutoDir 'state.json'
  $lock = $null
  if (Test-Path -LiteralPath $lockPath) {
    try { $lock = Get-Content -LiteralPath $lockPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { return [pscustomobject]@{ action = 'QUARANTINE'; reason = 'run.lock is unreadable' } }
  }
  $process = if ($lock.pid) { Get-Process -Id ([int]$lock.pid) -ErrorAction SilentlyContinue } else { $null }
  if ($process) { return [pscustomobject]@{ action = 'REFUSE_SECOND_INSTANCE'; reason = 'owner PID is alive'; runId = $lock.runId } }
  $state = $null
  if (Test-Path -LiteralPath $statePath) { try { $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json } catch {} }
  $checkpointPath = $null
  $checkpointDir = Join-Path $AutoDir 'checkpoints'
  if (Test-Path -LiteralPath $checkpointDir -PathType Container) {
    $checkpointFiles = @(Get-ChildItem -LiteralPath $checkpointDir -Filter '*.json' -File)
    $issueId = ''
    foreach ($candidate in @([string]$lock.issueId, [string]$state.currentIssue)) {
      $match = [regex]::Match($candidate, 'ISSUE-[0-9-]+')
      if ($match.Success) { $issueId = $match.Value; break }
    }
    if ($issueId) {
      $candidatePath = Get-AutopilotIssueCheckpointPath -AutoDir $AutoDir -IssueId $issueId
      if (Test-Path -LiteralPath $candidatePath) { $checkpointPath = $candidatePath }
    } elseif ($checkpointFiles.Count -eq 1) {
      $checkpointPath = $checkpointFiles[0].FullName
    } elseif ($checkpointFiles.Count -gt 1) {
      return [pscustomobject]@{ action='QUARANTINE'; reason='multiple active Issue checkpoints cannot be attributed to the dead run'; runId=$lock.runId }
    }
  }
  if ($checkpointPath) {
    try { $checkpoint = Read-AutopilotIssueCheckpoint -Path $checkpointPath }
    catch { return [pscustomobject]@{ action='QUARANTINE'; reason=$_.Exception.Message; runId=$lock.runId; checkpointPath=$checkpointPath } }
    $repoRoot = Split-Path -Parent $AutoDir
    $readyPath = Join-Path $repoRoot ([string]$checkpoint.readyPath)
    $closeoutCommitPresent = $false
    $closeoutAlreadyMerged = $false
    $boundManualReviewPass = $false
    try {
      if (!(Test-Path -LiteralPath $checkpoint.worktree -PathType Container)) { throw 'recoverable Issue worktree is missing' }
      $actualBranch = (& git -C $checkpoint.worktree branch --show-current 2>$null | Select-Object -First 1).Trim()
      if ($actualBranch -ne [string]$checkpoint.branch) { throw 'Issue worktree branch no longer matches checkpoint' }
      $mainHead = (& git -C $repoRoot rev-parse HEAD 2>$null | Select-Object -First 1).Trim()
      $worktreeHead = (& git -C $checkpoint.worktree rev-parse HEAD 2>$null | Select-Object -First 1).Trim()
      if (!$mainHead -or !$worktreeHead) { throw 'cannot resolve recovery Git heads' }
      $terminalPhases = @('CLOSING','CLOSEOUT_COMMITTED','REGISTERED','CLOSED')
      if ([string]$checkpoint.phase -in $terminalPhases) {
        try {
          $normalizedHash = Get-AutopilotReadyContractHash -ReadyPath (Join-Path $checkpoint.worktree ([string]$checkpoint.readyPath)) -IssueId $checkpoint.issueId -NormalizeDoneStatus
          $closeoutCommitPresent = $normalizedHash -eq [string]$checkpoint.readyContentHash
        } catch { $closeoutCommitPresent = $false }
      }
      if ($closeoutCommitPresent) {
        $expectedCloseout = [string](Get-AutopilotCheckpointProperty $checkpoint.evidence 'closeoutCommit' '')
        if ($expectedCloseout -and $worktreeHead -ne $expectedCloseout) { throw 'worktree HEAD no longer matches the checkpoint closeout commit' }
        & git -C $repoRoot merge-base --is-ancestor $worktreeHead $mainHead 2>$null
        $closeoutAlreadyMerged = $LASTEXITCODE -eq 0
        if ($closeoutAlreadyMerged) {
          $mainNormalizedHash = Get-AutopilotReadyContractHash -ReadyPath $readyPath -IssueId $checkpoint.issueId -NormalizeDoneStatus
          if ($mainNormalizedHash -ne [string]$checkpoint.readyContentHash) { throw 'merged Ready contract no longer matches the dispatch contract' }
        } elseif ($mainHead -ne [string]$checkpoint.baseCommit) { throw 'base branch advanced before the durable closeout commit was merged' }
      } else {
        $readyHash = Get-AutopilotReadyContractHash -ReadyPath $readyPath -IssueId $checkpoint.issueId
        if ($readyHash -ne [string]$checkpoint.readyContentHash) { throw 'Ready contract hash changed after Issue dispatch' }
        if ($mainHead -ne [string]$checkpoint.baseCommit) { throw 'base branch advanced after Issue dispatch' }
      }
      $actualDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $checkpoint.worktree -BaseCommit $checkpoint.baseCommit
      if (!$closeoutCommitPresent -and $checkpoint.evidence.diffHash -and $actualDiffHash -ne [string]$checkpoint.evidence.diffHash) { throw 'Issue diff hash no longer matches checkpoint' }
      if ([string]$checkpoint.phase -eq 'REVIEW_TOOL_BLOCKED' -and $checkpoint.artifacts.reviewResultPath -and (Test-Path -LiteralPath $checkpoint.artifacts.reviewResultPath -PathType Leaf)) {
        try {
          $manualReview = Get-Content -LiteralPath $checkpoint.artifacts.reviewResultPath -Raw -Encoding UTF8 | ConvertFrom-Json
          $boundManualReviewPass = [string]$manualReview.decision -eq 'pass' -and [string]$manualReview.issueId -eq [string]$checkpoint.issueId -and [string]$manualReview.reviewedDiffHash -eq $actualDiffHash
        } catch { $boundManualReviewPass = $false }
      }
    } catch {
      try { Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase QUARANTINED -QuarantineReason $_.Exception.Message | Out-Null } catch {}
      return [pscustomobject]@{ action='QUARANTINE'; reason=$_.Exception.Message; runId=$lock.runId; worktree=$checkpoint.worktree; checkpointPath=$checkpointPath; issueId=$checkpoint.issueId }
    }
    $action = if ($closeoutAlreadyMerged -and [string]$checkpoint.phase -ne 'CLOSED') { 'RESUME_FINALIZE' } else { switch ([string]$checkpoint.phase) {
      'IMPLEMENTED' { if ([int](Get-AutopilotCheckpointProperty $checkpoint.metrics 'environmentRetryCount' 0) -ge 2) { 'PAUSE_ENVIRONMENT_RETRY_EXHAUSTED' } else { 'RESUME_VALIDATION' } }
      'VALIDATING' { 'RESUME_VALIDATION' }
      'VALIDATED' { 'RESUME_REVIEW' }
      'REVIEWING' { 'RESUME_REVIEW' }
      'REVIEW_TOOL_BLOCKED' { if ($boundManualReviewPass) { 'RESUME_REVIEW' } elseif ([int]$checkpoint.metrics.reviewDispatchCount -ge 2) { 'PAUSE_REVIEW_TOOL_BLOCKED' } else { 'RESUME_REVIEW' } }
      'REVIEWED' { 'RESUME_CLOSEOUT' }
      'CLOSING' { 'RESUME_CLOSEOUT' }
      'IMPLEMENTATION_COMMITTED' { 'RESUME_SCORE_AND_CLOSEOUT' }
      'CLOSEOUT_COMMITTED' { 'RESUME_MERGE_AND_REGISTER' }
      'REGISTERED' { 'RESUME_FINALIZE' }
      'CLOSED' { 'CLEAN_CLOSED_CHECKPOINT' }
      'QUARANTINED' { 'QUARANTINE' }
      default { 'VERIFY_UNCOMMITTED' }
    } }
    $reason = if ($action -eq 'PAUSE_REVIEW_TOOL_BLOCKED') { 'Reviewer tool retry exhausted; preserve worktree and wait for tool recovery or bound manual review' } elseif ($action -eq 'PAUSE_ENVIRONMENT_RETRY_EXHAUSTED') { 'classified environment validation retry exhausted; preserve implementation and wait for environment recovery evidence' } elseif ($action -eq 'VERIFY_UNCOMMITTED') { 'implementation phase lacks durable completion evidence' } else { "resume from durable Issue phase $($checkpoint.phase)" }
    return [pscustomobject]@{ action=$action; reason=$reason; runId=$lock.runId; worktree=$checkpoint.worktree; checkpointPath=$checkpointPath; checkpoint=$checkpoint; issueId=$checkpoint.issueId }
  }
  if ($state -and $state.worktree -and (Test-Path -LiteralPath $state.worktree)) {
    $changes = @(& git -C $state.worktree status --porcelain=v1 2>$null)
    if ($changes.Count -eq 0) {
      $repoRoot = Split-Path -Parent $AutoDir
      $mainHead = (& git -C $repoRoot rev-parse HEAD 2>$null | Select-Object -First 1).Trim()
      $worktreeHead = (& git -C $state.worktree rev-parse HEAD 2>$null | Select-Object -First 1).Trim()
      if ($mainHead -and $worktreeHead -and $mainHead -ne $worktreeHead) {
        & git -C $repoRoot merge-base --is-ancestor $mainHead $worktreeHead 2>$null
        if ($LASTEXITCODE -eq 0) { return [pscustomobject]@{ action = 'QUARANTINE'; reason = 'unmerged issue commit lacks a durable Issue checkpoint; worktree preserved for manual evidence recovery'; runId = $lock.runId; worktree = $state.worktree; commit = $worktreeHead } }
      }
    }
  }
  if ($state -and $state.lastCommit -and $state.status -in @('CLOSING','COMMITTING','MERGING')) { return [pscustomobject]@{ action = 'QUARANTINE'; reason = 'closeout commit exists without a safely recoverable worktree'; runId = $lock.runId } }
  if ($state -and $state.worktree -and (Test-Path -LiteralPath $state.worktree)) {
    $changes = @(& git -C $state.worktree status --porcelain=v1 2>$null)
    if ($changes.Count -gt 0) { return [pscustomobject]@{ action = 'VERIFY_UNCOMMITTED'; reason = 'dead executor left a diff'; runId = $lock.runId; worktree = $state.worktree } }
  }
  if (!$lock) { return [pscustomobject]@{ action = 'NEW_RUN'; reason = 'no run.lock and no residual worktree' } }
  return [pscustomobject]@{ action = 'RESUME_FROM_CHECKPOINT'; reason = 'owner PID is gone and no unsafe diff was found'; runId = $lock.runId }
}

function Remove-AutopilotResidualWorktree {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$Worktree)
  $repoFull = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\')
  $worktreeFull = [IO.Path]::GetFullPath($Worktree).TrimEnd('\')
  if (!$worktreeFull.StartsWith($repoFull + '\.worktrees\', [StringComparison]::OrdinalIgnoreCase)) { throw 'residual worktree is outside the repository isolation root' }
  $branch = (& git -C $Worktree branch --show-current 2>$null | Select-Object -First 1).Trim()
  & git -c core.longpaths=true -C $RepoRoot worktree remove --force $Worktree | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'failed to remove residual issue worktree' }
  if (Test-Path -LiteralPath $worktreeFull) {
    $deletePath = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '\\?\' + $worktreeFull } else { $worktreeFull }
    Remove-Item -LiteralPath $deletePath -Recurse -Force
  }
  if (Test-Path -LiteralPath $worktreeFull) { throw 'residual issue worktree directory still exists after removal' }
  if ($branch) { & git -C $RepoRoot branch -D $branch 2>$null | Out-Null }
  return [pscustomobject]@{ removed=$true; branch=$branch }
}

function Get-AutopilotCloseoutKey {
  param([string]$IssueId, [string]$Commit, [string]$ReportPath)
  $source = "$IssueId|$Commit|$($ReportPath.Replace('\','/').ToLowerInvariant())"
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($source)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}

function Register-AutopilotCloseout {
  param([string]$LedgerPath, [string]$Key)
  $parent = Split-Path -Parent $LedgerPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  if (Test-Path -LiteralPath $LedgerPath) {
    foreach ($line in Get-Content -Encoding UTF8 -LiteralPath $LedgerPath) {
      try { if (($line | ConvertFrom-Json).key -eq $Key) { return $false } } catch {}
    }
  }
  $entry = [ordered]@{ key = $Key; registeredAt = [datetimeoffset]::Now.ToString('o') } | ConvertTo-Json -Compress
  [IO.File]::AppendAllText($LedgerPath, $entry + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
  return $true
}

function Test-AutopilotCloseoutRegistered {
  param([Parameter(Mandatory)][string]$LedgerPath, [Parameter(Mandatory)][string]$Key)
  if (!(Test-Path -LiteralPath $LedgerPath -PathType Leaf)) { return $false }
  foreach ($line in Get-Content -Encoding UTF8 -LiteralPath $LedgerPath) {
    try { if (($line | ConvertFrom-Json).key -eq $Key) { return $true } } catch {}
  }
  return $false
}

function Set-AutopilotCloseoutCandidateScore {
  param(
    [Parameter(Mandatory)][string]$LedgerPath,
    [Parameter(Mandatory)][string]$Key,
    [Parameter(Mandatory)][object]$Score,
    [Parameter(Mandatory)][object]$PhaseMetrics
  )
  $entries = @()
  $found = $false
  foreach ($line in $(if (Test-Path -LiteralPath $LedgerPath -PathType Leaf) { Get-Content -Encoding UTF8 -LiteralPath $LedgerPath } else { @() })) {
    if (!$line.Trim()) { continue }
    $entry = $line | ConvertFrom-Json
    if ([string]$entry.key -eq $Key) {
      $entry | Add-Member -NotePropertyName candidateScoreV2 -NotePropertyValue $Score -Force
      $entry | Add-Member -NotePropertyName phaseMetrics -NotePropertyValue $PhaseMetrics -Force
      $entry | Add-Member -NotePropertyName candidateScoreUpdatedAt -NotePropertyValue ([datetimeoffset]::Now.ToString('o')) -Force
      $found = $true
    }
    $entries += $entry
  }
  if (!$found) {
    $entries += [pscustomobject]@{ key=$Key; candidateScoreV2=$Score; phaseMetrics=$PhaseMetrics; candidateScoreUpdatedAt=[datetimeoffset]::Now.ToString('o') }
  }
  $parent = Split-Path -Parent $LedgerPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  $content = (@($entries | ForEach-Object { $_ | ConvertTo-Json -Depth 16 -Compress }) -join [Environment]::NewLine) + [Environment]::NewLine
  $temp = "$LedgerPath.$([guid]::NewGuid().ToString('N')).tmp"
  try {
    [IO.File]::WriteAllText($temp, $content, [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temp -Destination $LedgerPath -Force
  } finally { Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue }
  $registered = @(Get-Content -Encoding UTF8 -LiteralPath $LedgerPath | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { $_.key -eq $Key } | Select-Object -First 1)
  if ($registered.Count -ne 1 -or $null -eq $registered[0].candidateScoreV2 -or [string]$registered[0].candidateScoreV2.key -ne [string]$Score.key -or [int]$registered[0].candidateScoreV2.total -ne [int]$Score.total -or [int]$registered[0].candidateScoreV2.dimensions.taskExecutionEfficiency.score -ne [int]$Score.dimensions.taskExecutionEfficiency.score) { throw 'candidate score ledger read-back failed' }
  return $registered[0]
}
