[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$calculator = Join-Path $PSScriptRoot 'measure-codex-task-efficiency.ps1'

function Invoke-Sample([hashtable]$Parameters) {
  return ((& $calculator @Parameters) -join "`n") | ConvertFrom-Json
}

function Assert-Rejected([hashtable]$Parameters, [string]$ExpectedText) {
  $rejected = $false
  try {
    & $calculator @Parameters | Out-Null
  } catch {
    $rejected = $_.Exception.Message -match [regex]::Escape($ExpectedText)
  }
  if (!$rejected) { throw "sample calculator did not reject invalid input: $ExpectedText" }
}

$untimed = Invoke-Sample @{
  SampleType = 'acceptance-audit'
  ToolCallCount = 16
  ToolInvocationCount = 1
  OriginalRetryCount = 1
  CommentaryCount = 2
  OrdinaryTask = $true
}
if ($untimed.schemaVersion -ne 1 -or !$untimed.ordinaryTask) { throw 'sample identity fields are invalid' }
if ($untimed.rates.toolFailureRatePct -ne 6.25) { throw 'tool failure rate calculation is invalid' }
if ($untimed.measurementStatus.controlPlaneShare -ne 'not_available' -or $untimed.rates.controlPlaneSharePct -ne 'not_available') { throw 'missing timing was fabricated' }
if ($untimed.followups.net -ne 0) { throw 'zero follow-up net was calculated incorrectly' }

$timed = Invoke-Sample @{
  SampleType = 'ordinary-repair'
  ToolCallCount = 40
  ToolConfigCount = 1
  CommentaryCount = 3
  ControlPlaneSeconds = 12
  EffectiveExecutionSeconds = 80
  AddedFollowupCount = 2
  ClosedFollowupCount = 1
  OrdinaryTask = $true
}
if ($timed.rates.toolFailureRatePct -ne 2.5 -or $timed.rates.controlPlaneSharePct -ne 15) { throw 'timed rate calculation is invalid' }
if ($timed.measurementStatus.controlPlaneShare -ne 'available' -or $timed.followups.net -ne 1) { throw 'timed sample availability or follow-up net is invalid' }

Assert-Rejected @{SampleType='invalid';ToolCallCount=1;ToolConfigCount=1;ToolInvocationCount=1} 'classified tool outcomes cannot exceed total tool calls'
Assert-Rejected @{SampleType='invalid';ToolCallCount=2;ToolInvocationCount=1;OriginalRetryCount=2} 'original retries must be a subset of tool invocation failures'
Assert-Rejected @{SampleType='invalid';ToolCallCount=2;BrowserCallCount=0;BrowserErrorCount=1} 'browser errors cannot exceed browser calls'
Assert-Rejected @{SampleType='invalid';ToolCallCount=2;ControlPlaneSeconds=1} 'control-plane and effective execution time must be provided together'
Assert-Rejected @{SampleType='invalid';ToolCallCount=2;ControlPlaneSeconds=3;EffectiveExecutionSeconds=2} 'control-plane time cannot exceed effective execution time'

$calculatorText = Get-Content -LiteralPath $calculator -Raw -Encoding UTF8
foreach ($writePrimitive in @('Set-Content','Add-Content','Out-File','WriteAllText','AppendAllText')) {
  if ($calculatorText -match [regex]::Escape($writePrimitive)) { throw "sample calculator contains persistent write primitive: $writePrimitive" }
}

[pscustomobject]@{
  ok = $true
  calculator = 'scripts/codex-autopilot/measure-codex-task-efficiency.ps1'
  toolFailureRatePct = $untimed.rates.toolFailureRatePct
  controlPlaneSharePct = $timed.rates.controlPlaneSharePct
  persistentWrites = 0
} | ConvertTo-Json -Depth 3
