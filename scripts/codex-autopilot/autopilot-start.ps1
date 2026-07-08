param()

$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"
$Now = Get-Date -Format s

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null
}

Remove-Item (Join-Path $AutoDir "stop.flag") -ErrorAction SilentlyContinue
Remove-Item (Join-Path $AutoDir "pause.flag") -ErrorAction SilentlyContinue

"started at $Now" | Out-File -Encoding utf8 (Join-Path $AutoDir "start.flag")
"enabled" | Out-File -Encoding utf8 (Join-Path $AutoDir "enabled.flag")

$State = [ordered]@{
  enabled = $true
  status = "STARTING"
  startedAt = $Now
  lastHeartbeatAt = $Now
  stopRequested = $false
  autoMerge = $true
  autoPush = $false
  allowTestDataReset = $true
}

$State | ConvertTo-Json | Out-File -Encoding utf8 $StatePath

Write-Host "CGC-PMS Codex AutoPilot started."
