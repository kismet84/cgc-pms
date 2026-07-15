function Invoke-AutopilotRunCoordinator {
  [CmdletBinding()]
  param(
    [string]$RepoRoot = "D:\projects-test\cgc-pms",
    [string]$ConfigPath = "",
    [switch]$DryRun,
    [switch]$ApplyBacklogSplit,
    [switch]$ExplainNextAction,
    [Nullable[int]]$MaxIterations = $null,
    [int]$MaxLoops = 20
  )

  $scriptDir = $PSScriptRoot
  . (Join-Path $scriptDir 'autopilot-state.ps1')
  . (Join-Path $scriptDir 'autopilot-execution-host.ps1')
  . (Join-Path $scriptDir 'autopilot-execution-mode.ps1')
  . (Join-Path $scriptDir 'autopilot-native-command.ps1')
  . (Join-Path $scriptDir 'autopilot-run-lock.ps1')
  . (Join-Path $scriptDir 'autopilot-control-plane-fingerprint.ps1')
  . (Join-Path $scriptDir 'autopilot-metrics.ps1')
  . (Join-Path $scriptDir 'autopilot-task-score.ps1')
  . (Join-Path $scriptDir 'autopilot-ready.ps1')
  . (Join-Path $scriptDir 'autopilot-route.ps1')
  . (Join-Path $scriptDir 'autopilot-worktree.ps1')
  . (Join-Path $scriptDir 'autopilot-progress.ps1')
  . (Join-Path $scriptDir 'autopilot-context.ps1')
  . (Join-Path $scriptDir 'autopilot-verify.ps1')
  . (Join-Path $scriptDir 'autopilot-review.ps1')
  . (Join-Path $scriptDir 'autopilot-issue-checkpoint.ps1')
  . (Join-Path $scriptDir 'autopilot-transition.ps1')
  . (Join-Path $scriptDir 'autopilot-recover.ps1')
  . (Join-Path $scriptDir 'autopilot-refill.ps1')
  . (Join-Path $scriptDir 'autopilot-closeout.ps1')
  . (Join-Path $scriptDir 'autopilot-runtime-context.ps1')
  . (Join-Path $scriptDir 'autopilot-run-coordinator-support.ps1')
  . (Join-Path $scriptDir 'autopilot-executor-supervisor.ps1')
  . (Join-Path $scriptDir 'autopilot-stage-result.ps1')
  . (Join-Path $scriptDir 'autopilot-issue-lifecycle.ps1')
  $runtimeContext = New-AutopilotRuntimeContext -ScriptDirectory $scriptDir -RepoRoot $RepoRoot -ConfigPath $ConfigPath -DryRun ([bool]$DryRun) -ApplyBacklogSplit ([bool]$ApplyBacklogSplit) -ExplainNextAction ([bool]$ExplainNextAction) -IterationLimit $MaxIterations
  $ConfigPath = $runtimeContext.configPath
  $RepoRoot = $runtimeContext.repoRoot
  $config = $runtimeContext.config
  $executionHost = [string]$runtimeContext.executionHost
  $script:ExecutionHost = $executionHost
  if ($executionHost -eq 'desktop-native') {
    return New-AutopilotDesktopHandoff -RepoRoot $RepoRoot -ConfigPath $ConfigPath -MaxIterations $MaxIterations -DryRun ([bool]$DryRun) -ApplyBacklogSplit ([bool]$ApplyBacklogSplit) -ExplainNextAction ([bool]$ExplainNextAction)
  }
  $configuredBaseBranch = $runtimeContext.baseBranch
  $script:TaskScoringActive = if ($config.PSObject.Properties.Name -contains 'taskScoring') { Test-AutopilotTaskScoringActive $config.taskScoring } else { $false }
  $script:RetrospectiveActive = Test-AutopilotRetrospectiveActive $(if ($config.PSObject.Properties.Name -contains 'taskScoring') { $config.taskScoring } else { $null }) $(if ($config.PSObject.Properties.Name -contains 'retrospective') { $config.retrospective } else { $null })
  $autoDir = $runtimeContext.autoDir
  $controlPlaneCanaryEnabled = $runtimeContext.controlPlaneCanaryEnabled
  $script:ControlPlaneFingerprint = $runtimeContext.controlPlaneFingerprint
  $script:ControlPlanePolicyVersion = $runtimeContext.controlPlanePolicyVersion
  $script:ControlPlanePolicyHash = $runtimeContext.controlPlanePolicyHash
  $script:ControlPlanePolicyRefs = @($runtimeContext.controlPlanePolicyRefs)
  $script:CandidateEvidenceHead = ''
  $script:ExecutionBaseCommit = $runtimeContext.executionBaseCommit
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

  $readyPath = $runtimeContext.readyPath
  $executionMode = $runtimeContext.executionMode
  $applyMode = [string]$executionMode.mode -eq 'APPLY'
  $dryRunMode = [string]$executionMode.mode -ne 'APPLY'
  $script:RunLock = $null
  $script:FenceValid = $false
  $script:RecoveryCheckedAfterLock = $false
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
  Initialize-IterationProgress $autoDir $readyPath $MaxIterations $dryRunMode
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
    decision = [string]$executionMode.mode
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

  if ($executionMode.isExplain) {
    $checkpoint = Test-Checkpoint $autoDir
    Write-RunEvent "checkpoint" ([pscustomobject]@{ checkpoint = $checkpoint; decision = "EXPLAIN" })
    $readyIssues = if ($checkpoint -eq "CONTINUE") { @(Get-ReadyIssues $readyPath $RepoRoot $scriptDir) } else { @() }
    $refillDecision = if ($checkpoint -eq "CONTINUE" -and $readyIssues.Count -eq 0) { Get-AutopilotRefillDecision -RepoRoot $RepoRoot } else { $null }
    $candidates = if ($refillDecision -and $refillDecision.action -in @('PLAN_READY','GENERATE_READY')) { @($refillDecision.candidates) } else { @() }
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
      $script:RunLock = New-RunLock $autoDir $maxRunMinutes 'APPLY'
      $script:FenceValid = Assert-CurrentControlPlaneFence
      Set-AutopilotStateFenceContext -LockPath (Join-Path $autoDir 'run.lock') -RunInstanceId ([string]$script:RunLock.runInstanceId) -LeaseEpoch ([string]$script:RunLock.leaseEpoch) -ControlPlaneFingerprint ([string]$script:ControlPlaneFingerprint)
      Set-AutopilotCheckpointFenceContext -LockPath (Join-Path $autoDir 'run.lock') -RunInstanceId ([string]$script:RunLock.runInstanceId) -LeaseEpoch ([string]$script:RunLock.leaseEpoch) -ControlPlaneFingerprint ([string]$script:ControlPlaneFingerprint)
      $recovery = Get-AutopilotRecoveryDecision -AutoDir $autoDir -PermittedBaseAdvancePaths @($config.controlPlaneCanary.fingerprintPaths) -CurrentRunLock $script:RunLock
      $script:RecoveryCheckedAfterLock = $true
      $resumeActions = @('RESUME_VALIDATION','RESUME_REVIEW','RESUME_CLOSEOUT','RESUME_SCORE_AND_CLOSEOUT','RESUME_MERGE_AND_REGISTER','RESUME_FINALIZE')
      if ($recovery.action -eq 'CLEAN_CLOSED_CHECKPOINT') {
        Remove-AutopilotIssueCheckpoint -Path $recovery.checkpointPath -Closed
        Write-RunEvent 'recovery' ([pscustomobject]@{ decision='CLEAN_CLOSED_CHECKPOINT'; status='RECOVERED'; reason='final state was already read back before checkpoint retirement' })
        $recovery = [pscustomobject]@{ action='NEW_RUN'; reason='closed checkpoint retired' }
      }
      if ($recovery.action -in @('RESUME_FROM_CHECKPOINT') + $resumeActions) {
        if ($recovery.action -in $resumeActions) {
          $resumeMetricArgs = @{ Path=$recovery.checkpointPath; Phase=[string]$recovery.checkpoint.phase; IncrementRunResume=$true }
          if ([string]$recovery.checkpoint.phase -in @('VALIDATING','REVIEWING','CLOSING','REPAIRING')) { $resumeMetricArgs.IncrementPhaseRestart = $true }
          $recovery.checkpoint = Move-AutopilotIssuePhase @resumeMetricArgs
          $script:RecoveryDecision = $recovery
          $script:IssueCheckpointPath = [string]$recovery.checkpointPath
          $script:IssuePhase = [string]$recovery.checkpoint.phase
        }
        Write-Host 'STALE_RUN_LOCK_TAKEN_OVER'
        Write-RunEvent 'recovery' ([pscustomobject]@{ decision = $recovery.action; status = 'RECOVERED'; reason = $recovery.reason })
      } elseif ($recovery.action -eq 'PAUSE_REVIEW_TOOL_BLOCKED') {
        Write-State $autoDir 'PAUSED' $false 'REVIEW_TOOL_BLOCKED' ([string]$recovery.issueId) $recovery.reason 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED'
        Write-Host 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED'
        exit 0
      } elseif ($recovery.action -eq 'PAUSE_ENVIRONMENT_RETRY_EXHAUSTED') {
        Write-State $autoDir 'PAUSED' $false 'ENVIRONMENT_RETRY_EXHAUSTED' ([string]$recovery.issueId) $recovery.reason 'STOP_ENVIRONMENT_RETRY_EXHAUSTED'
        Write-Host 'STOP_ENVIRONMENT_RETRY_EXHAUSTED'
        exit 0
      } elseif ($recovery.action -eq 'PAUSE_RECOVERY_TOOL_CONFIG') {
        Write-State $autoDir 'PAUSED' $false 'RECOVERY_TOOL_CONFIG' ([string]$recovery.issueId) $recovery.reason 'STOP_RECOVERY_TOOL_CONFIG'
        Write-Host 'STOP_RECOVERY_TOOL_CONFIG'
        exit 0
      } elseif ($recovery.action -eq 'PAUSE_RECOVERY_ENVIRONMENT') {
        Write-State $autoDir 'PAUSED' $false 'RECOVERY_ENVIRONMENT' ([string]$recovery.issueId) $recovery.reason 'STOP_RECOVERY_ENVIRONMENT'
        Write-Host 'STOP_RECOVERY_ENVIRONMENT'
        exit 0
      } elseif ($recovery.action -in @('VERIFY_UNCOMMITTED','QUARANTINE')) {
        Write-State $autoDir 'BLOCKED' $false 'RECOVERY_REVIEW_REQUIRED' '' $recovery.reason 'STOP_RECOVERY_REVIEW_REQUIRED'
        Write-Host 'STOP_RECOVERY_REVIEW_REQUIRED'
        exit 0
      }
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
        Write-State $autoDir 'STOP_RETROSPECTIVE_REQUIRED' $dryRunMode 'RETROSPECTIVE_REQUIRED' '' 'pending retrospective blocks new task dispatch' 'STOP_RETROSPECTIVE_REQUIRED'
        Write-Host 'STOP_RETROSPECTIVE_REQUIRED'
        exit 0
      }
    }

    if (!$script:RecoveryDecision -and $null -ne $script:IterationLimit -and $script:IterationCompleted -ge $script:IterationLimit) {
      Write-RunEvent "stop" ([pscustomobject]@{ decision = "STOP"; status = "STOP_ITERATION_LIMIT_REACHED"; stopReason = "STOP_ITERATION_LIMIT_REACHED" })
      Remove-Item -LiteralPath (Join-Path $autoDir 'enabled.flag') -Force -ErrorAction SilentlyContinue
      Write-State $autoDir "STOP_ITERATION_LIMIT_REACHED" $dryRunMode "STOP" "" "STOP_ITERATION_LIMIT_REACHED" "STOP_ITERATION_LIMIT_REACHED"
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
        Write-State $autoDir $checkpoint $dryRunMode "STOP" "" $checkpoint $checkpoint
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
          Write-State $autoDir "STOP_READY_LINT_FAILED" $dryRunMode "STOP" $readyIssues[0].title "STOP_READY_LINT_FAILED" "STOP_READY_LINT_FAILED"
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
        $script:CandidateEvidenceHead = if ($selectedIssue.contract -and $selectedIssue.contract.candidateEvidenceHead) { [string]$selectedIssue.contract.candidateEvidenceHead } else { '' }
        $script:ExecutionBaseCommit = (Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim().ToLowerInvariant()
        $readyStatus = if ($batchPlan.parallel) { "READY_ISSUE_BATCH_FOUND" } else { "READY_ISSUE_FOUND" }
        Write-State $autoDir $readyStatus $dryRunMode $readyStatus $selectedIssue.title $readyStatus ""
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
        if ($dryRunMode -or $batchPlan.parallel) {
          for ($index = 0; $index -lt $batchIssues.Count; $index++) {
            $issue = $batchIssues[$index]
            $commandText = Get-ExecutorCommand $RepoRoot $ConfigPath $issue.title
            if ($batchIssues.Count -eq 1) {
              Write-Host "executorCommand=$commandText"
            } else {
              Write-Host ("executorCommand[{0}]={1}" -f ($index + 1), $commandText)
            }
          }
          if ($batchPlan.parallel -and !$dryRunMode) {
            Write-Host "PARALLEL_BATCH_PLAN_ONLY"
          }
        } else {
          Assert-AutopilotDispatchAuthority -ExecutionMode $executionMode -LockOwned ($null -ne $script:RunLock) -RecoveryCheckedAfterLock $script:RecoveryCheckedAfterLock -FenceValid (Assert-CurrentControlPlaneFence) | Out-Null
          if ($script:RecoveryDecision -and $selectedIssue.lint.issueId -eq $script:RecoveryDecision.issueId) {
            $stageResult = Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $selectedIssue -Route $selectedRoute -ResumeCheckpoint $script:RecoveryDecision.checkpoint
          } else {
            $stageResult = Invoke-IssueExecutor $RepoRoot $ConfigPath $selectedIssue $selectedRoute
          }
          Assert-AutopilotStageResult -Result $stageResult | Out-Null
        }
        exit 0
      }

      $refillDecision = Get-AutopilotRefillDecision -RepoRoot $RepoRoot
      $readyPlannerEnabled = ($config.PSObject.Properties.Name -contains 'readyPlanner') -and $null -ne $config.readyPlanner -and $config.readyPlanner.enabled -eq $true
      $canApplyRefill = $applyMode -and (Test-AutopilotReadyPlanningAllowed -Action $refillDecision.action) -and ($refillDecision.action -eq 'GENERATE_READY' -or $readyPlannerEnabled)
      if ($canApplyRefill) {
        Write-State $autoDir 'REFILLING' $false 'READY_PLANNER_START' '' $refillDecision.reason ''
        $planResultPath = Join-Path $script:RunContext.dir 'ready-plan.json'
        $planSchemaPath = Join-Path $RepoRoot 'plugins\cgc-pms-autopilot\schemas\ready-plan.schema.json'
        $expectedCandidateRefs = @($refillDecision.candidates | ForEach-Object { Get-AutopilotCandidateRef -Candidate $_ })
        if ($refillDecision.action -eq 'GENERATE_READY') {
          $deterministicPlan = New-AutopilotDeterministicReadyPlan -Candidate $refillDecision.candidates[0] -RepoRoot $RepoRoot
          [IO.File]::WriteAllText($planResultPath, ($deterministicPlan | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
        } else {
          $plannerHeartbeatSeconds = if ($config.readyPlanner.PSObject.Properties.Name -contains 'heartbeatSeconds') { [int]$config.readyPlanner.heartbeatSeconds } else { 30 }
          $heartbeatWriter = { param($heartbeat) Write-State $autoDir 'REFILLING' $false 'READY_PLANNER_HEARTBEAT' '' $refillDecision.reason '' }
          Invoke-AutopilotReadyPlanner -RepoRoot $RepoRoot -Candidates $refillDecision.candidates -OutputPath $planResultPath -SchemaPath $planSchemaPath -Model $config.readyPlanner.model -Thinking $config.readyPlanner.thinking -TimeoutSeconds $config.readyPlanner.timeoutSeconds -HeartbeatSeconds $plannerHeartbeatSeconds -HeartbeatWriter $heartbeatWriter -RunId $script:RunContext.id -CandidateRefs $expectedCandidateRefs -ExecutionHost $executionHost | Out-Null
        }
        $imported = Import-AutopilotReadyPlan -PlanPath $planResultPath -ReadyPath $readyPath -RepoRoot $RepoRoot -ExpectedCandidateRefs $expectedCandidateRefs
        if ($imported.createdCount -eq 0) {
          $outcomes = @($imported.candidateDecisions | ForEach-Object { [string]$_.outcome } | Select-Object -Unique)
          $zeroReadyStatus = if ($outcomes -contains 'BLOCKED') { 'READY_CANDIDATES_BLOCKED' } else { 'READY_CANDIDATES_REJECTED' }
          $refillStageResult = if ($outcomes -contains 'BLOCKED') {
            $refillFailureCategory = Get-AutopilotRefillStageFailureCategory -CandidateDecisions $imported.candidateDecisions
            New-AutopilotStageResult -Scope RUN -SubjectId $script:RunContext.id -Stage REFILL -Outcome BLOCKED -FailureCategory $refillFailureCategory -StopReason $zeroReadyStatus -Reason $refillDecision.reason -TransitionIntent STOP
          } else {
            New-AutopilotStageResult -Scope RUN -SubjectId $script:RunContext.id -Stage REFILL -Outcome TERMINAL -Reason $refillDecision.reason -TransitionIntent STOP
          }
          Write-RunEvent 'refill.decided' ([pscustomobject]@{ decision=$zeroReadyStatus; status=$zeroReadyStatus; reason=$refillDecision.reason; createdReadyIssueDrafts=0; candidateDecisions=$imported.candidateDecisions; stageResult=$refillStageResult })
          Write-State $autoDir 'REFILLING' $false $zeroReadyStatus '' $refillDecision.reason $zeroReadyStatus
          $zeroTarget = if ($outcomes -contains 'BLOCKED') { 'BLOCKED' } else { 'STOPPED' }
          Move-AutopilotRunPhase -Path (Join-Path $autoDir 'state.json') -Status $zeroTarget -Phase 'refill-no-ready' -Reason $zeroReadyStatus | Out-Null
          Write-Host $zeroReadyStatus
          exit 0
        }
        $refillCommit = Commit-ReadyRefill $RepoRoot $readyPath
        $runtimeContext.executionBaseCommit = $refillCommit.ToLowerInvariant()
        $runtimeContext.candidateEvidenceHead = [string]$refillDecision.candidateEvidenceHead
        $script:ExecutionBaseCommit = $runtimeContext.executionBaseCommit
        $script:CandidateEvidenceHead = $runtimeContext.candidateEvidenceHead
        $refillStageResult = New-AutopilotStageResult -Scope RUN -SubjectId $script:RunContext.id -Stage REFILL -Outcome SUCCEEDED -NextStage SELECT -Reason $refillDecision.reason -SemanticProgress $true -EvidencePaths @($planResultPath,$readyPath) -TransitionIntent SELECT
        Write-RunEvent 'refill.planned' ([pscustomobject]@{ decision = 'BACKLOG_SPLIT_APPLIED'; status = 'BACKLOG_SPLIT_APPLIED'; reason = $refillDecision.reason; createdReadyIssueDrafts = $imported.createdCount; commit = $refillCommit; candidateDecisions=$imported.candidateDecisions; stageResult=$refillStageResult })
        Write-State $autoDir 'REFILLING' $false 'BACKLOG_SPLIT_APPLIED' '' 'BACKLOG_SPLIT_APPLIED' ''
        Move-AutopilotRunPhase -Path (Join-Path $autoDir 'state.json') -Status CHECKPOINT -Phase 'refill-complete' -Reason 'Ready created; continue same run' | Out-Null
        Write-Host 'BACKLOG_SPLIT_APPLIED'
        Write-Host "createdReadyIssueDrafts=$($imported.createdCount)"
        Write-Host 'REFILL_CONTINUE_SAME_RUN'
        continue
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
}
