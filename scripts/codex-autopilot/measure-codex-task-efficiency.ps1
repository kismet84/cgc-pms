[CmdletBinding()]
param(
  [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$SampleType,
  [Parameter(Mandatory)][ValidateRange(1, 2147483647)][int]$ToolCallCount,
  [ValidateRange(0, 2147483647)][int]$ToolConfigCount = 0,
  [ValidateRange(0, 2147483647)][int]$ToolInvocationCount = 0,
  [ValidateRange(0, 2147483647)][int]$RetrievalGapCount = 0,
  [ValidateRange(0, 2147483647)][int]$EnvironmentPrerequisiteCount = 0,
  [ValidateRange(0, 2147483647)][int]$QualityOrSecurityCount = 0,
  [ValidateRange(0, 2147483647)][int]$OriginalRetryCount = 0,
  [ValidateRange(0, 2147483647)][int]$BrowserCallCount = 0,
  [ValidateRange(0, 2147483647)][int]$BrowserErrorCount = 0,
  [ValidateRange(0, 2147483647)][int]$CommentaryCount = 0,
  [Nullable[double]]$ControlPlaneSeconds,
  [Nullable[double]]$EffectiveExecutionSeconds,
  [ValidateRange(0, 2147483647)][int]$InterruptedReimplementationCount = 0,
  [ValidateRange(0, 2147483647)][int]$AddedFollowupCount = 0,
  [ValidateRange(0, 2147483647)][int]$ClosedFollowupCount = 0,
  [switch]$OrdinaryTask
)

$ErrorActionPreference = 'Stop'

$classifiedCount = $ToolConfigCount + $ToolInvocationCount + $RetrievalGapCount + $EnvironmentPrerequisiteCount + $QualityOrSecurityCount
if ($classifiedCount -gt $ToolCallCount) { throw 'classified tool outcomes cannot exceed total tool calls' }
if ($OriginalRetryCount -gt $ToolInvocationCount) { throw 'original retries must be a subset of tool invocation failures' }
if ($BrowserErrorCount -gt $BrowserCallCount) { throw 'browser errors cannot exceed browser calls' }

$hasControlTime = $PSBoundParameters.ContainsKey('ControlPlaneSeconds')
$hasEffectiveTime = $PSBoundParameters.ContainsKey('EffectiveExecutionSeconds')
if ($hasControlTime -ne $hasEffectiveTime) { throw 'control-plane and effective execution time must be provided together' }
if ($hasControlTime) {
  if ([double]$ControlPlaneSeconds -lt 0) { throw 'control-plane time cannot be negative' }
  if ([double]$EffectiveExecutionSeconds -le 0) { throw 'effective execution time must be greater than zero' }
  if ([double]$ControlPlaneSeconds -gt [double]$EffectiveExecutionSeconds) { throw 'control-plane time cannot exceed effective execution time' }
}

$toolFailureRate = [Math]::Round((100.0 * ($ToolConfigCount + $ToolInvocationCount) / $ToolCallCount), 2)
$controlPlaneShare = if ($hasControlTime) {
  [Math]::Round((100.0 * [double]$ControlPlaneSeconds / [double]$EffectiveExecutionSeconds), 2)
} else {
  'not_available'
}

[ordered]@{
  schemaVersion = 1
  sampleType = $SampleType
  ordinaryTask = [bool]$OrdinaryTask
  counts = [ordered]@{
    toolCalls = $ToolCallCount
    toolConfig = $ToolConfigCount
    toolInvocation = $ToolInvocationCount
    retrievalGap = $RetrievalGapCount
    environmentPrerequisite = $EnvironmentPrerequisiteCount
    qualityOrSecurity = $QualityOrSecurityCount
    originalRetries = $OriginalRetryCount
    browserCalls = $BrowserCallCount
    browserErrors = $BrowserErrorCount
    commentary = $CommentaryCount
    interruptedReimplementation = $InterruptedReimplementationCount
  }
  rates = [ordered]@{
    toolFailureRatePct = $toolFailureRate
    controlPlaneSharePct = $controlPlaneShare
  }
  measurementStatus = [ordered]@{
    toolFailureRate = 'available'
    controlPlaneShare = $(if ($hasControlTime) { 'available' } else { 'not_available' })
  }
  followups = [ordered]@{
    added = $AddedFollowupCount
    closed = $ClosedFollowupCount
    net = $AddedFollowupCount - $ClosedFollowupCount
  }
} | ConvertTo-Json -Depth 5
