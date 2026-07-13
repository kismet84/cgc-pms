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
  return Get-Content -Encoding UTF8 -Raw $Path | ConvertFrom-Json
}

function Test-Checkpoint {
  param([string]$AutoDir)

  if (Test-Path (Join-Path $AutoDir "stop.flag")) {
    return "STOP_STOP_FLAG"
  }
  if (Test-Path (Join-Path $AutoDir "pause.flag")) {
    return "STOP_PAUSE_FLAG"
  }
  if (!(Test-Path (Join-Path $AutoDir "enabled.flag"))) {
    return "STOP_DISABLED"
  }
  $statePath = Join-Path $AutoDir 'state.json'
  if (Test-Path -LiteralPath $statePath) {
    $state = Read-AutopilotState -Path $statePath
    $boundedBatchComplete = $null -ne $state.iterationLimit -and [int]$state.remainingIterations -le 0
    if ([bool]$state.retrospectiveDue -and ($null -eq $state.iterationLimit -or $boundedBatchComplete)) {
      return 'STOP_RETROSPECTIVE_REQUIRED'
    }
  }
  return "CONTINUE"
}

function Read-RunLock {
  param([string]$LockPath)

  if (!(Test-Path $LockPath)) {
    return $null
  }
  try {
    return Get-Content -Encoding UTF8 -Raw $LockPath | ConvertFrom-Json
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
        lint = [pscustomobject]@{ status = 'pass'; issueId = $_.issueId; title = $_.title; readyContentHash = $_.readyContentHash; failureCategory = 'none'; errorCode = ''; errors = @(); warnings = @() }
      }
    })
  } catch {
    $text = Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8
    $match = [regex]::Match($text, '(?ms)^###\s+(ISSUE-[0-9-]+[^\r\n]*)\r?\n(.*?)(?=^###\s+ISSUE-|\z)')
    if (!$match.Success) { return @() }
    $title = $match.Groups[1].Value.Trim()
    return @([pscustomobject]@{
      title = $title; status = 'Ready'; body = $match.Groups[2].Value; contract = $null
      lint = [pscustomobject]@{ status = 'fail'; issueId = ([regex]::Match($title, '^(ISSUE-[0-9-]+)')).Groups[1].Value; title = $title; readyContentHash = ''; failureCategory = 'ready_issue_config'; errorCode = $(if ($_.Exception.Message -match 'READY_SCOPE_CONTRADICTION') { 'READY_SCOPE_CONTRADICTION' } else { 'READY_CONTRACT_INVALID' }); errors = @($_.Exception.Message); warnings = @() }
    })
  }
}

function Get-RecoveryIssueFromCheckpoint {
  param([Parameter(Mandatory)][object]$Recovery, [Parameter(Mandatory)][string]$RepoRoot)
  $checkpoint = $Recovery.checkpoint
  $readyPath = Join-Path ([string]$checkpoint.worktree) ([string]$checkpoint.readyPath)
  $block = @(Get-AutopilotIssueBlocks $readyPath | Where-Object { $_.issueId -eq [string]$checkpoint.issueId } | Select-Object -First 1)
  if ($block.Count -ne 1) { throw 'recoverable terminal Issue contract is missing from its preserved worktree' }
  $normalizedBody = [regex]::Replace([string]$block[0].body, '(?m)^状态：Done[ \t]*(?=\r?$)', '状态：Ready')
  $normalizedRaw = [regex]::Replace([string]$block[0].rawBlock, '(?m)^状态：Done[ \t]*(?=\r?$)', '状态：Ready')
  $normalizedBlock = [pscustomobject]@{ issueId=$block[0].issueId; title=$block[0].title; body=$normalizedBody; rawBlock=$normalizedRaw }
  $contract = ConvertTo-AutopilotReadyIssue -Block $normalizedBlock -RepoRoot $RepoRoot
  $normalizedHash = Get-AutopilotReadyContractHash -ReadyPath $readyPath -IssueId $checkpoint.issueId -NormalizeDoneStatus
  if ($normalizedHash -ne [string]$checkpoint.readyContentHash) { throw 'reconstructed terminal Issue contract no longer matches its dispatch hash' }
  $contract | Add-Member -NotePropertyName readyContentHash -NotePropertyValue ([string]$checkpoint.readyContentHash) -Force
  return [pscustomobject]@{
    title=$contract.title; status='Ready'; body=$contract.body; contract=$contract
    lint=[pscustomobject]@{ status='pass'; issueId=$contract.issueId; title=$contract.title; readyContentHash=$contract.readyContentHash; failureCategory='none'; errorCode=''; errors=@(); warnings=@() }
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
    try { $existing = Read-AutopilotState -Path $statePath } catch { throw "AutoPilot state cannot be updated safely: $($_.Exception.Message)" }
  }
  $completedIds = @()
  if ($null -ne $existing -and $existing.PSObject.Properties.Name -contains 'completedIssueIds') { $completedIds = @($existing.completedIssueIds) }
  while ($completedIds.Count -lt $script:IterationCompleted) { $completedIds += "legacy-completed-$($completedIds.Count + 1)" }
  $stateStatus = if ($script:AutopilotStatuses -contains $Status) { $Status } elseif ($Status -eq 'STOP_ITERATION_LIMIT_REACHED') { 'LIMIT_REACHED' } elseif ($Status -eq 'STOP_RETROSPECTIVE_REQUIRED') { 'RETROSPECTIVE_REQUIRED' } elseif ($Status -eq 'STOP_PAUSE_FLAG') { 'PAUSED' } elseif ($Status -eq 'STOP_DISABLED') { 'DISABLED' } elseif ($Status -like 'STOP*') { 'STOPPED' } elseif ($Status -like 'READY_ISSUE*') { 'PLANNING' } elseif ($Status -like '*SPLIT*') { 'REFILLING' } else { 'CHECKPOINT' }
  $phase = $stateStatus.ToLowerInvariant()
  $state = [ordered]@{
    schemaVersion = 3
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
    executorStartedAt = $script:ExecutorStartedAt
    lastProgressAt = $script:LastProgressAt
    retryCount = $script:Attempt
    timeoutReason = $script:TimeoutReason
    retiredAt = $script:RetiredAt
    retiredStatus = $script:RetiredStatus
    retiredExecutors = @($script:RetiredExecutors)
    lastCommit = $script:LastCommit
    failureFingerprint = $script:FailureFingerprint
    reviewCycleId = [string](Get-AutopilotStateProperty $existing 'reviewCycleId' '')
    reviewCycleStartedAt = Get-AutopilotStateProperty $existing 'reviewCycleStartedAt'
    reviewCycleCompletedIssueIds = @(Get-AutopilotStateProperty $existing 'reviewCycleCompletedIssueIds' @())
    reviewCycleScoreKeys = @(Get-AutopilotStateProperty $existing 'reviewCycleScoreKeys' @())
    reviewCycleCompletedCount = [int](Get-AutopilotStateProperty $existing 'reviewCycleCompletedCount' 0)
    retrospectiveDue = [bool](Get-AutopilotStateProperty $existing 'retrospectiveDue' $false)
    retrospectiveStatus = [string](Get-AutopilotStateProperty $existing 'retrospectiveStatus' 'IDLE')
    retrospectivePhase = [string](Get-AutopilotStateProperty $existing 'retrospectivePhase' 'NONE')
    retrospectiveRequiredAt = Get-AutopilotStateProperty $existing 'retrospectiveRequiredAt'
    retrospectiveReportCommit = Get-AutopilotStateProperty $existing 'retrospectiveReportCommit'
    retrospectiveFactsCommit = Get-AutopilotStateProperty $existing 'retrospectiveFactsCommit'
    retrospectiveGraphGitCursor = Get-AutopilotStateProperty $existing 'retrospectiveGraphGitCursor'
    retrospectiveEpisodeId = Get-AutopilotStateProperty $existing 'retrospectiveEpisodeId'
    retrospectiveFailureCategory = Get-AutopilotStateProperty $existing 'retrospectiveFailureCategory'
    lastRetrospectiveAt = Get-AutopilotStateProperty $existing 'lastRetrospectiveAt'
    lastRetrospectiveReport = Get-AutopilotStateProperty $existing 'lastRetrospectiveReport'
    activeScoringVersion = if ($script:TaskScoringActive) { [string]$config.taskScoring.activeVersion } else { $null }
    issueCheckpointPath = if ($script:IssueCheckpointPath) { [string]$script:IssueCheckpointPath } else { [string](Get-AutopilotStateProperty $existing 'issueCheckpointPath' '') }
    currentIssuePhase = if ($script:IssuePhase) { [string]$script:IssuePhase } else { [string](Get-AutopilotStateProperty $existing 'currentIssuePhase' '') }
    lastCanaryFingerprint = if ($script:LastCanaryFingerprint) { [string]$script:LastCanaryFingerprint } else { [string](Get-AutopilotStateProperty $existing 'lastCanaryFingerprint' '') }
    lastCanaryReport = if ($script:LastCanaryReport) { [string]$script:LastCanaryReport } else { [string](Get-AutopilotStateProperty $existing 'lastCanaryReport' '') }
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

  $text = Get-Content -Encoding UTF8 -Raw $ReadyPath
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

  return $statusMatch.Groups[1].Value.Trim() -match "^(Done|已完成|Iteration|Iterated|已迭代|迭代完成)\b"
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
      $result = Get-Content -Encoding UTF8 -Raw $resultPath | ConvertFrom-Json
    } catch {
      return
    }

    $durablyClosed = [string]$result.status -eq 'done' -and
      [bool]$result.merged -and
      [string]$result.nextAction -eq 'CHECKPOINT' -and
      $null -ne $result.gitSummary -and
      [string]$result.gitSummary.closeoutCommit
    if (!$result.issueId -or !$durablyClosed) {
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
    $state = Get-Content -Encoding UTF8 -Raw $statePath | ConvertFrom-Json
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

  $text = Get-Content -Encoding UTF8 -Raw $ReadyPath
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
    [string]$StopReason,
    [object]$RefillDecision
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

  if ($RefillDecision -and $RefillDecision.action -eq 'UNBLOCK_FIRST') {
    Write-Host "nextAction=UNBLOCK_FIRST"
    Write-Host "stopReason="
    Write-Host "missingGate=blocked-prerequisite"
    Write-Host "shouldSplitBacklog=false"
    Write-Host "selectedIssue="
    Write-Host "refillReason=$($RefillDecision.reason)"
    return
  }

  if ($Candidates.Count -gt 0) {
    Write-Host "nextAction=SPLIT_BACKLOG"
    Write-Host "stopReason="
    Write-Host "missingGate="
    Write-Host "shouldSplitBacklog=true"
    Write-Host "selectedIssue="
    Write-Host "wouldCreateReadyIssueDrafts=$($Candidates.Count)"
    Write-Host "candidateSource=$($Candidates[0].source)"
    for ($index = 0; $index -lt $Candidates.Count; $index++) {
      $candidateRef = if ($Candidates[$index].marker) { $Candidates[$index].marker } elseif ($Candidates[$index].anchor) { $Candidates[$index].anchor } else { $Candidates[$index].name }
      Write-Host ("splitCandidate[{0}]={1}" -f ($index + 1), $candidateRef)
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

function Write-ExecutorStallBlockedIssue {
  param([string]$RepoRoot, [object]$Issue, [object[]]$RetiredExecutors)
  $path = Join-Path $RepoRoot 'docs\backlog\blocked-issues.md'
  $existing = if (Test-Path -LiteralPath $path) { Get-Content -LiteralPath $path -Raw -Encoding UTF8 } else { "# Blocked Issues`r`n" }
  if ($existing -match "(?m)^###\s+$([regex]::Escape($Issue.lint.issueId))\b") { return $path }
  $rows = @($RetiredExecutors | ForEach-Object { "- executorPid=$($_.executorPid)，startedAt=$($_.startedAt)，lastProgressAt=$($_.lastProgressAt)，retiredAt=$($_.retiredAt)，retryCount=$($_.retryCount)" })
  $block = @"

### $($Issue.lint.issueId)：执行单元连续两次 stall timeout

- 失败分类：环境前置 / executor_stall_timeout。
- 退役证据：
$($rows -join "`r`n")
- 解除条件：补齐缺失上下文或拆小剩余范围后，由人工确认重新进入 Ready。
- 未完成验收项：该 Issue 的实现、验证、独立复核与归档尚未完成。
- 安全恢复方式：保持两个 executorPid 永久退役，从干净 checkpoint 创建全新执行单元；不得复用旧 PID 或启动第三次自动重派。
"@
  [IO.File]::WriteAllText($path, ($existing.TrimEnd() + "`r`n" + $block.TrimStart()), [Text.UTF8Encoding]::new($false))
  return $path
}

function Invoke-ChildWithHeartbeat {
  param(
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [int]$TimeoutSeconds,
    [int]$StallInspectSeconds = 300,
    [int]$StallTerminateSeconds = 600,
    [int]$HeartbeatMilliseconds = 30000,
    [object[]]$LongRunningCommands = @(),
    [string]$Task = ''
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
  $startedAt = [datetimeoffset]::Now
  $script:ExecutorPid = $process.Id
  $script:ExecutorStartedAt = $startedAt.ToString('o')
  $script:LastProgressAt = $script:ExecutorStartedAt
  $script:TimeoutReason = $null
  $script:RetiredAt = $null
  $script:RetiredStatus = $null
  Write-State $autoDir 'EXECUTING' $false 'EXECUTOR_RUNNING' $script:RunLock.issueId 'EXECUTING' ''
  if ($script:Attempt -gt 0) {
    $previousRetirement = @($script:RetiredExecutors | Select-Object -Last 1)[0]
    Write-RunEvent 'executor.stall.retry' ([pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; status = 'RETRY'; executorPid = $process.Id; startedAt = $startedAt.ToString('o'); lastProgressAt = $startedAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'previous executor produced no new evidence; scope limited to unfinished acceptance items'; retiredAt = $previousRetirement.retiredAt; retiredStatus = $previousRetirement.retiredStatus })
  }
  $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
  $deadline = [datetimeoffset]::Now.AddSeconds($TimeoutSeconds)
  $timedOut = $false
  $stallTimedOut = $false
  $stallInspected = $false
  $lastProgressAt = $startedAt
  $progressFingerprint = Get-AutopilotProgressFingerprint -Worktree $WorkingDirectory -RootPid $process.Id
  while (!$process.WaitForExit($HeartbeatMilliseconds)) {
    if ($script:RunLock) { Write-RunLock (Join-Path $autoDir 'run.lock') $script:RunLock }
    $currentFingerprint = Get-AutopilotProgressFingerprint -Worktree $WorkingDirectory -RootPid $process.Id
    if ($currentFingerprint -ne $progressFingerprint) {
      $progressFingerprint = $currentFingerprint
      $lastProgressAt = [datetimeoffset]::Now
      $script:LastProgressAt = $lastProgressAt.ToString('o')
      $stallInspected = $false
    }
    $idleSeconds = ([datetimeoffset]::Now - $lastProgressAt).TotalSeconds
    if ([datetimeoffset]::Now -ge $deadline) { $timedOut = $true; Stop-ChildProcessTree $process; break }
    if (!$stallInspected -and $idleSeconds -ge $StallInspectSeconds) {
      $stallInspected = $true
      Write-RunEvent 'executor.stall.inspect' ([pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; status = 'INSPECT'; reason = 'no durable worktree progress'; idleSeconds = [int]$idleSeconds; executorPid = $process.Id; startedAt = $startedAt.ToString('o'); lastProgressAt = $lastProgressAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'no new evidence'; retiredAt = $null; retiredStatus = $null })
    }
    if ($idleSeconds -ge $StallTerminateSeconds) {
      $activeLongCommand = Get-AutopilotActiveLongCommand -RootPid $process.Id -Declarations $LongRunningCommands -StartedAt $startedAt
      if ($activeLongCommand) {
        Write-RunEvent 'executor.stall.long-command' ([pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; status = 'RUNNING'; executorPid = $process.Id; command = $activeLongCommand.command; commandPid = $activeLongCommand.processId; expectedSeconds = $activeLongCommand.expectedSeconds; startedAt = $startedAt.ToString('o'); lastProgressAt = $lastProgressAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'declared long command active'; retiredAt = $null; retiredStatus = $null })
        continue
      }
      $timedOut = $true
      $stallTimedOut = $true
      $retiredAt = [datetimeoffset]::Now.ToString('o')
      $retired = [pscustomobject]@{ issueId = $script:RunLock.issueId; task = $Task; executorPid = $process.Id; startedAt = $startedAt.ToString('o'); lastProgressAt = $lastProgressAt.ToString('o'); retryCount = $script:Attempt; timeoutReason = 'no new evidence'; retiredAt = $retiredAt; retiredStatus = 'RETIRED' }
      $script:RetiredExecutors += $retired
      $script:TimeoutReason = $retired.timeoutReason
      $script:RetiredAt = $retiredAt
      $script:RetiredStatus = 'RETIRED'
      Write-RunEvent 'executor.stall.retire' $retired
      Stop-ChildProcessTree $process
      break
    }
  }
  $process.WaitForExit()
  $script:ExecutorPid = $null
  $stdout = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
  if ($stdout) { Write-Host $stdout.TrimEnd() }
  if ($stderr) { Write-Warning $stderr.TrimEnd() }
  return [pscustomobject]@{ exitCode = if ($timedOut) { 124 } else { $process.ExitCode }; timedOut = $timedOut; stallTimedOut = $stallTimedOut; executorPid = $process.Id }
}

function Invoke-IssueExecutor {
  param(
    [string]$RepoRoot,
    [string]$ConfigPath,
    [object]$Issue,
    [object]$Route,
    [int]$Attempt = 0,
    [ValidateSet('implement','repair')][string]$Phase = 'implement',
    [string]$PreviousSummary = '',
    [object]$ResumeCheckpoint = $null
  )

  $resuming = $null -ne $ResumeCheckpoint
  $executorPath = Join-Path $scriptDir "autopilot-exec-issue.ps1"
  if (!$resuming -and !(Test-Path $executorPath)) {
    Write-Host "EXECUTOR_NOT_FOUND"
    Write-Host "executorCommand=$(Get-ExecutorCommand $RepoRoot $ConfigPath $Issue.title)"
    return
  }
  if (!$resuming -and $Route.verificationProfile -eq 'runtime-health') {
    Write-State $autoDir 'VERIFYING' $false 'RUNTIME_HEALTH_GATE' $Issue.title 'runtime preflight' ''
    $preflight = Invoke-AutopilotRuntimePreflight -RepoRoot $RepoRoot -RuntimeRefresh $config.runtimeRefresh
    $healthDir = Join-Path $script:RunContext.dir $Issue.lint.issueId
    New-Item -ItemType Directory -Path $healthDir -Force | Out-Null
    $healthPath = Join-Path $healthDir 'runtime-health.json'
    [IO.File]::WriteAllText($healthPath, ($preflight | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    if ($preflight.status -ne 'pass') {
      Write-State $autoDir 'BLOCKED' $false 'RUNTIME_HEALTH_FAILED' $Issue.title 'environment' 'STOP_RUNTIME_HEALTH_FAILED'
      return
    }
  }
  $baseCommit = if ($resuming) { [string]$ResumeCheckpoint.baseCommit } else { (& git -C $RepoRoot rev-parse HEAD).Trim() }
  $worktree = if ($resuming) {
    [pscustomobject]@{ path=[string]$ResumeCheckpoint.worktree; branch=[string]$ResumeCheckpoint.branch; baseCommit=$baseCommit; reused=$true }
  } else {
    New-AutopilotIssueWorktree -RepoRoot $RepoRoot -IssueId $Issue.lint.issueId -BaseCommit $baseCommit -AllowDirtyReuse:($Phase -eq 'repair')
  }
  $script:Attempt = $Attempt
  $script:CurrentWorktree = $worktree.path
  $script:CurrentBranch = $worktree.branch
  $issueDir = if ($resuming -and $ResumeCheckpoint.artifacts.issueDirectory) { [string]$ResumeCheckpoint.artifacts.issueDirectory } else { Join-Path $script:RunContext.dir $Issue.lint.issueId }
  New-Item -ItemType Directory -Path $issueDir -Force | Out-Null
  $checkpointPath = if ($resuming) { [string]$script:RecoveryDecision.checkpointPath } else { Get-AutopilotIssueCheckpointPath -AutoDir $autoDir -IssueId $Issue.lint.issueId }
  if ($resuming) {
    $checkpoint = Read-AutopilotIssueCheckpoint -Path $checkpointPath
  } elseif ($Phase -eq 'repair' -and (Test-Path -LiteralPath $checkpointPath)) {
    $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase REPAIRING -IncrementDispatch repair
  } else {
    $checkpoint = New-AutopilotIssueCheckpoint -AutoDir $autoDir -IssueId $Issue.lint.issueId -ReadyPath (Join-Path $RepoRoot 'docs\backlog\ready-issues.md') -BaseCommit $baseCommit -Worktree $worktree.path -Branch $worktree.branch -AllowedPaths $Issue.contract.allowedPaths -ForbiddenPaths $Issue.contract.forbiddenPaths -ArtifactDirectory $issueDir
    $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase IMPLEMENTING -IncrementDispatch implementation
  }
  $script:IssueCheckpointPath = $checkpointPath
  $script:IssuePhase = [string]$checkpoint.phase
  if (!$resuming) {
    $contextPath = Join-Path $issueDir ("$Phase-$Attempt\context.json")
    $longRunningCommands = if ($config.issueExecutor.longRunningCommands) { @($config.issueExecutor.longRunningCommands) } else { @() }
    New-AutopilotContextPack -Issue $Issue.contract -Phase $Phase -RepoRoot $RepoRoot -Worktree $worktree.path -OutputPath $contextPath -PreviousPhaseSummary $PreviousSummary -LongRunningCommands $longRunningCommands | Out-Null
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
    $childResult = Invoke-ChildWithHeartbeat -Arguments $executorArgs -WorkingDirectory $worktree.path -TimeoutSeconds $executorTimeout -StallInspectSeconds $stallInspectSeconds -StallTerminateSeconds $stallTerminateSeconds -HeartbeatMilliseconds $heartbeatMilliseconds -LongRunningCommands $longRunningCommands -Task $Phase
    if ($childResult.exitCode -ne 0) {
    if ($childResult.stallTimedOut -and $config.repair -and $config.repair.enabled -eq $true -and $Attempt -lt 1) {
      Write-State $autoDir 'REPAIRING' $false 'EXECUTOR_STALL_REPAIR' $Issue.title 'executor timed out without worktree progress' ''
      Write-RunEvent 'executor.stall.retry-request' ([pscustomobject]@{ issueId = $Issue.lint.issueId; task = 'repair'; status = 'RETRY_REQUESTED'; executorPid = $childResult.executorPid; startedAt = $script:ExecutorStartedAt; lastProgressAt = $script:LastProgressAt; retryCount = 1; timeoutReason = 'no new evidence; retry scope limited to unfinished acceptance items with missing context supplied'; retiredAt = $script:RetiredAt; retiredStatus = $script:RetiredStatus })
      Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $Issue -Route $Route -Attempt 1 -Phase 'repair' -PreviousSummary '首次 executor 因 600 秒无新证据已退役；仅处理未完成验收项，补充缺失上下文，不得扩大范围或再次重派。'
      return
    }
    if ($childResult.stallTimedOut) {
      $blockedPath = Write-ExecutorStallBlockedIssue -RepoRoot $RepoRoot -Issue $Issue -RetiredExecutors $script:RetiredExecutors
      Write-RunEvent 'executor.stall.blocked' ([pscustomobject]@{ issueId = $Issue.lint.issueId; task = $Phase; status = 'BLOCKED'; stopReason = 'STOP_EXECUTOR_STALL_RETRY_EXHAUSTED'; executorPid = $childResult.executorPid; startedAt = $script:ExecutorStartedAt; lastProgressAt = $script:LastProgressAt; retryCount = 1; timeoutReason = 'second executor stalled; automatic retry exhausted'; retiredAt = $script:RetiredAt; retiredStatus = $script:RetiredStatus; evidencePath = $blockedPath })
      Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_STALL_BLOCKED' $Issue.title 'executor_stall_timeout' 'STOP_EXECUTOR_STALL_RETRY_EXHAUSTED'
      return
    }
    Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_PROCESS_FAILED' $Issue.title 'tool_config' 'STOP_EXECUTOR_PROCESS_FAILED'
    return
    }
    $executionRunId = if ($Attempt -eq 0) { $script:RunContext.id } else { "$($script:RunContext.id)-repair-$Attempt" }
    $resultPath = Join-Path (Join-Path $autoDir "runs\$executionRunId") 'result.json'
  } else {
    $resultPath = [string]$ResumeCheckpoint.artifacts.resultPath
    if (!$resultPath) { throw 'recoverable Issue checkpoint is missing resultPath' }
    Write-RunEvent 'issue.phase.resume' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision=$script:RecoveryDecision.action; status='RECOVERED'; reason=$script:RecoveryDecision.reason; checkpointPath=$checkpointPath })
  }
  if (Test-Path -LiteralPath $resultPath) {
    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($resuming -and $script:RecoveryDecision.action -eq 'RESUME_VALIDATION' -and [string]$result.status -eq 'blocked' -and [string]$result.failureCategory -eq 'environment' -and [string]$result.stopReason -eq 'STOP_VERIFICATION_FAILED') {
      $result.status = 'done'; $result.failureCategory = 'none'; $result.nextAction = 'VERIFY'; $result.stopReason = ''
      $result.validation = @()
      Write-RunEvent 'validation.environment-retry' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='RETRY_VALIDATION'; status='RECOVERED'; reason='one classified environment prerequisite retry uses the preserved implementation diff' })
    }
    if ($result.status -eq 'done') {
      if (!$resuming) {
        $implementedHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
        $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase IMPLEMENTED -Artifacts @{resultPath=$resultPath;issueDirectory=$issueDir;archiveReport=[string]$Issue.contract.archiveReport} -Evidence @{diffHash=$implementedHash}
        $script:IssuePhase = 'IMPLEMENTED'
      }
      $changes = @(Get-AutopilotIssueChanges -Worktree $worktree.path -BaseCommit $baseCommit)
      try {
        Assert-AutopilotAllowedChanges -ChangedPaths $changes -AllowedPaths $Issue.contract.allowedPaths -ForbiddenPaths $Issue.contract.forbiddenPaths | Out-Null
      } catch {
        $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_SCOPE_VIOLATION'
        $result.validation += [pscustomobject]@{ name = 'scope-allowlist'; status = 'fail'; message = $_.Exception.Message }
      }
      $resumePhase = if ($resuming) { [string]$checkpoint.phase } else { '' }
      $skipValidation = $resuming -and $resumePhase -in @('VALIDATED','REVIEWING','REVIEW_TOOL_BLOCKED','REVIEWED','CLOSING','IMPLEMENTATION_COMMITTED','CLOSEOUT_COMMITTED','REGISTERED')
      $evidencePaths = if ($skipValidation) { @($checkpoint.artifacts.evidencePaths) } else { @() }
      $failureSummary = ''
      $currentFingerprint = ''
      if ($result.status -eq 'done' -and !$skipValidation) {
        $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase VALIDATING -IncrementDispatch validation
        $script:IssuePhase = 'VALIDATING'
        Write-State $autoDir 'VERIFYING' $false 'EXECUTOR_COMPLETED' $Issue.title 'VERIFYING' ''
        $verifyDir = Join-Path $issueDir 'verify'
        New-Item -ItemType Directory -Path $verifyDir -Force | Out-Null
        for ($index = 0; $index -lt $Issue.contract.validationCommands.Count; $index++) {
          $validationCommand = [string]$Issue.contract.validationCommands[$index]
          if (!(Test-AutopilotPostExecutionVerificationRequired -Command $validationCommand)) {
            $result.validation += [pscustomobject]@{ name = "ready-command-$($index + 1)"; status = 'pass'; message = 'Ready lint passed before executor dispatch; post-closeout status is expected to be Done.' }
            continue
          }
          $evidencePath = Join-Path $verifyDir ("evidence-{0:00}.json" -f ($index + 1))
          $logPath = Join-Path $verifyDir ("command-{0:00}.log" -f ($index + 1))
          $evidence = Invoke-AutopilotVerificationCommand -IssueId $Issue.lint.issueId -Worktree $worktree.path -BaseCommit $baseCommit -Command $validationCommand -EvidencePath $evidencePath -LogPath $logPath
          $evidencePaths += $evidencePath
          $result.validation += [pscustomobject]@{ name = "ready-command-$($index + 1)"; status = $evidence.classification; message = "exitCode=$($evidence.exitCode); evidence=$evidencePath" }
          if ($evidence.exitCode -ne 0) {
            $classifierPath = Join-Path (Resolve-Path (Join-Path $scriptDir '..\..')).Path 'plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1'
            $classification = & $classifierPath -ErrorText ([string]$evidence.summary) -ExitCode ([int]$evidence.exitCode) | ConvertFrom-Json
            $result.status = 'blocked'
            $result.failureCategory = if ($classification.category -eq 'environment_prereq') { 'environment' } elseif ($classification.category -eq 'ready_issue_config') { 'ready_issue_config' } elseif ($classification.category -eq 'tool_config') { 'tool_config' } else { 'quality_security' }
            $result.nextAction = 'STOP'; $result.stopReason = 'STOP_VERIFICATION_FAILED'
            if ($classification.category -eq 'environment_prereq') {
              $environmentDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
              $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase IMPLEMENTED -Evidence @{diffHash=$environmentDiffHash} -IncrementEnvironmentRetry
              $script:IssuePhase = 'IMPLEMENTED'
            }
            $result | Add-Member -NotePropertyName failureFingerprint -NotePropertyValue $classification.failureFingerprint -Force
            $failureSummary = $classification.reason
            $currentFingerprint = $classification.failureFingerprint
            break
          }
        }
      }
      if ($result.status -eq 'done') {
        foreach ($evidencePath in $evidencePaths) {
          try {
            $boundEvidence = Get-Content -LiteralPath $evidencePath -Raw -Encoding UTF8 | ConvertFrom-Json
            Assert-AutopilotEvidenceCurrent -Evidence $boundEvidence -IssueId $Issue.lint.issueId -Worktree $worktree.path -BaseCommit $baseCommit | Out-Null
          } catch {
            $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_EVIDENCE_STALE'
            $result.validation += [pscustomobject]@{ name='evidence-current'; status='fail'; message=$_.Exception.Message }
            break
          }
        }
      }
      if ($result.status -eq 'done' -and !$skipValidation) {
        $verifiedHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
        $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase VALIDATED -Artifacts @{evidencePaths=@($evidencePaths)} -Evidence @{diffHash=$verifiedHash;verificationDiffHash=$verifiedHash}
        $script:IssuePhase = 'VALIDATED'
      }
      $effectiveRoute = Get-AutopilotRoute -Issue $Issue.contract -ChangedPaths @(Get-AutopilotIssueChanges -Worktree $worktree.path -BaseCommit $baseCommit)
      if ($result.status -eq 'done') {
        try {
          Assert-AutopilotAllowedChanges -ChangedPaths @(Get-AutopilotIssueChanges -Worktree $worktree.path -BaseCommit $baseCommit) -AllowedPaths $Issue.contract.allowedPaths -ForbiddenPaths $Issue.contract.forbiddenPaths | Out-Null
        } catch {
          $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_SCOPE_VIOLATION'
          $result | Add-Member -NotePropertyName scopeViolationCount -NotePropertyValue 1 -Force
          $result.validation += [pscustomobject]@{ name = 'post-validation-scope-allowlist'; status = 'fail'; message = $_.Exception.Message }
        }
      }
      $result | Add-Member -NotePropertyName evidencePaths -NotePropertyValue @($evidencePaths) -Force
      $result | Add-Member -NotePropertyName verificationBaseCommit -NotePropertyValue $baseCommit -Force
      $result | Add-Member -NotePropertyName verifiedDiffHash -NotePropertyValue $(if ($result.status -eq 'done') { Get-AutopilotDiffHash -Worktree $worktree.path -BaseCommit $baseCommit } else { '' }) -Force
      $result | Add-Member -NotePropertyName reviewRequired -NotePropertyValue ([bool]$effectiveRoute.reviewRequired) -Force
      $result | Add-Member -NotePropertyName attempt -NotePropertyValue $Attempt -Force
      $result | Add-Member -NotePropertyName firstPassSuccess -NotePropertyValue ($Attempt -eq 0 -and $result.status -eq 'done') -Force
      $result | Add-Member -NotePropertyName manualInterventionCount -NotePropertyValue 0 -Force
      $result | Add-Member -NotePropertyName scopeViolationCount -NotePropertyValue $(if ($result.stopReason -eq 'STOP_SCOPE_VIOLATION') { 1 } else { 0 }) -Force
      $checkpoint = Read-AutopilotIssueCheckpoint -Path $checkpointPath
      foreach ($metricName in @('implementationDispatchCount','validationDispatchCount','reviewDispatchCount','repairDispatchCount','closeoutDispatchCount','runResumeCount','phaseRestartCount','manualRecoveryCount','toolConfigBlockCount','environmentRetryCount','duplicateDispatchBlockedCount','wallClockSeconds')) {
        $result | Add-Member -NotePropertyName $metricName -NotePropertyValue (Get-AutopilotCheckpointProperty $checkpoint.metrics $metricName 0) -Force
      }
      $result | Add-Member -NotePropertyName phaseDurationsSeconds -NotePropertyValue $checkpoint.metrics.phaseDurationsSeconds -Force
      $result | Add-Member -NotePropertyName resumedFromPhase -NotePropertyValue $(if ($resuming) { $resumePhase } else { '' }) -Force
      $closed = $false
      if ($result.status -eq 'done') { $script:FailureFingerprint = $null }
      if ($result.status -eq 'done' -and $effectiveRoute.reviewRequired -and $resuming -and $resumePhase -eq 'REVIEW_TOOL_BLOCKED') {
        $manualReviewPath = [string]$checkpoint.artifacts.reviewResultPath
        if ($manualReviewPath -and (Test-Path -LiteralPath $manualReviewPath -PathType Leaf)) {
          try {
            $manualReview = Get-Content -LiteralPath $manualReviewPath -Raw -Encoding UTF8 | ConvertFrom-Json
            $manualReviewHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
            $manualDisposition = Get-AutopilotReviewDisposition -ReviewResult $manualReview -ExpectedIssueId $Issue.lint.issueId -ExpectedDiffHash $manualReviewHash
            if ($manualDisposition.action -eq 'PASS') {
              $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase REVIEWED -Evidence @{diffHash=$manualReviewHash;reviewDiffHash=$manualReviewHash} -IncrementManualRecovery
              $resumePhase = 'REVIEWED'
              $script:IssuePhase = 'REVIEWED'
              Write-RunEvent 'review.manual-pass-consumed' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='PASS'; status='REVIEWED'; reviewedDiffHash=$manualReviewHash })
            }
          } catch {
            Write-RunEvent 'review.manual-pass-rejected' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='RETRY_TOOL'; status='REVIEW_TOOL_BLOCKED'; reason=$_.Exception.Message })
          }
        }
      }
      $reviewAlreadyPassed = $resuming -and $resumePhase -in @('REVIEWED','CLOSING','IMPLEMENTATION_COMMITTED','CLOSEOUT_COMMITTED','REGISTERED')
      if ($result.status -eq 'done' -and $effectiveRoute.reviewRequired -and $reviewAlreadyPassed) {
        $reviewPath = [string]$checkpoint.artifacts.reviewResultPath
        if (!(Test-Path -LiteralPath $reviewPath -PathType Leaf)) { throw 'reviewed checkpoint is missing bound Reviewer result' }
        $review = Get-Content -LiteralPath $reviewPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $currentReviewHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
        $reviewDisposition = Get-AutopilotReviewDisposition -ReviewResult $review -ExpectedIssueId $Issue.lint.issueId -ExpectedDiffHash $currentReviewHash
        if ($reviewDisposition.action -ne 'PASS') { throw 'checkpoint Reviewer evidence no longer permits closeout' }
        $result | Add-Member -NotePropertyName review -NotePropertyValue $review -Force
        $result | Add-Member -NotePropertyName reviewedDiffHashExpected -NotePropertyValue $currentReviewHash -Force
      }
      if ($result.status -eq 'done' -and $effectiveRoute.reviewRequired -and !$reviewAlreadyPassed) {
        $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase REVIEWING -IncrementDispatch review
        $script:IssuePhase = 'REVIEWING'
        Write-State $autoDir 'REVIEWING' $false 'VERIFICATION_COMPLETED' $Issue.title 'REVIEWING' ''
        $reviewDir = Join-Path $issueDir 'review'; New-Item -ItemType Directory -Path $reviewDir -Force | Out-Null
        $diffPath = Join-Path $reviewDir 'final.diff'
        Write-AutopilotReviewDiff -Text (Get-AutopilotDiffText -Worktree $worktree.path -BaseCommit $baseCommit) -OutputPath $diffPath
        $requestPath = Join-Path $reviewDir 'request.json'
        $request = New-AutopilotReviewRequest -IssueId $Issue.lint.issueId -ReadyPath (Join-Path $worktree.path 'docs\backlog\ready-issues.md') -DiffPath $diffPath -EvidencePaths $evidencePaths -OutputPath $requestPath
        if (!$config.issueReviewer -or $config.issueReviewer.enabled -ne $true) {
          $result.status = 'blocked'; $result.failureCategory = 'tool_config'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEWER_REQUIRED'
        } else {
          $reviewPath = Join-Path $reviewDir 'result.json'
          $review = Invoke-AutopilotReviewer -Worktree $worktree.path -RequestPath $requestPath -ResultPath $reviewPath -SchemaPath (Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\schemas\review-result.schema.json') -Model $config.issueReviewer.model -Thinking $config.issueReviewer.thinking
          $result | Add-Member -NotePropertyName review -NotePropertyValue $review -Force
          $result | Add-Member -NotePropertyName reviewedDiffHashExpected -NotePropertyValue $request.diffSha256 -Force
          $reviewDisposition = Get-AutopilotReviewDisposition -ReviewResult $review -ExpectedIssueId $Issue.lint.issueId -ExpectedDiffHash $request.diffSha256
          if ($reviewDisposition.action -eq 'PASS') {
            $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase REVIEWED -Artifacts @{reviewRequestPath=$requestPath;reviewResultPath=$reviewPath} -Evidence @{diffHash=$request.diffSha256;reviewDiffHash=$request.diffSha256}
            $script:IssuePhase = 'REVIEWED'
          } elseif ($reviewDisposition.action -eq 'REPAIR') {
            $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEW_NEEDS_REPAIR'
            $failureSummary = $reviewDisposition.summary; $currentFingerprint = $reviewDisposition.failureFingerprint
            $result | Add-Member -NotePropertyName failureFingerprint -NotePropertyValue $currentFingerprint -Force
          } elseif ($reviewDisposition.action -eq 'BLOCK') {
            $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEW_FAILED'
          } elseif ($reviewDisposition.action -eq 'BLOCK_TOOL') {
            $result.status = 'blocked'; $result.failureCategory = 'tool_config'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEWER_TOOL_FAILURE'
            $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase REVIEW_TOOL_BLOCKED -Artifacts @{reviewRequestPath=$requestPath;reviewResultPath=$reviewPath} -Evidence @{diffHash=$request.diffSha256} -IncrementToolConfigBlock
            $script:IssuePhase = 'REVIEW_TOOL_BLOCKED'
            if ([int]$checkpoint.metrics.reviewDispatchCount -ge 2) { $result.stopReason = 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED' }
          }
        }
      }
      if ($result.status -eq 'done') {
        $finalChanges = @(Get-AutopilotIssueChanges -Worktree $worktree.path -BaseCommit $baseCommit)
        try {
          Assert-AutopilotAllowedChanges -ChangedPaths $finalChanges -AllowedPaths $Issue.contract.allowedPaths -ForbiddenPaths $Issue.contract.forbiddenPaths | Out-Null
        } catch {
          $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_SCOPE_VIOLATION'
          $result.scopeViolationCount = 1
          $result.validation += [pscustomobject]@{ name = 'final-scope-allowlist'; status = 'fail'; message = $_.Exception.Message }
        }
      }
      if ($result.status -eq 'done' -and $config.closeout -and $config.closeout.enabled -eq $true) {
        $terminalResume = $resuming -and $resumePhase -in @('CLOSEOUT_COMMITTED','REGISTERED')
        if (!$terminalResume) { $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase CLOSING -IncrementDispatch closeout }
        $script:IssuePhase = 'CLOSING'
        Write-State $autoDir 'COMMITTING' $false 'CLOSEOUT_START' $Issue.title 'COMMITTING' ''
        $scoreEvidence = $null
        $scoreShadowEvidence = $null
        if ($script:TaskScoringActive) {
          $reportPath = Join-Path $worktree.path $Issue.contract.archiveReport
          $isStockIssue = (Get-IssueBodyByTitle (Join-Path $worktree.path 'docs\backlog\ready-issues.md') $Issue.title) -match '\[stock:[^\]]+\]'
          $checkpoint = Read-AutopilotIssueCheckpoint -Path $checkpointPath
          foreach ($metricName in @('implementationDispatchCount','validationDispatchCount','reviewDispatchCount','repairDispatchCount','closeoutDispatchCount','runResumeCount','phaseRestartCount','manualRecoveryCount','toolConfigBlockCount','environmentRetryCount','duplicateDispatchBlockedCount','wallClockSeconds')) {
            $result | Add-Member -NotePropertyName $metricName -NotePropertyValue (Get-AutopilotCheckpointProperty $checkpoint.metrics $metricName 0) -Force
          }
          $scoreEvidence = New-AutopilotTaskScoreEvidenceFromResult -Result $result -ReportPath $reportPath -ImplementationCommit ('0' * 40) -StockIssueTarget $isStockIssue
          if ($config.taskScoring.candidateVersion -eq $script:AutopilotTaskScoreV2CandidateVersion -and $config.taskScoring.candidateEnabled -ne $true) {
            $scoreShadowEvidence = New-AutopilotTaskScoreV2EvidenceFromResult -Result $result -ReportPath $reportPath -ImplementationCommit ('0' * 40) -StockIssueTarget $isStockIssue
          }
        }
        $closeout = Complete-AutopilotIssueCloseout -RepoRoot $RepoRoot -Worktree $worktree.path -Issue $Issue.contract -AutoMerge $false -BaseBranch $configuredBaseBranch -ExpectedBaseCommit $baseCommit -ScoreEvidence $scoreEvidence -ScoreShadowEvidence $scoreShadowEvidence -TaskScoringConfig $(if ($script:TaskScoringActive) { $config.taskScoring } else { $null })
        $script:LastCommit = $closeout.commit
        $result.gitSummary | Add-Member -NotePropertyName commit -NotePropertyValue $closeout.commit -Force
        $result.gitSummary | Add-Member -NotePropertyName implementationCommit -NotePropertyValue $closeout.implementationCommit -Force
        $result.gitSummary | Add-Member -NotePropertyName closeoutCommit -NotePropertyValue $closeout.closeoutCommit -Force
        if ($closeout.score) { $result | Add-Member -NotePropertyName taskScore -NotePropertyValue $closeout.score -Force }
        if ($closeout.scoreShadow) { $result | Add-Member -NotePropertyName taskScoreV2Shadow -NotePropertyValue $closeout.scoreShadow -Force }
        $result.nextAction = 'CHECKPOINT'
        $closeoutDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
        $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase CLOSEOUT_COMMITTED -Evidence @{implementationCommit=[string]$closeout.implementationCommit;closeoutCommit=[string]$closeout.closeoutCommit;diffHash=$closeoutDiffHash}
        $script:IssuePhase = 'CLOSEOUT_COMMITTED'
        $merged = $false
        if ([bool]$config.autoMerge) {
          $mergeResult = Merge-AutopilotIssueCloseoutCommit -RepoRoot $RepoRoot -Commit $closeout.closeoutCommit -ExpectedBaseCommit $baseCommit
          $merged = [bool]$mergeResult.merged
        }
        $result | Add-Member -NotePropertyName merged -NotePropertyValue $merged -Force
        if ($merged) {
          $closeoutKey = Get-AutopilotCloseoutKey -IssueId $Issue.lint.issueId -Commit $closeout.commit -ReportPath $Issue.contract.archiveReport
          $ledgerPath = Join-Path $autoDir 'closeouts.ndjson'
          Register-AutopilotCloseout -LedgerPath $ledgerPath -Key $closeoutKey | Out-Null
          if (!(Test-AutopilotCloseoutRegistered -LedgerPath $ledgerPath -Key $closeoutKey)) { throw 'closeout ledger read-back failed' }
          if ($script:RetrospectiveActive) {
            $remainingAfterCloseout = if ($null -eq $script:IterationLimit) { $null } else { [Math]::Max(0, [int]$script:RemainingIterations - 1) }
            Add-AutopilotReviewCycleIssue -Path (Join-Path $autoDir 'state.json') -IssueId $Issue.lint.issueId -ScoreKey $closeout.score.key -ScoringVersion $closeout.score.scoringVersion -Threshold ([int]$config.retrospective.threshold) -BoundedBatchRemaining $remainingAfterCloseout | Out-Null
          }
          $checkpoint = Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase REGISTERED
          $script:IssuePhase = 'REGISTERED'
          $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $resultPath -Encoding UTF8
          Write-State $autoDir 'REGISTERED' $false 'CLOSEOUT_REGISTERED' $Issue.title 'REGISTERED' ''
          $registeredState = Read-AutopilotState -Path (Join-Path $autoDir 'state.json')
          if ([string]$registeredState.issueCheckpointPath -ne $checkpointPath -or [string]$registeredState.currentIssuePhase -ne 'REGISTERED') { throw 'registered AutoPilot state read-back failed' }
          if ($controlPlaneCanaryEnabled -and $null -ne $script:IterationLimit -and [int]$script:IterationLimit -eq 1) {
            $graphSnapshot = Get-AutopilotKnowledgeGraphIssueSnapshot -RepoRoot $RepoRoot
            $mergedHead = (& git -C $RepoRoot rev-parse HEAD).Trim()
            if (!$graphSnapshot.available -or [string]$graphSnapshot.cursor -ne $mergedHead) { throw "control-plane canary knowledge-graph cursor gate failed: $($graphSnapshot.stopReason) $($graphSnapshot.message)" }
            $script:LastCanaryFingerprint = $script:ControlPlaneFingerprint
            $script:LastCanaryReport = [string]$Issue.contract.archiveReport
            Write-State $autoDir 'REGISTERED' $false 'CONTROL_PLANE_CANARY_PASSED' $Issue.title 'REGISTERED' ''
            $canaryState = Read-AutopilotState -Path (Join-Path $autoDir 'state.json')
            if ([string]$canaryState.lastCanaryFingerprint -ne $script:ControlPlaneFingerprint -or [string]$canaryState.lastCanaryReport -ne [string]$Issue.contract.archiveReport) { throw 'control-plane canary state read-back failed' }
            Write-RunEvent 'control-plane.canary-passed' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='PASS'; status='CANARY_PASSED'; controlPlaneFingerprint=$script:ControlPlaneFingerprint; evidencePath=$Issue.contract.archiveReport; graphGitCursor=$graphSnapshot.cursor })
          }
          if ($script:TaskScoringActive -and $config.taskScoring.candidateVersion -eq $script:AutopilotTaskScoreV2CandidateVersion -and $config.taskScoring.candidateEnabled -ne $true) {
            $finalCheckpoint = Read-AutopilotIssueCheckpoint -Path $checkpointPath
            foreach ($metricName in @('implementationDispatchCount','validationDispatchCount','reviewDispatchCount','repairDispatchCount','closeoutDispatchCount','runResumeCount','phaseRestartCount','manualRecoveryCount','toolConfigBlockCount','environmentRetryCount','duplicateDispatchBlockedCount','wallClockSeconds')) {
              $result | Add-Member -NotePropertyName $metricName -NotePropertyValue (Get-AutopilotCheckpointProperty $finalCheckpoint.metrics $metricName 0) -Force
            }
            $finalV2Evidence = New-AutopilotTaskScoreV2EvidenceFromResult -Result $result -ReportPath (Join-Path $worktree.path $Issue.contract.archiveReport) -ImplementationCommit $closeout.implementationCommit -StockIssueTarget ([bool]$isStockIssue)
            $finalV2Score = New-AutopilotTaskScoreV2Shadow -Evidence $finalV2Evidence
            Set-AutopilotCloseoutCandidateScore -LedgerPath (Join-Path $autoDir 'candidate-score-shadows.ndjson') -Key $closeoutKey -Score $finalV2Score -PhaseMetrics $finalCheckpoint.metrics | Out-Null
            $result | Add-Member -NotePropertyName taskScoreV2Shadow -NotePropertyValue $finalV2Score -Force
            $result | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $resultPath -Encoding UTF8
          }
          $script:IssueCheckpointPath = ''
          $script:IssuePhase = ''
          Write-State $autoDir 'CHECKPOINT' $false 'ISSUE_CLOSED' $Issue.title 'CHECKPOINT' ''
          $closedState = Read-AutopilotState -Path (Join-Path $autoDir 'state.json')
          if ([string]$closedState.issueCheckpointPath -or [string]$closedState.currentIssuePhase) { throw 'closed AutoPilot state read-back failed' }
          Set-AutopilotIssueCheckpointPhase -Path $checkpointPath -Phase CLOSED | Out-Null
          Remove-AutopilotIssueCheckpoint -Path $checkpointPath -Closed
          Write-RunEvent 'closeout' ([pscustomobject]@{ issueId = $Issue.lint.issueId; title = $Issue.title; decision = 'DONE'; status = 'CLOSED'; reason = 'ledger/state/graph read-back completed before checkpoint retirement'; evidencePath = $Issue.contract.archiveReport; commit = $closeout.commit })
          $closed = $true
        }
      }
      $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $resultPath -Encoding UTF8
      if ($Attempt -gt 0) { Copy-Item -LiteralPath $resultPath -Destination (Join-Path $script:RunContext.dir 'result.json') -Force }
      if ($result.status -eq 'blocked' -and (Test-AutopilotCodeRepairAllowed -FailureCategory $result.failureCategory -StopReason $result.stopReason) -and $config.repair -and $config.repair.enabled -eq $true -and (Test-AutopilotRetryAllowed -PreviousFingerprint $script:FailureFingerprint -CurrentFingerprint $currentFingerprint -Attempt $Attempt)) {
        $script:FailureFingerprint = $currentFingerprint
        Write-State $autoDir 'REPAIRING' $false 'REPAIR_START' $Issue.title $failureSummary ''
        Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $Issue -Route $Route -Attempt ($Attempt + 1) -Phase 'repair' -PreviousSummary $failureSummary
        return
      }
      if ($result.status -eq 'done' -and $closed) {
        Write-Host 'ISSUE_DURABLY_CLOSED'
      } elseif ($result.status -eq 'done') { Write-State $autoDir 'CLOSING' $false 'VERIFICATION_COMPLETED' $Issue.title 'CLOSING' '' } elseif ($result.stopReason -eq 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED') { Write-State $autoDir 'PAUSED' $false 'REVIEW_TOOL_BLOCKED' $Issue.title $result.failureCategory $result.stopReason } else { Write-State $autoDir 'BLOCKED' $false 'VERIFICATION_BLOCKED' $Issue.title $result.failureCategory $result.stopReason }
    } else {
      Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_BLOCKED' $Issue.title $result.failureCategory $result.stopReason
    }
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-state.ps1')
. (Join-Path $scriptDir 'autopilot-control-plane-fingerprint.ps1')
. (Join-Path $scriptDir 'autopilot-task-score.ps1')
. (Join-Path $scriptDir 'autopilot-ready.ps1')
. (Join-Path $scriptDir 'autopilot-route.ps1')
. (Join-Path $scriptDir 'autopilot-worktree.ps1')
. (Join-Path $scriptDir 'autopilot-progress.ps1')
. (Join-Path $scriptDir 'autopilot-context.ps1')
. (Join-Path $scriptDir 'autopilot-verify.ps1')
. (Join-Path $scriptDir 'autopilot-review.ps1')
. (Join-Path $scriptDir 'autopilot-issue-checkpoint.ps1')
. (Join-Path $scriptDir 'autopilot-recover.ps1')
. (Join-Path $scriptDir 'autopilot-refill.ps1')
. (Join-Path $scriptDir 'autopilot-closeout.ps1')
if (!$ConfigPath) {
  $ConfigPath = Join-Path $scriptDir "codex-autopilot.config.json"
}
$config = Read-JsonFile $ConfigPath
$script:TaskScoringActive = if ($config.PSObject.Properties.Name -contains 'taskScoring') { Test-AutopilotTaskScoringActive $config.taskScoring } else { $false }
$script:RetrospectiveActive = Test-AutopilotRetrospectiveActive $(if ($config.PSObject.Properties.Name -contains 'taskScoring') { $config.taskScoring } else { $null }) $(if ($config.PSObject.Properties.Name -contains 'retrospective') { $config.retrospective } else { $null })
if ($config.repoRoot) {
  $RepoRoot = $config.repoRoot
}
$configuredBaseBranch = if ($config.baseBranch) { [string]$config.baseBranch } else { 'master' }
$actualBaseBranch = (& git -C $RepoRoot branch --show-current 2>$null | Select-Object -First 1).Trim()
if ($actualBaseBranch -ne $configuredBaseBranch) { throw "AutoPilot control plane requires base branch $configuredBaseBranch, actual=$actualBaseBranch" }

$autoDir = if ($config.autopilotDir) { $config.autopilotDir } else { Join-Path $RepoRoot ".codex-autopilot" }
$controlPlaneCanaryEnabled = $null -ne $config.controlPlaneCanary -and $config.controlPlaneCanary.enabled -eq $true
$script:ControlPlaneFingerprint = if ($controlPlaneCanaryEnabled) { Get-AutopilotControlPlaneFingerprint -RepoRoot $RepoRoot -Paths @($config.controlPlaneCanary.fingerprintPaths) } else { '' }
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
$applyMode = [bool]$ApplyBacklogSplit -and -not [bool]$DryRun -and -not [bool]$ExplainNextAction
$dryRunMode = -not $applyMode
$script:RunLock = $null
$script:CurrentWorktree = ''
$script:CurrentBranch = ''
$script:ExecutorPid = $null
$script:ExecutorStartedAt = $null
$script:LastProgressAt = $null
$script:TimeoutReason = $null
$script:RetiredAt = $null
$script:RetiredStatus = $null
$script:RetiredExecutors = @()
$script:LastCommit = $null
$script:FailureFingerprint = $null
$script:Attempt = 0
$script:IssueCheckpointPath = ''
$script:IssuePhase = ''
$script:LastCanaryFingerprint = ''
$script:LastCanaryReport = ''
$script:RecoveryDecision = $null
Initialize-IterationProgress $autoDir $readyPath $MaxIterations ([bool]$DryRun -or [bool]$ExplainNextAction)
$existingStatePath = Join-Path $autoDir 'state.json'
if (Test-Path -LiteralPath $existingStatePath) {
  try {
    $existingState = Get-Content -LiteralPath $existingStatePath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($existingState.PSObject.Properties.Name -contains 'retiredExecutors') { $script:RetiredExecutors = @($existingState.retiredExecutors) }
    if ($existingState.PSObject.Properties.Name -contains 'lastCanaryFingerprint') { $script:LastCanaryFingerprint = [string]$existingState.lastCanaryFingerprint }
    if ($existingState.PSObject.Properties.Name -contains 'lastCanaryReport') { $script:LastCanaryReport = [string]$existingState.lastCanaryReport }
  } catch { $script:RetiredExecutors = @() }
}
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
  $refillDecision = if ($checkpoint -eq "CONTINUE" -and $readyIssues.Count -eq 0) { Get-AutopilotRefillDecision -RepoRoot $RepoRoot } else { $null }
  $candidates = if ($refillDecision -and $refillDecision.action -eq 'PLAN_READY') { @($refillDecision.candidates) } else { @() }
  $stopReason = if ($checkpoint -eq "CONTINUE" -and $readyIssues.Count -eq 0 -and $candidates.Count -eq 0) { Get-StopReasonForEmptyPool $readyPath } else { $checkpoint }
  $batchPlan = if ($readyIssues.Count -gt 0 -and $readyIssues[0].lint.status -eq "pass") { Get-ReadyIssueBatchPlan $readyIssues $maxParallelIssues $parallelSafetyMode } else { $null }
  $decision = if ($checkpoint -ne "CONTINUE") { "STOP" } elseif ($readyIssues.Count -gt 0 -and $readyIssues[0].lint.status -ne "pass") { "STOP_READY_LINT_FAILED" } elseif ($readyIssues.Count -gt 0) { $batchPlan.decision } elseif ($refillDecision -and $refillDecision.action -eq 'UNBLOCK_FIRST') { "UNBLOCK_FIRST" } elseif ($candidates.Count -gt 0) { "SPLIT_BACKLOG" } else { "STOP" }
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
  Write-NextActionExplanation $checkpoint $readyIssues $candidates $stopReason $refillDecision
  exit 0
}

try {
  if ($applyMode) {
    $recovery = Get-AutopilotRecoveryDecision -AutoDir $autoDir
    if ($recovery.action -eq 'REFUSE_SECOND_INSTANCE') { Write-Host 'RUN_LOCK_ACTIVE'; throw 'Another AutoPilot run is active.' }
    $resumeActions = @('RESUME_VALIDATION','RESUME_REVIEW','RESUME_CLOSEOUT','RESUME_SCORE_AND_CLOSEOUT','RESUME_MERGE_AND_REGISTER','RESUME_FINALIZE')
    if ($recovery.action -eq 'CLEAN_CLOSED_CHECKPOINT') {
      Remove-Item -LiteralPath (Join-Path $autoDir 'run.lock') -Force -ErrorAction SilentlyContinue
      Remove-AutopilotIssueCheckpoint -Path $recovery.checkpointPath -Closed
      Write-RunEvent 'recovery' ([pscustomobject]@{ decision='CLEAN_CLOSED_CHECKPOINT'; status='RECOVERED'; reason='final state was already read back before checkpoint retirement' })
      $recovery = [pscustomobject]@{ action='NEW_RUN'; reason='closed checkpoint retired' }
    }
    if ($recovery.action -in @('RESUME_FROM_CHECKPOINT') + $resumeActions) {
      Remove-Item -LiteralPath (Join-Path $autoDir 'run.lock') -Force -ErrorAction SilentlyContinue
      if ($recovery.action -in $resumeActions) {
        $resumeMetricArgs = @{ Path=$recovery.checkpointPath; Phase=[string]$recovery.checkpoint.phase; IncrementRunResume=$true }
        if ([string]$recovery.checkpoint.phase -in @('VALIDATING','REVIEWING','CLOSING','REPAIRING')) { $resumeMetricArgs.IncrementPhaseRestart = $true }
        $recovery.checkpoint = Set-AutopilotIssueCheckpointPhase @resumeMetricArgs
        $script:RecoveryDecision = $recovery
        $script:IssueCheckpointPath = [string]$recovery.checkpointPath
        $script:IssuePhase = [string]$recovery.checkpoint.phase
      }
      Write-Host 'STALE_RUN_LOCK_REMOVED'
      Write-RunEvent 'recovery' ([pscustomobject]@{ decision = $recovery.action; status = 'RECOVERED'; reason = $recovery.reason })
    } elseif ($recovery.action -eq 'PAUSE_REVIEW_TOOL_BLOCKED') {
      Write-State $autoDir 'PAUSED' $false 'REVIEW_TOOL_BLOCKED' ([string]$recovery.issueId) $recovery.reason 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED'
      Write-Host 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED'
      exit 0
    } elseif ($recovery.action -eq 'PAUSE_ENVIRONMENT_RETRY_EXHAUSTED') {
      Write-State $autoDir 'PAUSED' $false 'ENVIRONMENT_RETRY_EXHAUSTED' ([string]$recovery.issueId) $recovery.reason 'STOP_ENVIRONMENT_RETRY_EXHAUSTED'
      Write-Host 'STOP_ENVIRONMENT_RETRY_EXHAUSTED'
      exit 0
    } elseif ($recovery.action -in @('VERIFY_UNCOMMITTED','QUARANTINE')) {
      Write-State $autoDir 'BLOCKED' $false 'RECOVERY_REVIEW_REQUIRED' '' $recovery.reason 'STOP_RECOVERY_REVIEW_REQUIRED'
      Write-Host 'STOP_RECOVERY_REVIEW_REQUIRED'
      exit 0
    }
    $script:RunLock = New-RunLock $autoDir $maxRunMinutes "apply-backlog-split"
  }

  $boundaryStatePath = Join-Path $autoDir 'state.json'
  $boundaryCheckpoint = Test-Checkpoint $autoDir
  if ($boundaryCheckpoint -eq 'CONTINUE' -and !$script:RecoveryDecision -and (Test-AutopilotControlPlaneCanaryRequired -Enabled $controlPlaneCanaryEnabled -IterationLimit $script:IterationLimit -CurrentFingerprint $(if ($script:ControlPlaneFingerprint) { $script:ControlPlaneFingerprint } else { 'disabled' }) -LastCanaryFingerprint $script:LastCanaryFingerprint)) {
    Write-RunEvent 'control-plane.canary-required' ([pscustomobject]@{ decision='STOP'; status='CONTROL_PLANE_CANARY_REQUIRED'; stopReason='CONTROL_PLANE_CANARY_REQUIRED'; controlPlaneFingerprint=$script:ControlPlaneFingerprint })
    Write-State $autoDir 'PAUSED' $false 'CONTROL_PLANE_CANARY_REQUIRED' '' 'control-plane fingerprint requires a successful user-started single-Issue canary' 'CONTROL_PLANE_CANARY_REQUIRED'
    Write-Host 'CONTROL_PLANE_CANARY_REQUIRED'
    exit 0
  }
  if (Test-Path -LiteralPath $boundaryStatePath) {
    $boundaryState = Read-AutopilotState -Path $boundaryStatePath
    $boundedReviewDue = $null -ne $boundaryState.iterationLimit -and [int]$boundaryState.remainingIterations -le 0
    if (!$script:RecoveryDecision -and [bool]$boundaryState.retrospectiveDue -and ($null -eq $boundaryState.iterationLimit -or $boundedReviewDue)) {
      Write-RunEvent 'stop' ([pscustomobject]@{ decision='STOP'; status='STOP_RETROSPECTIVE_REQUIRED'; stopReason='STOP_RETROSPECTIVE_REQUIRED'; reviewCycleId=$boundaryState.reviewCycleId; reviewCycleCompletedCount=$boundaryState.reviewCycleCompletedCount })
      Write-State $autoDir 'STOP_RETROSPECTIVE_REQUIRED' ([bool]$DryRun) 'RETROSPECTIVE_REQUIRED' '' 'pending retrospective blocks new task dispatch' 'STOP_RETROSPECTIVE_REQUIRED'
      Write-Host 'STOP_RETROSPECTIVE_REQUIRED'
      exit 0
    }
  }

  if (!$script:RecoveryDecision -and $null -ne $script:IterationLimit -and $script:IterationCompleted -ge $script:IterationLimit) {
    Write-RunEvent "stop" ([pscustomobject]@{ decision = "STOP"; status = "STOP_ITERATION_LIMIT_REACHED"; stopReason = "STOP_ITERATION_LIMIT_REACHED" })
    Remove-Item -LiteralPath (Join-Path $autoDir 'enabled.flag') -Force -ErrorAction SilentlyContinue
    Write-State $autoDir "STOP_ITERATION_LIMIT_REACHED" ([bool]$DryRun) "STOP" "" "STOP_ITERATION_LIMIT_REACHED" "STOP_ITERATION_LIMIT_REACHED"
    Write-Host "STOP_ITERATION_LIMIT_REACHED"
    exit 0
  }

  for ($loop = 1; $loop -le $MaxLoops; $loop++) {
    $checkpoint = Test-Checkpoint $autoDir
    Write-RunEvent "checkpoint" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "CHECKPOINT"; loop = $loop })
    Write-Host "checkpoint[$loop]=$checkpoint"
    if ($checkpoint -ne "CONTINUE") {
      if ($script:RecoveryDecision -and $script:RecoveryDecision.action -in @('RESUME_VALIDATION','RESUME_REVIEW','RESUME_CLOSEOUT','RESUME_SCORE_AND_CLOSEOUT','RESUME_MERGE_AND_REGISTER','RESUME_FINALIZE')) {
        Write-RunEvent 'checkpoint.active-issue-closeout' ([pscustomobject]@{ checkpoint=$checkpoint; decision=$script:RecoveryDecision.action; status='RECOVERING'; reason='stop/pause prevents new selection but does not abandon the already-started durable Issue' })
      } else {
      Write-RunEvent "stop" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "STOP"; status = $checkpoint; stopReason = $checkpoint; loop = $loop })
      Write-State $autoDir $checkpoint ([bool]$DryRun) "STOP" "" $checkpoint $checkpoint
      Write-Host $checkpoint
      exit 0
      }
    }

    $readyIssues = @(Get-ReadyIssues $readyPath $RepoRoot $scriptDir)
    if ($script:RecoveryDecision -and @($readyIssues | Where-Object { $_.lint.issueId -eq $script:RecoveryDecision.issueId }).Count -eq 0 -and $script:RecoveryDecision.action -in @('RESUME_MERGE_AND_REGISTER','RESUME_FINALIZE')) {
      $readyIssues = @((Get-RecoveryIssueFromCheckpoint -Recovery $script:RecoveryDecision -RepoRoot $RepoRoot)) + $readyIssues
    }
    if ($readyIssues.Count -gt 0) {
      if ($script:RecoveryDecision -and $script:RecoveryDecision.action -in @('RESUME_VALIDATION','RESUME_REVIEW','RESUME_CLOSEOUT','RESUME_SCORE_AND_CLOSEOUT','RESUME_MERGE_AND_REGISTER','RESUME_FINALIZE')) {
        $recoverable = @($readyIssues | Where-Object { $_.lint.issueId -eq $script:RecoveryDecision.issueId })
        if ($recoverable.Count -ne 1) {
          Write-State $autoDir 'BLOCKED' $false 'RECOVERY_READY_MISSING' ([string]$script:RecoveryDecision.issueId) 'recoverable Issue no longer has one Ready contract' 'STOP_RECOVERY_REVIEW_REQUIRED'
          Write-Host 'STOP_RECOVERY_REVIEW_REQUIRED'
          exit 0
        }
        $readyIssues = @($recoverable[0]) + @($readyIssues | Where-Object { $_.lint.issueId -ne $script:RecoveryDecision.issueId })
      }
      if ($readyIssues[0].lint.status -ne "pass") {
        Write-RunEvent "ready-lint" ([pscustomobject]@{
          issueId = $readyIssues[0].lint.issueId
          title = $readyIssues[0].title
          decision = "STOP"
          status = "fail"
          stopReason = "STOP_READY_LINT_FAILED"
           missingGate = "ready-lint"
           failureCategory = $readyIssues[0].lint.failureCategory
           errorCode = $readyIssues[0].lint.errorCode
        })
        Write-State $autoDir "STOP_READY_LINT_FAILED" ([bool]$DryRun) "STOP" $readyIssues[0].title "STOP_READY_LINT_FAILED" "STOP_READY_LINT_FAILED"
        Write-Host "STOP_READY_LINT_FAILED"
        Write-Host "selected=$($readyIssues[0].title)"
         Write-Host "missingGate=ready-lint"
         Write-Host "failureCategory=$($readyIssues[0].lint.failureCategory)"
         Write-Host "errorCode=$($readyIssues[0].lint.errorCode)"
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
        if ($script:RecoveryDecision -and $selectedIssue.lint.issueId -eq $script:RecoveryDecision.issueId) {
          Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $selectedIssue -Route $selectedRoute -ResumeCheckpoint $script:RecoveryDecision.checkpoint
        } else {
          Invoke-IssueExecutor $RepoRoot $ConfigPath $selectedIssue $selectedRoute
        }
      }
      exit 0
    }

    $refillDecision = Get-AutopilotRefillDecision -RepoRoot $RepoRoot
    $readyPlannerEnabled = ($config.PSObject.Properties.Name -contains 'readyPlanner') -and $null -ne $config.readyPlanner -and $config.readyPlanner.enabled -eq $true
    if ($applyMode -and $readyPlannerEnabled -and (Test-AutopilotReadyPlanningAllowed -Action $refillDecision.action)) {
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
    if ($refillDecision.action -in @('STOP_KG_REFILL_UNAVAILABLE','STOP_KG_REFILL_STALE')) {
      Write-RunEvent 'refill.graph-stop' ([pscustomobject]@{ decision = 'STOP'; status = $refillDecision.action; stopReason = $refillDecision.action; reason = $refillDecision.reason; failureCategory = $refillDecision.failureCategory })
      Write-State $autoDir 'BLOCKED' $false 'STOP' '' $refillDecision.reason $refillDecision.action
      Write-Host $refillDecision.action
      Write-Host "failureCategory=$($refillDecision.failureCategory)"
      Write-Host "refillReason=$($refillDecision.reason)"
      exit 0
    }
    if ($refillDecision.action -eq 'UNBLOCK_FIRST') {
      Write-State $autoDir 'BLOCKED' $false 'UNBLOCK_REQUIRED' '' $refillDecision.reason 'STOP_UNBLOCK_PLANNER_UNAVAILABLE'
      Write-Host 'STOP_UNBLOCK_PLANNER_UNAVAILABLE'
      exit 0
    }
    if ($refillDecision.action -eq 'NO_CANDIDATES') {
      $stopReason = Get-StopReasonForEmptyPool $readyPath
      Write-RunEvent "stop" ([pscustomobject]@{ decision = "STOP"; status = $stopReason; stopReason = $stopReason; reason = $refillDecision.reason })
      Write-State $autoDir $stopReason $dryRunMode "STOP" "" $refillDecision.reason $stopReason
      Write-Host $stopReason
      Write-Host "refillReason=$($refillDecision.reason)"
      exit 0
    }
    if ($applyMode -and $refillDecision.action -eq 'PLAN_READY' -and !$readyPlannerEnabled) {
      Write-State $autoDir 'BLOCKED' $false 'READY_PLANNER_REQUIRED' '' $refillDecision.reason 'STOP_READY_PLANNER_UNAVAILABLE'
      Write-Host 'STOP_READY_PLANNER_UNAVAILABLE'
      exit 0
    }

    Write-Host "SPLIT_MODE"
    Write-Host "candidateSource=$($refillDecision.candidates[0].source)"
    $candidates = @($refillDecision.candidates)
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
      $candidateRef = if ($candidates[$index].marker) { $candidates[$index].marker } elseif ($candidates[$index].anchor) { $candidates[$index].anchor } else { $candidates[$index].name }
      Write-Host ("splitCandidate[{0}]={1}" -f ($index + 1), $candidateRef)
    }

    if ($dryRunMode) {
      Write-State $autoDir "DRY_RUN_SPLIT_PLANNED" $true "SPLIT_BACKLOG" "" "DRY_RUN_SPLIT_PLANNED" ""
      Write-RunEvent "split.dry_run" ([pscustomobject]@{ decision = "SPLIT_BACKLOG"; status = "DRY_RUN_SPLIT_PLANNED"; shouldSplitBacklog = $true })
      Write-Host "DRY_RUN_NO_BACKLOG_WRITE"
      exit 0
    }
    throw 'unreachable refill path: non-dry-run Ready creation requires the configured Ready Planner'
  }

  Write-RunEvent "stop" ([pscustomobject]@{ decision = "STOP"; status = "STOP_SESSION_LIMIT"; stopReason = "STOP_SESSION_LIMIT" })
  Write-State $autoDir "STOP_SESSION_LIMIT" $dryRunMode "STOP" "" "STOP_SESSION_LIMIT" "STOP_SESSION_LIMIT"
  Write-Host "STOP_SESSION_LIMIT"
} finally {
  Remove-RunLock $autoDir $script:RunLock
}
