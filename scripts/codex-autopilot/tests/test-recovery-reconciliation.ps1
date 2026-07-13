param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-test-fixture.ps1')
Invoke-AutopilotThemeTest -Scripts @('test-recovery.ps1','test-phase-recovery.ps1')
Write-Host 'recovery reconciliation theme self-test passed'
