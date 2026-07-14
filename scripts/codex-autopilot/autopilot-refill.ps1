$ErrorActionPreference = 'Stop'
$readyLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-ready.ps1'
if (Test-Path -LiteralPath $readyLibrary) { . $readyLibrary }
$commandLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-command.ps1'
if (Test-Path -LiteralPath $commandLibrary) { . $commandLibrary }
$nativeCommandLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue)) { . $nativeCommandLibrary }
$metricsLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-metrics.ps1'
if (!(Get-Command New-AutopilotInvocationId -ErrorAction SilentlyContinue)) { . $metricsLibrary }

function Test-AutopilotDomainContinuationAllowed {
  param([string[]]$RecentTitles, [string]$Domain, [string]$FocusText)
  $streak = 0
  foreach ($title in $RecentTitles) { if ($title -match [regex]::Escape($Domain)) { $streak++ } else { break } }
  if ($streak -ge 5) { return $FocusText -match '候选域对比' -and $FocusText -match '为何仍需继续当前域' -and $FocusText -match '为何其他候选域不应先做' }
  if ($streak -ge 3) { return $FocusText -match '候选域对比' }
  return $true
}

function Test-AutopilotReadyPlanningAllowed {
  param([string]$Action)
  return $Action -in @('PLAN_READY','GENERATE_READY')
}

function Get-AutopilotObjectPropertyValue {
  param([object]$Object, [string]$Name)
  if ($null -eq $Object) { return $null }
  if ($Object -is [System.Collections.IDictionary]) {
    if ($Object.Contains($Name)) { return $Object[$Name] }
    return $null
  }
  $property = $Object.PSObject.Properties[$Name]
  if ($property) { return $property.Value }
  return $null
}

function ConvertTo-AutopilotStringArray {
  param([object]$Value)
  return @($Value | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
}

function Get-AutopilotCandidateRef {
  param([Parameter(Mandatory)][object]$Candidate)
  $explicit = [string](Get-AutopilotObjectPropertyValue $Candidate 'candidateRef')
  if ($explicit) { return $explicit }
  $issueKey = [string](Get-AutopilotObjectPropertyValue $Candidate 'issueKey')
  if ($issueKey) { return $issueKey }
  $source = [string](Get-AutopilotObjectPropertyValue $Candidate 'source')
  $name = [string](Get-AutopilotObjectPropertyValue $Candidate 'name')
  if (!$source -or !$name) { throw 'candidateRef cannot be derived from candidate' }
  return "$source::$name"
}

function Get-AutopilotRefillStageFailureCategory {
  param([object[]]$CandidateDecisions)
  $categories = @($CandidateDecisions | Where-Object outcome -eq 'BLOCKED' | ForEach-Object { [string]$_.failureCategory })
  foreach ($category in @('quality_security','tool_config','environment_prereq','ready_issue_config')) {
    if ($categories -contains $category) { return $category }
  }
  if ($categories -contains 'needs_confirmation') { return 'ready_issue_config' }
  return 'ready_issue_config'
}

function Test-AutopilotAuthoritativeReadySpec {
  param([Parameter(Mandatory)][object]$Candidate, [Parameter(Mandatory)][string]$RepoRoot)
  $errors = @()
  $spec = Get-AutopilotObjectPropertyValue $Candidate 'readySpec'
  if ($null -eq $spec) {
    return [pscustomobject]@{ eligible = $false; errors = @('authoritative readySpec is missing'); spec = $null }
  }
  $requiredScalars = @('readyIssueId','taskNature','migration','dependencies','riskLevel','runtimeRequirement','reviewerRequirement','archiveReport')
  $requiredArrays = @('goal','nonGoals','allowedPaths','forbiddenPaths','acceptanceCriteria','validationCommands')
  foreach ($name in $requiredScalars) {
    if ([string]::IsNullOrWhiteSpace([string](Get-AutopilotObjectPropertyValue $spec $name))) { $errors += "readySpec.$name is missing" }
  }
  foreach ($name in $requiredArrays) {
    if (@(ConvertTo-AutopilotStringArray (Get-AutopilotObjectPropertyValue $spec $name)).Count -eq 0) { $errors += "readySpec.$name is missing" }
  }
  $readyIssueId = [string](Get-AutopilotObjectPropertyValue $spec 'readyIssueId')
  if ($readyIssueId -and $readyIssueId -notmatch '^ISSUE-[0-9-]+$') { $errors += 'readySpec.readyIssueId is invalid' }
  $taskNature = [string](Get-AutopilotObjectPropertyValue $spec 'taskNature')
  if ($taskNature -and $taskNature -notin @('能力新增','缺口修复','回归证明','运维治理')) { $errors += 'readySpec.taskNature is invalid' }
  $migration = [string](Get-AutopilotObjectPropertyValue $spec 'migration')
  if ($migration -and $migration -notin @('需要','不需要')) { $errors += 'readySpec.migration is invalid' }
  $riskLevel = [string](Get-AutopilotObjectPropertyValue $spec 'riskLevel')
  if ($riskLevel -and $riskLevel -notin @('低','中','高')) { $errors += 'readySpec.riskLevel is invalid' }
  $candidateEvidenceHead = [string](Get-AutopilotObjectPropertyValue $Candidate 'candidateEvidenceHead')
  if ($candidateEvidenceHead -notmatch '^[a-f0-9]{40}$') { $errors += 'candidateEvidenceHead must be a 40-character lowercase Git SHA' }
  foreach ($sourceRef in @(ConvertTo-AutopilotStringArray (Get-AutopilotObjectPropertyValue $Candidate 'sourceRefs'))) {
    $pathText = ($sourceRef -split '(?=:\d+(?::\d+)?$)')[0]
    if ($pathText -match '^[a-zA-Z]+://') { continue }
    $fullPath = Join-Path $RepoRoot $pathText
    if (!(Test-Path -LiteralPath $fullPath)) { $errors += "sourceRef does not resolve on current branch: $sourceRef" }
  }
  $allowedPaths = @(ConvertTo-AutopilotStringArray (Get-AutopilotObjectPropertyValue $spec 'allowedPaths'))
  $forbiddenPaths = @(ConvertTo-AutopilotStringArray (Get-AutopilotObjectPropertyValue $spec 'forbiddenPaths'))
  if ($allowedPaths.Count -gt 0 -and $forbiddenPaths.Count -gt 0) {
    try { Assert-AutopilotReadyScopeContract -IssueId $readyIssueId -AllowedPaths $allowedPaths -ForbiddenPaths $forbiddenPaths } catch { $errors += $_.Exception.Message }
  }
  foreach ($command in @(ConvertTo-AutopilotStringArray (Get-AutopilotObjectPropertyValue $spec 'validationCommands'))) {
    $entryError = Test-AutopilotValidationEntry -Command $command -RepoRoot $RepoRoot
    if ($entryError) { $errors += $entryError }
  }
  $archiveReport = [string](Get-AutopilotObjectPropertyValue $spec 'archiveReport')
  if ($archiveReport -and !$archiveReport.StartsWith('docs/quality/', [StringComparison]::OrdinalIgnoreCase)) { $errors += 'readySpec.archiveReport must be under docs/quality/' }
  return [pscustomobject]@{ eligible = $errors.Count -eq 0; errors = @($errors); spec = $spec }
}

function ConvertTo-AutopilotMarkdownList {
  param([object]$Values, [switch]$Code)
  $items = @(ConvertTo-AutopilotStringArray $Values)
  if ($items.Count -eq 0) { return @('- 无') }
  if ($Code) { return @($items | ForEach-Object { '- `' + $_ + '`' }) }
  return @($items | ForEach-Object { '- ' + $_ })
}

function New-AutopilotDeterministicReadyPlan {
  param([Parameter(Mandatory)][object]$Candidate, [Parameter(Mandatory)][string]$RepoRoot)
  $validation = Test-AutopilotAuthoritativeReadySpec -Candidate $Candidate -RepoRoot $RepoRoot
  if (!$validation.eligible) { throw "candidate is not eligible for deterministic Ready generation: $($validation.errors -join '; ')" }
  $spec = $validation.spec
  $issueId = [string](Get-AutopilotObjectPropertyValue $spec 'readyIssueId')
  $title = [string](Get-AutopilotObjectPropertyValue $Candidate 'name')
  $marker = [string](Get-AutopilotObjectPropertyValue $Candidate 'marker')
  $candidateRef = Get-AutopilotCandidateRef -Candidate $Candidate
  $candidateEvidenceHead = [string](Get-AutopilotObjectPropertyValue $Candidate 'candidateEvidenceHead')
  $sourceRefs = @(ConvertTo-AutopilotStringArray (Get-AutopilotObjectPropertyValue $Candidate 'sourceRefs'))
  $sourceAnchor = (@($sourceRefs | ForEach-Object { '`' + $_ + '`' }) -join ', ') + "; candidateEvidenceHead=$candidateEvidenceHead"
  $lines = @(
    "### $issueId：$title",
    "任务性质：$([string](Get-AutopilotObjectPropertyValue $spec 'taskNature'))",
    '目标：'
  )
  $lines += ConvertTo-AutopilotMarkdownList (Get-AutopilotObjectPropertyValue $spec 'goal')
  $lines += '非目标：'
  $lines += ConvertTo-AutopilotMarkdownList (Get-AutopilotObjectPropertyValue $spec 'nonGoals')
  $lines += '允许修改：'
  $lines += ConvertTo-AutopilotMarkdownList (Get-AutopilotObjectPropertyValue $spec 'allowedPaths') -Code
  $lines += '禁止修改：'
  $lines += ConvertTo-AutopilotMarkdownList (Get-AutopilotObjectPropertyValue $spec 'forbiddenPaths') -Code
  $lines += '验收标准：'
  $lines += ConvertTo-AutopilotMarkdownList (Get-AutopilotObjectPropertyValue $spec 'acceptanceCriteria')
  $lines += '状态：Ready'
  $lines += "来源锚点：$sourceAnchor"
  if ($marker) { $lines += "存量问题键：$marker" }
  $lines += '验证命令：'
  $lines += ConvertTo-AutopilotMarkdownList (Get-AutopilotObjectPropertyValue $spec 'validationCommands') -Code
  $lines += ('归档报告：`' + [string](Get-AutopilotObjectPropertyValue $spec 'archiveReport') + '`')
  $lines += "Migration：$([string](Get-AutopilotObjectPropertyValue $spec 'migration'))"
  $lines += "依赖：$([string](Get-AutopilotObjectPropertyValue $spec 'dependencies'))"
  $lines += "风险等级：$([string](Get-AutopilotObjectPropertyValue $spec 'riskLevel'))"
  $lines += "运行态要求：$([string](Get-AutopilotObjectPropertyValue $spec 'runtimeRequirement'))"
  $lines += "Reviewer要求：$([string](Get-AutopilotObjectPropertyValue $spec 'reviewerRequirement'))"
  $block = $lines -join [Environment]::NewLine
  return [pscustomobject][ordered]@{
    schemaVersion = 2
    candidateDecisions = @([pscustomobject][ordered]@{ candidateRef = $candidateRef; outcome = 'CREATED'; reason = 'authoritative ReadySpec passed deterministic validation'; readyIssueId = $issueId })
    readyBlocks = @($block)
  }
}

function Invoke-AutopilotKnowledgeGraphCli {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$CliPath,
    [Parameter(Mandatory)][string[]]$Arguments,
    [scriptblock]$CommandInvoker
  )
  if ($CommandInvoker) { return & $CommandInvoker $CliPath $Arguments $RepoRoot }
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = 'node'
  $quoted = @($CliPath) + @($Arguments) | ForEach-Object { if ($_ -match '[\s"]') { '"' + $_.Replace('"','\"') + '"' } else { $_ } }
  $startInfo.Arguments = $quoted -join ' '
  $startInfo.WorkingDirectory = $RepoRoot
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo
  [void]$process.Start()
  $stdout = $process.StandardOutput.ReadToEnd(); $stderr = $process.StandardError.ReadToEnd()
  $process.WaitForExit()
  if ($process.ExitCode -ne 0) { throw "knowledge graph CLI failed (exit=$($process.ExitCode)): $($stderr.Trim())" }
  if (!$stdout.Trim()) { throw 'knowledge graph CLI returned empty output' }
  return $stdout | ConvertFrom-Json
}

function Get-AutopilotKnowledgeGraphIssueSnapshot {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [scriptblock]$CommandInvoker
  )
  try {
    $configPath = Join-Path $RepoRoot 'scripts\codex-autopilot\codex-autopilot.config.json'
    if (!(Test-Path -LiteralPath $configPath -PathType Leaf)) { throw 'AutoPilot config is missing' }
    $config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if (!$config.issueGraph -or $config.issueGraph.enabled -ne $true) { throw 'issueGraph.enabled must be true' }
    if ($config.issueGraph.allowRegistryFallback -eq $true) { throw 'issueGraph.allowRegistryFallback must remain false' }
    $cliPath = [string]$config.issueGraph.cli
    if (!$cliPath) { throw 'issueGraph.cli is required' }
    $cliFull = if ([IO.Path]::IsPathRooted($cliPath)) { $cliPath } else { Join-Path $RepoRoot $cliPath }
    if (!(Test-Path -LiteralPath $cliFull -PathType Leaf) -and !$CommandInvoker) { throw "issueGraph.cli does not exist: $cliPath" }
    $limit = [Math]::Max(1, [Math]::Min([int]$config.issueGraph.queryLimit, 200))
    $head = (Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
    if (!$head) { throw 'cannot resolve current Git HEAD' }

    $readStatus = {
      $value = Invoke-AutopilotKnowledgeGraphCli -RepoRoot $RepoRoot -CliPath $cliFull -Arguments @('status') -CommandInvoker $CommandInvoker
      if ($value -is [array]) { return $value | Select-Object -First 1 }
      return $value
    }
    $status = & $readStatus
    $gitCursor = @($status.cursors | Where-Object { $_.source -eq 'git' } | Select-Object -First 1)[0]
    $healthyRun = !$status.lastRunStatus -or ([string]$status.lastRunStatus).ToUpperInvariant() -in @('SUCCESS','SUCCEEDED','COMPLETED')
    $failures = if ($null -eq $status.lastRunFailures) { 0 } else { [int]$status.lastRunFailures }
    if (!$healthyRun -or $failures -ne 0 -or $null -eq $status.currentIssues) { throw 'knowledge graph status is unhealthy or incomplete' }
    $refreshed = $false
    if (!$gitCursor -or [string]$gitCursor.cursor -ne $head) {
      if ($config.issueGraph.refreshWhenHeadDiffers -ne $true) {
        return [pscustomobject]@{ available = $false; stopReason = 'STOP_KG_REFILL_STALE'; failureCategory = 'quality_security'; message = 'git cursor differs from current HEAD and refresh is disabled'; issues = @() }
      }
      $null = Invoke-AutopilotKnowledgeGraphCli -RepoRoot $RepoRoot -CliPath $cliFull -Arguments @('collect','--trigger','autopilot-refill') -CommandInvoker $CommandInvoker
      $refreshed = $true
      $status = & $readStatus
      $gitCursor = @($status.cursors | Where-Object { $_.source -eq 'git' } | Select-Object -First 1)[0]
      $healthyRun = !$status.lastRunStatus -or ([string]$status.lastRunStatus).ToUpperInvariant() -in @('SUCCESS','SUCCEEDED','COMPLETED')
      $failures = if ($null -eq $status.lastRunFailures) { 0 } else { [int]$status.lastRunFailures }
      if (!$healthyRun -or $failures -ne 0 -or !$gitCursor -or [string]$gitCursor.cursor -ne $head) {
        return [pscustomobject]@{ available = $false; stopReason = 'STOP_KG_REFILL_STALE'; failureCategory = 'quality_security'; message = 'knowledge graph remains stale or unhealthy after one refresh'; issues = @() }
      }
    }
    $issueResult = Invoke-AutopilotKnowledgeGraphCli -RepoRoot $RepoRoot -CliPath $cliFull -Arguments @('issues','--view','list','--current-only','--limit',[string]$limit) -CommandInvoker $CommandInvoker
    if ($null -eq $issueResult.total -or $null -eq $issueResult.issues) { throw 'knowledge graph issues response does not match the expected schema' }
    if ([int]$status.currentIssues -ne [int]$issueResult.total) {
      return [pscustomobject]@{ available = $false; stopReason = 'STOP_KG_REFILL_STALE'; failureCategory = 'quality_security'; message = 'status currentIssues differs from issues query total'; issues = @() }
    }
    return [pscustomobject]@{ available = $true; source = 'knowledge-graph'; head = $head; cursor = [string]$gitCursor.cursor; refreshed = $refreshed; issues = @($issueResult.issues) }
  } catch {
    $message = $_.Exception.Message
    $category = if ($message -match 'ECONNREFUSED|connection|connectivity|Neo4j|socket|port') { 'environment_prereq' } else { 'tool_config' }
    return [pscustomobject]@{ available = $false; stopReason = 'STOP_KG_REFILL_UNAVAILABLE'; failureCategory = $category; message = $message; issues = @() }
  }
}

function Get-AutopilotStockIssueCandidates {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][object]$KnowledgeGraphSnapshot,
    [int]$Limit = 5
  )

  $backlog = Join-Path $RepoRoot 'docs\backlog'
  $issues = @($KnowledgeGraphSnapshot.issues)

  $existingText = ''
  foreach ($name in @('ready-issues.md', 'done-issues.md', 'blocked-issues.md')) {
    $path = Join-Path $backlog $name
    if (Test-Path -LiteralPath $path) { $existingText += "`n" + (Get-Content -LiteralPath $path -Raw -Encoding UTF8) }
  }
  $parentKeys = @($issues | Where-Object { $_.parentIssueKey } | ForEach-Object { [string]$_.parentIssueKey } | Select-Object -Unique)
  $priorityOrder = @{ P0 = 0; P1 = 1; P2 = 2 }
  $statusOrder = @{ OPEN = 0; OBSERVATION = 1 }
  $registryIssues = @()
  $registryPath = Join-Path $backlog 'current-issues.json'
  if (Test-Path -LiteralPath $registryPath -PathType Leaf) {
    try { $registryIssues = @((Get-Content -LiteralPath $registryPath -Raw -Encoding UTF8 | ConvertFrom-Json).issues) } catch { $registryIssues = @() }
  }

  $candidates = foreach ($issue in $issues) {
    $issueKey = [string]$issue.issueKey
    $marker = "[stock:$issueKey]"
    if (!$issueKey -or $issue.blocking -eq $true) { continue }
    if ([string]$issue.status -notin @('OPEN', 'OBSERVATION')) { continue }
    if ([string]$issue.classification -notin @('STILL_APPLICABLE', 'NON_BLOCKING_OBSERVATION', 'OPERATIONAL_RISK')) { continue }
    if ($parentKeys -contains $issueKey) { continue }
    if (!$issue.acceptanceCriteria -or @($issue.sourceRefs).Count -eq 0) { continue }
    if ($existingText.Contains($marker)) { continue }
    $readySpec = Get-AutopilotObjectPropertyValue $issue 'readySpec'
    if ($null -eq $readySpec) {
      $registryIssue = @($registryIssues | Where-Object { [string]$_.issueKey -eq $issueKey } | Select-Object -First 1)[0]
      if ($registryIssue) { $readySpec = Get-AutopilotObjectPropertyValue $registryIssue 'readySpec' }
    }

    [pscustomobject]@{
      name = [string]$issue.title
      status = [string]$issue.status
      source = 'knowledge-graph'
      issueKey = $issueKey
      marker = $marker
      priority = [string]$issue.priority
      classification = [string]$issue.classification
      parentIssueKey = if ($issue.parentIssueKey) { [string]$issue.parentIssueKey } else { $null }
      summary = [string]$issue.summary
      acceptanceCriteria = [string]$issue.acceptanceCriteria
      sourceRefs = @($issue.sourceRefs)
      candidateRef = $issueKey
      readySpec = $readySpec
      priorityOrder = if ($priorityOrder.ContainsKey([string]$issue.priority)) { $priorityOrder[[string]$issue.priority] } else { 99 }
      statusOrder = if ($statusOrder.ContainsKey([string]$issue.status)) { $statusOrder[[string]$issue.status] } else { 99 }
      specificityOrder = if ($issue.parentIssueKey) { 0 } else { 1 }
    }
  }

  return @($candidates | Sort-Object priorityOrder,statusOrder,specificityOrder,issueKey | Select-Object -First ([Math]::Max(0, [Math]::Min($Limit, 5))))
}

function Get-AutopilotRefillDecision {
  param([Parameter(Mandatory)][string]$RepoRoot, [object]$KnowledgeGraphSnapshot)
  $autoDir = Join-Path $RepoRoot '.codex-autopilot'
  $backlog = Join-Path $RepoRoot 'docs\backlog'
  if (Test-Path -LiteralPath (Join-Path $autoDir 'stop.flag')) { return [pscustomobject]@{ action = 'STOP'; targetReadyCount = 0; candidates = @(); reason = 'stop.flag present' } }
  if (Test-Path -LiteralPath (Join-Path $autoDir 'pause.flag')) { return [pscustomobject]@{ action = 'PAUSE'; targetReadyCount = 0; candidates = @(); reason = 'pause.flag present' } }
  $readyPath = Join-Path $backlog 'ready-issues.md'
  $readyCount = if (Test-Path -LiteralPath $readyPath) { @(Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $RepoRoot).Count } else { 0 }
  if ($readyCount -ge 1) { return [pscustomobject]@{ action = 'READY_SUFFICIENT'; targetReadyCount = $readyCount; candidates = @(); reason = 'Ready queue already has at least one item' } }

  $needed = [Math]::Max(0, 1 - $readyCount)
  if (!$KnowledgeGraphSnapshot) { $KnowledgeGraphSnapshot = Get-AutopilotKnowledgeGraphIssueSnapshot -RepoRoot $RepoRoot }
  if ($KnowledgeGraphSnapshot.available -ne $true) {
    return [pscustomobject]@{ action = [string]$KnowledgeGraphSnapshot.stopReason; targetReadyCount = $readyCount; candidates = @(); reason = [string]$KnowledgeGraphSnapshot.message; failureCategory = [string]$KnowledgeGraphSnapshot.failureCategory }
  }
  $stockCandidates = @(Get-AutopilotStockIssueCandidates -RepoRoot $RepoRoot -KnowledgeGraphSnapshot $KnowledgeGraphSnapshot -Limit ([Math]::Min($needed, 5 - $readyCount)))
  if ($stockCandidates.Count -gt 0) {
    foreach ($candidate in $stockCandidates) {
      $candidate | Add-Member -NotePropertyName candidateEvidenceHead -NotePropertyValue ([string]$KnowledgeGraphSnapshot.head).ToLowerInvariant() -Force
    }
    $fastValidation = Test-AutopilotAuthoritativeReadySpec -Candidate $stockCandidates[0] -RepoRoot $RepoRoot
    return [pscustomobject]@{
      action = if ($fastValidation.eligible) { 'GENERATE_READY' } else { 'PLAN_READY' }
      targetReadyCount = $readyCount + $stockCandidates.Count
      candidates = $stockCandidates
      reason = if ($fastValidation.eligible) { 'top-ranked current stock issue has a complete authoritative ReadySpec' } else { 'eligible current stock issue requires bounded semantic planning' }
      fastPathErrors = @($fastValidation.errors)
      candidateEvidenceHead = [string]$KnowledgeGraphSnapshot.head
    }
  }

  $focusPath = Join-Path $backlog 'current-focus.md'; $blockedPath = Join-Path $backlog 'blocked-issues.md'
  $focusText = if (Test-Path -LiteralPath $focusPath) { Get-Content -LiteralPath $focusPath -Raw -Encoding UTF8 } else { '' }
  $blockedText = if (Test-Path -LiteralPath $blockedPath) { Get-Content -LiteralPath $blockedPath -Raw -Encoding UTF8 } else { '' }
  $focusMatch = [regex]::Match($focusText, '(?im)当前\s*focus[：:]\s*([^\r\n]+)')
  if ($focusMatch.Success -and $blockedText -match [regex]::Escape($focusMatch.Groups[1].Value.Trim())) {
    return [pscustomobject]@{ action = 'UNBLOCK_FIRST'; targetReadyCount = 1; candidates = @([pscustomobject]@{ name = $focusMatch.Groups[1].Value.Trim(); status = 'BlockedPrerequisite'; source = 'blocked-issues.md' }); reason = 'current focus prerequisite is blocked' }
  }

  $adHocPath = Join-Path $backlog 'ad-hoc-plan.md'
  $candidates = @()
  if (Test-Path -LiteralPath $adHocPath) {
    $adHoc = Get-Content -LiteralPath $adHocPath -Raw -Encoding UTF8
    foreach ($match in [regex]::Matches($adHoc, '(?m)^\|\s*([^|]+?)\s*\|\s*高\s*\|\s*(ReadyToSplit|Candidate)\s*\|')) {
      $name = $match.Groups[1].Value.Trim()
      $candidates += [pscustomobject]@{ name = $name; status = $match.Groups[2].Value; source = 'ad-hoc-plan.md'; candidateRef = "ad-hoc-plan.md::$name" }
    }
    $candidates = @($candidates | Sort-Object @{Expression={ if ($_.status -eq 'ReadyToSplit') { 0 } else { 1 } }} | Select-Object -Unique name,status,source,candidateRef)
  }
  $selected = @($candidates | Select-Object -First ([Math]::Min($needed, 5 - $readyCount)))
  if ($selected.Count -eq 0) { return [pscustomobject]@{ action = 'NO_CANDIDATES'; targetReadyCount = $readyCount; candidates = @(); reason = 'no eligible stock issue or decision-backed ad-hoc candidate; refresh product intelligence before splitting Ready' } }
  return [pscustomobject]@{ action = 'PLAN_READY'; targetReadyCount = $readyCount + $selected.Count; candidates = $selected; reason = 'stock issues are exhausted; fresh Planner may split decision-backed ad-hoc candidates' }
}

function Import-AutopilotReadyPlan {
  param([string]$PlanPath, [string]$ReadyPath, [string]$RepoRoot, [string[]]$ExpectedCandidateRefs = @())
  $plan = Get-Content -LiteralPath $PlanPath -Raw -Encoding UTF8 | ConvertFrom-Json
  if ($plan.schemaVersion -ne 2) { throw 'ready planner result must use schemaVersion=2' }
  $decisions = @($plan.candidateDecisions)
  if ($decisions.Count -lt 1 -or $decisions.Count -gt 5) { throw 'ready planner must return 1..5 candidate decisions' }
  $decisionRefs = @($decisions | ForEach-Object { [string]$_.candidateRef })
  if ($decisionRefs -contains '') { throw 'each candidate decision must contain candidateRef' }
  if (@($decisionRefs | Select-Object -Unique).Count -ne $decisionRefs.Count) { throw 'candidate decision refs must be unique' }
  if ($ExpectedCandidateRefs.Count -gt 0) {
    $expected = @($ExpectedCandidateRefs | Sort-Object -Unique)
    $actual = @($decisionRefs | Sort-Object -Unique)
    if ($expected.Count -ne $actual.Count -or (Compare-Object $expected $actual)) { throw 'candidate decisions must exactly cover the bounded candidate set' }
  }
  $allowedOutcomes = @('CREATED','REJECTED','BLOCKED')
  $allowedBlockedCategories = @('ready_issue_config','tool_config','environment_prereq','quality_security','needs_confirmation')
  foreach ($decision in $decisions) {
    if ([string]$decision.outcome -notin $allowedOutcomes) { throw "unsupported candidate outcome: $($decision.outcome)" }
    if ([string]::IsNullOrWhiteSpace([string]$decision.reason)) { throw "candidate decision reason is required: $($decision.candidateRef)" }
    if ($decision.outcome -eq 'BLOCKED' -and [string]$decision.failureCategory -notin $allowedBlockedCategories) { throw "unsupported blocked failureCategory: $($decision.failureCategory)" }
    if ($decision.outcome -ne 'BLOCKED' -and $decision.PSObject.Properties.Name -contains 'failureCategory' -and ![string]::IsNullOrWhiteSpace([string]$decision.failureCategory)) { throw 'failureCategory is only valid for BLOCKED decisions' }
  }
  $blocks = @($plan.readyBlocks)
  $createdDecisions = @($decisions | Where-Object outcome -eq 'CREATED')
  if ($blocks.Count -ne $createdDecisions.Count) { throw 'readyBlocks must map one-to-one to CREATED candidate decisions' }
  if ($blocks.Count -gt 5) { throw 'ready planner must return at most five blocks' }
  if ($blocks.Count -eq 0) {
    $existingCount = if (Test-Path -LiteralPath $ReadyPath) { @(Get-AutopilotReadyIssues -Path $ReadyPath -RepoRoot $RepoRoot).Count } else { 0 }
    return [pscustomobject]@{ createdCount = 0; totalReadyCount = $existingCount; issueIds = @(); candidateDecisions = $decisions }
  }
  foreach ($block in $blocks) { if ([regex]::Matches([string]$block, '(?m)^###\s+ISSUE-[0-9-]+').Count -ne 1) { throw 'each planned block must contain exactly one Issue' } }
  $plannedIds = @($blocks | ForEach-Object { $match = [regex]::Match([string]$_, '(?m)^###\s+(ISSUE-[0-9-]+)'); if (!$match.Success) { throw 'planned block is missing an Issue header' }; $match.Groups[1].Value })
  if (@($plannedIds | Select-Object -Unique).Count -ne $blocks.Count) { throw 'planned Ready Issue IDs must be unique' }
  for ($index = 0; $index -lt $createdDecisions.Count; $index++) {
    if ([string]$createdDecisions[$index].readyIssueId -ne $plannedIds[$index]) { throw "CREATED decision readyIssueId does not match ready block: $($createdDecisions[$index].candidateRef)" }
  }
  $existing = if (Test-Path -LiteralPath $ReadyPath) { Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8 } else { '# Ready Issues' }
  $tempPath = "$ReadyPath.$([guid]::NewGuid().ToString('N')).tmp"
  try {
    [IO.File]::WriteAllText($tempPath, ($existing.TrimEnd() + [Environment]::NewLine + [Environment]::NewLine + ($blocks -join ([Environment]::NewLine + [Environment]::NewLine))), [Text.UTF8Encoding]::new($false))
    $issues = @(Get-AutopilotReadyIssues -Path $tempPath -RepoRoot $RepoRoot)
    foreach ($plannedId in $plannedIds) { if ($issues.issueId -notcontains $plannedId) { throw "planned block did not produce a strict Ready Issue: $plannedId" } }
    if ($issues.Count -gt 5) { throw 'refill would exceed five Ready Issues' }
    [IO.File]::Copy($tempPath, $ReadyPath, $true)
    return [pscustomobject]@{ createdCount = $blocks.Count; totalReadyCount = $issues.Count; issueIds = @($plannedIds); candidateDecisions = $decisions }
  } finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
  }
}

function Invoke-AutopilotReadyPlanner {
  param(
    [string]$RepoRoot,
    [object[]]$Candidates,
    [string]$OutputPath,
    [string]$SchemaPath,
    [string]$Model = 'gpt-5.6-sol',
    [string]$Thinking = 'high',
    [int]$TimeoutSeconds = 300,
    [int]$HeartbeatSeconds = 30,
    [scriptblock]$HeartbeatWriter,
    [Parameter(Mandatory)][string]$RunId,
    [string[]]$CandidateRefs = @(),
    [scriptblock]$InvocationWriter
  )
  $codex = Resolve-AutopilotCodexInvocation
  $candidateJson = $Candidates | ConvertTo-Json -Depth 5 -Compress
  $prompt = @"
Act as the fresh AutoPilot Planner for cgc-pms. Read AGENTS.override.md, AGENTS.md, docs/backlog/current-focus.md,
docs/backlog/ready-issues.md, docs/backlog/blocked-issues.md, docs/backlog/ad-hoc-plan.md,
docs/product-intelligence/project-map.md and docs/product-intelligence/evolution-decision.md. Do not scan the complete
current-issues.json registry to discover alternatives; discovery has already been bounded by the knowledge graph.
Decide only these bounded candidates: $candidateJson
Before producing a block, verify that candidate against its sourceRefs, current branch code/configuration, and the unique
backlog carrier. Explicitly decide whether the issue still exists, user value is clear, acceptance is executable,
dependencies are satisfied, and it is not duplicated by Ready/Done/Blocked. If verification fails, do not create Ready.
Return schemaVersion=2 and exactly one candidateDecisions entry for every supplied candidateRef. Return one readyBlocks
entry for every CREATED decision, in the same order. REJECTED/BLOCKED-only output with zero Ready blocks is valid.
BLOCKED failureCategory must be ready_issue_config, tool_config, environment_prereq, quality_security, or needs_confirmation.
Each block must satisfy scripts/codex-autopilot/autopilot-ready.ps1, use real existing paths and validation entrypoints,
state explicit non-goals/migration/risk/runtime/reviewer requirements, and must not modify business code.
In 禁止修改, put only actual forbidden path rules in backticks. Never repeat an allowed path there, including inside
carve-out or exception prose, because every code-formatted value in that section is parsed as a forbidden rule. Rely on
允许修改 as the positive allowlist and omit explanatory exception paths from 禁止修改.
For a stock candidate, preserve its exact marker as a line like 存量问题键：[stock:ISSUE_KEY], cite the
formal registry and sourceRefs, keep the smallest executable acceptance slice, and require closeout to update/remove that source
issue in current-issues.json after verification. Never turn RELEASE_GATE, blocking=true, FROZEN, NEEDS_CONFIRMATION,
an aggregate parent with children, or an evidence-incomplete item into Ready. Never split directly from the long-term plan.
Return JSON matching $SchemaPath.
"@
  $args = @('exec','--ephemeral','--sandbox','danger-full-access','--model',$Model,'-c',"model_reasoning_effort=$Thinking",'--cd',$RepoRoot,'--output-schema',$SchemaPath,'--output-last-message',$OutputPath,'-')
  $args = @(Get-AutopilotCodexRedirectedStdinArguments -Arguments $args)
  $startInfo = [Diagnostics.ProcessStartInfo]::new(); $startInfo.FileName = $codex.fileName
  $startInfo.Arguments = (@($codex.argumentPrefix) + $args | ForEach-Object { if ($_ -match '[\s"]') { '"' + $_.Replace('"','\"') + '"' } else { $_ } }) -join ' '
  $startInfo.WorkingDirectory = $RepoRoot; $startInfo.UseShellExecute = $false; $startInfo.RedirectStandardInput = $true; $startInfo.RedirectStandardOutput = $true; $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo
  $startedAt = [datetimeoffset]::Now.ToString('o')
  [void]$process.Start()
  $invocationId = New-AutopilotInvocationId -Role PLANNER -Scope RUN -ScopeId $RunId -ProcessId $process.Id -StartedAt $startedAt
  $invocationEvent = New-AutopilotModelInvocationEvent -Role PLANNER -Scope RUN -ScopeId $RunId -InvocationId $invocationId -RunId $RunId -CandidateRefs $CandidateRefs -ProcessId $process.Id -StartedAt $startedAt
  if ($InvocationWriter) { & $InvocationWriter $invocationEvent }
  elseif (Get-Command Write-RunEvent -ErrorAction SilentlyContinue) { Write-RunEvent 'model.invocation' $invocationEvent }
  $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync(); $process.StandardInput.Write($prompt); $process.StandardInput.Close()
  $deadline = [datetimeoffset]::Now.AddSeconds($TimeoutSeconds)
  $nextHeartbeat = [datetimeoffset]::Now.AddSeconds([Math]::Max(1, $HeartbeatSeconds))
  while (!$process.HasExited) {
    if ([datetimeoffset]::Now -ge $deadline) { $process.Kill($true); throw "Ready Planner timed out after $TimeoutSeconds seconds" }
    if ($HeartbeatWriter -and [datetimeoffset]::Now -ge $nextHeartbeat) {
      & $HeartbeatWriter ([pscustomobject]@{ phase = 'READY_PLANNER'; pid = $process.Id; observedAt = [datetimeoffset]::Now.ToString('o') })
      $nextHeartbeat = [datetimeoffset]::Now.AddSeconds([Math]::Max(1, $HeartbeatSeconds))
    }
    [Threading.Thread]::Sleep(200)
  }
  $process.WaitForExit(); $null = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
  if ($process.ExitCode -ne 0) { throw "Ready Planner failed: $stderr" }
  return Get-Content -LiteralPath $OutputPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
