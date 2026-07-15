param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [string]$AutopilotDir = '.codex-autopilot',
  [switch]$CheckHealth,
  [switch]$CheckGit,
  [switch]$AsJson
)

$ErrorActionPreference = 'Stop'
$pluginCheckpoint = Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\scripts\autopilot-checkpoint.ps1'
if (!(Test-Path -LiteralPath $pluginCheckpoint -PathType Leaf)) { throw "plugin checkpoint is missing: $pluginCheckpoint" }
$arguments = @{
  RepoRoot = $RepoRoot
  AutopilotDir = $AutopilotDir
  CheckHealth = [bool]$CheckHealth
  CheckGit = [bool]$CheckGit
  AsJson = [bool]$AsJson
}
& $pluginCheckpoint @arguments
