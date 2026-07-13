param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-test-fixture.ps1')
Invoke-AutopilotThemeTest -Scripts @('test-closeout.ps1','test-completion-accounting.ps1')
Write-Host 'closeout consistency theme self-test passed'
