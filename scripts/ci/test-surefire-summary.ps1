param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'summarize-surefire.ps1')

$fixture = Join-Path ([IO.Path]::GetTempPath()) "cgc-pms-surefire-$([guid]::NewGuid())"
New-Item -ItemType Directory -Path $fixture | Out-Null
try {
  [IO.File]::WriteAllText((Join-Path $fixture 'TEST-a.xml'), '<testsuite name="A" tests="2" failures="1" errors="0" skipped="0" time="1.25"/>')
  [IO.File]::WriteAllText((Join-Path $fixture 'TEST-b.xml'), '<testsuite name="B" tests="3" failures="0" errors="0" skipped="1" time="4.5"/>')
  $summary = Get-SurefireSummary $fixture
  if ($summary.tests -ne 5 -or $summary.failures -ne 1 -or $summary.skipped -ne 1) {
    throw 'Surefire totals are incorrect'
  }
  if ($summary.slowest[0].name -ne 'B' -or $summary.seconds -ne 5.75) {
    throw 'Surefire duration ranking is incorrect'
  }
} finally {
  Remove-Item -LiteralPath $fixture -Recurse -Force
}

Write-Host 'Surefire summary self-test passed'
