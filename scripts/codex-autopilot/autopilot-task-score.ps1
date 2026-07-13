$ErrorActionPreference = 'Stop'

$script:AutopilotTaskScoreVersion = 'autopilot-task-score/v1'
$script:AutopilotTaskScoreWeights = [ordered]@{
  deliveryCorrectness = 35
  zeroDanglingIssues = 25
  firstPassAcceptance = 20
  cycleEfficiency = 10
  stockIssueReduction = 10
}

function Get-AutopilotScoreProperty {
  param([object]$Object, [string]$Name, [object]$Default = $null)
  if ($null -eq $Object) { return $Default }
  if ($Object -is [System.Collections.IDictionary] -and $Object.Contains($Name)) { return $Object[$Name] }
  if ($Object.PSObject.Properties.Name -contains $Name) { return $Object.$Name }
  return $Default
}

function Set-AutopilotScoreProperty {
  param([object]$Object, [string]$Name, [object]$Value)
  if ($Object -is [System.Collections.IDictionary]) { $Object[$Name] = $Value } else { $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force }
}

function Get-AutopilotSha256 {
  param([Parameter(Mandatory)][string]$Text)
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}

function Get-AutopilotTaskScoreKey {
  param(
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$ImplementationCommit,
    [Parameter(Mandatory)][string]$ScoringVersion
  )
  return Get-AutopilotSha256 "$IssueId|$ImplementationCommit|$ScoringVersion"
}

function Test-AutopilotTaskScoringActive {
  param([object]$Config)
  if ($null -eq $Config -or (Get-AutopilotScoreProperty $Config 'enabled' $false) -ne $true) { return $false }
  $activeVersion = [string](Get-AutopilotScoreProperty $Config 'activeVersion' '')
  if (!$activeVersion) { throw 'task scoring cannot be enabled without an approved activeVersion' }
  if ($activeVersion -ne $script:AutopilotTaskScoreVersion) { throw "unsupported active task scoring version: $activeVersion" }
  if ([string](Get-AutopilotScoreProperty $Config 'approvalStatus' '') -ne 'APPROVED') { throw 'task scoring activeVersion requires approvalStatus=APPROVED' }
  return $true
}

function Test-AutopilotRetrospectiveActive {
  param([object]$TaskScoringConfig, [object]$RetrospectiveConfig)
  $scoringActive = Test-AutopilotTaskScoringActive $TaskScoringConfig
  $retrospectiveEnabled = $null -ne $RetrospectiveConfig -and (Get-AutopilotScoreProperty $RetrospectiveConfig 'enabled' $false) -eq $true
  if (!$scoringActive) {
    if ($retrospectiveEnabled) { throw 'retrospective cannot be enabled without approved active task scoring' }
    return $false
  }
  if (!$retrospectiveEnabled) { throw 'approved active task scoring requires retrospective.enabled=true' }
  if ([int](Get-AutopilotScoreProperty $RetrospectiveConfig 'threshold' 0) -lt 1) { throw 'retrospective threshold must be positive' }
  return $true
}

function Assert-AutopilotTaskScoreEvidence {
  param([Parameter(Mandatory)][object]$Evidence)
  $required = @(
    'issueId','implementationCommit','scoringVersion','hardGatesPassed','acceptanceCriteriaCovered',
    'targetValidationPassed','scopeConsistent','discoveriesDispositionComplete','followupGovernanceComplete',
    'attempt','cycleEvidenceComplete','avoidableReworkCount','stockIssueTarget','stockIssueClosed',
    'sameRootIssueAdded','followupNetChange','sourceRefs'
  )
  foreach ($name in $required) {
    $hasProperty = ($Evidence -is [System.Collections.IDictionary] -and $Evidence.Contains($name)) -or ($Evidence.PSObject.Properties.Name -contains $name)
    if (!$hasProperty) { throw "task score evidence missing required property: $name" }
  }
  if (![bool](Get-AutopilotScoreProperty $Evidence 'hardGatesPassed' $false)) { throw 'task score requires all hard gates to pass' }
  if ([string](Get-AutopilotScoreProperty $Evidence 'scoringVersion' '') -ne $script:AutopilotTaskScoreVersion) { throw 'task score evidence uses an unsupported scoringVersion' }
  if ([string](Get-AutopilotScoreProperty $Evidence 'implementationCommit' '') -notmatch '^[a-fA-F0-9]{40}$') { throw 'implementationCommit must be a full Git SHA-1' }
  foreach ($name in @('acceptanceCriteriaCovered','targetValidationPassed','scopeConsistent','discoveriesDispositionComplete','followupGovernanceComplete','cycleEvidenceComplete','stockIssueTarget','stockIssueClosed','sameRootIssueAdded')) {
    if ((Get-AutopilotScoreProperty $Evidence $name) -isnot [bool]) { throw "task score evidence property must be boolean: $name" }
  }
  if ([int](Get-AutopilotScoreProperty $Evidence 'attempt' -1) -lt 0) { throw 'attempt cannot be negative' }
  if ([int](Get-AutopilotScoreProperty $Evidence 'avoidableReworkCount' -1) -lt 0) { throw 'avoidableReworkCount cannot be negative' }
  if (@(Get-AutopilotScoreProperty $Evidence 'sourceRefs' @()).Count -eq 0) { throw 'task score requires at least one formal sourceRef' }
}

function New-AutopilotTaskScore {
  param([Parameter(Mandatory)][object]$Evidence)
  Assert-AutopilotTaskScoreEvidence $Evidence

  $delivery = 0
  if ([bool]$Evidence.acceptanceCriteriaCovered) { $delivery += 20 }
  if ([bool]$Evidence.targetValidationPassed) { $delivery += 10 }
  if ([bool]$Evidence.scopeConsistent) { $delivery += 5 }

  $dangling = 0
  if ([bool]$Evidence.discoveriesDispositionComplete) { $dangling += 15 }
  if ([bool]$Evidence.followupGovernanceComplete) { $dangling += 10 }

  $attempt = [int]$Evidence.attempt
  $firstPass = if ($attempt -eq 0) { 20 } elseif ($attempt -eq 1) { 10 } else { 0 }

  $reworkCount = [int]$Evidence.avoidableReworkCount
  $cycle = if (![bool]$Evidence.cycleEvidenceComplete) { 0 } elseif ($reworkCount -eq 0) { 10 } elseif ($reworkCount -eq 1) { 5 } else { 0 }

  $stock = if ([bool]$Evidence.stockIssueTarget) {
    if ([bool]$Evidence.stockIssueClosed -and ![bool]$Evidence.sameRootIssueAdded) { 10 } else { 0 }
  } else {
    if ([int]$Evidence.followupNetChange -le 0 -and ![bool]$Evidence.sameRootIssueAdded) { 5 } else { 0 }
  }

  $sourceRefs = @($Evidence.sourceRefs | ForEach-Object { [string]$_ } | Where-Object { $_ } | Sort-Object -Unique)
  $key = Get-AutopilotTaskScoreKey -IssueId $Evidence.issueId -ImplementationCommit $Evidence.implementationCommit -ScoringVersion $Evidence.scoringVersion
  $score = [ordered]@{
    schemaVersion = 1
    key = $key
    issueId = [string]$Evidence.issueId
    implementationCommit = ([string]$Evidence.implementationCommit).ToLowerInvariant()
    scoringVersion = [string]$Evidence.scoringVersion
    scoredAt = [datetimeoffset]::Now.ToString('o')
    total = $delivery + $dangling + $firstPass + $cycle + $stock
    dimensions = [ordered]@{
      deliveryCorrectness = [ordered]@{ score = $delivery; max = 35; evidence = @($sourceRefs) }
      zeroDanglingIssues = [ordered]@{ score = $dangling; max = 25; evidence = @($sourceRefs) }
      firstPassAcceptance = [ordered]@{ score = $firstPass; max = 20; evidence = @($sourceRefs) }
      cycleEfficiency = [ordered]@{ score = $cycle; max = 10; evidence = @($sourceRefs) }
      stockIssueReduction = [ordered]@{ score = $stock; max = 10; evidence = @($sourceRefs) }
    }
    hardGatesPassed = $true
    followupNetChange = [int]$Evidence.followupNetChange
    sourceRefs = @($sourceRefs)
  }
  return [pscustomobject]$score
}

function Add-AutopilotTaskScoreToReport {
  param(
    [Parameter(Mandatory)][string]$ReportPath,
    [Parameter(Mandatory)][object]$Score
  )
  if (!(Test-Path -LiteralPath $ReportPath -PathType Leaf)) { throw "formal iteration report is missing: $ReportPath" }
  $text = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8
  $begin = "<!-- AUTOPILOT-TASK-SCORE:BEGIN key=$($Score.key) -->"
  if ($text.Contains($begin)) { return $false }
  if ($text -match '<!-- AUTOPILOT-TASK-SCORE:BEGIN key=([^ ]+) -->') { throw 'iteration report already contains a different task score' }
  $json = $Score | ConvertTo-Json -Depth 12
  $fence = ([string][char]96) + ([string][char]96) + ([string][char]96)
  $block = @('', '', $begin, '## AutoPilot 任务评分', '', ($fence + 'json'), $json, $fence, '<!-- AUTOPILOT-TASK-SCORE:END -->', '') -join "`r`n"
  [IO.File]::WriteAllText($ReportPath, ($text.TrimEnd() + $block), [Text.UTF8Encoding]::new($false))
  return $true
}

function New-AutopilotTaskScoreEvidenceFromResult {
  param(
    [Parameter(Mandatory)][object]$Result,
    [Parameter(Mandatory)][string]$ReportPath,
    [Parameter(Mandatory)][string]$ImplementationCommit,
    [Parameter(Mandatory)][bool]$StockIssueTarget
  )
  if (!(Test-Path -LiteralPath $ReportPath -PathType Leaf)) { throw "formal score evidence report is missing: $ReportPath" }
  $report = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8
  $netMatch = [regex]::Match($report, '(?m)(?:本轮)?后续项净变化[：:]\s*([+-]?\d+)')
  if (!$netMatch.Success) { throw 'formal report is missing 后续项净变化' }
  $governanceComplete = $report -match '(?:本轮)?新增后续项[：:]' -and $report -match '(?:本轮)?关闭后续项[：:]'
  if (!$governanceComplete) { throw 'formal report is missing 新增后续项 or 关闭后续项' }
  $validation = @($Result.validation)
  $failedStatuses = @('fail','failed','quality_security')
  $validationPassed = $validation.Count -gt 0 -and @($validation | Where-Object { $failedStatuses -contains ([string]$_.status) }).Count -eq 0
  $sourceRefs = @([string]$ReportPath) + @($Result.evidencePaths)
  return [ordered]@{
    issueId = [string]$Result.issueId
    implementationCommit = $ImplementationCommit
    scoringVersion = $script:AutopilotTaskScoreVersion
    hardGatesPassed = ([string]$Result.status -eq 'done')
    acceptanceCriteriaCovered = ([string]$Result.status -eq 'done')
    targetValidationPassed = $validationPassed
    scopeConsistent = ([int](Get-AutopilotScoreProperty $Result 'scopeViolationCount' 0) -eq 0)
    discoveriesDispositionComplete = $governanceComplete
    followupGovernanceComplete = $governanceComplete
    attempt = [int](Get-AutopilotScoreProperty $Result 'attempt' 0)
    cycleEvidenceComplete = ($Result.PSObject.Properties.Name -contains 'firstPassSuccess')
    avoidableReworkCount = [int](Get-AutopilotScoreProperty $Result 'attempt' 0)
    stockIssueTarget = $StockIssueTarget
    stockIssueClosed = $StockIssueTarget
    sameRootIssueAdded = ([int]$netMatch.Groups[1].Value -gt 0)
    followupNetChange = [int]$netMatch.Groups[1].Value
    sourceRefs = @($sourceRefs | Where-Object { $_ } | Sort-Object -Unique)
  }
}
