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
  $defaults = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $scriptDir 'codex-autopilot.config.json') -Raw | ConvertFrom-Json
  if ([int]$defaults.issueExecutor.stallInspectSeconds -ne 300 -or [int]$defaults.issueExecutor.stallTerminateSeconds -ne 600) { throw 'production stall thresholds must remain 300/600 seconds' }
  $runnerText = Get-Content -Encoding UTF8 -LiteralPath $runner -Raw
  if ($runnerText -notmatch '(?s)\$idleSeconds\s*=.*?Now\s+-ge\s+\$deadline.*?\$idleSeconds\s+-ge\s+\$StallTerminateSeconds') { throw 'issueExecutor total timeout must be checked before long-command stall exemption' }
  if ($runnerText -match 'Where-Object\s+executorPid\s+-eq\s+\$process\.Id') { throw 'retired executor identity must not use a cross-run PID-only tombstone' }
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
Start-Sleep -Seconds 20
"@ | Set-Content -LiteralPath (Join-Path $fixtureScripts 'mock-executor.ps1') -Encoding UTF8
  $configPath = Join-Path $fixtureScripts 'codex-autopilot.config.json'
  [ordered]@{
    repoRoot=$root;autopilotDir=$autoDir;maxIssuesPerRun=1;maxParallelIssues=1;parallelSafetyMode='strict-independent-only';autoMerge=$true;autoPush=$false;maxRunMinutes=30
    issueExecutor=[ordered]@{command='powershell';args=@('-NoProfile','-ExecutionPolicy','Bypass','-File','{repoRoot}\scripts\codex-autopilot\mock-executor.ps1','-RepoRoot','{repoRoot}','-IssueId','{issueId}','-PromptPath','{promptFile}');timeoutSeconds=60;stallInspectSeconds=2;stallTerminateSeconds=4;heartbeatMilliseconds=250;requireChangedFiles=$true;longRunningCommands=@([ordered]@{command='declared-but-not-running';expectedSeconds=601})}
    closeout=[ordered]@{enabled=$true};repair=[ordered]@{enabled=$true;maxRepairAttempts=2};readyPlanner=[ordered]@{enabled=$false}
  } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configPath -Encoding UTF8
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  & git -C $root add .
  & git -C $root commit -qm 'stall base'
  . (Join-Path $scriptDir 'autopilot-progress.ps1')
  $longChild = Start-Process powershell -ArgumentList '-NoProfile','-Command','Start-Sleep -Seconds 30' -PassThru -WindowStyle Hidden
  try {
    Start-Sleep -Seconds 1
    $active = Get-AutopilotActiveLongCommand -RootPid $PID -Declarations @([pscustomobject]@{command='Start-Sleep -Seconds 30';expectedSeconds=601}) -StartedAt ([datetimeoffset]::Now.AddSeconds(-5))
    if (!$active -or $active.processId -ne $longChild.Id) { throw 'declared live descendant command was not recognized' }
    if (Get-AutopilotActiveLongCommand -RootPid $PID -Declarations @([pscustomobject]@{command='*';expectedSeconds=601}) -StartedAt ([datetimeoffset]::Now.AddSeconds(-5))) { throw 'long-command declaration used wildcard matching' }
    $delayed = Get-AutopilotActiveLongCommand -RootPid $PID -Declarations @([pscustomobject]@{command='Start-Sleep -Seconds 30';expectedSeconds=601}) -StartedAt ([datetimeoffset]::Now.AddSeconds(-602))
    if (!$delayed -or $delayed.processId -ne $longChild.Id) { throw 'delayed long command was timed from executor start instead of its own creation time' }
  } finally {
    & taskkill.exe /PID $longChild.Id /T /F 2>$null | Out-Null
  }
  $old = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $configPath -MaxIterations 1 -MaxLoops 1 -ApplyBacklogSplit 2>&1 | Out-String
  $ErrorActionPreference = $old
  $state = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $autoDir 'state.json') -Raw | ConvertFrom-Json
  if ($state.status -ne 'BLOCKED' -or $state.stopReason -ne 'STOP_EXECUTOR_STALL_RETRY_EXHAUSTED') { throw "stalled executor did not stop safely: $output" }
  if ([int](Get-Content -Encoding UTF8 -LiteralPath $counterPath -Raw) -ne 2) { throw 'stalled executor did not use exactly one fresh retry' }
  $events = @(Get-Content -Encoding UTF8 -LiteralPath (Join-Path $autoDir 'events.ndjson') | ForEach-Object { $_ | ConvertFrom-Json })
  $inspectEvents = @($events | Where-Object event -eq 'executor.stall.inspect')
  $retireEvents = @($events | Where-Object event -eq 'executor.stall.retire')
  if ($inspectEvents.Count -lt 2 -or $retireEvents.Count -ne 2) { throw "inspect/retire evidence is incomplete: inspect=$($inspectEvents.Count), retire=$($retireEvents.Count), events=$($events.event -join ',')" }
  if (@($inspectEvents | Group-Object { "$($_.executorPid)|$($_.lastProgressAt)" } | Where-Object Count -gt 1).Count -gt 0) { throw 'inspect was written more than once for the same idle window' }
  if (@($retireEvents.executorPid | Select-Object -Unique).Count -ne 2) { throw 'repair reused a retired executorPid' }
  foreach ($event in @($inspectEvents + $retireEvents)) {
    foreach ($name in 'issueId','task','executorPid','startedAt','lastProgressAt','retryCount','timeoutReason','retiredAt','retiredStatus') {
      if ($event.PSObject.Properties.Name -notcontains $name) { throw "stall evidence missing $name" }
    }
  }
  if (@($state.retiredExecutors).Count -ne 2 -or [int]$state.retryCount -ne 1) { throw 'retired executor state was not persisted' }
  $repairContext = Get-ChildItem -LiteralPath (Join-Path $autoDir 'runs') -Recurse -Filter context.json | Where-Object FullName -match 'repair-1' | Select-Object -First 1
  $context = Get-Content -Encoding UTF8 -LiteralPath $repairContext.FullName -Raw | ConvertFrom-Json
  if ($context.phase -ne 'repair' -or !$context.previousPhaseSummary -or @($context.longRunningCommands).Count -ne 1) { throw 'repair context did not carry narrowed scope, timeout reason, and long-command declaration' }
  $blocked = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $backlog 'blocked-issues.md') -Raw
  if ($blocked -notmatch '两个 executorPid 永久退役' -or $blocked -notmatch '未完成验收项') { throw 'blocked backlog closeout is incomplete' }
  Write-Host 'executor stall self-test passed'
} finally {
  if (Test-Path -LiteralPath (Join-Path $root '.git')) { & git -C $root worktree prune 2>$null | Out-Null }
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
