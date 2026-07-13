param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runner = Join-Path $scriptDir 'autopilot-run-continuous.ps1'
. (Join-Path $scriptDir 'autopilot-metrics.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-canary-' + [guid]::NewGuid().ToString('N'))
$backlog = Join-Path $root 'docs\backlog'
$fixtureScripts = Join-Path $root 'scripts\codex-autopilot'
$autoDir = Join-Path $root '.codex-autopilot'
New-Item -ItemType Directory -Path $backlog,$fixtureScripts,$autoDir -Force | Out-Null

function New-CanaryBlock([int]$Index) {
  $id = 'ISSUE-990-{0:000}' -f $Index
  $tick = [char]96
  return @"
### ${id}：Unattended canary $Index
任务性质：回归证明
目标：
- Complete one isolated docs canary.
非目标：
- No business or production change.
允许修改：
- ${tick}docs/quality/**${tick}
禁止修改：
- ${tick}deploy/**${tick}
验收标准：
- Evidence, Done, commit and merge are recorded.
状态：Ready
来源锚点：${tick}docs/plans/第36条主线-本地AutoPilot无人值守统一控制面与上下文隔离任务计划书.md${tick}
验证命令：
- ${tick}git diff --check${tick}
归档报告：${tick}docs/quality/$($id.ToLowerInvariant()).md${tick}
Migration：不需要
依赖：无
风险等级：低
运行态要求：无
Reviewer要求：不需要
"@
}

try {
  $blocks = 1..20 | ForEach-Object { New-CanaryBlock $_ }
  ("# Ready Issues`r`n`r`n" + ($blocks -join "`r`n")) | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  '# Done Issues' | Set-Content -LiteralPath (Join-Path $backlog 'done-issues.md') -Encoding UTF8
  '# Current Focus' | Set-Content -LiteralPath (Join-Path $backlog 'current-focus.md') -Encoding UTF8
  '# Plan' | Set-Content -LiteralPath (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md') -Encoding UTF8
  '# Ad-hoc' | Set-Content -LiteralPath (Join-Path $backlog 'ad-hoc-plan.md') -Encoding UTF8
  '# Blocked' | Set-Content -LiteralPath (Join-Path $backlog 'blocked-issues.md') -Encoding UTF8
  ".worktrees/`r`n.codex-autopilot/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'enabled' | Set-Content -LiteralPath (Join-Path $autoDir 'enabled.flag') -Encoding UTF8

  @'
param([string]$RepoRoot,[string]$IssueId,[string]$PromptPath)
$quality = Join-Path $RepoRoot 'docs\quality'
New-Item -ItemType Directory -Path $quality -Force | Out-Null
"canary $IssueId" | Set-Content -LiteralPath (Join-Path $quality ($IssueId.ToLowerInvariant() + '.md')) -Encoding UTF8
'@ | Set-Content -LiteralPath (Join-Path $fixtureScripts 'mock-executor.ps1') -Encoding UTF8

  $configPath = Join-Path $fixtureScripts 'codex-autopilot.config.json'
  [ordered]@{
    repoRoot = $root; autopilotDir = $autoDir; maxIssuesPerRun = 1; maxParallel = 1; maxParallelIssues = 1
    parallelSafetyMode = 'strict-independent-only'; autoMerge = $true; autoPush = $false; maxRunMinutes = 30
    issueExecutor = [ordered]@{ command = 'powershell'; args = @('-NoProfile','-ExecutionPolicy','Bypass','-File','{repoRoot}\scripts\codex-autopilot\mock-executor.ps1','-RepoRoot','{repoRoot}','-IssueId','{issueId}','-PromptPath','{promptFile}'); timeoutSeconds = 30; requireChangedFiles = $true }
    closeout = [ordered]@{ enabled = $true; localCommit = $true; localFastForwardMerge = $true }
    readyPlanner = [ordered]@{ enabled = $false }
  } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configPath -Encoding UTF8

  & git -C $root init -q; & git -C $root config user.email 'autopilot@test.local'; & git -C $root config user.name 'AutoPilot Test'; & git -C $root add .; & git -C $root commit -qm 'canary base'
  $oldErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  for ($index = 1; $index -le 20; $index++) {
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $configPath -MaxIterations 20 -MaxLoops 2 -ApplyBacklogSplit 2>&1 | Out-String
    if ($output -notmatch 'EXECUTOR_RESULT_WRITTEN') { throw "canary $index did not execute: $output" }
    $result = Get-ChildItem -LiteralPath (Join-Path $autoDir 'runs') -Filter result.json -Recurse | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | ForEach-Object { Get-Content -Encoding UTF8 -LiteralPath $_.FullName -Raw | ConvertFrom-Json }
    if ($result.status -ne 'done' -or !$result.gitSummary.commit -or @($result.evidencePaths).Count -eq 0) { throw "canary $index lacks completion integrity" }
    if (!$result.firstPassSuccess -or $result.manualInterventionCount -ne 0 -or $result.scopeViolationCount -ne 0) { throw "canary $index lacks unattended qualification fields" }
  }
  $limitOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $configPath -MaxIterations 20 -MaxLoops 1 -ApplyBacklogSplit 2>&1 | Out-String
  $ErrorActionPreference = $oldErrorActionPreference
  if ($limitOutput -notmatch 'STOP_ITERATION_LIMIT_REACHED') { throw 'canary did not stop at 20/20' }
  $qualification = Get-AutopilotQualification -RunsDir (Join-Path $autoDir 'runs') -WindowSize 20
  if (!$qualification.qualified) { throw "20-run qualification failed: $($qualification.reasons -join '; ')" }
  if ($qualification.firstPassRate -lt 0.8 -or $qualification.manualInterventionCount -ne 0 -or $qualification.scopeViolationCount -ne 0) { throw '20-run qualification thresholds were not enforced' }
  Write-Host 'unattended 20-run canary passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree prune 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
