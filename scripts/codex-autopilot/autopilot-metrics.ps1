$ErrorActionPreference = 'Stop'

function Get-AutopilotRunMetrics {
  param([Parameter(Mandatory)][string]$EventPath, [Parameter(Mandatory)][string]$RunId)
  if (!(Test-Path -LiteralPath $EventPath)) { throw "event file not found: $EventPath" }
  $seen = @{}
  $events = @()
  $valid = $true
  [datetimeoffset]$previous = [datetimeoffset]::MinValue
  foreach ($line in Get-Content -LiteralPath $EventPath) {
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
    $results = @(Get-ChildItem -LiteralPath $RunsDir -Filter result.json -Recurse | Sort-Object LastWriteTime -Descending | ForEach-Object {
      try { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 | ConvertFrom-Json } catch {}
    } | Where-Object { $_ -and $_.status -ne 'noop' } | Select-Object -First $WindowSize)
  }
  $reasons = @()
  if ($results.Count -lt $WindowSize) { $reasons += "sample size $($results.Count)/$WindowSize" }
  if (@($results | Where-Object status -ne 'done').Count -gt 0) { $reasons += 'window contains non-done results' }
  if (@($results | Where-Object { !$_.gitSummary -or !$_.gitSummary.commit }).Count -gt 0) { $reasons += 'commit binding is missing' }
  if (@($results | Where-Object { $_.PSObject.Properties.Name -notcontains 'evidencePaths' -or @($_.evidencePaths).Count -eq 0 }).Count -gt 0) { $reasons += 'bound verification evidence is missing' }
  if (@($results | Where-Object { $_.PSObject.Properties.Name -contains 'reviewRequired' -and $_.reviewRequired -and (!$_.review -or $_.review.decision -ne 'pass') }).Count -gt 0) { $reasons += 'required independent review is missing' }
  return [pscustomobject]@{ qualified = $reasons.Count -eq 0; sampleSize = $results.Count; requiredSampleSize = $WindowSize; reasons = @($reasons) }
}
