param(
  [string]$ErrorText = '',
  [int]$ExitCode = 1,
  [string]$OutputPath
)
$ErrorActionPreference = 'Stop'
$entry = Join-Path $PSScriptRoot '..\..\plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1'
& $entry @PSBoundParameters
