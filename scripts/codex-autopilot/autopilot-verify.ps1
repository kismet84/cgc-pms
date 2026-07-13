$ErrorActionPreference = 'Stop'
$contextScript = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-context.ps1'
if (Test-Path -LiteralPath $contextScript) { . $contextScript }

function Test-AutopilotPostExecutionVerificationRequired {
  param([Parameter(Mandatory)][string]$Command)
  return $Command -notmatch '(?i)scripts[\\/]codex-autopilot[\\/]ready-lint\.ps1'
}

function Invoke-AutopilotVerificationCommand {
  param(
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$BaseCommit,
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string]$EvidencePath,
    [Parameter(Mandatory)][string]$LogPath,
    [int]$TimeoutSeconds = 1800
  )
  $started = [datetimeoffset]::Now
  $wrappedCommand = "& { $Command }; if (`$null -ne `$LASTEXITCODE) { exit `$LASTEXITCODE }"
  $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($wrappedCommand))
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = 'powershell'
  $startInfo.Arguments = "-NoProfile -EncodedCommand $encoded"
  $startInfo.WorkingDirectory = $Worktree
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  [void]$process.Start()
  $stdoutTask = $process.StandardOutput.ReadToEndAsync()
  $stderrTask = $process.StandardError.ReadToEndAsync()
  $timedOut = !$process.WaitForExit($TimeoutSeconds * 1000)
  if ($timedOut) { $process.Kill($true) }
  $process.WaitForExit()
  $stdout = $stdoutTask.GetAwaiter().GetResult()
  $stderr = $stderrTask.GetAwaiter().GetResult()
  $exitCode = if ($timedOut) { 124 } else { [int]$process.ExitCode }
  $parent = Split-Path -Parent $LogPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($LogPath, "[stdout]`r`n$stdout`r`n[stderr]`r`n$stderr", [Text.UTF8Encoding]::new($false))
  $summarySource = if ($exitCode -eq 0) { $stdout } else { "$stderr`n$stdout" }
  $summaryLines = @($summarySource -split '\r?\n' | Where-Object { $_ } | Select-Object -Last 20)
  $commit = (& git -C $Worktree rev-parse HEAD).Trim()
  $evidence = [ordered]@{
    schemaVersion = 1
    issueId = $IssueId
    baseCommit = $BaseCommit
    commit = $commit
    diffHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $BaseCommit
    command = $Command
    startedAt = $started.ToString('o')
    durationSeconds = [Math]::Round(([datetimeoffset]::Now - $started).TotalSeconds, 3)
    exitCode = $exitCode
    classification = if ($exitCode -eq 0) { 'pass' } elseif ($timedOut) { 'timeout' } else { 'fail' }
    summary = (($summaryLines -join "`n").Trim())
    rawLogPath = $LogPath
  }
  $evidenceParent = Split-Path -Parent $EvidencePath
  if ($evidenceParent -and !(Test-Path -LiteralPath $evidenceParent)) { New-Item -ItemType Directory -Path $evidenceParent -Force | Out-Null }
  [IO.File]::WriteAllText($EvidencePath, ($evidence | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]$evidence
}

function Assert-AutopilotEvidenceCurrent {
  param([object]$Evidence, [string]$IssueId, [string]$Worktree, [string]$BaseCommit)
  if ($Evidence.issueId -ne $IssueId) { throw 'evidence Issue ID mismatch' }
  if ($Evidence.baseCommit -ne $BaseCommit) { throw 'evidence base commit mismatch' }
  if ($Evidence.exitCode -ne 0 -or $Evidence.classification -ne 'pass') { throw 'evidence does not prove a pass' }
  $currentHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $BaseCommit
  if ($Evidence.diffHash -ne $currentHash) { throw 'evidence diff hash is stale' }
  return $true
}

function New-AutopilotReadyLintEvidence {
  param(
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$BaseCommit,
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string]$ReadyContentHash,
    [Parameter(Mandatory)][string]$ExpectedReadyContentHash,
    [Parameter(Mandatory)][string]$EvidencePath,
    [Parameter(Mandatory)][string]$LogPath
  )
  if ($ReadyContentHash -notmatch '^[a-f0-9]{64}$' -or $ReadyContentHash -ne $ExpectedReadyContentHash) {
    throw 'normalized terminal Ready lint hash does not match the dispatched contract'
  }
  $started = [datetimeoffset]::Now
  $summary = "Normalized terminal Ready contract passed the production parser; readyContentHash=$ReadyContentHash"
  $parent = Split-Path -Parent $LogPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($LogPath, $summary, [Text.UTF8Encoding]::new($false))
  $evidence = [ordered]@{
    schemaVersion = 1
    issueId = $IssueId
    baseCommit = $BaseCommit
    commit = (& git -C $Worktree rev-parse HEAD).Trim()
    diffHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $BaseCommit
    command = $Command
    startedAt = $started.ToString('o')
    durationSeconds = [Math]::Round(([datetimeoffset]::Now - $started).TotalSeconds, 3)
    exitCode = 0
    classification = 'pass'
    summary = $summary
    rawLogPath = $LogPath
    readyContentHash = $ReadyContentHash
  }
  $evidenceParent = Split-Path -Parent $EvidencePath
  if ($evidenceParent -and !(Test-Path -LiteralPath $evidenceParent)) { New-Item -ItemType Directory -Path $evidenceParent -Force | Out-Null }
  [IO.File]::WriteAllText($EvidencePath, ($evidence | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]$evidence
}

function Get-AutopilotConcatenatedEvidencePaths {
  param([AllowEmptyString()][string]$Message)
  if ([string]::IsNullOrWhiteSpace($Message)) { return @() }
  return @([regex]::Matches($Message, '(?i)[a-z]:\\.*?evidence(?:-\d+)?\.json') | ForEach-Object { $_.Value } | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
}

function Get-AutopilotRetryBudget {
  param([string]$Category, [string]$Subcategory = '')
  if ($Category -in @('tool_config','environment_prereq','ready_issue_config','unknown')) { return 1 }
  if ($Category -eq 'real_quality_or_security' -and $Subcategory -eq 'real_permission_or_security_failure') { return 1 }
  if ($Category -eq 'real_quality_or_security') { return 2 }
  return 0
}

function Test-AutopilotHealthGate {
  param([int]$TimeoutSeconds = 10)
  $targets = @(
    @{ name = 'backend'; url = 'http://localhost:8080/api/actuator/health' },
    @{ name = 'frontend'; url = 'http://localhost:5173/' },
    @{ name = 'dev-login'; url = 'http://localhost:5173/api/auth/dev-login?redirect=/dashboard' }
  )
  $results = foreach ($target in $targets) {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri $target.url -TimeoutSec $TimeoutSeconds -MaximumRedirection 0 -ErrorAction Stop
      [pscustomobject]@{ name = $target.name; url = $target.url; status = 'pass'; statusCode = [int]$response.StatusCode }
    } catch {
      [pscustomobject]@{ name = $target.name; url = $target.url; status = 'fail'; statusCode = $null; error = $_.Exception.Message }
    }
  }
  return [pscustomobject]@{ status = if (@($results | Where-Object status -eq 'fail').Count -eq 0) { 'pass' } else { 'fail' }; results = @($results) }
}

function Invoke-AutopilotRuntimePreflight {
  param([Parameter(Mandatory)][string]$RepoRoot, [object]$RuntimeRefresh)
  $before = Test-AutopilotHealthGate
  if ($before.status -eq 'pass') { return [pscustomobject]@{ status='pass'; refreshed=$false; before=$before; after=$before } }
  if (!$RuntimeRefresh -or $RuntimeRefresh.enabled -ne $true -or !$RuntimeRefresh.command) { return [pscustomobject]@{ status='fail'; refreshed=$false; before=$before; after=$before; reason='runtime refresh is unavailable' } }
  $timeoutSeconds = if ($RuntimeRefresh.timeoutSeconds) { [int]$RuntimeRefresh.timeoutSeconds } else { 900 }
  $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("& { $($RuntimeRefresh.command) }; if (`$null -ne `$LASTEXITCODE) { exit `$LASTEXITCODE }"))
  $process = Start-Process -FilePath powershell -ArgumentList '-NoProfile','-EncodedCommand',$encoded -WorkingDirectory $RepoRoot -PassThru -WindowStyle Hidden
  if (!$process.WaitForExit($timeoutSeconds * 1000)) { & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null; return [pscustomobject]@{ status='fail'; refreshed=$true; before=$before; after=$null; reason='runtime refresh timed out' } }
  if ($process.ExitCode -ne 0) { return [pscustomobject]@{ status='fail'; refreshed=$true; before=$before; after=$null; reason="runtime refresh exitCode=$($process.ExitCode)" } }
  $waitSeconds = if ($RuntimeRefresh.waitSeconds) { [int]$RuntimeRefresh.waitSeconds } else { 180 }
  Start-Sleep -Seconds $waitSeconds
  $after = Test-AutopilotHealthGate
  return [pscustomobject]@{ status=$after.status; refreshed=$true; before=$before; after=$after; reason=if($after.status -eq 'pass'){'runtime recovered'}else{'runtime remains unhealthy after refresh'} }
}
