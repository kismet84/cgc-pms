param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-test-fixture.ps1')
Invoke-AutopilotThemeTest -Scripts @('test-executor-stall.ps1','test-progress-fingerprint.ps1')
Write-Host 'semantic stall theme self-test passed'
