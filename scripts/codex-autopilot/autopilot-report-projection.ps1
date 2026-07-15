$ErrorActionPreference = 'Stop'
$contextLibrary = Join-Path $PSScriptRoot 'autopilot-context.ps1'
if (!(Get-Command Get-AutopilotCanonicalHash -ErrorAction SilentlyContinue)) { . $contextLibrary }

function Get-AutopilotReportFollowupSummary {
  param([Parameter(Mandatory)][string]$ReportPath)
  $text = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8
  function Read-Count([string]$Label) {
    $match = [regex]::Match($text, '(?m)^\s*(?:[-*]\s*)?(?:本轮)?' + [regex]::Escape($Label) + '\s*[：:=]\s*(-?\d+)\s*$')
    if (!$match.Success) { throw "formal report is missing numeric $Label" }
    return [int]$match.Groups[1].Value
  }
  $added = Read-Count '新增后续项'; $closed = Read-Count '关闭后续项'; $net = Read-Count '后续项净变化'
  if ($net -ne ($added - $closed)) { throw 'formal report follow-up net change is inconsistent' }
  return [pscustomobject]@{ added=$added; closed=$closed; netChange=$net }
}

function Get-AutopilotEvidenceManifestHash {
  param([string[]]$EvidencePaths)
  $manifest = foreach ($path in @($EvidencePaths | Where-Object { $_ } | Sort-Object -Unique)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "closeout evidence is missing: $path" }
    $evidence = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    [ordered]@{
      schemaVersion = [int]$evidence.schemaVersion
      evidenceId = if ($evidence.PSObject.Properties.Name -contains 'evidenceId') { [string]$evidence.evidenceId } else { '' }
      sourceEvidenceId = if ($evidence.PSObject.Properties.Name -contains 'sourceEvidenceId') { $evidence.sourceEvidenceId } else { $null }
      executionMode = if ($evidence.PSObject.Properties.Name -contains 'executionMode') { [string]$evidence.executionMode } else { 'LEGACY_EXECUTED' }
      category = if ($evidence.PSObject.Properties.Name -contains 'evidenceCategory') { [string]$evidence.evidenceCategory } else { 'LEGACY' }
      commandHash = if ($evidence.PSObject.Properties.Name -contains 'commandHash') { [string]$evidence.commandHash } else { Get-AutopilotTextHash ([string]$evidence.command) }
      diffHash = [string]$evidence.diffHash
      classification = [string]$evidence.classification
      exitCode = [int]$evidence.exitCode
    }
  }
  return Get-AutopilotCanonicalHash ([object[]]@($manifest))
}

function New-AutopilotMetricsSummary {
  param([Parameter(Mandatory)][object]$Metrics, [int]$PlannerInvocationCount = 0, [string[]]$PlannerCandidateRefs = @(), [switch]$IncludeProjection)
  return [pscustomobject][ordered]@{
    executorInvocationCount = [int]$Metrics.executorInvocationCount
    reviewerInvocationCount = [int]$Metrics.reviewerInvocationCount
    plannerInvocationCount = $PlannerInvocationCount
    plannerCandidateRefs = @($PlannerCandidateRefs | Where-Object { $_ } | Sort-Object -Unique)
    contextBaseBuildCount = [int]$Metrics.contextBaseBuildCount
    contextDeltaBuildCount = [int]$Metrics.contextDeltaBuildCount
    validationExecutedCount = [int]$Metrics.validationExecutedCount
    validationReusedCount = [int]$Metrics.validationReusedCount
    reportProjectionCount = [int]$Metrics.reportProjectionCount + $(if ($IncludeProjection) { 1 } else { 0 })
    implementationDispatchCount = [int]$Metrics.implementationDispatchCount
    validationDispatchCount = [int]$Metrics.validationDispatchCount
    reviewDispatchCount = [int]$Metrics.reviewDispatchCount
    repairDispatchCount = [int]$Metrics.repairDispatchCount
    closeoutDispatchCount = [int]$Metrics.closeoutDispatchCount
    runResumeCount = [int]$Metrics.runResumeCount
    phaseRestartCount = [int]$Metrics.phaseRestartCount
    wallClockSeconds = [int]$Metrics.wallClockSeconds
    phaseDurationsSeconds = $Metrics.phaseDurationsSeconds
    tokenUsageStatus = [string]$Metrics.tokenUsageStatus
    inputTokens = $Metrics.inputTokens
    outputTokens = $Metrics.outputTokens
    totalTokens = $Metrics.totalTokens
  }
}

function New-AutopilotPreCloseoutFacts {
  param(
    [Parameter(Mandatory)][object]$Issue,
    [Parameter(Mandatory)][string]$ImplementationCommit,
    [Parameter(Mandatory)][string[]]$EvidencePaths,
    [Parameter(Mandatory)][bool]$ReviewRequired,
    [string]$ReviewDecision = '',
    [Parameter(Mandatory)][string]$VerifiedDiffHash,
    [Parameter(Mandatory)][object]$Followups,
    [Parameter(Mandatory)][object]$MetricsSummary,
    [Parameter(Mandatory)][string]$ControlPlaneFingerprint
  )
  $payload = [ordered]@{
    schemaVersion = 1
    issueId = [string]$Issue.issueId
    readyContentHash = [string]$Issue.readyContentHash
    outcome = 'PASSED'
    implementationCommit = $ImplementationCommit
    reportPath = ([string]$Issue.archiveReport).Replace('\','/')
    evidenceManifestHash = Get-AutopilotEvidenceManifestHash -EvidencePaths $EvidencePaths
    reviewRequired = $ReviewRequired
    reviewDecision = $ReviewDecision
    verifiedDiffHash = $VerifiedDiffHash
    followupsAdded = [int]$Followups.added
    followupsClosed = [int]$Followups.closed
    followupsNetChange = [int]$Followups.netChange
    metricsSummary = $MetricsSummary
    metricsHash = Get-AutopilotCanonicalHash $MetricsSummary
    controlPlaneFingerprint = $ControlPlaneFingerprint
  }
  $hash = Get-AutopilotCanonicalHash ([pscustomobject]$payload)
  $facts = [ordered]@{ preCloseoutFactsHash=$hash }
  foreach ($name in $payload.Keys) { $facts[$name] = $payload[$name] }
  return [pscustomobject]$facts
}

function Set-AutopilotReportProjection {
  param([Parameter(Mandatory)][string]$ReportPath, [Parameter(Mandatory)][object]$Facts)
  $start = '<!-- AUTOPILOT-FACTS:START -->'
  $end = '<!-- AUTOPILOT-FACTS:END -->'
  $metricsJson = Get-AutopilotCanonicalJson $Facts.metricsSummary
  $block = @(
    $start,
    '## AutoPilot 自动事实',
    '',
    "- Issue：$($Facts.issueId)",
    "- Ready 哈希：$($Facts.readyContentHash)",
    "- 实施提交：$($Facts.implementationCommit)",
    "- 验证差异哈希：$($Facts.verifiedDiffHash)",
    "- Evidence manifest：$($Facts.evidenceManifestHash)",
    "- Reviewer：required=$([bool]$Facts.reviewRequired); decision=$($Facts.reviewDecision)",
    "- 后续项：added=$($Facts.followupsAdded); closed=$($Facts.followupsClosed); net=$($Facts.followupsNetChange)",
    "- 指标：$metricsJson",
    "- 控制面指纹：$($Facts.controlPlaneFingerprint)",
    "- PreCloseout Facts：$($Facts.preCloseoutFactsHash)",
    $end
  ) -join "`n"
  $text = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8
  $pattern = '(?s)' + [regex]::Escape($start) + '.*?' + [regex]::Escape($end)
  $updated = if ([regex]::IsMatch($text, $pattern)) { [regex]::Replace($text, $pattern, [Text.RegularExpressions.MatchEvaluator]{ param($match) $block }, 1) } else { $text.TrimEnd("`r","`n") + "`n`n" + $block + "`n" }
  [IO.File]::WriteAllText($ReportPath, $updated, [Text.UTF8Encoding]::new($false))
  return [pscustomobject]@{ reportPath=$ReportPath; projectionHash=(Get-AutopilotTextHash $block); reportHash=(Get-FileHash -LiteralPath $ReportPath -Algorithm SHA256).Hash.ToLowerInvariant() }
}

function Write-AutopilotFrozenResultSnapshot {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][object]$Result)
  $payload = [ordered]@{}
  foreach ($property in @($Result.PSObject.Properties | Sort-Object Name)) {
    if ($property.Name -notin @('resultHash','registeredAt','ledgerStatus')) { $payload[$property.Name] = $property.Value }
  }
  $json = Get-AutopilotCanonicalJson ([pscustomobject]$payload)
  $hash = Get-AutopilotTextHash $json
  if (Test-Path -LiteralPath $Path -PathType Leaf) {
    $existing = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ((Get-AutopilotTextHash $existing) -ne $hash) { throw 'frozen final result integrity conflict' }
    return [pscustomobject]@{ path=$Path; resultHash=$hash; idempotent=$true }
  }
  $parent = Split-Path -Parent $Path
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
  return [pscustomobject]@{ path=$Path; resultHash=$hash; idempotent=$false }
}
