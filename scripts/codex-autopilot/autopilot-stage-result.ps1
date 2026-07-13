$script:AutopilotStageOutcomes = @('SUCCEEDED','RETRYABLE','PAUSED','BLOCKED','TERMINAL')
$script:AutopilotFailureCategories = @('none','tool_config','environment','environment_prereq','quality_security','ready_issue_config','integrity_conflict','executor_stall_timeout')

function New-AutopilotStageResult {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][ValidateSet('SUCCEEDED','RETRYABLE','PAUSED','BLOCKED','TERMINAL')][string]$Outcome,
    [string]$NextStage = '',
    [string]$FailureCategory = 'none',
    [string]$StopReason = '',
    [string]$Reason = '',
    [bool]$SemanticProgress = $false,
    [string[]]$EvidencePaths = @(),
    [string]$RetryKey = '',
    [string]$TransitionIntent = ''
  )

  if ($script:AutopilotFailureCategories -notcontains $FailureCategory) {
    throw "Unsupported AutoPilot failure category: $FailureCategory"
  }
  $result = [pscustomobject][ordered]@{
    schemaVersion = 1
    issueId = $IssueId
    stage = $Stage.ToUpperInvariant()
    outcome = $Outcome
    nextStage = $NextStage.ToUpperInvariant()
    failureCategory = $FailureCategory
    stopReason = $StopReason
    reason = $Reason
    semanticProgress = $SemanticProgress
    evidencePaths = @($EvidencePaths | Where-Object { $_ } | Select-Object -Unique)
    retryKey = $RetryKey
    transitionIntent = $TransitionIntent.ToUpperInvariant()
  }
  Assert-AutopilotStageResult -Result $result | Out-Null
  return $result
}

function Assert-AutopilotStageResult {
  param([Parameter(Mandatory)][object]$Result)

  $required = @('schemaVersion','issueId','stage','outcome','nextStage','failureCategory','stopReason','reason','semanticProgress','evidencePaths','retryKey','transitionIntent')
  foreach ($name in $required) {
    if ($Result.PSObject.Properties.Name -notcontains $name) { throw "StageResult missing field: $name" }
  }
  if ([int]$Result.schemaVersion -ne 1) { throw "Unsupported StageResult schemaVersion: $($Result.schemaVersion)" }
  if ([string]::IsNullOrWhiteSpace([string]$Result.issueId) -or [string]::IsNullOrWhiteSpace([string]$Result.stage)) { throw 'StageResult issueId and stage are required' }
  if ($script:AutopilotStageOutcomes -notcontains [string]$Result.outcome) { throw "Unsupported StageResult outcome: $($Result.outcome)" }
  if ($script:AutopilotFailureCategories -notcontains [string]$Result.failureCategory) { throw "Unsupported StageResult failure category: $($Result.failureCategory)" }
  if ([string]$Result.outcome -in @('BLOCKED','PAUSED','RETRYABLE') -and [string]::IsNullOrWhiteSpace([string]$Result.stopReason)) { throw "StageResult $($Result.outcome) requires stopReason" }
  if ([string]$Result.outcome -eq 'RETRYABLE' -and [string]::IsNullOrWhiteSpace([string]$Result.retryKey)) { throw 'Retryable StageResult requires retryKey' }
  return $true
}

function ConvertTo-AutopilotStageResult {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][object]$ExecutorResult,
    [string[]]$EvidencePaths = @(),
    [bool]$Closed = $false
  )

  $failureCategory = if ($ExecutorResult.failureCategory) { [string]$ExecutorResult.failureCategory } else { 'none' }
  if ($failureCategory -eq 'executor_stall') { $failureCategory = 'executor_stall_timeout' }
  $stopReason = [string]$ExecutorResult.stopReason
  $nextStage = [string]$ExecutorResult.nextAction
  $outcome = if ($Closed) {
    'TERMINAL'
  } elseif ([string]$ExecutorResult.status -eq 'done') {
    'SUCCEEDED'
  } elseif ($stopReason -eq 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED' -or $failureCategory -in @('tool_config','environment','environment_prereq')) {
    'PAUSED'
  } elseif ($nextStage -eq 'REPAIR') {
    'RETRYABLE'
  } else {
    'BLOCKED'
  }
  $retryKey = if ($outcome -eq 'RETRYABLE') { "$IssueId|$Stage|$failureCategory|$stopReason" } else { '' }
  return New-AutopilotStageResult -IssueId $IssueId -Stage $Stage -Outcome $outcome -NextStage $nextStage -FailureCategory $failureCategory -StopReason $stopReason -Reason ([string]$ExecutorResult.summary) -SemanticProgress ([bool]($ExecutorResult.status -eq 'done' -or @($EvidencePaths).Count -gt 0)) -EvidencePaths $EvidencePaths -RetryKey $retryKey -TransitionIntent $(if ($Closed) { 'CLOSED' } elseif ($nextStage) { $nextStage } else { $Stage })
}
