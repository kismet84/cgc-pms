param(
  [string]$RepoRoot = "D:\projects-test\cgc-pms",
  [string]$ConfigPath = "",
  [string]$IssueId = "",
  [string]$Title = "",
  [string]$ReadyPath = "",
  [string]$RunId = "",
  [switch]$DryRun,
  [switch]$Noop
)

$ErrorActionPreference = "Stop"

function Read-JsonFile {
  param([string]$Path)
  if (!(Test-Path $Path)) {
    throw "Config not found: $Path"
  }
  return Get-Content -Raw $Path | ConvertFrom-Json
}

function Get-IssueBlocks {
  param([string]$Path)

  if (!(Test-Path $Path)) {
    return @()
  }

  $text = Get-Content -Raw $Path
  $matches = [regex]::Matches($text, "(?ms)^###\s+(ISSUE-[0-9-]+[^\r\n]*)\r?\n(.*?)(?=^###\s+ISSUE-|\z)")
  $issues = @()
  foreach ($match in $matches) {
    $heading = $match.Groups[1].Value.Trim()
    $idMatch = [regex]::Match($heading, "^(ISSUE-[0-9-]+)")
    $body = $match.Groups[2].Value
    $statusMatch = [regex]::Match($body, "(?m)^状态[：:]\s*(.+?)\s*$")
    $issues += [pscustomobject]@{
      issueId = if ($idMatch.Success) { $idMatch.Groups[1].Value } else { "" }
      title = $heading
      body = $body
      readyStatus = if ($statusMatch.Success) { $statusMatch.Groups[1].Value.Trim() } else { "" }
    }
  }
  return $issues
}

function Select-Issue {
  param(
    [object[]]$Issues,
    [string]$IssueId,
    [string]$Title
  )

  if ($IssueId) {
    return $Issues | Where-Object { $_.issueId -eq $IssueId -or $_.title -eq $IssueId } | Select-Object -First 1
  }
  if ($Title) {
    return $Issues | Where-Object { $_.title -eq $Title -or $_.issueId -eq $Title } | Select-Object -First 1
  }
  return $Issues | Where-Object { $_.readyStatus -ceq "Ready" } | Select-Object -First 1
}

function Get-GitSummary {
  param([string]$Root)

  $branch = (& git -C $Root branch --show-current 2>$null | Out-String).Trim()
  $status = @(& git -C $Root status --short 2>$null)
  return [pscustomobject]@{
    branch = $branch
    isClean = ($status.Count -eq 0)
    statusShort = @($status)
  }
}

function New-Result {
  param(
    [string]$IssueId,
    [string]$Title,
    [string]$Status,
    [string]$FailureCategory,
    [string]$NextAction,
    [string]$StopReason,
    [object]$GitSummary,
    [object[]]$Validation
  )

  if (@("done", "blocked", "failed", "noop") -notcontains $Status) {
    throw "Invalid result status: $Status"
  }
  if (@("", "none", "tool_config", "environment", "quality_security", "ready_issue_config", "execution_disabled") -notcontains $FailureCategory) {
    throw "Invalid failureCategory: $FailureCategory"
  }

  return [ordered]@{
    issueId = $IssueId
    title = $Title
    status = $Status
    failureCategory = $FailureCategory
    artifacts = @()
    gitSummary = $GitSummary
    validation = @($Validation)
    nextAction = $NextAction
    stopReason = $StopReason
    createdAt = Get-Date -Format o
  }
}

function Write-RunEvent {
  param(
    [string]$RunDir,
    [string]$Event,
    [object]$Data
  )

  $eventPath = Join-Path $RunDir "events.jsonl"
  $payload = [ordered]@{
    timestamp = Get-Date -Format o
    event = $Event
    mode = if ($DryRun) { "dry-run" } elseif ($Noop) { "noop" } else { "noop" }
    issueId = if ($Data.issueId) { $Data.issueId } else { "" }
    title = if ($Data.title) { $Data.title } else { $Title }
    checkpoint = if ($Data.checkpoint) { $Data.checkpoint } else { "" }
    decision = if ($Data.decision) { $Data.decision } else { "" }
    status = if ($Data.status) { $Data.status } else { "" }
    stopReason = if ($Data.stopReason) { $Data.stopReason } else { "" }
    dryRun = [bool]$DryRun
    apply = $false
    maxIterations = $null
  }
  foreach ($name in @($Data.PSObject.Properties.Name)) {
    if ($payload.Contains($name)) {
      continue
    }
    $payload[$name] = $Data.$name
  }
  ($payload | ConvertTo-Json -Compress -Depth 8) | Add-Content -Encoding utf8 $eventPath
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (!$ConfigPath) {
  $ConfigPath = Join-Path $scriptDir "codex-autopilot.config.json"
}
$config = Read-JsonFile $ConfigPath
if ($config.repoRoot) {
  $RepoRoot = $config.repoRoot
}
if (!$ReadyPath) {
  $ReadyPath = Join-Path $RepoRoot "docs\backlog\ready-issues.md"
}
$autoDir = if ($config.autopilotDir) { $config.autopilotDir } else { Join-Path $RepoRoot ".codex-autopilot" }
$runsDir = Join-Path $autoDir "runs"
New-Item -ItemType Directory -Path $runsDir -Force | Out-Null

$issues = @(Get-IssueBlocks $ReadyPath)
$issue = Select-Issue $issues $IssueId $Title
$gitSummary = Get-GitSummary $RepoRoot
if (!$RunId) {
  $RunId = "{0}-{1}" -f (Get-Date -Format "yyyyMMdd-HHmmss-fff"), $PID
}
$runId = $RunId
$runDir = Join-Path $runsDir $runId
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
Write-RunEvent $runDir "executor.start" ([pscustomobject]@{
  issueId = if ($issue) { $issue.issueId } else { $IssueId }
  title = if ($issue) { $issue.title } else { $Title }
  decision = "EXECUTOR_START"
})

if (!$issue) {
  $result = New-Result "" $Title "blocked" "ready_issue_config" "STOP" "STOP_READY_ISSUE_NOT_FOUND" $gitSummary @(
    [pscustomobject]@{ name = "issue-selection"; status = "fail"; message = "Ready Issue 不存在" }
  )
} else {
  $mode = if ($DryRun) { "dry-run" } elseif ($Noop) { "noop" } else { "noop" }
  $result = New-Result $issue.issueId $issue.title "noop" "execution_disabled" "STOP" "STOP_EXECUTION_DISABLED_M3" $gitSummary @(
    [pscustomobject]@{ name = "executor-mode"; status = "pass"; message = $mode },
    [pscustomobject]@{ name = "business-execution"; status = "skip"; message = "M3 仅产出结构化结果，真实业务执行禁用" }
  )
}

$resultPath = Join-Path $runDir "result.json"
$result | ConvertTo-Json -Depth 8 | Out-File -Encoding utf8 $resultPath
Write-RunEvent $runDir "executor.result" ([pscustomobject]@{
  issueId = $result.issueId
  title = $result.title
  decision = $result.nextAction
  status = $result.status
  stopReason = $result.stopReason
  failureCategory = $result.failureCategory
  resultPath = $resultPath
})
Write-Host "EXECUTOR_RESULT_WRITTEN"
Write-Host "runId=$runId"
Write-Host "resultPath=$resultPath"
Write-Host "status=$($result.status)"
Write-Host "failureCategory=$($result.failureCategory)"
