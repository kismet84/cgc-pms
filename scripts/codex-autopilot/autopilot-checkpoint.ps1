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
$arguments = @(
  '-NoProfile',
  '-ExecutionPolicy', 'Bypass',
  '-File', $pluginCheckpoint,
  '-RepoRoot', $RepoRoot,
  '-AutopilotDir', $AutopilotDir
)
if ($CheckHealth) { $arguments += '-CheckHealth' }
if ($CheckGit) { $arguments += '-CheckGit' }
if ($AsJson) { $arguments += '-AsJson' }
& powershell @arguments
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
