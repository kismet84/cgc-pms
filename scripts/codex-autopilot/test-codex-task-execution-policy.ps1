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
Assert-Contains 'PowerShell ripgrep invocation' $ci @(
  'PowerShell 中禁止使用 Bash/C 风格的反斜杠转义双引号',
  '包含双引号的检索表达式必须使用 PowerShell 单引号字面量',
  '精确文本检索使用 `rg -F`',
  '每个目标通过独立 `-e ''literal''` 传入',
  '构建/测试与证据检索分开执行',
  '`regex parse error`',
  '归类 `tool_invocation`',
  '只做一次最小复验',
  '核对退出码与命中结果'
)
Assert-Contains 'backend full test monitoring' $ci @(
  '最近 10 次成功 GitHub Actions `backend-test`',
  '`Run backend tests with coverage`',
  '`startedAt`、`completedAt`',
  '平均 `344.8` 秒',
  '中位数 `5分52秒`',
  '范围 `4分19秒～6分15秒`',
  '禁止固定 60 秒心跳',
  '“仍在执行”“未见失败”',
  'Maven/Java 子进程、CPU、最新 Surefire 报告时间和终端新增输出',
  'max(平均时长 × 1.5, 最近最大值 + 120秒)',
  '当前约 `8分37秒`',
  '先分类为 `unknown`',
  'Maven `BUILD SUCCESS`/`BUILD FAILURE`',
  '不能只凭进程退出码裁决',
  '用户可见更新仅限：启动、阶段变化、明确失败、超过动态异常阈值、最终完成',
  '不得因等待时间触发重跑、取消、修改 CI 或终止 Maven'
)
$fixedPollingPattern = 'Start-Sleep\s+-Seconds\s+' + '55\b'
$fixedPollingMatches = @(
  Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'scripts') -Recurse -File -Filter '*.ps1' |
    Select-String -Pattern $fixedPollingPattern
)
if ($fixedPollingMatches.Count -gt 0) {
  throw "fixed 55-second CI polling must not return: $($fixedPollingMatches.Path -join ', ')"
}
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
