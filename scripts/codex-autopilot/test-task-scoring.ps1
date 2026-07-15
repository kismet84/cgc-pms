param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-task-score.ps1')

if ((Get-AutopilotEffectiveTaskAttempt -InvocationAttempt 0 -RepairDispatchCount 0) -ne 0) { throw 'clean first attempt was not preserved' }
if ((Get-AutopilotEffectiveTaskAttempt -InvocationAttempt 0 -RepairDispatchCount 2) -ne 2) { throw 'recovery entry erased durable repair attempts' }
if ((Get-AutopilotEffectiveTaskAttempt -InvocationAttempt 1 -RepairDispatchCount 0) -ne 1) { throw 'explicit repair attempt was not preserved' }

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-task-score-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $evidence = [ordered]@{
    issueId = 'ISSUE-TEST-001'
    implementationCommit = ('a' * 40)
    scoringVersion = 'autopilot-task-score/v1'
    hardGatesPassed = $true
    acceptanceCriteriaCovered = $true
    targetValidationPassed = $true
    scopeConsistent = $true
    discoveriesDispositionComplete = $true
    followupGovernanceComplete = $true
    attempt = 0
    cycleEvidenceComplete = $true
    avoidableReworkCount = 0
    stockIssueTarget = $true
    stockIssueClosed = $true
    sameRootIssueAdded = $false
    followupNetChange = 0
    sourceRefs = @('docs/quality/issue-test-001.md','docs/iterations/issue-test-001.md')
  }
  $score = New-AutopilotTaskScore $evidence
  if ($score.total -ne 100 -or $score.hardGatesPassed -ne $true) { throw 'full evidence did not produce 100 points' }
  $again = New-AutopilotTaskScore $evidence
  if ($again.key -ne $score.key -or $again.total -ne $score.total) { throw 'task scoring is not deterministic' }

  $evidence.attempt = 1
  $evidence.avoidableReworkCount = 1
  $evidence.stockIssueTarget = $false
  $evidence.stockIssueClosed = $false
  $repairScore = New-AutopilotTaskScore $evidence
  if ($repairScore.total -ne 80) { throw "expected repaired non-stock score=80, actual=$($repairScore.total)" }

  $missingRejected = $false
  $missing = [ordered]@{} + $evidence
  $missing.Remove('followupGovernanceComplete')
  try { New-AutopilotTaskScore $missing | Out-Null } catch { $missingRejected = $_.Exception.Message -match 'missing required property' }
  if (!$missingRejected) { throw 'missing formal evidence was scored' }

  $hardGateRejected = $false
  $evidence.hardGatesPassed = $false
  try { New-AutopilotTaskScore $evidence | Out-Null } catch { $hardGateRejected = $_.Exception.Message -match 'hard gates' }
  if (!$hardGateRejected) { throw 'failed hard gates were scored' }
  $evidence.hardGatesPassed = $true

  if (Test-AutopilotTaskScoringActive ([pscustomobject]@{ enabled=$false; activeVersion=$null; approvalStatus='NEEDS_CONFIRMATION' })) { throw 'candidate scoring became active without approval' }
  $unapprovedRejected = $false
  try { Test-AutopilotTaskScoringActive ([pscustomobject]@{ enabled=$true; activeVersion='autopilot-task-score/v1'; approvalStatus='NEEDS_CONFIRMATION' }) | Out-Null } catch { $unapprovedRejected = $true }
  if (!$unapprovedRejected) { throw 'unapproved active scoring config was accepted' }
  if (Test-AutopilotRetrospectiveActive -TaskScoringConfig ([pscustomobject]@{enabled=$false;activeVersion=$null;approvalStatus='NEEDS_CONFIRMATION'}) -RetrospectiveConfig ([pscustomobject]@{enabled=$false;threshold=20})) { throw 'disabled retrospective candidate was activated' }
  $approved = [pscustomobject]@{enabled=$true;activeVersion='autopilot-task-score/v1';approvalStatus='APPROVED'}
  $coherenceRejected = $false
  try { Test-AutopilotRetrospectiveActive -TaskScoringConfig $approved -RetrospectiveConfig ([pscustomobject]@{enabled=$false;threshold=20}) | Out-Null } catch { $coherenceRejected = $true }
  if (!$coherenceRejected) { throw 'active scoring without retrospective activation was not rejected' }
  if (!(Test-AutopilotRetrospectiveActive -TaskScoringConfig $approved -RetrospectiveConfig ([pscustomobject]@{enabled=$true;threshold=20}))) { throw 'approved scoring and retrospective config did not activate' }
  $candidateActivationRejected = $false
  try { Test-AutopilotTaskScoringActive ([pscustomobject]@{enabled=$true;activeVersion='autopilot-task-score/v2-candidate';approvalStatus='APPROVED'}) | Out-Null } catch { $candidateActivationRejected = $true }
  if (!$candidateActivationRejected) { throw 'disabled v2 candidate became an active scoring version' }
  if (!(Test-AutopilotTaskScoringActive ([pscustomobject]@{enabled=$true;activeVersion='autopilot-task-score/v2';approvalStatus='APPROVED'}))) { throw 'approved v2 scoring did not activate' }

  $report = Join-Path $root 'iteration.md'
  '# Iteration' | Set-Content -LiteralPath $report -Encoding UTF8
  if (!(Add-AutopilotTaskScoreToReport -ReportPath $report -Score $score)) { throw 'score was not appended' }
  if (Add-AutopilotTaskScoreToReport -ReportPath $report -Score $score) { throw 'duplicate score was appended' }
  if ((Get-Content -LiteralPath $report -Raw -Encoding UTF8) -notmatch [regex]::Escape($score.key)) { throw 'score key missing from iteration report' }

  $v2Evidence = [ordered]@{} + $evidence
  $v2Evidence.implementationDispatchCount = 1
  $v2Evidence.validationDispatchCount = 1
  $v2Evidence.reviewDispatchCount = 0
  $v2Evidence.repairDispatchCount = 0
  $v2Evidence.closeoutDispatchCount = 1
  $v2Evidence.reviewRequired = $false
  $v2Evidence.runResumeCount = 0
  $v2Evidence.phaseRestartCount = 0
  $v2Evidence.manualRecoveryCount = 0
  $v2Evidence.toolConfigBlockCount = 0
  $v2Evidence.environmentRetryCount = 0
  $v2Evidence.duplicateDispatchBlockedCount = 0
  $v2Evidence.executionEvidenceComplete = $true
  $v2Evidence.wallClockSeconds = 120
  $v2Evidence.phaseDurationsSeconds = [pscustomobject]@{IMPLEMENTING=40;VALIDATING=20;CLOSING=10;RECOVERY_WAIT=50}
  $v2Evidence.semanticProgressAt = '2026-07-14T12:00:00+08:00'
  $v2 = New-AutopilotTaskScoreV2Shadow $v2Evidence
  if ($v2.shadow -ne $true -or $v2.dimensions.taskExecutionEfficiency.score -ne 10 -or $v2.total -ne 85) { throw 'v2 shadow did not award deterministic full task execution efficiency' }
  if ($v2.executionTiming.businessPhaseSeconds -ne 70 -or $v2.executionTiming.controlPlaneSeconds -ne 50 -or !$v2.executionTiming.livenessSignalsExcluded) { throw 'v2 timing evidence did not separate business work from control-plane overhead' }
  $v2Formal = New-AutopilotTaskScoreV2 $v2Evidence
  if ($v2Formal.shadow -ne $false -or $v2Formal.scoringVersion -ne 'autopilot-task-score/v2' -or $v2Formal.dimensions.taskExecutionEfficiency.score -ne 10 -or $v2Formal.total -ne 85 -or $v2Formal.key -eq $v2.key) { throw 'formal v2 score did not use its approved version and efficiency dimension' }
  $v2Evidence.toolConfigBlockCount = 1
  $v2Evidence.runResumeCount = 1
  $v2Retry = New-AutopilotTaskScoreV2Shadow $v2Evidence
  if ($v2Retry.dimensions.taskExecutionEfficiency.score -ne 5) { throw 'one classified tool retry did not produce 5 task execution efficiency points' }
  $v2Evidence.toolConfigBlockCount = 0
  $v2Evidence.environmentRetryCount = 1
  $v2Evidence.validationDispatchCount = 2
  $v2EnvironmentRetry = New-AutopilotTaskScoreV2Shadow $v2Evidence
  if ($v2EnvironmentRetry.dimensions.taskExecutionEfficiency.score -ne 5) { throw 'one classified environment retry did not produce 5 task execution efficiency points' }
  $v2Evidence.implementationDispatchCount = 2
  $v2Repeated = New-AutopilotTaskScoreV2Shadow $v2Evidence
  if ($v2Repeated.dimensions.taskExecutionEfficiency.score -ne 0) { throw 'repeated implementation dispatch was not scored as zero efficiency' }
  if (!(Add-AutopilotTaskScoreV2ShadowToReport -ReportPath $report -Score $v2)) { throw 'v2 shadow was not appended' }
  if (Add-AutopilotTaskScoreV2ShadowToReport -ReportPath $report -Score $v2) { throw 'duplicate v2 shadow was appended' }

  $formalDir = Join-Path $root 'docs\quality'
  New-Item -ItemType Directory -Path $formalDir -Force | Out-Null
  $formalReport = Join-Path $formalDir 'issue-test-002.md'
  "本轮新增后续项：0`r`n本轮关闭后续项：1`r`n本轮后续项净变化：-1" | Set-Content -LiteralPath $formalReport -Encoding UTF8
  $formalResult = [pscustomobject]@{ issueId='ISSUE-TEST-002';status='done';attempt=0;firstPassSuccess=$true;scopeViolationCount=0;validation=@([pscustomobject]@{status='pass'});evidencePaths=@('evidence/test.json','.codex-autopilot/runs/transient/evidence.json');reviewRequired=$false;validationDispatchCount=2;reviewDispatchCount=0;repairDispatchCount=1;closeoutDispatchCount=1;runResumeCount=1;environmentRetryCount=1 }
  $formalEvidence = New-AutopilotTaskScoreEvidenceFromResult -Result $formalResult -ReportPath $formalReport -ImplementationCommit ('b' * 40) -StockIssueTarget $true
  if (@($formalEvidence.sourceRefs | Where-Object { [IO.Path]::IsPathRooted([string]$_) -or $_ -match '\.worktrees' }).Count -gt 0) { throw 'formal score sourceRefs leaked an absolute or worktree-local path' }
  if (@($formalEvidence.sourceRefs) -notcontains 'docs/quality/issue-test-002.md') { throw 'formal report sourceRef was not normalized to a repository path' }
  if (@($formalEvidence.sourceRefs | Where-Object { $_ -match '\.codex-autopilot' }).Count -gt 0) { throw 'formal score sourceRefs retained a transient run artifact path' }
  $formalResult | Add-Member implementationDispatchCount 2
  $formalResult | Add-Member phaseRestartCount 1
  $formalResult | Add-Member manualRecoveryCount 1
  $formalResult | Add-Member toolConfigBlockCount 1
  $formalResult | Add-Member duplicateDispatchBlockedCount 1
  $formalV2Evidence = New-AutopilotTaskScoreV2EvidenceFromResult -Result $formalResult -ReportPath $formalReport -ImplementationCommit ('b' * 40) -StockIssueTarget $true
  if ((New-AutopilotTaskScoreV2Shadow $formalV2Evidence).dimensions.taskExecutionEfficiency.score -ne 0) { throw 'ISSUE-040-022 style cross-run rework did not produce zero efficiency' }

  Write-Host 'task scoring self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
