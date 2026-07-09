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
  $status = @(& git -C $Root status --short --untracked-files=all 2>$null)
  return [pscustomobject]@{
    branch = $branch
    isClean = ($status.Count -eq 0)
    statusShort = @($status)
  }
}

function Get-BusinessGitStatus {
  param([string[]]$StatusLines)

  return @($StatusLines | Where-Object { $_ -notmatch "^\S\S\s+\.codex-autopilot/" })
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

function Expand-ExecutorToken {
  param(
    [string]$Value,
    [object]$Issue,
    [string]$PromptPath,
    [string]$RunDir
  )

  $expanded = $Value.Replace("{repoRoot}", $RepoRoot)
  $expanded = $expanded.Replace("{issueId}", $Issue.issueId)
  $expanded = $expanded.Replace("{issueTitle}", $Issue.title)
  $expanded = $expanded.Replace("{promptFile}", $PromptPath)
  $expanded = $expanded.Replace("{runDir}", $RunDir)
  return $expanded
}

function New-IssuePromptFile {
  param(
    [object]$Issue,
    [string]$RunDir
  )

  $promptPath = Join-Path $RunDir "issue-prompt.md"
  @"
你是被 AutoPilot 明确派工的执行智能体，不是主线程；在本 Ready Issue 范围内可以执行授权动作。

仓库：$RepoRoot
Issue：$($Issue.title)

任务正文：
$($Issue.body)

执行边界：
- 只处理该 Ready Issue 声明的允许修改范围。
- 不连接生产库，不发布生产，不自动 push。
- 完成后留下可验收的文件改动和验证证据；无法完成时按 blocked/failed 收口。
"@ | Out-File -Encoding utf8 $promptPath
  return $promptPath
}

function Invoke-ConfiguredIssueExecutor {
  param(
    [object]$Issue,
    [string]$RunDir,
    [object]$Config
  )

  $executor = $Config.issueExecutor
  if (!$executor -or !$executor.command) {
    return [pscustomobject]@{
      status = "blocked"
      failureCategory = "tool_config"
      nextAction = "STOP"
      stopReason = "STOP_EXECUTOR_COMMAND_MISSING"
      validation = @([pscustomobject]@{ name = "executor-command"; status = "fail"; message = "issueExecutor.command 未配置" })
      artifacts = @()
    }
  }

  $command = [string]$executor.command
  if (!(Test-CommandAvailable $command)) {
    return [pscustomobject]@{
      status = "blocked"
      failureCategory = "tool_config"
      nextAction = "STOP"
      stopReason = "STOP_EXECUTOR_COMMAND_NOT_FOUND"
      validation = @([pscustomobject]@{ name = "executor-command"; status = "fail"; message = "Executor command not found: $command" })
      artifacts = @()
    }
  }

  $promptPath = New-IssuePromptFile $Issue $RunDir
  $logPath = Join-Path $RunDir "executor.log"
  $before = @(Get-BusinessGitStatus @((Get-GitSummary $RepoRoot).statusShort))
  $args = @()
  foreach ($arg in @($executor.args)) {
    $args += (Expand-ExecutorToken ([string]$arg) $Issue $promptPath $RunDir)
  }
  $stdinPath = if ($executor.stdinFile) { Expand-ExecutorToken ([string]$executor.stdinFile) $Issue $promptPath $RunDir } else { "" }

  $exitCode = 0
  try {
    if ($stdinPath) {
      Get-Content -Raw -LiteralPath $stdinPath | & $command @args 2>&1 | Tee-Object -FilePath $logPath | Out-Null
    } else {
      & $command @args 2>&1 | Tee-Object -FilePath $logPath | Out-Null
    }
    if ($null -ne $LASTEXITCODE) {
      $exitCode = [int]$LASTEXITCODE
    }
  } catch {
    $_.Exception.Message | Out-File -Encoding utf8 $logPath
    $exitCode = 1
  }

  $afterSummary = Get-GitSummary $RepoRoot
  $after = @(Get-BusinessGitStatus @($afterSummary.statusShort))
  $newChanges = @($after | Where-Object { $before -notcontains $_ })
  $requireChangedFiles = if ($null -ne $executor.requireChangedFiles) { [bool]$executor.requireChangedFiles } else { $true }
  $logTail = if (Test-Path $logPath) { @((Get-Content -Tail 20 -LiteralPath $logPath) -join "`n") } else { @() }

  if ($exitCode -ne 0) {
    return [pscustomobject]@{
      status = "failed"
      failureCategory = "quality_security"
      nextAction = "STOP"
      stopReason = "STOP_EXECUTOR_FAILED"
      validation = @(
        [pscustomobject]@{ name = "executor-command"; status = "fail"; message = "exitCode=$exitCode" },
        [pscustomobject]@{ name = "executor-log-tail"; status = "info"; message = ($logTail -join "`n") }
      )
      artifacts = @($promptPath, $logPath)
    }
  }

  if ($requireChangedFiles -and $newChanges.Count -eq 0) {
    return [pscustomobject]@{
      status = "blocked"
      failureCategory = "quality_security"
      nextAction = "STOP"
      stopReason = "STOP_NO_EXECUTION_ARTIFACTS"
      validation = @(
        [pscustomobject]@{ name = "executor-command"; status = "pass"; message = "exitCode=0" },
        [pscustomobject]@{ name = "execution-artifacts"; status = "fail"; message = "Executor completed but produced no new git-visible changes." }
      )
      artifacts = @($promptPath, $logPath)
    }
  }

  return [pscustomobject]@{
    status = "done"
    failureCategory = "none"
    nextAction = "VALIDATE_AND_MERGE"
    stopReason = ""
    validation = @(
      [pscustomobject]@{ name = "executor-command"; status = "pass"; message = "exitCode=0" },
      [pscustomobject]@{ name = "execution-artifacts"; status = "pass"; message = ($newChanges -join "`n") }
    )
    artifacts = @($promptPath, $logPath)
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
    mode = if ($DryRun) { "dry-run" } elseif ($Noop) { "noop" } else { "execute" }
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
  if ($DryRun) {
    $result = New-Result $issue.issueId $issue.title "noop" "none" "STOP" "" $gitSummary @(
      [pscustomobject]@{ name = "executor-mode"; status = "pass"; message = "dry-run" },
      [pscustomobject]@{ name = "business-execution"; status = "skip"; message = "DryRun only prints/plans executor handoff." }
    )
  } elseif ($Noop) {
    $result = New-Result $issue.issueId $issue.title "blocked" "execution_disabled" "STOP" "STOP_EXECUTION_DISABLED" $gitSummary @(
      [pscustomobject]@{ name = "executor-mode"; status = "fail"; message = "noop" },
      [pscustomobject]@{ name = "business-execution"; status = "fail"; message = "Noop executor cannot satisfy unattended execution." }
    )
  } else {
    $execution = Invoke-ConfiguredIssueExecutor $issue $runDir $config
    $gitSummary = Get-GitSummary $RepoRoot
    $result = New-Result $issue.issueId $issue.title $execution.status $execution.failureCategory $execution.nextAction $execution.stopReason $gitSummary @($execution.validation)
    $result["artifacts"] = @($execution.artifacts)
  }
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
