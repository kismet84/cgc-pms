param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runner = Join-Path $scriptDir 'autopilot-run-continuous.ps1'
$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-stall-' + [guid]::NewGuid().ToString('N'))
$backlog = Join-Path $root 'docs\backlog'
$fixtureScripts = Join-Path $root 'scripts\codex-autopilot'
$autoDir = Join-Path $root '.codex-autopilot'
$counterPath = Join-Path $root 'executor-attempts.txt'
New-Item -ItemType Directory -Path $backlog,$fixtureScripts,$autoDir -Force | Out-Null
try {
  $tick = [char]96
  @"
# Ready Issues
### ISSUE-992-001：Stall recovery canary
任务性质：回归证明
目标：
- Terminate a stalled executor and retry once with fresh context.
非目标：
- No production change.
允许修改：
- ${tick}docs/quality/**${tick}
禁止修改：
- ${tick}deploy/**${tick}
验收标准：
- Two stalled attempts end safely in Blocked.
状态：Ready
来源锚点：${tick}docs/plans/source.md${tick}
验证命令：
- ${tick}git diff --check${tick}
归档报告：${tick}docs/quality/issue-992-001.md${tick}
Migration：不需要
依赖：无
风险等级：低
运行态要求：无
Reviewer要求：不需要
"@ | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  '# Done Issues' | Set-Content -LiteralPath (Join-Path $backlog 'done-issues.md') -Encoding UTF8
  '# Current Focus' | Set-Content -LiteralPath (Join-Path $backlog 'current-focus.md') -Encoding UTF8
  '# Plan' | Set-Content -LiteralPath (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md') -Encoding UTF8
  ".worktrees/`r`n.codex-autopilot/`r`nexecutor-attempts.txt" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'enabled' | Set-Content -LiteralPath (Join-Path $autoDir 'enabled.flag') -Encoding UTF8
  @"
param([string]`$RepoRoot,[string]`$IssueId,[string]`$PromptPath)
`$count = if (Test-Path -LiteralPath '$($counterPath.Replace("'","''"))') { [int](Get-Content -LiteralPath '$($counterPath.Replace("'","''"))' -Raw) + 1 } else { 1 }
`$count | Set-Content -LiteralPath '$($counterPath.Replace("'","''"))' -Encoding ascii
Start-Sleep -Seconds 30
"@ | Set-Content -LiteralPath (Join-Path $fixtureScripts 'mock-executor.ps1') -Encoding UTF8
  $configPath = Join-Path $fixtureScripts 'codex-autopilot.config.json'
  [ordered]@{
    repoRoot=$root;autopilotDir=$autoDir;maxIssuesPerRun=1;maxParallelIssues=1;parallelSafetyMode='strict-independent-only';autoMerge=$true;autoPush=$false;maxRunMinutes=30
    issueExecutor=[ordered]@{command='powershell';args=@('-NoProfile','-ExecutionPolicy','Bypass','-File','{repoRoot}\scripts\codex-autopilot\mock-executor.ps1','-RepoRoot','{repoRoot}','-IssueId','{issueId}','-PromptPath','{promptFile}');timeoutSeconds=60;stallInspectSeconds=3;stallTerminateSeconds=8;heartbeatMilliseconds=250;requireChangedFiles=$true}
    closeout=[ordered]@{enabled=$true};repair=[ordered]@{enabled=$true;maxRepairAttempts=2};readyPlanner=[ordered]@{enabled=$false}
  } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configPath -Encoding UTF8
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  & git -C $root add .
  & git -C $root commit -qm 'stall base'
  $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $configPath -MaxIterations 1 -MaxLoops 1 -ApplyBacklogSplit 2>&1 | Out-String
  $ErrorActionPreference = $old
  $state = Get-Content -LiteralPath (Join-Path $autoDir 'state.json') -Raw | ConvertFrom-Json
  if ($state.status -ne 'BLOCKED' -or $state.stopReason -ne 'STOP_EXECUTOR_PROCESS_FAILED') { throw "stalled executor did not stop safely: $output" }
  if ([int](Get-Content -LiteralPath $counterPath -Raw) -ne 2) { throw 'stalled executor did not use exactly one fresh retry' }
  $stallEvents = @(Get-Content -LiteralPath (Join-Path $autoDir 'events.ndjson') | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object event -eq 'executor.stall.terminate')
  if ($stallEvents.Count -ne 2) { throw 'stall termination evidence is incomplete' }
  Write-Host 'executor stall self-test passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree prune 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
