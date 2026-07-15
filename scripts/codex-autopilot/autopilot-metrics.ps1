$ErrorActionPreference = 'Stop'

function Get-AutopilotMetricsSha256 {
  param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '').ToLowerInvariant() }
  finally { $sha.Dispose() }
}

function New-AutopilotInvocationId {
  param(
    [Parameter(Mandatory)][ValidateSet('EXECUTOR','REVIEWER','PLANNER')][string]$Role,
    [Parameter(Mandatory)][ValidateSet('ISSUE','RUN')][string]$Scope,
    [Parameter(Mandatory)][string]$ScopeId,
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][string]$StartedAt
  )
  return Get-AutopilotMetricsSha256 ((@($Role, $Scope, $ScopeId, [string]$ProcessId, $StartedAt)) -join '|')
}

function New-AutopilotModelInvocationEvent {
  param(
    [Parameter(Mandatory)][ValidateSet('EXECUTOR','REVIEWER','PLANNER')][string]$Role,
    [Parameter(Mandatory)][ValidateSet('ISSUE','RUN')][string]$Scope,
    [Parameter(Mandatory)][string]$ScopeId,
    [Parameter(Mandatory)][string]$InvocationId,
    [string]$IssueId = '',
    [string]$RunId = '',
    [string[]]$CandidateRefs = @(),
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][string]$StartedAt
  )
  if ($InvocationId -notmatch '^[a-f0-9]{64}$') { throw 'model invocationId must be a SHA-256 value' }
  if ($Scope -eq 'ISSUE' -and !$IssueId) { throw 'ISSUE model invocation requires issueId' }
  if ($Scope -eq 'RUN' -and !$RunId) { throw 'RUN model invocation requires runId' }
  return [pscustomobject]@{
    invocationId = $InvocationId
    role = $Role
    scope = $Scope
    scopeId = $ScopeId
    issueId = $IssueId
    runId = $RunId
    candidateRefs = @($CandidateRefs | Where-Object { $_ } | Sort-Object -Unique)
    processId = $ProcessId
    startedAt = $StartedAt
    tokenUsageStatus = 'not_available'
    inputTokens = $null
    outputTokens = $null
    totalTokens = $null
  }
}

function Read-AutopilotMetricEvents {
  param([Parameter(Mandatory)][string[]]$EventPaths)
  foreach ($path in @($EventPaths | Where-Object { $_ } | Sort-Object -Unique)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { continue }
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
      try { $line | ConvertFrom-Json } catch { continue }
    }
  }
}

function Get-AutopilotEfficiencyMetrics {
  param(
    [Parameter(Mandatory)][object[]]$Events,
    [Parameter(Mandatory)][ValidateSet('ISSUE','RUN')][string]$Scope,
    [Parameter(Mandatory)][string]$ScopeId
  )
  $selected = @($Events | Where-Object {
    if ($Scope -eq 'ISSUE') { return [string]$_.issueId -eq $ScopeId }
    return [string]$_.runId -eq $ScopeId
  })
  $invocations = @($selected | Where-Object { $_.event -eq 'model.invocation' -and $_.invocationId } | Group-Object invocationId | ForEach-Object { $_.Group | Select-Object -First 1 })
  function Get-OperationCount([string]$EventName) {
    $matches = @($selected | Where-Object event -eq $EventName)
    $keys = @($matches | ForEach-Object {
      if ($_.operationId) { [string]$_.operationId }
      elseif ($_.eventId) { [string]$_.eventId }
      else { "$([string]$_.event)|$([string]$_.issueId)|$([string]$_.runId)|$([string]$_.timestamp)|$([string]$_.reason)" }
    } | Sort-Object -Unique)
    return $keys.Count
  }
  return [pscustomobject]@{
    scope = $Scope
    scopeId = $ScopeId
    executorInvocationCount = @($invocations | Where-Object role -eq 'EXECUTOR').Count
    reviewerInvocationCount = @($invocations | Where-Object role -eq 'REVIEWER').Count
    plannerInvocationCount = @($invocations | Where-Object role -eq 'PLANNER').Count
    contextBaseBuildCount = Get-OperationCount 'context.base-built'
    contextDeltaBuildCount = Get-OperationCount 'context.delta-built'
    validationExecutedCount = Get-OperationCount 'validation.executed'
    validationReusedCount = Get-OperationCount 'validation.reused'
    reportProjectionCount = Get-OperationCount 'report.projected'
    tokenUsageStatus = 'not_available'
    inputTokens = $null
    outputTokens = $null
    totalTokens = $null
  }
}

function Get-AutopilotIssueEfficiencyMetrics {
  param([Parameter(Mandatory)][string[]]$EventPaths, [Parameter(Mandatory)][string]$IssueId)
  return Get-AutopilotEfficiencyMetrics -Events @(Read-AutopilotMetricEvents -EventPaths $EventPaths) -Scope ISSUE -ScopeId $IssueId
}

function Get-AutopilotRunEfficiencyMetrics {
  param([Parameter(Mandatory)][string[]]$EventPaths, [Parameter(Mandatory)][string]$RunId)
  return Get-AutopilotEfficiencyMetrics -Events @(Read-AutopilotMetricEvents -EventPaths $EventPaths) -Scope RUN -ScopeId $RunId
}

function Get-AutopilotPercentile {
  param([object[]]$Values, [Parameter(Mandatory)][ValidateRange(0.0,1.0)][double]$Percentile)
  $numbers = @($Values | Where-Object { $null -ne $_ -and $_ -ne 'not_available' } | ForEach-Object { [double]$_ } | Sort-Object)
  if ($numbers.Count -eq 0) { return $null }
  $index = [Math]::Max(0, [Math]::Ceiling($Percentile * $numbers.Count) - 1)
  return $numbers[[int]$index]
}

function Get-AutopilotMetricPercentiles {
  param([Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Samples, [Parameter(Mandatory)][string[]]$MetricNames)
  $result = [ordered]@{ sampleCount = $Samples.Count; metrics = [ordered]@{} }
  foreach ($name in $MetricNames) {
    $values = @($Samples | ForEach-Object {
      if ($_.PSObject.Properties.Name -contains $name) { $_.$name }
    } | Where-Object { $null -ne $_ -and $_ -ne 'not_available' })
    $result.metrics[$name] = [ordered]@{
      sampleCount = $values.Count
      p50 = Get-AutopilotPercentile -Values $values -Percentile 0.50
      p95 = Get-AutopilotPercentile -Values $values -Percentile 0.95
    }
  }
  return [pscustomobject]$result
}

function Get-AutopilotRunMetrics {
  param([Parameter(Mandatory)][string]$EventPath, [Parameter(Mandatory)][string]$RunId)
  if (!(Test-Path -LiteralPath $EventPath)) { throw "event file not found: $EventPath" }
  $seen = @{}
  $events = @()
  $valid = $true
  [datetimeoffset]$previous = [datetimeoffset]::MinValue
  foreach ($line in Get-Content -Encoding UTF8 -LiteralPath $EventPath) {
    try { $event = $line | ConvertFrom-Json } catch { $valid = $false; continue }
    if ($event.runId -ne $RunId) { continue }
    $key = "$($event.runId)|$($event.issueId)|$($event.to)|$($event.timestamp)|$($event.reason)"
    if ($seen.ContainsKey($key)) { continue }
    $seen[$key] = $true
    [datetimeoffset]$time = [datetimeoffset]::MinValue
    if (![datetimeoffset]::TryParse([string]$event.timestamp, [ref]$time)) { $valid = $false; continue }
    if ($previous -ne [datetimeoffset]::MinValue -and $time -lt $previous) { $valid = $false }
    $previous = $time
    $events += [pscustomobject]@{ event = $event; time = $time }
  }
  function Get-FirstTime([string]$Status) { return @($events | Where-Object { $_.event.to -eq $Status } | Select-Object -First 1).time }
  function Get-Seconds([object]$Start, [object]$End) { if ($null -eq $Start -or $null -eq $End -or $End -lt $Start) { return $null }; return [Math]::Round(($End - $Start).TotalSeconds, 3) }
  $selecting = Get-FirstTime 'SELECTING'; $executing = Get-FirstTime 'EXECUTING'; $verifying = Get-FirstTime 'VERIFYING'; $reviewing = Get-FirstTime 'REVIEWING'; $closing = Get-FirstTime 'CLOSING'; $committing = Get-FirstTime 'COMMITTING'
  $efficiency = Get-AutopilotRunEfficiencyMetrics -EventPaths @($EventPath) -RunId $RunId
  return [pscustomobject]@{
    runId = $RunId
    eventCount = $events.Count
    valid = $valid
    queueWaitSeconds = Get-Seconds $selecting $executing
    activeSeconds = Get-Seconds $executing $verifying
    verifySeconds = Get-Seconds $verifying $(if ($reviewing) { $reviewing } else { $closing })
    reviewSeconds = Get-Seconds $reviewing $closing
    closeoutSeconds = Get-Seconds $closing $committing
    executorInvocationCount = $efficiency.executorInvocationCount
    reviewerInvocationCount = $efficiency.reviewerInvocationCount
    plannerInvocationCount = $efficiency.plannerInvocationCount
    contextBaseBuildCount = $efficiency.contextBaseBuildCount
    contextDeltaBuildCount = $efficiency.contextDeltaBuildCount
    validationExecutedCount = $efficiency.validationExecutedCount
    validationReusedCount = $efficiency.validationReusedCount
    reportProjectionCount = $efficiency.reportProjectionCount
    tokenUsageStatus = $efficiency.tokenUsageStatus
    inputTokens = $null
    outputTokens = $null
    totalTokens = $null
  }
}

function Get-AutopilotQualification {
  param([Parameter(Mandatory)][string]$RunsDir, [int]$WindowSize = 20)
  $results = @()
  if (Test-Path -LiteralPath $RunsDir) {
    $results = @(Get-ChildItem -LiteralPath $RunsDir -Filter result.json -Recurse | ForEach-Object {
      try {
        $result = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        [datetimeoffset]$created = [datetimeoffset]::MinValue
        $validCreatedAt = $result.createdAt -and [datetimeoffset]::TryParse([string]$result.createdAt, [ref]$created)
        $result | Add-Member -NotePropertyName qualificationCreatedAtValid -NotePropertyValue ([bool]$validCreatedAt) -Force
        [pscustomobject]@{ result=$result; createdAt=$(if($validCreatedAt){$created}else{[datetimeoffset]::MaxValue}); path=$_.FullName }
      } catch {}
    } | Where-Object { $_ -and $_.result.status -ne 'noop' } | Sort-Object @{Expression='createdAt';Descending=$true},@{Expression='path';Descending=$true} | Select-Object -First $WindowSize | ForEach-Object result)
  }
  $reasons = @()
  if (@($results | Where-Object { !$_.qualificationCreatedAtValid }).Count -gt 0) { $reasons += 'qualification window contains invalid result timestamp' }
  if ($results.Count -lt $WindowSize) { $reasons += "sample size $($results.Count)/$WindowSize" }
  if (@($results | Where-Object status -ne 'done').Count -gt 0) { $reasons += 'window contains non-done results' }
  if (@($results | Where-Object { !$_.gitSummary -or !$_.gitSummary.commit }).Count -gt 0) { $reasons += 'commit binding is missing' }
  $invalidEvidence = @($results | Where-Object {
    if ($_.PSObject.Properties.Name -notcontains 'evidencePaths' -or @($_.evidencePaths).Count -eq 0) { return $true }
    foreach ($path in @($_.evidencePaths)) {
      if (!(Test-Path -LiteralPath $path -PathType Leaf)) { return $true }
      try { $evidence = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json } catch { return $true }
      if ($evidence.issueId -ne $_.issueId -or $evidence.exitCode -ne 0 -or $evidence.classification -ne 'pass' -or !$_.verificationBaseCommit -or !$_.verifiedDiffHash -or $evidence.baseCommit -ne $_.verificationBaseCommit -or $evidence.diffHash -ne $_.verifiedDiffHash) { return $true }
    }
    return $false
  }).Count
  if ($invalidEvidence -gt 0) { $reasons += 'bound verification evidence is missing or invalid' }
  if (@($results | Where-Object { $_.PSObject.Properties.Name -contains 'reviewRequired' -and $_.reviewRequired -and (!$_.review -or $_.review.decision -ne 'pass' -or $_.review.issueId -ne $_.issueId -or !$_.reviewedDiffHashExpected -or ([string]$_.review.reviewedDiffHash).ToLowerInvariant() -ne ([string]$_.reviewedDiffHashExpected).ToLowerInvariant()) }).Count -gt 0) { $reasons += 'required independent review is missing or mismatched' }
  $missingQualificationFields = @($results | Where-Object {
    $_.PSObject.Properties.Name -notcontains 'firstPassSuccess' -or
    $_.PSObject.Properties.Name -notcontains 'manualInterventionCount' -or
    $_.PSObject.Properties.Name -notcontains 'scopeViolationCount'
  }).Count
  if ($missingQualificationFields -gt 0) { $reasons += 'unattended qualification fields are missing' }
  $firstPassCount = @($results | Where-Object { $_.firstPassSuccess -eq $true }).Count
  $firstPassRate = if ($results.Count -gt 0) { [Math]::Round($firstPassCount / $results.Count, 4) } else { 0 }
  if ($results.Count -gt 0 -and $firstPassRate -lt 0.8) { $reasons += 'first pass success rate is below 80 percent' }
  $manualInterventionCount = [int](($results | Measure-Object -Property manualInterventionCount -Sum).Sum)
  $scopeViolationCount = [int](($results | Measure-Object -Property scopeViolationCount -Sum).Sum)
  if ($manualInterventionCount -ne 0) { $reasons += 'manual intervention count is not zero' }
  if ($scopeViolationCount -ne 0) { $reasons += 'scope violation count is not zero' }
  return [pscustomobject]@{
    qualified = $reasons.Count -eq 0
    sampleSize = $results.Count
    requiredSampleSize = $WindowSize
    firstPassCount = $firstPassCount
    firstPassRate = $firstPassRate
    manualInterventionCount = $manualInterventionCount
    scopeViolationCount = $scopeViolationCount
    reasons = @($reasons)
  }
}
