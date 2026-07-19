[CmdletBinding()]
param([string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)

$ErrorActionPreference = 'Stop'

function Read-RepoText([string]$RelativePath) {
  $path = Join-Path $RepoRoot $RelativePath
  if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "required execution-policy artifact is missing: $RelativePath" }
  return Get-Content -LiteralPath $path -Raw -Encoding UTF8
}

function Assert-TextContains([string]$Text, [string[]]$Patterns, [string]$Name) {
  foreach ($pattern in $Patterns) {
    if ($Text -notmatch [regex]::Escape($pattern)) { throw "$Name is missing required contract text: $pattern" }
  }
}

$policy = Read-RepoText 'docs\standards\codex-task-execution-policy.md'
$override = Read-RepoText 'AGENTS.override.md'
$agents = Read-RepoText 'AGENTS.md'
$mainline = Read-RepoText '.agents\skills\cgc-pms-mainline-owner-flow\SKILL.md'
$ci = Read-RepoText '.agents\skills\cgc-pms-ci-gate-triage\SKILL.md'
$runtime = Read-RepoText '.agents\skills\cgc-pms-runtime-refresh\SKILL.md'
$desktop = Read-RepoText 'plugins\cgc-pms-autopilot\references\desktop-execution-policy.md'
$gitLifecycleTest = Read-RepoText 'scripts\codex-autopilot\test-codex-task-git-lifecycle.ps1'
$efficiencyCalculator = Read-RepoText 'scripts\codex-autopilot\measure-codex-task-efficiency.ps1'
$efficiencyCalculatorTest = Read-RepoText 'scripts\codex-autopilot\test-codex-task-efficiency.ps1'
$policySuite = Read-RepoText 'scripts\codex-autopilot\test-codex-task-policy-suite.ps1'
$prePrGate = Read-RepoText 'scripts\codex-autopilot\verify-pre-pr-ci.ps1'
$prePrGateTest = Read-RepoText 'scripts\codex-autopilot\test-pre-pr-ci-gate.ps1'
$ciWorkflow = Read-RepoText '.github\workflows\ci.yml'
$mysqlGrantScript = Read-RepoText 'scripts\ci\verify-mysql-grants.sh'
$prTemplate = Read-RepoText '.github\pull_request_template.md'
$config = Read-RepoText 'scripts\codex-autopilot\codex-autopilot.config.json' | ConvertFrom-Json

Assert-TextContains $policy @(
  '已知文件、配置键、错误文本、精确字符串','Java、TypeScript、Vue 未知符号与局部引用',
  '跨前后端、跨语言、多跳调用链、架构边界','PowerShell 定义、引用、动态调用和调用链',
  '禁止使用 CodeGraph','retrieval_gap','默认最多切换一个有信息增益的备用路径',
  'tool_config','tool_invocation','environment_prerequisite','quality_or_security','unknown',
  'L1 目标验证','L2 模块回归','L3 批次验收','L4 发布验证',
  '禁止直接向 `master` 推送','普通任务通常不超过 5 次过程播报',
  '任务目标=','禁止重复执行=','是否允许提交/推送/合并=',
  '最小样本胶囊','工具调用总数=','控制面耗时/有效执行耗时=','not_available',
  'measure-codex-task-efficiency.ps1','不内置计划目标阈值',
  'project 标识是运行态事实','不得猜测或再次使用已被工具拒绝的值',
  'powershell_parser_error','报表归一化不得改写 AutoPilot 历史状态',
  '首次非 Draft PR 前置门禁','同 HEAD SHA','DELIVERY_GATE_OMISSION','PR 首次 CI 通过率',
  'verify-pre-pr-ci.ps1','event=push','backend-test-mysql','frontend-v2-gate','supply-chain-security','e2e'
) 'shared task policy'

foreach ($entry in @(
  @{name='AGENTS.override.md';text=$override}, @{name='AGENTS.md';text=$agents},
  @{name='mainline skill';text=$mainline}, @{name='desktop policy';text=$desktop}
)) {
  Assert-TextContains $entry.text @('docs/standards/codex-task-execution-policy.md') $entry.name
}

foreach ($legacyHeading in @('CI 与验收失败分类规则：','内置浏览器测试规则：','### 通用源码检索与交叉核验','### PowerShell 专用检索规则')) {
  if ($override -match [regex]::Escape($legacyHeading)) { throw "AGENTS.override.md still duplicates shared policy section: $legacyHeading" }
}

Assert-TextContains $override @(
  'Codex Local AutoPilot 最高级边界与索引',
  'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md',
  'plugins/cgc-pms-autopilot/references/control-plane-policy.md',
  'plugins/cgc-pms-autopilot/references/desktop-execution-policy.md',
  'scripts/codex-autopilot/codex-autopilot.config.json'
) 'AGENTS.override.md AutoPilot index'
Assert-TextContains $agents @(
  'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md',
  '`current-issues.json` 是正式写回源，不是默认发现入口',
  '图谱异常时 fail-close'
) 'AGENTS.md AutoPilot discovery index'
if ($agents -match 'AutoPilot 补货优先级固定为') { throw 'AGENTS.md still copies an AutoPilot refill priority' }

Assert-TextContains $ci @(
  'tool_invocation','ready_issue_config','一次最小等价复验','退避节奏','状态未变化时保持静默',
  'Git SSH','Accept-Ranges','256 KB','临时签名 URL','DELIVERY_GATE_OMISSION',
  '只有 GitHub 服务、网络或 Runner 基础设施故障','verify-pre-pr-ci.ps1','PR 首次 CI 通过率'
) 'CI skill'
Assert-TextContains $mainline @('同 HEAD SHA','可提 PR','交付门禁遗漏') 'mainline skill pre-PR gate'
Assert-TextContains $runtime @('actuator/health','dev-login','只初始化一次','同一参数错误不得原样重试','唯一输出目录') 'runtime skill'
Assert-TextContains $gitLifecycleTest @('remoteMasterUnchanged','unmergedBranchExcluded','occupiedWorktreeDetected') 'Git lifecycle test'
Assert-TextContains $efficiencyCalculator @('toolFailureRatePct','controlPlaneSharePct','classified tool outcomes cannot exceed total tool calls','ConvertTo-Json') 'efficiency sample calculator'
Assert-TextContains $efficiencyCalculatorTest @('persistentWrites = 0','missing timing was fabricated','timed rate calculation is invalid') 'efficiency sample calculator test'
Assert-TextContains $policySuite @('test-codex-task-efficiency.ps1','test-codex-task-git-lifecycle.ps1','test-pre-pr-ci-gate.ps1','test-tool-routing.ps1','test-control-plane-fingerprint.ps1','test-mainline-owner-flow.ps1','Parameter(Mandatory)') 'Codex task policy suite'
Assert-TextContains $prePrGate @('headBranch','TRACKED_WORKTREE_DIRTY','INVALID_FEATURE_BRANCH','event','push','PRE_PR_CI_EVIDENCE_MISSING','PRE_PR_CI_JOB_EVIDENCE_MISSING','build-summary','frontend-v2-gate','supply-chain-security','e2e') 'pre-PR CI evidence gate'
Assert-TextContains $prePrGateTest @('valid pre-PR CI evidence','PRE_PR_CI_NOT_GREEN','PRE_PR_CI_JOB_NOT_GREEN') 'pre-PR CI evidence gate test'
Assert-TextContains $ciWorkflow @("branches: ['**']",'Verify MySQL migration user scope','bash ./scripts/ci/verify-mysql-grants.sh','Run MySQL least-privilege Flyway migration smoke test','Verify backend test order independence','frontend-v2-gate','supply-chain-security','e2e') 'CI workflow pre-PR equivalence'
Assert-TextContains $mysqlGrantScript @('normalized_grants','CI_MYSQL_DATABASE','MySQL migration user has global privileges') 'MySQL least-privilege script'
Assert-TextContains $prTemplate @('首次非 Draft PR HEAD SHA','verify-pre-pr-ci.ps1','只能保持 Draft','V2 门禁','E2E') 'pull request evidence template'

if ([string]$config.baseBranch -ne 'master') { throw 'AutoPilot baseBranch is not aligned with the actual repository base branch' }
$fingerprints = @($config.controlPlaneCanary.fingerprintPaths)
foreach ($path in @(
  'docs/standards/codex-task-execution-policy.md',
  'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md',
  '.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md',
  '.agents/skills/cgc-pms-ci-gate-triage/SKILL.md',
  '.agents/skills/cgc-pms-runtime-refresh/SKILL.md',
  '.github/workflows/ci.yml',
  'scripts/codex-autopilot/verify-pre-pr-ci.ps1'
)) {
  if ($fingerprints -notcontains $path) { throw "control-plane fingerprint is missing execution-policy behavior path: $path" }
}

if ($policy -match 'autopilot-task-score/v\d|\b35/25/20/10/10\b|timeoutSeconds\s*[=:]') { throw 'shared task policy copied dynamic AutoPilot facts' }
if ($override -match 'autopilot-task-score/v\d|\b35/25/20/10/10\b|timeoutSeconds\s*[=:]|第\s*20\s*个有效任务|第\s*21\s*个任务') { throw 'AGENTS.override.md copied dynamic AutoPilot facts' }

[pscustomobject]@{
  ok = $true
  policy = 'docs/standards/codex-task-execution-policy.md'
  baseBranch = [string]$config.baseBranch
  fingerprintedArtifacts = 7
} | ConvertTo-Json -Depth 3
