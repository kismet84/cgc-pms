param(
  [string]$RepoRoot = "D:\projects-test\cgc-pms",
  [string]$ConfigPath = "",
  [switch]$DryRun,
  [switch]$ApplyBacklogSplit,
  [switch]$ExplainNextAction,
  [Nullable[int]]$MaxIterations = $null,
  [int]$MaxLoops = 20
)

$ErrorActionPreference = "Stop"

$powerShellHostLibrary = Join-Path $PSScriptRoot 'autopilot-powershell-host.ps1'
if (!(Test-Path -LiteralPath $powerShellHostLibrary -PathType Leaf)) { throw 'AUTOPILOT_POWERSHELL7_REQUIRED: host resolver is missing.' }
. $powerShellHostLibrary
Assert-AutopilotPowerShell7 | Out-Null
$script:PowerShellHost = Resolve-AutopilotPowerShellHost

. (Join-Path $PSScriptRoot 'autopilot-run-coordinator.ps1')
Invoke-AutopilotRunCoordinator @PSBoundParameters
