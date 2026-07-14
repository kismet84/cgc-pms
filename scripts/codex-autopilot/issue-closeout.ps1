param(
  [Parameter(Mandatory)][ValidateSet('done','blocked')][string]$CloseoutType,
  [Parameter(Mandatory)][string]$VariablesJson,
  [Parameter(Mandatory)][string]$OutputPath,
  [switch]$Force
)
$ErrorActionPreference = 'Stop'
$entry = Join-Path $PSScriptRoot '..\..\plugins\cgc-pms-autopilot\scripts\issue-closeout.ps1'
& $entry @PSBoundParameters
