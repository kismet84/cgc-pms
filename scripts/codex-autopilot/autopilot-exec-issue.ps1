param(
  [string]$RepoRoot = "D:\projects-test\cgc-pms",
  [string]$ConfigPath = "",
  [string]$IssueId = "",
  [string]$Title = "",
  [string]$ReadyPath = "",
  [string]$RunId = "",
  [string]$ContextPath = "",
  [string]$Model = "",
  [string]$Thinking = "",
  [string]$ExecutorRole = "",
  [switch]$ReviewRequired,
  [switch]$DryRun,
  [switch]$Noop
)

$ErrorActionPreference = "Stop"

function Read-JsonFile {
  param([string]$Path)
  if (!(Test-Path $Path)) {
    throw "Config not found: $Path"
  }
  return Get-Content -Encoding UTF8 -Raw $Path | ConvertFrom-Json
}

function Get-IssueBlocks {
  param([string]$Path)

  if (!(Test-Path $Path)) {
    return @()
  }

  $text = Get-Content -Encoding UTF8 -Raw $Path
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

  return @($StatusLines | Where-Object {
    $paths = @(Get-BusinessPathsFromStatusLine $_)
    $paths.Count -gt 0
  })
}

function Test-ExcludedBusinessPath {
  param([string]$Path)

  if (!$Path) {
    return $true
  }

  $normalized = $Path.Replace('\', '/')
  if ($normalized.StartsWith("./")) {
    $normalized = $normalized.Substring(2)
  }
  return $normalized -match '^(?:\.codex-autopilot/|\.omc/|\.omo/|\.opencode/|\.claude/|\.mimocode/|graphify-out/|\.sisyphus/|\.archive/|archive/v1\.0/private/)'
}

function Get-BusinessPathsFromStatusLine {
  param([string]$StatusLine)

  if (!$StatusLine -or $StatusLine.Length -lt 4) {
    return @()
  }

  $rawPath = $StatusLine.Substring(3).Trim()
  if (!$rawPath) {
    return @()
  }

  $paths = @()
  if ($rawPath -match '\s+->\s+') {
    $paths += $rawPath -split '\s+->\s+'
  } else {
    $paths += $rawPath
  }

  return @($paths | ForEach-Object { $_.Trim('"') } | Where-Object { $_ -and -not (Test-ExcludedBusinessPath $_) } | Select-Object -Unique)
}

function Get-BusinessFileFingerprints {
  param(
    [string]$Root,
    [string[]]$StatusLines
  )

  $fingerprints = @{}
  foreach ($path in @($StatusLines | ForEach-Object { Get-BusinessPathsFromStatusLine $_ } | Select-Object -Unique)) {
    $fullPath = Join-Path $Root $path
    if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
      $item = Get-Item -LiteralPath $fullPath
      $hash = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash
      $fingerprints[$path] = "$hash`:$($item.Length)"
    } else {
      $fingerprints[$path] = "__MISSING__"
    }
  }
  return $fingerprints
}

function Get-ChangedBusinessArtifacts {
  param(
    [string[]]$BeforeStatus,
    [string[]]$AfterStatus,
    [hashtable]$BeforeFingerprints,
    [hashtable]$AfterFingerprints
  )

  $statusChanges = @($AfterStatus | Where-Object { $BeforeStatus -notcontains $_ })
  $contentChanges = @()
  foreach ($path in @($beforeFingerprints.Keys + $afterFingerprints.Keys | Select-Object -Unique)) {
    if ($beforeFingerprints[$path] -ne $afterFingerprints[$path]) {
      $contentChanges += "content:$path"
    }
  }

  return [pscustomobject]@{
    statusChanges = $statusChanges
    contentChanges = $contentChanges
    artifacts = @($statusChanges + $contentChanges)
  }
}

function Test-CommandAvailable {
  param([string]$Command)

  if (!$Command) {
    return $false
  }
  if ((Split-Path -Leaf $Command) -ne $Command -and (Test-Path -LiteralPath $Command)) {
    return $true
  }
  if ($null -ne (Get-Command $Command -ErrorAction SilentlyContinue)) { return $true }
  if ($Command -match '^codex(?:\.exe)?$' -and (Get-Command Get-AppxPackage -ErrorAction SilentlyContinue)) {
    $package = Get-AppxPackage -Name 'OpenAI.Codex' -ErrorAction SilentlyContinue
    if ($package -and (Test-Path -LiteralPath (Join-Path $package.InstallLocation 'app\resources\codex.exe'))) { return $true }
  }
  return $false
}

function Resolve-ExecutorCommand {
  param([string]$Command)

  if ((Split-Path -Leaf $Command) -ne $Command -and (Test-Path -LiteralPath $Command)) {
    return (Resolve-Path -LiteralPath $Command).Path
  }

  $resolved = Get-Command $Command -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($resolved -and $resolved.Source) {
    return $resolved.Source
  }
  if ($Command -match '^codex(?:\.exe)?$' -and (Get-Command Get-AppxPackage -ErrorAction SilentlyContinue)) {
    $package = Get-AppxPackage -Name 'OpenAI.Codex' -ErrorAction SilentlyContinue
    if ($package) {
      $candidate = Join-Path $package.InstallLocation 'app\resources\codex.exe'
      if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
  }
  return $Command
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
  $contextInstruction = if ($ContextPath) { "只读取并遵守上下文包：$ContextPath" } else { "任务正文：`n$($Issue.body)" }
  @"
你是被 AutoPilot 明确派工的执行智能体，不是主线程；在本 Ready Issue 范围内可以执行授权动作。

仓库：$RepoRoot
Issue：$($Issue.title)
角色：$ExecutorRole
需要独立复核：$([bool]$ReviewRequired)

$contextInstruction

执行边界：
- 只处理该 Ready Issue 声明的允许修改范围。
- 不连接生产库，不发布生产，不自动 push。
- 完成后留下可验收的文件改动和验证证据；无法完成时按 blocked/failed 收口。
"@ | Out-File -Encoding utf8 $promptPath
  return $promptPath
}

function Invoke-ExecutorProcess {
  param(
    [string]$Command,
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [string]$StdinPath,
    [string]$LogPath,
    [int]$TimeoutSeconds
  )

  function Quote-ProcessArgument {
    param([string]$Argument)

    if ($Argument -notmatch '[\s"]') {
      return $Argument
    }
    return '"' + ($Argument.Replace('"', '\"')) + '"'
  }

  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = Resolve-ExecutorCommand $Command
  $startInfo.Arguments = (@($Arguments) | ForEach-Object { Quote-ProcessArgument $_ }) -join " "
  $startInfo.WorkingDirectory = $WorkingDirectory
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $startInfo.RedirectStandardInput = [bool]$StdinPath

  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo

  $startedAt = Get-Date -Format o
  [void]$process.Start()
  $stdoutTask = $process.StandardOutput.ReadToEndAsync()
  $stderrTask = $process.StandardError.ReadToEndAsync()

  if ($StdinPath) {
    $process.StandardInput.Write((Get-Content -Encoding UTF8 -Raw -LiteralPath $StdinPath))
    $process.StandardInput.Close()
  }

  $waitMs = if ($TimeoutSeconds -gt 0) { $TimeoutSeconds * 1000 } else { -1 }
  $timedOut = $false
  if (!$process.WaitForExit($waitMs)) {
    $timedOut = $true
    $process.Kill($true)
  }
  $process.WaitForExit()

  $stdout = $stdoutTask.GetAwaiter().GetResult()
  $stderr = $stderrTask.GetAwaiter().GetResult()
  $logLines = @(
    "executor.startedAt=$startedAt"
    "executor.command=$($startInfo.FileName)"
    "executor.args=$($Arguments -join ' ')"
    "executor.cwd=$WorkingDirectory"
    "executor.timeoutSeconds=$TimeoutSeconds"
    "executor.timedOut=$timedOut"
    "executor.exitCode=$($process.ExitCode)"
    ""
    "[stdout]"
    $stdout
    ""
    "[stderr]"
    $stderr
  )
  $logLines | Out-File -Encoding utf8 $LogPath

  return [pscustomobject]@{
    exitCode = if ($timedOut) { 124 } else { [int]$process.ExitCode }
    timedOut = $timedOut
  }
}

function Get-ExecutorFailureCategory {
  param(
    [string]$Command,
    [string]$LogText
  )

  $leaf = Split-Path -Leaf $Command
  if ($leaf -match "^codex(\.exe)?$" -and $LogText -notmatch "OpenAI Codex v") {
    return "tool_config"
  }
  return "quality_security"
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
  $beforeFingerprints = Get-BusinessFileFingerprints -Root $RepoRoot -StatusLines $before
  $args = @()
  foreach ($arg in @($executor.args)) {
    $args += (Expand-ExecutorToken ([string]$arg) $Issue $promptPath $RunDir)
  }
  if ((Split-Path -Leaf $command) -match '^codex(?:\.exe)?$') {
    if ($Model) { $args += @('--model', $Model) }
    if ($Thinking) { $args += @('-c', "model_reasoning_effort=$Thinking") }
  }
  $stdinPath = if ($executor.stdinFile) { Expand-ExecutorToken ([string]$executor.stdinFile) $Issue $promptPath $RunDir } else { "" }

  $timeoutSeconds = if ($executor.timeoutSeconds) { [int]$executor.timeoutSeconds } else { 2700 }
  $exitCode = 0
  try {
    $processResult = Invoke-ExecutorProcess `
      -Command $command `
      -Arguments @($args) `
      -WorkingDirectory $RepoRoot `
      -StdinPath $stdinPath `
      -LogPath $logPath `
      -TimeoutSeconds $timeoutSeconds
    $exitCode = [int]$processResult.exitCode
  } catch {
    $_.Exception.Message | Out-File -Encoding utf8 $logPath
    $exitCode = 1
  }

  $afterSummary = Get-GitSummary $RepoRoot
  $after = @(Get-BusinessGitStatus @($afterSummary.statusShort))
  $afterFingerprints = Get-BusinessFileFingerprints -Root $RepoRoot -StatusLines $after
  $artifactChanges = Get-ChangedBusinessArtifacts `
    -BeforeStatus $before `
    -AfterStatus $after `
    -BeforeFingerprints $beforeFingerprints `
    -AfterFingerprints $afterFingerprints
  $requireChangedFiles = if ($null -ne $executor.requireChangedFiles) { [bool]$executor.requireChangedFiles } else { $true }
  $logText = if (Test-Path $logPath) { Get-Content -Encoding UTF8 -Raw -LiteralPath $logPath } else { "" }
  $logTail = if (Test-Path $logPath) { @((Get-Content -Encoding UTF8 -Tail 40 -LiteralPath $logPath) -join "`n") } else { @() }

  if ($exitCode -ne 0) {
    $failureCategory = Get-ExecutorFailureCategory $command $logText
    return [pscustomobject]@{
      status = "failed"
      failureCategory = $failureCategory
      nextAction = "STOP"
      stopReason = if ($failureCategory -eq "tool_config") { "STOP_EXECUTOR_TOOLCHAIN_FAILED" } else { "STOP_EXECUTOR_FAILED" }
      validation = @(
        [pscustomobject]@{ name = "executor-command"; status = "fail"; message = "exitCode=$exitCode" },
        [pscustomobject]@{ name = "executor-log-tail"; status = "info"; message = ($logTail -join "`n") }
      )
      artifacts = @($promptPath, $logPath)
    }
  }

  if ($requireChangedFiles -and $artifactChanges.artifacts.Count -eq 0) {
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
      [pscustomobject]@{ name = "execution-artifacts"; status = "pass"; message = ($artifactChanges.artifacts -join "`n") }
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
if ($config.repoRoot -and !$PSBoundParameters.ContainsKey('RepoRoot')) {
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
