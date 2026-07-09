param(
  [string]$Repo = "D:\projects-test\cgc-pms"
)

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

if (Test-Path $StatePath) {
  $State = Get-Content -Raw $StatePath | ConvertFrom-Json
} else {
  $State = [pscustomobject]@{
    enabled = $false
    status = "STARTING"
    startedAt = $Now
    lastHeartbeatAt = $Now
    stopRequested = $false
    autoMerge = $true
    autoPush = $false
    allowTestDataReset = $true
  }
}

$State | Add-Member -NotePropertyName enabled -NotePropertyValue $true -Force
$State | Add-Member -NotePropertyName status -NotePropertyValue "STARTING" -Force
if (!$State.startedAt) {
  $State | Add-Member -NotePropertyName startedAt -NotePropertyValue $Now -Force
}
$State | Add-Member -NotePropertyName lastHeartbeatAt -NotePropertyValue $Now -Force
$State | Add-Member -NotePropertyName stopRequested -NotePropertyValue $false -Force
if ($null -eq $State.autoMerge) {
  $State | Add-Member -NotePropertyName autoMerge -NotePropertyValue $true -Force
}
if ($null -eq $State.autoPush) {
  $State | Add-Member -NotePropertyName autoPush -NotePropertyValue $false -Force
}
if ($null -eq $State.allowTestDataReset) {
  $State | Add-Member -NotePropertyName allowTestDataReset -NotePropertyValue $true -Force
}

$State | ConvertTo-Json | Out-File -Encoding utf8 $StatePath

Write-Host "CGC-PMS Codex AutoPilot started."
