param(
  [string]$RepoRoot = "D:\projects-test\cgc-pms",
  [string]$ConfigPath = "",
  [switch]$AllowStopped
)

$ErrorActionPreference = "Stop"

function Add-Gate {
  param(
    [System.Collections.ArrayList]$Gates,
    [string]$Name,
    [string]$Status,
    [string]$Message,
    [object]$Evidence = $null
  )

  [void]$Gates.Add([pscustomobject]@{
    name = $Name
    status = $Status
    message = $Message
    evidence = $Evidence
  })
}

function Read-JsonFile {
  param([string]$Path)
  if (!(Test-Path $Path)) {
    throw "JSON file not found: $Path"
  }
  return Get-Content -Raw -Encoding UTF8 $Path | ConvertFrom-Json
}

function Get-FirstReadyIssueTitle {
  param([string]$ReadyPath)
  if (!(Test-Path $ReadyPath)) {
    return ""
  }

  $text = Get-Content -Raw -Encoding UTF8 $ReadyPath
  foreach ($match in [regex]::Matches($text, "(?ms)^###\s+(ISSUE-[0-9-]+[^\r\n]*)\r?\n(.*?)(?=^###\s+ISSUE-|\z)")) {
    $body = $match.Groups[2].Value
    $statusMatch = [regex]::Match($body, "(?m)^状态[：:]\s*(.+?)\s*$")
    if ($statusMatch.Success -and $statusMatch.Groups[1].Value.Trim() -ceq "Ready") {
      return $match.Groups[1].Value.Trim()
    }
  }
  return ""
}

function Invoke-JsonScript {
  param([string[]]$Arguments)

  $output = & powershell @Arguments 2>&1 | Out-String
  try {
    return [pscustomobject]@{
      ok = $true
      raw = $output.Trim()
      json = ($output | ConvertFrom-Json)
    }
  } catch {
    return [pscustomobject]@{
      ok = $false
      raw = $output.Trim()
      json = $null
    }
  }
}

function Test-FileContains {
  param([string]$Path, [string[]]$Patterns)
  if (!(Test-Path $Path)) {
    return @()
  }
  $text = Get-Content -Raw -Encoding UTF8 $Path
  return @($Patterns | Where-Object { $text -notmatch [regex]::Escape($_) })
}

function Test-CommandAvailable {
  param([string]$Command)

  if (!$Command) {
    return $false
  }
  if ((Split-Path -Leaf $Command) -ne $Command -and (Test-Path -LiteralPath $Command)) {
    return $true
  }
  return $null -ne (Get-Command $Command -ErrorAction SilentlyContinue)
}

if (!$ConfigPath) {
  $ConfigPath = Join-Path $RepoRoot "scripts\codex-autopilot\codex-autopilot.config.json"
}

$scriptDir = Join-Path $RepoRoot "scripts\codex-autopilot"
$autoDir = Join-Path $RepoRoot ".codex-autopilot"
$readyPath = Join-Path $RepoRoot "docs\backlog\ready-issues.md"
$gates = [System.Collections.ArrayList]::new()
$warnings = [System.Collections.ArrayList]::new()

$config = Read-JsonFile $ConfigPath
$configuredAutoDir = if ($config.autopilotDir) { $config.autopilotDir } else { $autoDir }

Add-Gate $gates "config.exists" "pass" "AutoPilot config is readable." @{ path = $ConfigPath }

if ($config.autoPush -eq $false) {
  Add-Gate $gates "config.autoPush" "pass" "autoPush=false." @{ autoPush = $config.autoPush }
} else {
  Add-Gate $gates "config.autoPush" "fail" "autoPush must remain false before full unattended mode." @{ autoPush = $config.autoPush }
}

if ($config.maxIssuesPerRun -eq 1) {
  Add-Gate $gates "config.maxIssuesPerRun" "pass" "maxIssuesPerRun=1." @{ maxIssuesPerRun = $config.maxIssuesPerRun }
} else {
  Add-Gate $gates "config.maxIssuesPerRun" "fail" "maxIssuesPerRun must be 1." @{ maxIssuesPerRun = $config.maxIssuesPerRun }
}

if ($config.worktreeRoot) {
  Add-Gate $gates "config.worktreeRoot" "pass" "worktreeRoot is configured." @{ worktreeRoot = $config.worktreeRoot }
} else {
  Add-Gate $gates "config.worktreeRoot" "fail" "worktreeRoot is required for implementation task isolation." $null
}

$resetRule = $config.devDataResetAllowedWhen
$markerPath = if ($resetRule.requiredMarkerFile) { Join-Path $RepoRoot $resetRule.requiredMarkerFile } else { "" }
$hosts = @($resetRule.databaseHosts)
$profiles = @($resetRule.springProfiles)
if ($config.requireTestDataResetMarker -and $markerPath -and (Test-Path $markerPath) -and ($hosts -contains "localhost") -and ($hosts -contains "127.0.0.1") -and ($profiles | Where-Object { $_ -in @("dev", "test", "demo") })) {
  Add-Gate $gates "testDataReset.marker" "pass" "Test data reset marker and dev/test/demo host rules are present." @{
    marker = $markerPath
    databaseHosts = $hosts
    springProfiles = $profiles
  }
} else {
  Add-Gate $gates "testDataReset.marker" "fail" "Test data reset requires marker plus dev/test/demo and localhost/127.0.0.1 rules." @{
    marker = $markerPath
    markerExists = if ($markerPath) { Test-Path $markerPath } else { $false }
    databaseHosts = $hosts
    springProfiles = $profiles
  }
}

$stopFlag = Test-Path (Join-Path $configuredAutoDir "stop.flag")
$pauseFlag = Test-Path (Join-Path $configuredAutoDir "pause.flag")
$enabledFlag = Test-Path (Join-Path $configuredAutoDir "enabled.flag")
if (!$stopFlag -and !$pauseFlag -and ($enabledFlag -or $AllowStopped)) {
  Add-Gate $gates "controlFlags" "pass" "stop/pause are clear and startup is allowed." @{
    enabledFlag = $enabledFlag
    stopFlag = $stopFlag
    pauseFlag = $pauseFlag
  }
} else {
  Add-Gate $gates "controlFlags" "fail" "Unattended mode is blocked by control flags or disabled state." @{
    enabledFlag = $enabledFlag
    stopFlag = $stopFlag
    pauseFlag = $pauseFlag
    allowStopped = [bool]$AllowStopped
  }
}

$lockPath = Join-Path $configuredAutoDir "run.lock"
if (!(Test-Path $lockPath)) {
  Add-Gate $gates "runLock.current" "pass" "No active run.lock exists." @{ path = $lockPath }
} else {
  Add-Gate $gates "runLock.current" "fail" "run.lock exists; do not start another unattended run." @{ path = $lockPath }
}

$runnerPath = Join-Path $scriptDir "autopilot-run-continuous.ps1"
$runnerMissing = Test-FileContains $runnerPath @("New-RunLock", "Read-RunLock", "Test-RunLockStale", "Write-RunEvent", "Invoke-IssueExecutor", "ExplainNextAction")
if ($runnerMissing.Count -eq 0) {
  Add-Gate $gates "runner.capabilities" "pass" "Continuous runner contains lock, JSONL, executor handoff, and explain support." @{ path = $runnerPath }
} else {
  Add-Gate $gates "runner.capabilities" "fail" "Continuous runner is missing required capabilities." @{ missing = $runnerMissing }
}

$executorPath = Join-Path $scriptDir "autopilot-exec-issue.ps1"
$executorMissing = Test-FileContains $executorPath @("result.json", "failureCategory", "gitSummary", "validation", "nextAction")
if ($executorMissing.Count -eq 0) {
  Add-Gate $gates "executor.result" "pass" "Executor writes structured result.json." @{ path = $executorPath }
} else {
  Add-Gate $gates "executor.result" "fail" "Executor result contract is incomplete." @{ missing = $executorMissing }
}

$realExecutorMissing = Test-FileContains $executorPath @("Invoke-ConfiguredIssueExecutor", "STOP_NO_EXECUTION_ARTIFACTS", "STOP_EXECUTOR_COMMAND_MISSING")
$issueExecutor = $config.issueExecutor
if ($realExecutorMissing.Count -gt 0) {
  Add-Gate $gates "executor.realExecution" "fail" "Executor script does not expose a real configured execution path." @{ missing = $realExecutorMissing }
} elseif (!$issueExecutor -or !$issueExecutor.command) {
  Add-Gate $gates "executor.realExecution" "fail" "issueExecutor.command is required; noop-only execution cannot enter unattended mode." $null
} elseif (!(Test-CommandAvailable ([string]$issueExecutor.command))) {
  Add-Gate $gates "executor.realExecution" "fail" "Configured issueExecutor command is not available." @{ command = $issueExecutor.command }
} else {
  Add-Gate $gates "executor.realExecution" "pass" "Executor has a configured real command path." @{
    command = $issueExecutor.command
    requireChangedFiles = if ($null -ne $issueExecutor.requireChangedFiles) { [bool]$issueExecutor.requireChangedFiles } else { $true }
  }
}

$statusPath = Join-Path $scriptDir "autopilot-status.ps1"
$statusMissing = Test-FileContains $statusPath @("latestResultPath", "latestEventPath", "lockExists", "lockStale")
if ($statusMissing.Count -eq 0) {
  Add-Gate $gates "status.summary" "pass" "Status exposes lock, latest JSONL event, and latest result summary." @{ path = $statusPath }
} else {
  Add-Gate $gates "status.summary" "fail" "Status summary is missing required fields." @{ missing = $statusMissing }
}

$readyLintPath = Join-Path $scriptDir "ready-lint.ps1"
if (Test-Path $readyLintPath) {
  $readyIssue = Get-FirstReadyIssueTitle $readyPath
  if ($readyIssue) {
    $lint = Invoke-JsonScript @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $readyLintPath, "-RepoRoot", $RepoRoot, "-ReadyPath", $readyPath, "-IssueTitle", $readyIssue)
    if ($lint.ok -and $lint.json.status -eq "pass") {
      Add-Gate $gates "readyLint.currentReady" "pass" "First Ready Issue passes ready-lint." @{ issue = $readyIssue; lint = $lint.json }
    } else {
      Add-Gate $gates "readyLint.currentReady" "fail" "First Ready Issue does not pass ready-lint." @{ issue = $readyIssue; output = $lint.raw }
    }
  } else {
    Add-Gate $gates "readyLint.currentReady" "warn" "No Ready Issue is currently queued; runner must split backlog before business execution." @{ readyPath = $readyPath }
    [void]$warnings.Add("当前 ready-issues.md 无 Ready 任务，完整无人值守长跑只能先进入拆单轮。")
  }
} else {
  Add-Gate $gates "readyLint.currentReady" "fail" "ready-lint.ps1 is missing." @{ path = $readyLintPath }
}

$explain = Invoke-JsonScript @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $statusPath, "-Repo", $RepoRoot)
if ($explain.ok) {
  Add-Gate $gates "status.json" "pass" "autopilot-status returns JSON." @{
    status = $explain.json.status
    latestResultStatus = $explain.json.latestResultStatus
    latestEvent = $explain.json.latestEvent
  }
} else {
  Add-Gate $gates "status.json" "fail" "autopilot-status output is not JSON." @{ output = $explain.raw }
}

$testPath = Join-Path $scriptDir "test-continuous-runner.ps1"
$testMissing = Test-FileContains $testPath @("Assert-ResultSchema", "Assert-EventSchema", "RUN_LOCK_ACTIVE", "STALE_RUN_LOCK_REMOVED", "EXPLAIN_NEXT_ACTION")
if ($testMissing.Count -eq 0) {
  Add-Gate $gates "selfTest.coverage" "pass" "Runner self-test covers result, event, lock, stale lock, and explain paths." @{ path = $testPath }
} else {
  Add-Gate $gates "selfTest.coverage" "fail" "Runner self-test coverage is incomplete." @{ missing = $testMissing }
}

$failed = @($gates | Where-Object { $_.status -eq "fail" })
$warned = @($gates | Where-Object { $_.status -eq "warn" })
$allowed = ($failed.Count -eq 0)

[pscustomobject]@{
  status = if ($allowed) { "pass" } else { "fail" }
  unattendedModeAllowed = $allowed
  repoRoot = $RepoRoot
  checkedAt = Get-Date -Format o
  summary = [pscustomobject]@{
    total = $gates.Count
    pass = @($gates | Where-Object { $_.status -eq "pass" }).Count
    warn = $warned.Count
    fail = $failed.Count
  }
  gates = @($gates)
  warnings = @($warnings)
} | ConvertTo-Json -Depth 10
