param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-run-lock.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-run-lock-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $lockPath = Join-Path $root 'run.lock'
  $first = New-AutopilotRunLock -AutoDir $root -RepoRoot $root -RunId 'run-1' -Mode 'APPLY'
  if (!(Test-AutopilotRunFence -LockPath $lockPath -RunInstanceId $first.runInstanceId -LeaseEpoch $first.leaseEpoch)) { throw 'new run lock did not establish a valid fence' }

  $secondRejected = $false
  try { New-AutopilotRunLock -AutoDir $root -RepoRoot $root -RunId 'run-2' -Mode 'APPLY' | Out-Null } catch { $secondRejected = $_.Exception.Message -match 'active' }
  if (!$secondRejected) { throw 'second APPLY acquired an active run lock' }

  $stale = $first | ConvertTo-Json -Depth 6 | ConvertFrom-Json
  $stale.pid = 2147483000
  $stale.heartbeatAt = [datetimeoffset]::Now.AddHours(-3).ToString('o')
  [IO.File]::WriteAllText($lockPath, ($stale | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
  $second = New-AutopilotRunLock -AutoDir $root -RepoRoot $root -RunId 'run-2' -Mode 'APPLY' -MaxRunMinutes 1
  if ($second.runInstanceId -eq $first.runInstanceId -or $second.leaseEpoch -eq $first.leaseEpoch) { throw 'lock takeover reused the stale fencing token' }
  if (Test-AutopilotRunFence -LockPath $lockPath -RunInstanceId $first.runInstanceId -LeaseEpoch $first.leaseEpoch) { throw 'stale owner fence remained valid after takeover' }

  $writeRejected = $false
  try { Update-AutopilotRunLock -LockPath $lockPath -Lock $first | Out-Null } catch { $writeRejected = $_.Exception.Message -match 'FENCE_REJECTED' }
  if (!$writeRejected) { throw 'stale owner updated the replacement run lock' }
  $removeRejected = $false
  try { Remove-AutopilotRunLock -LockPath $lockPath -Lock $first | Out-Null } catch { $removeRejected = $_.Exception.Message -match 'FENCE_REJECTED' }
  if (!$removeRejected) { throw 'stale owner removed the replacement run lock' }
  if (!(Remove-AutopilotRunLock -LockPath $lockPath -Lock $second)) { throw 'current owner could not remove its run lock' }

  Write-Host 'run lock fencing self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
