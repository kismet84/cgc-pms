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
$loopBudget = Read-RepoText 'plugins\cgc-pms-autopilot\references\loop-budget-policy.md'
$artifactGovernance = Read-RepoText 'plugins\cgc-pms-autopilot\references\artifact-governance.md'
$pluginInstall = Read-RepoText 'plugins\cgc-pms-autopilot\references\install.md'
$classifier = Read-RepoText 'plugins\cgc-pms-autopilot\references\classifier-rules.md'
$classificationSchema = Read-RepoText 'plugins\cgc-pms-autopilot\schemas\classification-result.schema.json'
$prePrGate = Read-RepoText 'scripts\codex-autopilot\verify-pre-pr-ci.ps1'
$ciWorkflow = Read-RepoText '.github\workflows\ci.yml'
$backendStandard = Read-RepoText 'docs\standards\04-后端开发规范.md'
$quickStart = Read-RepoText 'docs\standards\01-快速开始.md'
$apiStandard = Read-RepoText 'docs\standards\06-API契约规范.md'
$databaseStandard = Read-RepoText 'docs\standards\07-数据库与迁移规范.md'
$permissionStandard = Read-RepoText 'docs\standards\08-权限与审批流程.md'
$testStandard = Read-RepoText 'docs\standards\09-测试规范.md'
$securityStandard = Read-RepoText 'docs\standards\11-安全规范.md'
$scoringStandard = Read-RepoText 'docs\standards\14-AutoPilot任务评分与自动改进回顾规范.md'
$promptIndex = Read-RepoText 'docs\prompt\README.md'
$frontendPrompt = Read-RepoText 'docs\prompt\frontend-docker-ui-test-rules.md'
$larkPrompt = Read-RepoText 'docs\prompt\lark-confirmation-flow.md'
$envExample = Read-RepoText 'deploy\.env.example'
$composeDev = Read-RepoText 'deploy\docker-compose.dev.yml'
$composeProd = Read-RepoText 'deploy\docker-compose.prod.yml'
$config = Read-RepoText 'scripts\codex-autopilot\codex-autopilot.config.json' | ConvertFrom-Json
$skillsLock = Read-RepoText 'skills-lock.json' | ConvertFrom-Json

if (Test-Path -LiteralPath (Join-Path $RepoRoot 'AGENTS.override.md')) { throw 'AGENTS.override.md must not remain as a second root rule' }
$agentLineCount = @($agents -split "\r?\n").Count
if ($agentLineCount -gt 80) { throw "AGENTS.md exceeds 80 lines: $agentLineCount" }
Assert-Contains 'AGENTS.md' $agents @(
  '所有回答使用中文','未获明确授权','git branch --show-current','git status --short',
  '保留既有脏改动','禁止自动发布生产','最小相关验证','Git','零悬空收口',
  '启动迭代-1','普通任务无需显式重读本文件','通常创建1～5个短生命周期子智能体',
  '存在三个明确独立工作流时最多5个','用户单独发送完整指令“推送”时','不授权无关改动、强推、绕过保护、生产部署或删除目标分支',
  'docs/codemap/codemap.lock','docs/codemap/codemap.json','regenerate `docs/codemap/codemap.html`',
  '本项目当前仅存在本地开发环境','不得发起、尝试、规划或将非本地环境测试/验收列为阻塞项'
)
if ($agents -match 'luna_worker') { throw 'repository root hard-depends on an external fixed agent' }

Assert-Contains 'route index' $policy @(
  '普通代码、文档、审查和解释任务','显式规则读取为 0','任务路由',
  '运行态与 CI 各只读取对应 Skill','非 AutoPilot 任务不得读取 checkpoint',
  'Skill 不重新读取根规则','仅 Git 已跟踪的 `.agents/skills/**`','不得用本地存在性冒充项目来源'
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
Assert-Contains 'CI evidence reuse policy' $ci @(
  'pr-push-evidence','精确 HEAD','15 个 required jobs','CI_PR_PUSH_REUSE_ENABLED=true','fork PR','push run、带 PR 编号的 PR run','merge tree'
)
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
foreach ($legacyTrigger in @('启动自动迭代系统','启动连续自动迭代系统','停止自动迭代系统')) {
  if (($owner + $pluginInstall) -match [regex]::Escape($legacyTrigger)) { throw "AutoPilot active guidance retains unauthorized compatibility trigger: $legacyTrigger" }
}
Assert-Contains 'control-plane policy' $controlPlane @('Ready 为空时','知识图谱健康','有界查询','图谱异常','控制面指纹')
if (Test-Path -LiteralPath (Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\references\failure-classification.md')) {
  throw 'duplicate AutoPilot failure-classification authority still exists'
}

Assert-Contains 'local artifact policy' $policy @('四类处理','实际 `storeDir`','一次性目录完成离线安装','数据库文件','守护日志','进程或锁','补同步结果','父目录时间不得单独触发删除或重建')
if ($policy -match '(?i)(?:命中\s*`?\.gitignore`?|可重建|可下载).{0,20}(?:即可|直接|应当)删除') { throw 'ignored or rebuildable artifacts can still be deleted without reuse evidence' }
Assert-Contains 'artifact governance' $artifactGovernance @('插件自身计划书','独立项目业务任务的计划书','docs/plans','plugins/cgc-pms-autopilot/artifacts/plans')

Assert-Contains 'soft-delete standards' ($backendStandard + $databaseStandard) @('active_unique_token','活动行固定为 `0`','删除行')
if ($databaseStandard -match '待迁移至\s*`?deleted_token|V90\+.{0,40}(?:未来|迁移)') { throw 'database standard revived retired deleted_token or V90+ work' }
Assert-Contains 'project permission standard' $permissionStandard @('必须同时按当前租户和项目访问范围过滤','范围证据不足时拒绝访问')
Assert-Contains 'JWT standard' $securityStandard @('已完成统一','15 分钟','1 小时','无待修改项')
if ($securityStandard -match 'test 和 local 使用 24h|(?:test|local).{0,40}需修改') { throw 'JWT standard still reports completed profile work as pending' }
Assert-Contains 'pagination standard' $apiStandard @('pageSize=10','新版前端业务列表默认 10 条','`pageSize` 是调用方参数')
Assert-Contains 'test standard' $testStandard @('普通修改先执行风险相称的相关测试','首次非 Draft PR','主线正式收口','同 HEAD SHA 等价 CI')
if ($testStandard -match '提交前必须.{0,120}(?:全量|`verify`)') { throw 'test standard still requires unconditional full backend gates for every commit' }

if ($scoringStandard -match 'autopilot-task-score/v\d|35/25/20/10/10|(?:第|达到|完成)\s*20\s*个有效任务|timeoutSeconds\s*[=:]') { throw 'scoring standard copied dynamic AutoPilot facts' }
Assert-Contains 'scoring authority' $scoringStandard @('控制面策略','codex-autopilot.config.json','schemas/**','批准来源')
if ($loopBudget -match '(?m)^\s*-\s*max_[a-z_]+\s*=|max_wall_time_minutes') { throw 'loop budget copied dynamic numeric defaults' }
Assert-Contains 'loop budget scopes' $loopBudget @('run 总时长','Issue 补修次数','command 重试','不同作用域')

if ($promptIndex -match 'AGENTS\.override\.md') { throw 'prompt index still references removed AGENTS.override.md' }
if ($frontendPrompt -match '必须重启 Docker|180秒|重启 Docker frontend') { throw 'frontend prompt still requires unconditional Docker restart or fixed wait' }
Assert-Contains 'frontend runtime routing' $frontendPrompt @('运行态刷新 Skill','证据陈旧','不使用固定等待时长')
if (($quickStart + $pluginInstall) -match '等待\s*`?180\s*秒|每轮最多并行\s*3') { throw 'active guidance still copies fixed runtime waits or AutoPilot parallelism' }
Assert-Contains 'quick-start runtime routing' $quickStart @('运行态刷新 Skill','不使用固定等待时长')
if ($larkPrompt -match '(?i)\b(?:ou|oc)_[a-z0-9]+\b|默认选择|默认选项') { throw 'Lark confirmation prompt retains fixed recipient IDs or timeout authorization' }
Assert-Contains 'Lark fail-close' $larkPrompt @('超时将停止该决策点并保持现状','收到与本次确认绑定的有效回复后才继续','仍无有效回复则停止该决策点')

Assert-Contains 'MinIO environment contract' $envExample @('MINIO_ROOT_USER=','MINIO_ROOT_PASSWORD=','MINIO_ACCESS_KEY=','MINIO_SECRET_KEY=','separate least-privilege service account')
foreach ($entry in @(@{name='dev Compose';text=$composeDev},@{name='prod Compose';text=$composeProd})) {
  Assert-Contains $entry.name $entry.text @('MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY','MINIO_SECRET_KEY: ${MINIO_SECRET_KEY')
  if ($entry.text -match 'MINIO_ACCESS_KEY:\s*\$\{MINIO_ROOT_USER\}|MINIO_SECRET_KEY:\s*\$\{MINIO_ROOT_PASSWORD\}') { throw "$($entry.name) maps application credentials to MinIO root" }
}
Assert-Contains 'production MinIO preflight' $composeProd @('MINIO_ACCESS_KEY must not reuse MINIO_ROOT_USER','MINIO_SECRET_KEY must not reuse MINIO_ROOT_PASSWORD')

$larkSlidesLock = $skillsLock.skills.'lark-slides'
if (!$larkSlidesLock -or [string]$larkSlidesLock.source -ne 'open.feishu.cn' -or [string]$larkSlidesLock.sourceType -ne 'well-known' -or [string]$larkSlidesLock.computedHash -notmatch '^[a-f0-9]{64}$') {
  throw 'tracked skills lock does not identify the lark-slides provider and immutable hash'
}
$larkCli = Get-Command lark-cli -ErrorAction SilentlyContinue
if ($null -ne $larkCli) {
  $embeddedSlides = (& $larkCli.Source skills read lark-slides 2>&1) -join "`n"
  if ($LASTEXITCODE -ne 0 -or $embeddedSlides -notmatch '(?m)^name:\s*lark-slides\s*$' -or $embeddedSlides -notmatch '\.\./lark-shared/SKILL\.md') { throw 'lark-cli embedded lark-slides authority is unavailable or missing its shared-auth dependency' }
  $embeddedShared = (& $larkCli.Source skills read lark-shared 2>&1) -join "`n"
  if ($LASTEXITCODE -ne 0 -or $embeddedShared -notmatch '(?m)^name:\s*lark-shared\s*$' -or $embeddedShared -notmatch 'auth login') { throw 'lark-cli embedded lark-shared authority is unavailable' }
}

$longTaskSkillRoot = Join-Path $RepoRoot '.agents\skills\long-task-gate'
$longTaskHooksPath = Join-Path $RepoRoot '.codex\hooks.json'
if (!(Test-Path -LiteralPath $longTaskSkillRoot -PathType Container) -or !(Test-Path -LiteralPath $longTaskHooksPath -PathType Leaf)) {
  throw 'long-task-gate deliverable Skill or repository Hook is missing'
}
$longTaskHooks = Get-Content -LiteralPath $longTaskHooksPath -Raw -Encoding UTF8 | ConvertFrom-Json
$longTaskHookText = Get-Content -LiteralPath $longTaskHooksPath -Raw -Encoding UTF8
$longTaskScript = Get-Content -LiteralPath (Join-Path $longTaskSkillRoot 'scripts\long-task-gate.mjs') -Raw -Encoding UTF8
$longTaskMetadata = Get-Content -LiteralPath (Join-Path $longTaskSkillRoot 'agents\openai.yaml') -Raw -Encoding UTF8
Assert-Contains 'long-task Hook contract' $longTaskHookText @('UserPromptSubmit','Stop','commandWindows','long-task-gate.mjs')
Assert-Contains 'long-task execution boundary' $longTaskScript @('shell: false','BLOCKED_GATE','BLOCKED_NOTIFICATION','--idempotency-key','targetEnv')
Assert-Contains 'long-task explicit Skill policy' $longTaskMetadata @('allow_implicit_invocation: false')
if (@($longTaskHooks.hooks.UserPromptSubmit).Count -ne 1 -or @($longTaskHooks.hooks.Stop).Count -ne 1) { throw 'long-task Hook event count is not fail-close' }
if (($longTaskHookText + $longTaskScript + $longTaskMetadata) -match '(?i)\b(?:ou|oc)_[a-z0-9]{8,}\b|app_secret\s*[:=]|tenant_access_token\s*[:=]') {
  throw 'long-task deliverable contains a fixed recipient or credential'
}

$trackedSkillPaths = @(& git -C $RepoRoot ls-files -c -o --exclude-standard -- '.agents/skills/**/*.md' 'plugins/**/skills/**/*.md')
if ($LASTEXITCODE -ne 0 -or $trackedSkillPaths.Count -eq 0) { throw 'deliverable Skill Markdown inventory is unavailable' }
foreach ($relativePath in $trackedSkillPaths) {
  $source = Get-Item -LiteralPath (Join-Path $RepoRoot $relativePath)
  $text = Get-Content -LiteralPath $source.FullName -Raw -Encoding UTF8
  foreach ($match in [regex]::Matches($text,'\[[^\]]+\]\((?<target>[^)]+)\)')) {
    $target = $match.Groups['target'].Value.Trim().Trim('<','>')
    if ($target -match '^(?:https?://|mailto:|#)') { continue }
    $pathPart = ($target -split '#',2)[0]
    if (!$pathPart) { continue }
    $resolved = [IO.Path]::GetFullPath((Join-Path $source.DirectoryName ([Uri]::UnescapeDataString($pathPart))))
    if (!(Test-Path -LiteralPath $resolved)) { throw "tracked skill Markdown link is missing: $($source.FullName) -> $target" }
  }
}

Assert-Contains 'pre-PR gate' $prePrGate @('headBranch','TRACKED_WORKTREE_DIRTY','event','push','PRE_PR_CI_EVIDENCE_MISSING','pr-push-evidence','backend-order-sensitive','frontend-v2-gate','supply-chain-security','e2e')
Assert-Contains 'CI workflow' $ciWorkflow @(
  'branches-ignore: [master, main]','workflow_dispatch:','pr-push-evidence',
  'verify-pr-push-evidence.ps1','Verify MySQL migration user scope','frontend-v2-gate','supply-chain-security','e2e'
)

if ([string]$config.baseBranch -ne 'master') { throw 'AutoPilot baseBranch is not aligned with repository policy' }
$fingerprints = @($config.controlPlaneCanary.fingerprintPaths)
if ($fingerprints -contains 'AGENTS.override.md') { throw 'control-plane fingerprint still references removed AGENTS.override.md' }
foreach ($path in @(
  'AGENTS.md',
  'plugins/cgc-pms-autopilot/references/classifier-rules.md',
  'plugins/cgc-pms-autopilot/schemas/classification-result.schema.json',
  'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md',
  '.agents/skills/cgc-pms-ci-gate-triage/SKILL.md',
  '.github/CODEOWNERS',
  '.github/workflows/post-merge.yml',
  'scripts/ci/verify-pr-push-evidence.ps1',
  'scripts/ci/verify-post-merge-ci.ps1'
)) {
  if ($fingerprints -notcontains $path) { throw "control-plane fingerprint missing behavior path: $path" }
}

[pscustomobject]@{
  ok = $true
  rootRuleLines = $agentLineCount
  canonicalFailureCategories = $canonicalCategories
  ordinaryExplicitRuleReads = 0
} | ConvertTo-Json -Depth 4
