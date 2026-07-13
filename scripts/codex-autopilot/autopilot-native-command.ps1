$ErrorActionPreference = 'Stop'

function ConvertTo-AutopilotNativeArgument {
  param([AllowEmptyString()][string]$Value)
  if ($Value -notmatch '[\s"]') { return $Value }
  $escaped = $Value -replace '(\\*)"', '$1$1\"'
  $escaped = $escaped -replace '(\\+)$', '$1$1'
  return '"' + $escaped + '"'
}

function Invoke-AutopilotNativeCommand {
  param(
    [Parameter(Mandatory)][string]$FilePath,
    [string[]]$Arguments = @(),
    [string]$WorkingDirectory = '',
    [int[]]$AcceptedExitCodes = @(0),
    [int]$TimeoutSeconds = 120,
    [switch]$ThrowOnFailure
  )
  if ($TimeoutSeconds -lt 1) { throw 'Native command timeout must be positive.' }
  if (@($AcceptedExitCodes).Count -eq 0) { throw 'Native command requires at least one accepted exit code.' }

  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $FilePath
  $startInfo.Arguments = (@($Arguments) | ForEach-Object { ConvertTo-AutopilotNativeArgument ([string]$_) }) -join ' '
  if ($WorkingDirectory) { $startInfo.WorkingDirectory = $WorkingDirectory }
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true

  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $startedAt = [datetimeoffset]::Now
  try {
    [void]$process.Start()
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $completed = $process.WaitForExit($TimeoutSeconds * 1000)
    if (!$completed) {
      try { & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null } catch { try { $process.Kill() } catch {} }
      $process.WaitForExit()
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $exitCode = if ($completed) { [int]$process.ExitCode } else { 124 }
    $accepted = $completed -and $AcceptedExitCodes -contains $exitCode
    $result = [pscustomobject]@{
      filePath = $FilePath
      arguments = @($Arguments)
      workingDirectory = $WorkingDirectory
      stdout = $stdout
      stderr = $stderr
      exitCode = $exitCode
      timedOut = !$completed
      succeeded = $accepted
      startedAt = $startedAt.ToString('o')
      durationMilliseconds = [Math]::Max(0, [int][Math]::Round(([datetimeoffset]::Now - $startedAt).TotalMilliseconds))
    }
    if ($ThrowOnFailure -and !$accepted) {
      $diagnostic = if ($stderr) { $stderr.Trim() } elseif ($stdout) { $stdout.Trim() } elseif (!$completed) { 'command timed out' } else { 'no diagnostic output' }
      throw "Native command failed: $FilePath (exitCode=$exitCode): $diagnostic"
    }
    return $result
  } finally {
    $process.Dispose()
  }
}

function Invoke-AutopilotGit {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string[]]$Arguments,
    [int[]]$AcceptedExitCodes = @(0),
    [int]$TimeoutSeconds = 120,
    [switch]$ThrowOnFailure
  )
  $gitArguments = @('-c','core.quotePath=false','-C',$RepoRoot) + @($Arguments)
  return Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments $gitArguments -AcceptedExitCodes $AcceptedExitCodes -TimeoutSeconds $TimeoutSeconds -ThrowOnFailure:$ThrowOnFailure
}

function Get-AutopilotNativeOutputLines {
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return @() }
  return @($Text -split '\r?\n' | Where-Object { $_ -ne '' })
}
