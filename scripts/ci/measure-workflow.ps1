[CmdletBinding()]
param(
  [string]$WorkflowPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path '.github\workflows\ci.yml')
)

$ErrorActionPreference = 'Stop'
$resolved = (Resolve-Path -LiteralPath $WorkflowPath).Path
$lines = @(Get-Content -LiteralPath $resolved -Encoding UTF8)
$jobsLine = [Array]::IndexOf($lines, 'jobs:')
if ($jobsLine -lt 0) { throw "workflow jobs mapping is missing: $resolved" }

$jobLines = @($lines[($jobsLine + 1)..($lines.Count - 1)] | Where-Object { $_ -match '^  [a-z0-9][a-z0-9-]*:$' })
$longInlineRuns = 0
for ($index = 0; $index -lt $lines.Count; $index++) {
  if ($lines[$index] -notmatch '^(\s*)run: \|\s*$') { continue }
  $indent = $matches[1].Length
  $bodyLines = 0
  for ($bodyIndex = $index + 1; $bodyIndex -lt $lines.Count; $bodyIndex++) {
    if ($lines[$bodyIndex].Trim().Length -eq 0) { continue }
    $bodyIndent = $lines[$bodyIndex].Length - $lines[$bodyIndex].TrimStart().Length
    if ($bodyIndent -le $indent) { break }
    $bodyLines++
  }
  if ($bodyLines -ge 5) { $longInlineRuns++ }
}

[pscustomobject]@{
  workflow = $resolved
  lineCount = $lines.Count
  jobCount = $jobLines.Count
  stepCount = @($lines | Where-Object { $_ -match '^      - (?:name|uses):' }).Count
  checkout = @($lines | Where-Object { $_ -match 'uses: actions/checkout@' }).Count
  setupJava = @($lines | Where-Object { $_ -match 'uses: actions/setup-java@' }).Count
  setupNode = @($lines | Where-Object { $_ -match 'uses: actions/setup-node@' }).Count
  setupPnpm = @($lines | Where-Object { $_ -match 'uses: pnpm/action-setup@' }).Count
  setupBackendComposite = @($lines | Where-Object { $_ -match 'uses: \./\.github/actions/setup-backend' }).Count
  setupFrontendComposite = @($lines | Where-Object { $_ -match 'uses: \./\.github/actions/setup-frontend' }).Count
  inlineRunBlocks = @($lines | Where-Object { $_ -match '^\s+run: \|\s*$' }).Count
  longInlineRunBlocksGte5 = $longInlineRuns
  uploadArtifact = @($lines | Where-Object { $_ -match 'uses: actions/upload-artifact@' }).Count
  downloadArtifact = @($lines | Where-Object { $_ -match 'uses: actions/download-artifact@' }).Count
} | ConvertTo-Json -Depth 3
