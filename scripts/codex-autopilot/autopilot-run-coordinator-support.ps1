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

  $output = & pwsh -NoProfile -ExecutionPolicy Bypass -File $lintPath -RepoRoot $RepoRoot -ReadyPath $ReadyPath -IssueTitle $IssueTitle 2>&1 | Out-String
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

function Commit-ReadyRefill {
  param([string]$RepoRoot, [string]$ReadyPath)
  $repoPrefix = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
  $readyFull = [IO.Path]::GetFullPath($ReadyPath)
  if (!$readyFull.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Ready path is outside repository.' }
  $relativeReady = $readyFull.Substring($repoPrefix.Length).Replace('\','/')
  $statusLines = Get-AutopilotNativeOutputLines (Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('status','--porcelain=v1') -ThrowOnFailure).stdout
  $otherChanges = @($statusLines | Where-Object { $_.Length -lt 4 -or $_.Substring(3).Trim('"').Replace('\','/') -ne $relativeReady })
  if ($otherChanges.Count -gt 0) { throw 'Cannot commit Ready refill while unrelated worktree changes exist.' }
  Assert-CurrentControlPlaneFence | Out-Null
  Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('add','--',$relativeReady) -ThrowOnFailure | Out-Null
  Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('diff','--cached','--check') -ThrowOnFailure | Out-Null
  Assert-CurrentControlPlaneFence | Out-Null
  Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('commit','-m','chore(autopilot): refill ready queue') -ThrowOnFailure | Out-Null
  return (Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
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
