param()
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$runner=Join-Path $scriptDir 'autopilot-run-continuous.ps1'
$root=Join-Path ([IO.Path]::GetTempPath()) ('autopilot-final-scope-'+[guid]::NewGuid().ToString('N'))
$backlog=Join-Path $root 'docs\backlog';$scripts=Join-Path $root 'scripts\codex-autopilot';$autoDir=Join-Path $root '.codex-autopilot'
New-Item -ItemType Directory -Path $backlog,$scripts,$autoDir -Force|Out-Null
try{
  $tick=[char]96
  @"
# Ready Issues
### ISSUE-993-001：Final scope canary
任务性质：回归证明
目标：
- Reject validation-generated forbidden files.
非目标：
- No production change.
允许修改：
- ${tick}docs/quality/**${tick}
禁止修改：
- ${tick}deploy/**${tick}
验收标准：
- Final diff is checked.
状态：Ready
来源锚点：${tick}docs/plans/source.md${tick}
验证命令：
- ${tick}powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/generate-forbidden.ps1${tick}
归档报告：${tick}docs/quality/issue-993-001.md${tick}
Migration：不需要
依赖：无
风险等级：低
运行态要求：无
Reviewer要求：不需要
"@|Set-Content (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  '# Done'|Set-Content (Join-Path $backlog 'done-issues.md');'# Focus'|Set-Content (Join-Path $backlog 'current-focus.md');'# Plan'|Set-Content (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md')
  ".worktrees/`r`n.codex-autopilot/"|Set-Content (Join-Path $root '.gitignore');'enabled'|Set-Content (Join-Path $autoDir 'enabled.flag')
  "param([string]`$RepoRoot)`nNew-Item -ItemType Directory -Path (Join-Path `$RepoRoot 'docs\quality') -Force|Out-Null`n'ok'|Set-Content (Join-Path `$RepoRoot 'docs\quality\issue-993-001.md')"|Set-Content (Join-Path $scripts 'mock.ps1')
  "New-Item -ItemType Directory -Path deploy -Force|Out-Null`n'generated'|Set-Content deploy/generated.txt`nexit 0"|Set-Content (Join-Path $scripts 'generate-forbidden.ps1')
  $config=Join-Path $scripts 'config.json';[ordered]@{repoRoot=$root;autopilotDir=$autoDir;maxIssuesPerRun=1;maxParallelIssues=1;parallelSafetyMode='strict-independent-only';autoMerge=$true;autoPush=$false;maxRunMinutes=30;issueExecutor=[ordered]@{command='powershell';args=@('-NoProfile','-File','{repoRoot}\scripts\codex-autopilot\mock.ps1','-RepoRoot','{repoRoot}');timeoutSeconds=30;requireChangedFiles=$true};closeout=[ordered]@{enabled=$true};repair=[ordered]@{enabled=$false};readyPlanner=[ordered]@{enabled=$false}}|ConvertTo-Json -Depth 8|Set-Content $config
  & git -C $root init -q;& git -C $root config user.email a@b.c;& git -C $root config user.name test;& git -C $root add .;& git -C $root commit -qm base
  $old=$ErrorActionPreference;$ErrorActionPreference='Continue';$output=& powershell -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $config -MaxIterations 1 -MaxLoops 1 -ApplyBacklogSplit 2>&1|Out-String;$code=$LASTEXITCODE;$ErrorActionPreference=$old
  if($code -ne 0){throw "runner failed: $output"}
  $result=Get-ChildItem (Join-Path $autoDir 'runs') -Filter result.json -Recurse|Select-Object -First 1|ForEach-Object{Get-Content $_.FullName -Raw|ConvertFrom-Json}
  if($result.status -ne 'blocked' -or $result.stopReason -ne 'STOP_SCOPE_VIOLATION'){throw 'final scope violation was not blocked'}
  Write-Host 'final scope gate self-test passed'
}finally{if(Test-Path (Join-Path $root '.git')){& git -C $root worktree prune 2>$null|Out-Null};Remove-Item $root -Recurse -Force -ErrorAction SilentlyContinue}
