$ErrorActionPreference = 'Stop'

function Get-AutopilotIssueBlocks {
  param([Parameter(Mandatory)][string]$Path)
  if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Ready Issue file not found: $Path" }
  $text = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
  $blocks = @()
  foreach ($match in [regex]::Matches($text, '(?ms)^###\s+(ISSUE-[0-9-]+[^\r\n]*)\r?\n(.*?)(?=^###\s+ISSUE-|\z)')) {
    $title = $match.Groups[1].Value.Trim()
    $idMatch = [regex]::Match($title, '^(ISSUE-[0-9-]+)')
    $blocks += [pscustomobject]@{ issueId = $idMatch.Groups[1].Value; title = $title; body = $match.Groups[2].Value; rawBlock = $match.Value.Trim() }
  }
  $duplicates = @($blocks | Group-Object issueId | Where-Object Count -gt 1)
  if ($duplicates.Count -gt 0) { throw "duplicate Ready Issue ID: $($duplicates[0].Name)" }
  return $blocks
}

function Get-AutopilotFieldValue {
  param([string]$Body, [string]$Name)
  $match = [regex]::Match($Body, "(?m)^$([regex]::Escape($Name))[：:]\s*(.*?)\s*$")
  if ($match.Success) { return $match.Groups[1].Value.Trim() }
  return ''
}

function Get-AutopilotSectionLines {
  param([string]$Body, [string]$Name)
  $match = [regex]::Match($Body, "(?ms)^$([regex]::Escape($Name))[：:]\s*(?:\r?\n)?(.*?)(?=^[^\r\n：:]{2,24}[：:]|^###\s+ISSUE-|\z)")
  if (!$match.Success) { return @() }
  return @($match.Groups[1].Value -split '\r?\n' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Get-AutopilotCodeValues {
  param([string[]]$Lines)
  $values = @()
  foreach ($line in $Lines) {
    foreach ($match in [regex]::Matches($line, '``([^``]+)``|`([^`]+)`')) {
      $value = if ($match.Groups[1].Success) { $match.Groups[1].Value } else { $match.Groups[2].Value }
      if ($value.Trim()) { $values += $value.Trim() }
    }
  }
  return @($values | Select-Object -Unique)
}

function Test-AutopilotValidationEntry {
  param([string]$Command, [string]$RepoRoot)
  $workDir = $RepoRoot
  $commandText = $Command.Trim()
  $cdMatch = [regex]::Match($commandText, '^\s*cd\s+([^;&]+)\s*(?:;|&&)\s*(.+)$')
  if ($cdMatch.Success) {
    $target = $cdMatch.Groups[1].Value.Trim().Trim('"', "'")
    $workDir = Join-Path $RepoRoot $target
    $repoFull = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\')
    $workFull = [IO.Path]::GetFullPath($workDir).TrimEnd('\')
    if ($workFull -ne $repoFull -and !$workFull.StartsWith($repoFull + '\', [StringComparison]::OrdinalIgnoreCase)) { return "验证命令目录逃逸仓库：$Command" }
    if (!(Test-Path -LiteralPath $workDir -PathType Container)) { return "验证命令入口不存在：$Command" }
    $commandText = $cdMatch.Groups[2].Value.Trim()
  }
  if ($commandText -match '[;&|<>`]' -or $commandText -match '\$\(|@\(') { return "验证命令不在白名单：$Command" }
  if ($commandText -match '^(?:\.\\|\./)?mvnw(?:\.cmd)?\b') {
    if (!(Test-Path (Join-Path $workDir 'mvnw.cmd')) -and !(Test-Path (Join-Path $workDir 'mvnw'))) { return "验证命令入口不存在：$Command" }
  } elseif ($commandText -match '^pnpm\b') {
    if (!(Test-Path (Join-Path $workDir 'package.json'))) { return "验证命令入口不存在：$Command" }
  } elseif ($commandText -match '^git\s+diff\s+--check\b') {
    return ''
  } elseif ($commandText -match '^pwsh(?:\.exe)?\s+[^;&|<>]*-File\s+([^\s]+\.ps1)\b') {
    $scriptPath = $matches[1].Trim('"', "'")
    if (!(Test-Path (Join-Path $workDir $scriptPath))) { return "验证命令入口不存在：$Command" }
  } else {
    return "验证命令不在白名单：$Command"
  }
  return ''
}

function Get-AutopilotSha256 {
  param([string]$Text)
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}

function ConvertTo-AutopilotPathRule {
  param([string]$Rule)
  return ([string]$Rule).Trim().Replace('\', '/').TrimStart('./').TrimEnd('/')
}

function Test-AutopilotPathRuleCovers {
  param([string]$CoverRule, [string]$TargetRule)
  $cover = ConvertTo-AutopilotPathRule $CoverRule
  $target = ConvertTo-AutopilotPathRule $TargetRule
  if (!$cover -or !$target) { return $false }
  if ($cover.Equals($target, [StringComparison]::OrdinalIgnoreCase)) { return $true }
  if (!$cover.EndsWith('/**', [StringComparison]::Ordinal)) { return $false }
  $base = $cover.Substring(0, $cover.Length - 3).TrimEnd('/')
  if (!$base) { return $true }
  return $target.StartsWith($base + '/', [StringComparison]::OrdinalIgnoreCase)
}

function Assert-AutopilotReadyScopeContract {
  param([string]$IssueId, [string[]]$AllowedPaths, [string[]]$ForbiddenPaths)
  foreach ($allowed in @($AllowedPaths)) {
    foreach ($forbidden in @($ForbiddenPaths)) {
      if (Test-AutopilotPathRuleCovers -CoverRule $forbidden -TargetRule $allowed) {
        $conflictPath = ConvertTo-AutopilotPathRule $allowed
        throw "READY_SCOPE_CONTRADICTION issueId=$IssueId allowed=$allowed forbidden=$forbidden conflictPath=$conflictPath"
      }
    }
  }
}

function ConvertTo-AutopilotReadyIssue {
  param([object]$Block, [string]$RepoRoot)
  $errors = @()
  $requiredSections = @('目标','非目标','允许修改','禁止修改','验收标准','验证命令')
  foreach ($name in $requiredSections) {
    if (@(Get-AutopilotSectionLines $Block.body $name).Count -eq 0) { $errors += "缺少或为空：$name" }
  }
  $requiredFields = @('任务性质','状态','来源锚点','归档报告','Migration','依赖','风险等级','运行态要求','Reviewer要求')
  foreach ($name in $requiredFields) {
    if (!(Get-AutopilotFieldValue $Block.body $name)) { $errors += "缺少字段：$name" }
  }
  $nature = Get-AutopilotFieldValue $Block.body '任务性质'
  if ($nature -and @('能力新增','缺口修复','回归证明','运维治理') -notcontains $nature) { $errors += "任务性质无效：$nature" }
  $status = Get-AutopilotFieldValue $Block.body '状态'
  if ($status -cne 'Ready') { $errors += "状态必须精确为 Ready，当前：$status" }
  $migration = Get-AutopilotFieldValue $Block.body 'Migration'
  if ($migration -and @('需要','不需要') -notcontains $migration) { $errors += "Migration 必须为需要或不需要，当前：$migration" }
  $risk = Get-AutopilotFieldValue $Block.body '风险等级'
  if ($risk -and @('低','中','高') -notcontains $risk) { $errors += "风险等级无效：$risk" }
  $sourceAnchor = Get-AutopilotFieldValue $Block.body '来源锚点'
  $candidateEvidenceHead = ''
  $candidateEvidenceMatch = [regex]::Match($sourceAnchor, '(?i)(?:^|[;；,，\s])candidateEvidenceHead=([a-f0-9]{40})(?:$|[;；,，\s])')
  if ($candidateEvidenceMatch.Success) { $candidateEvidenceHead = $candidateEvidenceMatch.Groups[1].Value.ToLowerInvariant() }
  $allowedPaths = @(Get-AutopilotCodeValues (Get-AutopilotSectionLines $Block.body '允许修改'))
  if ($allowedPaths.Count -eq 0) { $errors += '允许修改必须包含代码格式路径' }
  $forbiddenPaths = @(Get-AutopilotCodeValues (Get-AutopilotSectionLines $Block.body '禁止修改'))
  Assert-AutopilotReadyScopeContract -IssueId $Block.issueId -AllowedPaths $allowedPaths -ForbiddenPaths $forbiddenPaths
  $commands = @(Get-AutopilotCodeValues (Get-AutopilotSectionLines $Block.body '验证命令'))
  if ($commands.Count -eq 0) { $errors += '验证命令必须包含代码格式命令' }
  foreach ($command in $commands) {
    $entryError = Test-AutopilotValidationEntry $command $RepoRoot
    if ($entryError) { $errors += $entryError }
  }
  if ($errors.Count -gt 0) { throw "$($Block.issueId) Ready contract failed: $($errors -join '; ')" }
  return [pscustomobject]@{
    issueId = $Block.issueId
    title = $Block.title
    body = $Block.body
    rawBlock = $Block.rawBlock
    readyContentHash = Get-AutopilotSha256 $Block.rawBlock
    taskNature = $nature
    goal = @(Get-AutopilotSectionLines $Block.body '目标')
    nonGoals = @(Get-AutopilotSectionLines $Block.body '非目标')
    acceptanceCriteria = @(Get-AutopilotSectionLines $Block.body '验收标准')
    allowedPaths = $allowedPaths
    forbiddenPaths = $forbiddenPaths
    validationCommands = $commands
    migration = $migration
    dependencies = Get-AutopilotFieldValue $Block.body '依赖'
    riskLevel = $risk
    runtimeRequirement = Get-AutopilotFieldValue $Block.body '运行态要求'
    reviewerRequirement = Get-AutopilotFieldValue $Block.body 'Reviewer要求'
    archiveReport = (Get-AutopilotFieldValue $Block.body '归档报告').Trim('`')
    sourceAnchor = $sourceAnchor
    candidateEvidenceHead = $candidateEvidenceHead
  }
}

function Get-AutopilotReadyIssues {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$RepoRoot)
  $issues = @()
  foreach ($block in @(Get-AutopilotIssueBlocks $Path)) {
    if ((Get-AutopilotFieldValue $block.body '状态') -ceq 'Ready') { $issues += ConvertTo-AutopilotReadyIssue $block $RepoRoot }
  }
  return $issues
}
