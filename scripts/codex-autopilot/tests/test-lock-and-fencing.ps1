param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-test-fixture.ps1')
Invoke-AutopilotThemeTest -Scripts @('test-run-lock-fencing.ps1','test-control-plane-fingerprint.ps1')
& (Join-Path $PSScriptRoot 'test-transition-writer.ps1')
Write-Host 'lock and fencing theme self-test passed'
