param()

$ErrorActionPreference = 'Stop'
$testDir = Join-Path $PSScriptRoot 'tests'

$tests = @(
  'test-runner-compatibility.ps1'
  'test-stage-result-contract.ps1'
  'test-execution-modes.ps1'
  'test-lock-and-fencing.ps1'
  'test-recovery-reconciliation.ps1'
  'test-semantic-stall.ps1'
  'test-review-routing.ps1'
  'test-closeout-consistency.ps1'
)
foreach ($test in $tests) {
  & (Join-Path $testDir $test)
  if ($LASTEXITCODE -ne 0) { throw "continuous runner theme failed: $test (exit=$LASTEXITCODE)" }
}

Write-Host 'continuous runner self-test passed'
