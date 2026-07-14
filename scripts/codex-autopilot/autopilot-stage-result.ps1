$script:AutopilotStageOutcomes = @('SUCCEEDED','RETRYABLE','PAUSED','BLOCKED','TERMINAL')
$script:AutopilotFailureCategories = @('none','tool_config','environment','environment_prereq','quality_security','ready_issue_config','integrity_conflict','executor_stall_timeout')
if (!(Get-Variable -Name ControlPlanePolicyVersion -Scope Script -ErrorAction SilentlyContinue)) { $script:ControlPlanePolicyVersion = '' }
if (!(Get-Variable -Name ControlPlanePolicyHash -Scope Script -ErrorAction SilentlyContinue)) { $script:ControlPlanePolicyHash = '' }
if (!(Get-Variable -Name ControlPlanePolicyRefs -Scope Script -ErrorAction SilentlyContinue)) { $script:ControlPlanePolicyRefs = @() }
if (!(Get-Variable -Name CandidateEvidenceHead -Scope Script -ErrorAction SilentlyContinue)) { $script:CandidateEvidenceHead = '' }
if (!(Get-Variable -Name ExecutionBaseCommit -Scope Script -ErrorAction SilentlyContinue)) { $script:ExecutionBaseCommit = '' }

function New-AutopilotStageResult {
  [CmdletBinding()]
  param(
    [string]$IssueId = '',
    [ValidateSet('ISSUE','RUN')][string]$Scope = 'ISSUE',
    [string]$SubjectId = '',
    [Parameter(Mandatory)][string]$Stage,
    [Parameter(Mandatory)][ValidateSet('SUCCEEDED','RETRYABLE','PAUSED','BLOCKED','TERMINAL')][string]$Outcome,
    [string]$NextStage = '',
    [string]$FailureCategory = 'none',
    [string]$StopReason = '',
    [string]$Reason = '',
    [bool]$SemanticProgress = $false,
    [string[]]$EvidencePaths = @(),
    [string]$RetryKey = '',
    [string]$TransitionIntent = '',
    [string]$ControlPlanePolicyVersion = $(if ($script:ControlPlanePolicyVersion) { [string]$script:ControlPlanePolicyVersion } else { '' }),
    [string]$ControlPlanePolicyHash = $(if ($script:ControlPlanePolicyHash) { [string]$script:ControlPlanePolicyHash } else { '' }),
    [string[]]$ControlPlanePolicyRefs = $(if ($script:ControlPlanePolicyRefs) { @($script:ControlPlanePolicyRefs) } else { @() }),
    [string]$CandidateEvidenceHead = $(if ($script:CandidateEvidenceHead) { [string]$script:CandidateEvidenceHead } else { '' }),
    [string]$ExecutionBaseCommit = $(if ($script:ExecutionBaseCommit) { [string]$script:ExecutionBaseCommit } else { '' })
  )

  if ($script:AutopilotFailureCategories -notcontains $FailureCategory) {
    throw "Unsupported AutoPilot failure category: $FailureCategory"
  }
  if (!$SubjectId) { $SubjectId = $IssueId }
  if ($Scope -eq 'ISSUE' -and !$IssueId) { $IssueId = $SubjectId }
  if ([string]::IsNullOrWhiteSpace($SubjectId)) { throw 'StageResult subjectId is required' }
  if ($Scope -eq 'ISSUE' -and [string]::IsNullOrWhiteSpace($IssueId)) { throw 'ISSUE StageResult issueId is required' }
  $result = [pscustomobject][ordered]@{
    schemaVersion = 2
    scope = $Scope
    subjectId = $SubjectId
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
    controlPlanePolicyVersion = $ControlPlanePolicyVersion
    controlPlanePolicyHash = $ControlPlanePolicyHash
    controlPlanePolicyRefs = @($ControlPlanePolicyRefs)
    candidateEvidenceHead = $CandidateEvidenceHead
    executionBaseCommit = $ExecutionBaseCommit
  }
  Assert-AutopilotStageResult -Result $result | Out-Null
  return $result
}

function Assert-AutopilotStageResult {
  param([Parameter(Mandatory)][object]$Result)

  $version = [int]$Result.schemaVersion
  if ($version -eq 1) {
    $legacyRequired = @('schemaVersion','issueId','stage','outcome','nextStage','failureCategory','stopReason','reason','semanticProgress','evidencePaths','retryKey','transitionIntent')
    foreach ($name in $legacyRequired) { if ($Result.PSObject.Properties.Name -notcontains $name) { throw "StageResult v1 missing field: $name" } }
    if ([string]::IsNullOrWhiteSpace([string]$Result.issueId) -or [string]::IsNullOrWhiteSpace([string]$Result.stage)) { throw 'StageResult v1 issueId and stage are required' }
    if ($script:AutopilotStageOutcomes -notcontains [string]$Result.outcome) { throw "Unsupported StageResult outcome: $($Result.outcome)" }
    if ($script:AutopilotFailureCategories -notcontains [string]$Result.failureCategory) { throw "Unsupported StageResult failure category: $($Result.failureCategory)" }
    if ([string]$Result.outcome -in @('BLOCKED','PAUSED','RETRYABLE') -and [string]::IsNullOrWhiteSpace([string]$Result.stopReason)) { throw "StageResult $($Result.outcome) requires stopReason" }
    if ([string]$Result.outcome -eq 'RETRYABLE' -and [string]::IsNullOrWhiteSpace([string]$Result.retryKey)) { throw 'Retryable StageResult requires retryKey' }
    return $true
  }
  $required = @('schemaVersion','scope','subjectId','issueId','stage','outcome','nextStage','failureCategory','stopReason','reason','semanticProgress','evidencePaths','retryKey','transitionIntent','controlPlanePolicyVersion','controlPlanePolicyHash','controlPlanePolicyRefs','candidateEvidenceHead','executionBaseCommit')
  foreach ($name in $required) {
    if ($Result.PSObject.Properties.Name -notcontains $name) { throw "StageResult missing field: $name" }
  }
  if ($version -ne 2) { throw "Unsupported StageResult schemaVersion: $($Result.schemaVersion)" }
  if ([string]$Result.scope -notin @('ISSUE','RUN')) { throw "Unsupported StageResult scope: $($Result.scope)" }
  if ([string]::IsNullOrWhiteSpace([string]$Result.subjectId) -or [string]::IsNullOrWhiteSpace([string]$Result.stage)) { throw 'StageResult subjectId and stage are required' }
  if ([string]$Result.scope -eq 'ISSUE' -and [string]::IsNullOrWhiteSpace([string]$Result.issueId)) { throw 'ISSUE StageResult issueId is required' }
  if ([string]$Result.scope -eq 'RUN' -and ![string]::IsNullOrWhiteSpace([string]$Result.issueId)) { throw 'RUN StageResult must not contain issueId' }
  if ($script:AutopilotStageOutcomes -notcontains [string]$Result.outcome) { throw "Unsupported StageResult outcome: $($Result.outcome)" }
  if ($script:AutopilotFailureCategories -notcontains [string]$Result.failureCategory) { throw "Unsupported StageResult failure category: $($Result.failureCategory)" }
  if ([string]$Result.outcome -in @('BLOCKED','PAUSED','RETRYABLE') -and [string]::IsNullOrWhiteSpace([string]$Result.stopReason)) { throw "StageResult $($Result.outcome) requires stopReason" }
  if ([string]$Result.outcome -eq 'RETRYABLE' -and [string]::IsNullOrWhiteSpace([string]$Result.retryKey)) { throw 'Retryable StageResult requires retryKey' }
  if ($Result.controlPlanePolicyHash -and [string]$Result.controlPlanePolicyHash -notmatch '^[a-f0-9]{64}$') { throw 'StageResult control-plane policy hash is invalid' }
  foreach ($name in @('candidateEvidenceHead','executionBaseCommit')) { if ($Result.$name -and [string]$Result.$name -notmatch '^[a-f0-9]{40}$') { throw "StageResult $name is invalid" } }
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
