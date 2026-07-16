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

Assert-Contains 'AGENTS.override.md' @('docs/standards/codex-task-execution-policy.md', 'PowerShell', 'codebase-memory-mcp', 'retrieval_gap')
Assert-Contains 'docs/standards/codex-task-execution-policy.md' @('已知文件、配置键、错误文本、精确字符串', 'CodeGraph，一次有界查询', '跨前后端、跨语言、多跳调用链、架构边界', 'PowerShell 定义、引用、动态调用和调用链', '禁止使用 CodeGraph', 'retrieval_gap', '原样重试')
Assert-Contains 'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md' @('查询目的', '交叉核验', '图谱检索证据')
Assert-Contains 'plugins/cgc-pms-autopilot/references/role-contracts.md' @('查询目的', '交叉核验', '图谱检索证据')
Assert-Contains 'plugins/cgc-pms-autopilot/templates/iteration-report-entry.md' @('图谱检索证据', '{{graph_evidence}}')
Assert-Contains 'plugins/cgc-pms-autopilot/examples/iteration-report-entry.example.md' @('CodeGraph', 'codebase-memory-mcp', '查询目的', '交叉核验')

Write-Host 'tool routing self-test passed'
