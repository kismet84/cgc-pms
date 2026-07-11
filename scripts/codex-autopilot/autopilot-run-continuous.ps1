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
      runId = if ($script:RunContext) { $script:RunContext.id } else { '' }
      host = $env:COMPUTERNAME
      workspaceRoot = $RepoRoot
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

  if (!(Test-Path $ReadyPath)) { return @() }
  try {
    return @(Get-AutopilotReadyIssues -Path $ReadyPath -RepoRoot $RepoRoot | ForEach-Object {
      [pscustomobject]@{
        title = $_.title; status = 'Ready'; body = $_.body; contract = $_
        lint = [pscustomobject]@{ status = 'pass'; issueId = $_.issueId; title = $_.title; readyContentHash = $_.readyContentHash; errors = @(); warnings = @() }
      }
    })
  } catch {
    $text = Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8
    $match = [regex]::Match($text, '(?ms)^###\s+(ISSUE-[0-9-]+[^\r\n]*)\r?\n(.*?)(?=^###\s+ISSUE-|\z)')
    if (!$match.Success) { return @() }
    $title = $match.Groups[1].Value.Trim()
    return @([pscustomobject]@{
      title = $title; status = 'Ready'; body = $match.Groups[2].Value; contract = $null
      lint = [pscustomobject]@{ status = 'fail'; issueId = ([regex]::Match($title, '^(ISSUE-[0-9-]+)')).Groups[1].Value; title = $title; readyContentHash = ''; errors = @($_.Exception.Message); warnings = @() }
    })
  }
}

function Get-SectionLines {
  param([string]$Body, [string]$Name)

  $match = [regex]::Match($Body, "(?ms)^$([regex]::Escape($Name))[：:].*?\r?\n(.*?)(?=^[^\r\n：:]{2,20}[：:]|^###\s+ISSUE-|\z)")
  if (!$match.Success) {
    return @()
  }
  return @($match.Groups[1].Value -split "\r?\n" | Where-Object { $_.Trim() })
}

function Get-BacktickValues {
  param([string]$Text)

  $values = @()
  foreach ($match in [regex]::Matches($Text, '``([^``]+)``|`([^`]+)`')) {
    $value = if ($match.Groups[1].Success) { $match.Groups[1].Value } else { $match.Groups[2].Value }
    if ($value.Trim()) {
      $values += $value.Trim()
    }
  }
  return $values
}

function Get-ConflictKey {
  param([string]$Path)

  $normalized = ($Path -replace "\\", "/").Trim().Trim("/")
  if (!$normalized -or $normalized -match "[*?]") {
    return ""
  }

  $parts = @($normalized -split "/" | Where-Object { $_ })
  if ($parts.Count -eq 0) {
    return ""
  }

  if ($parts[0] -eq "backend") {
    $cgcpmsIndex = [Array]::IndexOf($parts, "cgcpms")
    if ($cgcpmsIndex -ge 0 -and $parts.Count -gt ($cgcpmsIndex + 1)) {
      return "backend/$($parts[$cgcpmsIndex + 1])"
    }
    return ($parts[0..([Math]::Min(2, $parts.Count - 1))] -join "/")
  }

  if ($parts[0] -eq "frontend-admin") {
    if ($parts.Count -ge 4 -and $parts[2] -eq "pages") {
      return "frontend-admin/pages/$($parts[3])"
    }
    if ($parts.Count -ge 5 -and $parts[2] -eq "api" -and $parts[3] -eq "modules") {
      return "frontend-admin/api/modules/$($parts[4])"
    }
    return ($parts[0..([Math]::Min(3, $parts.Count - 1))] -join "/")
  }

  return $normalized.ToLowerInvariant()
}

function Get-ParallelIssueInfo {
  param([pscustomobject]$Issue)

  $body = [string]$Issue.body
  $route = if ($Issue.contract) { Get-AutopilotRoute -Issue $Issue.contract } else { $null }
  $paths = @()
  $paths += Get-BacktickValues ((Get-SectionLines $body "允许修改") -join "`n")
  $paths += Get-BacktickValues ((Get-SectionLines $body "归档报告") -join "`n")
  $paths = @($paths | Where-Object { $_ -match "^(backend|frontend-admin|docs|scripts)/|^(backend|frontend-admin|docs|scripts)\\" } | Select-Object -Unique)
  $concretePaths = @($paths | Where-Object { $_ -notmatch "[*?]" })
  $keys = @($concretePaths | ForEach-Object { Get-ConflictKey $_ } | Where-Object { $_ } | Select-Object -Unique)
  $hasBroadScope = @($paths | Where-Object { $_ -match "[*?]" }).Count -gt 0

  $reason = ""
  $canParallel = $true
  if ($route -and $route.highRisk) {
    $canParallel = $false
    $reason = "SERIAL_HIGH_RISK_DOMAIN"
  } elseif ($hasBroadScope -or $keys.Count -eq 0) {
    $canParallel = $false
    $reason = "SERIAL_UNPROVEN_INDEPENDENCE"
  }

  return [pscustomobject]@{
    issue = $Issue
    canParallel = $canParallel
    reason = $reason
    keys = @($keys)
    paths = @($concretePaths)
  }
}

function Get-ReadyIssueBatchPlan {
  param(
    [object[]]$ReadyIssues,
    [int]$MaxParallelIssues,
    [string]$ParallelSafetyMode
  )

  if ($ReadyIssues.Count -eq 0) {
    return [pscustomobject]@{ issues = [object[]]@(); decision = "EMPTY"; reason = ""; parallel = $false }
  }
  if ($ParallelSafetyMode -ne "strict-independent-only" -or $MaxParallelIssues -le 1) {
    return [pscustomobject]@{ issues = [object[]]@($ReadyIssues[0]); decision = "READY_ISSUE"; reason = "SERIAL_CONFIG"; parallel = $false }
  }

  $batch = @()
  $usedKeys = @{}
  foreach ($issue in $ReadyIssues) {
    if ($issue.lint.status -ne "pass") {
      break
    }
    if ($issue.contract) {
      $route = Get-AutopilotRoute -Issue $issue.contract
      if ($route.serialRequired) {
        return [pscustomobject]@{ issues = [object[]]@($ReadyIssues[0]); decision = "READY_ISSUE"; reason = "SERIAL_RISK_POLICY"; parallel = $false }
      }
    }

    $info = Get-ParallelIssueInfo $issue
    if (!$info.canParallel) {
      $reason = if ($batch.Count -eq 0) { $info.reason } else { "SERIAL_CONFLICT" }
      $issues = if ($batch.Count -eq 0) { [object[]]@($ReadyIssues[0]) } else { [object[]]@($batch) }
      return [pscustomobject]@{ issues = $issues; decision = "READY_ISSUE"; reason = $reason; parallel = $false }
    }

    foreach ($key in @($info.keys)) {
      if ($usedKeys.ContainsKey($key)) {
        $issues = if ($batch.Count -eq 0) { [object[]]@($ReadyIssues[0]) } else { [object[]]@($batch) }
        return [pscustomobject]@{ issues = $issues; decision = "READY_ISSUE"; reason = "SERIAL_CONFLICT"; parallel = $false }
      }
    }

    $batch += $issue
    foreach ($key in @($info.keys)) {
      $usedKeys[$key] = $true
    }
    if ($batch.Count -ge $MaxParallelIssues) {
      break
    }
  }

  if ($batch.Count -gt 1) {
    return [pscustomobject]@{ issues = [object[]]@($batch); decision = "READY_ISSUE_BATCH"; reason = "STRICT_INDEPENDENT"; parallel = $true }
  }
  return [pscustomobject]@{ issues = [object[]]@($ReadyIssues[0]); decision = "READY_ISSUE"; reason = "SERIAL_SINGLE"; parallel = $false }
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
任务性质：回归证明
状态：Ready
自动合并：auto-merge/local-commit-only
来源锚点：``docs/backlog/cgc-pms-production-enhancement-plan.md`` 第 ``$($Candidate.anchor)`` 节
是否需要新增 migration：否；如执行中确认必须新增表/字段，先转 Blocked 并回报人工裁决。
Migration：不需要
依赖：无
风险等级：中
运行态要求：按验收命令判断
Reviewer要求：跨前后端时需要
目标：
- 基于现有架构补齐“$($Candidate.name)”的一轮最小可验收能力或回归断言。
- 不扩大为完整平台化改造，不连接生产环境。
非目标：
- 不新增平台、依赖或生产能力。
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

  $statePath = Join-Path $AutoDir 'state.json'
  $now = [datetimeoffset]::Now.ToString('o')
  $existing = $null
  if (Test-Path -LiteralPath $statePath) {
    try { $existing = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $existing = $null }
  }
  $completedIds = @()
  if ($null -ne $existing -and $existing.PSObject.Properties.Name -contains 'completedIssueIds') { $completedIds = @($existing.completedIssueIds) }
  while ($completedIds.Count -lt $script:IterationCompleted) { $completedIds += "legacy-completed-$($completedIds.Count + 1)" }
  $stateStatus = if ($script:AutopilotStatuses -contains $Status) { $Status } elseif ($Status -eq 'STOP_ITERATION_LIMIT_REACHED') { 'LIMIT_REACHED' } elseif ($Status -eq 'STOP_PAUSE_FLAG') { 'PAUSED' } elseif ($Status -eq 'STOP_DISABLED') { 'DISABLED' } elseif ($Status -like 'STOP*') { 'STOPPED' } elseif ($Status -like 'READY_ISSUE*') { 'PLANNING' } elseif ($Status -like '*SPLIT*') { 'REFILLING' } else { 'CHECKPOINT' }
  $phase = $stateStatus.ToLowerInvariant()
  $state = [ordered]@{
    schemaVersion = 2
    runId = if ($script:RunContext) { $script:RunContext.id } else { 'run-' + [datetimeoffset]::Now.ToString('yyyyMMdd-HHmmss-fff') }
    status = $stateStatus
    phase = $phase
    currentIssue = $LastIssue
    attempt = $script:Attempt
    startedAt = if ($null -ne $existing -and $existing.PSObject.Properties.Name -contains 'startedAt' -and $existing.startedAt) { [string]$existing.startedAt } else { $now }
    phaseStartedAt = $now
    lastHeartbeatAt = $now
    iterationLimit = $script:IterationLimit
    completedImplementationIssues = $script:IterationCompleted
    completedIssueIds = @($completedIds)
    worktree = if ($script:CurrentWorktree) { $script:CurrentWorktree } else { '' }
    branch = if ($script:CurrentBranch) { $script:CurrentBranch } else { (& git -C $RepoRoot branch --show-current 2>$null | Select-Object -First 1) }
    executorPid = $script:ExecutorPid
    lastCommit = $script:LastCommit
    failureFingerprint = $script:FailureFingerprint
    enabled = Test-Path (Join-Path $AutoDir 'enabled.flag')
    mode = 'continuous-runner'
    iterationCompleted = $script:IterationCompleted
    remainingIterations = $script:RemainingIterations
    iterationLastCountedIssue = $script:IterationLastCountedIssue
    lastAction = $LastAction
    lastIssue = $LastIssue
    lastReason = $LastReason
    stopReason = $StopReason
    stopRequested = Test-Path (Join-Path $AutoDir 'stop.flag')
    autoMerge = if ($config.PSObject.Properties.Name -contains 'autoMerge') { [bool]$config.autoMerge } else { $true }
    autoPush = $false
    allowTestDataReset = Test-Path (Join-Path $AutoDir 'ALLOW_TEST_DATA_RESET')
  }
  Write-AutopilotStateAtomic -Path $statePath -State $state | Out-Null
}

function Commit-ReadyRefill {
  param([string]$RepoRoot, [string]$ReadyPath)
  $repoPrefix = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
  $readyFull = [IO.Path]::GetFullPath($ReadyPath)
  if (!$readyFull.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Ready path is outside repository.' }
  $relativeReady = $readyFull.Substring($repoPrefix.Length).Replace('\','/')
  $otherChanges = @(& git -C $RepoRoot status --porcelain=v1 | Where-Object { $_.Substring(3).Trim('"').Replace('\','/') -ne $relativeReady })
  if ($otherChanges.Count -gt 0) { throw 'Cannot commit Ready refill while unrelated worktree changes exist.' }
  & git -C $RepoRoot add -- $relativeReady
  if ($LASTEXITCODE -ne 0) { throw 'Failed to stage Ready refill.' }
  & git -C $RepoRoot diff --cached --check
  if ($LASTEXITCODE -ne 0) { throw 'Ready refill failed git diff --check.' }
  & git -C $RepoRoot commit -m 'chore(autopilot): refill ready queue' | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Failed to commit Ready refill.' }
  return (& git -C $RepoRoot rev-parse HEAD).Trim()
}

function New-RunContext {
  param([string]$AutoDir)

  $runsDir = Join-Path $AutoDir "runs"
  New-Item -ItemType Directory -Path $runsDir -Force | Out-Null
  $runId = "{0}-{1}" -f (Get-Date -Format "yyyyMMdd-HHmmss-fff"), $PID
  $runDir = Join-Path $runsDir $runId
  New-Item -ItemType Directory -Path $runDir -Force | Out-Null
  return [pscustomobject]@{
    id = $runId
    dir = $runDir
    events = Join-Path $runDir "events.jsonl"
  }
}

function Write-RunEvent {
  param(
    [string]$Event,
    [object]$Data = [pscustomobject]@{}
  )

  if (!$script:RunContext) {
    return
  }
  $payload = [ordered]@{
    runId = $script:RunContext.id
    timestamp = Get-Date -Format o
    event = $Event
    mode = "continuous-runner"
    issueId = if ($Data.issueId) { $Data.issueId } else { "" }
    title = if ($Data.title) { $Data.title } else { "" }
    checkpoint = if ($Data.checkpoint) { $Data.checkpoint } else { "" }
    decision = if ($Data.decision) { $Data.decision } else { "" }
    status = if ($Data.status) { $Data.status } else { "" }
    stopReason = if ($Data.stopReason) { $Data.stopReason } else { "" }
    dryRun = [bool]$DryRun
    apply = [bool]$ApplyBacklogSplit
    maxIterations = $script:IterationLimit
    from = if ($Data.from) { $Data.from } else { '' }
    to = if ($Data.to) { $Data.to } elseif ($Data.status) { [string]$Data.status } else { $Event }
    reason = if ($Data.reason) { [string]$Data.reason } elseif ($Data.decision) { [string]$Data.decision } else { $Event }
    evidencePath = if ($Data.evidencePath) { [string]$Data.evidencePath } else { '' }
  }
  foreach ($name in @($Data.PSObject.Properties.Name)) {
    if ($payload.Contains($name)) {
      continue
    }
    $payload[$name] = $Data.$name
  }
  $line = $payload | ConvertTo-Json -Compress -Depth 8
  $line | Add-Content -Encoding utf8 $script:RunContext.events
  [IO.File]::AppendAllText((Join-Path $autoDir 'events.ndjson'), $line + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
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

function Get-IterationProgressFromRuns {
  param(
    [string]$AutoDir,
    [datetime]$StartedAt
  )

  $runsDir = Join-Path $AutoDir "runs"
  if (!(Test-Path $runsDir)) {
    return [pscustomobject]@{
      completedCount = 0
      latestIssueRef = ""
      completedIssueIds = @()
      completedIssueRefs = @()
    }
  }

  $completed = @{}
  $latestCreatedAt = [datetime]::MinValue
  $latestIssueRef = ""

  Get-ChildItem -Path $runsDir -Directory -ErrorAction SilentlyContinue | ForEach-Object {
    $resultPath = Join-Path $_.FullName "result.json"
    if (!(Test-Path $resultPath)) {
      return
    }

    try {
      $result = Get-Content -Raw $resultPath | ConvertFrom-Json
    } catch {
      return
    }

    if (!$result.issueId -or @("done", "blocked") -notcontains [string]$result.status) {
      return
    }

    [datetime]$createdAt = [datetime]::MinValue
    if (![datetime]::TryParse([string]$result.createdAt, [ref]$createdAt)) {
      $createdAt = [datetime]::MinValue
    }
    if ($createdAt -lt $StartedAt) {
      return
    }

    $completed[[string]$result.issueId] = if ($result.title) { [string]$result.title } else { [string]$result.issueId }
    if ($createdAt -ge $latestCreatedAt) {
      $latestCreatedAt = $createdAt
      $latestIssueRef = if ($result.title) { [string]$result.title } else { [string]$result.issueId }
    }
  }

  return [pscustomobject]@{
    completedCount = $completed.Count
    latestIssueRef = $latestIssueRef
    completedIssueIds = @($completed.Keys)
    completedIssueRefs = @($completed.Values)
  }
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
    $statePath = Join-Path $AutoDir 'state.json'
    if (Test-Path -LiteralPath $statePath) {
      $state = Read-AutopilotState -Path $statePath
      $script:IterationCompleted = [Math]::Max([int]$state.completedImplementationIssues, @($state.completedIssueIds).Count)
      if ($state.iterationLastCountedIssue) { $script:IterationLastCountedIssue = [string]$state.iterationLastCountedIssue }
    }
    return
  }

  if ($Limit -lt 1 -or $Limit -gt 50) {
    throw "MaxIterations must be a positive integer between 1 and 50."
  }

  $script:IterationLimit = [int]$Limit
  $statePath = Join-Path $AutoDir "state.json"
  if (Test-Path $statePath) {
    $state = Get-Content -Raw $statePath | ConvertFrom-Json
    $runProgress = [pscustomobject]@{ completedCount = 0; latestIssueRef = ''; completedIssueIds = @(); completedIssueRefs = @() }
    if ($null -ne $state.iterationCompleted) {
      $script:IterationCompleted = [int]$state.iterationCompleted
    }
    if ($state.iterationLastCountedIssue) {
      $script:IterationLastCountedIssue = $state.iterationLastCountedIssue
    }

    [datetime]$startedAt = [datetime]::MinValue
    if ($state.startedAt -and [datetime]::TryParse([string]$state.startedAt, [ref]$startedAt)) {
      $runProgress = Get-IterationProgressFromRuns $AutoDir $startedAt
      if ($runProgress.completedCount -gt $script:IterationCompleted) {
        $script:IterationCompleted = $runProgress.completedCount
        if ($runProgress.latestIssueRef) {
          $script:IterationLastCountedIssue = $runProgress.latestIssueRef
        }
      }
    }

    $lastIssueId = if ($state.lastIssue) { ([regex]::Match([string]$state.lastIssue, '^(ISSUE-[0-9-]+)')).Groups[1].Value } else { '' }
    $alreadyInRuns = $state.lastIssue -in @($runProgress.completedIssueRefs) -or ($lastIssueId -and $lastIssueId -in @($runProgress.completedIssueIds))
    if (!$ReadOnlyMode -and !$alreadyInRuns -and $state.lastAction -eq "READY_ISSUE_FOUND" -and $state.lastIssue -and $state.iterationLastCountedIssue -ne $state.lastIssue) {
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
    Write-Host "missingGate=$Checkpoint"
    Write-Host "shouldSplitBacklog=false"
    Write-Host "selectedIssue="
    return
  }

  if ($ReadyIssues.Count -gt 0) {
    if ($ReadyIssues[0].lint.status -ne "pass") {
      Write-Host "nextAction=STOP"
      Write-Host "stopReason=STOP_READY_LINT_FAILED"
      Write-Host "missingGate=ready-lint"
      Write-Host "shouldSplitBacklog=false"
      Write-Host "selectedIssue=$($ReadyIssues[0].title)"
      foreach ($errorItem in @($ReadyIssues[0].lint.errors)) {
        Write-Host "lintError=$errorItem"
      }
      return
    }
    $batchPlan = Get-ReadyIssueBatchPlan $ReadyIssues $script:MaxParallelIssues $script:ParallelSafetyMode
    $batchIssues = @($batchPlan.issues)
    Write-Host "nextAction=$($batchPlan.decision)"
    Write-Host "nextReady=$($batchIssues[0].title)"
    Write-Host "stopReason="
    Write-Host "missingGate="
    Write-Host "shouldSplitBacklog=false"
    Write-Host "selectedIssue=$($batchIssues[0].title)"
    Write-Host "parallelSafetyMode=$script:ParallelSafetyMode"
    Write-Host "parallelBatchSize=$($batchIssues.Count)"
    Write-Host "parallelDecision=$($batchPlan.reason)"
    for ($index = 0; $index -lt $batchIssues.Count; $index++) {
      Write-Host ("parallelIssue[{0}]={1}" -f ($index + 1), $batchIssues[$index].title)
    }
    return
  }

  if ($Candidates.Count -gt 0) {
    Write-Host "nextAction=SPLIT_BACKLOG"
    Write-Host "stopReason="
    Write-Host "missingGate="
    Write-Host "shouldSplitBacklog=true"
    Write-Host "selectedIssue="
    Write-Host "wouldCreateReadyIssueDrafts=$($Candidates.Count)"
    for ($index = 0; $index -lt $Candidates.Count; $index++) {
      Write-Host ("splitCandidate[{0}]={1}" -f ($index + 1), $Candidates[$index].anchor)
    }
    return
  }

  Write-Host "nextAction=STOP"
  Write-Host "stopReason=$StopReason"
  Write-Host "missingGate=ready-issue"
  Write-Host "shouldSplitBacklog=false"
  Write-Host "selectedIssue="
}

function Get-ExecutorCommand {
  param(
    [string]$RepoRoot,
    [string]$ConfigPath,
    [string]$IssueTitle
  )

  $executorPath = Join-Path $scriptDir "autopilot-exec-issue.ps1"
  return "powershell -NoProfile -ExecutionPolicy Bypass -File `"$executorPath`" -RepoRoot `"$RepoRoot`" -ConfigPath `"$ConfigPath`" -Title `"$IssueTitle`""
}

function Invoke-ChildWithHeartbeat {
  param(
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [int]$TimeoutSeconds,
    [int]$StallInspectSeconds = 300,
    [int]$StallTerminateSeconds = 600,
    [int]$HeartbeatMilliseconds = 30000
  )
  function Stop-ChildProcessTree([Diagnostics.Process]$Child) {
    & taskkill.exe /PID $Child.Id /T /F 2>$null | Out-Null
    if (!$Child.HasExited) { $Child.Kill() }
  }
  function Quote-ChildArgument([string]$Value) { if ($Value -match '[\s"]') { return '"' + $Value.Replace('"','\"') + '"' }; return $Value }
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = 'powershell'
  $startInfo.Arguments = ($Arguments | ForEach-Object { Quote-ChildArgument $_ }) -join ' '
  $startInfo.WorkingDirectory = $WorkingDirectory
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo
  [void]$process.Start()
  $script:ExecutorPid = $process.Id
  Write-State $autoDir 'EXECUTING' $false 'EXECUTOR_RUNNING' $script:RunLock.issueId 'EXECUTING' ''
  $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
  $deadline = [datetimeoffset]::Now.AddSeconds($TimeoutSeconds)
  $timedOut = $false
  $stallTimedOut = $false
  $stallInspected = $false
  $lastProgressAt = [datetimeoffset]::Now
  $progressFingerprint = Get-AutopilotProgressFingerprint -Worktree $WorkingDirectory -RootPid $process.Id
  while (!$process.WaitForExit($HeartbeatMilliseconds)) {
    if ($script:RunLock) { Write-RunLock (Join-Path $autoDir 'run.lock') $script:RunLock }
    $currentFingerprint = Get-AutopilotProgressFingerprint -Worktree $WorkingDirectory -RootPid $process.Id
    if ($currentFingerprint -ne $progressFingerprint) {
      $progressFingerprint = $currentFingerprint
      $lastProgressAt = [datetimeoffset]::Now
      $stallInspected = $false
    }
    $idleSeconds = ([datetimeoffset]::Now - $lastProgressAt).TotalSeconds
    if (!$stallInspected -and $idleSeconds -ge $StallInspectSeconds) {
      $stallInspected = $true
      Write-RunEvent 'executor.stall.inspect' ([pscustomobject]@{ issueId = $script:RunLock.issueId; status = 'INSPECT'; reason = 'no worktree progress'; idleSeconds = [int]$idleSeconds; executorPid = $process.Id })
    }
    if ($idleSeconds -ge $StallTerminateSeconds) {
      $timedOut = $true
      $stallTimedOut = $true
      Write-RunEvent 'executor.stall.terminate' ([pscustomobject]@{ issueId = $script:RunLock.issueId; status = 'TERMINATE'; reason = 'no worktree progress'; idleSeconds = [int]$idleSeconds; executorPid = $process.Id })
      Stop-ChildProcessTree $process
      break
    }
    if ([datetimeoffset]::Now -ge $deadline) { $timedOut = $true; Stop-ChildProcessTree $process; break }
  }
  $process.WaitForExit()
  $script:ExecutorPid = $null
  $stdout = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
  if ($stdout) { Write-Host $stdout.TrimEnd() }
  if ($stderr) { Write-Warning $stderr.TrimEnd() }
  return [pscustomobject]@{ exitCode = if ($timedOut) { 124 } else { $process.ExitCode }; timedOut = $timedOut; stallTimedOut = $stallTimedOut }
}

function Invoke-IssueExecutor {
  param(
    [string]$RepoRoot,
    [string]$ConfigPath,
    [object]$Issue,
    [object]$Route,
    [int]$Attempt = 0,
    [ValidateSet('implement','repair')][string]$Phase = 'implement',
    [string]$PreviousSummary = ''
  )

  $executorPath = Join-Path $scriptDir "autopilot-exec-issue.ps1"
  if (!(Test-Path $executorPath)) {
    Write-Host "EXECUTOR_NOT_FOUND"
    Write-Host "executorCommand=$(Get-ExecutorCommand $RepoRoot $ConfigPath $Issue.title)"
    return
  }
  $baseCommit = (& git -C $RepoRoot rev-parse HEAD).Trim()
  $worktree = New-AutopilotIssueWorktree -RepoRoot $RepoRoot -IssueId $Issue.lint.issueId -BaseCommit $baseCommit -AllowDirtyReuse:($Phase -eq 'repair')
  $script:Attempt = $Attempt
  $script:CurrentWorktree = $worktree.path
  $script:CurrentBranch = $worktree.branch
  $issueDir = Join-Path $script:RunContext.dir $Issue.lint.issueId
  New-Item -ItemType Directory -Path $issueDir -Force | Out-Null
  $contextPath = Join-Path $issueDir ("$Phase-$Attempt\context.json")
  New-AutopilotContextPack -Issue $Issue.contract -Phase $Phase -RepoRoot $RepoRoot -Worktree $worktree.path -OutputPath $contextPath -PreviousPhaseSummary $PreviousSummary | Out-Null
  Write-State $autoDir 'EXECUTING' $false 'EXECUTOR_START' $Issue.title 'EXECUTING' ''
  $executorArgs = @(
    '-NoProfile','-ExecutionPolicy','Bypass','-File',$executorPath,
    '-RepoRoot',$worktree.path,
    '-ConfigPath',$ConfigPath,
    '-ReadyPath',(Join-Path $worktree.path 'docs\backlog\ready-issues.md'),
    '-Title',$Issue.title,
    '-RunId',$(if ($Attempt -eq 0) { $script:RunContext.id } else { "$($script:RunContext.id)-repair-$Attempt" }),
    '-ContextPath',$contextPath,
    '-Model',$Route.modelBaseline,
    '-Thinking',$Route.thinkingBaseline,
    '-ExecutorRole',$Route.executorRole
  )
  if ($Route.reviewRequired) { $executorArgs += '-ReviewRequired' }
  $executorTimeout = if ($config.issueExecutor.timeoutSeconds) { [int]$config.issueExecutor.timeoutSeconds + 120 } else { 2820 }
  $stallInspectSeconds = if ($config.issueExecutor.stallInspectSeconds) { [int]$config.issueExecutor.stallInspectSeconds } else { 300 }
  $stallTerminateSeconds = if ($config.issueExecutor.stallTerminateSeconds) { [int]$config.issueExecutor.stallTerminateSeconds } else { 600 }
  $heartbeatMilliseconds = if ($config.issueExecutor.heartbeatMilliseconds) { [int]$config.issueExecutor.heartbeatMilliseconds } else { 30000 }
  $childResult = Invoke-ChildWithHeartbeat -Arguments $executorArgs -WorkingDirectory $worktree.path -TimeoutSeconds $executorTimeout -StallInspectSeconds $stallInspectSeconds -StallTerminateSeconds $stallTerminateSeconds -HeartbeatMilliseconds $heartbeatMilliseconds
  if ($childResult.exitCode -ne 0) {
    if ($childResult.stallTimedOut -and $config.repair -and $config.repair.enabled -eq $true -and $Attempt -lt 1) {
      Write-State $autoDir 'REPAIRING' $false 'EXECUTOR_STALL_REPAIR' $Issue.title 'executor timed out without worktree progress' ''
      Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $Issue -Route $Route -Attempt ($Attempt + 1) -Phase 'repair' -PreviousSummary 'executor timed out without worktree progress'
      return
    }
    Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_PROCESS_FAILED' $Issue.title 'tool_config' 'STOP_EXECUTOR_PROCESS_FAILED'
    return
  }
  $executionRunId = if ($Attempt -eq 0) { $script:RunContext.id } else { "$($script:RunContext.id)-repair-$Attempt" }
  $resultPath = Join-Path (Join-Path $autoDir "runs\$executionRunId") 'result.json'
  if (Test-Path -LiteralPath $resultPath) {
    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($result.status -eq 'done') {
      $changes = @(Get-AutopilotWorktreeChanges -Worktree $worktree.path)
      try {
        Assert-AutopilotAllowedChanges -ChangedPaths $changes -AllowedPaths $Issue.contract.allowedPaths -ForbiddenPaths $Issue.contract.forbiddenPaths | Out-Null
      } catch {
        $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_SCOPE_VIOLATION'
        $result.validation += [pscustomobject]@{ name = 'scope-allowlist'; status = 'fail'; message = $_.Exception.Message }
      }
      $evidencePaths = @()
      $failureSummary = ''
      $currentFingerprint = ''
      if ($result.status -eq 'done') {
        Write-State $autoDir 'VERIFYING' $false 'EXECUTOR_COMPLETED' $Issue.title 'VERIFYING' ''
        $verifyDir = Join-Path $issueDir 'verify'
        New-Item -ItemType Directory -Path $verifyDir -Force | Out-Null
        for ($index = 0; $index -lt $Issue.contract.validationCommands.Count; $index++) {
          $evidencePath = Join-Path $verifyDir ("evidence-{0:00}.json" -f ($index + 1))
          $logPath = Join-Path $verifyDir ("command-{0:00}.log" -f ($index + 1))
          $evidence = Invoke-AutopilotVerificationCommand -IssueId $Issue.lint.issueId -Worktree $worktree.path -BaseCommit $baseCommit -Command $Issue.contract.validationCommands[$index] -EvidencePath $evidencePath -LogPath $logPath
          $evidencePaths += $evidencePath
          $result.validation += [pscustomobject]@{ name = "ready-command-$($index + 1)"; status = $evidence.classification; message = "exitCode=$($evidence.exitCode); evidence=$evidencePath" }
          if ($evidence.exitCode -ne 0) {
            $classifierPath = Join-Path (Resolve-Path (Join-Path $scriptDir '..\..')).Path 'plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1'
            $classification = & powershell -NoProfile -ExecutionPolicy Bypass -File $classifierPath -ErrorText $evidence.summary -ExitCode $evidence.exitCode | ConvertFrom-Json
            $result.status = 'blocked'
            $result.failureCategory = if ($classification.category -eq 'environment_prereq') { 'environment' } elseif ($classification.category -eq 'ready_issue_config') { 'ready_issue_config' } elseif ($classification.category -eq 'tool_config') { 'tool_config' } else { 'quality_security' }
            $result.nextAction = 'STOP'; $result.stopReason = 'STOP_VERIFICATION_FAILED'
            $result | Add-Member -NotePropertyName failureFingerprint -NotePropertyValue $classification.failureFingerprint -Force
            $failureSummary = $classification.reason
            $currentFingerprint = $classification.failureFingerprint
            break
          }
        }
      }
      $result | Add-Member -NotePropertyName evidencePaths -NotePropertyValue @($evidencePaths) -Force
      $result | Add-Member -NotePropertyName reviewRequired -NotePropertyValue ([bool]$Route.reviewRequired) -Force
      $result | Add-Member -NotePropertyName attempt -NotePropertyValue $Attempt -Force
      $result | Add-Member -NotePropertyName firstPassSuccess -NotePropertyValue ($Attempt -eq 0 -and $result.status -eq 'done') -Force
      $result | Add-Member -NotePropertyName manualInterventionCount -NotePropertyValue 0 -Force
      $result | Add-Member -NotePropertyName scopeViolationCount -NotePropertyValue $(if ($result.stopReason -eq 'STOP_SCOPE_VIOLATION') { 1 } else { 0 }) -Force
      $closed = $false
      if ($result.status -eq 'done') { $script:FailureFingerprint = $null }
      if ($result.status -eq 'done' -and $Route.reviewRequired) {
        Write-State $autoDir 'REVIEWING' $false 'VERIFICATION_COMPLETED' $Issue.title 'REVIEWING' ''
        $reviewDir = Join-Path $issueDir 'review'; New-Item -ItemType Directory -Path $reviewDir -Force | Out-Null
        $diffPath = Join-Path $reviewDir 'final.diff'
        (& git -C $worktree.path diff --binary $baseCommit -- | Out-String) | Set-Content -LiteralPath $diffPath -Encoding UTF8
        $requestPath = Join-Path $reviewDir 'request.json'
        New-AutopilotReviewRequest -IssueId $Issue.lint.issueId -ReadyPath (Join-Path $worktree.path 'docs\backlog\ready-issues.md') -DiffPath $diffPath -EvidencePaths $evidencePaths -OutputPath $requestPath | Out-Null
        if (!$config.issueReviewer -or $config.issueReviewer.enabled -ne $true) {
          $result.status = 'blocked'; $result.failureCategory = 'tool_config'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEWER_REQUIRED'
        } else {
          $reviewPath = Join-Path $reviewDir 'result.json'
          $review = Invoke-AutopilotReviewer -Worktree $worktree.path -RequestPath $requestPath -ResultPath $reviewPath -SchemaPath (Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\schemas\review-result.schema.json') -Model $config.issueReviewer.model -Thinking $config.issueReviewer.thinking
          $result | Add-Member -NotePropertyName review -NotePropertyValue $review -Force
          $reviewDisposition = Get-AutopilotReviewDisposition -ReviewResult $review
          if ($reviewDisposition.action -eq 'REPAIR') {
            $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEW_NEEDS_REPAIR'
            $failureSummary = $reviewDisposition.summary; $currentFingerprint = $reviewDisposition.failureFingerprint
            $result | Add-Member -NotePropertyName failureFingerprint -NotePropertyValue $currentFingerprint -Force
          } elseif ($reviewDisposition.action -eq 'BLOCK') {
            $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEW_FAILED'
          }
        }
      }
      if ($result.status -eq 'done' -and $config.closeout -and $config.closeout.enabled -eq $true) {
        Write-State $autoDir 'COMMITTING' $false 'CLOSEOUT_START' $Issue.title 'COMMITTING' ''
        $closeout = Complete-AutopilotIssueCloseout -RepoRoot $RepoRoot -Worktree $worktree.path -Issue $Issue.contract -AutoMerge ([bool]$config.autoMerge)
        $script:LastCommit = $closeout.commit
        $result.gitSummary | Add-Member -NotePropertyName commit -NotePropertyValue $closeout.commit -Force
        $result | Add-Member -NotePropertyName merged -NotePropertyValue $closeout.merged -Force
        $result.nextAction = 'CHECKPOINT'
        $closeoutKey = Get-AutopilotCloseoutKey -IssueId $Issue.lint.issueId -Commit $closeout.commit -ReportPath $Issue.contract.archiveReport
        Register-AutopilotCloseout -LedgerPath (Join-Path $autoDir 'closeouts.ndjson') -Key $closeoutKey | Out-Null
        Write-RunEvent 'closeout' ([pscustomobject]@{ issueId = $Issue.lint.issueId; title = $Issue.title; decision = 'DONE'; status = 'CLOSING'; reason = 'local commit closeout'; evidencePath = $Issue.contract.archiveReport; commit = $closeout.commit })
        $closed = $true
      }
      $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $resultPath -Encoding UTF8
      if ($Attempt -gt 0) { Copy-Item -LiteralPath $resultPath -Destination (Join-Path $script:RunContext.dir 'result.json') -Force }
      if ($result.status -eq 'blocked' -and $result.stopReason -in @('STOP_VERIFICATION_FAILED','STOP_REVIEW_NEEDS_REPAIR') -and $config.repair -and $config.repair.enabled -eq $true -and (Test-AutopilotRetryAllowed -PreviousFingerprint $script:FailureFingerprint -CurrentFingerprint $currentFingerprint -Attempt $Attempt)) {
        $script:FailureFingerprint = $currentFingerprint
        Write-State $autoDir 'REPAIRING' $false 'REPAIR_START' $Issue.title $failureSummary ''
        Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $Issue -Route $Route -Attempt ($Attempt + 1) -Phase 'repair' -PreviousSummary $failureSummary
        return
      }
      if ($result.status -eq 'done' -and $closed) { Write-State $autoDir 'CHECKPOINT' $false 'ISSUE_CLOSED' $Issue.title 'CHECKPOINT' '' } elseif ($result.status -eq 'done') { Write-State $autoDir 'CLOSING' $false 'VERIFICATION_COMPLETED' $Issue.title 'CLOSING' '' } else { Write-State $autoDir 'BLOCKED' $false 'VERIFICATION_BLOCKED' $Issue.title $result.failureCategory $result.stopReason }
    } else {
      Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_BLOCKED' $Issue.title $result.failureCategory $result.stopReason
    }
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-state.ps1')
. (Join-Path $scriptDir 'autopilot-ready.ps1')
. (Join-Path $scriptDir 'autopilot-route.ps1')
. (Join-Path $scriptDir 'autopilot-worktree.ps1')
. (Join-Path $scriptDir 'autopilot-progress.ps1')
. (Join-Path $scriptDir 'autopilot-context.ps1')
. (Join-Path $scriptDir 'autopilot-verify.ps1')
. (Join-Path $scriptDir 'autopilot-review.ps1')
. (Join-Path $scriptDir 'autopilot-recover.ps1')
. (Join-Path $scriptDir 'autopilot-refill.ps1')
. (Join-Path $scriptDir 'autopilot-closeout.ps1')
if (!$ConfigPath) {
  $ConfigPath = Join-Path $scriptDir "codex-autopilot.config.json"
}
$config = Read-JsonFile $ConfigPath
if ($config.repoRoot) {
  $RepoRoot = $config.repoRoot
}

$autoDir = if ($config.autopilotDir) { $config.autopilotDir } else { Join-Path $RepoRoot ".codex-autopilot" }
$maxIssuesPerRun = if ($config.maxIssuesPerRun) { [int]$config.maxIssuesPerRun } else { 1 }
$maxParallelIssues = if ($config.maxParallelIssues) { [int]$config.maxParallelIssues } else { 3 }
$parallelSafetyMode = if ($config.parallelSafetyMode) { [string]$config.parallelSafetyMode } else { "strict-independent-only" }
$maxRunMinutes = if ($config.maxRunMinutes) { [int]$config.maxRunMinutes } else { 120 }
if ($maxParallelIssues -lt 1 -or $maxParallelIssues -gt 3) {
  throw "maxParallelIssues must be between 1 and 3, actual=$maxParallelIssues"
}
if ($parallelSafetyMode -ne "strict-independent-only") {
  throw "parallelSafetyMode must be strict-independent-only, actual=$parallelSafetyMode"
}
$script:MaxParallelIssues = $maxParallelIssues
$script:ParallelSafetyMode = $parallelSafetyMode

$readyPath = Join-Path $RepoRoot "docs\backlog\ready-issues.md"
$focusPath = Join-Path $RepoRoot "docs\backlog\current-focus.md"
$planPath = Join-Path $RepoRoot "docs\backlog\cgc-pms-production-enhancement-plan.md"
$splitLimit = 5
$applyMode = [bool]$ApplyBacklogSplit -and -not [bool]$DryRun -and -not [bool]$ExplainNextAction
$dryRunMode = -not $applyMode
$script:RunLock = $null
$script:CurrentWorktree = ''
$script:CurrentBranch = ''
$script:ExecutorPid = $null
$script:LastCommit = $null
$script:FailureFingerprint = $null
$script:Attempt = 0
Initialize-IterationProgress $autoDir $readyPath $MaxIterations ([bool]$DryRun -or [bool]$ExplainNextAction)
$script:RunContext = New-RunContext $autoDir
Write-RunEvent "runner.start" ([pscustomobject]@{
  decision = if ($ExplainNextAction) { "EXPLAIN_NEXT_ACTION" } elseif ($applyMode) { "APPLY_BACKLOG_SPLIT" } else { "DRY_RUN" }
  status = "STARTED"
  applyMode = $applyMode
})

Write-Host "CGC-PMS AutoPilot continuous runner"
Write-Host "repoRoot=$RepoRoot"
Write-Host "maxIssuesPerRun=$maxIssuesPerRun"
Write-Host "maxParallelIssues=$maxParallelIssues"
Write-Host "parallelSafetyMode=$parallelSafetyMode"
Write-Host "iterationLimit=$script:IterationLimit"
Write-Host "iterationCompleted=$script:IterationCompleted"
Write-Host "remainingIterations=$script:RemainingIterations"
Write-Host "autoPush=$($config.autoPush)"
Write-Host "dryRun=$dryRunMode"
Write-Host "applyBacklogSplit=$applyMode"

if ($ExplainNextAction) {
  $checkpoint = Test-Checkpoint $autoDir
  Write-RunEvent "checkpoint" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "EXPLAIN" })
  $readyIssues = if ($checkpoint -eq "CONTINUE") { @(Get-ReadyIssues $readyPath $RepoRoot $scriptDir) } else { @() }
  $candidates = if ($checkpoint -eq "CONTINUE" -and $readyIssues.Count -eq 0) { @(Get-SplitCandidates $focusPath $planPath $readyPath $splitLimit) } else { @() }
  $stopReason = if ($checkpoint -eq "CONTINUE" -and $readyIssues.Count -eq 0 -and $candidates.Count -eq 0) { Get-StopReasonForEmptyPool $readyPath } else { $checkpoint }
  $batchPlan = if ($readyIssues.Count -gt 0 -and $readyIssues[0].lint.status -eq "pass") { Get-ReadyIssueBatchPlan $readyIssues $maxParallelIssues $parallelSafetyMode } else { $null }
  $decision = if ($checkpoint -ne "CONTINUE") { "STOP" } elseif ($readyIssues.Count -gt 0 -and $readyIssues[0].lint.status -ne "pass") { "STOP_READY_LINT_FAILED" } elseif ($readyIssues.Count -gt 0) { $batchPlan.decision } elseif ($candidates.Count -gt 0) { "SPLIT_BACKLOG" } else { "STOP" }
  Write-RunEvent "decision" ([pscustomobject]@{
    decision = $decision
    issueId = if ($readyIssues.Count -gt 0) { $readyIssues[0].lint.issueId } else { "" }
    title = if ($readyIssues.Count -gt 0) { $readyIssues[0].title } else { "" }
    stopReason = if ($decision -eq "STOP") { $stopReason } elseif ($decision -eq "STOP_READY_LINT_FAILED") { "STOP_READY_LINT_FAILED" } else { "" }
    missingGate = if ($decision -eq "STOP_READY_LINT_FAILED") { "ready-lint" } elseif ($decision -eq "STOP") { "ready-issue" } else { "" }
    shouldSplitBacklog = ($decision -eq "SPLIT_BACKLOG")
    selectedIssue = if ($readyIssues.Count -gt 0) { $readyIssues[0].title } else { "" }
    parallelBatchSize = if ($batchPlan) { @($batchPlan.issues).Count } else { 0 }
    parallelDecision = if ($batchPlan) { $batchPlan.reason } else { "" }
  })
  Write-NextActionExplanation $checkpoint $readyIssues $candidates $stopReason
  exit 0
}

try {
  if ($applyMode) {
    $recovery = Get-AutopilotRecoveryDecision -AutoDir $autoDir
    if ($recovery.action -eq 'REFUSE_SECOND_INSTANCE') { Write-Host 'RUN_LOCK_ACTIVE'; throw 'Another AutoPilot run is active.' }
    if ($recovery.action -in @('RESUME_FROM_CHECKPOINT','RESUME_CLOSEOUT')) {
      Remove-Item -LiteralPath (Join-Path $autoDir 'run.lock') -Force -ErrorAction SilentlyContinue
      Write-Host 'STALE_RUN_LOCK_REMOVED'
      Write-RunEvent 'recovery' ([pscustomobject]@{ decision = $recovery.action; status = 'RECOVERED'; reason = $recovery.reason })
    } elseif ($recovery.action -in @('VERIFY_UNCOMMITTED','QUARANTINE')) {
      Write-State $autoDir 'BLOCKED' $false 'RECOVERY_REVIEW_REQUIRED' '' $recovery.reason 'STOP_RECOVERY_REVIEW_REQUIRED'
      Write-Host 'STOP_RECOVERY_REVIEW_REQUIRED'
      exit 0
    }
    $script:RunLock = New-RunLock $autoDir $maxRunMinutes "apply-backlog-split"
  }

  if ($null -ne $script:IterationLimit -and $script:IterationCompleted -ge $script:IterationLimit) {
    Write-RunEvent "stop" ([pscustomobject]@{ decision = "STOP"; status = "STOP_ITERATION_LIMIT_REACHED"; stopReason = "STOP_ITERATION_LIMIT_REACHED" })
    Write-State $autoDir "STOP_ITERATION_LIMIT_REACHED" ([bool]$DryRun) "STOP" "" "STOP_ITERATION_LIMIT_REACHED" "STOP_ITERATION_LIMIT_REACHED"
    Write-Host "STOP_ITERATION_LIMIT_REACHED"
    exit 0
  }

  for ($loop = 1; $loop -le $MaxLoops; $loop++) {
    $checkpoint = Test-Checkpoint $autoDir
    Write-RunEvent "checkpoint" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "CHECKPOINT"; loop = $loop })
    Write-Host "checkpoint[$loop]=$checkpoint"
    if ($checkpoint -ne "CONTINUE") {
      Write-RunEvent "stop" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "STOP"; status = $checkpoint; stopReason = $checkpoint; loop = $loop })
      Write-State $autoDir $checkpoint ([bool]$DryRun) "STOP" "" $checkpoint $checkpoint
      Write-Host $checkpoint
      exit 0
    }

    $readyIssues = @(Get-ReadyIssues $readyPath $RepoRoot $scriptDir)
    if ($readyIssues.Count -gt 0) {
      if ($readyIssues[0].lint.status -ne "pass") {
        Write-RunEvent "ready-lint" ([pscustomobject]@{
          issueId = $readyIssues[0].lint.issueId
          title = $readyIssues[0].title
          decision = "STOP"
          status = "fail"
          stopReason = "STOP_READY_LINT_FAILED"
          missingGate = "ready-lint"
        })
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
      Write-RunEvent "ready-lint" ([pscustomobject]@{
        issueId = $readyIssues[0].lint.issueId
        title = $readyIssues[0].title
        decision = "PASS"
        status = "pass"
      })
      $batchPlan = Get-ReadyIssueBatchPlan $readyIssues $maxParallelIssues $parallelSafetyMode
      $batchIssues = @($batchPlan.issues)
      $selectedIssue = $batchIssues[0]
      $selectedRoute = if ($selectedIssue.contract) { Get-AutopilotRoute -Issue $selectedIssue.contract } else { $null }
      $readyStatus = if ($batchPlan.parallel) { "READY_ISSUE_BATCH_FOUND" } else { "READY_ISSUE_FOUND" }
      Write-State $autoDir $readyStatus ([bool]$DryRun) $readyStatus $selectedIssue.title $readyStatus ""
      Write-RunEvent "decision" ([pscustomobject]@{
        issueId = $selectedIssue.lint.issueId
        title = $selectedIssue.title
        decision = $readyStatus
        status = $readyStatus
        selectedIssue = $selectedIssue.title
        parallelBatchSize = $batchIssues.Count
        parallelDecision = $batchPlan.reason
        parallelSafetyMode = $parallelSafetyMode
        executorRole = if ($selectedRoute) { $selectedRoute.executorRole } else { '' }
        modelBaseline = if ($selectedRoute) { $selectedRoute.modelBaseline } else { '' }
        thinkingBaseline = if ($selectedRoute) { $selectedRoute.thinkingBaseline } else { '' }
        reviewRequired = if ($selectedRoute) { $selectedRoute.reviewRequired } else { $false }
        verificationProfile = if ($selectedRoute) { $selectedRoute.verificationProfile } else { '' }
      })
      Write-Host $readyStatus
      Write-Host "selected=$($selectedIssue.title)"
      Write-Host "parallelSafetyMode=$parallelSafetyMode"
      Write-Host "parallelBatchSize=$($batchIssues.Count)"
      Write-Host "parallelDecision=$($batchPlan.reason)"
      for ($index = 0; $index -lt $batchIssues.Count; $index++) {
        $issue = $batchIssues[$index]
        Write-Host ("parallelIssue[{0}]={1}" -f ($index + 1), $issue.title)
      }
      if ($DryRun -or $batchPlan.parallel) {
        for ($index = 0; $index -lt $batchIssues.Count; $index++) {
          $issue = $batchIssues[$index]
          $commandText = Get-ExecutorCommand $RepoRoot $ConfigPath $issue.title
          if ($batchIssues.Count -eq 1) {
            Write-Host "executorCommand=$commandText"
          } else {
            Write-Host ("executorCommand[{0}]={1}" -f ($index + 1), $commandText)
          }
        }
        if ($batchPlan.parallel -and !$DryRun) {
          Write-Host "PARALLEL_BATCH_PLAN_ONLY"
        }
      } else {
        Invoke-IssueExecutor $RepoRoot $ConfigPath $selectedIssue $selectedRoute
      }
      exit 0
    }

    $refillDecision = Get-AutopilotRefillDecision -RepoRoot $RepoRoot
    if ($applyMode -and $config.readyPlanner -and $config.readyPlanner.enabled -eq $true -and $refillDecision.action -in @('PLAN_READY','UNBLOCK_FIRST')) {
      Write-State $autoDir 'REFILLING' $false 'READY_PLANNER_START' '' $refillDecision.reason ''
      $planResultPath = Join-Path $script:RunContext.dir 'ready-plan.json'
      $planSchemaPath = Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\schemas\ready-plan.schema.json'
      Invoke-AutopilotReadyPlanner -RepoRoot $RepoRoot -Candidates $refillDecision.candidates -OutputPath $planResultPath -SchemaPath $planSchemaPath -Model $config.readyPlanner.model -Thinking $config.readyPlanner.thinking -TimeoutSeconds $config.readyPlanner.timeoutSeconds | Out-Null
      $imported = Import-AutopilotReadyPlan -PlanPath $planResultPath -ReadyPath $readyPath -RepoRoot $RepoRoot
      $refillCommit = Commit-ReadyRefill $RepoRoot $readyPath
      Write-RunEvent 'refill.planned' ([pscustomobject]@{ decision = 'BACKLOG_SPLIT_APPLIED'; status = 'BACKLOG_SPLIT_APPLIED'; reason = $refillDecision.reason; createdReadyIssueDrafts = $imported.createdCount; commit = $refillCommit })
      Write-State $autoDir 'REFILLING' $false 'BACKLOG_SPLIT_APPLIED' '' 'BACKLOG_SPLIT_APPLIED' ''
      Write-Host 'BACKLOG_SPLIT_APPLIED'
      Write-Host "createdReadyIssueDrafts=$($imported.createdCount)"
      Write-Host 'REFILL_ROUND_COMPLETE'
      exit 0
    }
    if ($refillDecision.action -eq 'UNBLOCK_FIRST') {
      Write-State $autoDir 'BLOCKED' $false 'UNBLOCK_REQUIRED' '' $refillDecision.reason 'STOP_UNBLOCK_PLANNER_UNAVAILABLE'
      Write-Host 'STOP_UNBLOCK_PLANNER_UNAVAILABLE'
      exit 0
    }

    Write-Host "SPLIT_MODE"
    Write-Host "focusPath=$focusPath"
    $candidates = @(Get-SplitCandidates $focusPath $planPath $readyPath $splitLimit)
    if ($candidates.Count -eq 0) {
      $stopReason = Get-StopReasonForEmptyPool $readyPath
      Write-RunEvent "stop" ([pscustomobject]@{ decision = "STOP"; status = $stopReason; stopReason = $stopReason })
      Write-State $autoDir $stopReason $dryRunMode "STOP" "" $stopReason $stopReason
      Write-Host $stopReason
      exit 0
    }

    Write-Host "splitCandidateCount=$($candidates.Count)"
    Write-RunEvent "split.candidates" ([pscustomobject]@{
      decision = "SPLIT_BACKLOG"
      status = "planned"
      shouldSplitBacklog = $true
      splitCandidateCount = $candidates.Count
    })
    for ($index = 0; $index -lt $candidates.Count; $index++) {
      Write-Host ("splitCandidate[{0}]={1}" -f ($index + 1), $candidates[$index].anchor)
    }

    if ($dryRunMode) {
      Write-State $autoDir "DRY_RUN_SPLIT_PLANNED" $true "SPLIT_BACKLOG" "" "DRY_RUN_SPLIT_PLANNED" ""
      Write-RunEvent "split.dry_run" ([pscustomobject]@{ decision = "SPLIT_BACKLOG"; status = "DRY_RUN_SPLIT_PLANNED"; shouldSplitBacklog = $true })
      Write-Host "DRY_RUN_NO_BACKLOG_WRITE"
      exit 0
    }

    $createdCount = Add-ReadyIssueDrafts $readyPath $candidates
    $refillCommit = Commit-ReadyRefill $RepoRoot $readyPath
    Write-State $autoDir "BACKLOG_SPLIT_APPLIED" $false "BACKLOG_SPLIT_APPLIED" "" "BACKLOG_SPLIT_APPLIED" ""
    Write-RunEvent "split.applied" ([pscustomobject]@{ decision = "BACKLOG_SPLIT_APPLIED"; status = "BACKLOG_SPLIT_APPLIED"; shouldSplitBacklog = $true; createdReadyIssueDrafts = $createdCount; commit = $refillCommit })
    Write-Host "BACKLOG_SPLIT_APPLIED"
    Write-Host "createdReadyIssueDrafts=$createdCount"

    $checkpoint = Test-Checkpoint $autoDir
    Write-RunEvent "checkpoint" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "POST_SPLIT_CHECKPOINT" })
    Write-Host "postSplitCheckpoint=$checkpoint"
    if ($checkpoint -ne "CONTINUE") {
      Write-RunEvent "stop" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "STOP"; status = $checkpoint; stopReason = $checkpoint })
      Write-State $autoDir $checkpoint $false "STOP" "" $checkpoint $checkpoint
      Write-Host $checkpoint
      exit 0
    }
    Write-Host 'REFILL_ROUND_COMPLETE'
    exit 0
  }

  Write-RunEvent "stop" ([pscustomobject]@{ decision = "STOP"; status = "STOP_SESSION_LIMIT"; stopReason = "STOP_SESSION_LIMIT" })
  Write-State $autoDir "STOP_SESSION_LIMIT" $dryRunMode "STOP" "" "STOP_SESSION_LIMIT" "STOP_SESSION_LIMIT"
  Write-Host "STOP_SESSION_LIMIT"
} finally {
  Remove-RunLock $autoDir $script:RunLock
}
