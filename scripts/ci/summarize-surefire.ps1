[CmdletBinding()]
param(
  [string]$ReportsPath = 'backend/target/surefire-reports'
)

$ErrorActionPreference = 'Stop'

function Get-SurefireSummary([string]$Path) {
  $files = @(Get-ChildItem -LiteralPath $Path -Filter 'TEST-*.xml' -File)
  if ($files.Count -eq 0) { throw "SUREFIRE_REPORTS_MISSING: $Path" }

  $suites = foreach ($file in $files) {
    [xml]$document = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $suite = $document.testsuite
    if (!$suite) { throw "SUREFIRE_REPORT_INVALID: $($file.FullName)" }
    [pscustomobject]@{
      name = [string]$suite.name
      tests = [int]$suite.tests
      failures = [int]$suite.failures
      errors = [int]$suite.errors
      skipped = [int]$suite.skipped
      seconds = [double]::Parse([string]$suite.time, [Globalization.CultureInfo]::InvariantCulture)
    }
  }

  [pscustomobject]@{
    files = $files.Count
    tests = ($suites | Measure-Object tests -Sum).Sum
    failures = ($suites | Measure-Object failures -Sum).Sum
    errors = ($suites | Measure-Object errors -Sum).Sum
    skipped = ($suites | Measure-Object skipped -Sum).Sum
    seconds = [math]::Round(($suites | Measure-Object seconds -Sum).Sum, 3)
    slowest = @($suites | Sort-Object seconds -Descending | Select-Object -First 20 name,seconds)
  }
}

if ($MyInvocation.InvocationName -ne '.') {
  $summary = Get-SurefireSummary $ReportsPath
  $lines = @(
    '## Backend Surefire Summary',
    '',
    "- Report files: $($summary.files)",
    "- Tests: $($summary.tests); failures: $($summary.failures); errors: $($summary.errors); skipped: $($summary.skipped)",
    "- Aggregated test time: $($summary.seconds)s",
    '',
    '### Slowest 20 test classes',
    '',
    '| Class | Seconds |',
    '| --- | ---: |'
  )
  $lines += $summary.slowest | ForEach-Object { "| $($_.name) | $($_.seconds) |" }
  $text = ($lines -join "`n") + "`n"
  Write-Host $text
  if ($env:GITHUB_STEP_SUMMARY) {
    [IO.File]::AppendAllText($env:GITHUB_STEP_SUMMARY, $text, [Text.UTF8Encoding]::new($false))
  }
}
