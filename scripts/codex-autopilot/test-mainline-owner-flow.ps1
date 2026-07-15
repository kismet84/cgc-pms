[CmdletBinding()]
param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [string]$PlanPath = '',
  [ValidateSet('Light','Standard','HighRisk')][string]$Profile = 'Standard'
)

$ErrorActionPreference = 'Stop'
function Get-PlanProfilePatterns([string]$Name) {
  $patterns = @('\*\*Goal:\*\*','\*\*Architecture:\*\*','范围','非目标','验收','风险','回滚','计划状态')
  if ($Name -in @('Standard','HighRisk')) { $patterns += @('阶段','失败分类') }
  if ($Name -eq 'HighRisk') { $patterns += @('恢复','金丝雀') }
  return $patterns
}
function Get-MissingPlanProfilePatterns([string]$Text,[string]$Name) {
  return @(Get-PlanProfilePatterns $Name | Where-Object { $Text -notmatch $_ })
}
$skillPath = Join-Path $RepoRoot '.agents\skills\cgc-pms-mainline-owner-flow\SKILL.md'
$autopilotOwnerSkillPath = Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\skills\cgc-pms-autopilot-owner\SKILL.md'
$policyPath = Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\references\control-plane-policy.md'
$taskPolicyPath = Join-Path $RepoRoot 'docs\standards\codex-task-execution-policy.md'
$configPath = Join-Path $RepoRoot 'scripts\codex-autopilot\codex-autopilot.config.json'
foreach ($path in @($skillPath,$autopilotOwnerSkillPath,$policyPath,$taskPolicyPath,$configPath)) {
  if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "mainline owner flow reference is missing: $path" }
}

$skill = Get-Content -LiteralPath $skillPath -Raw -Encoding UTF8
if ($skill -notmatch '(?m)^description:.*explicitly requests.*mainline.*Backlog.*AutoPilot') { throw 'mainline owner skill trigger is not explicitly scoped' }
if ($skill -match 'autopilot-task-score/v\d|\b35/25/20/10/10\b|达到\s*20\s*个有效任务|timeoutSeconds\s*[=:]') { throw 'mainline owner skill copied dynamic AutoPilot facts' }
foreach ($reference in @('AGENTS.override.md','AGENTS.md','docs/standards/codex-task-execution-policy.md','scripts/codex-autopilot/codex-autopilot.config.json','plugins/cgc-pms-autopilot/references/control-plane-policy.md')) {
  if ($skill -notmatch [regex]::Escape($reference)) { throw "mainline owner skill does not reference $reference" }
}
$taskPolicy = Get-Content -LiteralPath $taskPolicyPath -Raw -Encoding UTF8
foreach ($pattern in @('状态机与粘性模式','确定性工具路由','失败分类','浏览器执行模板','分层验证与证据复用','Git 生命周期','事件驱动沟通','任务恢复胶囊')) {
  if ($taskPolicy -notmatch [regex]::Escape($pattern)) { throw "shared task policy is missing required section: $pattern" }
}
if ($taskPolicy -match 'autopilot-task-score/v\d|\b35/25/20/10/10\b|timeoutSeconds\s*[=:]') { throw 'shared task policy copied dynamic AutoPilot facts' }
$autopilotOwnerSkill = Get-Content -LiteralPath $autopilotOwnerSkillPath -Raw -Encoding UTF8
foreach ($pattern in @('非 PowerShell 源码才允许回退 CodeGraph 与 `rg`','PowerShell 检索必须改用 `rg` 与直接读取','禁止使用 CodeGraph')) {
  if ($autopilotOwnerSkill -notmatch [regex]::Escape($pattern)) { throw "AutoPilot owner skill is missing safe PowerShell fallback: $pattern" }
}
foreach ($pattern in @('scripts/codex-autopilot/codex-autopilot.config.json','issueExecutor','runtimeRefresh.waitSeconds','maxParallel','maxParallelIssues','parallelSafetyMode')) {
  if ($autopilotOwnerSkill -notmatch [regex]::Escape($pattern)) { throw "AutoPilot owner skill is missing dynamic config reference: $pattern" }
}
if ($autopilotOwnerSkill -match '默认\s*45\s*分钟|5\s*分钟检查停滞|10\s*分钟终止|稳定等待\s*180\s*秒|每轮最多并行\s*3') {
  throw 'AutoPilot owner skill copied dynamic timeout, wait, or parallel values'
}

if ($PlanPath) {
  $resolvedPlan = if ([IO.Path]::IsPathRooted($PlanPath)) { $PlanPath } else { Join-Path $RepoRoot $PlanPath }
  if (!(Test-Path -LiteralPath $resolvedPlan -PathType Leaf)) { throw "plan file is missing: $resolvedPlan" }
  $name = Split-Path -Leaf $resolvedPlan
  if ($name -notmatch '^第\d+(?:[-.]\d+)?条主线(?:-M\d+)?-.+任务计划书(?:-\d{4}-\d{2}-\d{2})?\.md$') { throw "plan filename does not match the mainline convention: $name" }
  if ($name -match '^第(\d+)条主线-(?!M\d+-)') {
    $number = $matches[1]
    $duplicates = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'docs\plans') -File | Where-Object { $_.Name -match "^第${number}条主线-(?!M\d+-)" })
    if ($duplicates.Count -gt 1) { throw "duplicate primary mainline number: $number" }
  }
  $text = Get-Content -LiteralPath $resolvedPlan -Raw -Encoding UTF8
  $missingPatterns = @(Get-MissingPlanProfilePatterns -Text $text -Name $Profile)
  if ($missingPatterns.Count -gt 0) { throw "plan profile $Profile is missing required content: $($missingPatterns -join ', ')" }
  if ($text -match '(?im)^.*(?:run id|临时日志路径|截图名)\s*[=:：].+$') { throw 'plan contains session-only temporary artifact identifiers' }
}

$profileFixture = '**Goal:** goal **Architecture:** architecture 范围 非目标 验收 风险 回滚 计划状态 阶段 失败分类 恢复 金丝雀'
foreach ($fixtureProfile in @('Light','Standard','HighRisk')) {
  if (@(Get-MissingPlanProfilePatterns -Text $profileFixture -Name $fixtureProfile).Count -ne 0) { throw "positive $fixtureProfile plan fixture was rejected" }
  $negativeFixture = $profileFixture.Replace('**Goal:**','')
  if (@(Get-MissingPlanProfilePatterns -Text $negativeFixture -Name $fixtureProfile).Count -eq 0) { throw "negative $fixtureProfile plan fixture was accepted" }
}

[pscustomobject]@{ ok=$true; profile=$Profile; skill=$skillPath; policy=$policyPath; plan=$PlanPath } | ConvertTo-Json -Depth 3
