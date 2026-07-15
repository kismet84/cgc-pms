$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$checkpointScript = Join-Path $repoRoot 'plugins\cgc-pms-autopilot\scripts\autopilot-checkpoint.ps1'
$temp = Join-Path ([IO.Path]::GetTempPath()) ('cgc-pms-desktop-checkpoint-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $temp '.codex-autopilot\checkpoints') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $temp 'scripts\codex-autopilot') -Force | Out-Null
try {
  & git -C $temp init --quiet
  & git -C $temp config core.autocrlf false
  & git -C $temp config core.eol lf
  [IO.File]::WriteAllText((Join-Path $temp 'scripts\codex-autopilot\codex-autopilot.config.json'), '{"executionHost":"desktop-native","controlPlaneCanary":{"enabled":false}}', [Text.UTF8Encoding]::new($false))
  $checkpointPath = Join-Path $temp '.codex-autopilot\checkpoints\issue-046-001.json'
  $checkpoint = [ordered]@{ issueId='ISSUE-046-001'; phase='VALIDATED'; generation=7; readyContentHash=('a'*64); baseCommit=('b'*40); worktree=(Join-Path $temp 'worktree'); branch='codex/test' }
  [IO.File]::WriteAllText($checkpointPath, ($checkpoint | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
  $statePath = Join-Path $temp '.codex-autopilot\state.json'
  $state = [ordered]@{ status='VERIFYING'; phase='verifying'; currentIssue='ISSUE-046-001'; currentIssuePhase='VALIDATED'; issueCheckpointPath=$checkpointPath; stopReason=''; lastHeartbeatAt=[datetimeoffset]::Now.ToString('o'); reviewCycleCompletedCount=0; retrospectiveDue=$false; retrospectiveStatus='IDLE'; retrospectivePhase='NONE'; activeScoringVersion=$null; iterationLimit=1; remainingIterations=1 }
  [IO.File]::WriteAllText($statePath, ($state | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText((Join-Path $temp '.codex-autopilot\enabled.flag'), 'true', [Text.UTF8Encoding]::new($false))
  $beforeState = (Get-FileHash $statePath -Algorithm SHA256).Hash
  $beforeCheckpoint = (Get-FileHash $checkpointPath -Algorithm SHA256).Hash

  $result = (& pwsh -NoProfile -File $checkpointScript -RepoRoot $temp -AsJson | Out-String) | ConvertFrom-Json
  if ($result.executionHost -ne 'desktop-native' -or !$result.activeCheckpoint.present) { throw 'desktop checkpoint did not expose the active recovery fact' }
  if ($result.activeCheckpoint.phase -ne 'VALIDATED' -or $result.activeCheckpoint.generation -ne 7) { throw 'desktop checkpoint recovery facts changed' }
  if ((Get-FileHash $statePath -Algorithm SHA256).Hash -ne $beforeState -or (Get-FileHash $checkpointPath -Algorithm SHA256).Hash -ne $beforeCheckpoint) { throw 'read-only checkpoint mutated durable state' }

  & git -C $temp config user.email 'autopilot-fixture@example.invalid'
  & git -C $temp config user.name 'AutoPilot Fixture'
  $readyDir = Join-Path $temp 'docs\backlog'
  New-Item -ItemType Directory -Path $readyDir -Force | Out-Null
  $readyPath = Join-Path $readyDir 'ready-issues.md'
  [IO.File]::WriteAllText($readyPath, "# Ready Issues`n`n### ISSUE-046-002：desktop lifecycle fixture`n`n状态：Ready`n", [Text.UTF8Encoding]::new($false))
  & git -C $temp add -- 'docs/backlog/ready-issues.md'
  & git -C $temp commit --quiet -m 'fixture baseline'
  $baseCommit = (& git -C $temp rev-parse HEAD).Trim()
  $script:ExecutionHost = 'desktop-native'
  . (Join-Path $repoRoot 'scripts\codex-autopilot\autopilot-native-command.ps1')
  . (Join-Path $repoRoot 'scripts\codex-autopilot\autopilot-issue-checkpoint.ps1')
  . (Join-Path $repoRoot 'scripts\codex-autopilot\autopilot-transition.ps1')
  . (Join-Path $repoRoot 'scripts\codex-autopilot\autopilot-review.ps1')
  $lifecycle = New-AutopilotIssueCheckpoint -AutoDir (Join-Path $temp '.codex-autopilot') -IssueId 'ISSUE-046-002' -ReadyPath $readyPath -BaseCommit $baseCommit -Worktree $temp -Branch 'master' -AllowedPaths @('docs/**') -ForbiddenPaths @('.git/**') -ExecutionHost 'desktop-native'
  foreach ($phase in @('IMPLEMENTED','VALIDATING','VALIDATED','REVIEWING')) {
    $lifecycle = Move-AutopilotIssuePhase -Path (Join-Path $temp '.codex-autopilot\checkpoints\issue-046-002.json') -Phase $phase
  }
  $reviewDir = Join-Path $temp 'review'
  New-Item -ItemType Directory -Path $reviewDir -Force | Out-Null
  $diffPath = Join-Path $reviewDir 'diff.patch'
  [IO.File]::WriteAllText($diffPath, 'desktop fixture diff', [Text.UTF8Encoding]::new($false))
  $requestPath = Join-Path $reviewDir 'request.json'
  $request = New-AutopilotReviewRequest -IssueId 'ISSUE-046-002' -ReadyPath $readyPath -DiffPath $diffPath -EvidencePaths @() -OutputPath $requestPath
  $resultPath = Join-Path $reviewDir 'result.json'
  $reviewResult = [ordered]@{ schemaVersion=1; issueId='ISSUE-046-002'; decision='pass'; findings=@(); reviewedDiffHash=$request.diffSha256; reviewedAt=[datetimeoffset]::Now.ToString('o') }
  [IO.File]::WriteAllText($resultPath, ($reviewResult | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
  Import-AutopilotDesktopReviewResult -RequestPath $requestPath -ResultPath $resultPath | Out-Null
  foreach ($phase in @('REVIEWED','CLOSING','IMPLEMENTATION_COMMITTED','CLOSEOUT_COMMITTED','REGISTERED','CLOSED')) {
    $lifecycle = Move-AutopilotIssuePhase -Path (Join-Path $temp '.codex-autopilot\checkpoints\issue-046-002.json') -Phase $phase
  }
  if ($lifecycle.phase -ne 'CLOSED' -or $lifecycle.executionHost -ne 'desktop-native') { throw 'desktop lifecycle did not close with a durable desktop host fact' }
  Write-Host 'desktop checkpoint recovery self-test passed'
} finally {
  Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
