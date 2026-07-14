param(
  [Parameter(Mandatory)][string]$IssueId,
  [string]$CommitMessage = '',
  [string[]]$ExpectedPaths = @(),
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$entry = Join-Path $PSScriptRoot '..\..\plugins\cgc-pms-autopilot\scripts\local-commit-closeout.ps1'
& $entry @PSBoundParameters
