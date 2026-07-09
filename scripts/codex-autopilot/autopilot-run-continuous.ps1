param(
  [string]$RepoRoot = "D:\projects-test\cgc-pms",
  [string]$ConfigPath = "",
  [switch]$DryRun,
  [int]$MaxLoops = 20
)

$ErrorActionPreference = "Stop"

function Read-JsonFile {
  param([string]$Path)
  if (!(Test-Path $Path)) {
    throw "Config not found: $Path"
  }
  return Get-Content -Raw $Path | ConvertFrom-Json
}

function Test-Checkpoint {
  param([string]$AutoDir)

  if (!(Test-Path (Join-Path $AutoDir "enabled.flag"))) {
    return "STOP_DISABLED"
  }
  if (Test-Path (Join-Path $AutoDir "pause.flag")) {
    return "STOP_PAUSED"
  }
  if (Test-Path (Join-Path $AutoDir "stop.flag")) {
    return "STOP_REQUESTED"
  }
  return "CONTINUE"
}

function Get-ReadyIssues {
  param([string]$ReadyPath)

  if (!(Test-Path $ReadyPath)) {
    return @()
  }

  $text = Get-Content -Raw $ReadyPath
  $matches = [regex]::Matches($text, "(?ms)^###\s+(ISSUE-[0-9-]+[^\r\n]*)\r?\n(.*?)(?=^###\s+ISSUE-|\z)")
  $issues = @()
  foreach ($match in $matches) {
    $body = $match.Groups[2].Value
    $statusMatch = [regex]::Match($body, "(?m)^状态[：:]\s*(.+?)\s*$")
    $status = if ($statusMatch.Success) { $statusMatch.Groups[1].Value.Trim() } else { "" }
    $hasValidation = $body -match "(?m)^验证命令[：:]"
    if ($status -notmatch "^(Done|Blocked|已完成|阻塞)" -and $hasValidation) {
      $issues += [pscustomobject]@{
        title = $match.Groups[1].Value.Trim()
        status = $status
      }
    }
  }
  return $issues
}

function Get-SplitCandidates {
  param(
    [string]$PlanPath,
    [int]$Limit
  )

  if (!(Test-Path $PlanPath)) {
    return @()
  }

  $forbidden = "财务|生产数据库|生产发布|总工程师|BIM|AI"
  $allowed = "报表|规则治理|通知|WBS|进度|甘特图|供应商|采购增强"
  $candidates = @()
  foreach ($line in Get-Content $PlanPath) {
    if ($line -match "^#{2,3}\s+(.+)$") {
      $title = $matches[1].Trim()
      if ($title -match $allowed -and $title -notmatch $forbidden) {
        $candidates += $title
      }
    }
    if ($candidates.Count -ge $Limit) {
      break
    }
  }
  return $candidates
}

function Write-State {
  param(
    [string]$AutoDir,
    [string]$Status,
    [bool]$DryRunMode
  )

  if ($DryRunMode) {
    return
  }

  $statePath = Join-Path $AutoDir "state.json"
  $now = Get-Date -Format s
  if (Test-Path $statePath) {
    $state = Get-Content -Raw $statePath | ConvertFrom-Json
  } else {
    $state = [pscustomobject]@{}
  }
  $state | Add-Member -NotePropertyName status -NotePropertyValue $Status -Force
  $state | Add-Member -NotePropertyName lastHeartbeatAt -NotePropertyValue $now -Force
  $state | ConvertTo-Json | Out-File -Encoding utf8 $statePath
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (!$ConfigPath) {
  $ConfigPath = Join-Path $scriptDir "codex-autopilot.config.json"
}
$config = Read-JsonFile $ConfigPath
if ($config.repoRoot) {
  $RepoRoot = $config.repoRoot
}

$autoDir = if ($config.autopilotDir) { $config.autopilotDir } else { Join-Path $RepoRoot ".codex-autopilot" }
$maxIssuesPerRun = if ($config.maxIssuesPerRun) { [int]$config.maxIssuesPerRun } else { 1 }
if ($maxIssuesPerRun -ne 1) {
  throw "Continuous runner requires maxIssuesPerRun=1, actual=$maxIssuesPerRun"
}

$readyPath = Join-Path $RepoRoot "docs\backlog\ready-issues.md"
$focusPath = Join-Path $RepoRoot "docs\backlog\current-focus.md"
$planPath = Join-Path $RepoRoot "docs\backlog\cgc-pms-production-enhancement-plan.md"
$splitLimit = 5

Write-Host "CGC-PMS AutoPilot continuous runner"
Write-Host "repoRoot=$RepoRoot"
Write-Host "maxIssuesPerRun=$maxIssuesPerRun"
Write-Host "autoPush=$($config.autoPush)"
Write-Host "dryRun=$([bool]$DryRun)"

for ($loop = 1; $loop -le $MaxLoops; $loop++) {
  $checkpoint = Test-Checkpoint $autoDir
  Write-Host "checkpoint[$loop]=$checkpoint"
  if ($checkpoint -ne "CONTINUE") {
    Write-State $autoDir $checkpoint ([bool]$DryRun)
    Write-Host $checkpoint
    exit 0
  }

  $readyIssues = @(Get-ReadyIssues $readyPath)
  if ($readyIssues.Count -gt 0) {
    Write-State $autoDir "READY_ISSUE_FOUND" ([bool]$DryRun)
    Write-Host "READY_ISSUE_FOUND"
    Write-Host "selected=$($readyIssues[0].title)"
    Write-Host "BUSINESS_EXECUTION_NOT_STARTED"
    exit 0
  }

  Write-Host "SPLIT_MODE"
  Write-Host "focusPath=$focusPath"
  $candidates = @(Get-SplitCandidates $planPath $splitLimit)
  if ($candidates.Count -eq 0) {
    Write-State $autoDir "STOP_NO_READY_OR_SPLIT_CANDIDATE" ([bool]$DryRun)
    Write-Host "STOP_NO_READY_OR_SPLIT_CANDIDATE"
    exit 0
  }

  Write-Host "splitCandidateCount=$($candidates.Count)"
  for ($index = 0; $index -lt $candidates.Count; $index++) {
    Write-Host ("splitCandidate[{0}]={1}" -f ($index + 1), $candidates[$index])
  }

  if ($DryRun) {
    Write-State $autoDir "DRY_RUN_SPLIT_PLANNED" $true
    Write-Host "DRY_RUN_NO_BACKLOG_WRITE"
    exit 0
  }

  Write-State $autoDir "SPLIT_REQUIRED_MANUAL_OWNER" $false
  Write-Host "SPLIT_REQUIRED_MANUAL_OWNER"
  Write-Host "No Ready Issue was generated automatically; project owner must write docs/backlog/ready-issues.md."

  $checkpoint = Test-Checkpoint $autoDir
  Write-Host "postSplitCheckpoint=$checkpoint"
  if ($checkpoint -ne "CONTINUE") {
    Write-Host $checkpoint
    exit 0
  }

  $readyIssues = @(Get-ReadyIssues $readyPath)
  if ($readyIssues.Count -gt 0) {
    continue
  }

  Write-Host "STOP_NO_READY_OR_SPLIT_CANDIDATE"
  exit 0
}

Write-Host "STOP_MAX_LOOPS"
