$ErrorActionPreference = 'Stop'

function Get-AutopilotRunLockMutexName {
  param([Parameter(Mandatory)][string]$LockPath)
  $normalized = [IO.Path]::GetFullPath($LockPath).ToLowerInvariant()
  $sha = [Security.Cryptography.SHA256]::Create()
  try { $hash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($normalized)))).Replace('-', '') }
  finally { $sha.Dispose() }
  return 'Local\cgc-pms-autopilot-run-lock-' + $hash.Substring(0, 24)
}

function Invoke-WithAutopilotRunLockMutex {
  param([Parameter(Mandatory)][string]$LockPath, [Parameter(Mandatory)][scriptblock]$Action, [int]$TimeoutSeconds = 10)
  $mutex = [Threading.Mutex]::new($false, (Get-AutopilotRunLockMutexName -LockPath $LockPath))
  $owned = $false
  try {
    try { $owned = $mutex.WaitOne([TimeSpan]::FromSeconds($TimeoutSeconds)) }
    catch [Threading.AbandonedMutexException] { $owned = $true }
    if (!$owned) { throw 'Timed out waiting for the AutoPilot run-lock acquisition mutex.' }
    return & $Action
  } finally {
    if ($owned) { try { $mutex.ReleaseMutex() } catch {} }
    $mutex.Dispose()
  }
}

function Read-AutopilotRunLockFile {
  param([Parameter(Mandatory)][string]$LockPath, [switch]$AllowMissing)
  if (!(Test-Path -LiteralPath $LockPath -PathType Leaf)) {
    if ($AllowMissing) { return $null }
    throw "AutoPilot run.lock is missing: $LockPath"
  }
  try { return Get-Content -LiteralPath $LockPath -Raw -Encoding UTF8 | ConvertFrom-Json }
  catch { throw "AutoPilot run.lock is invalid JSON: $LockPath" }
}

function Test-AutopilotRunLockStale {
  param([Parameter(Mandatory)][object]$Lock, [int]$MaxRunMinutes = 120)
  if ($Lock.pid) {
    $process = Get-Process -Id ([int]$Lock.pid) -ErrorAction SilentlyContinue
    if ($process) { return $false }
  }
  [datetimeoffset]$heartbeat = [datetimeoffset]::MinValue
  if ($Lock.heartbeatAt -and [datetimeoffset]::TryParse([string]$Lock.heartbeatAt, [ref]$heartbeat)) {
    return ([datetimeoffset]::Now - $heartbeat).TotalMinutes -gt $MaxRunMinutes -or !$Lock.pid
  }
  return $true
}

function Test-AutopilotRunFence {
  param(
    [Parameter(Mandatory)][string]$LockPath,
    [Parameter(Mandatory)][string]$RunInstanceId,
    [Parameter(Mandatory)][string]$LeaseEpoch
  )
  try { $current = Read-AutopilotRunLockFile -LockPath $LockPath }
  catch { return $false }
  return [string]$current.runInstanceId -eq $RunInstanceId -and [string]$current.leaseEpoch -eq $LeaseEpoch
}

function New-AutopilotRunLock {
  param(
    [Parameter(Mandatory)][string]$AutoDir,
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$RunId,
    [Parameter(Mandatory)][string]$Mode,
    [string]$ControlPlaneFingerprint = '',
    [string]$ExecutionHost = 'cli-legacy',
    [int]$MaxRunMinutes = 120
  )
  if (!(Test-Path -LiteralPath $AutoDir)) { New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null }
  $lockPath = Join-Path $AutoDir 'run.lock'
  return Invoke-WithAutopilotRunLockMutex -LockPath $lockPath -Action {
    $existing = Read-AutopilotRunLockFile -LockPath $lockPath -AllowMissing
    if ($existing -and !(Test-AutopilotRunLockStale -Lock $existing -MaxRunMinutes $MaxRunMinutes)) {
      throw "Another AutoPilot run is active (pid=$($existing.pid), runInstanceId=$($existing.runInstanceId))."
    }
    $tookOverStale = $null -ne $existing
    if ($existing) { Remove-Item -LiteralPath $lockPath -Force }

    $now = [datetimeoffset]::Now
    $lock = [ordered]@{
      schemaVersion = 2
      owner = "$env:USERNAME@$env:COMPUTERNAME"
      pid = $PID
      runId = $RunId
      runInstanceId = [guid]::NewGuid().ToString('N')
      leaseEpoch = ($now.UtcTicks.ToString() + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
      host = $env:COMPUTERNAME
      workspaceRoot = $RepoRoot
      startedAt = $now.ToString('o')
      heartbeatAt = $now.ToString('o')
      mode = $Mode
      controlPlaneFingerprint = $ControlPlaneFingerprint
      executionHost = $ExecutionHost
      issueId = ''
      tookOverStale = $tookOverStale
    }
    $stream = [IO.File]::Open($lockPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
      $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($lock | ConvertTo-Json -Depth 6))
      $stream.Write($bytes, 0, $bytes.Length)
      $stream.Flush($true)
    } finally { $stream.Dispose() }
    return [pscustomobject]$lock
  }
}

function Update-AutopilotRunLock {
  param([Parameter(Mandatory)][string]$LockPath, [Parameter(Mandatory)][object]$Lock)
  return Invoke-WithAutopilotRunLockMutex -LockPath $LockPath -Action {
    $current = Read-AutopilotRunLockFile -LockPath $LockPath
    if ([string]$current.runInstanceId -ne [string]$Lock.runInstanceId -or [string]$current.leaseEpoch -ne [string]$Lock.leaseEpoch) {
      throw 'AUTOPILOT_FENCE_REJECTED: run.lock ownership changed.'
    }
    $Lock.heartbeatAt = [datetimeoffset]::Now.ToString('o')
    $json = $Lock | ConvertTo-Json -Depth 6
    $temp = "$LockPath.$([guid]::NewGuid().ToString('N')).tmp"
    $backup = "$LockPath.$([guid]::NewGuid().ToString('N')).bak"
    try {
      [IO.File]::WriteAllText($temp, $json, [Text.UTF8Encoding]::new($false))
      [IO.File]::Replace($temp, $LockPath, $backup, $true)
    } finally {
      Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
      Remove-Item -LiteralPath $backup -Force -ErrorAction SilentlyContinue
    }
    return $Lock
  }
}

function Remove-AutopilotRunLock {
  param([Parameter(Mandatory)][string]$LockPath, [Parameter(Mandatory)][object]$Lock)
  return Invoke-WithAutopilotRunLockMutex -LockPath $LockPath -Action {
    $current = Read-AutopilotRunLockFile -LockPath $LockPath -AllowMissing
    if (!$current) { return $false }
    if ([string]$current.runInstanceId -ne [string]$Lock.runInstanceId -or [string]$current.leaseEpoch -ne [string]$Lock.leaseEpoch) {
      throw 'AUTOPILOT_FENCE_REJECTED: stale owner cannot remove run.lock.'
    }
    Remove-Item -LiteralPath $LockPath -Force
    return $true
  }
}
