function Get-ExecutorCommand {
  param(
    [string]$RepoRoot,
    [string]$ConfigPath,
    [string]$IssueTitle
  )

  $executorPath = Join-Path $scriptDir "autopilot-exec-issue.ps1"
  return "pwsh -NoProfile -ExecutionPolicy Bypass -File `"$executorPath`" -RepoRoot `"$RepoRoot`" -ConfigPath `"$ConfigPath`" -Title `"$IssueTitle`""
}

function Write-ExecutorStallBlockedIssue {
  param([string]$RepoRoot, [object]$Issue, [object[]]$RetiredExecutors)
  $path = Join-Path $RepoRoot 'docs\backlog\blocked-issues.md'
  $existing = if (Test-Path -LiteralPath $path) { Get-Content -LiteralPath $path -Raw -Encoding UTF8 } else { "# Blocked Issues`r`n" }
  if ($existing -match "(?m)^###\s+$([regex]::Escape($Issue.lint.issueId))\b") { return $path }
  $rows = @($RetiredExecutors | ForEach-Object { "- executorPid=$($_.executorPid)，startedAt=$($_.startedAt)，lastProgressAt=$($_.lastProgressAt)，retiredAt=$($_.retiredAt)，retryCount=$($_.retryCount)" })
  $block = @"

### $($Issue.lint.issueId)：执行单元连续两次 stall timeout

- 失败分类：环境前置 / executor_stall_timeout。
- 退役证据：
$($rows -join "`r`n")
- 解除条件：补齐缺失上下文或拆小剩余范围后，由人工确认重新进入 Ready。
- 未完成验收项：该 Issue 的实现、验证、独立复核与归档尚未完成。
- 安全恢复方式：保持两个 executorPid 永久退役，从干净 checkpoint 创建全新执行单元；不得复用旧 PID 或启动第三次自动重派。
"@
  [IO.File]::WriteAllText($path, ($existing.TrimEnd() + "`r`n" + $block.TrimStart()), [Text.UTF8Encoding]::new($false))
  return $path
}

function Invoke-ChildWithHeartbeat {
  param(
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [int]$TimeoutSeconds,
    [int]$StallInspectSeconds = 300,
    [int]$StallTerminateSeconds = 600,
    [int]$HeartbeatMilliseconds = 30000,
    [object[]]$LongRunningCommands = @(),
    [string[]]$SemanticEvidencePaths = @(),
    [string]$Task = ''
  )
  function Stop-ChildProcessTree([Diagnostics.Process]$Child) {
    & taskkill.exe /PID $Child.Id /T /F 2>$null | Out-Null
    if (!$Child.HasExited) { $Child.Kill() }
  }
  function Quote-ChildArgument([string]$Value) { if ($Value -match '[\s"]') { return '"' + $Value.Replace('"','\"') + '"' }; return $Value }
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = 'pwsh'
  $startInfo.Arguments = ($Arguments | ForEach-Object { Quote-ChildArgument $_ }) -join ' '
  $startInfo.WorkingDirectory = $WorkingDirectory
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo
  [void]$process.Start()
  $startedAt = [datetimeoffset]::Now
  $script:ExecutorPid = $process.Id
  $script:ExecutorStartedAt = $startedAt.ToString('o')
  $script:LastProgressAt = $script:ExecutorStartedAt
  $script:TimeoutReason = $null
  $script:RetiredAt = $null
  $script:RetiredStatus = $null
  Write-State $autoDir 'EXECUTING' $false 'EXECUTOR_RUNNING' $script:RunLock.issueId 'EXECUTING' ''
  if ($script:Attempt -gt 0) {
    $previousRetirement = @($script:RetiredExecutors | Select-Object -Last 1)[0]
    Write-RunEvent 'executor.stall.retry' ([pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; status = 'RETRY'; executorPid = $process.Id; startedAt = $startedAt.ToString('o'); lastProgressAt = $startedAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'previous executor produced no new evidence; scope limited to unfinished acceptance items'; retiredAt = $previousRetirement.retiredAt; retiredStatus = $previousRetirement.retiredStatus })
  }
  $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
  $deadline = [datetimeoffset]::Now.AddSeconds($TimeoutSeconds)
  $timedOut = $false
  $stallTimedOut = $false
  $stallInspected = $false
  $lastProgressAt = $startedAt
  $progressFingerprint = Get-AutopilotProgressFingerprint -Worktree $WorkingDirectory -RootPid $process.Id -SemanticEvidencePaths $SemanticEvidencePaths
  while (!$process.WaitForExit($HeartbeatMilliseconds)) {
    if ($script:RunLock) { Write-RunLock (Join-Path $autoDir 'run.lock') $script:RunLock }
    $currentFingerprint = Get-AutopilotProgressFingerprint -Worktree $WorkingDirectory -RootPid $process.Id -SemanticEvidencePaths $SemanticEvidencePaths
    if ($currentFingerprint -ne $progressFingerprint) {
      $progressFingerprint = $currentFingerprint
      $lastProgressAt = [datetimeoffset]::Now
      $script:LastProgressAt = $lastProgressAt.ToString('o')
      $stallInspected = $false
    }
    $idleSeconds = ([datetimeoffset]::Now - $lastProgressAt).TotalSeconds
    if ([datetimeoffset]::Now -ge $deadline) { $timedOut = $true; Stop-ChildProcessTree $process; break }
    if (!$stallInspected -and $idleSeconds -ge $StallInspectSeconds) {
      $stallInspected = $true
      Write-RunEvent 'executor.stall.inspect' ([pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; status = 'INSPECT'; reason = 'no durable worktree progress'; idleSeconds = [int]$idleSeconds; executorPid = $process.Id; startedAt = $startedAt.ToString('o'); lastProgressAt = $lastProgressAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'no new evidence'; retiredAt = $null; retiredStatus = $null })
    }
    if ($idleSeconds -ge $StallTerminateSeconds) {
      $activeLongCommand = Get-AutopilotActiveLongCommand -RootPid $process.Id -Declarations $LongRunningCommands -StartedAt $startedAt
      if ($activeLongCommand) {
        Write-RunEvent 'executor.stall.long-command' ([pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; status = 'RUNNING'; executorPid = $process.Id; command = $activeLongCommand.command; commandPid = $activeLongCommand.processId; expectedSeconds = $activeLongCommand.expectedSeconds; startedAt = $startedAt.ToString('o'); lastProgressAt = $lastProgressAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'declared long command active'; retiredAt = $null; retiredStatus = $null })
        continue
      }
      $timedOut = $true
      $stallTimedOut = $true
      $retiredAt = [datetimeoffset]::Now.ToString('o')
      $retired = [pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; executorPid = $process.Id; startedAt = $startedAt.ToString('o'); lastProgressAt = $lastProgressAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'no new evidence'; retiredAt = $retiredAt; retiredStatus = 'RETIRED' }
      $script:RetiredExecutors += $retired
      $script:TimeoutReason = $retired.timeoutReason
      $script:RetiredAt = $retiredAt
      $script:RetiredStatus = 'RETIRED'
      Write-RunEvent 'executor.stall.retire' $retired
      Stop-ChildProcessTree $process
      break
    }
  }
  $process.WaitForExit()
  $script:ExecutorPid = $null
  $stdout = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
  if ($stdout) { Write-Host $stdout.TrimEnd() }
  if ($stderr) { Write-Warning $stderr.TrimEnd() }
  return [pscustomobject]@{ exitCode = if ($timedOut) { 124 } else { $process.ExitCode }; timedOut = $timedOut; stallTimedOut = $stallTimedOut; executorPid = $process.Id }
}
