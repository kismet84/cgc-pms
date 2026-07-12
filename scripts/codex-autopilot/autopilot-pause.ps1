param()

$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"
$Now = Get-Date -Format s

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null
}

"paused at $Now" | Out-File -Encoding utf8 (Join-Path $AutoDir "pause.flag")

if (Test-Path $StatePath) {
  $State = Get-Content -Encoding UTF8 -Raw $StatePath | ConvertFrom-Json
  $State.status = "PAUSED"
  $State.lastHeartbeatAt = $Now
  $State | ConvertTo-Json | Out-File -Encoding utf8 $StatePath
}

Write-Host "CGC-PMS Codex AutoPilot paused."
