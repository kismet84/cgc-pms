param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-issue-checkpoint.ps1')
. (Join-Path $scriptDir 'autopilot-recover.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-phase-recovery-' + [guid]::NewGuid().ToString('N'))
$autoDir = Join-Path $root '.codex-autopilot'
New-Item -ItemType Directory -Path (Join-Path $root 'docs\backlog') -Force | Out-Null
try {
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  ".worktrees/`r`n.codex-autopilot/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  "# Ready Issues`r`n`r`n### ISSUE-040-022：phase recovery`r`n`r`n状态：Ready`r`n" | Set-Content -LiteralPath (Join-Path $root 'docs\backlog\ready-issues.md') -Encoding UTF8
  & git -C $root add .
  & git -C $root commit -qm 'base'
  $base = (& git -C $root rev-parse HEAD).Trim()
  $worktree = Join-Path $root '.worktrees\autopilot\issue-040-022'
  New-Item -ItemType Directory -Path (Split-Path -Parent $worktree) -Force | Out-Null
  & git -C $root worktree add -q -b codex/autopilot/issue-040-022 $worktree $base
  'implementation' | Set-Content -LiteralPath (Join-Path $worktree 'implementation.txt') -Encoding UTF8
  $issueDir = Join-Path $autoDir 'runs\old\ISSUE-040-022'
  New-Item -ItemType Directory -Path $issueDir -Force | Out-Null
  $resultPath = Join-Path $issueDir 'result.json'
  '{"status":"done"}' | Set-Content -LiteralPath $resultPath -Encoding UTF8
  $checkpoint = New-AutopilotIssueCheckpoint -AutoDir $autoDir -IssueId 'ISSUE-040-022' -ReadyPath (Join-Path $root 'docs\backlog\ready-issues.md') -BaseCommit $base -Worktree $worktree -Branch 'codex/autopilot/issue-040-022' -AllowedPaths @('implementation.txt') -ArtifactDirectory $issueDir
  $path = Get-AutopilotIssueCheckpointPath $autoDir 'ISSUE-040-022'
  $diffHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree -BaseCommit $base
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase IMPLEMENTING -IncrementDispatch implementation | Out-Null
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase IMPLEMENTED -Artifacts @{resultPath=$resultPath} -Evidence @{diffHash=$diffHash} | Out-Null
  [ordered]@{pid=999999;runId='dead';issueId='ISSUE-040-022'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $autoDir 'run.lock') -Encoding UTF8
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($decision.action -ne 'RESUME_VALIDATION') { throw "implemented checkpoint did not resume validation: $($decision | ConvertTo-Json -Compress)" }

  'control-plane' | Set-Content -LiteralPath (Join-Path $root 'autopilot-control.ps1') -Encoding UTF8
  & git -C $root add autopilot-control.ps1
  & git -C $root commit -qm 'fix control plane'
  $advancedBase = (& git -C $root rev-parse HEAD).Trim()
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir -PermittedBaseAdvancePaths @('autopilot-control.ps1')
  if ($decision.action -ne 'RESUME_VALIDATION') { throw "permitted disjoint base advance did not resume validation: $($decision | ConvertTo-Json -Compress)" }
  $forwardedCheckpoint = Read-AutopilotIssueCheckpoint $path
  if ($forwardedCheckpoint.baseCommit -ne $advancedBase -or [int]$forwardedCheckpoint.metrics.manualRecoveryCount -ne 1 -or $forwardedCheckpoint.phase -ne 'IMPLEMENTED') { throw 'permitted base advance did not rebind the durable checkpoint' }
  $base = $advancedBase
  $diffHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree -BaseCommit $base
  $duplicateBlocked = $false
  try { Set-AutopilotIssueCheckpointPhase -Path $path -Phase IMPLEMENTING -IncrementDispatch implementation | Out-Null } catch { $duplicateBlocked = $_.Exception.Message -match 'duplicate implementation dispatch' }
  if (!$duplicateBlocked) { throw 'duplicate implementation dispatch was not blocked' }
  if ((Read-AutopilotIssueCheckpoint $path).metrics.implementationDispatchCount -ne 1) { throw 'duplicate dispatch changed implementationDispatchCount' }
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase IMPLEMENTED -IncrementEnvironmentRetry | Out-Null
  $environmentDecision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($environmentDecision.action -ne 'RESUME_VALIDATION' -or [int]$environmentDecision.checkpoint.metrics.environmentRetryCount -ne 1) { throw 'single classified environment retry did not preserve implementation and resume validation' }

  Set-AutopilotIssueCheckpointPhase -Path $path -Phase REPAIRING -Artifacts @{resultPath=$resultPath} -IncrementDispatch repair | Out-Null
  $repairDecision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($repairDecision.action -ne 'RESUME_VALIDATION') { throw 'completed repair result did not resume validation' }
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase IMPLEMENTED | Out-Null

  Set-AutopilotIssueCheckpointPhase -Path $path -Phase VALIDATED -Evidence @{verificationDiffHash=$diffHash} -IncrementDispatch validation | Out-Null
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($decision.action -ne 'RESUME_REVIEW') { throw 'validated checkpoint did not resume Reviewer' }
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase REVIEWED -Evidence @{reviewDiffHash=$diffHash} -IncrementDispatch review | Out-Null
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($decision.action -ne 'RESUME_CLOSEOUT') { throw 'reviewed checkpoint did not resume closeout' }

  $reviewPath = Join-Path $issueDir 'review-result.json'
  [ordered]@{schemaVersion=1;issueId='ISSUE-040-022';decision='tool_blocked';findings=@();reviewedDiffHash=$diffHash;reviewedAt=[datetimeoffset]::Now.ToString('o')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reviewPath -Encoding UTF8
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase REVIEW_TOOL_BLOCKED -Artifacts @{reviewResultPath=$reviewPath} -Evidence @{diffHash=$diffHash} -IncrementDispatch review -IncrementToolConfigBlock | Out-Null
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($decision.action -ne 'PAUSE_REVIEW_TOOL_BLOCKED') { throw 'exhausted Reviewer tool retry was not paused' }
  [ordered]@{schemaVersion=1;issueId='ISSUE-040-022';decision='pass';findings=@();reviewedDiffHash=$diffHash;reviewedAt=[datetimeoffset]::Now.ToString('o')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reviewPath -Encoding UTF8
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($decision.action -ne 'RESUME_REVIEW') { throw 'bound manual Reviewer pass did not override the exhausted tool retry pause' }
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase REVIEWED -Evidence @{reviewDiffHash=$diffHash} | Out-Null

  $worktreeReadyPath = Join-Path $worktree 'docs\backlog\ready-issues.md'
  $doneReady = (Get-Content -LiteralPath $worktreeReadyPath -Raw -Encoding UTF8).Replace('状态：Ready','状态：Done')
  [IO.File]::WriteAllText($worktreeReadyPath, $doneReady, [Text.UTF8Encoding]::new($false))
  & git -C $worktree add .
  & git -C $worktree commit -qm 'chore(autopilot): score and close issue-040-022'
  $closeoutCommit = (& git -C $worktree rev-parse HEAD).Trim()
  $closeoutDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree -BaseCommit $base
  Set-AutopilotIssueCheckpointPhase -Path $path -Phase CLOSEOUT_COMMITTED -Evidence @{implementationCommit=$closeoutCommit;closeoutCommit=$closeoutCommit;diffHash=$closeoutDiffHash} -IncrementDispatch closeout | Out-Null
  & git -C $root merge --ff-only $closeoutCommit | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'post-merge recovery fixture could not fast-forward' }
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($decision.action -ne 'RESUME_FINALIZE') { throw "merged closeout checkpoint did not resume final registration: $($decision | ConvertTo-Json -Compress)" }

  Add-Content -LiteralPath (Join-Path $root 'docs\backlog\ready-issues.md') -Value "`r`nchanged"
  $decision = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($decision.action -ne 'QUARANTINE' -or $decision.reason -notmatch 'Ready') { throw "changed Ready contract was not quarantined: $($decision | ConvertTo-Json -Compress)" }
  if (!(Test-Path -LiteralPath $worktree)) { throw 'quarantine deleted the recoverable worktree' }
  Write-Host 'phase recovery self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
