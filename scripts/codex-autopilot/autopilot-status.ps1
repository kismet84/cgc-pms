param()

$Repo = "D:\projects-test\cgc-pms"
$AutoDir = Join-Path $Repo ".codex-autopilot"
$StatePath = Join-Path $AutoDir "state.json"

$Summary = [ordered]@{
  repo = $Repo
  enabledFlag = Test-Path (Join-Path $AutoDir "enabled.flag")
  stopFlag = Test-Path (Join-Path $AutoDir "stop.flag")
  pauseFlag = Test-Path (Join-Path $AutoDir "pause.flag")
  startFlag = Test-Path (Join-Path $AutoDir "start.flag")
}

if (Test-Path $StatePath) {
  $State = Get-Content -Raw $StatePath | ConvertFrom-Json
  $Summary.stateExists = $true
  $Summary.status = $State.status
  $Summary.enabled = $State.enabled
  $Summary.stopRequested = $State.stopRequested
  $Summary.autoMerge = $State.autoMerge
  $Summary.autoPush = $State.autoPush
  $Summary.allowTestDataReset = $State.allowTestDataReset
  $Summary.startedAt = $State.startedAt
  $Summary.lastHeartbeatAt = $State.lastHeartbeatAt
} else {
  $Summary.stateExists = $false
  $Summary.status = "STOPPED"
  $Summary.message = "No AutoPilot state found."
}

$Summary | ConvertTo-Json
