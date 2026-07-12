$ErrorActionPreference = 'Stop'

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
  return [pscustomobject]@{
    runId = $RunId
    eventCount = $events.Count
    valid = $valid
    queueWaitSeconds = Get-Seconds $selecting $executing
    activeSeconds = Get-Seconds $executing $verifying
    verifySeconds = Get-Seconds $verifying $(if ($reviewing) { $reviewing } else { $closing })
    reviewSeconds = Get-Seconds $reviewing $closing
    closeoutSeconds = Get-Seconds $closing $committing
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
