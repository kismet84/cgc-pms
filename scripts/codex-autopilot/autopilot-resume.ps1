param()

$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"
$Now = Get-Date -Format s

if (!(Test-Path $AutoDir)) {
  New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null
}

Remove-Item (Join-Path $AutoDir "pause.flag") -ErrorAction SilentlyContinue

if (Test-Path $StatePath) {
  $State = Get-Content -Encoding UTF8 -Raw $StatePath | ConvertFrom-Json
  $State.status = "STARTING"
  $State.lastHeartbeatAt = $Now
  $State | ConvertTo-Json | Out-File -Encoding utf8 $StatePath
}

Write-Host "CGC-PMS Codex AutoPilot resumed."
