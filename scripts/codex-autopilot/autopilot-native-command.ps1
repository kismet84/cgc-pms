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
  $allowedConfigOverrides = @('core.autocrlf=false','core.safecrlf=false','core.longpaths=true')
  $commandIndex = 0
  while ($commandIndex -lt $Arguments.Count -and $Arguments[$commandIndex] -eq '-c') {
    if ($commandIndex + 1 -ge $Arguments.Count -or $Arguments[$commandIndex + 1] -notin $allowedConfigOverrides) {
      throw 'AUTOPILOT_GIT_GLOBAL_OPTION_FORBIDDEN: only approved wrapper configuration overrides are allowed.'
    }
    $commandIndex += 2
  }
  if ($commandIndex -ge $Arguments.Count -or ([string]$Arguments[$commandIndex]).StartsWith('-')) {
    throw 'AUTOPILOT_GIT_GLOBAL_OPTION_FORBIDDEN: callers must not supply Git global options.'
  }
  $command = ([string]$Arguments[$commandIndex]).ToLowerInvariant()
  $subcommand = if ($commandIndex + 1 -lt $Arguments.Count) { ([string]$Arguments[$commandIndex + 1]).ToLowerInvariant() } else { '' }
  $readOnlyCommands = @('check-ignore','diff','log','ls-files','merge-base','rev-parse','show','show-ref','status')
  $requiredAuthorization = switch ($command) {
    { $_ -in @('add','commit') } { 'Commit'; break }
    'merge' { 'Merge'; break }
    'worktree' {
      if ($subcommand -in @('add','remove')) { 'Branch'; break }
      if ($subcommand -eq 'list') { break }
      throw "AUTOPILOT_GIT_COMMAND_NOT_ALLOWED: Git $command $subcommand is not an approved control-plane operation."
    }
    'branch' { if ($subcommand -ne '--show-current') { 'Branch' }; break }
    { $_ -in @('switch','checkout') } { 'Branch'; break }
    { $_ -in $readOnlyCommands } { break }
    default { throw "AUTOPILOT_GIT_COMMAND_NOT_ALLOWED: Git $command is not an approved control-plane operation." }
  }
  if ($requiredAuthorization) {
    $authorizationVariable = Get-Variable -Name "Autopilot$($requiredAuthorization)Authorized" -Scope Script -ErrorAction SilentlyContinue
    if ($null -eq $authorizationVariable -or ![bool]$authorizationVariable.Value) {
      throw "AUTOPILOT_GIT_$($requiredAuthorization.ToUpperInvariant())_AUTHORIZATION_REQUIRED: Git $command $subcommand requires explicit authorization for this run."
    }
  }
  $gitArguments = @('-c','core.quotePath=false','-C',$RepoRoot) + @($Arguments)
  return Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments $gitArguments -AcceptedExitCodes $AcceptedExitCodes -TimeoutSeconds $TimeoutSeconds -ThrowOnFailure:$ThrowOnFailure
}

function Get-AutopilotNativeOutputLines {
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return @() }
  return @($Text -split '\r?\n' | Where-Object { $_ -ne '' })
}
