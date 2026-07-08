param(
  [switch]$ForceKill
)

$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"
$Now = Get-Date -Format s

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null
}

"stop requested at $Now" | Out-File -Encoding utf8 (Join-Path $AutoDir "stop.flag")
Remove-Item (Join-Path $AutoDir "enabled.flag") -ErrorAction SilentlyContinue

if (Test-Path $StatePath) {
  $State = Get-Content -Raw $StatePath | ConvertFrom-Json
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
