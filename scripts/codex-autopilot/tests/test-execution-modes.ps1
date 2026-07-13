param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-test-fixture.ps1')
Invoke-AutopilotThemeTest -Scripts @('test-execution-mode-matrix.ps1')
Write-Host 'execution modes theme self-test passed'
