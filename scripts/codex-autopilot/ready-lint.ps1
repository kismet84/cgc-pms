param(
  [string]$RepoRoot = "D:\projects-test\cgc-pms",
  [string]$ReadyPath = "",
  [string]$IssueTitle = ""
)

$ErrorActionPreference = "Stop"
$QuoteChars = [char[]]@([char]34, [char]39)

if (!$ReadyPath) {
  $ReadyPath = Join-Path $RepoRoot "docs\backlog\ready-issues.md"
}

function Get-IssueBlocks {
  param([string]$Path)

  if (!(Test-Path $Path)) {
    return @()
  }

  $text = Get-Content -Raw $Path
  $matches = [regex]::Matches($text, "(?ms)^###\s+(ISSUE-[0-9-]+[^\r\n]*)\r?\n(.*?)(?=^###\s+ISSUE-|\z)")
  $issues = @()
  foreach ($match in $matches) {
    $title = $match.Groups[1].Value.Trim()
    $idMatch = [regex]::Match($title, "^(ISSUE-[0-9-]+)")
    $issues += [pscustomobject]@{
      issueId = if ($idMatch.Success) { $idMatch.Groups[1].Value } else { "" }
      title = $title
      body = $match.Groups[2].Value
    }
  }
  return $issues
}

function Get-FieldValue {
  param([string]$Body, [string]$Name)

  $match = [regex]::Match($Body, "(?m)^$([regex]::Escape($Name))[：:]\s*(.+?)\s*$")
  if ($match.Success) {
    return $match.Groups[1].Value.Trim()
  }
  return ""
}

function Test-HasField {
  param([string]$Body, [string]$Name)
  return $Body -match "(?m)^$([regex]::Escape($Name))[：:]"
}

function Get-SectionLines {
  param([string]$Body, [string]$Name)

  $match = [regex]::Match($Body, "(?ms)^$([regex]::Escape($Name))[：:].*?\r?\n(.*?)(?=^[^\r\n：:]{2,20}[：:]|^###\s+ISSUE-|\z)")
  if (!$match.Success) {
    return @()
  }
  return @($match.Groups[1].Value -split "\r?\n" | Where-Object { $_.Trim() })
}

function Get-ValidationCommands {
  param([string]$Body)

  $commands = @()
  foreach ($line in (Get-SectionLines -Body $Body -Name "验证命令")) {
    foreach ($match in [regex]::Matches($line, '``([^``]+)``|`([^`]+)`')) {
      $value = if ($match.Groups[1].Success) { $match.Groups[1].Value } else { $match.Groups[2].Value }
      if ($value.Trim()) {
        $commands += $value.Trim()
      }
    }
  }
  return $commands
}

function Test-ValidationCommandEntry {
  param([string]$Command, [string]$Root)

  $workDir = $Root
  $commandText = $Command.Trim()
  $cdMatch = [regex]::Match($commandText, '^\s*cd\s+([^;&]+)\s*(?:;|&&)\s*(.+)$')
  if ($cdMatch.Success) {
    $target = $cdMatch.Groups[1].Value.Trim().Trim($QuoteChars)
    $workDir = Join-Path $Root $target
    if (!(Test-Path $workDir -PathType Container)) {
      return "验证命令入口不存在：$Command"
    }
    $commandText = $cdMatch.Groups[2].Value.Trim()
  }

  if ($commandText -match "^(?:\.\\|\.\/)?mvnw(?:\.cmd)?\b") {
    if (!(Test-Path (Join-Path $workDir "mvnw.cmd")) -and !(Test-Path (Join-Path $workDir "mvnw"))) {
      return "验证命令入口不存在：$Command"
    }
  } elseif ($commandText -match "^pnpm\b") {
    if (!(Test-Path (Join-Path $workDir "package.json"))) {
      return "验证命令入口不存在：$Command"
    }
  } elseif ($commandText -match "^git\s+diff\s+--check\b") {
    return ""
  } elseif ($commandText -match "(?:-File\s+|^)([^\s]+\.ps1)\b") {
    $scriptPath = $matches[1].Trim($QuoteChars)
    if (!(Test-Path (Join-Path $workDir $scriptPath))) {
      return "验证命令入口不存在：$Command"
    }
  }

  return ""
}

function Test-Issue {
  param([pscustomobject]$Issue, [string]$Root)

  $errors = @()
  $warnings = @()
  $required = @("目标", "允许修改", "禁止修改", "验收标准", "验证命令", "来源锚点", "归档报告")
  foreach ($field in $required) {
    if (!(Test-HasField $Issue.body $field)) {
      $errors += "缺少字段：$field"
    }
  }

  $status = Get-FieldValue $Issue.body "状态"
  if ($status -cne "Ready") {
    $errors += "状态必须精确为 Ready，当前：$status"
  }

  $allowText = (Get-SectionLines $Issue.body "允许修改") -join "`n"
  $validationText = (Get-SectionLines $Issue.body "验证命令") -join "`n"
  $goalText = (Get-SectionLines $Issue.body "目标") -join "`n"
  if ("$allowText`n$validationText`n$goalText" -match "生产发布|生产数据库|已应用\s*Flyway|db/migration|db\\migration") {
    $errors += "出现越界词：生产发布/生产数据库/已应用 Flyway/migration"
  }

  $commands = @(Get-ValidationCommands $Issue.body)
  if ($commands.Count -eq 0) {
    $errors += "缺少可预检的验证命令"
  }
  foreach ($command in $commands) {
    $entryError = Test-ValidationCommandEntry $command $Root
    if ($entryError) {
      $errors += $entryError
    }
  }

  return [pscustomobject]@{
    status = if ($errors.Count -eq 0) { "pass" } else { "fail" }
    issueId = $Issue.issueId
    title = $Issue.title
    errors = @($errors)
    warnings = @($warnings)
  }
}

$issues = @(Get-IssueBlocks $ReadyPath)
$issue = $null
if ($IssueTitle) {
  $issue = $issues | Where-Object { $_.title -eq $IssueTitle -or $_.issueId -eq $IssueTitle } | Select-Object -First 1
} else {
  $issue = $issues | Where-Object { (Get-FieldValue $_.body "状态") -cne "Done" } | Select-Object -First 1
}

if (!$issue) {
  [pscustomobject]@{
    status = "fail"
    issueId = ""
    title = $IssueTitle
    errors = @("Ready Issue 不存在")
    warnings = @()
  } | ConvertTo-Json -Depth 5
  exit 1
}

$result = Test-Issue $issue $RepoRoot
$result | ConvertTo-Json -Depth 5
if ($result.status -ne "pass") {
  exit 1
}
