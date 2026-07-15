param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-test-fixture.ps1')
Invoke-AutopilotThemeTest -Scripts @('test-review-repair.ps1')
Write-Host 'review routing theme self-test passed'
