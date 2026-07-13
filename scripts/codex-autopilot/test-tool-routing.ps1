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

Assert-Contains 'AGENTS.override.md' @('跨层影响', 'codebase-memory-mcp', 'tool_config')
Assert-Contains 'plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md' @('查询目的', '交叉核验', '图谱检索证据')
Assert-Contains 'plugins/cgc-pms-autopilot/references/role-contracts.md' @('查询目的', '交叉核验', '图谱检索证据')
Assert-Contains 'plugins/cgc-pms-autopilot/templates/iteration-report-entry.md' @('图谱检索证据', '{{graph_evidence}}')
Assert-Contains 'plugins/cgc-pms-autopilot/examples/iteration-report-entry.example.md' @('CodeGraph', 'codebase-memory-mcp', '查询目的', '交叉核验')

Write-Host 'tool routing self-test passed'
