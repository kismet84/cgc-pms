param()
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$runner=Join-Path $scriptDir 'autopilot-run-continuous.ps1'
$root=Join-Path ([IO.Path]::GetTempPath()) ('autopilot-count-'+[guid]::NewGuid().ToString('N'))
$backlog=Join-Path $root 'docs\backlog';$autoDir=Join-Path $root '.codex-autopilot';$scripts=Join-Path $root 'scripts\codex-autopilot';$runDir=Join-Path $autoDir 'runs\blocked-run'
New-Item -ItemType Directory -Path $backlog,$scripts,$runDir -Force|Out-Null
try{
  '# Ready Issues'|Set-Content (Join-Path $backlog 'ready-issues.md');'# Focus'|Set-Content (Join-Path $backlog 'current-focus.md');'# Plan'|Set-Content (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md')
  ".worktrees/`r`n.codex-autopilot/"|Set-Content (Join-Path $root '.gitignore')
  $started=[datetimeoffset]::Now.AddMinutes(-5).ToString('o')
  [ordered]@{schemaVersion=2;runId='old';status='CHECKPOINT';phase='checkpoint';currentIssue='';attempt=0;startedAt=$started;phaseStartedAt=$started;lastHeartbeatAt=$started;iterationLimit=2;completedImplementationIssues=0;completedIssueIds=@();worktree='';branch='master';executorPid=$null;lastCommit=$null;failureFingerprint=$null;iterationCompleted=0;iterationLastCountedIssue=''}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $autoDir 'state.json')
  [ordered]@{issueId='ISSUE-1';title='ISSUE-1：Blocked';status='blocked';createdAt=[datetimeoffset]::Now.ToString('o')}|ConvertTo-Json|Set-Content (Join-Path $runDir 'result.json')
  $config=Join-Path $scripts 'config.json';[ordered]@{repoRoot=$root;autopilotDir=$autoDir;baseBranch='master';maxIssuesPerRun=1;maxParallelIssues=1;parallelSafetyMode='strict-independent-only';autoPush=$false;readyPlanner=[ordered]@{enabled=$false}}|ConvertTo-Json -Depth 5|Set-Content $config
  & git -C $root init -q;& git -C $root config user.email a@b.c;& git -C $root config user.name test;& git -C $root add .;& git -C $root commit -qm base
  $output=& powershell -NoProfile -ExecutionPolicy Bypass -File $runner -RepoRoot $root -ConfigPath $config -MaxIterations 2 -MaxLoops 1 -ExplainNextAction 2>&1|Out-String
  if($output -notmatch 'iterationCompleted=0' -or $output -notmatch 'remainingIterations=2'){throw "blocked result consumed completion quota: $output"}
  Write-Host 'completion accounting self-test passed'
}finally{Remove-Item $root -Recurse -Force -ErrorAction SilentlyContinue}
