param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path (Split-Path -Parent $scriptDir) 'autopilot-stage-result.ps1')

$passed = New-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage validate -Outcome SUCCEEDED -NextStage review -SemanticProgress $true -EvidencePaths @('evidence.json','evidence.json') -TransitionIntent REVIEWING
if ($passed.schemaVersion -ne 1 -or $passed.outcome -ne 'SUCCEEDED' -or $passed.nextStage -ne 'REVIEW' -or @($passed.evidencePaths).Count -ne 1) { throw 'StageResult success contract is unstable' }

$retryable = ConvertTo-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage review -ExecutorResult ([pscustomobject]@{status='blocked';failureCategory='quality_security';stopReason='STOP_REVIEW_NEEDS_REPAIR';nextAction='REPAIR';summary='review finding'})
if ($retryable.outcome -ne 'RETRYABLE' -or !$retryable.retryKey -or $retryable.transitionIntent -ne 'REPAIR') { throw 'StageResult repair routing is unstable' }

$toolBlocked = ConvertTo-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage review -ExecutorResult ([pscustomobject]@{status='blocked';failureCategory='tool_config';stopReason='STOP_REVIEWER_TOOL_RETRY_EXHAUSTED';nextAction='STOP';summary='reviewer unavailable'})
if ($toolBlocked.outcome -ne 'PAUSED' -or $toolBlocked.failureCategory -ne 'tool_config') { throw 'StageResult tool_config routing is unstable' }

$invalidRejected = $false
try { New-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage review -Outcome BLOCKED -FailureCategory quality_security | Out-Null } catch { $invalidRejected = $true }
if (!$invalidRejected) { throw 'StageResult accepted a blocked outcome without stopReason' }

Write-Host 'stage result contract self-test passed'
