param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runner = Join-Path $scriptDir 'autopilot-run-continuous.ps1'
$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-repair-' + [guid]::NewGuid().ToString('N'))
$backlog = Join-Path $root 'docs\backlog'; $scripts = Join-Path $root 'scripts\codex-autopilot'; $autoDir = Join-Path $root '.codex-autopilot'
New-Item -ItemType Directory -Path $backlog,$scripts,$autoDir -Force | Out-Null
try {
  $tick = [char]96
  @"
# Ready Issues
### ISSUE-991-001：Repair canary
任务性质：缺口修复
目标：
- Repair one deterministic failure.
非目标：
- No production change.
允许修改：
- ${tick}docs/quality/**${tick}
禁止修改：
- ${tick}deploy/**${tick}
验收标准：
- Second fresh executor attempt passes.
状态：Ready
来源锚点：${tick}docs/plans/source.md${tick}
验证命令：
- ${tick}powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/check-repair.ps1${tick}
归档报告：${tick}docs/quality/issue-991-001.md${tick}
Migration：不需要
依赖：无
风险等级：低
运行态要求：无
Reviewer要求：不需要
"@ | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  '# Done Issues' | Set-Content -LiteralPath (Join-Path $backlog 'done-issues.md') -Encoding UTF8
  '# Current Focus' | Set-Content -LiteralPath (Join-Path $backlog 'current-focus.md') -Encoding UTF8
  '# Plan' | Set-Content -LiteralPath (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md') -Encoding UTF8
  ".worktrees/`r`n.codex-autopilot/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'enabled' | Set-Content -LiteralPath (Join-Path $autoDir 'enabled.flag') -Encoding UTF8
  @'
param([string]$RepoRoot,[string]$IssueId,[string]$PromptPath)
$quality = Join-Path $RepoRoot 'docs\quality'; New-Item -ItemType Directory -Path $quality -Force | Out-Null
$attemptPath = Join-Path $quality 'attempt.txt'; $attempt = if (Test-Path $attemptPath) { [int](Get-Content $attemptPath -Raw) + 1 } else { 1 }
$attempt | Set-Content -LiteralPath $attemptPath -Encoding ascii
"repair $attempt" | Set-Content -LiteralPath (Join-Path $quality 'issue-991-001.md') -Encoding UTF8
'@ | Set-Content -LiteralPath (Join-Path $scripts 'mock-executor.ps1') -Encoding UTF8
  @'
$attempt = [int](Get-Content -LiteralPath 'docs\quality\attempt.txt' -Raw)
if ($attempt -lt 2) { Write-Error 'deterministic first-attempt failure'; exit 1 }
'@ | Set-Content -LiteralPath (Join-Path $scripts 'check-repair.ps1') -Encoding UTF8
  $configPath = Join-Path $scripts 'codex-autopilot.config.json'
  [ordered]@{
    repoRoot=$root;autopilotDir=$autoDir;maxIssuesPerRun=1;maxParallelIssues=1;parallelSafetyMode='strict-independent-only';autoMerge=$true;autoPush=$false;maxRunMinutes=30
    issueExecutor=[ordered]@{command='powershell';args=@('-NoProfile','-ExecutionPolicy','Bypass','-File','{repoRoot}\scripts\codex-autopilot\mock-executor.ps1','-RepoRoot','{repoRoot}','-IssueId','{issueId}','-PromptPath','{promptFile}');timeoutSeconds=30;requireChangedFiles=$true}
    closeout=[ordered]@{enabled=$true};repair=[ordered]@{enabled=$true;maxRepairAttempts=2};readyPlanner=[ordered]@{enabled=$false}
  } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configPath -Encoding UTF8
  & git -C $root init -q; & git -C $root config user.email 'autopilot@test.local'; & git -C $root config user.name 'AutoPilot Test'; & git -C $root add .; & git -C $root commit -qm 'repair base'
  $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $configPath -MaxIterations 1 -MaxLoops 1 -ApplyBacklogSplit 2>&1 | Out-String
  $ErrorActionPreference = $old
  $canonical = Get-ChildItem -LiteralPath (Join-Path $autoDir 'runs') -Directory | Where-Object Name -notmatch '-repair-' | Select-Object -First 1
  $result = Get-Content -LiteralPath (Join-Path $canonical.FullName 'result.json') -Raw | ConvertFrom-Json
  if ($result.status -ne 'done' -or !$result.gitSummary.commit) { throw "repair integration did not close: $output" }
  if ([int](Get-Content -LiteralPath (Join-Path $root 'docs\quality\attempt.txt') -Raw) -ne 2) { throw 'repair did not use exactly one fresh retry' }
  if (!(Test-Path -LiteralPath (Join-Path $canonical.FullName 'ISSUE-991-001\repair-1\context.json'))) { throw 'repair context was not isolated' }
  Write-Host 'repair integration self-test passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree prune 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
