$ErrorActionPreference = 'Stop'
$contextScript = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-context.ps1'
if (Test-Path -LiteralPath $contextScript) { . $contextScript }
$nativeCommandLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue)) { . $nativeCommandLibrary }

function Test-AutopilotPostExecutionVerificationRequired {
  param([Parameter(Mandatory)][string]$Command)
  return $Command -notmatch '(?i)scripts[\\/]codex-autopilot[\\/]ready-lint\.ps1'
}

function Get-AutopilotEvidenceCategory {
  param([Parameter(Mandatory)][string]$Command)
  if ($Command -match '(?i)^\s*git\s+diff\s+--check|ready-lint|eslint|checkstyle|spotless') { return 'STATIC_CHEAP' }
  if ($Command -match '(?i)playwright|cypress|browser|dev-login') { return 'BROWSER' }
  if ($Command -match '(?i)(mvnw?|gradlew?).*\b(test|package|verify)\b|pnpm.*\b(test|build|typecheck)\b|npm.*\b(test|build)\b') { return 'UNIT_BUILD' }
  return 'INTEGRATION'
}

function Get-AutopilotVerificationEnvironment {
  param([Parameter(Mandatory)][string]$Worktree)
  $descriptor = [ordered]@{
    os = [Environment]::OSVersion.Platform.ToString()
    osVersion = [Environment]::OSVersion.VersionString
    architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
    powershellVersion = $PSVersionTable.PSVersion.ToString()
    worktreeVolume = [IO.Path]::GetPathRoot([IO.Path]::GetFullPath($Worktree))
  }
  return [pscustomobject]@{ descriptor=[pscustomobject]$descriptor; fingerprint=(Get-AutopilotCanonicalHash ([pscustomobject]$descriptor)) }
}

function New-AutopilotEvidenceId {
  param([Parameter(Mandatory)][string]$IssueId, [Parameter(Mandatory)][string]$CommandHash, [Parameter(Mandatory)][string]$DiffHash, [Parameter(Mandatory)][string]$ExecutionMode, [Parameter(Mandatory)][string]$Nonce)
  return Get-AutopilotTextHash ((@($IssueId,$CommandHash,$DiffHash,$ExecutionMode,$Nonce)) -join '|')
}

function Test-AutopilotEvidenceReusable {
  param(
    [Parameter(Mandatory)][object]$Evidence,
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$ReadyContentHash,
    [Parameter(Mandatory)][string]$ContextBaseId,
    [Parameter(Mandatory)][string]$ContextBaseHash,
    [Parameter(Mandatory)][string]$ContextDeltaId,
    [Parameter(Mandatory)][string]$ContextDeltaHash,
    [AllowEmptyString()][string]$CandidateEvidenceHead,
    [Parameter(Mandatory)][string]$ExecutionBaseCommit,
    [Parameter(Mandatory)][string]$ControlPlanePolicyHash,
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string]$DiffHash,
    [Parameter(Mandatory)][string]$EnvironmentFingerprint
  )
  function Refuse([string]$Code) { return [pscustomobject]@{ reusable=$false; reasonCode=$Code; sourceEvidenceId=$(if($Evidence.PSObject.Properties.Name -contains 'evidenceId'){$Evidence.evidenceId}else{$null}) } }
  if ([int]$Evidence.schemaVersion -ne 2) { return $(Refuse 'EVIDENCE_V1_NOT_REUSABLE') }
  if ([string]$Evidence.evidenceCategory -ne 'UNIT_BUILD') { return $(Refuse 'CATEGORY_NOT_REUSABLE') }
  if ([int]$Evidence.exitCode -ne 0 -or [string]$Evidence.classification -ne 'pass') { return $(Refuse 'SOURCE_NOT_PASS') }
  if ([string]$Evidence.issueId -ne $IssueId) { return $(Refuse 'ISSUE_MISMATCH') }
  if ([string]$Evidence.readyContentHash -ne $ReadyContentHash) { return $(Refuse 'READY_HASH_CHANGED') }
  if ([string]$Evidence.contextBaseId -ne $ContextBaseId -or [string]$Evidence.contextBaseHash -ne $ContextBaseHash) { return $(Refuse 'BASE_CONTEXT_CHANGED') }
  if ([string]$Evidence.contextDeltaId -ne $ContextDeltaId -or [string]$Evidence.contextDeltaHash -ne $ContextDeltaHash) { return $(Refuse 'DELTA_CONTEXT_CHANGED') }
  if ([string]$Evidence.candidateEvidenceHead -ne $CandidateEvidenceHead) { return $(Refuse 'CANDIDATE_HEAD_CHANGED') }
  if ([string]$Evidence.executionBaseCommit -ne $ExecutionBaseCommit) { return $(Refuse 'EXECUTION_BASE_CHANGED') }
  if ([string]$Evidence.controlPlanePolicyHash -ne $ControlPlanePolicyHash) { return $(Refuse 'POLICY_CHANGED') }
  if ([string]$Evidence.commandHash -ne (Get-AutopilotTextHash $Command.Trim())) { return $(Refuse 'COMMAND_CHANGED') }
  if ([string]$Evidence.diffHash -ne $DiffHash) { return $(Refuse 'DIFF_CHANGED') }
  if ([string]$Evidence.environmentFingerprint -ne $EnvironmentFingerprint) { return $(Refuse 'ENVIRONMENT_CHANGED') }
  return [pscustomobject]@{ reusable=$true; reasonCode='REUSABLE'; sourceEvidenceId=[string]$Evidence.evidenceId }
}

function New-AutopilotReusedEvidence {
  param([Parameter(Mandatory)][object]$SourceEvidence, [Parameter(Mandatory)][string]$EvidencePath)
  if ([int]$SourceEvidence.schemaVersion -ne 2 -or !$SourceEvidence.evidenceId) { throw 'only immutable Evidence v2 can be reused' }
  $copy = [ordered]@{}
  foreach ($property in @($SourceEvidence.PSObject.Properties)) { $copy[$property.Name] = $property.Value }
  $copy.executionMode = 'REUSED'
  $copy.sourceEvidenceId = [string]$SourceEvidence.evidenceId
  $copy.startedAt = [datetimeoffset]::Now.ToString('o')
  $copy.durationSeconds = 0
  $copy.rawLogPath = [string]$SourceEvidence.rawLogPath
  $copy.evidenceId = New-AutopilotEvidenceId -IssueId ([string]$SourceEvidence.issueId) -CommandHash ([string]$SourceEvidence.commandHash) -DiffHash ([string]$SourceEvidence.diffHash) -ExecutionMode REUSED -Nonce ([guid]::NewGuid().ToString('N'))
  $parent = Split-Path -Parent $EvidencePath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($EvidencePath, ($copy | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]$copy
}

function Invoke-AutopilotVerificationCommand {
  param(
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$BaseCommit,
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string]$EvidencePath,
    [Parameter(Mandatory)][string]$LogPath,
    [Parameter(Mandatory)][string]$ReadyContentHash,
    [Parameter(Mandatory)][string]$ContextBaseId,
    [Parameter(Mandatory)][string]$ContextBaseHash,
    [Parameter(Mandatory)][string]$ContextDeltaId,
    [Parameter(Mandatory)][string]$ContextDeltaHash,
    [AllowEmptyString()][string]$CandidateEvidenceHead = '',
    [Parameter(Mandatory)][string]$ExecutionBaseCommit,
    [Parameter(Mandatory)][string]$ControlPlanePolicyHash,
    [Parameter(Mandatory)][string]$AcceptanceRef,
    [ValidateSet('STATIC_CHEAP','UNIT_BUILD','INTEGRATION','BROWSER')][string]$EvidenceCategory = '',
    [int]$TimeoutSeconds = 1800
  )
  $started = [datetimeoffset]::Now
  $wrappedCommand = "& { $Command }; if (`$null -ne `$LASTEXITCODE) { exit `$LASTEXITCODE }"
  $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($wrappedCommand))
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = 'pwsh'
  $startInfo.Arguments = "-NoProfile -EncodedCommand $encoded"
  $startInfo.WorkingDirectory = $Worktree
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  [void]$process.Start()
  $stdoutTask = $process.StandardOutput.ReadToEndAsync()
  $stderrTask = $process.StandardError.ReadToEndAsync()
  $timedOut = !$process.WaitForExit($TimeoutSeconds * 1000)
  if ($timedOut) { $process.Kill($true) }
  $process.WaitForExit()
  $stdout = $stdoutTask.GetAwaiter().GetResult()
  $stderr = $stderrTask.GetAwaiter().GetResult()
  $exitCode = if ($timedOut) { 124 } else { [int]$process.ExitCode }
  $parent = Split-Path -Parent $LogPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($LogPath, "[stdout]`r`n$stdout`r`n[stderr]`r`n$stderr", [Text.UTF8Encoding]::new($false))
  $summarySource = if ($exitCode -eq 0) { $stdout } else { "$stderr`n$stdout" }
  $summaryLines = @($summarySource -split '\r?\n' | Where-Object { $_ } | Select-Object -Last 20)
  $commit = (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
  $diffHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $BaseCommit
  $commandHash = Get-AutopilotTextHash $Command.Trim()
  $environment = Get-AutopilotVerificationEnvironment -Worktree $Worktree
  if (!$EvidenceCategory) { $EvidenceCategory = Get-AutopilotEvidenceCategory -Command $Command }
  $evidence = [ordered]@{
    schemaVersion = 2
    evidenceId = New-AutopilotEvidenceId -IssueId $IssueId -CommandHash $commandHash -DiffHash $diffHash -ExecutionMode EXECUTED -Nonce $started.ToString('o')
    sourceEvidenceId = $null
    executionMode = 'EXECUTED'
    evidenceCategory = $EvidenceCategory
    issueId = $IssueId
    baseCommit = $BaseCommit
    commit = $commit
    executionBaseCommit = $ExecutionBaseCommit
    readyContentHash = $ReadyContentHash
    contextBaseId = $ContextBaseId
    contextBaseHash = $ContextBaseHash
    contextDeltaId = $ContextDeltaId
    contextDeltaHash = $ContextDeltaHash
    candidateEvidenceHead = $CandidateEvidenceHead
    controlPlanePolicyHash = $ControlPlanePolicyHash
    acceptanceRef = $AcceptanceRef
    diffHash = $diffHash
    command = $Command
    commandHash = $commandHash
    environment = $environment.descriptor
    environmentFingerprint = $environment.fingerprint
    startedAt = $started.ToString('o')
    durationSeconds = [Math]::Round(([datetimeoffset]::Now - $started).TotalSeconds, 3)
    exitCode = $exitCode
    classification = if ($exitCode -eq 0) { 'pass' } elseif ($timedOut) { 'timeout' } else { 'fail' }
    summary = (($summaryLines -join "`n").Trim())
    rawLogPath = $LogPath
  }
  $evidenceParent = Split-Path -Parent $EvidencePath
  if ($evidenceParent -and !(Test-Path -LiteralPath $evidenceParent)) { New-Item -ItemType Directory -Path $evidenceParent -Force | Out-Null }
  [IO.File]::WriteAllText($EvidencePath, ($evidence | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]$evidence
}

function Assert-AutopilotEvidenceCurrent {
  param(
    [object]$Evidence, [string]$IssueId, [string]$Worktree, [string]$BaseCommit,
    [string]$ReadyContentHash = '', [string]$ContextBaseId = '', [string]$ContextBaseHash = '',
    [string]$ContextDeltaId = '', [string]$ContextDeltaHash = '', [AllowEmptyString()][string]$CandidateEvidenceHead = '',
    [string]$ExecutionBaseCommit = '', [string]$ControlPlanePolicyHash = ''
  )
  if ($Evidence.issueId -ne $IssueId) { throw 'evidence Issue ID mismatch' }
  if ($Evidence.baseCommit -ne $BaseCommit) { throw 'evidence base commit mismatch' }
  if ($Evidence.exitCode -ne 0 -or $Evidence.classification -ne 'pass') { throw 'evidence does not prove a pass' }
  $currentHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $BaseCommit
  if ($Evidence.diffHash -ne $currentHash) { throw 'evidence diff hash is stale' }
  if ([int]$Evidence.schemaVersion -eq 2) {
    if ([string]$Evidence.evidenceId -notmatch '^[a-f0-9]{64}$') { throw 'Evidence v2 identity is invalid' }
    if ([string]$Evidence.commandHash -ne (Get-AutopilotTextHash (([string]$Evidence.command).Trim()))) { throw 'Evidence v2 command hash is invalid' }
    $environmentHash = Get-AutopilotCanonicalHash $Evidence.environment
    if ([string]$Evidence.environmentFingerprint -ne $environmentHash) { throw 'Evidence v2 environment fingerprint is invalid' }
    $currentEnvironment = Get-AutopilotVerificationEnvironment -Worktree $Worktree
    if ([string]$Evidence.environmentFingerprint -ne [string]$currentEnvironment.fingerprint) { throw 'Evidence v2 environment is stale' }
    if ([string]$Evidence.executionMode -eq 'REUSED') {
      if ([string]$Evidence.sourceEvidenceId -notmatch '^[a-f0-9]{64}$' -or [string]$Evidence.sourceEvidenceId -eq [string]$Evidence.evidenceId) { throw 'reused Evidence v2 source identity is invalid' }
    } elseif ([string]$Evidence.executionMode -ne 'EXECUTED' -or $null -ne $Evidence.sourceEvidenceId) { throw 'executed Evidence v2 source identity is invalid' }
    foreach ($pair in @(
      @('readyContentHash',$ReadyContentHash), @('contextBaseId',$ContextBaseId), @('contextBaseHash',$ContextBaseHash),
      @('contextDeltaId',$ContextDeltaId), @('contextDeltaHash',$ContextDeltaHash), @('executionBaseCommit',$ExecutionBaseCommit),
      @('controlPlanePolicyHash',$ControlPlanePolicyHash)
    )) {
      if ([string]$pair[1] -and [string]$Evidence.($pair[0]) -ne [string]$pair[1]) { throw "Evidence v2 $($pair[0]) is stale" }
    }
    if ($PSBoundParameters.ContainsKey('CandidateEvidenceHead') -and [string]$Evidence.candidateEvidenceHead -ne $CandidateEvidenceHead) { throw 'Evidence v2 candidate evidence head is stale' }
  } elseif ([int]$Evidence.schemaVersion -ne 1) { throw 'unsupported evidence schemaVersion' }
  return $true
}

function New-AutopilotReadyLintEvidence {
  param(
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$BaseCommit,
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string]$ReadyContentHash,
    [Parameter(Mandatory)][string]$ExpectedReadyContentHash,
    [Parameter(Mandatory)][string]$EvidencePath,
    [Parameter(Mandatory)][string]$LogPath,
    [Parameter(Mandatory)][string]$ContextBaseId,
    [Parameter(Mandatory)][string]$ContextBaseHash,
    [Parameter(Mandatory)][string]$ContextDeltaId,
    [Parameter(Mandatory)][string]$ContextDeltaHash,
    [AllowEmptyString()][string]$CandidateEvidenceHead = '',
    [Parameter(Mandatory)][string]$ExecutionBaseCommit,
    [Parameter(Mandatory)][string]$ControlPlanePolicyHash,
    [Parameter(Mandatory)][string]$AcceptanceRef
  )
  if ($ReadyContentHash -notmatch '^[a-f0-9]{64}$' -or $ReadyContentHash -ne $ExpectedReadyContentHash) {
    throw 'normalized terminal Ready lint hash does not match the dispatched contract'
  }
  $started = [datetimeoffset]::Now
  $summary = "Normalized terminal Ready contract passed the production parser; readyContentHash=$ReadyContentHash"
  $parent = Split-Path -Parent $LogPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($LogPath, $summary, [Text.UTF8Encoding]::new($false))
  $diffHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $BaseCommit
  $commandHash = Get-AutopilotTextHash $Command.Trim()
  $environment = Get-AutopilotVerificationEnvironment -Worktree $Worktree
  $evidence = [ordered]@{
    schemaVersion = 2
    evidenceId = New-AutopilotEvidenceId -IssueId $IssueId -CommandHash $commandHash -DiffHash $diffHash -ExecutionMode EXECUTED -Nonce $started.ToString('o')
    sourceEvidenceId = $null
    executionMode = 'EXECUTED'
    evidenceCategory = 'STATIC_CHEAP'
    issueId = $IssueId
    baseCommit = $BaseCommit
    commit = (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
    executionBaseCommit = $ExecutionBaseCommit
    readyContentHash = $ReadyContentHash
    contextBaseId = $ContextBaseId
    contextBaseHash = $ContextBaseHash
    contextDeltaId = $ContextDeltaId
    contextDeltaHash = $ContextDeltaHash
    candidateEvidenceHead = $CandidateEvidenceHead
    controlPlanePolicyHash = $ControlPlanePolicyHash
    acceptanceRef = $AcceptanceRef
    diffHash = $diffHash
    command = $Command
    commandHash = $commandHash
    environment = $environment.descriptor
    environmentFingerprint = $environment.fingerprint
    startedAt = $started.ToString('o')
    durationSeconds = [Math]::Round(([datetimeoffset]::Now - $started).TotalSeconds, 3)
    exitCode = 0
    classification = 'pass'
    summary = $summary
    rawLogPath = $LogPath
  }
  $evidenceParent = Split-Path -Parent $EvidencePath
  if ($evidenceParent -and !(Test-Path -LiteralPath $evidenceParent)) { New-Item -ItemType Directory -Path $evidenceParent -Force | Out-Null }
  [IO.File]::WriteAllText($EvidencePath, ($evidence | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]$evidence
}

function Get-AutopilotConcatenatedEvidencePaths {
  param([AllowEmptyString()][string]$Message)
  if ([string]::IsNullOrWhiteSpace($Message)) { return @() }
  return @([regex]::Matches($Message, '(?i)[a-z]:\\.*?evidence(?:-\d+)?\.json') | ForEach-Object { $_.Value } | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
}

function Get-AutopilotRetryBudget {
  param([string]$Category, [string]$Subcategory = '')
  if ($Category -in @('tool_config','environment_prereq','ready_issue_config','unknown')) { return 1 }
  if ($Category -eq 'real_quality_or_security' -and $Subcategory -eq 'real_permission_or_security_failure') { return 1 }
  if ($Category -eq 'real_quality_or_security') { return 2 }
  return 0
}

function Test-AutopilotHealthGate {
  param([int]$TimeoutSeconds = 10)
  $targets = @(
    @{ name = 'backend'; url = 'http://localhost:8080/api/actuator/health' },
    @{ name = 'frontend'; url = 'http://localhost:5173/' },
    @{ name = 'dev-login'; url = 'http://localhost:5173/api/auth/dev-login?redirect=/dashboard' }
  )
  $results = foreach ($target in $targets) {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri $target.url -TimeoutSec $TimeoutSeconds -MaximumRedirection 0 -ErrorAction Stop
      [pscustomobject]@{ name = $target.name; url = $target.url; status = 'pass'; statusCode = [int]$response.StatusCode }
    } catch {
      [pscustomobject]@{ name = $target.name; url = $target.url; status = 'fail'; statusCode = $null; error = $_.Exception.Message }
    }
  }
  return [pscustomobject]@{ status = if (@($results | Where-Object status -eq 'fail').Count -eq 0) { 'pass' } else { 'fail' }; results = @($results) }
}

function Invoke-AutopilotRuntimePreflight {
  param([Parameter(Mandatory)][string]$RepoRoot, [object]$RuntimeRefresh)
  $before = Test-AutopilotHealthGate
  if ($before.status -eq 'pass') { return [pscustomobject]@{ status='pass'; refreshed=$false; before=$before; after=$before } }
  if (!$RuntimeRefresh -or $RuntimeRefresh.enabled -ne $true -or !$RuntimeRefresh.command) { return [pscustomobject]@{ status='fail'; refreshed=$false; before=$before; after=$before; reason='runtime refresh is unavailable' } }
  $timeoutSeconds = if ($RuntimeRefresh.timeoutSeconds) { [int]$RuntimeRefresh.timeoutSeconds } else { 900 }
  $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("& { $($RuntimeRefresh.command) }; if (`$null -ne `$LASTEXITCODE) { exit `$LASTEXITCODE }"))
  $process = Start-Process -FilePath pwsh -ArgumentList '-NoProfile','-EncodedCommand',$encoded -WorkingDirectory $RepoRoot -PassThru -WindowStyle Hidden
  if (!$process.WaitForExit($timeoutSeconds * 1000)) { & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null; return [pscustomobject]@{ status='fail'; refreshed=$true; before=$before; after=$null; reason='runtime refresh timed out' } }
  if ($process.ExitCode -ne 0) { return [pscustomobject]@{ status='fail'; refreshed=$true; before=$before; after=$null; reason="runtime refresh exitCode=$($process.ExitCode)" } }
  $waitSeconds = if ($RuntimeRefresh.waitSeconds) { [int]$RuntimeRefresh.waitSeconds } else { 25 }
  Start-Sleep -Seconds $waitSeconds
  $after = Test-AutopilotHealthGate
  return [pscustomobject]@{ status=$after.status; refreshed=$true; before=$before; after=$after; reason=if($after.status -eq 'pass'){'runtime recovered'}else{'runtime remains unhealthy after refresh'} }
}
