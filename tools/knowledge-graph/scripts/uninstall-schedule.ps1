param(
    [switch]$Confirm
)

$ErrorActionPreference = 'Stop'
$taskName = 'CGC-PMS Knowledge Graph Reconciliation'
if (-not $Confirm) {
    Write-Output "DryRun: would remove scheduled task '$taskName'. Pass -Confirm to execute."
    exit 0
}

if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    Write-Output "Removed scheduled task: $taskName"
} else {
    Write-Output "Scheduled task not present: $taskName"
}
