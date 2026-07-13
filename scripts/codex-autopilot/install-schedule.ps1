param(
  [string]$Repo = "D:\projects-test\cgc-pms",
  [string]$StartTime = "09:00",
  [string]$StopTime = "23:30"
)

$StartScript = Join-Path $Repo "scripts\codex-autopilot\autopilot-start.ps1"
$StopScript = Join-Path $Repo "scripts\codex-autopilot\autopilot-stop.ps1"
$StartAt = [datetime]::Today.Add([timespan]::ParseExact($StartTime, "hh\:mm", $null))
$StopAt = [datetime]::Today.Add([timespan]::ParseExact($StopTime, "hh\:mm", $null))

$StartAction = New-ScheduledTaskAction `
  -Execute "pwsh" `
  -Argument "-ExecutionPolicy Bypass -File `"$StartScript`""

$StartTrigger = New-ScheduledTaskTrigger -Daily -At $StartAt

Register-ScheduledTask `
  -TaskName "CGC-PMS Codex AutoPilot Start" `
  -Action $StartAction `
  -Trigger $StartTrigger `
  -Description "Start CGC-PMS Codex AutoPilot" `
  -Force | Out-Null

$StopAction = New-ScheduledTaskAction `
  -Execute "pwsh" `
  -Argument "-ExecutionPolicy Bypass -File `"$StopScript`""

$StopTrigger = New-ScheduledTaskTrigger -Daily -At $StopAt

Register-ScheduledTask `
  -TaskName "CGC-PMS Codex AutoPilot Stop" `
  -Action $StopAction `
  -Trigger $StopTrigger `
  -Description "Stop CGC-PMS Codex AutoPilot" `
  -Force | Out-Null

Write-Host "AutoPilot schedule installed."
Write-Host "Start time: $StartTime"
Write-Host "Stop time:  $StopTime"
