param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path,
    [string]$AutopilotDir = '.codex-autopilot',
    [switch]$CheckHealth,
    [switch]$CheckGit,
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Push-Location $RepoRoot
try {
    $branch = (& git branch --show-current).Trim()
    $gitStatus = @((& git status --short)) -join [Environment]::NewLine
    $flagsRoot = Join-Path $RepoRoot $AutopilotDir

    $stopPath = Join-Path $flagsRoot 'stop.flag'
    $pausePath = Join-Path $flagsRoot 'pause.flag'
    $enabledPath = Join-Path $flagsRoot 'enabled.flag'
    $statePath = Join-Path $flagsRoot 'state.json'
    $state = if (Test-Path -LiteralPath $statePath) {
        try { Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $null }
    } else { $null }
    $configPath = Join-Path $RepoRoot 'scripts\codex-autopilot\codex-autopilot.config.json'
    $config = if (Test-Path -LiteralPath $configPath -PathType Leaf) {
        try { Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $null }
    } else { $null }
    $executionHost = if ($config -and $config.PSObject.Properties.Name -contains 'executionHost') { [string]$config.executionHost } else { 'cli-legacy' }
    $runLockPath = Join-Path $flagsRoot 'run.lock'
    $runLock = if (Test-Path -LiteralPath $runLockPath -PathType Leaf) {
        try { Get-Content -LiteralPath $runLockPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $null }
    } else { $null }
    $checkpointPath = if ($state -and $state.PSObject.Properties.Name -contains 'issueCheckpointPath') { [string]$state.issueCheckpointPath } else { '' }
    if ($checkpointPath -and ![IO.Path]::IsPathRooted($checkpointPath)) { $checkpointPath = Join-Path $RepoRoot $checkpointPath }
    $activeCheckpoint = if ($checkpointPath -and (Test-Path -LiteralPath $checkpointPath -PathType Leaf)) {
        try { Get-Content -LiteralPath $checkpointPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $null }
    } else { $null }
    $checkpointHash = if ($checkpointPath -and (Test-Path -LiteralPath $checkpointPath -PathType Leaf)) {
        (Get-FileHash -LiteralPath $checkpointPath -Algorithm SHA256).Hash.ToLowerInvariant()
    } else { '' }
    $controlPlaneFingerprint = ''
    if ($config -and ($config.PSObject.Properties.Name -contains 'controlPlaneCanary') -and $config.controlPlaneCanary -and $config.controlPlaneCanary.enabled -eq $true) {
        $fingerprintLibrary = Join-Path $RepoRoot 'scripts\codex-autopilot\autopilot-control-plane-fingerprint.ps1'
        if (Test-Path -LiteralPath $fingerprintLibrary -PathType Leaf) {
            . $fingerprintLibrary
            $controlPlaneFingerprint = Get-AutopilotControlPlaneFingerprint -RepoRoot $RepoRoot -Paths @($config.controlPlaneCanary.fingerprintPaths)
        }
    }
    $worktrees = @(& git worktree list --porcelain)

    $health = $null
    if ($CheckHealth) {
        $health = [ordered]@{}
        foreach ($url in @(
            'http://localhost:8080/api/actuator/health',
            'http://localhost:5173/',
            'http://localhost:5173/api/auth/dev-login?redirect=/dashboard'
        )) {
            try {
                $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
                $health[$url] = @{ ok = $true; statusCode = [int]$resp.StatusCode }
            } catch {
                $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode.value__ } else { $null }
                $health[$url] = @{ ok = $false; statusCode = $code; message = $_.Exception.Message }
            }
        }
    }

    $decision = 'go'
    $reasons = @()
    if (Test-Path -LiteralPath $stopPath) {
        $decision = 'stop'
        $reasons += 'stop.flag present'
    }
    if (Test-Path -LiteralPath $pausePath) {
        if ($decision -ne 'stop') { $decision = 'pause' }
        $reasons += 'pause.flag present'
    }
    if (-not (Test-Path -LiteralPath $enabledPath)) {
        if ($decision -eq 'go') { $decision = 'disabled' }
        $reasons += 'enabled.flag missing'
    }
    $boundedBatchComplete = $state -and $null -ne $state.iterationLimit -and [int]$state.remainingIterations -le 0
    if ($decision -in @('go', 'disabled') -and $state -and [bool]$state.retrospectiveDue -and ($null -eq $state.iterationLimit -or $boundedBatchComplete)) {
        $decision = 'retrospective_required'
        $reasons += 'retrospective is due before a new batch can start'
    }
    if ($decision -in @('go', 'disabled') -and $state -and (
        $state.status -eq 'LIMIT_REACHED' -or
        ($null -ne $state.iterationLimit -and [int]$state.remainingIterations -le 0)
    )) {
        $decision = 'limit_reached'
        $reasons += 'iteration limit reached'
    }

    $result = [ordered]@{
        repoRoot = $RepoRoot
        branch = $branch
        gitStatus = $gitStatus
        stopFlag = Test-Path -LiteralPath $stopPath
        pauseFlag = Test-Path -LiteralPath $pausePath
        enabledFlag = Test-Path -LiteralPath $enabledPath
        checkGit = [bool]$CheckGit
        checkHealth = [bool]$CheckHealth
        healthGate = $health
        decision = $decision
        reasons = $reasons
        reviewCycleCompletedCount = if ($state) { $state.reviewCycleCompletedCount } else { 0 }
        retrospectiveDue = if ($state) { [bool]$state.retrospectiveDue } else { $false }
        retrospectiveStatus = if ($state) { $state.retrospectiveStatus } else { 'IDLE' }
        retrospectivePhase = if ($state) { $state.retrospectivePhase } else { 'NONE' }
        activeScoringVersion = if ($state) { $state.activeScoringVersion } else { $null }
        executionHost = $executionHost
        nestedModelCliInvocationAllowed = $executionHost -eq 'cli-legacy'
        runLock = if ($runLock) { [ordered]@{
            present = $true
            runInstanceId = if ($runLock.PSObject.Properties.Name -contains 'runInstanceId') { [string]$runLock.runInstanceId } else { '' }
            leaseEpoch = if ($runLock.PSObject.Properties.Name -contains 'leaseEpoch') { [string]$runLock.leaseEpoch } else { '' }
            controlPlaneFingerprint = if ($runLock.PSObject.Properties.Name -contains 'controlPlaneFingerprint') { [string]$runLock.controlPlaneFingerprint } else { '' }
        } } else { [ordered]@{ present=$false; runInstanceId=''; leaseEpoch=''; controlPlaneFingerprint='' } }
        state = if ($state) { [ordered]@{
            status = [string]$state.status
            phase = [string]$state.phase
            currentIssue = [string]$state.currentIssue
            currentIssuePhase = [string]$state.currentIssuePhase
            issueCheckpointPath = $checkpointPath
            stopReason = [string]$state.stopReason
            lastHeartbeatAt = [string]$state.lastHeartbeatAt
            executionHost = if ($state.PSObject.Properties.Name -contains 'executionHost') { [string]$state.executionHost } else { 'cli-legacy' }
        } } else { $null }
        activeCheckpoint = if ($activeCheckpoint) { [ordered]@{
            present = $true
            path = $checkpointPath
            sha256 = $checkpointHash
            issueId = [string]$activeCheckpoint.issueId
            phase = [string]$activeCheckpoint.phase
            generation = [int]$activeCheckpoint.generation
            readyContentHash = [string]$activeCheckpoint.readyContentHash
            baseCommit = [string]$activeCheckpoint.baseCommit
            worktree = [string]$activeCheckpoint.worktree
            branch = [string]$activeCheckpoint.branch
            executionHost = if ($activeCheckpoint.PSObject.Properties.Name -contains 'executionHost') { [string]$activeCheckpoint.executionHost } else { 'cli-legacy' }
        } } else { [ordered]@{ present=$false; path=$checkpointPath; sha256=''; issueId=''; phase=''; generation=0; readyContentHash=''; baseCommit=''; worktree=''; branch=''; executionHost='' } }
        controlPlaneFingerprint = $controlPlaneFingerprint
        worktreesPorcelain = $worktrees
    }

    if ($AsJson -or $true) {
        $result | ConvertTo-Json -Depth 5
    }
} finally {
    Pop-Location
}
