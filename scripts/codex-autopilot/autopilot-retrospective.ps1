$ErrorActionPreference = 'Stop'

function Get-AutopilotRetrospectiveSha256 {
  param([Parameter(Mandatory)][string]$Text)
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}

function Get-AutopilotMedian {
  param([double[]]$Values)
  $sorted = @($Values | Sort-Object)
  if ($sorted.Count -eq 0) { return $null }
  $middle = [Math]::Floor($sorted.Count / 2)
  if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
  return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2
}

function New-AutopilotImprovementProposal {
  param(
    [Parameter(Mandatory)][string]$Rule,
    [Parameter(Mandatory)][string]$Subject,
    [Parameter(Mandatory)][string]$Summary,
    [Parameter(Mandatory)][string]$AcceptanceCriteria,
    [Parameter(Mandatory)][string]$ReviewCycleId,
    [string[]]$SourceIssueIds = @(),
    [string]$Priority = 'P1'
  )
  $hash = Get-AutopilotRetrospectiveSha256 "autopilot-improvement|$Rule|$Subject"
  return [pscustomobject][ordered]@{
    proposalKey = 'AUTO-IMPROVEMENT-' + $hash.Substring(0, 16).ToUpperInvariant()
    reviewCycleId = $ReviewCycleId
    rule = $Rule
    subject = $Subject
    title = "AutoPilot 改进：$Subject"
    summary = $Summary
    priority = $Priority
    approvalStatus = 'NEEDS_CONFIRMATION'
    blocking = $false
    acceptanceCriteria = $AcceptanceCriteria
    sourceIssueIds = @($SourceIssueIds | Sort-Object -Unique)
  }
}

function New-AutopilotRetrospective {
  param(
    [Parameter(Mandatory)][string]$ReviewCycleId,
    [Parameter(Mandatory)][object[]]$TaskRecords,
    [Parameter(Mandatory)][string]$ScoringVersion,
    [Nullable[int]]$OpeningIssueCount = $null,
    [Nullable[int]]$ClosingIssueCount = $null,
    [Nullable[double]]$PreviousMedianCycleSeconds = $null
  )
  if ($TaskRecords.Count -lt 1) { throw 'retrospective requires at least one task record' }
  $dimensionNames = @('deliveryCorrectness','zeroDanglingIssues','firstPassAcceptance','cycleEfficiency','taskExecutionEfficiency','stockIssueReduction')
  $dimensionStats = [ordered]@{}
  foreach ($name in $dimensionNames) {
    $dimensionRecords = @($TaskRecords | Where-Object { $null -ne $_.score.dimensions -and $_.score.dimensions.PSObject.Properties.Name -contains $name })
    if ($dimensionRecords.Count -eq 0) { continue }
    $values = @($dimensionRecords | ForEach-Object { [double]$_.score.dimensions.$name.score })
    $max = [int]$dimensionRecords[0].score.dimensions.$name.max
    $dimensionStats[$name] = [ordered]@{
      average = [Math]::Round((($values | Measure-Object -Average).Average), 2)
      median = [Math]::Round((Get-AutopilotMedian $values), 2)
      minimum = [Math]::Round((($values | Measure-Object -Minimum).Minimum), 2)
      max = $max
      taskCount = $dimensionRecords.Count
    }
  }
  $totals = @($TaskRecords | ForEach-Object { [double]$_.score.total })
  $firstPassCount = @($TaskRecords | Where-Object { [int]$_.attempt -eq 0 }).Count
  $firstPassRate = [Math]::Round($firstPassCount / $TaskRecords.Count, 4)
  $followupNetChange = [int](($TaskRecords | Measure-Object -Property followupNetChange -Sum).Sum)
  $cycleValues = @($TaskRecords | Where-Object { $null -ne $_.cycleSeconds } | ForEach-Object { [double]$_.cycleSeconds })
  $medianCycle = Get-AutopilotMedian $cycleValues
  $proposals = New-Object System.Collections.Generic.List[object]

  foreach ($name in @($dimensionStats.Keys)) {
    $stats = $dimensionStats[$name]
    if ([double]$stats.average -lt ([double]$stats.max * 0.8)) {
      $dimensionIssueIds = @($TaskRecords | Where-Object { $_.score.dimensions.PSObject.Properties.Name -contains $name } | ForEach-Object issueId)
      $proposals.Add((New-AutopilotImprovementProposal -Rule 'DIMENSION_AVERAGE_BELOW_80' -Subject $name -Summary "周期平均分 $($stats.average)/$($stats.max)，低于 80%。" -AcceptanceCriteria "该维度连续一个完整回顾周期平均分达到满分的 80% 及以上，且不降低硬门禁。" -ReviewCycleId $ReviewCycleId -SourceIssueIds $dimensionIssueIds))
    }
  }
  if ($firstPassRate -lt 0.8) {
    $proposals.Add((New-AutopilotImprovementProposal -Rule 'FIRST_PASS_BELOW_80' -Subject '首次验收通过率' -Summary "首次验收通过率为 $firstPassRate，低于 80%。" -AcceptanceCriteria '连续一个完整回顾周期首次验收通过率达到 80% 及以上。' -ReviewCycleId $ReviewCycleId -SourceIssueIds @($TaskRecords.issueId)))
  }
  if ($followupNetChange -gt 0) {
    $proposals.Add((New-AutopilotImprovementProposal -Rule 'FOLLOWUP_NET_GROWTH' -Subject '后续项净增长' -Summary "周期后续项净变化为 +$followupNetChange。" -AcceptanceCriteria '完成已登记同根因项处置，使后续项总量恢复到本周期开始前基线或更低。' -ReviewCycleId $ReviewCycleId -SourceIssueIds @($TaskRecords.issueId)))
  }
  $rootGroups = @($TaskRecords | Where-Object { $_.rootCause } | Group-Object rootCause | Where-Object Count -ge 3)
  foreach ($group in $rootGroups) {
    $proposals.Add((New-AutopilotImprovementProposal -Rule 'REPEATED_ROOT_CAUSE' -Subject ([string]$group.Name) -Summary "同一根因在本周期出现 $($group.Count) 次。" -AcceptanceCriteria "消除根因并用至少 3 条相关场景回归证明未再发生。" -ReviewCycleId $ReviewCycleId -SourceIssueIds @($group.Group.issueId)))
  }
  if ($null -ne $OpeningIssueCount -and $null -ne $ClosingIssueCount -and $ClosingIssueCount -ge $OpeningIssueCount -and @($TaskRecords | Where-Object { $_.stockIssueTarget }).Count -gt 0) {
    $proposals.Add((New-AutopilotImprovementProposal -Rule 'STOCK_NOT_DECREASING' -Subject '存量问题未下降' -Summary "当前问题数量从 $OpeningIssueCount 变为 $ClosingIssueCount。" -AcceptanceCriteria '完成一个以存量问题关闭为目标的周期后，当前问题总量相对期初下降。' -ReviewCycleId $ReviewCycleId -SourceIssueIds @($TaskRecords.issueId)))
  }
  if ($null -ne $PreviousMedianCycleSeconds -and $null -ne $medianCycle -and $PreviousMedianCycleSeconds -gt 0 -and $medianCycle -gt ($PreviousMedianCycleSeconds * 1.2)) {
    $proposals.Add((New-AutopilotImprovementProposal -Rule 'CYCLE_MEDIAN_REGRESSION' -Subject '周期中位耗时恶化' -Summary "中位耗时从 $PreviousMedianCycleSeconds 秒增加到 $medianCycle 秒。" -AcceptanceCriteria '在不裁剪必需验证的前提下，中位耗时恢复到上周期的 120% 以内。' -ReviewCycleId $ReviewCycleId -SourceIssueIds @($TaskRecords.issueId)))
  }

  $scoringVersions = @($TaskRecords | ForEach-Object { if ($_.score.PSObject.Properties.Name -contains 'scoringVersion' -and $_.score.scoringVersion) { [string]$_.score.scoringVersion } else { $ScoringVersion } } | Sort-Object -Unique)
  return [pscustomobject][ordered]@{
    schemaVersion = 1
    reviewCycleId = $ReviewCycleId
    scoringVersion = $ScoringVersion
    scoringVersions = @($scoringVersions)
    generatedAt = [datetimeoffset]::Now.ToString('o')
    taskCount = $TaskRecords.Count
    taskIssueIds = @($TaskRecords.issueId)
    total = [ordered]@{ average = [Math]::Round((($totals | Measure-Object -Average).Average), 2); median = [Math]::Round((Get-AutopilotMedian $totals), 2); minimum = [Math]::Round((($totals | Measure-Object -Minimum).Minimum), 2) }
    dimensions = $dimensionStats
    firstPassRate = $firstPassRate
    followupNetChange = $followupNetChange
    medianCycleSeconds = $medianCycle
    openingIssueCount = $OpeningIssueCount
    closingIssueCount = $ClosingIssueCount
    proposals = @($proposals.ToArray())
  }
}

function Get-AutopilotRetrospectiveEpisodeId {
  param([Parameter(Mandatory)][string]$ReviewCycleId, [Parameter(Mandatory)][string]$ScoringVersion)
  $versionKey = (Get-AutopilotRetrospectiveSha256 $ScoringVersion).Substring(0, 16)
  return ('cgc-pms:episode:autopilot-retrospective:{0}:{1}' -f $ReviewCycleId, $versionKey)
}

function Write-AutopilotRetrospectiveReport {
  param([Parameter(Mandatory)][object]$Retrospective, [Parameter(Mandatory)][string]$Path)
  $parent = Split-Path -Parent $Path
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  if (@($Retrospective.proposals).Count -eq 0) {
    $proposalLines = '- 无；聚合规则未产生需要确认的改进提案。'
  } else {
    $proposalLines = (@($Retrospective.proposals | ForEach-Object { '- [{0}] {1}: {2} ({3})' -f $_.proposalKey, $_.title, $_.summary, $_.approvalStatus }) -join [Environment]::NewLine)
  }
  $json = $Retrospective | ConvertTo-Json -Depth 14
  $fence = ([string][char]96) + ([string][char]96) + ([string][char]96)
  $markdown = @(
    "# AutoPilot 任务周期回顾：$($Retrospective.reviewCycleId)", '',
    "- 当前评分版本：$($Retrospective.scoringVersion)",
    "- 周期包含版本：$(@($Retrospective.scoringVersions) -join ', ')",
    "- 有效任务：$($Retrospective.taskCount)",
    "- 平均总分：$($Retrospective.total.average)",
    "- 首次验收通过率：$($Retrospective.firstPassRate)",
    "- 后续项净变化：$($Retrospective.followupNetChange)", '',
    '## 改进提案', '', $proposalLines, '',
    '## 结构化数据', '', ($fence + 'json'), $json, $fence, ''
  ) -join "`r`n"
  [IO.File]::WriteAllText($Path, $markdown, [Text.UTF8Encoding]::new($false))
  return $Path
}

function Merge-AutopilotImprovementProposals {
  param([Parameter(Mandatory)][string]$RegistryPath, [Parameter(Mandatory)][object[]]$Proposals, [Parameter(Mandatory)][string]$ReportPath)
  $registry = Get-Content -LiteralPath $RegistryPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $issues = @($registry.issues)
  $added = 0
  $merged = 0
  foreach ($proposal in $Proposals) {
    $existing = @($issues | Where-Object { [string]$_.issueKey -eq [string]$proposal.proposalKey })
    if ($existing.Count -gt 0) {
      $refs = @($existing[0].sourceRefs) + $ReportPath | Sort-Object -Unique
      $existing[0].sourceRefs = @($refs)
      $merged += 1
      continue
    }
    $issues += [pscustomobject][ordered]@{
      issueKey = [string]$proposal.proposalKey
      title = [string]$proposal.title
      status = 'NEEDS_CONFIRMATION'
      classification = 'NEEDS_CONFIRMATION'
      priority = [string]$proposal.priority
      blocking = $false
      summary = [string]$proposal.summary
      acceptanceCriteria = [string]$proposal.acceptanceCriteria
      deferReason = 'AutoPilot 回顾提案等待用户批准；未批准前不得补货或自动实施。'
      sourceRefs = @($ReportPath)
    }
    $added += 1
  }
  $registry.issues = @($issues)
  [IO.File]::WriteAllText($RegistryPath, ($registry | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]@{ added = $added; merged = $merged; total = @($issues).Count }
}
