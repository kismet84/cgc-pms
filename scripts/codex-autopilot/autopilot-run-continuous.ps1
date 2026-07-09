param(
  [string]$RepoRoot = "D:\projects-test\cgc-pms",
  [string]$ConfigPath = "",
  [switch]$DryRun,
  [switch]$ApplyBacklogSplit,
  [switch]$ExplainNextAction,
  [Nullable[int]]$MaxIterations = $null,
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
    return "STOP_PAUSE_FLAG"
  }
  if (Test-Path (Join-Path $AutoDir "stop.flag")) {
    return "STOP_STOP_FLAG"
  }
  return "CONTINUE"
}

function Read-RunLock {
  param([string]$LockPath)

  if (!(Test-Path $LockPath)) {
    return $null
  }
  try {
    return Get-Content -Raw $LockPath | ConvertFrom-Json
  } catch {
    return [pscustomobject]@{
      owner = "unknown"
      pid = $null
      startedAt = $null
      heartbeatAt = $null
      mode = "unknown"
      issueId = ""
      unreadable = $true
    }
  }
}

function Test-RunLockStale {
  param(
    [object]$Lock,
    [int]$MaxRunMinutes
  )

  if (!$Lock) {
    return $false
  }
  if ($Lock.unreadable) {
    return $true
  }

  [datetime]$heartbeat = [datetime]::MinValue
  if ($Lock.heartbeatAt -and [datetime]::TryParse([string]$Lock.heartbeatAt, [ref]$heartbeat)) {
    if (((Get-Date) - $heartbeat).TotalMinutes -gt $MaxRunMinutes) {
      return $true
    }
  }

  if ($Lock.pid) {
    $process = Get-Process -Id ([int]$Lock.pid) -ErrorAction SilentlyContinue
    if (!$process) {
      return $true
    }
  }
  return $false
}

function Write-RunLock {
  param(
    [string]$LockPath,
    [object]$Lock
  )

  $Lock.heartbeatAt = Get-Date -Format o
  $Lock | ConvertTo-Json -Depth 5 | Out-File -Encoding utf8 $LockPath
}

function New-RunLock {
  param(
    [string]$AutoDir,
    [int]$MaxRunMinutes,
    [string]$Mode
  )

  if (!(Test-Path $AutoDir)) {
    New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null
  }

  $lockPath = Join-Path $AutoDir "run.lock"
  $existing = Read-RunLock $lockPath
  if ($existing) {
    if (Test-RunLockStale $existing $MaxRunMinutes) {
      Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
      Write-Host "STALE_RUN_LOCK_REMOVED"
    } else {
      Write-Host "RUN_LOCK_ACTIVE"
      Write-Host "lockOwner=$($existing.owner)"
      Write-Host "lockPid=$($existing.pid)"
      Write-Host "lockHeartbeatAt=$($existing.heartbeatAt)"
      throw "Another AutoPilot run is active."
    }
  }

  $stream = $null
  try {
    $stream = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    $lock = [pscustomobject]@{
      owner = "$env:USERNAME@$env:COMPUTERNAME"
      pid = $PID
      startedAt = Get-Date -Format o
      heartbeatAt = Get-Date -Format o
      mode = $Mode
      issueId = ""
    }
    $json = $lock | ConvertTo-Json -Depth 5
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $stream.Write($bytes, 0, $bytes.Length)
    Write-Host "RUN_LOCK_ACQUIRED"
    return $lock
  } catch {
    throw "Failed to acquire run.lock: $($_.Exception.Message)"
  } finally {
    if ($stream) {
      $stream.Dispose()
    }
  }
}

function Remove-RunLock {
  param(
    [string]$AutoDir,
    [object]$Lock
  )

  if (!$Lock) {
    return
  }

  $lockPath = Join-Path $AutoDir "run.lock"
  $existing = Read-RunLock $lockPath
  if ($existing -and $existing.pid -eq $Lock.pid -and $existing.startedAt -eq $Lock.startedAt) {
    Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
    Write-Host "RUN_LOCK_RELEASED"
  }
}

function Get-ReadyIssues {
  param([string]$ReadyPath, [string]$RepoRoot, [string]$ScriptDir)

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
    if ($status -ceq "Ready") {
      $title = $match.Groups[1].Value.Trim()
      $lint = Invoke-ReadyLint $RepoRoot $ReadyPath $ScriptDir $title
      $issues += [pscustomobject]@{
        title = $title
        status = $status
        lint = $lint
      }
    }
  }
  return $issues
}

function Invoke-ReadyLint {
  param(
    [string]$RepoRoot,
    [string]$ReadyPath,
    [string]$ScriptDir,
    [string]$IssueTitle
  )

  $lintPath = Join-Path $ScriptDir "ready-lint.ps1"
  if (!(Test-Path $lintPath)) {
    return [pscustomobject]@{
      status = "fail"
      issueId = ""
      title = $IssueTitle
      errors = @("ready-lint.ps1 不存在")
      warnings = @()
    }
  }

  $output = & powershell -NoProfile -ExecutionPolicy Bypass -File $lintPath -RepoRoot $RepoRoot -ReadyPath $ReadyPath -IssueTitle $IssueTitle 2>&1 | Out-String
  try {
    return $output | ConvertFrom-Json
  } catch {
    return [pscustomobject]@{
      status = "fail"
      issueId = ""
      title = $IssueTitle
      errors = @("ready-lint 输出不是 JSON：$output")
      warnings = @()
    }
  }
}

function Get-SplitCandidates {
  param(
    [string]$FocusPath,
    [string]$PlanPath,
    [string]$ReadyPath,
    [int]$Limit
  )

  if (!(Test-Path $FocusPath) -or !(Test-Path $PlanPath)) {
    return @()
  }

  $focus = Get-Content -Raw $FocusPath
  $ready = if (Test-Path $ReadyPath) { Get-Content -Raw $ReadyPath } else { "" }
  $forbidden = "财务|生产数据库|生产发布|总工程师|BIM|AI"
  $allowed = "报表|规则治理|通知|WBS|进度|甘特图|供应商|采购增强"
  $candidates = @()
  foreach ($line in Get-Content $PlanPath) {
    if ($line -match "^#{2,3}\s+([0-9]+\.[0-9]+)\s+(.+)$") {
      $section = $matches[1].Trim()
      $name = $matches[2].Trim()
      $anchor = "$section $name"
      $anchorToken = '`' + $anchor + '`'
      if ($anchor -match $allowed -and $anchor -notmatch $forbidden -and $focus -match $allowed -and $ready -notmatch [regex]::Escape($anchorToken)) {
        $candidates += [pscustomobject]@{
          section = $section
          name = $name
          anchor = $anchor
        }
      }
    }
    if ($candidates.Count -ge $Limit) {
      break
    }
  }
  return $candidates
}

function Get-NextIssueId {
  param(
    [string]$ReadyText,
    [string]$Section,
    [int]$Offset
  )

  $prefix = "{0:000}" -f [int]($Section.Split(".")[0])
  $max = 0
  foreach ($match in [regex]::Matches($ReadyText, "ISSUE-$prefix-([0-9]+)")) {
    $max = [Math]::Max($max, [int]$match.Groups[1].Value)
  }
  $suffix = "{0:000}" -f ($max + $Offset)
  return "ISSUE-$prefix-$suffix"
}

function New-ReadyIssueDraft {
  param(
    [pscustomobject]$Candidate,
    [string]$IssueId
  )

  $slug = ($Candidate.name -replace "[^\p{L}\p{Nd}]+", "-").Trim("-").ToLowerInvariant()
  @"

### ${IssueId}：$($Candidate.name) 最小可行回归

优先级：P2
类型：生产增强 / 回归 / 最小实现
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``$($Candidate.anchor)`` 节
是否需要新增 migration：否；如执行中确认必须新增表/字段，先转 Blocked 并回报人工裁决。
目标：
- 基于现有架构补齐“$($Candidate.name)”的一轮最小可验收能力或回归断言。
- 不扩大为完整平台化改造，不连接生产环境。
允许修改：
- ``backend/**``
- ``frontend-admin/**``
- ``docs/quality/**``
- ``docs/iterations/**``
- ``docs/backlog/**``
禁止修改：
- 已应用 Flyway migration
- 生产凭据、生产数据库连接、生产发布配置
- 与本 Issue 无关的大范围重构或新依赖
验收标准：
- 至少留下一个能证明核心口径或页面/接口行为的自动化验证。
- 不放宽现有鉴权、租户、项目边界。
- 更新 iteration 或 quality 报告，并同步 backlog 状态。
验证命令：
- ``cd backend; .\mvnw.cmd test``
- ``cd frontend-admin; pnpm type-check``
- ``git diff --check``
归档报告：``docs/quality/$($IssueId.ToLowerInvariant())-$slug.md``
"@
}

function Add-ReadyIssueDrafts {
  param(
    [string]$ReadyPath,
    [object[]]$Candidates
  )

  if ($Candidates.Count -eq 0) {
    return 0
  }

  $readyText = if (Test-Path $ReadyPath) { Get-Content -Raw $ReadyPath } else { "# Ready Issues`r`n" }
  $drafts = @()
  for ($index = 0; $index -lt $Candidates.Count; $index++) {
    $issueId = Get-NextIssueId $readyText $Candidates[$index].section ($index + 1)
    $drafts += New-ReadyIssueDraft $Candidates[$index] $issueId
  }

  $appendText = ($drafts -join "`r`n")
  Add-Content -Encoding utf8 -Path $ReadyPath -Value $appendText
  return $Candidates.Count
}

function Write-State {
  param(
    [string]$AutoDir,
    [string]$Status,
    [bool]$DryRunMode,
    [string]$LastAction = $Status,
    [string]$LastIssue = "",
    [string]$LastReason = $Status,
    [string]$StopReason = ""
  )

  if ($DryRunMode) {
    return
  }

  if ($script:RunLock) {
    Write-RunLock (Join-Path $AutoDir "run.lock") $script:RunLock
  }

  $statePath = Join-Path $AutoDir "state.json"
  $now = Get-Date -Format s
  if (Test-Path $statePath) {
    $state = Get-Content -Raw $statePath | ConvertFrom-Json
  } else {
    $state = [pscustomobject]@{}
  }
  $state | Add-Member -NotePropertyName status -NotePropertyValue $Status -Force
  $state | Add-Member -NotePropertyName mode -NotePropertyValue "continuous-runner" -Force
  $state | Add-Member -NotePropertyName lastAction -NotePropertyValue $LastAction -Force
  $state | Add-Member -NotePropertyName lastIssue -NotePropertyValue $LastIssue -Force
  $state | Add-Member -NotePropertyName lastReason -NotePropertyValue $LastReason -Force
  $state | Add-Member -NotePropertyName stopReason -NotePropertyValue $StopReason -Force
  $state | Add-Member -NotePropertyName iterationLimit -NotePropertyValue $script:IterationLimit -Force
  $state | Add-Member -NotePropertyName iterationCompleted -NotePropertyValue $script:IterationCompleted -Force
  $state | Add-Member -NotePropertyName remainingIterations -NotePropertyValue $script:RemainingIterations -Force
  $state | Add-Member -NotePropertyName iterationLastCountedIssue -NotePropertyValue $script:IterationLastCountedIssue -Force
  $state | Add-Member -NotePropertyName lastHeartbeatAt -NotePropertyValue $now -Force
  $state | ConvertTo-Json | Out-File -Encoding utf8 $statePath
}

function Get-IssueBodyByTitle {
  param(
    [string]$ReadyPath,
    [string]$Title
  )

  if (!$Title -or !(Test-Path $ReadyPath)) {
    return ""
  }

  $text = Get-Content -Raw $ReadyPath
  $pattern = "(?ms)^###\s+" + [regex]::Escape($Title) + "\r?\n(.*?)(?=^###\s+ISSUE-|\z)"
  $match = [regex]::Match($text, $pattern)
  if (!$match.Success) {
    return ""
  }
  return $match.Groups[1].Value
}

function Test-IssueCompleted {
  param(
    [string]$ReadyPath,
    [string]$Title
  )

  $body = Get-IssueBodyByTitle $ReadyPath $Title
  if (!$body) {
    return $false
  }

  $statusMatch = [regex]::Match($body, "(?m)^状态[：:]\s*(.+?)\s*$")
  if (!$statusMatch.Success) {
    return $false
  }

  return $statusMatch.Groups[1].Value.Trim() -match "^(Done|Blocked|已完成|阻塞|Iteration|Iterated|已迭代|迭代完成)\b"
}

function Initialize-IterationProgress {
  param(
    [string]$AutoDir,
    [string]$ReadyPath,
    [Nullable[int]]$Limit,
    [bool]$ReadOnlyMode
  )

  $script:IterationLimit = $null
  $script:IterationCompleted = 0
  $script:RemainingIterations = $null
  $script:IterationLastCountedIssue = ""

  if ($null -eq $Limit) {
    return
  }

  if ($Limit -lt 1 -or $Limit -gt 50) {
    throw "MaxIterations must be a positive integer between 1 and 50."
  }

  $script:IterationLimit = [int]$Limit
  $statePath = Join-Path $AutoDir "state.json"
  if (Test-Path $statePath) {
    $state = Get-Content -Raw $statePath | ConvertFrom-Json
    if ($null -ne $state.iterationCompleted) {
      $script:IterationCompleted = [int]$state.iterationCompleted
    }
    if ($state.iterationLastCountedIssue) {
      $script:IterationLastCountedIssue = $state.iterationLastCountedIssue
    }

    if (!$ReadOnlyMode -and $state.lastAction -eq "READY_ISSUE_FOUND" -and $state.lastIssue -and $state.iterationLastCountedIssue -ne $state.lastIssue) {
      if (Test-IssueCompleted $ReadyPath $state.lastIssue) {
        $script:IterationCompleted += 1
        $script:IterationLastCountedIssue = $state.lastIssue
      }
    }
  }

  $script:RemainingIterations = [Math]::Max(0, $script:IterationLimit - $script:IterationCompleted)
}

function Get-StopReasonForEmptyPool {
  param([string]$ReadyPath)

  if (!(Test-Path $ReadyPath)) {
    return "STOP_READY_AND_POOL_EMPTY"
  }

  $text = Get-Content -Raw $ReadyPath
  if ($text -match "(?m)^状态[：:]\s*(Blocked|阻塞)\b") {
    return "STOP_CURRENT_ISSUE_BLOCKED"
  }
  return "STOP_READY_AND_POOL_EMPTY"
}

function Write-NextActionExplanation {
  param(
    [string]$Checkpoint,
    [object[]]$ReadyIssues,
    [object[]]$Candidates,
    [string]$StopReason
  )

  Write-Host "EXPLAIN_NEXT_ACTION"
  if ($Checkpoint -ne "CONTINUE") {
    Write-Host "nextAction=STOP"
    Write-Host "stopReason=$Checkpoint"
    return
  }

  if ($ReadyIssues.Count -gt 0) {
    if ($ReadyIssues[0].lint.status -ne "pass") {
      Write-Host "nextAction=STOP"
      Write-Host "stopReason=STOP_READY_LINT_FAILED"
      Write-Host "missingGate=ready-lint"
      foreach ($errorItem in @($ReadyIssues[0].lint.errors)) {
        Write-Host "lintError=$errorItem"
      }
      return
    }
    Write-Host "nextAction=READY_ISSUE"
    Write-Host "nextReady=$($ReadyIssues[0].title)"
    return
  }

  if ($Candidates.Count -gt 0) {
    Write-Host "nextAction=SPLIT_BACKLOG"
    Write-Host "wouldCreateReadyIssueDrafts=$($Candidates.Count)"
    for ($index = 0; $index -lt $Candidates.Count; $index++) {
      Write-Host ("splitCandidate[{0}]={1}" -f ($index + 1), $Candidates[$index].anchor)
    }
    return
  }

  Write-Host "nextAction=STOP"
  Write-Host "stopReason=$StopReason"
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
$maxRunMinutes = if ($config.maxRunMinutes) { [int]$config.maxRunMinutes } else { 120 }
if ($maxIssuesPerRun -ne 1) {
  throw "Continuous runner requires maxIssuesPerRun=1, actual=$maxIssuesPerRun"
}

$readyPath = Join-Path $RepoRoot "docs\backlog\ready-issues.md"
$focusPath = Join-Path $RepoRoot "docs\backlog\current-focus.md"
$planPath = Join-Path $RepoRoot "docs\backlog\cgc-pms-production-enhancement-plan.md"
$splitLimit = 5
$applyMode = [bool]$ApplyBacklogSplit -and -not [bool]$DryRun -and -not [bool]$ExplainNextAction
$dryRunMode = -not $applyMode
$script:RunLock = $null
Initialize-IterationProgress $autoDir $readyPath $MaxIterations ([bool]$DryRun -or [bool]$ExplainNextAction)

Write-Host "CGC-PMS AutoPilot continuous runner"
Write-Host "repoRoot=$RepoRoot"
Write-Host "maxIssuesPerRun=$maxIssuesPerRun"
Write-Host "iterationLimit=$script:IterationLimit"
Write-Host "iterationCompleted=$script:IterationCompleted"
Write-Host "remainingIterations=$script:RemainingIterations"
Write-Host "autoPush=$($config.autoPush)"
Write-Host "dryRun=$dryRunMode"
Write-Host "applyBacklogSplit=$applyMode"

if ($ExplainNextAction) {
  $checkpoint = Test-Checkpoint $autoDir
  $readyIssues = if ($checkpoint -eq "CONTINUE") { @(Get-ReadyIssues $readyPath $RepoRoot $scriptDir) } else { @() }
  $candidates = if ($checkpoint -eq "CONTINUE" -and $readyIssues.Count -eq 0) { @(Get-SplitCandidates $focusPath $planPath $readyPath $splitLimit) } else { @() }
  $stopReason = if ($checkpoint -eq "CONTINUE" -and $readyIssues.Count -eq 0 -and $candidates.Count -eq 0) { Get-StopReasonForEmptyPool $readyPath } else { $checkpoint }
  Write-NextActionExplanation $checkpoint $readyIssues $candidates $stopReason
  exit 0
}

try {
  if ($applyMode) {
    $script:RunLock = New-RunLock $autoDir $maxRunMinutes "apply-backlog-split"
  }

  if ($null -ne $script:IterationLimit -and $script:IterationCompleted -ge $script:IterationLimit) {
    Write-State $autoDir "STOP_ITERATION_LIMIT_REACHED" ([bool]$DryRun) "STOP" "" "STOP_ITERATION_LIMIT_REACHED" "STOP_ITERATION_LIMIT_REACHED"
    Write-Host "STOP_ITERATION_LIMIT_REACHED"
    exit 0
  }

  for ($loop = 1; $loop -le $MaxLoops; $loop++) {
    $checkpoint = Test-Checkpoint $autoDir
    Write-Host "checkpoint[$loop]=$checkpoint"
    if ($checkpoint -ne "CONTINUE") {
      Write-State $autoDir $checkpoint ([bool]$DryRun) "STOP" "" $checkpoint $checkpoint
      Write-Host $checkpoint
      exit 0
    }

    $readyIssues = @(Get-ReadyIssues $readyPath $RepoRoot $scriptDir)
    if ($readyIssues.Count -gt 0) {
      if ($readyIssues[0].lint.status -ne "pass") {
        Write-State $autoDir "STOP_READY_LINT_FAILED" ([bool]$DryRun) "STOP" $readyIssues[0].title "STOP_READY_LINT_FAILED" "STOP_READY_LINT_FAILED"
        Write-Host "STOP_READY_LINT_FAILED"
        Write-Host "selected=$($readyIssues[0].title)"
        Write-Host "missingGate=ready-lint"
        foreach ($errorItem in @($readyIssues[0].lint.errors)) {
          Write-Host "lintError=$errorItem"
        }
        exit 0
      }
      if ($script:RunLock) {
        $script:RunLock.issueId = $readyIssues[0].title
      }
      Write-State $autoDir "READY_ISSUE_FOUND" ([bool]$DryRun) "READY_ISSUE_FOUND" $readyIssues[0].title "READY_ISSUE_FOUND" ""
      Write-Host "READY_ISSUE_FOUND"
      Write-Host "selected=$($readyIssues[0].title)"
      Write-Host "BUSINESS_EXECUTION_NOT_STARTED"
      exit 0
    }

    Write-Host "SPLIT_MODE"
    Write-Host "focusPath=$focusPath"
    $candidates = @(Get-SplitCandidates $focusPath $planPath $readyPath $splitLimit)
    if ($candidates.Count -eq 0) {
      $stopReason = Get-StopReasonForEmptyPool $readyPath
      Write-State $autoDir $stopReason $dryRunMode "STOP" "" $stopReason $stopReason
      Write-Host $stopReason
      exit 0
    }

    Write-Host "splitCandidateCount=$($candidates.Count)"
    for ($index = 0; $index -lt $candidates.Count; $index++) {
      Write-Host ("splitCandidate[{0}]={1}" -f ($index + 1), $candidates[$index].anchor)
    }

    if ($dryRunMode) {
      Write-State $autoDir "DRY_RUN_SPLIT_PLANNED" $true "SPLIT_BACKLOG" "" "DRY_RUN_SPLIT_PLANNED" ""
      Write-Host "DRY_RUN_NO_BACKLOG_WRITE"
      exit 0
    }

    $createdCount = Add-ReadyIssueDrafts $readyPath $candidates
    Write-State $autoDir "BACKLOG_SPLIT_APPLIED" $false "BACKLOG_SPLIT_APPLIED" "" "BACKLOG_SPLIT_APPLIED" ""
    Write-Host "BACKLOG_SPLIT_APPLIED"
    Write-Host "createdReadyIssueDrafts=$createdCount"

    $checkpoint = Test-Checkpoint $autoDir
    Write-Host "postSplitCheckpoint=$checkpoint"
    if ($checkpoint -ne "CONTINUE") {
      Write-State $autoDir $checkpoint $false "STOP" "" $checkpoint $checkpoint
      Write-Host $checkpoint
      exit 0
    }

    $readyIssues = @(Get-ReadyIssues $readyPath $RepoRoot $scriptDir)
    if ($readyIssues.Count -gt 0) {
      continue
    }

    $stopReason = Get-StopReasonForEmptyPool $readyPath
    Write-State $autoDir $stopReason $false "STOP" "" $stopReason $stopReason
    Write-Host $stopReason
    exit 0
  }

  Write-State $autoDir "STOP_SESSION_LIMIT" $dryRunMode "STOP" "" "STOP_SESSION_LIMIT" "STOP_SESSION_LIMIT"
  Write-Host "STOP_SESSION_LIMIT"
} finally {
  Remove-RunLock $autoDir $script:RunLock
}
