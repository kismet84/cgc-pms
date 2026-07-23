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
foreach ($reference in @('../cgc-pms-ci-gate-triage/SKILL.md','../cgc-pms-runtime-refresh/SKILL.md','git-publish-and-cleanup','plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md')) {
  if ($skill -notmatch [regex]::Escape($reference)) { throw "mainline owner skill does not reference $reference" }
}
if ($skill -match 'AGENTS\.override\.md|(?:^|[/`])AGENTS\.md|docs/standards/codex-task-execution-policy\.md') { throw 'mainline owner skill requires rereading a root rule' }
$taskPolicy = Get-Content -LiteralPath $taskPolicyPath -Raw -Encoding UTF8
foreach ($pattern in @('任务路由','普通任务不读取本索引','运行态与 CI 各只读取对应 Skill','非 AutoPilot 任务不得读取 checkpoint','Skill 不重新读取根规则')) {
  if ($taskPolicy -notmatch [regex]::Escape($pattern)) { throw "shared task policy is missing required section: $pattern" }
}
if ($taskPolicy -match 'autopilot-task-score/v\d|\b35/25/20/10/10\b|timeoutSeconds\s*[=:]') { throw 'shared task policy copied dynamic AutoPilot facts' }
$autopilotOwnerSkill = Get-Content -LiteralPath $autopilotOwnerSkillPath -Raw -Encoding UTF8
foreach ($pattern in @('触发协议','不可绕过边界','唯一事实入口','scripts/codex-autopilot/codex-autopilot.config.json','control-plane-policy.md','role-contracts.md','classifier-rules.md')) {
  if ($autopilotOwnerSkill -notmatch [regex]::Escape($pattern)) { throw "AutoPilot owner skill is missing authority routing: $pattern" }
}
if (@($autopilotOwnerSkill -split "\r?\n").Count -gt 70) { throw 'AutoPilot owner skill is not a short entrypoint' }
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
