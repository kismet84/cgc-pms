param(
  [string]$Repo = "D:\projects-test\cgc-pms",
  [switch]$ForceKill
)

$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"
$LockPath = Join-Path $AutoDir "run.lock"
$ConfigPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "codex-autopilot.config.json"
$MaxRunMinutes = 120
if (Test-Path $ConfigPath) {
  $Config = Get-Content -Encoding UTF8 -Raw $ConfigPath | ConvertFrom-Json
  if ($Config.maxRunMinutes) {
    $MaxRunMinutes = [int]$Config.maxRunMinutes
  }
}
$Now = Get-Date -Format s

function Read-RunLock {
  param([string]$Path)

  if (!(Test-Path $Path)) {
    return $null
  }
  try {
    return Get-Content -Encoding UTF8 -Raw $Path | ConvertFrom-Json
  } catch {
    return [pscustomobject]@{
      owner = "unknown"
      pid = $null
      startedAt = $null
      heartbeatAt = $null
      mode = "unknown"
      issueId = ""
      unreadable = $true
    }
  }
}

function Test-RunLockStale {
  param([object]$Lock, [int]$MaxRunMinutes = 120)

  if (!$Lock) {
    return $false
  }
  if ($Lock.unreadable) {
    return $true
  }
  [datetime]$heartbeat = [datetime]::MinValue
  if ($Lock.heartbeatAt -and [datetime]::TryParse([string]$Lock.heartbeatAt, [ref]$heartbeat)) {
    if (((Get-Date) - $heartbeat).TotalMinutes -gt $MaxRunMinutes) {
      return $true
    }
  }
  if ($Lock.pid) {
    return !(Get-Process -Id ([int]$Lock.pid) -ErrorAction SilentlyContinue)
  }
  return $false
}

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null
}

"stop requested at $Now" | Out-File -Encoding utf8 (Join-Path $AutoDir "stop.flag")
Remove-Item (Join-Path $AutoDir "enabled.flag") -ErrorAction SilentlyContinue

if (Test-Path $StatePath) {
  $State = Get-Content -Encoding UTF8 -Raw $StatePath | ConvertFrom-Json
} else {
  $State = [pscustomobject]@{
    enabled = $false
    status = "STOPPING"
    startedAt = $null
    lastHeartbeatAt = $Now
    stopRequested = $true
    autoMerge = $true
    autoPush = $false
    allowTestDataReset = $true
  }
}

$State.enabled = $false
$State.status = "STOPPING"
$State.stopRequested = $true
$State.lastHeartbeatAt = $Now
$State | ConvertTo-Json | Out-File -Encoding utf8 $StatePath

$Lock = Read-RunLock $LockPath
if (!$Lock) {
  Write-Host "No run.lock found."
} elseif ((Test-RunLockStale $Lock $MaxRunMinutes) -or $ForceKill) {
  Remove-Item -LiteralPath $LockPath -Force -ErrorAction SilentlyContinue
  Write-Host "run.lock removed."
} else {
  Write-Host "Active run.lock kept. Pass -ForceKill to remove it."
  Write-Host "lockOwner=$($Lock.owner)"
  Write-Host "lockPid=$($Lock.pid)"
  Write-Host "lockHeartbeatAt=$($Lock.heartbeatAt)"
}

$Targets = Get-Process | Where-Object {
  $_.ProcessName -match "codex"
} | Select-Object Id, ProcessName, MainWindowTitle

if (!$Targets) {
  Write-Host "No Codex-related processes found."
  return
}

if (!$ForceKill) {
  Write-Host "Stop requested. Matching processes:"
  $Targets | Format-Table -AutoSize
  Write-Host "Pass -ForceKill to actually stop them."
  return
}

$Targets | Stop-Process -Force -ErrorAction SilentlyContinue
Write-Host "Codex-related processes were force-stopped."
