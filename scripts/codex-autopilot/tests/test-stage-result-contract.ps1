param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path (Split-Path -Parent $scriptDir) 'autopilot-stage-result.ps1')
$script:ControlPlanePolicyVersion = '1'
$script:ControlPlanePolicyHash = ('a' * 64)
$script:ControlPlanePolicyRefs = @('plugins/cgc-pms-autopilot/references/control-plane-policy.md')
$script:CandidateEvidenceHead = ('b' * 40)
$script:ExecutionBaseCommit = ('c' * 40)

$passed = New-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage validate -Outcome SUCCEEDED -NextStage review -SemanticProgress $true -EvidencePaths @('evidence.json','evidence.json') -TransitionIntent REVIEWING
if ($passed.schemaVersion -ne 2 -or $passed.scope -ne 'ISSUE' -or $passed.subjectId -ne 'ISSUE-TEST-001' -or $passed.outcome -ne 'SUCCEEDED' -or $passed.nextStage -ne 'REVIEW' -or @($passed.evidencePaths).Count -ne 1 -or $passed.controlPlanePolicyHash -ne ('a' * 64) -or $passed.candidateEvidenceHead -ne ('b' * 40) -or $passed.executionBaseCommit -ne ('c' * 40)) { throw 'StageResult success contract is unstable' }

$runResult = New-AutopilotStageResult -Scope RUN -SubjectId 'run-test-001' -Stage refill -Outcome SUCCEEDED -NextStage select -SemanticProgress $true -TransitionIntent SELECT
if ($runResult.issueId -or $runResult.scope -ne 'RUN' -or $runResult.nextStage -ne 'SELECT') { throw 'RUN StageResult contract is unstable' }

$retryable = ConvertTo-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage review -ExecutorResult ([pscustomobject]@{status='blocked';failureCategory='quality_security';stopReason='STOP_REVIEW_NEEDS_REPAIR';nextAction='REPAIR';summary='review finding'})
if ($retryable.outcome -ne 'RETRYABLE' -or !$retryable.retryKey -or $retryable.transitionIntent -ne 'REPAIR') { throw 'StageResult repair routing is unstable' }

$toolBlocked = ConvertTo-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage review -ExecutorResult ([pscustomobject]@{status='blocked';failureCategory='tool_config';stopReason='STOP_REVIEWER_TOOL_RETRY_EXHAUSTED';nextAction='STOP';summary='reviewer unavailable'})
if ($toolBlocked.outcome -ne 'PAUSED' -or $toolBlocked.failureCategory -ne 'tool_config') { throw 'StageResult tool_config routing is unstable' }

$invalidRejected = $false
try { New-AutopilotStageResult -IssueId 'ISSUE-TEST-001' -Stage review -Outcome BLOCKED -FailureCategory quality_security | Out-Null } catch { $invalidRejected = $true }
if (!$invalidRejected) { throw 'StageResult accepted a blocked outcome without stopReason' }

$runWithIssueRejected = $false
try { New-AutopilotStageResult -Scope RUN -SubjectId 'run-test-001' -IssueId 'ISSUE-TEST-001' -Stage refill -Outcome SUCCEEDED | Out-Null } catch { $runWithIssueRejected = $true }
if (!$runWithIssueRejected) { throw 'RUN StageResult accepted an Issue identity' }

$legacy = [pscustomobject]@{schemaVersion=1;issueId='ISSUE-TEST-LEGACY';stage='VALIDATE';outcome='SUCCEEDED';nextStage='REVIEW';failureCategory='none';stopReason='';reason='legacy';semanticProgress=$true;evidencePaths=@();retryKey='';transitionIntent='REVIEWING'}
if (!(Assert-AutopilotStageResult -Result $legacy)) { throw 'StageResult v1 compatibility was lost' }

Write-Host 'stage result contract self-test passed'
