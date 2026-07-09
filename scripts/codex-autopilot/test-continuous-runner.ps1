param()

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Runner = Join-Path $ScriptDir "autopilot-run-continuous.ps1"
$Executor = Join-Path $ScriptDir "autopilot-exec-issue.ps1"
$StatusScript = Join-Path $ScriptDir "autopilot-status.ps1"
$KillScript = Join-Path $ScriptDir "autopilot-kill.ps1"
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
    [int]$MaxIssuesPerRun = 1,
    [switch]$Enabled
  )

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
  "autoPush": false
}
"@ | Out-File -Encoding utf8 (Join-Path $ScriptDir "codex-autopilot.config.json")
  $Ready | Out-File -Encoding utf8 (Join-Path $BacklogDir "ready-issues.md")
  $Focus | Out-File -Encoding utf8 (Join-Path $BacklogDir "current-focus.md")
  $Plan | Out-File -Encoding utf8 (Join-Path $BacklogDir "cgc-pms-production-enhancement-plan.md")
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

function Set-IssueStatus {
  param(
    [string]$Root,
    [string]$Status
  )

  $ReadyPath = Join-Path $Root "docs\backlog\ready-issues.md"
  $text = Get-Content -Raw $ReadyPath
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

function Assert-Contains {
  param([string]$Text, [string]$Expected)

  if ($Text -notlike "*$Expected*") {
    throw "Expected output to contain '$Expected'. Actual:`n$Text"
  }
}

function Assert-NotContains {
  param([string]$Text, [string]$Unexpected)

  if ($Text -like "*$Unexpected*") {
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
  return @(Get-Content -LiteralPath $eventPath | ForEach-Object { $_ | ConvertFrom-Json })
}

function Assert-EventSchema {
  param([object]$Event)

  foreach ($field in @("timestamp", "event", "mode", "issueId", "title", "checkpoint", "decision", "status", "stopReason", "dryRun", "apply", "maxIterations")) {
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
  return Get-Content -Raw $resultPath.FullName | ConvertFrom-Json
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

  $BadLimitRoot = New-Fixture -Name "bad-limit" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n" -MaxIssuesPerRun 2
  $BadLimitOutput = Invoke-Runner $BadLimitRoot
  Assert-Contains $BadLimitOutput "Continuous runner requires maxIssuesPerRun=1"

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
  Assert-Contains $ReadyOutput "maxIssuesPerRun=1"
  Assert-Contains $ReadyOutput "BUSINESS_EXECUTION_DISABLED_M3"
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
  if ($ExecutorDryRunResult.failureCategory -ne "execution_disabled") { throw "Expected execution_disabled failureCategory" }

  $ReadyLimitCompatOutput = Invoke-Runner $ReadyRoot -Apply -MaxIterations 2
  Assert-Contains $ReadyLimitCompatOutput "READY_ISSUE_FOUND"
  Assert-Contains $ReadyLimitCompatOutput "EXECUTOR_RESULT_WRITTEN"
  Assert-Contains $ReadyLimitCompatOutput "iterationLimit=2"
  $ReadyNoopResult = Get-LatestResult $ReadyRoot
  Assert-ResultSchema $ReadyNoopResult
  $ReadyNoopEvents = Get-RunEvents $ReadyRoot
  if (!($ReadyNoopEvents | Where-Object { $_.event -eq "executor.result" -and $_.status -eq "noop" })) { throw "Expected executor result event" }
  $ReadyLimitState = Get-Content -Raw (Join-Path $ReadyRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($ReadyLimitState.iterationLimit -ne 2) { throw "Expected state.iterationLimit=2" }
  if ($ReadyLimitState.iterationCompleted -ne 0) { throw "Expected state.iterationCompleted=0" }
  if ($ReadyLimitState.remainingIterations -ne 2) { throw "Expected state.remainingIterations=2" }

  $ExplainReadyOutput = Invoke-Runner $ReadyRoot -Explain
  Assert-Contains $ExplainReadyOutput "EXPLAIN_NEXT_ACTION"
  Assert-Contains $ExplainReadyOutput "nextAction=READY_ISSUE"
  Assert-Contains $ExplainReadyOutput "nextReady=ISSUE-100-001"
  Assert-Contains $ExplainReadyOutput "selectedIssue=ISSUE-100-001"
  Assert-Contains $ExplainReadyOutput "shouldSplitBacklog=false"

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

  $MissingFieldRoot = New-Fixture -Name "ready-missing-field" -Enabled -Ready @"
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

  $SplitRoot = New-Fixture -Name "split" -Enabled -Ready "# Ready Issues`n" -Plan @"
# Plan

## 8.1 报表中心
可拆为经营总览、合同履约、成本动态、预警处理、审批效率、导出能力。
"@
  $SplitOutput = Invoke-Runner $SplitRoot
  Assert-Contains $SplitOutput "SPLIT_MODE"
  Assert-Contains $SplitOutput "DRY_RUN_NO_BACKLOG_WRITE"
  $SplitReadyAfterDryRun = Get-Content -Raw (Join-Path $SplitRoot "docs\backlog\ready-issues.md")
  Assert-NotContains $SplitReadyAfterDryRun "状态：Ready"
  $SplitEvents = Get-RunEvents $SplitRoot
  if (!($SplitEvents | Where-Object { $_.event -eq "split.dry_run" -and $_.shouldSplitBacklog })) { throw "Expected dry-run split event" }

  $ExplainSplitOutput = Invoke-Runner $SplitRoot -Explain
  Assert-Contains $ExplainSplitOutput "EXPLAIN_NEXT_ACTION"
  Assert-Contains $ExplainSplitOutput "nextAction=SPLIT_BACKLOG"
  Assert-Contains $ExplainSplitOutput "shouldSplitBacklog=true"
  Assert-Contains $ExplainSplitOutput "wouldCreateReadyIssueDrafts=1"
  $SplitReadyAfterExplain = Get-Content -Raw (Join-Path $SplitRoot "docs\backlog\ready-issues.md")
  Assert-NotContains $SplitReadyAfterExplain "状态：Ready"
  if (Test-Path (Join-Path $SplitRoot ".codex-autopilot\run.lock")) { throw "ExplainNextAction should not leave run.lock" }

  $SplitDryRunOutput = Invoke-Runner $SplitRoot
  Assert-Contains $SplitDryRunOutput "DRY_RUN_NO_BACKLOG_WRITE"
  if (Test-Path (Join-Path $SplitRoot ".codex-autopilot\run.lock")) { throw "DryRun should not leave run.lock" }

  $ApplyRoot = New-Fixture -Name "apply" -Enabled -Ready "# Ready Issues`n" -Plan @"
# Plan

## 8. 中期开发计划

### 8.1 报表中心
建设项目经营总览报表。

### 8.2 规则治理中心
建设预警规则版本、启停和回归。
"@
  $ApplyOutput = Invoke-Runner $ApplyRoot -Apply
  Assert-Contains $ApplyOutput "BACKLOG_SPLIT_APPLIED"
  Assert-Contains $ApplyOutput "postSplitCheckpoint=CONTINUE"
  Assert-Contains $ApplyOutput "READY_ISSUE_FOUND"
  Assert-Contains $ApplyOutput "BUSINESS_EXECUTION_DISABLED_M3"
  Assert-Contains $ApplyOutput "EXECUTOR_RESULT_WRITTEN"
  $ApplyReady = Get-Content -Raw (Join-Path $ApplyRoot "docs\backlog\ready-issues.md")
  Assert-Contains $ApplyReady "状态：Ready"
  Assert-Contains $ApplyReady "验证命令："
  Assert-Contains $ApplyReady "来源锚点："
  $ApplyState = Get-Content -Raw (Join-Path $ApplyRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($ApplyState.mode -ne "continuous-runner") { throw "Expected state.mode=continuous-runner" }
  if ($ApplyState.lastAction -ne "READY_ISSUE_FOUND") { throw "Expected state.lastAction=READY_ISSUE_FOUND" }
  if ($ApplyState.lastIssue -notlike "ISSUE-008-001*") { throw "Expected state.lastIssue to record selected issue" }
  if ($ApplyState.lastReason -ne "READY_ISSUE_FOUND") { throw "Expected state.lastReason=READY_ISSUE_FOUND" }
  if ($ApplyState.stopReason) { throw "Expected state.stopReason to be empty for ready issue handoff" }
  if ($ApplyState.iterationCompleted -ne 0) { throw "Expected split/ready handoff not to increment iterationCompleted" }
  if (Test-Path (Join-Path $ApplyRoot ".codex-autopilot\run.lock")) { throw "ApplyBacklogSplit should release run.lock" }
  $ApplyResult = Get-LatestResult $ApplyRoot
  Assert-ResultSchema $ApplyResult
  $ApplyStatus = & powershell -NoProfile -ExecutionPolicy Bypass -File $StatusScript -Repo $ApplyRoot 2>&1 | Out-String
  $ApplyStatusJson = $ApplyStatus | ConvertFrom-Json
  if ($ApplyStatusJson.latestResultStatus -ne "noop") { throw "Expected status to expose latest noop result" }
  if (!$ApplyStatusJson.latestResultPath) { throw "Expected status to expose latestResultPath" }
  if (!$ApplyStatusJson.latestRunId) { throw "Expected status to expose latestRunId" }
  if ($ApplyStatusJson.latestEvent -ne "executor.result") { throw "Expected status latestEvent=executor.result" }
  if ($ApplyStatusJson.latestStopReason -ne "STOP_EXECUTION_DISABLED_M3") { throw "Expected latestStopReason from events.jsonl" }

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
  $LimitOneDoneOutput = Invoke-Runner $LimitOneRoot -Apply -MaxIterations 1
  Assert-Contains $LimitOneDoneOutput "STOP_ITERATION_LIMIT_REACHED"
  $LimitOneState = Get-Content -Raw (Join-Path $LimitOneRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($LimitOneState.iterationCompleted -ne 1) { throw "Expected MaxIterations=1 to count one completed issue" }
  if ($LimitOneState.remainingIterations -ne 0) { throw "Expected MaxIterations=1 remainingIterations=0" }
  if ($LimitOneState.stopReason -ne "STOP_ITERATION_LIMIT_REACHED") { throw "Expected STOP_ITERATION_LIMIT_REACHED stopReason" }

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
  $LimitTwoDoneOutput = Invoke-Runner $LimitTwoRoot -Apply -MaxIterations 2
  Assert-Contains $LimitTwoDoneOutput "STOP_CURRENT_ISSUE_BLOCKED"
  Assert-NotContains $LimitTwoDoneOutput "STOP_ITERATION_LIMIT_REACHED"
  $LimitTwoState = Get-Content -Raw (Join-Path $LimitTwoRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($LimitTwoState.iterationCompleted -ne 1) { throw "Expected MaxIterations=2 to count one blocked issue" }
  if ($LimitTwoState.remainingIterations -ne 1) { throw "Expected MaxIterations=2 remainingIterations=1" }

  $EmptyRoot = New-Fixture -Name "empty" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  $EmptyOutput = Invoke-Runner $EmptyRoot
  Assert-Contains $EmptyOutput "STOP_READY_AND_POOL_EMPTY"
  if (Test-Path (Join-Path $EmptyRoot ".codex-autopilot\state.json")) { throw "Dry-run should not write state.json" }
  $EmptyApplyOutput = Invoke-Runner $EmptyRoot -Apply
  Assert-Contains $EmptyApplyOutput "STOP_READY_AND_POOL_EMPTY"
  $EmptyState = Get-Content -Raw (Join-Path $EmptyRoot ".codex-autopilot\state.json") | ConvertFrom-Json
  if ($EmptyState.stopReason -ne "STOP_READY_AND_POOL_EMPTY") { throw "Expected state.stopReason=STOP_READY_AND_POOL_EMPTY" }

  Write-Host "continuous runner self-test passed"
} finally {
  Remove-Item -LiteralPath $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
