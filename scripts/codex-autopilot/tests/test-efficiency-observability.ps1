param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $scriptDir 'autopilot-metrics.ps1')

function New-TestMetricEvent {
  param([string]$Event, [string]$Timestamp, [object]$Data)
  $payload = [ordered]@{ event=$Event; timestamp=$Timestamp }
  foreach ($property in @($Data.PSObject.Properties)) { $payload[$property.Name] = $property.Value }
  return [pscustomobject]$payload
}

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-efficiency-observability-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $eventsA = Join-Path $root 'run-a.ndjson'
  $eventsB = Join-Path $root 'run-b.ndjson'
  $startedAt = '2026-07-14T10:00:00+08:00'
  $executorId = New-AutopilotInvocationId -Role EXECUTOR -Scope ISSUE -ScopeId 'ISSUE-900-045' -ProcessId 1001 -StartedAt $startedAt
  $reviewerId = New-AutopilotInvocationId -Role REVIEWER -Scope ISSUE -ScopeId 'ISSUE-900-045' -ProcessId 1002 -StartedAt $startedAt
  $plannerId = New-AutopilotInvocationId -Role PLANNER -Scope RUN -ScopeId 'run-a' -ProcessId 1003 -StartedAt $startedAt
  $executor = New-AutopilotModelInvocationEvent -Role EXECUTOR -Scope ISSUE -ScopeId 'ISSUE-900-045' -InvocationId $executorId -IssueId 'ISSUE-900-045' -RunId 'run-a' -ProcessId 1001 -StartedAt $startedAt
  $reviewer = New-AutopilotModelInvocationEvent -Role REVIEWER -Scope ISSUE -ScopeId 'ISSUE-900-045' -InvocationId $reviewerId -IssueId 'ISSUE-900-045' -RunId 'run-b' -ProcessId 1002 -StartedAt $startedAt
  $planner = New-AutopilotModelInvocationEvent -Role PLANNER -Scope RUN -ScopeId 'run-a' -InvocationId $plannerId -RunId 'run-a' -CandidateRefs @('stock:ONE','stock:TWO') -ProcessId 1003 -StartedAt $startedAt
  @(
    (New-TestMetricEvent -Event 'model.invocation' -Timestamp $startedAt -Data $executor),
    (New-TestMetricEvent -Event 'model.invocation' -Timestamp $startedAt -Data $executor),
    (New-TestMetricEvent -Event 'model.invocation' -Timestamp $startedAt -Data $planner),
    [ordered]@{event='context.base-built';timestamp=$startedAt;runId='run-a';issueId='ISSUE-900-045';operationId='base-1'},
    [ordered]@{event='context.base-built';timestamp=$startedAt;runId='run-a';issueId='ISSUE-900-045';operationId='base-1'},
    [ordered]@{event='validation.executed';timestamp=$startedAt;runId='run-a';issueId='ISSUE-900-045';operationId='validation-1'}
  ) | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 8 } | Set-Content -LiteralPath $eventsA -Encoding UTF8
  @(
    (New-TestMetricEvent -Event 'model.invocation' -Timestamp $startedAt -Data $reviewer),
    [ordered]@{event='validation.reused';timestamp=$startedAt;runId='run-b';issueId='ISSUE-900-045';operationId='validation-1-reuse'}
  ) | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 8 } | Set-Content -LiteralPath $eventsB -Encoding UTF8

  $issue = Get-AutopilotIssueEfficiencyMetrics -EventPaths @($eventsA,$eventsB) -IssueId 'ISSUE-900-045'
  if ($issue.executorInvocationCount -ne 1 -or $issue.reviewerInvocationCount -ne 1 -or $issue.plannerInvocationCount -ne 0) { throw 'Issue-scoped model calls are not exact' }
  if ($issue.contextBaseBuildCount -ne 1 -or $issue.validationExecutedCount -ne 1 -or $issue.validationReusedCount -ne 1) { throw 'Issue-scoped operation counts are not exact' }
  $run = Get-AutopilotRunEfficiencyMetrics -EventPaths @($eventsA,$eventsB) -RunId 'run-a'
  if ($run.plannerInvocationCount -ne 1 -or $run.executorInvocationCount -ne 1 -or $run.reviewerInvocationCount -ne 0) { throw 'Run-scoped model calls are not exact' }
  if ($planner.candidateRefs.Count -ne 2 -or $planner.scope -ne 'RUN') { throw 'Planner invocation was copied to candidate Issue scope' }
  if ($issue.tokenUsageStatus -ne 'not_available' -or $null -ne $issue.inputTokens) { throw 'missing token usage was fabricated' }

  Write-Host 'efficiency observability self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
