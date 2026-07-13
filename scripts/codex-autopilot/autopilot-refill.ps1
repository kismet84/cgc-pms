$ErrorActionPreference = 'Stop'
$readyLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-ready.ps1'
if (Test-Path -LiteralPath $readyLibrary) { . $readyLibrary }
$commandLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-command.ps1'
if (Test-Path -LiteralPath $commandLibrary) { . $commandLibrary }

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
  return $Action -eq 'PLAN_READY'
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
    $head = (& git -C $RepoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or !$head) { throw 'cannot resolve current Git HEAD' }

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

  $candidates = foreach ($issue in $issues) {
    $issueKey = [string]$issue.issueKey
    $marker = "[stock:$issueKey]"
    if (!$issueKey -or $issue.blocking -eq $true) { continue }
    if ([string]$issue.status -notin @('OPEN', 'OBSERVATION')) { continue }
    if ([string]$issue.classification -notin @('STILL_APPLICABLE', 'NON_BLOCKING_OBSERVATION', 'OPERATIONAL_RISK')) { continue }
    if ($parentKeys -contains $issueKey) { continue }
    if (!$issue.acceptanceCriteria -or @($issue.sourceRefs).Count -eq 0) { continue }
    if ($existingText.Contains($marker)) { continue }

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
    return [pscustomobject]@{
      action = 'PLAN_READY'
      targetReadyCount = $readyCount + $stockCandidates.Count
      candidates = $stockCandidates
      reason = 'eligible current stock issues take precedence over blockers, ad-hoc candidates, and product discovery'
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
      $candidates += [pscustomobject]@{ name = $match.Groups[1].Value.Trim(); status = $match.Groups[2].Value; source = 'ad-hoc-plan.md' }
    }
    $candidates = @($candidates | Sort-Object @{Expression={ if ($_.status -eq 'ReadyToSplit') { 0 } else { 1 } }} | Select-Object -Unique name,status,source)
  }
  $selected = @($candidates | Select-Object -First ([Math]::Min($needed, 5 - $readyCount)))
  if ($selected.Count -eq 0) { return [pscustomobject]@{ action = 'NO_CANDIDATES'; targetReadyCount = $readyCount; candidates = @(); reason = 'no eligible stock issue or decision-backed ad-hoc candidate; refresh product intelligence before splitting Ready' } }
  return [pscustomobject]@{ action = 'PLAN_READY'; targetReadyCount = $readyCount + $selected.Count; candidates = $selected; reason = 'stock issues are exhausted; fresh Planner may split decision-backed ad-hoc candidates' }
}

function Import-AutopilotReadyPlan {
  param([string]$PlanPath, [string]$ReadyPath, [string]$RepoRoot)
  $plan = Get-Content -LiteralPath $PlanPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $blocks = @($plan.readyBlocks)
  if ($blocks.Count -lt 1 -or $blocks.Count -gt 5) { throw 'ready planner must return 1..5 blocks' }
  foreach ($block in $blocks) { if ([regex]::Matches([string]$block, '(?m)^###\s+ISSUE-[0-9-]+').Count -ne 1) { throw 'each planned block must contain exactly one Issue' } }
  $plannedIds = @($blocks | ForEach-Object { $match = [regex]::Match([string]$_, '(?m)^###\s+(ISSUE-[0-9-]+)'); if (!$match.Success) { throw 'planned block is missing an Issue header' }; $match.Groups[1].Value })
  if (@($plannedIds | Select-Object -Unique).Count -ne $blocks.Count) { throw 'planned Ready Issue IDs must be unique' }
  $existing = if (Test-Path -LiteralPath $ReadyPath) { Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8 } else { '# Ready Issues' }
  $tempPath = "$ReadyPath.$([guid]::NewGuid().ToString('N')).tmp"
  try {
    [IO.File]::WriteAllText($tempPath, ($existing.TrimEnd() + [Environment]::NewLine + [Environment]::NewLine + ($blocks -join ([Environment]::NewLine + [Environment]::NewLine))), [Text.UTF8Encoding]::new($false))
    $issues = @(Get-AutopilotReadyIssues -Path $tempPath -RepoRoot $RepoRoot)
    foreach ($plannedId in $plannedIds) { if ($issues.issueId -notcontains $plannedId) { throw "planned block did not produce a strict Ready Issue: $plannedId" } }
    if ($issues.Count -gt 5) { throw 'refill would exceed five Ready Issues' }
    [IO.File]::Copy($tempPath, $ReadyPath, $true)
    return [pscustomobject]@{ createdCount = $blocks.Count; totalReadyCount = $issues.Count; issueIds = @($issues.issueId) }
  } finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
  }
}

function Invoke-AutopilotReadyPlanner {
  param([string]$RepoRoot, [object[]]$Candidates, [string]$OutputPath, [string]$SchemaPath, [string]$Model = 'gpt-5.6-sol', [string]$Thinking = 'high', [int]$TimeoutSeconds = 1200)
  $codex = Resolve-AutopilotCodexInvocation
  $candidateJson = $Candidates | ConvertTo-Json -Depth 5 -Compress
  $prompt = @"
Act as the fresh AutoPilot Planner for cgc-pms. Read AGENTS.override.md, AGENTS.md, docs/backlog/current-focus.md,
docs/backlog/ready-issues.md, docs/backlog/blocked-issues.md, docs/backlog/ad-hoc-plan.md,
docs/product-intelligence/project-map.md and docs/product-intelligence/evolution-decision.md. Do not scan the complete
current-issues.json registry to discover alternatives; discovery has already been bounded by the knowledge graph.
Turn only these knowledge-graph candidates into 1..5 strict Ready Issue Markdown blocks: $candidateJson
Before producing a block, verify that candidate against its sourceRefs, current branch code/configuration, and the unique
backlog carrier. Explicitly decide whether the issue still exists, user value is clear, acceptance is executable,
dependencies are satisfied, and it is not duplicated by Ready/Done/Blocked. If verification fails, do not create Ready.
Each block must satisfy scripts/codex-autopilot/autopilot-ready.ps1, use real existing paths and validation entrypoints,
state explicit non-goals/migration/risk/runtime/reviewer requirements, and must not modify business code.
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
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo; [void]$process.Start()
  $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync(); $process.StandardInput.Write($prompt); $process.StandardInput.Close()
  if (!$process.WaitForExit($TimeoutSeconds * 1000)) { $process.Kill($true); throw 'Ready Planner timed out' }
  $process.WaitForExit(); $null = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
  if ($process.ExitCode -ne 0) { throw "Ready Planner failed: $stderr" }
  return Get-Content -LiteralPath $OutputPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
