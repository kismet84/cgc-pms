[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$suffix = [guid]::NewGuid().ToString('N')
$root = Join-Path ([IO.Path]::GetTempPath()) "codex-task-git-lifecycle-$suffix"
$remote = Join-Path ([IO.Path]::GetTempPath()) "codex-task-git-lifecycle-$suffix.git"
$occupied = Join-Path ([IO.Path]::GetTempPath()) "codex-task-git-lifecycle-$suffix-worktree"

function Invoke-Git([string]$Repo, [string[]]$Arguments) {
  $output = & git -C $Repo @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) { throw "git failed in ${Repo}: git $($Arguments -join ' ')`n$output" }
  return @($output)
}

try {
  New-Item -ItemType Directory -Path $root -Force | Out-Null
  & git init --bare -q $remote
  if ($LASTEXITCODE -ne 0) { throw 'bare remote initialization failed' }
  Invoke-Git $root @('init','-q','-b','master') | Out-Null
  Invoke-Git $root @('config','user.email','codex-policy@test.local') | Out-Null
  Invoke-Git $root @('config','user.name','Codex Policy Test') | Out-Null
  Invoke-Git $root @('config','core.autocrlf','false') | Out-Null
  'base' | Set-Content -LiteralPath (Join-Path $root 'policy.txt') -Encoding UTF8
  Invoke-Git $root @('add','policy.txt') | Out-Null
  Invoke-Git $root @('commit','-qm','base') | Out-Null
  Invoke-Git $root @('remote','add','origin',$remote) | Out-Null
  Invoke-Git $root @('push','-q','-u','origin','master') | Out-Null
  $masterBefore = (Invoke-Git $remote @('rev-parse','refs/heads/master') | Select-Object -First 1).Trim()

  Invoke-Git $root @('switch','-q','-c','codex/execution-policy-fixture') | Out-Null
  'feature' | Set-Content -LiteralPath (Join-Path $root 'policy.txt') -Encoding UTF8
  Invoke-Git $root @('add','policy.txt') | Out-Null
  Invoke-Git $root @('commit','-qm','feature') | Out-Null
  Invoke-Git $root @('push','-q','-u','origin','codex/execution-policy-fixture') | Out-Null
  $masterAfter = (Invoke-Git $remote @('rev-parse','refs/heads/master') | Select-Object -First 1).Trim()
  $featureRemote = (Invoke-Git $remote @('rev-parse','refs/heads/codex/execution-policy-fixture') | Select-Object -First 1).Trim()
  $featureLocal = (Invoke-Git $root @('rev-parse','HEAD') | Select-Object -First 1).Trim()
  if ($masterAfter -ne $masterBefore) { throw 'feature push changed remote master' }
  if ($featureRemote -ne $featureLocal) { throw 'feature branch was not pushed to its matching remote branch' }

  Invoke-Git $root @('switch','-q','-c','codex/unmerged-fixture') | Out-Null
  'unmerged' | Set-Content -LiteralPath (Join-Path $root 'unmerged.txt') -Encoding UTF8
  Invoke-Git $root @('add','unmerged.txt') | Out-Null
  Invoke-Git $root @('commit','-qm','unmerged') | Out-Null
  Invoke-Git $root @('switch','-q','codex/execution-policy-fixture') | Out-Null
  $mergedIntoMaster = @(Invoke-Git $root @('branch','--merged','master') | ForEach-Object { ([string]$_).Trim().TrimStart('*').Trim() })
  if ($mergedIntoMaster -contains 'codex/unmerged-fixture') { throw 'unmerged branch appeared in cleanup candidates' }

  Invoke-Git $root @('branch','codex/worktree-fixture') | Out-Null
  Invoke-Git $root @('worktree','add','-q',$occupied,'codex/worktree-fixture') | Out-Null
  $worktreeList = (Invoke-Git $root @('worktree','list','--porcelain')) -join "`n"
  if ($worktreeList -notmatch [regex]::Escape('branch refs/heads/codex/worktree-fixture')) { throw 'occupied worktree branch was not detected' }

  [pscustomobject]@{
    ok = $true
    remoteMasterUnchanged = $true
    featureBranchPushed = $true
    unmergedBranchExcluded = $true
    occupiedWorktreeDetected = $true
  } | ConvertTo-Json -Depth 3
} finally {
  foreach ($path in @($occupied,$root,$remote)) {
    if ($path -and (Test-Path -LiteralPath $path) -and [IO.Path]::GetFullPath($path).StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()), [StringComparison]::OrdinalIgnoreCase)) {
      Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
    }
  }
}
