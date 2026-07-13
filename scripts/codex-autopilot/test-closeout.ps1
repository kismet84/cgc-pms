param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-worktree.ps1')
. (Join-Path $scriptDir 'autopilot-closeout.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-closeout-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $root 'docs\backlog'),(Join-Path $root 'docs\quality') -Force | Out-Null
try {
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  ".worktrees/`r`n.codex-autopilot/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  @'
# Ready Issues
### ISSUE-900-040：Closeout
状态：Ready
存量问题键：[stock:STOCK-900-040]
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
  $result = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $worktree.path -Issue $issue -AutoMerge $true -BaseBranch $baseBranch -ExpectedBaseCommit $base
  if (!$result.commit -or !$result.merged) { throw 'closeout did not commit and merge' }
  if ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $root 'docs\backlog\ready-issues.md') -Raw) -notmatch '状态：Done') { throw 'Ready was not closed as Done' }
  if ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $root 'docs\backlog\done-issues.md') -Raw) -notmatch 'ISSUE-900-040') { throw 'done ledger was not updated' }
  $again = Complete-AutopilotIssueCloseout -RepoRoot $root -Worktree $worktree.path -Issue $issue -AutoMerge $true -BaseBranch $baseBranch -ExpectedBaseCommit $base
  if ($again.commit -ne $result.commit -or !$again.idempotent) { throw 'closeout retry was not idempotent' }
  Write-Host 'closeout self-test passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree remove --force (Join-Path $root '.worktrees\autopilot\issue-900-040') 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
