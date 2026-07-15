param(
    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$taskName = 'CGC-PMS Knowledge Graph Reconciliation'
$collectScript = (Resolve-Path (Join-Path $PSScriptRoot 'collect.ps1')).Path
$powerShell = (Get-Command powershell.exe).Source
$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$collectScript`" -Trigger scheduled"

if (-not $Install) {
    [pscustomobject]@{
        Mode = 'DryRun'
        TaskName = $taskName
        Executable = $powerShell
        Argument = $argument
        IntervalMinutes = 30
    } | Format-List
    exit 0
}

$action = New-ScheduledTaskAction -Execute $powerShell -Argument $argument
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes 30) `
    -RepetitionDuration (New-TimeSpan -Days 3650)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings `
    -Description 'Reconcile the local cgc-pms Neo4j knowledge graph every 30 minutes.' -Force | Out-Null
Write-Output "Installed scheduled task: $taskName"
