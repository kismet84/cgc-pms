param(
  [Parameter(Mandatory)][string]$VariablesJson,
  [string]$TemplatePath,
  [Parameter(Mandatory)][string]$OutputPath,
  [switch]$Force
)
$ErrorActionPreference = 'Stop'
$entry = Join-Path $PSScriptRoot '..\..\plugins\cgc-pms-autopilot\scripts\ready-issue-writer.ps1'
& $entry @PSBoundParameters
