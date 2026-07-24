param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-refill.ps1')

$plannerSchema = Get-Content -LiteralPath (Join-Path $scriptDir '..\..\plugins\cgc-pms-autopilot\schemas\ready-plan.schema.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$decisionSchema = $plannerSchema.properties.candidateDecisions.items
if ($plannerSchema.properties.schemaVersion.type -ne 'integer' -or $decisionSchema.PSObject.Properties.Name -contains 'allOf') { throw 'Ready Planner schema is not compatible with strict structured output' }
foreach ($name in @('candidateRef','outcome','reason','readyIssueId','failureCategory')) {
  if ($name -notin @($decisionSchema.required)) { throw "Ready Planner schema must require nullable decision field: $name" }
}

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-refill-test-' + [guid]::NewGuid().ToString('N'))
$autoDir = Join-Path $root '.codex-autopilot'
$backlog = Join-Path $root 'docs\backlog'
New-Item -ItemType Directory -Path $autoDir,$backlog -Force | Out-Null
try {
  function Write-CurrentIssues([object[]]$Issues) {
    $script:GraphIssues = @($Issues)
    [ordered]@{
      schemaVersion = 1
      versionScope = 'v1.5'
      updatedAt = '2026-07-13T14:00:00+08:00'
      issues = $Issues
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $backlog 'current-issues.json') -Encoding UTF8
  }
  function Get-TestKnowledgeGraphSnapshot {
    return [pscustomobject]@{
      available = $true
      source = 'knowledge-graph'
      head = ('a' * 40)
      cursor = ('a' * 40)
      refreshed = $false
      issues = @($script:GraphIssues)
    }
  }
  function New-StockIssue([string]$Key,[string]$Title,[string]$Status,[string]$Classification,[string]$Priority,[bool]$Blocking,[string]$Parent = '',[object]$ReadySpec = $null) {
    return [ordered]@{
      issueKey = $Key; title = $Title; status = $Status; classification = $Classification
      priority = $Priority; blocking = $Blocking; parentIssueKey = if ($Parent) { $Parent } else { $null }
      summary = "Summary for $Key"; acceptanceCriteria = "Acceptance for $Key"; sourceRefs = @('docs/backlog/current-focus.md')
      readySpec = $ReadySpec
    }
  }
  function New-ReadySpec([string]$Id) {
    return [ordered]@{
      readyIssueId = $Id; taskNature = '缺口修复'; goal = @('Close the verified gap.'); nonGoals = @('No production deployment.')
      allowedPaths = @('docs/quality/**'); forbiddenPaths = @('deploy/**'); acceptanceCriteria = @('Evidence is recorded.')
      validationCommands = @('git diff --check'); archiveReport = "docs/quality/$($Id.ToLowerInvariant()).md"; migration = '不需要'
      dependencies = '无'; riskLevel = '低'; runtimeRequirement = '无'; reviewerRequirement = '不需要'
    }
  }

  '# Ready Issues' | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  @'
# Ad-hoc
| 临时待办 | 优先级 | 状态 |
| --- | --- | --- |
| Candidate A | 高 | ReadyToSplit |
| Candidate B | 高 | Candidate |
| Candidate C | 高 | Candidate |
'@ | Set-Content -LiteralPath (Join-Path $backlog 'ad-hoc-plan.md') -Encoding UTF8
  '# Plan' | Set-Content -LiteralPath (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md') -Encoding UTF8
  '当前 focus：Candidate A' | Set-Content -LiteralPath (Join-Path $backlog 'current-focus.md') -Encoding UTF8
  '# Blocked Issues' | Set-Content -LiteralPath (Join-Path $backlog 'blocked-issues.md') -Encoding UTF8
  Write-CurrentIssues @(
    (New-StockIssue 'A-01' 'Aggregate parent' 'OPEN' 'STILL_APPLICABLE' 'P0' $false),
    (New-StockIssue 'A-01-LEAF' 'Stock leaf' 'OPEN' 'STILL_APPLICABLE' 'P1' $false 'A-01'),
    (New-StockIssue 'REL-GATE' 'Production release gate' 'RELEASE_GATE' 'RELEASE_PREREQUISITE' 'P0' $true),
    (New-StockIssue 'NEEDS-HUMAN' 'Needs human confirmation' 'NEEDS_CONFIRMATION' 'NEEDS_CONFIRMATION' 'P0' $false),
    (New-StockIssue 'OBS-LOCAL' 'Local observation' 'OBSERVATION' 'NON_BLOCKING_OBSERVATION' 'P2' $false)
  )

  $decision = Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)
  if ($decision.action -ne 'PLAN_READY' -or $decision.targetReadyCount -ne 1 -or $decision.candidates.Count -ne 1) { throw 'refill must select exactly one eligible stock issue' }
  if ($decision.candidates[0].issueKey -ne 'A-01-LEAF' -or $decision.candidates[0].source -ne 'knowledge-graph') { throw 'knowledge graph issue priority or aggregate/release/confirmation filtering failed' }
  if ($decision.candidates[0].marker -ne '[stock:A-01-LEAF]') { throw 'stock issue marker was not preserved for deduplication' }

  Write-CurrentIssues @((New-StockIssue 'FAST-01' 'Fast stock leaf' 'OPEN' 'STILL_APPLICABLE' 'P0' $false '' (New-ReadySpec 'ISSUE-901-010')))
  $fastDecision = Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)
  if ($fastDecision.action -ne 'GENERATE_READY' -or $fastDecision.candidates[0].candidateEvidenceHead -ne ('a' * 40)) { throw 'authoritative ReadySpec did not select deterministic fast path' }
  $fastPlanA = New-AutopilotDeterministicReadyPlan -Candidate $fastDecision.candidates[0] -RepoRoot $root
  $fastPlanB = New-AutopilotDeterministicReadyPlan -Candidate $fastDecision.candidates[0] -RepoRoot $root
  if (($fastPlanA | ConvertTo-Json -Depth 8 -Compress) -cne ($fastPlanB | ConvertTo-Json -Depth 8 -Compress)) { throw 'deterministic Ready generation was not byte stable' }
  if ($fastPlanA.readyBlocks[0] -notmatch 'candidateEvidenceHead=' + ('a' * 40)) { throw 'candidate evidence head was not bound into generated Ready' }
  $fastDurations = @(1..10 | ForEach-Object {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $iterationPlan = New-AutopilotDeterministicReadyPlan -Candidate $fastDecision.candidates[0] -RepoRoot $root
    $watch.Stop()
    if (($iterationPlan | ConvertTo-Json -Depth 8 -Compress) -cne ($fastPlanA | ConvertTo-Json -Depth 8 -Compress)) { throw 'repeated deterministic Ready generation drifted' }
    $watch.Elapsed.TotalSeconds
  } | Sort-Object)
  $medianFastSeconds = ($fastDurations[4] + $fastDurations[5]) / 2
  if ($medianFastSeconds -gt 10 -or $fastDurations[-1] -gt 30) { throw "deterministic refill performance budget exceeded: median=$medianFastSeconds max=$($fastDurations[-1])" }

  Write-CurrentIssues @(
    (New-StockIssue 'REL-GATE' 'Production release gate' 'RELEASE_GATE' 'RELEASE_PREREQUISITE' 'P0' $true),
    (New-StockIssue 'NEEDS-HUMAN' 'Needs human confirmation' 'NEEDS_CONFIRMATION' 'NEEDS_CONFIRMATION' 'P0' $false)
  )
  $adHocDecision = Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)
  if ($adHocDecision.action -ne 'PLAN_READY' -or $adHocDecision.candidates[0].name -ne 'Candidate A' -or $adHocDecision.candidates[0].source -ne 'ad-hoc-plan.md') { throw 'ad-hoc candidate was not used after eligible stock issues were exhausted' }

  '# Ad-hoc' | Set-Content -LiteralPath (Join-Path $backlog 'ad-hoc-plan.md') -Encoding UTF8
  "# Plan`n### 2.1 当前技术栈`n### 8.1 报表中心" | Set-Content -LiteralPath (Join-Path $backlog 'cgc-pms-production-enhancement-plan.md') -Encoding UTF8
  $longTermDecision = Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)
  if ($longTermDecision.action -ne 'NO_CANDIDATES' -or $longTermDecision.candidates.Count -ne 0 -or $longTermDecision.reason -notmatch 'refresh product intelligence') { throw 'long-term plan was still allowed to bypass stock/ad-hoc evidence gates' }

  'stop' | Set-Content -LiteralPath (Join-Path $autoDir 'stop.flag') -Encoding UTF8
  if ((Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)).action -ne 'STOP') { throw 'stop flag did not stop refill' }
  Remove-Item -LiteralPath (Join-Path $autoDir 'stop.flag')

  'pause' | Set-Content -LiteralPath (Join-Path $autoDir 'pause.flag') -Encoding UTF8
  if ((Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)).action -ne 'PAUSE') { throw 'pause flag did not stop refill' }
  Remove-Item -LiteralPath (Join-Path $autoDir 'pause.flag')

  "# Blocked Issues`n### ISSUE-1：Candidate A prerequisite`n状态：Blocked" | Set-Content -LiteralPath (Join-Path $backlog 'blocked-issues.md') -Encoding UTF8
  $unblockDecision = Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)
  if ($unblockDecision.action -ne 'UNBLOCK_FIRST') { throw 'current focus blocker was not prioritized' }
  if ($unblockDecision.targetReadyCount -ne 1) { throw 'blocked prerequisite still forced a three-item target' }
  if (Test-AutopilotReadyPlanningAllowed -Action 'UNBLOCK_FIRST') { throw 'blocked prerequisite was allowed into Ready Planner' }
  if (!(Test-AutopilotReadyPlanningAllowed -Action 'PLAN_READY')) { throw 'normal candidate refill was rejected' }
  if (!(Test-AutopilotReadyPlanningAllowed -Action 'GENERATE_READY')) { throw 'deterministic candidate refill was rejected' }

  $titles = @('报表中心 A','报表中心 B','报表中心 C')
  if (Test-AutopilotDomainContinuationAllowed -RecentTitles $titles -Domain '报表中心' -FocusText '继续做报表') { throw 'three-item domain streak skipped candidate comparison' }
  if (!(Test-AutopilotDomainContinuationAllowed -RecentTitles $titles -Domain '报表中心' -FocusText '候选域对比：报表更优')) { throw 'documented three-item comparison was rejected' }

  '# Blocked Issues' | Set-Content -LiteralPath (Join-Path $backlog 'blocked-issues.md') -Encoding UTF8
  function New-PlannedBlock([string]$Id) {
    $tick = [char]96
    return @"
### ${Id}：Planned candidate
任务性质：回归证明
目标：
- Prove existing behavior.
非目标：
- No production change.
允许修改：
- ${tick}docs/quality/**${tick}
禁止修改：
- ${tick}deploy/**${tick}
验收标准：
- Evidence is recorded.
状态：Ready
来源锚点：${tick}docs/backlog/ad-hoc-plan.md${tick}
验证命令：
- ${tick}git diff --check${tick}
归档报告：${tick}docs/quality/$($Id.ToLowerInvariant()).md${tick}
Migration：不需要
依赖：无
风险等级：低
运行态要求：无
Reviewer要求：不需要
"@
  }
  (New-PlannedBlock 'ISSUE-901-000') | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  $sufficientDecision = Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot (Get-TestKnowledgeGraphSnapshot)
  if ($sufficientDecision.action -ne 'READY_SUFFICIENT' -or $sufficientDecision.targetReadyCount -ne 1) { throw 'one valid Ready issue was not treated as sufficient' }

  '# Ready Issues' | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8
  $unavailable = [pscustomobject]@{ available = $false; stopReason = 'STOP_KG_REFILL_UNAVAILABLE'; failureCategory = 'environment_prerequisite'; message = 'neo4j unavailable'; issues = @() }
  $unavailableDecision = Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot $unavailable
  if ($unavailableDecision.action -ne 'STOP_KG_REFILL_UNAVAILABLE' -or $unavailableDecision.failureCategory -ne 'environment_prerequisite') { throw 'unavailable graph did not stop refill safely' }
  $stale = [pscustomobject]@{ available = $false; stopReason = 'STOP_KG_REFILL_STALE'; failureCategory = 'quality_or_security'; message = 'git cursor stale after refresh'; issues = @() }
  if ((Get-AutopilotRefillDecision -RepoRoot $root -KnowledgeGraphSnapshot $stale).action -ne 'STOP_KG_REFILL_STALE') { throw 'stale graph did not stop refill safely' }

  $configDir = Join-Path $root 'scripts\codex-autopilot'
  New-Item -ItemType Directory -Path $configDir -Force | Out-Null
  '{"issueGraph":{"enabled":true,"cli":"mock-kg.js","refreshWhenHeadDiffers":true,"allowRegistryFallback":false,"queryLimit":200}}' |
    Set-Content -LiteralPath (Join-Path $configDir 'codex-autopilot.config.json') -Encoding UTF8
  & git -C $root init -q 2>$null
  & git -C $root config user.email 'kg-refill@test.local'
  & git -C $root config user.name 'KG Refill Test'
  & git -C $root config core.autocrlf false
  & git -C $root config core.eol lf
  & git -C $root add .
  & git -C $root commit -qm 'fixture'
  $testHead = (& git -C $root rev-parse HEAD).Trim()
  $script:KgCalls = 0
  $refreshingInvoker = {
    param($CliPath,$Arguments,$RepoRoot)
    $script:KgCalls++
    $command = $Arguments[0]
    if ($command -eq 'collect') { return [pscustomobject]@{ status = 'SUCCESS' } }
    if ($command -eq 'issues') { return [pscustomobject]@{ total = 0; issues = @() } }
    $cursor = if ($script:KgCalls -eq 1) { 'stale-head' } else { $testHead }
    return [pscustomobject]@{ lastRunStatus = 'SUCCEEDED'; lastRunFailures = 0; currentIssues = 0; cursors = @([pscustomobject]@{ source = 'git'; cursor = $cursor }) }
  }
  $refreshedSnapshot = Get-AutopilotKnowledgeGraphIssueSnapshot -RepoRoot $root -CommandInvoker $refreshingInvoker
  if (!$refreshedSnapshot.available -or !$refreshedSnapshot.refreshed -or $script:KgCalls -ne 4) { throw 'stale graph was not refreshed exactly once before issue query' }

  $alwaysStaleInvoker = {
    param($CliPath,$Arguments,$RepoRoot)
    if ($Arguments[0] -eq 'collect') { return [pscustomobject]@{ status = 'SUCCESS' } }
    if ($Arguments[0] -eq 'issues') { return [pscustomobject]@{ total = 0; issues = @() } }
    return [pscustomobject]@{ lastRunStatus = 'SUCCESS'; lastRunFailures = 0; currentIssues = 0; cursors = @([pscustomobject]@{ source = 'git'; cursor = 'still-stale' }) }
  }
  $staleAfterRefresh = Get-AutopilotKnowledgeGraphIssueSnapshot -RepoRoot $root -CommandInvoker $alwaysStaleInvoker
  if ($staleAfterRefresh.stopReason -ne 'STOP_KG_REFILL_STALE' -or $staleAfterRefresh.failureCategory -ne 'quality_or_security') { throw 'post-refresh cursor mismatch was not classified as data consistency failure' }

  $unavailableInvoker = { param($CliPath,$Arguments,$RepoRoot) throw 'ECONNREFUSED localhost:7687' }
  $environmentStop = Get-AutopilotKnowledgeGraphIssueSnapshot -RepoRoot $root -CommandInvoker $unavailableInvoker
  if ($environmentStop.stopReason -ne 'STOP_KG_REFILL_UNAVAILABLE' -or $environmentStop.failureCategory -ne 'environment_prerequisite') { throw 'Neo4j outage was not classified as environment prerequisite' }

  $planPath = Join-Path $root 'ready-plan.json'
  [ordered]@{ schemaVersion = 2; candidateDecisions = @(
      [ordered]@{ candidateRef='candidate-1'; outcome='CREATED'; reason='valid'; readyIssueId='ISSUE-901-001' },
      [ordered]@{ candidateRef='candidate-2'; outcome='CREATED'; reason='valid'; readyIssueId='ISSUE-901-002' },
      [ordered]@{ candidateRef='candidate-3'; outcome='CREATED'; reason='valid'; readyIssueId='ISSUE-901-003' }
    ); readyBlocks = @((New-PlannedBlock 'ISSUE-901-001'),(New-PlannedBlock 'ISSUE-901-002'),(New-PlannedBlock 'ISSUE-901-003')) } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $planPath -Encoding UTF8
  $imported = Import-AutopilotReadyPlan -PlanPath $planPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root -ExpectedCandidateRefs @('candidate-1','candidate-2','candidate-3')
  if ($imported.createdCount -ne 3) { throw 'validated planner output was not imported' }
  $invalidPlanPath = Join-Path $root 'invalid-ready-plan.json'
  [ordered]@{ schemaVersion=2; candidateDecisions=@([ordered]@{candidateRef='candidate-4';outcome='CREATED';reason='invalid block';readyIssueId='ISSUE-901-004'}); readyBlocks = @((New-PlannedBlock 'ISSUE-901-004').Replace('状态：Ready','状态：Blocked')) } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $invalidPlanPath -Encoding UTF8
  $blockedRejected = $false
  try { Import-AutopilotReadyPlan -PlanPath $invalidPlanPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root | Out-Null } catch { $blockedRejected = $true }
  if (!$blockedRejected) { throw 'Planner Blocked block was imported into Ready backlog' }
  [ordered]@{ schemaVersion=2; candidateDecisions=@([ordered]@{candidateRef='candidate-5';outcome='CREATED';reason='mixed block';readyIssueId='ISSUE-901-005'}); readyBlocks = @((New-PlannedBlock 'ISSUE-901-005') + "`r`n" + (New-PlannedBlock 'ISSUE-901-006').Replace('状态：Ready','状态：Blocked')) } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $invalidPlanPath -Encoding UTF8
  $mixedRejected = $false
  try { Import-AutopilotReadyPlan -PlanPath $invalidPlanPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root | Out-Null } catch { $mixedRejected = $true }
  if (!$mixedRejected) { throw 'mixed multi-Issue Planner block was imported' }

  [ordered]@{ schemaVersion=2; candidateDecisions=@([ordered]@{candidateRef='candidate-r';outcome='REJECTED';reason='duplicate'}); readyBlocks=@() } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $invalidPlanPath -Encoding UTF8
  $rejected = Import-AutopilotReadyPlan -PlanPath $invalidPlanPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root -ExpectedCandidateRefs @('candidate-r')
  if ($rejected.createdCount -ne 0) { throw 'REJECTED-only planner result created Ready content' }
  [ordered]@{ schemaVersion=2; candidateDecisions=@([ordered]@{candidateRef='candidate-b';outcome='BLOCKED';reason='runtime unavailable';failureCategory='environment_prerequisite'}); readyBlocks=@() } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $invalidPlanPath -Encoding UTF8
  $blocked = Import-AutopilotReadyPlan -PlanPath $invalidPlanPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root -ExpectedCandidateRefs @('candidate-b')
  if ($blocked.createdCount -ne 0) { throw 'BLOCKED-only planner result created Ready content' }
  if ((Get-AutopilotRefillStageFailureCategory -CandidateDecisions $blocked.candidateDecisions) -ne 'environment_prerequisite') { throw 'blocked Planner category was not preserved for StageResult' }
  if ((Get-AutopilotRefillStageFailureCategory -CandidateDecisions @([pscustomobject]@{outcome='BLOCKED';failureCategory='needs_confirmation'})) -ne 'ready_issue_config') { throw 'needs_confirmation was not mapped into the stable StageResult category set' }
  $candidateMismatchRejected = $false
  try { Import-AutopilotReadyPlan -PlanPath $invalidPlanPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root -ExpectedCandidateRefs @('different-candidate') | Out-Null } catch { $candidateMismatchRejected = $true }
  if (!$candidateMismatchRejected) { throw 'planner decision escaped the bounded candidate set' }

  Write-Host 'refill self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
