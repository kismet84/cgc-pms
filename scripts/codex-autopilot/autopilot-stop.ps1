param()

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

Write-Host "Stop requested. Current task may finish naturally; no new task will start after this checkpoint."
