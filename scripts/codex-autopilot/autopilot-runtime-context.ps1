function Read-JsonFile {
  param([string]$Path)
  if (!(Test-Path $Path)) {
    throw "Config not found: $Path"
  }
  return Get-Content -Encoding UTF8 -Raw $Path | ConvertFrom-Json
}

function New-AutopilotRuntimeContext {
  [CmdletBinding()]
  param(
    [Parameter(Mandatory)][string]$ScriptDirectory,
    [Parameter(Mandatory)][string]$RepoRoot,
    [string]$ConfigPath = '',
    [bool]$DryRun = $false,
    [bool]$ApplyBacklogSplit = $false,
    [bool]$ExplainNextAction = $false,
    [Nullable[int]]$IterationLimit = $null
  )

  $resolvedConfigPath = if ($ConfigPath) { $ConfigPath } else { Join-Path $ScriptDirectory 'codex-autopilot.config.json' }
  $config = Read-JsonFile -Path $resolvedConfigPath
  $resolvedRepoRoot = if ($config.repoRoot) { [string]$config.repoRoot } else { $RepoRoot }
  $configuredBaseBranch = if ($config.baseBranch) { [string]$config.baseBranch } else { 'master' }
  $actualBaseBranch = (Invoke-AutopilotGit -RepoRoot $resolvedRepoRoot -Arguments @('branch','--show-current') -ThrowOnFailure).stdout.Trim()
  if ($actualBaseBranch -ne $configuredBaseBranch) {
    throw "AutoPilot control plane requires base branch $configuredBaseBranch, actual=$actualBaseBranch"
  }

  $executionMode = Resolve-AutopilotExecutionMode -DryRun $DryRun -ExplainNextAction $ExplainNextAction -ApplyBacklogSplit $ApplyBacklogSplit
  $autoDir = if ($config.autopilotDir) { [string]$config.autopilotDir } else { Join-Path $resolvedRepoRoot '.codex-autopilot' }
  $canaryEnabled = $null -ne $config.controlPlaneCanary -and $config.controlPlaneCanary.enabled -eq $true
  $fingerprint = if ($canaryEnabled) {
    Get-AutopilotControlPlaneFingerprint -RepoRoot $resolvedRepoRoot -Paths @($config.controlPlaneCanary.fingerprintPaths)
  } else { '' }

  return [pscustomobject]@{
    schemaVersion = 1
    repoRoot = $resolvedRepoRoot
    configPath = $resolvedConfigPath
    config = $config
    executionMode = $executionMode
    baseBranch = $configuredBaseBranch
    autoDir = $autoDir
    readyPath = Join-Path $resolvedRepoRoot 'docs\backlog\ready-issues.md'
    controlPlaneFingerprint = $fingerprint
    controlPlaneCanaryEnabled = $canaryEnabled
    runLock = $null
    runInstanceId = ''
    leaseEpoch = ''
    iterationLimit = $IterationLimit
    startedAt = [datetimeoffset]::Now.ToString('o')
  }
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
    dryRun = [bool]$dryRunMode
    apply = [bool]$applyMode
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
