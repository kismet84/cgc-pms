param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-task-score.ps1')

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

  $report = Join-Path $root 'iteration.md'
  '# Iteration' | Set-Content -LiteralPath $report -Encoding UTF8
  if (!(Add-AutopilotTaskScoreToReport -ReportPath $report -Score $score)) { throw 'score was not appended' }
  if (Add-AutopilotTaskScoreToReport -ReportPath $report -Score $score) { throw 'duplicate score was appended' }
  if ((Get-Content -LiteralPath $report -Raw -Encoding UTF8) -notmatch [regex]::Escape($score.key)) { throw 'score key missing from iteration report' }

  Write-Host 'task scoring self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
