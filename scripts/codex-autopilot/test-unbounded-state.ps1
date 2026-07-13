param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runner = Join-Path $scriptDir 'autopilot-run-continuous.ps1'
$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-unbounded-' + [guid]::NewGuid().ToString('N'))
$backlog = Join-Path $root 'docs\backlog'; $autoDir = Join-Path $root '.codex-autopilot'; $fixtureScripts = Join-Path $root 'scripts\codex-autopilot'
New-Item -ItemType Directory -Path $backlog,$autoDir,$fixtureScripts -Force | Out-Null
try {
  '# Ready Issues' | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  '# Current Focus' | Set-Content -LiteralPath (Join-Path $backlog 'current-focus.md') -Encoding UTF8
  '# Plan' | Set-Content -LiteralPath (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md') -Encoding UTF8
  ".worktrees/`r`n.codex-autopilot/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'enabled' | Set-Content -LiteralPath (Join-Path $autoDir 'enabled.flag') -Encoding UTF8
  [ordered]@{schemaVersion=2;runId='old-run';status='CHECKPOINT';phase='checkpoint';currentIssue='';attempt=0;startedAt=[datetimeoffset]::Now.AddMinutes(-5).ToString('o');phaseStartedAt=[datetimeoffset]::Now.ToString('o');lastHeartbeatAt=[datetimeoffset]::Now.ToString('o');iterationLimit=$null;completedImplementationIssues=2;completedIssueIds=@('ISSUE-1','ISSUE-2');worktree='';branch='master';executorPid=$null;lastCommit=$null;failureFingerprint=$null;iterationCompleted=2;iterationLastCountedIssue='ISSUE-2'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $autoDir 'state.json') -Encoding UTF8
  $configPath = Join-Path $fixtureScripts 'codex-autopilot.config.json'
  [ordered]@{repoRoot=$root;autopilotDir=$autoDir;maxIssuesPerRun=1;maxParallelIssues=1;parallelSafetyMode='strict-independent-only';autoMerge=$true;autoPush=$false;maxRunMinutes=30;readyPlanner=[ordered]@{enabled=$false}} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $configPath -Encoding UTF8
  & git -C $root init -q; & git -C $root config user.email 'autopilot@test.local'; & git -C $root config user.name 'AutoPilot Test'; & git -C $root add .; & git -C $root commit -qm 'base'
  $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & pwsh -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $configPath -MaxLoops 1 -ApplyBacklogSplit 2>&1 | Out-String
  $exitCode = $LASTEXITCODE
  $ErrorActionPreference = $old
  if ($exitCode -ne 0) { throw "unbounded runner failed: $output" }
  $state = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $autoDir 'state.json') -Raw | ConvertFrom-Json
  if ($state.completedImplementationIssues -ne 2 -or @($state.completedIssueIds).Count -ne 2) { throw "unbounded run corrupted completed state: $output" }
  Write-Host 'unbounded state self-test passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree prune 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
