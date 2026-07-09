param()

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Runner = Join-Path $ScriptDir "autopilot-run-continuous.ps1"
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
  New-Item -ItemType Directory -Path $AutoDir, $BacklogDir, $ScriptDir -Force | Out-Null
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
    [switch]$Apply
  )

  $Config = Join-Path $Root "scripts\codex-autopilot\codex-autopilot.config.json"
  $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $Runner, "-RepoRoot", $Root, "-ConfigPath", $Config, "-MaxLoops", "3")
  if ($Apply) {
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

try {
  $DisabledRoot = New-Fixture -Name "disabled" -Ready "# Ready Issues`n" -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $DisabledRoot) "STOP_DISABLED"

  $PauseRoot = New-Fixture -Name "paused" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  "pause" | Out-File -Encoding utf8 (Join-Path $PauseRoot ".codex-autopilot\pause.flag")
  Assert-Contains (Invoke-Runner $PauseRoot) "STOP_PAUSED"

  $StopRoot = New-Fixture -Name "stopped" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  "stop" | Out-File -Encoding utf8 (Join-Path $StopRoot ".codex-autopilot\stop.flag")
  Assert-Contains (Invoke-Runner $StopRoot) "STOP_REQUESTED"

  $BadLimitRoot = New-Fixture -Name "bad-limit" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n" -MaxIssuesPerRun 2
  $BadLimitOutput = Invoke-Runner $BadLimitRoot
  Assert-Contains $BadLimitOutput "Continuous runner requires maxIssuesPerRun=1"

  $ReadyRoot = New-Fixture -Name "ready" -Enabled -Ready @"
# Ready Issues

### ISSUE-100-001：Runner ready branch

状态：Ready
验证命令：
- ``git diff --check``
"@ -Plan "# Plan`n"
  $ReadyOutput = Invoke-Runner $ReadyRoot
  Assert-Contains $ReadyOutput "READY_ISSUE_FOUND"
  Assert-Contains $ReadyOutput "maxIssuesPerRun=1"

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
  Assert-Contains $ApplyOutput "BUSINESS_EXECUTION_NOT_STARTED"
  $ApplyReady = Get-Content -Raw (Join-Path $ApplyRoot "docs\backlog\ready-issues.md")
  Assert-Contains $ApplyReady "状态：Ready"
  Assert-Contains $ApplyReady "验证命令："
  Assert-Contains $ApplyReady "来源锚点："

  $EmptyRoot = New-Fixture -Name "empty" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $EmptyRoot) "STOP_NO_READY_OR_SPLIT_CANDIDATE"

  Write-Host "continuous runner self-test passed"
} finally {
  Remove-Item -LiteralPath $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
