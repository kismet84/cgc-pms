[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$PlanPath,
  [ValidateSet('Light','Standard','HighRisk')][string]$Profile = 'Standard'
)

$ErrorActionPreference = 'Stop'
$tests = @(
  'test-codex-task-efficiency.ps1',
  'test-codex-task-execution-policy.ps1',
  'test-codex-task-git-lifecycle.ps1',
  'test-tool-routing.ps1',
  'test-control-plane-fingerprint.ps1'
)
$completed = [System.Collections.Generic.List[string]]::new()

foreach ($test in $tests) {
  $output = & pwsh -NoProfile -File (Join-Path $PSScriptRoot $test) 2>&1
  if ($LASTEXITCODE -ne 0) { throw "Codex task policy suite failed: $test`n$($output -join "`n")" }
  $completed.Add($test)
}

$mainlineTest = Join-Path $PSScriptRoot 'test-mainline-owner-flow.ps1'
$mainlineOutput = & pwsh -NoProfile -File $mainlineTest -PlanPath $PlanPath -Profile $Profile 2>&1
if ($LASTEXITCODE -ne 0) { throw "Codex task policy suite failed: test-mainline-owner-flow.ps1`n$($mainlineOutput -join "`n")" }
$completed.Add('test-mainline-owner-flow.ps1')

[pscustomobject]@{
  ok = $true
  profile = $Profile
  plan = $PlanPath
  tests = @($completed)
  testCount = $completed.Count
} | ConvertTo-Json -Depth 3
