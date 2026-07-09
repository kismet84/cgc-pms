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
  "maxIssuesPerRun": 1,
  "autoPush": false
}
"@ | Out-File -Encoding utf8 (Join-Path $ScriptDir "codex-autopilot.config.json")
  $Ready | Out-File -Encoding utf8 (Join-Path $BacklogDir "ready-issues.md")
  "# Current Focus`n" | Out-File -Encoding utf8 (Join-Path $BacklogDir "current-focus.md")
  $Plan | Out-File -Encoding utf8 (Join-Path $BacklogDir "cgc-pms-production-enhancement-plan.md")
  return $Root
}

function Invoke-Runner {
  param([string]$Root)

  $Config = Join-Path $Root "scripts\codex-autopilot\codex-autopilot.config.json"
  & powershell -NoProfile -ExecutionPolicy Bypass -File $Runner -RepoRoot $Root -ConfigPath $Config -DryRun -MaxLoops 3 | Out-String
}

function Assert-Contains {
  param([string]$Text, [string]$Expected)

  if ($Text -notlike "*$Expected*") {
    throw "Expected output to contain '$Expected'. Actual:`n$Text"
  }
}

try {
  $DisabledRoot = New-Fixture -Name "disabled" -Ready "# Ready Issues`n" -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $DisabledRoot) "STOP_DISABLED"

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

  $EmptyRoot = New-Fixture -Name "empty" -Enabled -Ready "# Ready Issues`n" -Plan "# Plan`n"
  Assert-Contains (Invoke-Runner $EmptyRoot) "STOP_NO_READY_OR_SPLIT_CANDIDATE"

  Write-Host "continuous runner self-test passed"
} finally {
  Remove-Item -LiteralPath $TempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
