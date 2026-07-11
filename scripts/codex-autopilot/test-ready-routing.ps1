param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-ready.ps1')
. (Join-Path $scriptDir 'autopilot-route.ps1')

$root = Join-Path ([System.IO.Path]::GetTempPath()) ('autopilot-ready-test-' + [guid]::NewGuid().ToString('N'))
$readyPath = Join-Path $root 'docs\backlog\ready-issues.md'
New-Item -ItemType Directory -Path (Split-Path -Parent $readyPath) -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $root 'docs\quality') -Force | Out-Null

function New-ReadyText {
  param([string]$Id = 'ISSUE-900-001', [string]$Nature = '缺口修复', [string]$Risk = '低', [string]$Allowed = '`docs/quality/**`')
  $tick = [char]96
  @"
# Ready Issues

### ${Id}：Strict contract

任务性质：$Nature
目标：
- Prove strict parsing.
非目标：
- No business or production change.
允许修改：
- $Allowed
禁止修改：
- ${tick}deploy/**${tick}
验收标准：
- Contract is machine-readable.
状态：Ready
来源锚点：${tick}docs/plans/source.md${tick}
验证命令：
- ${tick}git diff --check${tick}
归档报告：${tick}docs/quality/$($Id.ToLowerInvariant()).md${tick}
Migration：不需要
依赖：无
风险等级：$Risk
运行态要求：无
Reviewer要求：不需要
"@
}

function Assert-Fails {
  param([scriptblock]$Action, [string]$Expected)
  $failed = $false
  try { & $Action | Out-Null } catch { $failed = $_.Exception.Message -match [regex]::Escape($Expected) }
  if (!$failed) { throw "Expected failure containing: $Expected" }
}

try {
  New-ReadyText | Set-Content -LiteralPath $readyPath -Encoding UTF8
  $issues = @(Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root)
  if ($issues.Count -ne 1 -or $issues[0].issueId -ne 'ISSUE-900-001') { throw 'valid Ready was not parsed' }
  $firstHash = $issues[0].readyContentHash
  (New-ReadyText).Replace('Prove strict parsing.', 'Prove changed parsing.') | Set-Content -LiteralPath $readyPath -Encoding UTF8
  $secondHash = @(Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root)[0].readyContentHash
  if ($firstHash -eq $secondHash) { throw 'Ready content hash did not change' }

  ((New-ReadyText) + "`n" + (New-ReadyText)) | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } 'duplicate Ready Issue ID'

  (New-ReadyText).Replace("非目标：`n- No business or production change.`n", '') | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } '非目标'

  New-ReadyText -Nature '随便做做' | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } '任务性质'

  (New-ReadyText).Replace('Migration：不需要', '') | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } 'Migration'

  New-ReadyText -Allowed '' | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } '允许修改'

  (New-ReadyText).Replace('`git diff --check`', '`cd missing-backend; .\mvnw.cmd test`') | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } '验证命令入口不存在'

  (New-ReadyText).Replace('`git diff --check`', '`Remove-Item -Recurse deploy`') | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } '验证命令不在白名单'
  (New-ReadyText).Replace('`git diff --check`', '`git diff --check $(Remove-Item deploy)`') | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } '验证命令不在白名单'
  (New-ReadyText).Replace('`git diff --check`', '`cd ..; git diff --check`') | Set-Content -LiteralPath $readyPath -Encoding UTF8
  Assert-Fails { Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root } '验证命令目录逃逸仓库'

  New-ReadyText | Set-Content -LiteralPath $readyPath -Encoding UTF8
  $low = @(Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root)[0]
  $upgraded = Get-AutopilotRoute -Issue $low -ChangedPaths @('backend/src/main/java/com/cgcpms/security/AuthGuard.java')
  if (!$upgraded.highRisk -or !$upgraded.reviewRequired) { throw 'actual high-risk diff did not upgrade review route' }
  $roleUpgrade = Get-AutopilotRoute -Issue $low -ChangedPaths @('backend/src/main/java/com/cgcpms/system/service/SysRoleService.java')
  if (!$roleUpgrade.highRisk -or !$roleUpgrade.reviewRequired) { throw 'actual permission-role diff did not upgrade review route' }

  New-ReadyText -Nature '运维治理' -Risk '高' -Allowed '`backend/src/main/java/com/cgcpms/auth/**`, `frontend-admin/src/**`' | Set-Content -LiteralPath $readyPath -Encoding UTF8
  $high = @(Get-AutopilotReadyIssues -Path $readyPath -RepoRoot $root)[0]
  $route = Get-AutopilotRoute -Issue $high -ChangedPaths @('backend/src/main/java/com/cgcpms/auth/X.java','frontend-admin/src/X.vue')
  if (!$route.reviewRequired -or !$route.serialRequired -or $route.modelBaseline -ne 'gpt-5.6-sol' -or $route.executorRole -ne 'ops') { throw 'high-risk route is not strict enough' }

  Write-Host 'ready routing self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
