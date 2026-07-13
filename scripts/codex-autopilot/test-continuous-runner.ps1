param()

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Runner = Join-Path $ScriptDir "autopilot-run-continuous.ps1"
$StartScript = Join-Path $ScriptDir "autopilot-start.ps1"
$Executor = Join-Path $ScriptDir "autopilot-exec-issue.ps1"
$StatusScript = Join-Path $ScriptDir "autopilot-status.ps1"
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$CheckpointScript = Join-Path $RepoRoot "plugins\cgc-pms-autopilot\scripts\autopilot-checkpoint.ps1"
$KillScript = Join-Path $ScriptDir "autopilot-kill.ps1"
$ReadinessScript = Join-Path $ScriptDir "autopilot-readiness-check.ps1"
$TempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("cgc-pms-autopilot-runner-test-" + [guid]::NewGuid().ToString("N"))

function New-Fixture {
  param(
    [string]$Name,
    [string]$Ready,
    [string]$Plan,
    [string]$Focus = @"
# Current Focus

允许进入下一阶段的候选范围：
- P2 报表中心、规则治理中心、通知平台、WBS / 进度计划 / 甘特图、供应商评分 / 采购增强。
"@,
    [int]$MaxIssuesPerRun = 3,
    [int]$MaxParallelIssues = 3,
    [string]$ParallelSafetyMode = "strict-independent-only",
    [string]$ExecutorMode = "success",
    [string]$CurrentIssues = '',
    [switch]$Enabled,
    [switch]$SkipStrictDefaults
  )

  if (!$SkipStrictDefaults) {
    $Ready = $Ready -replace '(?m)^目标：', "任务性质：缺口修复`r`n目标："
    $Ready = $Ready -replace '(?m)^允许修改：', "非目标：`r`n- No production or unrelated change.`r`n允许修改："
    $Ready = $Ready -replace '(?m)^状态：', "Migration：不需要`r`n依赖：无`r`n风险等级：低`r`n运行态要求：无`r`nReviewer要求：不需要`r`n状态："
  }

  $Root = Join-Path $TempRoot $Name
  $AutoDir = Join-Path $Root ".codex-autopilot"
  $BacklogDir = Join-Path $Root "docs\backlog"
  $ScriptDir = Join-Path $Root "scripts\codex-autopilot"
  $BackendDir = Join-Path $Root "backend"
  $FrontendDir = Join-Path $Root "frontend-admin"
  $QualityDir = Join-Path $Root "docs\quality"
  New-Item -ItemType Directory -Path $AutoDir, $BacklogDir, $ScriptDir, $BackendDir, $FrontendDir, $QualityDir -Force | Out-Null
  "" | Out-File -Encoding ascii (Join-Path $BackendDir "mvnw.cmd")
  "{}" | Out-File -Encoding utf8 (Join-Path $FrontendDir "package.json")
  if ($Enabled) {
    "enabled" | Out-File -Encoding utf8 (Join-Path $AutoDir "enabled.flag")
  }
  @"
{
  "repoRoot": "$($Root -replace '\\', '\\')",
  "autopilotDir": "$(($AutoDir) -replace '\\', '\\')",
  "maxIssuesPerRun": $MaxIssuesPerRun,
  "maxParallelIssues": $MaxParallelIssues,
  "parallelSafetyMode": "$ParallelSafetyMode",
  "autoPush": false,
  "issueExecutor": {
    "command": "powershell",
    "args": [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      "{repoRoot}\\scripts\\codex-autopilot\\mock-issue-executor.ps1",
      "-RepoRoot",
      "{repoRoot}",
      "-IssueId",
      "{issueId}",
      "-PromptPath",
      "{promptFile}"
    ],
    "timeoutSeconds": 30,
    "requireChangedFiles": true
  }
}
"@ | Out-File -Encoding utf8 (Join-Path $ScriptDir "codex-autopilot.config.json")
  @"
param(
  [string]`$RepoRoot,
  [string]`$IssueId,
  [string]`$PromptPath
)
if ("$ExecutorMode" -eq "missing") {
  exit 9
}
if ("$ExecutorMode" -eq "fail") {
  Write-Host "mock executor failed for `$IssueId"
  exit 7
}
if ("$ExecutorMode" -ne "no-change") {
  New-Item -ItemType Directory -Path (Join-Path `$RepoRoot "docs\quality") -Force | Out-Null
  "executed `$IssueId with `$PromptPath" | Out-File -Encoding utf8 (Join-Path `$RepoRoot "docs\quality\mock-execution.txt")
}
Write-Host "mock executor completed for `$IssueId"
"@ | Out-File -Encoding utf8 (Join-Path $ScriptDir "mock-issue-executor.ps1")
  $Ready | Out-File -Encoding utf8 (Join-Path $BacklogDir "ready-issues.md")
  $Focus | Out-File -Encoding utf8 (Join-Path $BacklogDir "current-focus.md")
  $Plan | Out-File -Encoding utf8 (Join-Path $BacklogDir "cgc-pms-production-enhancement-plan.md")
  if (!$CurrentIssues) {
    $CurrentIssues = '{"schemaVersion":1,"versionScope":"v1.5","updatedAt":"2026-07-13T14:00:00+08:00","issues":[]}'
  }
  $CurrentIssues | Out-File -Encoding utf8 (Join-Path $BacklogDir "current-issues.json")
  ".worktrees/`r`n.codex-autopilot/" | Out-File -Encoding utf8 (Join-Path $Root '.gitignore')
  & git -C $Root init -q 2>$null
  & git -C $Root config user.email 'autopilot@test.local'
  & git -C $Root config user.name 'AutoPilot Test'
  & git -C $Root add .
  & git -C $Root commit -qm 'fixture'
  return $Root
}

function Invoke-Runner {
  param(
    [string]$Root,
    [switch]$Apply,
    [switch]$Explain,
    [object]$MaxIterations = $null
  )

  $Config = Join-Path $Root "scripts\codex-autopilot\codex-autopilot.config.json"
  $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $Runner, "-RepoRoot", $Root, "-ConfigPath", $Config, "-MaxLoops", "3")
  if ($null -ne $MaxIterations) {
    $args += @("-MaxIterations", $MaxIterations)
  }
  if ($Explain) {
    $args += "-ExplainNextAction"
  } elseif ($Apply) {
    $args += "-ApplyBacklogSplit"
  } else {
    $args += "-DryRun"
  }
  $oldErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    & powershell @args 2>&1 | Out-String
  } finally {
    $ErrorActionPreference = $oldErrorActionPreference
  }
}

function Invoke-Executor {
  param(
    [string]$Root,
    [string]$IssueTitle,
    [switch]$DryRun,
    [switch]$Noop
  )

  $Config = Join-Path $Root "scripts\codex-autopilot\codex-autopilot.config.json"
  $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $Executor, "-RepoRoot", $Root, "-ConfigPath", $Config, "-Title", $IssueTitle)
  if ($DryRun) {
    $args += "-DryRun"
  }
  if ($Noop) {
    $args += "-Noop"
  }
  & powershell @args 2>&1 | Out-String
}

function Invoke-Readiness {
  param(
    [string]$Root,
    [string]$Config = ""
  )

  if (!$Config) {
    $Config = Join-Path $Root "scripts\codex-autopilot\codex-autopilot.config.json"
  }
  & powershell -NoProfile -ExecutionPolicy Bypass -File $ReadinessScript -RepoRoot $Root -ConfigPath $Config -AllowStopped 2>&1 | Out-String
}

function Set-IssueStatus {
  param(
    [string]$Root,
    [string]$Status
  )

  $ReadyPath = Join-Path $Root "docs\backlog\ready-issues.md"
  $text = Get-Content -Encoding UTF8 -Raw $ReadyPath
  $text = $text -replace "(?m)^状态[：:].*$", "状态：$Status"
  $text | Out-File -Encoding utf8 $ReadyPath
}

function Write-TestRunLock {
  param(
    [string]$Root,
    [int]$ProcessId,
    [string]$HeartbeatAt
  )

  [pscustomobject]@{
    owner = "test"
    pid = $ProcessId
    startedAt = $HeartbeatAt
    heartbeatAt = $HeartbeatAt
    mode = "apply-backlog-split"
    issueId = "ISSUE-TEST"
  } | ConvertTo-Json | Out-File -Encoding utf8 (Join-Path $Root ".codex-autopilot\run.lock")
}

function Add-MockRunResult {
  param(
    [string]$Root,
    [string]$RunId,
    [string]$IssueId,
    [string]$Title,
    [string]$Status,
    [string]$CreatedAt
  )

  $runDir = Join-Path $Root ".codex-autopilot\runs\$RunId"
  New-Item -ItemType Directory -Path $runDir -Force | Out-Null
  [ordered]@{
    issueId = $IssueId
    title = $Title
    status = $Status
    failureCategory = "none"
    artifacts = @()
    gitSummary = @{
      branch = "master"
      isClean = $false
      statusShort = @()
    }
    validation = @()
    nextAction = "VALIDATE_AND_MERGE"
    stopReason = ""
    createdAt = $CreatedAt
  } | ConvertTo-Json -Depth 8 | Out-File -Encoding utf8 (Join-Path $runDir "result.json")
}

function Assert-Contains {
  param([string]$Text, [string]$Expected)

  if (!$Text.Contains($Expected)) {
    throw "Expected output to contain '$Expected'. Actual:`n$Text"
  }
}

function Assert-NotContains {
  param([string]$Text, [string]$Unexpected)

  if ($Text.Contains($Unexpected)) {
    throw "Expected output not to contain '$Unexpected'. Actual:`n$Text"
  }
}

function Assert-ResultSchema {
  param([object]$Result)

  foreach ($field in @("issueId", "title", "status", "failureCategory", "artifacts", "gitSummary", "validation", "nextAction", "stopReason", "createdAt")) {
    if ($Result.PSObject.Properties.Name -notcontains $field) {
      throw "Missing result.json field: $field"
    }
  }
  if (@("done", "blocked", "failed", "noop") -notcontains $Result.status) {
    throw "Invalid result status: $($Result.status)"
  }
  if (@("none", "tool_config", "environment", "quality_security", "ready_issue_config", "execution_disabled") -notcontains $Result.failureCategory) {
    throw "Invalid failureCategory: $($Result.failureCategory)"
  }
}

function Get-LatestRunDir {
  param([string]$Root)

  $runDir = Get-ChildItem -Path (Join-Path $Root ".codex-autopilot\runs") -Directory |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (!$runDir) {
    throw "Expected latest run directory to exist"
  }
  return $runDir
}

function Get-RunEvents {
  param([string]$Root)

  $eventPath = Join-Path (Get-LatestRunDir $Root).FullName "events.jsonl"
  if (!(Test-Path $eventPath)) {
    throw "Expected events.jsonl to exist"
  }
  return @(Get-Content -Encoding UTF8 -LiteralPath $eventPath | ForEach-Object { $_ | ConvertFrom-Json })
}

function Assert-EventSchema {
  param([object]$Event)

  foreach ($field in @("runId", "timestamp", "event", "mode", "issueId", "title", "checkpoint", "decision", "status", "stopReason", "dryRun", "apply", "maxIterations", "from", "to", "reason", "evidencePath")) {
    if ($Event.PSObject.Properties.Name -notcontains $field) {
      throw "Missing events.jsonl field: $field"
    }
  }
}

function Get-LatestResult {
  param([string]$Root)

  $resultPath = Get-ChildItem -Path (Join-Path $Root ".codex-autopilot\runs") -Filter result.json -Recurse |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (!$resultPath) {
    throw "Expected result.json to exist"
  }
  return Get-Content -Encoding UTF8 -Raw $resultPath.FullName | ConvertFrom-Json
}

try {
  $DisabledRoot = New-Fixture -Name "disabled" -Ready "# Ready Issues`n" -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $DisabledRoot) "STOP_DISABLED"

  $PauseRoot = New-Fixture -Name "paused" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  "pause" | Out-File -Encoding utf8 (Join-Path $PauseRoot ".codex-autopilot\pause.flag")
  Assert-Contains (Invoke-Runner $PauseRoot) "STOP_PAUSE_FLAG"

  $StopRoot = New-Fixture -Name "stopped" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  "stop" | Out-File -Encoding utf8 (Join-Path $StopRoot ".codex-autopilot\stop.flag")
  Assert-Contains (Invoke-Runner $StopRoot) "STOP_STOP_FLAG"

  $BadLimitRoot = New-Fixture -Name "bad-limit" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n" -MaxParallelIssues 4
  $BadLimitOutput = Invoke-Runner $BadLimitRoot
  Assert-Contains $BadLimitOutput "maxParallelIssues must be between 1 and 3"

  $ReadyRoot = New-Fixture -Name "ready" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Runner ready branch

目标：
- Select this issue.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Can be selected only after lint passes.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
  $ReadyOutput = Invoke-Runner $ReadyRoot
  Assert-Contains $ReadyOutput "READY_ISSUE_FOUND"
  Assert-Contains $ReadyOutput "maxIssuesPerRun=3"
  Assert-Contains $ReadyOutput "maxParallelIssues=3"
  Assert-Contains $ReadyOutput "executorCommand="
  $ReadyEvents = Get-RunEvents $ReadyRoot
  Assert-EventSchema $ReadyEvents[0]
  if (!($ReadyEvents | Where-Object { $_.event -eq "ready-lint" -and $_.status -eq "pass" })) { throw "Expected ready-lint pass event" }
  if (!($ReadyEvents | Where-Object { $_.event -eq "decision" -and $_.selectedIssue -like "ISSUE-100-001*" })) { throw "Expected selectedIssue decision event" }

  $ExecutorDryRunOutput = Invoke-Executor $ReadyRoot "ISSUE-100-001：Runner ready branch" -DryRun
  Assert-Contains $ExecutorDryRunOutput "EXECUTOR_RESULT_WRITTEN"
  $ExecutorDryRunResult = Get-LatestResult $ReadyRoot
  Assert-ResultSchema $ExecutorDryRunResult
  if ($ExecutorDryRunResult.status -ne "noop") { throw "Expected dry-run result status noop" }
  if ($ExecutorDryRunResult.failureCategory -ne "none") { throw "Expected dry-run failureCategory none" }

  $ReadyLimitCompatOutput = Invoke-Runner $ReadyRoot -Apply -MaxIterations 2
  Assert-Contains $ReadyLimitCompatOutput "READY_ISSUE_FOUND"
  Assert-Contains $ReadyLimitCompatOutput "EXECUTOR_RESULT_WRITTEN"
  Assert-NotContains $ReadyLimitCompatOutput "BUSINESS_EXECUTION_DISABLED_M3"
  Assert-Contains $ReadyLimitCompatOutput "iterationLimit=2"
  $ReadyDoneResult = Get-LatestResult $ReadyRoot
  Assert-ResultSchema $ReadyDoneResult
  if ($ReadyDoneResult.status -ne "done") {
    $executorLog = @($ReadyDoneResult.artifacts | Where-Object { $_ -like '*executor.log' } | ForEach-Object { if (Test-Path $_) { Get-Content -Encoding UTF8 -LiteralPath $_ -Raw } }) -join "`n"
    throw "Expected real executor status done. Actual: $($ReadyDoneResult | ConvertTo-Json -Compress -Depth 8) Log: $executorLog"
  }
  if ($ReadyDoneResult.failureCategory -ne "none") { throw "Expected real executor failureCategory none. Actual: $($ReadyDoneResult | ConvertTo-Json -Compress -Depth 8)" }
  $ReadyNoopEvents = Get-RunEvents $ReadyRoot
  if (!($ReadyNoopEvents | Where-Object { $_.event -eq "executor.result" -and $_.status -eq "done" })) { throw "Expected executor result event" }
  $ReadyLimitState = Get-Content -Encoding UTF8 -Raw (Join-Path $ReadyRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($ReadyLimitState.iterationLimit -ne 2) { throw "Expected state.iterationLimit=2" }
  if ($ReadyLimitState.iterationCompleted -ne 0) { throw "Expected state.iterationCompleted=0" }
  if ($ReadyLimitState.remainingIterations -ne 2) { throw "Expected state.remainingIterations=2" }

  $ExplainReadyOutput = Invoke-Runner $ReadyRoot -Explain
  Assert-Contains $ExplainReadyOutput "EXPLAIN_NEXT_ACTION"
  Assert-Contains $ExplainReadyOutput "nextAction=READY_ISSUE"
  Assert-Contains $ExplainReadyOutput "nextReady=ISSUE-100-001"
  Assert-Contains $ExplainReadyOutput "selectedIssue=ISSUE-100-001"
  Assert-Contains $ExplainReadyOutput "shouldSplitBacklog=false"
  Assert-Contains $ExplainReadyOutput "parallelBatchSize=1"

  $ParallelRoot = New-Fixture -Name "parallel-safe" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Docs independent

目标：
- Update docs-only report A.
允许修改：
- ``docs/quality/issue-a.md``
禁止修改：
- 生产发布
验收标准：
- Report A is updated.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-a.md``

### ISSUE-100-002：Frontend independent

目标：
- Update one isolated frontend page.
允许修改：
- ``frontend-admin/src/pages/rules/RulePage.vue``
禁止修改：
- 生产发布
验收标准：
- Page check is updated.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.2 规则治理中心`` 节
验证命令：
- ``cd frontend-admin; pnpm type-check``
归档报告：``docs/quality/issue-b.md``

### ISSUE-100-003：Backend independent

目标：
- Update one isolated backend domain.
允许修改：
- ``backend/src/main/java/com/cgcpms/supplier/SupplierScoreService.java``
禁止修改：
- 生产发布
验收标准：
- Backend check is updated.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.5 供应商评分`` 节
验证命令：
- ``cd backend; .\mvnw.cmd test``
归档报告：``docs/quality/issue-c.md``
"@ -Plan "# Plan`n"
  $ParallelOutput = Invoke-Runner $ParallelRoot -Explain
  Assert-Contains $ParallelOutput "nextAction=READY_ISSUE_BATCH"
  Assert-Contains $ParallelOutput "parallelBatchSize=3"
  Assert-Contains $ParallelOutput "parallelSafetyMode=strict-independent-only"
  Assert-Contains $ParallelOutput "parallelIssue[1]=ISSUE-100-001"
  Assert-Contains $ParallelOutput "parallelIssue[2]=ISSUE-100-002"
  Assert-Contains $ParallelOutput "parallelIssue[3]=ISSUE-100-003"

  $ParallelLimitRoot = New-Fixture -Name "parallel-limit" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：One

目标：
- One.
允许修改：
- ``docs/quality/one.md``
禁止修改：
- 生产发布
验收标准：
- One.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 One`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/one.md``

### ISSUE-100-002：Two

目标：
- Two.
允许修改：
- ``frontend-admin/src/pages/two/TwoPage.vue``
禁止修改：
- 生产发布
验收标准：
- Two.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.2 Two`` 节
验证命令：
- ``cd frontend-admin; pnpm type-check``
归档报告：``docs/quality/two.md``

### ISSUE-100-003：Three

目标：
- Three.
允许修改：
- ``backend/src/main/java/com/cgcpms/three/ThreeService.java``
禁止修改：
- 生产发布
验收标准：
- Three.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.3 Three`` 节
验证命令：
- ``cd backend; .\mvnw.cmd test``
归档报告：``docs/quality/three.md``

### ISSUE-100-004：Four

目标：
- Four.
允许修改：
- ``frontend-admin/src/api/modules/four.ts``
禁止修改：
- 生产发布
验收标准：
- Four.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.4 Four`` 节
验证命令：
- ``cd frontend-admin; pnpm type-check``
归档报告：``docs/quality/four.md``
"@ -Plan "# Plan`n"
  $ParallelLimitOutput = Invoke-Runner $ParallelLimitRoot -Explain
  Assert-Contains $ParallelLimitOutput "nextAction=READY_ISSUE_BATCH"
  Assert-Contains $ParallelLimitOutput "parallelBatchSize=3"
  Assert-NotContains $ParallelLimitOutput "parallelIssue[4]="

  $SharedModuleRoot = New-Fixture -Name "parallel-shared-module" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Report page A

目标：
- Update report page A.
允许修改：
- ``frontend-admin/src/pages/report/A.vue``
禁止修改：
- 生产发布
验收标准：
- A.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 A`` 节
验证命令：
- ``cd frontend-admin; pnpm type-check``
归档报告：``docs/quality/a.md``

### ISSUE-100-002：Report page B

目标：
- Update report page B.
允许修改：
- ``frontend-admin/src/pages/report/B.vue``
禁止修改：
- 生产发布
验收标准：
- B.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 B`` 节
验证命令：
- ``cd frontend-admin; pnpm type-check``
归档报告：``docs/quality/b.md``
"@ -Plan "# Plan`n"
  $SharedModuleOutput = Invoke-Runner $SharedModuleRoot -Explain
  Assert-Contains $SharedModuleOutput "nextAction=READY_ISSUE"
  Assert-Contains $SharedModuleOutput "parallelBatchSize=1"
  Assert-Contains $SharedModuleOutput "parallelDecision=SERIAL_CONFLICT"

  $UncertainRoot = New-Fixture -Name "parallel-uncertain" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Uncertain scope

目标：
- Scope is too broad.
允许修改：
- ``backend/**``
禁止修改：
- 生产发布
验收标准：
- Must fall back to serial.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 Unknown`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/uncertain.md``

### ISSUE-100-002：Other scope

目标：
- Other.
允许修改：
- ``frontend-admin/src/pages/other/Other.vue``
禁止修改：
- 生产发布
验收标准：
- Other.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.2 Other`` 节
验证命令：
- ``cd frontend-admin; pnpm type-check``
归档报告：``docs/quality/other.md``
"@ -Plan "# Plan`n"
  $UncertainOutput = Invoke-Runner $UncertainRoot -Explain
  Assert-Contains $UncertainOutput "nextAction=READY_ISSUE"
  Assert-Contains $UncertainOutput "parallelBatchSize=1"
  Assert-Contains $UncertainOutput "parallelDecision=SERIAL_UNPROVEN_INDEPENDENCE"

  foreach ($NonReadyStatus in @("Draft", "", "Needs Fix")) {
    $NonReadyRoot = New-Fixture -Name ("non-ready-" + ($NonReadyStatus -replace "[^A-Za-z0-9]", "empty")) -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Runner non ready branch

目标：
- Do not select this issue.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Must not be selected.
状态：$NonReadyStatus
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
    $NonReadyOutput = Invoke-Runner $NonReadyRoot -Explain
    Assert-NotContains $NonReadyOutput "nextAction=READY_ISSUE"
    Assert-NotContains $NonReadyOutput "nextReady=ISSUE-100-001"
  }

  $MissingFieldRoot = New-Fixture -Name "ready-missing-field" -Enabled -SkipStrictDefaults -Ready @"
# Ready Issues

### ISSUE-100-001：Runner missing field

状态：Ready
验证命令：
- ``git diff --check``
"@ -Plan "# Plan`n"
  $MissingFieldOutput = Invoke-Runner $MissingFieldRoot -Explain
  Assert-Contains $MissingFieldOutput "nextAction=STOP"
  Assert-Contains $MissingFieldOutput "stopReason=STOP_READY_LINT_FAILED"
  Assert-Contains $MissingFieldOutput "missingGate=ready-lint"
  Assert-Contains $MissingFieldOutput "selectedIssue=ISSUE-100-001"
  Assert-Contains $MissingFieldOutput "目标"

  $ReadyLint = Join-Path $ScriptDir "ready-lint.ps1"
  $ReadyLintOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File $ReadyLint -RepoRoot $ReadyRoot -IssueTitle "ISSUE-100-001：Runner ready branch" 2>&1 | Out-String
  $ReadyLintJson = $ReadyLintOutput | ConvertFrom-Json
  if ($ReadyLintJson.status -ne "pass") { throw "Expected ready-lint status=pass. Actual: $ReadyLintOutput" }
  if ($ReadyLintJson.issueId -ne "ISSUE-100-001") { throw "Expected ready-lint issueId=ISSUE-100-001. Actual: $ReadyLintOutput" }

  $MissingCommandRoot = New-Fixture -Name "ready-missing-command-entry" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Runner missing command entry

目标：
- Lint must reject missing command entries.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Missing command entry is reported.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``cd missing-backend; .\mvnw.cmd test``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
  $MissingCommandOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File $ReadyLint -RepoRoot $MissingCommandRoot -IssueTitle "ISSUE-100-001：Runner missing command entry" 2>&1 | Out-String
  $MissingCommandJson = $MissingCommandOutput | ConvertFrom-Json
  if ($MissingCommandJson.status -ne "fail") { throw "Expected missing command entry to fail. Actual: $MissingCommandOutput" }
  Assert-Contains ($MissingCommandJson.errors -join "`n") "验证命令入口不存在"

  $BadZeroOutput = Invoke-Runner $ReadyRoot -MaxIterations 0
  Assert-Contains $BadZeroOutput "MaxIterations must be a positive integer between 1 and 50"
  $BadOverOutput = Invoke-Runner $ReadyRoot -MaxIterations 51
  Assert-Contains $BadOverOutput "MaxIterations must be a positive integer between 1 and 50"
  $BadTextOutput = Invoke-Runner $ReadyRoot -MaxIterations "abc"
  Assert-Contains $BadTextOutput "MaxIterations"

  $SplitRoot = New-Fixture -Name "split" -Enabled -Ready "# Ready Issues`n" -CurrentIssues '{"schemaVersion":1,"versionScope":"v1.5","updatedAt":"2026-07-13T14:00:00+08:00","issues":[{"issueKey":"OBS-STOCK-001","title":"存量回归缺口","status":"OBSERVATION","classification":"NON_BLOCKING_OBSERVATION","priority":"P1","blocking":false,"summary":"验证既有行为","acceptanceCriteria":"形成可执行验证并完成收口","sourceRefs":["docs/backlog/current-focus.md"]}]}' -Plan @"
# Plan

## 8.1 报表中心
可拆为经营总览、合同履约、成本动态、预警处理、审批效率、导出能力。
"@
  $SplitOutput = Invoke-Runner $SplitRoot
  Assert-Contains $SplitOutput "SPLIT_MODE"
  Assert-Contains $SplitOutput "candidateSource=current-issues.json"
  Assert-Contains $SplitOutput "splitCandidate[1]=[stock:OBS-STOCK-001]"
  Assert-Contains $SplitOutput "DRY_RUN_NO_BACKLOG_WRITE"
  $SplitReadyAfterDryRun = Get-Content -Encoding UTF8 -Raw (Join-Path $SplitRoot "docs\backlog\ready-issues.md")
  Assert-NotContains $SplitReadyAfterDryRun "状态：Ready"
  $SplitEvents = Get-RunEvents $SplitRoot
  if (!($SplitEvents | Where-Object { $_.event -eq "split.dry_run" -and $_.shouldSplitBacklog })) { throw "Expected dry-run split event" }

  $ExplainSplitOutput = Invoke-Runner $SplitRoot -Explain
  Assert-Contains $ExplainSplitOutput "EXPLAIN_NEXT_ACTION"
  Assert-Contains $ExplainSplitOutput "nextAction=SPLIT_BACKLOG"
  Assert-Contains $ExplainSplitOutput "shouldSplitBacklog=true"
  Assert-Contains $ExplainSplitOutput "wouldCreateReadyIssueDrafts=1"
  Assert-Contains $ExplainSplitOutput "candidateSource=current-issues.json"
  $SplitReadyAfterExplain = Get-Content -Encoding UTF8 -Raw (Join-Path $SplitRoot "docs\backlog\ready-issues.md")
  Assert-NotContains $SplitReadyAfterExplain "状态：Ready"
  if (Test-Path (Join-Path $SplitRoot ".codex-autopilot\run.lock")) { throw "ExplainNextAction should not leave run.lock" }

  $SplitDryRunOutput = Invoke-Runner $SplitRoot
  Assert-Contains $SplitDryRunOutput "DRY_RUN_NO_BACKLOG_WRITE"
  if (Test-Path (Join-Path $SplitRoot ".codex-autopilot\run.lock")) { throw "DryRun should not leave run.lock" }

  $ApplyRoot = New-Fixture -Name "apply" -Enabled -Ready "# Ready Issues`n" -CurrentIssues '{"schemaVersion":1,"versionScope":"v1.5","updatedAt":"2026-07-13T14:00:00+08:00","issues":[{"issueKey":"OBS-STOCK-001","title":"存量回归缺口","status":"OBSERVATION","classification":"NON_BLOCKING_OBSERVATION","priority":"P1","blocking":false,"summary":"验证既有行为","acceptanceCriteria":"形成可执行验证并完成收口","sourceRefs":["docs/backlog/current-focus.md"]}]}' -Plan @"
# Plan

## 8. 中期开发计划

### 8.1 报表中心
建设项目经营总览报表。

### 8.2 规则治理中心
建设预警规则版本、启停和回归。
"@
  $ApplyOutput = Invoke-Runner $ApplyRoot -Apply
  Assert-Contains $ApplyOutput "STOP_READY_PLANNER_UNAVAILABLE"
  Assert-NotContains $ApplyOutput "READY_ISSUE_FOUND"
  Assert-NotContains $ApplyOutput "EXECUTOR_RESULT_WRITTEN"
  $ApplyReady = Get-Content -Encoding UTF8 -Raw (Join-Path $ApplyRoot "docs\backlog\ready-issues.md")
  Assert-NotContains $ApplyReady "状态：Ready"
  $ApplyState = Get-Content -Encoding UTF8 -Raw (Join-Path $ApplyRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($ApplyState.mode -ne "continuous-runner") { throw "Expected state.mode=continuous-runner" }
  if ($ApplyState.status -ne "BLOCKED") { throw "Expected missing planner to fail closed" }
  if ($ApplyState.stopReason -ne "STOP_READY_PLANNER_UNAVAILABLE") { throw "Expected planner-unavailable stop reason" }
  if (Test-Path (Join-Path $ApplyRoot ".codex-autopilot\run.lock")) { throw "ApplyBacklogSplit should release run.lock" }
  $refillCommit = (& git -C $ApplyRoot log -1 --pretty=%s).Trim()
  if ($refillCommit -ne 'fixture') { throw 'Missing planner must not create a refill commit' }

  $FailRoot = New-Fixture -Name "executor-fail" -Enabled -ExecutorMode "fail" -Ready @"
# Ready Issues

### ISSUE-100-001：Runner executor fail branch

目标：
- Executor failures must block the run.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Failure is reported.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $FailRoot -Apply) "EXECUTOR_RESULT_WRITTEN"
  $FailResult = Get-LatestResult $FailRoot
  Assert-ResultSchema $FailResult
  if ($FailResult.status -ne "failed") { throw "Expected failed executor status failed" }
  if ($FailResult.failureCategory -ne "quality_security") { throw "Expected failed executor failureCategory quality_security" }

  $NoChangeRoot = New-Fixture -Name "executor-no-change" -Enabled -ExecutorMode "no-change" -Ready @"
# Ready Issues

### ISSUE-100-001：Runner executor no change branch

目标：
- Executor success without artifacts must block the run.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- No-change execution is blocked.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $NoChangeRoot -Apply) "EXECUTOR_RESULT_WRITTEN"
  $NoChangeResult = Get-LatestResult $NoChangeRoot
  Assert-ResultSchema $NoChangeResult
  if ($NoChangeResult.status -ne "blocked") { throw "Expected no-change executor status blocked" }
  if ($NoChangeResult.stopReason -ne "STOP_NO_EXECUTION_ARTIFACTS") { throw "Expected STOP_NO_EXECUTION_ARTIFACTS" }

  $DirtyBeforeRoot = New-Fixture -Name "executor-dirty-before-change" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Runner executor modifies pre-dirty file

目标：
- Executor success should pass when it changes an already dirty business file.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Pre-dirty file content change is treated as execution artifact.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
  "before" | Out-File -Encoding utf8 (Join-Path $DirtyBeforeRoot "docs\quality\mock-execution.txt")
  Assert-Contains (Invoke-Runner $DirtyBeforeRoot -Apply) "EXECUTOR_RESULT_WRITTEN"
  $DirtyBeforeResult = Get-LatestResult $DirtyBeforeRoot
  Assert-ResultSchema $DirtyBeforeResult
  if ($DirtyBeforeResult.status -ne "done") { throw "Expected pre-dirty executor status done. Actual: $($DirtyBeforeResult | ConvertTo-Json -Compress -Depth 8)" }
  if ($DirtyBeforeResult.failureCategory -ne "none") { throw "Expected pre-dirty executor failureCategory none. Actual: $($DirtyBeforeResult | ConvertTo-Json -Compress -Depth 8)" }
  if (!(($DirtyBeforeResult.validation | Where-Object { $_.name -eq "execution-artifacts" }).message -like "*docs/quality/mock-execution.txt*")) {
    throw "Expected isolated executor artifact to be recorded"
  }

  $NoExecutorConfig = Join-Path $TempRoot "no-executor.config.json"
  $ActualConfig = Get-Content -Encoding UTF8 -Raw (Join-Path $ScriptDir "codex-autopilot.config.json") | ConvertFrom-Json
  $ActualConfig.PSObject.Properties.Remove("issueExecutor")
  $ActualConfig | ConvertTo-Json -Depth 8 | Out-File -Encoding utf8 $NoExecutorConfig
  $NoExecutorReadiness = Invoke-Readiness (Split-Path -Parent (Split-Path -Parent $ScriptDir)) $NoExecutorConfig | ConvertFrom-Json
  if ($NoExecutorReadiness.unattendedModeAllowed) { throw "Expected readiness to reject noop-only executor capability" }
  if (!($NoExecutorReadiness.gates | Where-Object { $_.name -eq "executor.realExecution" -and $_.status -eq "fail" })) {
    throw "Expected readiness executor.realExecution gate to fail"
  }

  $ActiveLockRoot = New-Fixture -Name "active-lock" -Enabled -Ready "# Ready Issues`n" -Plan @"
# Plan

### 8.1 报表中心
建设项目经营总览报表。
"@
  Write-TestRunLock $ActiveLockRoot $PID (Get-Date -Format o)
  $ActiveLockOutput = Invoke-Runner $ActiveLockRoot -Apply
  Assert-Contains $ActiveLockOutput "RUN_LOCK_ACTIVE"
  Assert-Contains $ActiveLockOutput "Another AutoPilot run is active"
  if (!(Test-Path (Join-Path $ActiveLockRoot ".codex-autopilot\run.lock"))) { throw "Active run.lock should be kept" }
  $ActiveLockStatus = & powershell -NoProfile -ExecutionPolicy Bypass -File $StatusScript -Repo $ActiveLockRoot 2>&1 | Out-String
  $ActiveLockStatusJson = $ActiveLockStatus | ConvertFrom-Json
  if (!$ActiveLockStatusJson.lockExists) { throw "Expected status to report lockExists=true" }
  if ($ActiveLockStatusJson.lockStale) { throw "Expected active lock not to be stale" }

  $StaleLockRoot = New-Fixture -Name "stale-lock" -Enabled -Ready "# Ready Issues`n" -Plan @"
# Plan

### 8.1 报表中心
建设项目经营总览报表。
"@
  Write-TestRunLock $StaleLockRoot 999999 ((Get-Date).AddMinutes(-180).ToString("o"))
  $StaleLockOutput = Invoke-Runner $StaleLockRoot -Apply
  Assert-Contains $StaleLockOutput "STALE_RUN_LOCK_REMOVED"
  Assert-Contains $StaleLockOutput "RUN_LOCK_ACQUIRED"
  Assert-Contains $StaleLockOutput "RUN_LOCK_RELEASED"
  if (Test-Path (Join-Path $StaleLockRoot ".codex-autopilot\run.lock")) { throw "Stale run.lock should be replaced and released" }

  $KillStaleRoot = New-Fixture -Name "kill-stale-lock" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  Write-TestRunLock $KillStaleRoot 999999 ((Get-Date).AddMinutes(-180).ToString("o"))
  $KillStaleOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File $KillScript -Repo $KillStaleRoot 2>&1 | Out-String
  Assert-Contains $KillStaleOutput "run.lock removed."
  if (Test-Path (Join-Path $KillStaleRoot ".codex-autopilot\run.lock")) { throw "kill should remove stale run.lock" }

  $KillActiveRoot = New-Fixture -Name "kill-active-lock" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  Write-TestRunLock $KillActiveRoot $PID (Get-Date -Format o)
  $KillActiveOutput = & powershell -NoProfile -ExecutionPolicy Bypass -File $KillScript -Repo $KillActiveRoot 2>&1 | Out-String
  Assert-Contains $KillActiveOutput "Active run.lock kept"
  if (!(Test-Path (Join-Path $KillActiveRoot ".codex-autopilot\run.lock"))) { throw "kill without ForceKill should keep active run.lock" }

  $LimitOneRoot = New-Fixture -Name "limit-one" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Runner limit one

目标：
- Count this issue.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Can be selected only after lint passes.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $LimitOneRoot -Apply -MaxIterations 1) "READY_ISSUE_FOUND"
  Set-IssueStatus $LimitOneRoot "Done"
  $LimitOnePreState = Get-Content -Encoding UTF8 -Raw (Join-Path $LimitOneRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($LimitOnePreState.worktree -and (Test-Path $LimitOnePreState.worktree)) { & git -C $LimitOneRoot worktree remove --force $LimitOnePreState.worktree 2>$null | Out-Null }
  $LimitOnePreState.worktree = ''
  $LimitOnePreState | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $LimitOneRoot ".codex-autopilot\state.json") -Encoding UTF8
  $LimitOneDoneOutput = Invoke-Runner $LimitOneRoot -Apply -MaxIterations 1
  Assert-Contains $LimitOneDoneOutput "STOP_ITERATION_LIMIT_REACHED"
  $LimitOneState = Get-Content -Encoding UTF8 -Raw (Join-Path $LimitOneRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($LimitOneState.iterationCompleted -ne 1) { throw "Expected MaxIterations=1 to count one completed issue. Actual: $($LimitOneState | ConvertTo-Json -Compress -Depth 8)" }
  if ($LimitOneState.remainingIterations -ne 0) { throw "Expected MaxIterations=1 remainingIterations=0" }
  if ($LimitOneState.stopReason -ne "STOP_ITERATION_LIMIT_REACHED") { throw "Expected STOP_ITERATION_LIMIT_REACHED stopReason" }
  if (Test-Path (Join-Path $LimitOneRoot ".codex-autopilot\enabled.flag")) { throw "Iteration limit must disable future dispatch" }
  $LimitCheckpoint = & powershell -NoProfile -ExecutionPolicy Bypass -File $CheckpointScript -RepoRoot $LimitOneRoot -AsJson | ConvertFrom-Json
  if ($LimitCheckpoint.decision -ne 'limit_reached') { throw "Checkpoint must report limit_reached, got $($LimitCheckpoint.decision)" }
  $LimitStatus = & powershell -NoProfile -ExecutionPolicy Bypass -File $StatusScript -Repo $LimitOneRoot | ConvertFrom-Json
  if ($LimitStatus.lastIssue -ne $LimitOneState.iterationLastCountedIssue) { throw "Status must expose iterationLastCountedIssue as lastIssue" }
  if ($LimitStatus.recoveryAction -ne 'NONE') { throw "Terminal limit state must not advertise NEW_RUN recovery" }

  $LimitTwoRoot = New-Fixture -Name "limit-two" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Runner limit two

目标：
- Count this issue.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Can be selected only after lint passes.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-100-001.md``
"@ -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $LimitTwoRoot -Apply -MaxIterations 2) "READY_ISSUE_FOUND"
  Set-IssueStatus $LimitTwoRoot "Blocked"
  $LimitTwoPreState = Get-Content -Encoding UTF8 -Raw (Join-Path $LimitTwoRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($LimitTwoPreState.worktree -and (Test-Path $LimitTwoPreState.worktree)) { & git -C $LimitTwoRoot worktree remove --force $LimitTwoPreState.worktree 2>$null | Out-Null }
  $LimitTwoPreState.worktree = ''
  $LimitTwoPreState | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $LimitTwoRoot ".codex-autopilot\state.json") -Encoding UTF8
  $LimitTwoDoneOutput = Invoke-Runner $LimitTwoRoot -Apply -MaxIterations 2
  Assert-Contains $LimitTwoDoneOutput "STOP_CURRENT_ISSUE_BLOCKED"
  Assert-NotContains $LimitTwoDoneOutput "STOP_ITERATION_LIMIT_REACHED"
  $LimitTwoState = Get-Content -Encoding UTF8 -Raw (Join-Path $LimitTwoRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($LimitTwoState.iterationCompleted -ne 1) { throw "Expected the prior done executor result to consume one completion quota" }
  if ($LimitTwoState.remainingIterations -ne 1) { throw "Expected MaxIterations=2 remainingIterations=1" }

  $RepairRoot = New-Fixture -Name "repair-undercount" -Enabled -Ready @"
# Ready Issues

### ISSUE-008-009：Runner repair target

目标：
- Must not dispatch after three completed issues were already counted from runs.
允许修改：
- ``docs/quality/**``
禁止修改：
- 生产发布
验收标准：
- Runner should stop at iteration limit before selecting this issue.
状态：Ready
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``8.1 报表中心`` 节
验证命令：
- ``git diff --check``
归档报告：``docs/quality/issue-008-009.md``
"@ -Plan "# Plan`n"
  $RepairStartedAt = "2026-07-09T13:29:13"
  @"
{
  "enabled": false,
  "startedAt": "$RepairStartedAt",
  "stopRequested": true,
  "autoMerge": true,
  "autoPush": false,
  "allowTestDataReset": true,
  "status": "STOPPING",
  "mode": "continuous-runner",
  "lastAction": "READY_ISSUE_FOUND",
  "lastIssue": "ISSUE-008-008：WBS、进度计划与甘特图 最小可行回归",
  "lastReason": "READY_ISSUE_FOUND",
  "stopReason": "",
  "iterationLimit": 3,
  "iterationCompleted": 1,
  "remainingIterations": 2,
  "iterationLastCountedIssue": "ISSUE-008-006：规则治理中心 最小可行回归",
  "lastHeartbeatAt": "2026-07-09T14:01:35"
}
"@ | Out-File -Encoding utf8 (Join-Path $RepairRoot ".codex-autopilot\state.json")
  Add-MockRunResult $RepairRoot "20260709-133500-001" "ISSUE-008-006" "ISSUE-008-006：规则治理中心 最小可行回归" "done" "2026-07-09T13:35:00"
  Add-MockRunResult $RepairRoot "20260709-134500-002" "ISSUE-008-007" "ISSUE-008-007：通知平台 最小可行回归" "done" "2026-07-09T13:45:00"
  Add-MockRunResult $RepairRoot "20260709-135500-003" "ISSUE-008-008" "ISSUE-008-008：WBS、进度计划与甘特图 最小可行回归" "done" "2026-07-09T13:55:00"
  Remove-Item -LiteralPath (Join-Path $RepairRoot ".codex-autopilot\stop.flag") -ErrorAction SilentlyContinue
  "enabled" | Out-File -Encoding utf8 (Join-Path $RepairRoot ".codex-autopilot\enabled.flag")
  $RepairOutput = Invoke-Runner $RepairRoot -Apply -MaxIterations 3
  Assert-Contains $RepairOutput "iterationCompleted=3"
  Assert-Contains $RepairOutput "remainingIterations=0"
  Assert-Contains $RepairOutput "STOP_ITERATION_LIMIT_REACHED"
  Assert-NotContains $RepairOutput "READY_ISSUE_FOUND"
  $RepairState = Get-Content -Encoding UTF8 -Raw (Join-Path $RepairRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($RepairState.iterationCompleted -ne 3) { throw "Expected repaired state.iterationCompleted=3" }
  if ($RepairState.remainingIterations -ne 0) { throw "Expected repaired state.remainingIterations=0" }

  $StartStateRoot = New-Fixture -Name "start-preserve" -Ready "# Ready Issues`n" -Plan "# Plan`n"
  @"
{
  "enabled": false,
  "startedAt": "2026-07-09T13:29:13",
  "stopRequested": true,
  "status": "STOPPING",
  "mode": "continuous-runner",
  "iterationLimit": 3,
  "iterationCompleted": 2,
  "remainingIterations": 1,
  "iterationLastCountedIssue": "ISSUE-008-007：通知平台 最小可行回归"
}
"@ | Out-File -Encoding utf8 (Join-Path $StartStateRoot ".codex-autopilot\state.json")
  & powershell -NoProfile -ExecutionPolicy Bypass -File $StartScript -Repo $StartStateRoot 2>&1 | Out-String | Out-Null
  $StartState = Get-Content -Encoding UTF8 -Raw (Join-Path $StartStateRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($StartState.iterationCompleted -ne 2) { throw "Expected start to preserve iterationCompleted" }
  if ($StartState.remainingIterations -ne 1) { throw "Expected start to preserve remainingIterations" }
  if ($StartState.startedAt -ne "2026-07-09T13:29:13") { throw "Expected start to preserve startedAt" }

  $EmptyRoot = New-Fixture -Name "empty" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  $EmptyOutput = Invoke-Runner $EmptyRoot
  Assert-Contains $EmptyOutput "STOP_READY_AND_POOL_EMPTY"
  if (Test-Path (Join-Path $EmptyRoot ".codex-autopilot\state.json")) { throw "Dry-run should not write state.json" }
  $EmptyApplyOutput = Invoke-Runner $EmptyRoot -Apply
  Assert-Contains $EmptyApplyOutput "STOP_READY_AND_POOL_EMPTY"
  $EmptyState = Get-Content -Encoding UTF8 -Raw (Join-Path $EmptyRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($EmptyState.stopReason -ne "STOP_READY_AND_POOL_EMPTY") { throw "Expected state.stopReason=STOP_READY_AND_POOL_EMPTY" }

  Write-Host "continuous runner self-test passed"
} finally {
  Remove-Item -LiteralPath $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
