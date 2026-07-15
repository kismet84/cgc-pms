param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-worktree.ps1')
. (Join-Path $scriptDir 'autopilot-context.ps1')

$root = Join-Path ([System.IO.Path]::GetTempPath()) ('autopilot-context-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null

function Invoke-Git {
  param([string[]]$Arguments)
  & git -C $root @Arguments | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "git failed: $($Arguments -join ' ')" }
}

try {
  Invoke-Git @('init','-q')
  Invoke-Git @('config','user.email','autopilot@test.local')
  Invoke-Git @('config','user.name','AutoPilot Test')
  Invoke-Git @('config','core.autocrlf','false')
  Invoke-Git @('config','core.eol','lf')
  New-Item -ItemType Directory -Path (Join-Path $root 'docs\quality') -Force | Out-Null
  New-Item -ItemType Directory -Path (Join-Path $root 'plugins\cgc-pms-autopilot\references') -Force | Out-Null
  "# Policy`n`nPolicy-Version: 1`nStatus: active" | Set-Content -LiteralPath (Join-Path $root 'plugins\cgc-pms-autopilot\references\control-plane-policy.md') -Encoding UTF8
  ".worktrees/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'base' | Set-Content -LiteralPath (Join-Path $root 'docs\quality\base.md') -Encoding UTF8
  Invoke-Git @('add','.')
  Invoke-Git @('commit','-qm','base')
  $baseCommit = (& git -C $root rev-parse HEAD).Trim()

  'dirty-main' | Set-Content -LiteralPath (Join-Path $root 'docs\quality\base.md') -Encoding UTF8
  $issue = [pscustomobject]@{
    issueId = 'ISSUE-900-010'; title = 'ISSUE-900-010：Context isolation'; body = 'task'
    readyContentHash = 'ready-hash-a'; taskNature = '缺口修复'; goal = @('isolate')
    nonGoals = @('no leak'); acceptanceCriteria = @('preserve business behavior'); allowedPaths = @('docs/quality/**'); forbiddenPaths = @('deploy/**')
    validationCommands = @('git diff --check'); riskLevel = '低'; migration = '不需要'
    candidateEvidenceHead = ('a' * 40)
  }
  $worktree = New-AutopilotIssueWorktree -RepoRoot $root -IssueId $issue.issueId -BaseCommit $baseCommit
  if ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $worktree.path 'docs\quality\base.md') -Raw).Trim() -ne 'base') { throw 'main dirty content leaked into worktree' }
  'repair-diff' | Set-Content -LiteralPath (Join-Path $worktree.path 'docs\quality\repair.md') -Encoding UTF8
  $dirtyReuseRejected = $false
  try { New-AutopilotIssueWorktree -RepoRoot $root -IssueId $issue.issueId -BaseCommit $baseCommit | Out-Null } catch { $dirtyReuseRejected = $true }
  if (!$dirtyReuseRejected) { throw 'dirty worktree was reused by a fresh implementation attempt' }
  $repairWorktree = New-AutopilotIssueWorktree -RepoRoot $root -IssueId $issue.issueId -BaseCommit $baseCommit -AllowDirtyReuse
  if (!$repairWorktree.reused) { throw 'repair did not reuse the matching dirty worktree' }

  $contextPath = Join-Path $root 'context-a.json'
  $context = New-AutopilotContextPack -Issue $issue -Phase 'implement' -RepoRoot $root -Worktree $worktree.path -OutputPath $contextPath -RelevantSymbols @('docs/quality/base.md')
  if ($context.schemaVersion -ne 2 -or $context.issueId -ne $issue.issueId -or $context.baseCommit -ne $baseCommit -or $context.executionBaseCommit -ne $baseCommit -or $context.candidateEvidenceHead -ne ('a' * 40) -or $context.controlPlanePolicyHash -notmatch '^[a-f0-9]{64}$') { throw 'context identity is wrong' }
  if ($context.acceptanceCriteria -notcontains 'preserve business behavior') { throw 'acceptance criteria were omitted from isolated context' }
  $candidateMismatch = $issue.PSObject.Copy()
  $candidateMismatch.candidateEvidenceHead = ('b' * 40)
  $candidateMismatchRejected = $false
  try { Assert-AutopilotContextCurrent -Context $context -Issue $candidateMismatch -Worktree $worktree.path -ExpectedBaseCommit $baseCommit | Out-Null } catch { $candidateMismatchRejected = $true }
  if (!$candidateMismatchRejected) { throw 'stale candidate evidence head was accepted' }

  $issueB = $issue.PSObject.Copy()
  $issueB.issueId = 'ISSUE-900-011'
  $issueB.title = 'ISSUE-900-011：Other context'
  $issueB.readyContentHash = 'ready-hash-b'
  $contextBPath = Join-Path $root 'context-b.json'
  New-AutopilotContextPack -Issue $issueB -Phase 'implement' -RepoRoot $root -Worktree $worktree.path -OutputPath $contextBPath | Out-Null
  if ((Get-Content -Encoding UTF8 -LiteralPath $contextBPath -Raw) -match 'ISSUE-900-010|executor\.log') { throw 'previous Issue leaked into next context' }

  $violationRejected = $false
  try { Assert-AutopilotAllowedChanges -ChangedPaths @('deploy/prod.yml') -AllowedPaths $issue.allowedPaths -ForbiddenPaths $issue.forbiddenPaths } catch { $violationRejected = $true }
  if (!$violationRejected) { throw 'allowlist violation was accepted' }

  $mismatchRejected = $false
  try { Assert-AutopilotContextCurrent -Context $context -Issue $issue -Worktree $worktree.path -ExpectedBaseCommit '0000000000000000000000000000000000000000' } catch { $mismatchRejected = $true }
  if (!$mismatchRejected) { throw 'commit mismatch was accepted' }

  Invoke-Git @('-C',$worktree.path,'add','docs/quality/repair.md')
  & git -C $worktree.path commit -qm 'stale worktree head'
  $staleHeadRejected = $false
  try { New-AutopilotIssueWorktree -RepoRoot $root -IssueId $issue.issueId -BaseCommit $baseCommit -AllowDirtyReuse | Out-Null } catch { $staleHeadRejected = $true }
  if (!$staleHeadRejected) { throw 'worktree with stale committed HEAD was reused' }

  Write-Host 'context isolation self-test passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree remove --force (Join-Path $root '.worktrees\autopilot\issue-900-010') 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
