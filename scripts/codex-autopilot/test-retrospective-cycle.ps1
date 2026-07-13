param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-state.ps1')
. (Join-Path $scriptDir 'autopilot-task-score.ps1')
. (Join-Path $scriptDir 'autopilot-retrospective.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-retrospective-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  function New-State([string]$Path) {
    $now = [datetimeoffset]::Now.ToString('o')
    $state = [ordered]@{schemaVersion=2;runId='run-review';status='CHECKPOINT';phase='checkpoint';currentIssue='';attempt=0;startedAt=$now;phaseStartedAt=$now;lastHeartbeatAt=$now;iterationLimit=$null;completedImplementationIssues=0;completedIssueIds=@();worktree='';branch='test';executorPid=$null;lastCommit=$null;failureFingerprint=$null}
    Write-AutopilotStateAtomic -Path $Path -State $state | Out-Null
    $migrated = Read-AutopilotState -Path $Path
    Set-AutopilotProperty $migrated 'activeScoringVersion' 'autopilot-task-score/v1'
    Write-AutopilotStateAtomic -Path $Path -State $migrated | Out-Null
  }

  $unboundedPath = Join-Path $root 'unbounded.json'; New-State $unboundedPath
  for ($i=1; $i -le 20; $i++) {
    $key = Get-AutopilotTaskScoreKey -IssueId "ISSUE-$i" -ImplementationCommit (($i.ToString('x').PadLeft(40,'0'))) -ScoringVersion 'autopilot-task-score/v1'
    Add-AutopilotReviewCycleIssue -Path $unboundedPath -IssueId "ISSUE-$i" -ScoreKey $key -ScoringVersion 'autopilot-task-score/v1' -Threshold 20 | Out-Null
  }
  $unbounded = Read-AutopilotState $unboundedPath
  if (!$unbounded.retrospectiveDue -or $unbounded.status -ne 'RETROSPECTIVE_REQUIRED' -or $unbounded.reviewCycleCompletedCount -ne 20) { throw 'unbounded threshold did not stop before task 21' }
  $duplicateKey = $unbounded.reviewCycleScoreKeys[19]
  Add-AutopilotReviewCycleIssue -Path $unboundedPath -IssueId 'ISSUE-20' -ScoreKey $duplicateKey -ScoringVersion 'autopilot-task-score/v1' -Threshold 20 | Out-Null
  if ((Read-AutopilotState $unboundedPath).reviewCycleCompletedCount -ne 20) { throw 'duplicate closeout incremented the cycle' }

  $mixedStatePath = Join-Path $root 'mixed-state.json'; New-State $mixedStatePath
  $v1Key = Get-AutopilotTaskScoreKey -IssueId 'MIXED-V1' -ImplementationCommit ('1'.PadLeft(40,'0')) -ScoringVersion 'autopilot-task-score/v1'
  Add-AutopilotReviewCycleIssue -Path $mixedStatePath -IssueId 'MIXED-V1' -ScoreKey $v1Key -ScoringVersion 'autopilot-task-score/v1' -Threshold 20 | Out-Null
  $mixedState = Read-AutopilotState $mixedStatePath; Set-AutopilotProperty $mixedState 'activeScoringVersion' 'autopilot-task-score/v2'; Write-AutopilotStateAtomic -Path $mixedStatePath -State $mixedState | Out-Null
  $v2Key = Get-AutopilotTaskScoreKey -IssueId 'MIXED-V2' -ImplementationCommit ('2'.PadLeft(40,'0')) -ScoringVersion 'autopilot-task-score/v2'
  Add-AutopilotReviewCycleIssue -Path $mixedStatePath -IssueId 'MIXED-V2' -ScoreKey $v2Key -ScoringVersion 'autopilot-task-score/v2' -Threshold 20 | Out-Null
  if ((Read-AutopilotState $mixedStatePath).reviewCycleCompletedCount -ne 2) { throw 'v2 activation discarded or blocked the existing v1 review-cycle task' }

  $boundedPath = Join-Path $root 'bounded.json'; New-State $boundedPath
  $bounded = Read-AutopilotState $boundedPath; Set-AutopilotProperty $bounded 'iterationLimit' 3; Set-AutopilotProperty $bounded 'remainingIterations' 3; Write-AutopilotStateAtomic -Path $boundedPath -State $bounded | Out-Null
  for ($i=1; $i -le 21; $i++) {
    $remaining = if ($i -le 18) { 3 } else { 21 - $i }
    $key = Get-AutopilotTaskScoreKey -IssueId "BOUND-$i" -ImplementationCommit ((($i+100).ToString('x').PadLeft(40,'0'))) -ScoringVersion 'autopilot-task-score/v1'
    Add-AutopilotReviewCycleIssue -Path $boundedPath -IssueId "BOUND-$i" -ScoreKey $key -ScoringVersion 'autopilot-task-score/v1' -Threshold 20 -BoundedBatchRemaining $remaining | Out-Null
    if ($i -eq 20) {
      $atThreshold = Read-AutopilotState $boundedPath
      if (!$atThreshold.retrospectiveDue -or $atThreshold.status -eq 'RETROSPECTIVE_REQUIRED' -or $atThreshold.remainingIterations -ne 1) { throw 'bounded batch stopped immediately at task 20 instead of finishing N' }
    }
  }
  $bounded = Read-AutopilotState $boundedPath
  if ($bounded.reviewCycleCompletedCount -ne 21 -or !$bounded.retrospectiveDue -or $bounded.status -ne 'RETROSPECTIVE_REQUIRED') { throw 'bounded 18+3 cycle did not retain all 21 tasks' }
  $checkpointRoot = Join-Path $root 'checkpoint-repo'; New-Item -ItemType Directory -Path (Join-Path $checkpointRoot '.codex-autopilot') -Force | Out-Null
  & git -C $checkpointRoot init -q
  Copy-Item -LiteralPath $boundedPath -Destination (Join-Path $checkpointRoot '.codex-autopilot\state.json')
  'enabled' | Set-Content -LiteralPath (Join-Path $checkpointRoot '.codex-autopilot\enabled.flag') -Encoding UTF8
  $checkpointScript = Join-Path (Split-Path -Parent (Split-Path -Parent $scriptDir)) 'plugins\cgc-pms-autopilot\scripts\autopilot-checkpoint.ps1'
  $checkpoint = & pwsh -NoProfile -ExecutionPolicy Bypass -File $checkpointScript -RepoRoot $checkpointRoot -AsJson | ConvertFrom-Json
  if ($checkpoint.decision -ne 'retrospective_required' -or $checkpoint.reviewCycleCompletedCount -ne 21) { throw 'checkpoint did not block the next batch for retrospective' }
  $earlyResetRejected = $false
  try { Reset-AutopilotReviewCycle -Path $boundedPath | Out-Null } catch { $earlyResetRejected = $true }
  if (!$earlyResetRejected) { throw 'review cycle reset before recovery phases completed' }
  Set-AutopilotRetrospectivePhase -Path $boundedPath -Phase REPORT_COMMITTED -Evidence @{retrospectiveReportCommit=('a'*40);lastRetrospectiveReport='docs/iterations/review.md'} | Out-Null
  Set-AutopilotRetrospectivePhase -Path $boundedPath -Phase ISSUES_WRITTEN -Evidence @{retrospectiveFactsCommit=('b'*40)} | Out-Null
  Set-AutopilotRetrospectivePhase -Path $boundedPath -Phase GRAPH_REFRESHED -Evidence @{retrospectiveGraphGitCursor=('b'*40)} | Out-Null
  $cycleId = (Read-AutopilotState $boundedPath).reviewCycleId
  $episodeId = Get-AutopilotRetrospectiveEpisodeId -ReviewCycleId $cycleId -ScoringVersion 'autopilot-task-score/v1'
  Set-AutopilotRetrospectivePhase -Path $boundedPath -Phase EPISODE_RECORDED -Evidence @{retrospectiveEpisodeId=$episodeId} | Out-Null
  $reset = Reset-AutopilotReviewCycle -Path $boundedPath
  if ($reset.reviewCycleCompletedCount -ne 0 -or $reset.status -ne 'PAUSED' -or $reset.retrospectiveDue) { throw 'successful retrospective did not clear and remain paused' }

  $score = [pscustomobject]@{scoringVersion='autopilot-task-score/v1';total=70;dimensions=[pscustomobject]@{deliveryCorrectness=[pscustomobject]@{score=30;max=35};zeroDanglingIssues=[pscustomobject]@{score=20;max=25};firstPassAcceptance=[pscustomobject]@{score=10;max=20};cycleEfficiency=[pscustomobject]@{score=5;max=10};stockIssueReduction=[pscustomobject]@{score=5;max=10}}}
  $records = @(1..20 | ForEach-Object { [pscustomobject]@{issueId="R-$_";score=$score;attempt=1;followupNetChange=0;cycleSeconds=120;rootCause=$(if($_ -le 3){'ready-config'}else{$null});stockIssueTarget=$false} })
  $review = New-AutopilotRetrospective -ReviewCycleId 'review-sample' -TaskRecords $records -ScoringVersion 'autopilot-task-score/v1'
  if ($review.taskCount -ne 20 -or $review.firstPassRate -ne 0 -or @($review.proposals | Where-Object rule -eq 'REPEATED_ROOT_CAUSE').Count -ne 1) { throw 'retrospective aggregate rules are incorrect' }
  $v2Score = [pscustomobject]@{scoringVersion='autopilot-task-score/v2';total=75;dimensions=[pscustomobject]@{deliveryCorrectness=[pscustomobject]@{score=30;max=35};zeroDanglingIssues=[pscustomobject]@{score=20;max=25};firstPassAcceptance=[pscustomobject]@{score=10;max=20};taskExecutionEfficiency=[pscustomobject]@{score=10;max=10};stockIssueReduction=[pscustomobject]@{score=5;max=10}}}
  $mixedReview = New-AutopilotRetrospective -ReviewCycleId 'review-mixed' -TaskRecords @($records[0],[pscustomobject]@{issueId='R-V2';score=$v2Score;attempt=0;followupNetChange=0;cycleSeconds=100;rootCause=$null;stockIssueTarget=$false}) -ScoringVersion 'autopilot-task-score/v2'
  if (@($mixedReview.scoringVersions).Count -ne 2 -or $mixedReview.dimensions.cycleEfficiency.taskCount -ne 1 -or $mixedReview.dimensions.taskExecutionEfficiency.taskCount -ne 1) { throw 'mixed v1/v2 retrospective did not aggregate efficiency dimensions by scoring version' }
  if ((Get-AutopilotRetrospectiveEpisodeId 'review-sample' 'autopilot-task-score/v1') -ne (Get-AutopilotRetrospectiveEpisodeId 'review-sample' 'autopilot-task-score/v1')) { throw 'Episode id is not stable' }

  $registryPath = Join-Path $root 'issues.json'; '{"schemaVersion":1,"issues":[]}' | Set-Content -LiteralPath $registryPath -Encoding UTF8
  $merge1 = Merge-AutopilotImprovementProposals -RegistryPath $registryPath -Proposals $review.proposals -ReportPath 'docs/iterations/review.md'
  $merge2 = Merge-AutopilotImprovementProposals -RegistryPath $registryPath -Proposals $review.proposals -ReportPath 'docs/iterations/review.md'
  if ($merge1.added -lt 1 -or $merge2.added -ne 0 -or $merge2.merged -ne @($review.proposals).Count) { throw 'proposal registry merge is not idempotent' }

  Write-Host 'retrospective cycle self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
