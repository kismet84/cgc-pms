param()

Unregister-ScheduledTask -TaskName "CGC-PMS Codex AutoPilot Start" -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName "CGC-PMS Codex AutoPilot Stop" -Confirm:$false -ErrorAction SilentlyContinue

Write-Host "AutoPilot schedule uninstalled."
