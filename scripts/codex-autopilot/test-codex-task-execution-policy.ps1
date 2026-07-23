[CmdletBinding()]
param([string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)

$ErrorActionPreference = 'Stop'

function Read-RepoText([string]$RelativePath) {
  $path = Join-Path $RepoRoot $RelativePath
  if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "required execution-policy artifact is missing: $RelativePath" }
  return Get-Content -LiteralPath $path -Raw -Encoding UTF8
}

function Assert-Contains([string]$Name,[string]$Text,[string[]]$Patterns) {
  foreach ($pattern in $Patterns) {
    if ($Text -notmatch [regex]::Escape($pattern)) { throw "$Name is missing required contract text: $pattern" }
  }
}

$agents = Read-RepoText 'AGENTS.md'
$policy = Read-RepoText 'docs\standards\codex-task-execution-policy.md'
$runtime = Read-RepoText '.agents\skills\cgc-pms-runtime-refresh\SKILL.md'
$ci = Read-RepoText '.agents\skills\cgc-pms-ci-gate-triage\SKILL.md'
$mainline = Read-RepoText '.agents\skills\cgc-pms-mainline-owner-flow\SKILL.md'
$release = Read-RepoText '.agents\skills\release-skills\SKILL.md'
$owner = Read-RepoText 'plugins\cgc-pms-autopilot\skills\cgc-pms-autopilot-owner\SKILL.md'
$controlPlane = Read-RepoText 'plugins\cgc-pms-autopilot\references\control-plane-policy.md'
$classifier = Read-RepoText 'plugins\cgc-pms-autopilot\references\classifier-rules.md'
$classificationSchema = Read-RepoText 'plugins\cgc-pms-autopilot\schemas\classification-result.schema.json'
$prePrGate = Read-RepoText 'scripts\codex-autopilot\verify-pre-pr-ci.ps1'
$ciWorkflow = Read-RepoText '.github\workflows\ci.yml'
$config = Read-RepoText 'scripts\codex-autopilot\codex-autopilot.config.json' | ConvertFrom-Json

if (Test-Path -LiteralPath (Join-Path $RepoRoot 'AGENTS.override.md')) { throw 'AGENTS.override.md must not remain as a second root rule' }
$agentLineCount = @($agents -split "\r?\n").Count
if ($agentLineCount -gt 70) { throw "AGENTS.md exceeds 70 lines: $agentLineCount" }
Assert-Contains 'AGENTS.md' $agents @(
  '所有回答使用中文','未获明确授权','git branch --show-current','git status --short',
  '保留既有脏改动','禁止自动发布生产','最小相关验证','Git','零悬空收口',
  '启动迭代-1','普通任务无需显式重读本文件'
)

Assert-Contains 'route index' $policy @(
  '普通代码、文档、审查和解释任务','显式规则读取为 0','任务路由',
  '运行态与 CI 各只读取对应 Skill','非 AutoPilot 任务不得读取 checkpoint',
  'Skill 不重新读取根规则'
)
if (@($policy -split "\r?\n").Count -gt 50) { throw 'route index expanded into a second general policy body' }

foreach ($entry in @(
  @{name='runtime skill';text=$runtime},
  @{name='CI skill';text=$ci},
  @{name='mainline skill';text=$mainline},
  @{name='release skill';text=$release}
)) {
  if ($entry.text -match 'AGENTS\.override\.md|(?:^|[/`])AGENTS\.md|docs/standards/codex-task-execution-policy\.md') {
    throw "$($entry.name) requires rereading an automatically loaded or shared root rule"
  }
}
Assert-Contains 'runtime skill' $runtime @('actuator/health','dev-login','Vite','浏览器')
Assert-Contains 'mainline skill' $mainline @('**Goal:**','**Architecture:**','正式验收与零悬空','普通主线不读取 AutoPilot')

$canonicalCategories = @('tool_config','tool_invocation','environment_prerequisite','ready_issue_config','retrieval_gap','quality_or_security','unknown')
Assert-Contains 'CI skill categories' $ci $canonicalCategories
$retiredCategories = @(('environment_' + 'prereq'),('real_' + 'quality_or_security'),('quality_' + 'security'))
foreach ($retired in $retiredCategories) {
  if ($ci -match "(?<![a-z_])$([regex]::Escape($retired))(?![a-z_])") { throw "CI skill contains retired failure category: $retired" }
}
Assert-Contains 'classifier authority' $classifier @('一级分类名称唯一引用','.agents/skills/cgc-pms-ci-gate-triage/SKILL.md','tool_invocation','retrieval_gap')
Assert-Contains 'classification schema' $classificationSchema $canonicalCategories

Assert-Contains 'release trigger' $release @('版本发布','升版本','Tag','GitHub Release','不使用')
$triggerSection = [regex]::Match($release,'(?s)## 触发边界(?<body>.*?)(?:\r?\n## |\z)').Groups['body'].Value
if (!$triggerSection) { throw 'release skill lacks a bounded trigger section' }
if ($triggerSection -match '(?m)^\s*[-*]\s*`?(?:push|推送)`?\s*(?:[。；;]|$)') { throw 'standalone push still triggers release skill' }
foreach ($path in @(
  '.agents/skills/release-skills/references/01-detect-project.md',
  '.agents/skills/release-skills/references/02-analyze-and-version.md',
  '.agents/skills/release-skills/references/03-changelog.md',
  '.agents/skills/release-skills/references/04-module-commits.md',
  '.agents/skills/release-skills/references/05-prepare-and-confirm.md',
  '.agents/skills/release-skills/references/06-tag-and-publish.md',
  '.agents/skills/release-skills/references/07-backfill-github-releases.md'
)) {
  if (!(Test-Path -LiteralPath (Join-Path $RepoRoot $path) -PathType Leaf)) { throw "release reference missing: $path" }
}

if (@($owner -split "\r?\n").Count -gt 70) { throw 'AutoPilot owner is not a short trigger and authority entry' }
Assert-Contains 'AutoPilot owner' $owner @('普通任务不读取本 Skill','触发协议','不可绕过边界','唯一事实入口','启动迭代-1')
Assert-Contains 'control-plane policy' $controlPlane @('Ready 为空时','知识图谱健康','有界查询','图谱异常','控制面指纹')
if (Test-Path -LiteralPath (Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\references\failure-classification.md')) {
  throw 'duplicate AutoPilot failure-classification authority still exists'
}

Assert-Contains 'pre-PR gate' $prePrGate @('headBranch','TRACKED_WORKTREE_DIRTY','event','push','PRE_PR_CI_EVIDENCE_MISSING','build-summary','frontend-v2-gate','supply-chain-security','e2e')
Assert-Contains 'CI workflow' $ciWorkflow @('branches-ignore: [master, main]','workflow_dispatch:','Verify MySQL migration user scope','frontend-v2-gate','supply-chain-security','e2e')

if ([string]$config.baseBranch -ne 'master') { throw 'AutoPilot baseBranch is not aligned with repository policy' }
$fingerprints = @($config.controlPlaneCanary.fingerprintPaths)
if ($fingerprints -contains 'AGENTS.override.md') { throw 'control-plane fingerprint still references removed AGENTS.override.md' }
foreach ($path in @(
  'AGENTS.md',
  'plugins/cgc-pms-autopilot/references/classifier-rules.md',
  'plugins/cgc-pms-autopilot/schemas/classification-result.schema.json',
  'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md',
  '.agents/skills/cgc-pms-ci-gate-triage/SKILL.md'
)) {
  if ($fingerprints -notcontains $path) { throw "control-plane fingerprint missing behavior path: $path" }
}

[pscustomobject]@{
  ok = $true
  rootRuleLines = $agentLineCount
  canonicalFailureCategories = $canonicalCategories
  ordinaryExplicitRuleReads = 0
} | ConvertTo-Json -Depth 4
