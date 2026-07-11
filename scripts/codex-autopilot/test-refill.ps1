param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-refill.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-refill-test-' + [guid]::NewGuid().ToString('N'))
$autoDir = Join-Path $root '.codex-autopilot'
$backlog = Join-Path $root 'docs\backlog'
New-Item -ItemType Directory -Path $autoDir,$backlog -Force | Out-Null
try {
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

  $decision = Get-AutopilotRefillDecision -RepoRoot $root
  if ($decision.action -ne 'PLAN_READY' -or $decision.targetReadyCount -ne 1 -or $decision.candidates.Count -ne 1) { throw 'refill must select only the highest-priority candidate' }
  if ($decision.candidates[0].name -ne 'Candidate A') { throw 'refill did not keep candidate priority' }

  'pause' | Set-Content -LiteralPath (Join-Path $autoDir 'pause.flag') -Encoding UTF8
  if ((Get-AutopilotRefillDecision -RepoRoot $root).action -ne 'PAUSE') { throw 'pause flag did not stop refill' }
  Remove-Item -LiteralPath (Join-Path $autoDir 'pause.flag')

  "# Blocked Issues`n### ISSUE-1：Candidate A prerequisite`n状态：Blocked" | Set-Content -LiteralPath (Join-Path $backlog 'blocked-issues.md') -Encoding UTF8
  $unblockDecision = Get-AutopilotRefillDecision -RepoRoot $root
  if ($unblockDecision.action -ne 'UNBLOCK_FIRST') { throw 'current focus blocker was not prioritized' }
  if ($unblockDecision.targetReadyCount -ne 1) { throw 'blocked prerequisite still forced a three-item target' }
  if (Test-AutopilotReadyPlanningAllowed -Action 'UNBLOCK_FIRST') { throw 'blocked prerequisite was allowed into Ready Planner' }
  if (!(Test-AutopilotReadyPlanningAllowed -Action 'PLAN_READY')) { throw 'normal candidate refill was rejected' }

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
  $sufficientDecision = Get-AutopilotRefillDecision -RepoRoot $root
  if ($sufficientDecision.action -ne 'READY_SUFFICIENT' -or $sufficientDecision.targetReadyCount -ne 1) { throw 'one valid Ready issue was not treated as sufficient' }
  '# Ready Issues' | Set-Content -LiteralPath (Join-Path $backlog 'ready-issues.md') -Encoding UTF8

  $planPath = Join-Path $root 'ready-plan.json'
  [ordered]@{ readyBlocks = @((New-PlannedBlock 'ISSUE-901-001'),(New-PlannedBlock 'ISSUE-901-002'),(New-PlannedBlock 'ISSUE-901-003')) } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $planPath -Encoding UTF8
  $imported = Import-AutopilotReadyPlan -PlanPath $planPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root
  if ($imported.createdCount -ne 3) { throw 'validated planner output was not imported' }
  $invalidPlanPath = Join-Path $root 'invalid-ready-plan.json'
  [ordered]@{ readyBlocks = @((New-PlannedBlock 'ISSUE-901-004').Replace('状态：Ready','状态：Blocked')) } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $invalidPlanPath -Encoding UTF8
  $blockedRejected = $false
  try { Import-AutopilotReadyPlan -PlanPath $invalidPlanPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root | Out-Null } catch { $blockedRejected = $true }
  if (!$blockedRejected) { throw 'Planner Blocked block was imported into Ready backlog' }
  [ordered]@{ readyBlocks = @((New-PlannedBlock 'ISSUE-901-005') + "`r`n" + (New-PlannedBlock 'ISSUE-901-006').Replace('状态：Ready','状态：Blocked')) } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $invalidPlanPath -Encoding UTF8
  $mixedRejected = $false
  try { Import-AutopilotReadyPlan -PlanPath $invalidPlanPath -ReadyPath (Join-Path $backlog 'ready-issues.md') -RepoRoot $root | Out-Null } catch { $mixedRejected = $true }
  if (!$mixedRejected) { throw 'mixed multi-Issue Planner block was imported' }

  Write-Host 'refill self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
