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
    return New-AutopilotStageResult -IssueId $Issue.lint.issueId -Stage $Phase -Outcome BLOCKED -FailureCategory tool_config -StopReason STOP_EXECUTOR_NOT_FOUND -Reason 'Issue executor entry is missing' -TransitionIntent BLOCKED
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
      return New-AutopilotStageResult -IssueId $Issue.lint.issueId -Stage validate -Outcome PAUSED -FailureCategory environment -StopReason STOP_RUNTIME_HEALTH_FAILED -Reason 'runtime health preflight failed' -EvidencePaths @($healthPath) -TransitionIntent PAUSED
    }
  }
  $baseCommit = if ($resuming) { [string]$ResumeCheckpoint.baseCommit } else { (Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim() }
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
  $executionRunId = if ($Attempt -eq 0) { $script:RunContext.id } else { "$($script:RunContext.id)-repair-$Attempt" }
  $resultPath = Join-Path (Join-Path $autoDir "runs\$executionRunId") 'result.json'
  $checkpointPath = if ($resuming) { [string]$script:RecoveryDecision.checkpointPath } else { Get-AutopilotIssueCheckpointPath -AutoDir $autoDir -IssueId $Issue.lint.issueId }
  if ($resuming) {
    $checkpoint = Read-AutopilotIssueCheckpoint -Path $checkpointPath
  } elseif ($Phase -eq 'repair' -and (Test-Path -LiteralPath $checkpointPath)) {
    $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase REPAIRING -Artifacts @{resultPath=$resultPath;issueDirectory=$issueDir} -IncrementDispatch repair
  } else {
    $checkpoint = New-AutopilotIssueCheckpoint -AutoDir $autoDir -IssueId $Issue.lint.issueId -ReadyPath (Join-Path $RepoRoot 'docs\backlog\ready-issues.md') -BaseCommit $baseCommit -Worktree $worktree.path -Branch $worktree.branch -AllowedPaths $Issue.contract.allowedPaths -ForbiddenPaths $Issue.contract.forbiddenPaths -ArtifactDirectory $issueDir
    $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase IMPLEMENTING -Artifacts @{resultPath=$resultPath;issueDirectory=$issueDir} -IncrementDispatch implementation
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
    '-ExecutorRole',$Route.executorRole,
    '-RunInstanceId',([string]$script:RunLock.runInstanceId),
    '-LeaseEpoch',([string]$script:RunLock.leaseEpoch)
  )
    if ($script:ControlPlaneFingerprint) { $executorArgs += @('-ControlPlaneFingerprint',([string]$script:ControlPlaneFingerprint)) }
    if ($Route.reviewRequired) { $executorArgs += '-ReviewRequired' }
    $executorTimeout = if ($config.issueExecutor.timeoutSeconds) { [int]$config.issueExecutor.timeoutSeconds + 120 } else { 2820 }
    $stallInspectSeconds = if ($config.issueExecutor.stallInspectSeconds) { [int]$config.issueExecutor.stallInspectSeconds } else { 300 }
    $stallTerminateSeconds = if ($config.issueExecutor.stallTerminateSeconds) { [int]$config.issueExecutor.stallTerminateSeconds } else { 600 }
    $heartbeatMilliseconds = if ($config.issueExecutor.heartbeatMilliseconds) { [int]$config.issueExecutor.heartbeatMilliseconds } else { 30000 }
    $childResult = Invoke-ChildWithHeartbeat -Arguments $executorArgs -WorkingDirectory $worktree.path -TimeoutSeconds $executorTimeout -StallInspectSeconds $stallInspectSeconds -StallTerminateSeconds $stallTerminateSeconds -HeartbeatMilliseconds $heartbeatMilliseconds -LongRunningCommands $longRunningCommands -SemanticEvidencePaths @($checkpointPath,$resultPath) -Task $Phase
    if ($childResult.exitCode -ne 0) {
    if ($childResult.stallTimedOut -and $config.repair -and $config.repair.enabled -eq $true -and $Attempt -lt 1) {
      $stallDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
      $retryAllowed = Register-AutopilotFailureRecovery -Path $checkpointPath -FailureFingerprint 'executor_stall_timeout' -Phase $Phase -DiffHash $stallDiffHash
      if ($retryAllowed) {
        Write-State $autoDir 'REPAIRING' $false 'EXECUTOR_STALL_REPAIR' $Issue.title 'executor timed out without semantic progress' ''
        Write-RunEvent 'executor.stall.retry-request' ([pscustomobject]@{ issueId = $Issue.lint.issueId; task = 'repair'; status = 'RETRY_REQUESTED'; executorPid = $childResult.executorPid; startedAt = $script:ExecutorStartedAt; lastProgressAt = $script:LastProgressAt; retryCount = 1; timeoutReason = 'no new evidence; retry scope limited to unfinished acceptance items with missing context supplied'; retiredAt = $script:RetiredAt; retiredStatus = $script:RetiredStatus })
        return Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $Issue -Route $Route -Attempt 1 -Phase 'repair' -PreviousSummary '首次 executor 因 600 秒无新证据已退役；仅处理未完成验收项，补充缺失上下文，不得扩大范围或再次重派。'
      }
      Write-RunEvent 'executor.stall.retry-suppressed' ([pscustomobject]@{ issueId=$Issue.lint.issueId; task=$Phase; status='PAUSED'; retryCount=1; timeoutReason='same failureFingerprint + phase + diffHash already consumed its automatic recovery budget' })
    }
    if ($childResult.stallTimedOut) {
      $blockedPath = Write-ExecutorStallBlockedIssue -RepoRoot $RepoRoot -Issue $Issue -RetiredExecutors $script:RetiredExecutors
      Write-RunEvent 'executor.stall.blocked' ([pscustomobject]@{ issueId = $Issue.lint.issueId; task = $Phase; status = 'BLOCKED'; stopReason = 'STOP_EXECUTOR_STALL_RETRY_EXHAUSTED'; executorPid = $childResult.executorPid; startedAt = $script:ExecutorStartedAt; lastProgressAt = $script:LastProgressAt; retryCount = 1; timeoutReason = 'second executor stalled; automatic retry exhausted'; retiredAt = $script:RetiredAt; retiredStatus = $script:RetiredStatus; evidencePath = $blockedPath })
      Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_STALL_BLOCKED' $Issue.title 'executor_stall_timeout' 'STOP_EXECUTOR_STALL_RETRY_EXHAUSTED'
      return New-AutopilotStageResult -IssueId $Issue.lint.issueId -Stage $Phase -Outcome BLOCKED -FailureCategory executor_stall_timeout -StopReason STOP_EXECUTOR_STALL_RETRY_EXHAUSTED -Reason 'executor timed out without semantic progress and retry budget is exhausted' -EvidencePaths @($blockedPath) -TransitionIntent BLOCKED
    }
    Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_PROCESS_FAILED' $Issue.title 'tool_config' 'STOP_EXECUTOR_PROCESS_FAILED'
    return New-AutopilotStageResult -IssueId $Issue.lint.issueId -Stage $Phase -Outcome PAUSED -FailureCategory tool_config -StopReason STOP_EXECUTOR_PROCESS_FAILED -Reason 'executor process failed before a valid result was produced' -TransitionIntent PAUSED
    }
  } else {
    $resultPath = [string]$ResumeCheckpoint.artifacts.resultPath
    if (!$resultPath) { throw 'recoverable Issue checkpoint is missing resultPath' }
    Write-RunEvent 'issue.phase.resume' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision=$script:RecoveryDecision.action; status='RECOVERED'; reason=$script:RecoveryDecision.reason; checkpointPath=$checkpointPath })
  }
  $closed = $false
  if (Test-Path -LiteralPath $resultPath) {
    $result = Get-Content -LiteralPath $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $failureSummary = ''
    $currentFingerprint = ''
    if ($resuming -and $script:RecoveryDecision.action -eq 'RESUME_CLOSEOUT' -and [string]$checkpoint.phase -eq 'REVIEWED') {
      $reviewPath = [string]$checkpoint.artifacts.reviewResultPath
      if (!$reviewPath -or !(Test-Path -LiteralPath $reviewPath -PathType Leaf)) { throw 'reviewed checkpoint is missing bound Reviewer result' }
      $review = Get-Content -LiteralPath $reviewPath -Raw -Encoding UTF8 | ConvertFrom-Json
      $reviewHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
      $result = Restore-AutopilotReviewedResultForCloseout -Result $result -ReviewResult $review -IssueId $Issue.lint.issueId -ExpectedDiffHash $reviewHash
      Write-RunEvent 'review.pass-result-restored' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='PASS'; status='RECOVERED'; reason='bound Reviewer PASS supersedes the historical blocked executor result for closeout'; reviewedDiffHash=$reviewHash })
    }
    if ($resuming -and $script:RecoveryDecision.action -eq 'RESUME_VALIDATION' -and [string]$result.status -eq 'blocked') {
      if ([string]$result.failureCategory -eq 'environment' -and [string]$result.stopReason -eq 'STOP_VERIFICATION_FAILED') {
        $result.status = 'done'; $result.failureCategory = 'none'; $result.nextAction = 'VERIFY'; $result.stopReason = ''
        $result.validation = @()
        Write-RunEvent 'validation.environment-retry' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='RETRY_VALIDATION'; status='RECOVERED'; reason='one classified environment prerequisite retry uses the preserved implementation diff' })
      } elseif ([string]$result.stopReason -eq 'STOP_EVIDENCE_STALE') {
        $evidenceFailure = @($result.validation | Where-Object { $_.name -eq 'evidence-current' } | Select-Object -Last 1)
        $recoveredPaths = if ($evidenceFailure.Count -eq 1) { @(Get-AutopilotConcatenatedEvidencePaths -Message ([string]$evidenceFailure[0].message)) } else { @() }
        if ($recoveredPaths.Count -ge 2) {
          $result.status = 'done'; $result.failureCategory = 'none'; $result.nextAction = 'VERIFY'; $result.stopReason = ''
          $result.validation = @($result.validation | Where-Object { $_.name -in @('executor-command','execution-artifacts') })
          $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase IMPLEMENTED -IncrementToolConfigBlock
          Write-RunEvent 'validation.evidence-path-recovery' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='RETRY_VALIDATION'; status='RECOVERED'; reason='concatenated evidence paths were individually recovered and will be rebound to the forwarded base'; recoveredEvidenceCount=$recoveredPaths.Count })
        }
      }
    }
    if ($result.status -eq 'done') {
      if (!$resuming) {
        $implementedHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
        $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase IMPLEMENTED -Artifacts @{resultPath=$resultPath;issueDirectory=$issueDir;archiveReport=[string]$Issue.contract.archiveReport} -Evidence @{diffHash=$implementedHash}
        $script:IssuePhase = 'IMPLEMENTED'
      }
      $changes = @(Get-AutopilotIssueChanges -Worktree $worktree.path -BaseCommit $baseCommit)
      try {
        Assert-AutopilotAllowedChanges -ChangedPaths $changes -AllowedPaths $Issue.contract.allowedPaths -ForbiddenPaths $Issue.contract.forbiddenPaths | Out-Null
      } catch {
        $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_SCOPE_VIOLATION'
        $result.validation += [pscustomobject]@{ name = 'scope-allowlist'; status = 'fail'; message = $_.Exception.Message }
      }
      if ($result.status -eq 'done') {
        try {
          Assert-AutopilotImplementationCloseoutArtifacts -Worktree $worktree.path -Issue $Issue.contract | Out-Null
        } catch {
          $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'REPAIR'; $result.stopReason = 'STOP_CLOSEOUT_ARTIFACTS_MISSING'
          $failureSummary = "D/E 尚未开始；只补齐 F 文档与治理回写，不得重做或扩大 BC 实现。缺失项：$($_.Exception.Message)"
          $currentFingerprint = Get-AutopilotTextHash ("closeout-artifacts|" + $_.Exception.Message)
          $result | Add-Member -NotePropertyName failureFingerprint -NotePropertyValue $currentFingerprint -Force
          $result.validation += [pscustomobject]@{ name='implementation-closeout-artifacts'; status='fail'; message=$_.Exception.Message }
        }
      }
      $resumePhase = if ($resuming) { [string]$checkpoint.phase } else { '' }
      $skipValidation = $resuming -and $resumePhase -in @('VALIDATED','REVIEWING','REVIEW_TOOL_BLOCKED','REVIEWED','CLOSING','IMPLEMENTATION_COMMITTED','CLOSEOUT_COMMITTED','REGISTERED')
      [System.Collections.Generic.List[string]]$evidencePaths = [System.Collections.Generic.List[string]]::new()
      if ($skipValidation) {
        foreach ($savedEvidencePath in @($checkpoint.artifacts.evidencePaths)) {
          if (![string]::IsNullOrWhiteSpace([string]$savedEvidencePath)) {
            [void]$evidencePaths.Add([string]$savedEvidencePath)
          }
        }
      }
      if ($result.status -eq 'done' -and !$skipValidation) {
        $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase VALIDATING -IncrementDispatch validation
        $script:IssuePhase = 'VALIDATING'
        Write-State $autoDir 'VERIFYING' $false 'EXECUTOR_COMPLETED' $Issue.title 'VERIFYING' ''
        $verifyDir = Join-Path $issueDir 'verify'
        New-Item -ItemType Directory -Path $verifyDir -Force | Out-Null
        for ($index = 0; $index -lt $Issue.contract.validationCommands.Count; $index++) {
          $validationCommand = [string]$Issue.contract.validationCommands[$index]
          if (!(Test-AutopilotPostExecutionVerificationRequired -Command $validationCommand)) {
            $evidencePath = Join-Path $verifyDir ("evidence-{0:00}.json" -f ($index + 1))
            $logPath = Join-Path $verifyDir ("command-{0:00}.log" -f ($index + 1))
            $evidence = New-AutopilotReadyLintEvidence -IssueId $Issue.lint.issueId -Worktree $worktree.path -BaseCommit $baseCommit -Command $validationCommand -ReadyContentHash ([string]$Issue.lint.readyContentHash) -ExpectedReadyContentHash ([string]$checkpoint.readyContentHash) -EvidencePath $evidencePath -LogPath $logPath
            [void]$evidencePaths.Add($evidencePath)
            $result.validation += [pscustomobject]@{ name = "ready-command-$($index + 1)"; status = $evidence.classification; message = "exitCode=$($evidence.exitCode); evidence=$evidencePath" }
            continue
          }
          $evidencePath = Join-Path $verifyDir ("evidence-{0:00}.json" -f ($index + 1))
          $logPath = Join-Path $verifyDir ("command-{0:00}.log" -f ($index + 1))
          $evidence = Invoke-AutopilotVerificationCommand -IssueId $Issue.lint.issueId -Worktree $worktree.path -BaseCommit $baseCommit -Command $validationCommand -EvidencePath $evidencePath -LogPath $logPath
          [void]$evidencePaths.Add($evidencePath)
          $result.validation += [pscustomobject]@{ name = "ready-command-$($index + 1)"; status = $evidence.classification; message = "exitCode=$($evidence.exitCode); evidence=$evidencePath" }
          if ($evidence.exitCode -ne 0) {
            $classifierPath = Join-Path (Resolve-Path (Join-Path $scriptDir '..\..')).Path 'plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1'
            $classification = & $classifierPath -ErrorText ([string]$evidence.summary) -ExitCode ([int]$evidence.exitCode) | ConvertFrom-Json
            $result.status = 'blocked'
            $result.failureCategory = if ($classification.category -eq 'environment_prereq') { 'environment' } elseif ($classification.category -eq 'ready_issue_config') { 'ready_issue_config' } elseif ($classification.category -eq 'tool_config') { 'tool_config' } else { 'quality_security' }
            $result.nextAction = 'STOP'; $result.stopReason = 'STOP_VERIFICATION_FAILED'
            if ($classification.category -eq 'environment_prereq') {
              $environmentDiffHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
              $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase IMPLEMENTED -Evidence @{diffHash=$environmentDiffHash} -IncrementEnvironmentRetry
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
        $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase VALIDATED -Artifacts @{evidencePaths=@($evidencePaths)} -Evidence @{diffHash=$verifiedHash;verificationDiffHash=$verifiedHash}
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
      $checkpoint = Read-AutopilotIssueCheckpoint -Path $checkpointPath
      $effectiveAttempt = Get-AutopilotEffectiveTaskAttempt -InvocationAttempt $Attempt -RepairDispatchCount ([int](Get-AutopilotCheckpointProperty $checkpoint.metrics 'repairDispatchCount' 0))
      $result | Add-Member -NotePropertyName attempt -NotePropertyValue $effectiveAttempt -Force
      $result | Add-Member -NotePropertyName firstPassSuccess -NotePropertyValue ($effectiveAttempt -eq 0 -and $result.status -eq 'done') -Force
      $result | Add-Member -NotePropertyName manualInterventionCount -NotePropertyValue 0 -Force
      $result | Add-Member -NotePropertyName scopeViolationCount -NotePropertyValue $(if ($result.stopReason -eq 'STOP_SCOPE_VIOLATION') { 1 } else { 0 }) -Force
      foreach ($metricName in @('implementationDispatchCount','validationDispatchCount','reviewDispatchCount','repairDispatchCount','closeoutDispatchCount','runResumeCount','phaseRestartCount','manualRecoveryCount','toolConfigBlockCount','environmentRetryCount','duplicateDispatchBlockedCount','wallClockSeconds')) {
        $result | Add-Member -NotePropertyName $metricName -NotePropertyValue (Get-AutopilotCheckpointProperty $checkpoint.metrics $metricName 0) -Force
      }
      $result | Add-Member -NotePropertyName phaseDurationsSeconds -NotePropertyValue $checkpoint.metrics.phaseDurationsSeconds -Force
      $result | Add-Member -NotePropertyName semanticProgressAt -NotePropertyValue ([string]$checkpoint.semanticProgressAt) -Force
      $result | Add-Member -NotePropertyName resumedFromPhase -NotePropertyValue $(if ($resuming) { $resumePhase } else { '' }) -Force
      if ($result.status -eq 'done') { $script:FailureFingerprint = $null }
      if ($result.status -eq 'done' -and $effectiveRoute.reviewRequired -and $resuming -and $resumePhase -eq 'REVIEW_TOOL_BLOCKED') {
        $manualReviewPath = [string]$checkpoint.artifacts.reviewResultPath
        if ($manualReviewPath -and (Test-Path -LiteralPath $manualReviewPath -PathType Leaf)) {
          try {
            $manualReview = Get-Content -LiteralPath $manualReviewPath -Raw -Encoding UTF8 | ConvertFrom-Json
            $manualReviewHash = Get-AutopilotRecoveryDiffHash -Worktree $worktree.path -BaseCommit $baseCommit
            $manualDisposition = Get-AutopilotReviewDisposition -ReviewResult $manualReview -ExpectedIssueId $Issue.lint.issueId -ExpectedDiffHash $manualReviewHash
            if ($manualDisposition.action -eq 'PASS') {
              $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase REVIEWED -Evidence @{diffHash=$manualReviewHash;reviewDiffHash=$manualReviewHash} -IncrementManualRecovery
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
        $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase REVIEWING -IncrementDispatch review
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
            $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase REVIEWED -Artifacts @{reviewRequestPath=$requestPath;reviewResultPath=$reviewPath} -Evidence @{diffHash=$request.diffSha256;reviewDiffHash=$request.diffSha256}
            $script:IssuePhase = 'REVIEWED'
          } elseif ($reviewDisposition.action -eq 'REPAIR') {
            $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEW_NEEDS_REPAIR'
            $failureSummary = $reviewDisposition.summary; $currentFingerprint = $reviewDisposition.failureFingerprint
            $result | Add-Member -NotePropertyName failureFingerprint -NotePropertyValue $currentFingerprint -Force
          } elseif ($reviewDisposition.action -eq 'BLOCK') {
            $result.status = 'blocked'; $result.failureCategory = 'quality_security'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEW_FAILED'
          } elseif ($reviewDisposition.action -eq 'BLOCK_TOOL') {
            $result.status = 'blocked'; $result.failureCategory = 'tool_config'; $result.nextAction = 'STOP'; $result.stopReason = 'STOP_REVIEWER_TOOL_FAILURE'
            $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase REVIEW_TOOL_BLOCKED -Artifacts @{reviewRequestPath=$requestPath;reviewResultPath=$reviewPath} -Evidence @{diffHash=$request.diffSha256} -IncrementToolConfigBlock
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
        if (!$terminalResume) { $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase CLOSING -IncrementDispatch closeout }
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
          if ([string]$config.taskScoring.activeVersion -eq $script:AutopilotTaskScoreV2Version) {
            $scoreEvidence = New-AutopilotTaskScoreV2EvidenceFromResult -Result $result -ReportPath $reportPath -ImplementationCommit ('0' * 40) -StockIssueTarget $isStockIssue -Formal
          } else {
            $scoreEvidence = New-AutopilotTaskScoreEvidenceFromResult -Result $result -ReportPath $reportPath -ImplementationCommit ('0' * 40) -StockIssueTarget $isStockIssue
          }
          if ([string]$config.taskScoring.activeVersion -eq $script:AutopilotTaskScoreVersion -and $config.taskScoring.candidateVersion -eq $script:AutopilotTaskScoreV2CandidateVersion -and $config.taskScoring.candidateEnabled -ne $true) {
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
        $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase CLOSEOUT_COMMITTED -Evidence @{implementationCommit=[string]$closeout.implementationCommit;closeoutCommit=[string]$closeout.closeoutCommit;diffHash=$closeoutDiffHash}
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
          $checkpoint = Move-AutopilotIssuePhase -Path $checkpointPath -Phase REGISTERED
          $script:IssuePhase = 'REGISTERED'
          $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $resultPath -Encoding UTF8
          Write-State $autoDir 'REGISTERED' $false 'CLOSEOUT_REGISTERED' $Issue.title 'REGISTERED' ''
          $registeredState = Read-AutopilotState -Path (Join-Path $autoDir 'state.json')
          if ([string]$registeredState.issueCheckpointPath -ne $checkpointPath -or [string]$registeredState.currentIssuePhase -ne 'REGISTERED') { throw 'registered AutoPilot state read-back failed' }
          if ($controlPlaneCanaryEnabled -and $null -ne $script:IterationLimit -and [int]$script:IterationLimit -eq 1) {
            $graphSnapshot = Get-AutopilotKnowledgeGraphIssueSnapshot -RepoRoot $RepoRoot
            $mergedHead = (Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
            if (!$graphSnapshot.available -or [string]$graphSnapshot.cursor -ne $mergedHead) { throw "control-plane canary knowledge-graph cursor gate failed: $($graphSnapshot.stopReason) $($graphSnapshot.message)" }
            $script:LastCanaryFingerprint = $script:ControlPlaneFingerprint
            $script:LastCanaryReport = [string]$Issue.contract.archiveReport
            Write-State $autoDir 'REGISTERED' $false 'CONTROL_PLANE_CANARY_PASSED' $Issue.title 'REGISTERED' ''
            $canaryState = Read-AutopilotState -Path (Join-Path $autoDir 'state.json')
            if ([string]$canaryState.lastCanaryFingerprint -ne $script:ControlPlaneFingerprint -or [string]$canaryState.lastCanaryReport -ne [string]$Issue.contract.archiveReport) { throw 'control-plane canary state read-back failed' }
            Write-RunEvent 'control-plane.canary-passed' ([pscustomobject]@{ issueId=$Issue.lint.issueId; decision='PASS'; status='CANARY_PASSED'; controlPlaneFingerprint=$script:ControlPlaneFingerprint; evidencePath=$Issue.contract.archiveReport; graphGitCursor=$graphSnapshot.cursor })
          }
          if ($script:TaskScoringActive -and [string]$config.taskScoring.activeVersion -eq $script:AutopilotTaskScoreVersion -and $config.taskScoring.candidateVersion -eq $script:AutopilotTaskScoreV2CandidateVersion -and $config.taskScoring.candidateEnabled -ne $true) {
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
          Move-AutopilotIssuePhase -Path $checkpointPath -Phase CLOSED | Out-Null
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
        return Invoke-IssueExecutor -RepoRoot $RepoRoot -ConfigPath $ConfigPath -Issue $Issue -Route $Route -Attempt ($Attempt + 1) -Phase 'repair' -PreviousSummary $failureSummary
      }
      if ($result.status -eq 'done' -and $closed) {
        Write-Host 'ISSUE_DURABLY_CLOSED'
      } elseif ($result.status -eq 'done') { Write-State $autoDir 'CLOSING' $false 'VERIFICATION_COMPLETED' $Issue.title 'CLOSING' '' } elseif ($result.stopReason -eq 'STOP_REVIEWER_TOOL_RETRY_EXHAUSTED') { Write-State $autoDir 'PAUSED' $false 'REVIEW_TOOL_BLOCKED' $Issue.title $result.failureCategory $result.stopReason } else { Write-State $autoDir 'BLOCKED' $false 'VERIFICATION_BLOCKED' $Issue.title $result.failureCategory $result.stopReason }
    } else {
      Write-State $autoDir 'BLOCKED' $false 'EXECUTOR_BLOCKED' $Issue.title $result.failureCategory $result.stopReason
    }
    $stageName = if ($closed) { 'closeout' } elseif ($script:IssuePhase) { [string]$script:IssuePhase } else { $Phase }
    return ConvertTo-AutopilotStageResult -IssueId $Issue.lint.issueId -Stage $stageName -ExecutorResult $result -EvidencePaths @($evidencePaths) -Closed $closed
  }
  return New-AutopilotStageResult -IssueId $Issue.lint.issueId -Stage $Phase -Outcome PAUSED -FailureCategory tool_config -StopReason STOP_EXECUTOR_RESULT_MISSING -Reason 'executor did not produce the required result contract' -TransitionIntent PAUSED
}
