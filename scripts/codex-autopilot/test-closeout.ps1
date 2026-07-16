param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-worktree.ps1')
. (Join-Path $scriptDir 'autopilot-task-score.ps1')
. (Join-Path $scriptDir 'autopilot-closeout.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-closeout-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $root 'docs\backlog'),(Join-Path $root 'docs\quality') -Force | Out-Null
try {
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  & git -C $root config core.autocrlf false
  & git -C $root config core.eol lf
  & git -C $root config core.safecrlf false
  [IO.File]::WriteAllText((Join-Path $root '.gitattributes'), "* text=auto eol=lf`n*.cmd text eol=crlf`n", [Text.UTF8Encoding]::new($false))
  ".worktrees/`r`n.codex-autopilot/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  @'
# Ready Issues
### ISSUE-900-040：Closeout
状态：Ready
存量问题键：[stock:STOCK-900-040]

### ISSUE-900-039：Previously completed
状态：Done
'@ | Set-Content -LiteralPath (Join-Path $root 'docs\backlog\ready-issues.md') -Encoding UTF8
  '# Done Issues' | Set-Content -LiteralPath (Join-Path $root 'docs\backlog\done-issues.md') -Encoding UTF8
  @'
{
  "schemaVersion": 1,
  "issues": [
    {
      "issueKey": "STOCK-900-040",
      "priority": "P1",
      "status": "OPEN",
      "classification": "STILL_APPLICABLE",
      "blocking": false,
      "acceptanceCriteria": ["完成收口门禁测试"],
      "sourceRefs": ["docs/backlog/ready-issues.md"]
    }
  ]
}
'@ | Set-Content -LiteralPath (Join-Path $root 'docs\backlog\current-issues.json') -Encoding UTF8
  & git -C $root add .; & git -C $root commit -qm 'base'
  $base = (& git -C $root rev-parse HEAD).Trim()
  $worktree = New-AutopilotIssueWorktree -RepoRoot $root -IssueId 'ISSUE-900-040' -BaseCommit $base
  New-Item -ItemType Directory -Path (Join-Path $worktree.path 'docs\quality') -Force | Out-Null
  'accepted' | Set-Content -LiteralPath (Join-Path $worktree.path 'docs\quality\issue-900-040.md') -Encoding UTF8
  $issue = [pscustomobject]@{ issueId = 'ISSUE-900-040'; title = 'ISSUE-900-040：Closeout'; archiveReport = 'docs/quality/issue-900-040.md' }
  $baseBranch = (& git -C $root branch --show-current).Trim()
  & git -C $root switch -qc wrong-branch
  $wrongBranchRejected = $false
  try { Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $worktree.path -Issue $issue -AutoMerge $true -BaseBranch $baseBranch -ExpectedBaseCommit $base | Out-Null } catch { $wrongBranchRejected = $true }
  if (!$wrongBranchRejected) { throw 'closeout merged into a non-base branch' }
  & git -C $root switch -q $baseBranch
  $staleStockRejected = $false
  try { Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $worktree.path -Issue $issue -AutoMerge $true -BaseBranch $baseBranch -ExpectedBaseCommit $base | Out-Null } catch {
    $staleStockRejected = $_.Exception.Message -match 'stock issue remains eligible'
  }
  if (!$staleStockRejected) { throw 'closeout accepted a Ready whose stock issue remained eligible' }
  $registryPath = Join-Path $worktree.path 'docs\backlog\current-issues.json'
  $registry = Get-Content -LiteralPath $registryPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $registry.issues[0].status = 'FROZEN'
  $registry | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $registryPath -Encoding UTF8
  $result = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $worktree.path -Issue $issue -AutoMerge $false -BaseBranch $baseBranch -ExpectedBaseCommit $base
  if (!$result.commit -or $result.merged) { throw 'unscored closeout did not produce an isolated closeout commit' }
  $unmergedAgain = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $worktree.path -Issue $issue -AutoMerge $false -BaseBranch $baseBranch -ExpectedBaseCommit $base
  if (!$unmergedAgain.idempotent -or $unmergedAgain.score -or $unmergedAgain.closeoutCommit -ne $result.closeoutCommit) { throw 'unscored closeout retry incorrectly required scoring evidence' }
  $unscoredMerge = Merge-AutopilotIssueCloseoutCommit -RepoRoot $root -Commit $result.closeoutCommit -ExpectedBaseCommit $base
  if (!$unscoredMerge.merged -or $unscoredMerge.idempotent) { throw 'unscored closeout commit did not fast-forward after idempotent retry' }
  if ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $root 'docs\backlog\ready-issues.md') -Raw) -notmatch '状态：Done') { throw 'Ready was not closed as Done' }
  if ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $root 'docs\backlog\done-issues.md') -Raw) -notmatch 'ISSUE-900-040') { throw 'done ledger was not updated' }
  $again = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $worktree.path -Issue $issue -AutoMerge $true -BaseBranch $baseBranch -ExpectedBaseCommit $base
  if ($again.commit -ne $result.commit -or !$again.idempotent) { throw 'closeout retry was not idempotent' }

  Add-Content -LiteralPath (Join-Path $root 'docs\backlog\ready-issues.md') -Encoding UTF8 -Value "`r`n### ISSUE-900-041：Scored closeout`r`n状态：Ready`r`n"
  @'
# ISSUE-900-041

- 新增后续项：0
- 关闭后续项：0
- 后续项净变化：0
'@ | Set-Content -LiteralPath (Join-Path $root 'docs\quality\issue-900-041.md') -Encoding UTF8
  & git -C $root add .; & git -C $root commit -qm 'scored fixture'
  $scoredBase = (& git -C $root rev-parse HEAD).Trim()
  $scoredWorktree = New-AutopilotIssueWorktree -RepoRoot $root -IssueId 'ISSUE-900-041' -BaseCommit $scoredBase
  'implementation' | Set-Content -LiteralPath (Join-Path $scoredWorktree.path 'implementation.txt') -Encoding UTF8
  $scoredIssue = [pscustomobject]@{ issueId = 'ISSUE-900-041'; title = 'ISSUE-900-041：Scored closeout'; archiveReport = 'docs/quality/issue-900-041.md' }
  Assert-AutopilotImplementationCloseoutArtifacts -Worktree $scoredWorktree.path -Issue $scoredIssue | Out-Null
  $scoreEvidence = [ordered]@{
    issueId='ISSUE-900-041';implementationCommit=('0'*40);scoringVersion='autopilot-task-score/v1';hardGatesPassed=$true;
    acceptanceCriteriaCovered=$true;targetValidationPassed=$true;scopeConsistent=$true;discoveriesDispositionComplete=$true;
    followupGovernanceComplete=$true;attempt=0;cycleEvidenceComplete=$true;avoidableReworkCount=0;stockIssueTarget=$false;
    stockIssueClosed=$false;sameRootIssueAdded=$false;followupNetChange=0;sourceRefs=@('docs/quality/issue-900-041.md')
  }
  $scoreConfig = [pscustomobject]@{ enabled=$true; activeVersion='autopilot-task-score/v2'; approvalStatus='APPROVED' }
  $v2Evidence = [ordered]@{} + $scoreEvidence
  $v2Evidence.scoringVersion='autopilot-task-score/v2'; $v2Evidence.executionEvidenceComplete=$true; $v2Evidence.implementationDispatchCount=1; $v2Evidence.validationDispatchCount=1; $v2Evidence.reviewDispatchCount=0; $v2Evidence.repairDispatchCount=0; $v2Evidence.closeoutDispatchCount=1; $v2Evidence.reviewRequired=$false; $v2Evidence.runResumeCount=0; $v2Evidence.phaseRestartCount=0; $v2Evidence.manualRecoveryCount=0; $v2Evidence.toolConfigBlockCount=0; $v2Evidence.environmentRetryCount=0; $v2Evidence.duplicateDispatchBlockedCount=0
  $scored = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $scoredWorktree.path -Issue $scoredIssue -AutoMerge $false -BaseBranch $baseBranch -ExpectedBaseCommit $scoredBase -ScoreEvidence $v2Evidence -TaskScoringConfig $scoreConfig
  if ($scored.merged -or !$scored.score -or $scored.scoreShadow -or $scored.score.scoringVersion -ne 'autopilot-task-score/v2' -or !$scored.score.dimensions.taskExecutionEfficiency -or $scored.implementationCommit -eq $scored.closeoutCommit) { throw 'v2 scored closeout did not produce one formal score and two distinct unmerged commits' }
  $closeoutParent = (& git -C $root rev-parse "$($scored.closeoutCommit)^" | Select-Object -First 1).Trim()
  if ($closeoutParent -ne $scored.implementationCommit) { throw 'score closeout commit is not based on implementationCommit' }
  if ((Get-Content -LiteralPath (Join-Path $scoredWorktree.path 'docs\quality\issue-900-041.md') -Raw -Encoding UTF8) -notmatch [regex]::Escape($scored.score.key)) { throw 'formal report does not contain the bound score' }
  $durableMerge = Merge-AutopilotIssueCloseoutCommit -RepoRoot $root -Commit $scored.closeoutCommit -ExpectedBaseCommit $scoredBase
  if (!$durableMerge.merged -or $durableMerge.idempotent) { throw 'durable closeout merge did not fast-forward after checkpoint-ready commit creation' }
  $durableMergeAgain = Merge-AutopilotIssueCloseoutCommit -RepoRoot $root -Commit $scored.closeoutCommit -ExpectedBaseCommit $scoredBase
  if (!$durableMergeAgain.merged -or !$durableMergeAgain.idempotent) { throw 'durable closeout merge retry was not idempotent' }
  $recoveredMerge = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $scoredWorktree.path -Issue $scoredIssue -AutoMerge $true -BaseBranch $baseBranch -ExpectedBaseCommit $scoredBase -ScoreEvidence $v2Evidence -TaskScoringConfig $scoreConfig
  if (!$recoveredMerge.merged -or !$recoveredMerge.idempotent -or !$recoveredMerge.score -or $recoveredMerge.scoreShadow -or $recoveredMerge.score.scoringVersion -ne 'autopilot-task-score/v2') { throw 'closeout commit was not resumed and merged with its bound v2 score' }
  $scoredAgain = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $scoredWorktree.path -Issue $scoredIssue -AutoMerge $true -BaseBranch $baseBranch -ExpectedBaseCommit $scoredBase -ScoreEvidence $v2Evidence -TaskScoringConfig $scoreConfig
  if (!$scoredAgain.idempotent -or !$scoredAgain.score -or $scoredAgain.closeoutCommit -ne $scored.closeoutCommit) { throw 'scored closeout retry is not idempotent' }
  Write-Host 'closeout self-test passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree remove --force (Join-Path $root '.worktrees\autopilot\issue-900-040') 2>$null | Out-Null }
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree remove --force (Join-Path $root '.worktrees\autopilot\issue-900-041') 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
