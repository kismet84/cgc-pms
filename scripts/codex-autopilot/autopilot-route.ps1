$ErrorActionPreference = 'Stop'

function Get-AutopilotRoute {
  param([Parameter(Mandatory)][object]$Issue, [string[]]$ChangedPaths = @())

  $paths = @(@($Issue.allowedPaths) + @($ChangedPaths) | Where-Object { $_ } | Select-Object -Unique)
  $text = (($paths -join ' ') + ' ' + $Issue.body).ToLowerInvariant()
  $highRisk = $Issue.riskLevel -eq '高' -or $Issue.migration -eq '需要' -or $text -match 'auth|authorization|permission|security|tenant|rbac|acl|sysrole|sysuser|(?:^|[/\\._-])role(?:[/\\._-]|$)|鉴权|权限|租户|金额|付款|审批|db/migration|状态机|file.*security'
  $roots = @($paths | ForEach-Object { (($_ -replace '\\','/') -split '/')[0] } | Where-Object { $_ -in @('backend','frontend-admin','mobile','deploy','docs') } | Select-Object -Unique)
  $crossModule = $roots.Count -gt 1
  $docsOrTestsOnly = $paths.Count -gt 0 -and @($paths | Where-Object { $_ -notmatch '^(docs/|.*test.*|scripts/codex-autopilot/test-)'}).Count -eq 0
  $runtime = $Issue.taskNature -eq '运维治理' -or $Issue.runtimeRequirement -notin @('','无','不需要')
  $reviewRequired = $highRisk -or $crossModule -or $Issue.reviewerRequirement -notin @('','无','不需要')

  $role = if ($Issue.taskNature -eq '运维治理') { 'ops' } elseif ($text -match 'frontend-admin|mobile') { if ($text -match 'backend') { 'BC' } else { 'B' } } elseif ($text -match 'backend') { 'C' } else { 'F' }
  $model = if ($highRisk) { 'gpt-5.6-sol' } elseif ($docsOrTestsOnly) { 'gpt-5.6-luna' } else { 'gpt-5.6-terra' }
  $thinking = if ($highRisk) { 'high' } elseif ($docsOrTestsOnly) { 'low' } else { 'medium' }
  $profile = if ($highRisk) { 'high-security' } elseif ($runtime) { 'runtime-health' } else { 'standard' }

  return [pscustomobject]@{
    executorRole = $role
    modelBaseline = $model
    thinkingBaseline = $thinking
    reviewRequired = [bool]$reviewRequired
    serialRequired = [bool]($highRisk -or $crossModule)
    verificationProfile = $profile
    highRisk = [bool]$highRisk
    crossModule = [bool]$crossModule
    reason = if ($highRisk) { 'high-risk boundary' } elseif ($crossModule) { 'cross-module change' } elseif ($docsOrTestsOnly) { 'mechanical docs/test scope' } else { 'ordinary local implementation' }
  }
}
