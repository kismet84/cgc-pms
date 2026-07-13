$ErrorActionPreference = 'Stop'
$nativeLibrary = Join-Path $PSScriptRoot 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $nativeLibrary)) { . $nativeLibrary }

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

function Get-AutopilotRecoveryFailureCategory {
  param([Parameter(Mandatory)][string]$Message)
  if ($Message -match '(?i)Native command failed|not installed|not available|cannot read run\.lock|AUTOPILOT_POWERSHELL7_REQUIRED') { return 'tool_config' }
  if ($Message -match '(?i)ECONNREFUSED|Docker|database|port|runtime') { return 'environment_prereq' }
  return 'integrity_conflict'
}

function Test-AutopilotBoundChildResult {
  param([Parameter(Mandatory)][object]$Result, [Parameter(Mandatory)][object]$Checkpoint)
  if ([string]$Result.status -ne 'done') { return $false }
  if ($Result.PSObject.Properties.Name -contains 'runInstanceId' -and [string]$Result.runInstanceId) {
    return [string]$Result.runInstanceId -eq [string]$Checkpoint.runInstanceId -and
      [string]$Result.leaseEpoch -eq [string]$Checkpoint.leaseEpoch -and
      (!$Result.controlPlaneFingerprint -or [string]$Result.controlPlaneFingerprint -eq [string]$Checkpoint.controlPlaneFingerprint)
  }
  return $true
}

function Get-AutopilotRecoveryDecision {
  param(
    [Parameter(Mandatory)][string]$AutoDir,
    [string[]]$PermittedBaseAdvancePaths = @(),
    [object]$CurrentRunLock = $null
  )
  $lockPath = Join-Path $AutoDir 'run.lock'
  $statePath = Join-Path $AutoDir 'state.json'
  $lock = $null
  if (Test-Path -LiteralPath $lockPath) {
    try { $lock = Get-Content -LiteralPath $lockPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { return [pscustomobject]@{ action = 'QUARANTINE'; reason = 'run.lock is unreadable' } }
  }
  $process = if ($lock.pid) { Get-Process -Id ([int]$lock.pid) -ErrorAction SilentlyContinue } else { $null }
  $ownedByCurrentRun = $CurrentRunLock -and [string]$lock.runInstanceId -eq [string]$CurrentRunLock.runInstanceId -and [string]$lock.leaseEpoch -eq [string]$CurrentRunLock.leaseEpoch
  if ($process -and !$ownedByCurrentRun) { return [pscustomobject]@{ action = 'REFUSE_SECOND_INSTANCE'; reason = 'owner PID is alive'; runId = $lock.runId } }
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
      $actualBranch = (Invoke-AutopilotGit -RepoRoot $checkpoint.worktree -Arguments @('branch','--show-current') -ThrowOnFailure).stdout.Trim()
      if ($actualBranch -ne [string]$checkpoint.branch) { throw 'Issue worktree branch no longer matches checkpoint' }
      $mainHead = (Invoke-AutopilotGit -RepoRoot $repoRoot -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
      $worktreeHead = (Invoke-AutopilotGit -RepoRoot $checkpoint.worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
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
        $ancestor = Invoke-AutopilotGit -RepoRoot $repoRoot -Arguments @('merge-base','--is-ancestor',$worktreeHead,$mainHead) -AcceptedExitCodes @(0,1)
        $closeoutAlreadyMerged = $ancestor.exitCode -eq 0
        if ($closeoutAlreadyMerged) {
          $mainNormalizedHash = Get-AutopilotReadyContractHash -ReadyPath $readyPath -IssueId $checkpoint.issueId -NormalizeDoneStatus
          if ($mainNormalizedHash -ne [string]$checkpoint.readyContentHash) { throw 'merged Ready contract no longer matches the dispatch contract' }
        } elseif ($mainHead -ne [string]$checkpoint.baseCommit) { throw 'base branch advanced before the durable closeout commit was merged' }
      } else {
        $readyHash = Get-AutopilotReadyContractHash -ReadyPath $readyPath -IssueId $checkpoint.issueId
        if ($readyHash -ne [string]$checkpoint.readyContentHash) { throw 'Ready contract hash changed after Issue dispatch' }
        if ($mainHead -ne [string]$checkpoint.baseCommit) {
          $oldBase = [string]$checkpoint.baseCommit
          $baseAncestor = Invoke-AutopilotGit -RepoRoot $repoRoot -Arguments @('merge-base','--is-ancestor',$oldBase,$mainHead) -AcceptedExitCodes @(0,1)
          if ($baseAncestor.exitCode -ne 0) { throw 'base branch diverged after Issue dispatch' }
          if ($worktreeHead -ne $oldBase) { throw 'Issue worktree HEAD no longer matches its dispatched base' }
          $permitted = @($PermittedBaseAdvancePaths | ForEach-Object { ([string]$_).Replace('\','/').Trim() } | Where-Object { $_ } | Sort-Object -Unique)
          $baseAdvanceResult = Invoke-AutopilotGit -RepoRoot $repoRoot -Arguments @('diff','--name-only',$oldBase,$mainHead,'--') -ThrowOnFailure
          $baseAdvancePaths = @(Get-AutopilotNativeOutputLines $baseAdvanceResult.stdout | ForEach-Object { ([string]$_).Replace('\','/').Trim() } | Where-Object { $_ })
          if ($baseAdvancePaths.Count -eq 0) { throw 'cannot prove the base branch advance' }
          $unexpectedBasePaths = @($baseAdvancePaths | Where-Object { $_ -notin $permitted })
          if ($unexpectedBasePaths.Count -gt 0) { throw "base branch advanced outside permitted control-plane paths: $($unexpectedBasePaths -join ', ')" }
          $issueResult = Invoke-AutopilotGit -RepoRoot $checkpoint.worktree -Arguments @('diff','--name-only',$oldBase,'--') -ThrowOnFailure
          $issuePaths = @(Get-AutopilotNativeOutputLines $issueResult.stdout | ForEach-Object { ([string]$_).Replace('\','/').Trim() } | Where-Object { $_ })
          $overlap = @($issuePaths | Where-Object { $_ -in $baseAdvancePaths })
          if ($overlap.Count -gt 0) { throw "control-plane base advance overlaps the Issue diff: $($overlap -join ', ')" }
          $oldDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $checkpoint.worktree -BaseCommit $oldBase
          if ($checkpoint.evidence.diffHash -and $oldDiffHash -ne [string]$checkpoint.evidence.diffHash) { throw 'Issue diff hash no longer matches checkpoint before base advance' }
          if (Get-Command Assert-CurrentControlPlaneFence -ErrorAction SilentlyContinue) { Assert-CurrentControlPlaneFence | Out-Null }
          Invoke-AutopilotGit -RepoRoot $checkpoint.worktree -Arguments @('merge','--ff-only',$mainHead) -ThrowOnFailure | Out-Null
          $newDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $checkpoint.worktree -BaseCommit $mainHead
          $checkpoint = Move-AutopilotIssueCheckpointBaseForward -Path $checkpointPath -BaseCommit $mainHead -DiffHash $newDiffHash
          $worktreeHead = $mainHead
        }
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
      $category = Get-AutopilotRecoveryFailureCategory -Message $_.Exception.Message
      if ($category -ne 'integrity_conflict') {
        return [pscustomobject]@{ action=$(if ($category -eq 'environment_prereq') { 'PAUSE_RECOVERY_ENVIRONMENT' } else { 'PAUSE_RECOVERY_TOOL_CONFIG' }); reason=$_.Exception.Message; failureCategory=$category; runId=$lock.runId; worktree=$checkpoint.worktree; checkpointPath=$checkpointPath; issueId=$checkpoint.issueId }
      }
      try { Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase QUARANTINED -QuarantineReason $_.Exception.Message | Out-Null } catch {}
      return [pscustomobject]@{ action='QUARANTINE'; reason=$_.Exception.Message; failureCategory='integrity_conflict'; runId=$lock.runId; worktree=$checkpoint.worktree; checkpointPath=$checkpointPath; issueId=$checkpoint.issueId }
    }
    $action = if ($closeoutAlreadyMerged -and [string]$checkpoint.phase -ne 'CLOSED') { 'RESUME_FINALIZE' } else { switch ([string]$checkpoint.phase) {
      'IMPLEMENTING' {
        $implementationResultPath = [string](Get-AutopilotCheckpointProperty $checkpoint.artifacts 'resultPath' '')
        $implementationDone = $false
        if ($implementationResultPath -and (Test-Path -LiteralPath $implementationResultPath -PathType Leaf)) {
          try { $implementationDone = Test-AutopilotBoundChildResult -Result (Get-Content -LiteralPath $implementationResultPath -Raw -Encoding UTF8 | ConvertFrom-Json) -Checkpoint $checkpoint } catch { $implementationDone = $false }
        }
        if ($implementationDone) { 'RESUME_VALIDATION' } else { 'VERIFY_UNCOMMITTED' }
      }
      'IMPLEMENTED' { if ([int](Get-AutopilotCheckpointProperty $checkpoint.metrics 'environmentRetryCount' 0) -ge 2) { 'PAUSE_ENVIRONMENT_RETRY_EXHAUSTED' } else { 'RESUME_VALIDATION' } }
      'VALIDATING' { 'RESUME_VALIDATION' }
      'VALIDATED' { 'RESUME_REVIEW' }
      'REVIEWING' { 'RESUME_REVIEW' }
      'REVIEW_TOOL_BLOCKED' { if ($boundManualReviewPass) { 'RESUME_REVIEW' } elseif ([int]$checkpoint.metrics.reviewDispatchCount -ge 2) { 'PAUSE_REVIEW_TOOL_BLOCKED' } else { 'RESUME_REVIEW' } }
      'REVIEWED' { 'RESUME_CLOSEOUT' }
      'REPAIRING' {
        $repairResultPath = [string](Get-AutopilotCheckpointProperty $checkpoint.artifacts 'resultPath' '')
        $repairDone = $false
        if ($repairResultPath -and (Test-Path -LiteralPath $repairResultPath -PathType Leaf)) {
          try { $repairDone = Test-AutopilotBoundChildResult -Result (Get-Content -LiteralPath $repairResultPath -Raw -Encoding UTF8 | ConvertFrom-Json) -Checkpoint $checkpoint } catch { $repairDone = $false }
        }
        if ($repairDone) { 'RESUME_VALIDATION' } else { 'VERIFY_UNCOMMITTED' }
      }
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
    $changes = @(Get-AutopilotNativeOutputLines (Invoke-AutopilotGit -RepoRoot $state.worktree -Arguments @('status','--porcelain=v1') -ThrowOnFailure).stdout)
    if ($changes.Count -eq 0) {
      $repoRoot = Split-Path -Parent $AutoDir
      $mainHead = (Invoke-AutopilotGit -RepoRoot $repoRoot -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
      $worktreeHead = (Invoke-AutopilotGit -RepoRoot $state.worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
      if ($mainHead -and $worktreeHead -and $mainHead -ne $worktreeHead) {
        $unmergedAncestor = Invoke-AutopilotGit -RepoRoot $repoRoot -Arguments @('merge-base','--is-ancestor',$mainHead,$worktreeHead) -AcceptedExitCodes @(0,1)
        if ($unmergedAncestor.exitCode -eq 0) { return [pscustomobject]@{ action = 'QUARANTINE'; reason = 'unmerged issue commit lacks a durable Issue checkpoint; worktree preserved for manual evidence recovery'; runId = $lock.runId; worktree = $state.worktree; commit = $worktreeHead } }
      }
    }
  }
  if ($state -and $state.lastCommit -and $state.status -in @('CLOSING','COMMITTING','MERGING')) { return [pscustomobject]@{ action = 'QUARANTINE'; reason = 'closeout commit exists without a safely recoverable worktree'; runId = $lock.runId } }
  if ($state -and $state.worktree -and (Test-Path -LiteralPath $state.worktree)) {
    $changes = @(Get-AutopilotNativeOutputLines (Invoke-AutopilotGit -RepoRoot $state.worktree -Arguments @('status','--porcelain=v1') -ThrowOnFailure).stdout)
    if ($changes.Count -gt 0) { return [pscustomobject]@{ action = 'VERIFY_UNCOMMITTED'; reason = 'dead executor left a diff'; runId = $lock.runId; worktree = $state.worktree } }
  }
  if (!$lock -or $ownedByCurrentRun) { return [pscustomobject]@{ action = 'NEW_RUN'; reason = 'owned run.lock has no recoverable residual worktree' } }
  return [pscustomobject]@{ action = 'RESUME_FROM_CHECKPOINT'; reason = 'owner PID is gone and no unsafe diff was found'; runId = $lock.runId }
}

function Remove-AutopilotResidualWorktree {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$Worktree)
  $repoFull = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\')
  $worktreeFull = [IO.Path]::GetFullPath($Worktree).TrimEnd('\')
  if (!$worktreeFull.StartsWith($repoFull + '\.worktrees\', [StringComparison]::OrdinalIgnoreCase)) { throw 'residual worktree is outside the repository isolation root' }
  $branch = (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('branch','--show-current') -ThrowOnFailure).stdout.Trim()
  Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('-c','core.longpaths=true','worktree','remove','--force',$Worktree) -ThrowOnFailure | Out-Null
  if (Test-Path -LiteralPath $worktreeFull) {
    $deletePath = if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '\\?\' + $worktreeFull } else { $worktreeFull }
    Remove-Item -LiteralPath $deletePath -Recurse -Force
  }
  if (Test-Path -LiteralPath $worktreeFull) { throw 'residual issue worktree directory still exists after removal' }
  if ($branch) { Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('branch','-D',$branch) -AcceptedExitCodes @(0,1) | Out-Null }
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
