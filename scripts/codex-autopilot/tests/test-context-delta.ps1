param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $scriptDir 'autopilot-context.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-context-delta-' + [guid]::NewGuid().ToString('N'))
$artifactRoot = "$root-artifacts"
New-Item -ItemType Directory -Path $root -Force | Out-Null
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
function Invoke-TestGit {
  param([string[]]$Arguments)
  & git -C $root @Arguments | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "git failed: $($Arguments -join ' ')" }
}
try {
  Invoke-TestGit @('init','-q')
  Invoke-TestGit @('config','user.email','autopilot@test.local')
  Invoke-TestGit @('config','user.name','AutoPilot Test')
  Invoke-TestGit @('config','core.autocrlf','false')
  Invoke-TestGit @('config','core.eol','lf')
  New-Item -ItemType Directory -Path (Join-Path $root 'docs\quality') -Force | Out-Null
  New-Item -ItemType Directory -Path (Join-Path $root 'plugins\cgc-pms-autopilot\references') -Force | Out-Null
  "# Policy`n`nPolicy-Version: 1`nStatus: active" | Set-Content -LiteralPath (Join-Path $root 'plugins\cgc-pms-autopilot\references\control-plane-policy.md') -Encoding UTF8
  'base' | Set-Content -LiteralPath (Join-Path $root 'docs\quality\base.md') -Encoding UTF8
  Invoke-TestGit @('add','.')
  Invoke-TestGit @('commit','-qm','base')
  $baseCommit = (& git -C $root rev-parse HEAD).Trim()
  $issue = [pscustomobject]@{
    issueId='ISSUE-900-045'; readyContentHash=('a' * 64); candidateEvidenceHead=('b' * 40)
    goal=@('reuse base'); nonGoals=@('no cross-Issue cache'); acceptanceCriteria=@('base built once')
    allowedPaths=@('docs/quality/**'); forbiddenPaths=@('deploy/**'); validationCommands=@('git diff --check')
    archiveReport='docs/quality/issue-900-045.md'
  }
  $basePath = Join-Path $artifactRoot 'context\base.json'
  $base = New-AutopilotContextBase -Issue $issue -RepoRoot $root -Worktree $root -OutputPath $basePath
  $firstBytes = [IO.File]::ReadAllBytes($basePath)
  $secondPath = Join-Path $artifactRoot 'context\base-second.json'
  $second = New-AutopilotContextBase -Issue $issue -RepoRoot $root -Worktree $root -OutputPath $secondPath
  if ($base.baseId -ne $second.baseId -or $base.contentHash -ne $second.contentHash) { throw 'same base input did not produce a stable identity' }
  if ([Convert]::ToBase64String($firstBytes) -ne [Convert]::ToBase64String([IO.File]::ReadAllBytes($secondPath))) { throw 'same base input was not byte stable' }
  if ($firstBytes.Count -ge 3 -and $firstBytes[0] -eq 0xEF -and $firstBytes[1] -eq 0xBB -and $firstBytes[2] -eq 0xBF) { throw 'context base unexpectedly contains UTF-8 BOM' }

  $deltaPath = Join-Path $artifactRoot 'context\implement.delta.json'
  $delta = New-AutopilotContextDelta -Base $base -Phase implement -Worktree $root -OutputPath $deltaPath
  Assert-AutopilotContextPairCurrent -Base $base -Delta $delta -Issue $issue -Worktree $root -ExpectedBaseCommit $baseCommit | Out-Null
  'changed' | Set-Content -LiteralPath (Join-Path $root 'docs\quality\base.md') -Encoding UTF8
  $staleRejected = $false
  try { Assert-AutopilotContextPairCurrent -Base $base -Delta $delta -Issue $issue -Worktree $root -ExpectedBaseCommit $baseCommit | Out-Null } catch { $staleRejected = $true }
  if (!$staleRejected) { throw 'stale delta diff identity was accepted' }
  $repairPath = Join-Path $artifactRoot 'context\repair.delta.json'
  $repair = New-AutopilotContextDelta -Base $base -Phase repair -Worktree $root -OutputPath $repairPath -ChangedPaths @('docs/quality/base.md') -PreviousPhaseSummary 'repair only'
  Assert-AutopilotContextPairCurrent -Base $base -Delta $repair -Issue $issue -Worktree $root -ExpectedBaseCommit $baseCommit | Out-Null
  if ($repair.baseId -ne $base.baseId -or $repair.changedPaths -notcontains 'docs/quality/base.md') { throw 'repair delta did not reuse the immutable base' }

  $tampered = Get-Content -LiteralPath $basePath -Raw -Encoding UTF8 | ConvertFrom-Json
  $tampered.goal = @('tampered')
  $tamperRejected = $false
  try { Assert-AutopilotContextPairCurrent -Base $tampered -Delta $repair -Issue $issue -Worktree $root -ExpectedBaseCommit $baseCommit | Out-Null } catch { $tamperRejected = $true }
  if (!$tamperRejected) { throw 'tampered context base was accepted' }
  Write-Host 'context delta self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $artifactRoot -Recurse -Force -ErrorAction SilentlyContinue
}
