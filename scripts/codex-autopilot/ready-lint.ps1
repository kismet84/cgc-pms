param(
  [string]$RepoRoot = 'D:\projects-test\cgc-pms',
  [string]$ReadyPath = '',
  [string]$IssueTitle = ''
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-ready.ps1')
if (!$ReadyPath) { $ReadyPath = Join-Path $RepoRoot 'docs\backlog\ready-issues.md' }

$targetId = ([regex]::Match($IssueTitle, '^(ISSUE-[0-9-]+)')).Groups[1].Value
try {
  $issues = @(Get-AutopilotReadyIssues -Path $ReadyPath -RepoRoot $RepoRoot)
  $issue = if ($IssueTitle) { $issues | Where-Object { $_.title -eq $IssueTitle -or $_.issueId -eq $IssueTitle -or $_.issueId -eq $targetId } | Select-Object -First 1 } else { $issues | Select-Object -First 1 }
  if (!$issue) { throw 'Ready Issue 不存在或状态不是 Ready' }
  [pscustomobject]@{
    status = 'pass'; issueId = $issue.issueId; title = $issue.title
    readyContentHash = $issue.readyContentHash; taskNature = $issue.taskNature; riskLevel = $issue.riskLevel
    errors = @(); warnings = @()
  } | ConvertTo-Json -Depth 6
} catch {
  [pscustomobject]@{
    status = 'fail'; issueId = $targetId; title = $IssueTitle
    readyContentHash = ''; taskNature = ''; riskLevel = ''
    errors = @($_.Exception.Message); warnings = @()
  } | ConvertTo-Json -Depth 6
  exit 1
}
