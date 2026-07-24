Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Assert-Contains {
  param(
    [string]$RelativePath,
    [string[]]$Patterns
  )

  $content = Get-Content -LiteralPath (Join-Path $repoRoot $RelativePath) -Raw -Encoding UTF8
  foreach ($pattern in $Patterns) {
    if ($content -notmatch [regex]::Escape($pattern)) {
      throw "$RelativePath missing required tool-routing text: $pattern"
    }
  }
}

if (Test-Path -LiteralPath (Join-Path $repoRoot 'AGENTS.override.md')) { throw 'removed AGENTS.override.md still exists' }
Assert-Contains 'AGENTS.md' @('普通任务无需显式重读本文件', '运行态/浏览器', 'CI、PR、失败分类', '主线、计划、正式验收与收口')
Assert-Contains 'docs/standards/codex-task-execution-policy.md' @('普通任务不读取本索引', '运行态与 CI 各只读取对应 Skill', '非 AutoPilot 任务不得读取 checkpoint')
Assert-Contains '.agents/skills/cgc-pms-ci-gate-triage/SKILL.md' @('retrieval_gap', '使用允许的备用检索', '不作不存在断言')
Assert-Contains 'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md' @('role-contracts.md', 'classifier-rules.md', '普通任务不读取本 Skill')
Assert-Contains 'plugins/cgc-pms-autopilot/references/role-contracts.md' @('查询目的', '交叉核验', '图谱检索证据')
Assert-Contains 'plugins/cgc-pms-autopilot/templates/iteration-report-entry.md' @('图谱检索证据', '{{graph_evidence}}')
Assert-Contains 'plugins/cgc-pms-autopilot/examples/iteration-report-entry.example.md' @('CodeGraph', 'codebase-memory-mcp', '查询目的', '交叉核验')

Write-Host 'tool routing self-test passed'
