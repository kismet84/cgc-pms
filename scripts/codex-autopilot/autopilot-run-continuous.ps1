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

$executionHostLibrary = Join-Path $PSScriptRoot 'autopilot-execution-host.ps1'
if (!(Test-Path -LiteralPath $executionHostLibrary -PathType Leaf)) { throw 'AUTOPILOT_EXECUTION_HOST_LIBRARY_MISSING' }
. $executionHostLibrary
$resolvedConfigPath = if ($ConfigPath) { $ConfigPath } else { Join-Path $PSScriptRoot 'codex-autopilot.config.json' }
if (!(Test-Path -LiteralPath $resolvedConfigPath -PathType Leaf)) { throw "Config not found: $resolvedConfigPath" }
$entryConfig = Get-Content -LiteralPath $resolvedConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
$resolvedRepoRoot = if ($entryConfig.repoRoot) { [string]$entryConfig.repoRoot } else { $RepoRoot }
if (Test-AutopilotDesktopNativeHost -Config $entryConfig) {
  $handoff = New-AutopilotDesktopHandoff -RepoRoot $resolvedRepoRoot -ConfigPath $resolvedConfigPath -MaxIterations $MaxIterations -DryRun ([bool]$DryRun) -ApplyBacklogSplit ([bool]$ApplyBacklogSplit) -ExplainNextAction ([bool]$ExplainNextAction)
  Write-Output ($handoff | ConvertTo-Json -Depth 5)
  return
}

. (Join-Path $PSScriptRoot 'autopilot-run-coordinator.ps1')
Invoke-AutopilotRunCoordinator @PSBoundParameters
