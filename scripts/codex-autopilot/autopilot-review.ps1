$ErrorActionPreference = 'Stop'
$commandLibrary = Join-Path $PSScriptRoot 'autopilot-command.ps1'
if (Test-Path -LiteralPath $commandLibrary) { . $commandLibrary }
$contextLibrary = Join-Path $PSScriptRoot 'autopilot-context.ps1'
if (Test-Path -LiteralPath $contextLibrary) { . $contextLibrary }
$nativeCommandLibrary = Join-Path $PSScriptRoot 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue)) { . $nativeCommandLibrary }
$metricsLibrary = Join-Path $PSScriptRoot 'autopilot-metrics.ps1'
if (!(Get-Command New-AutopilotInvocationId -ErrorAction SilentlyContinue)) { . $metricsLibrary }
$executionHostLibrary = Join-Path $PSScriptRoot 'autopilot-execution-host.ps1'
if (!(Get-Command Assert-AutopilotLegacyModelProcessAllowed -ErrorAction SilentlyContinue)) { . $executionHostLibrary }

function Import-AutopilotDesktopReviewResult {
  param(
    [Parameter(Mandatory)][string]$RequestPath,
    [Parameter(Mandatory)][string]$ResultPath
  )
  if (!(Test-Path -LiteralPath $RequestPath -PathType Leaf)) { throw 'desktop review request is missing' }
  if (!(Test-Path -LiteralPath $ResultPath -PathType Leaf)) { throw 'desktop review result is missing' }
  $request = Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $result = Get-Content -LiteralPath $ResultPath -Raw -Encoding UTF8 | ConvertFrom-Json
  Get-AutopilotReviewDisposition -ReviewResult $result -ExpectedIssueId ([string]$request.issueId) -ExpectedDiffHash ([string]$request.diffSha256) | Out-Null
  return $result
}

function Write-AutopilotReviewDiff {
  param([Parameter(Mandatory)][string]$Text, [Parameter(Mandatory)][string]$OutputPath)
  $parent = Split-Path -Parent $OutputPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($OutputPath, $Text, [Text.UTF8Encoding]::new($false))
}

function New-AutopilotReviewRequest {
  param(
    [string]$IssueId,
    [string]$ReadyPath,
    [string]$DiffPath,
    [string[]]$EvidencePaths,
    [string]$ContextBasePath = '',
    [string]$ContextBaseHash = '',
    [string]$ContextDeltaPath = '',
    [string]$ContextDeltaHash = '',
    [string]$OutputPath
  )
  $forbidden = @($EvidencePaths | Where-Object { (Split-Path -Leaf $_) -match '^(executor\.log|issue-prompt\.md|conversation|session)' })
  if ($forbidden.Count -gt 0) { throw 'Reviewer input must not contain Implementer reasoning or raw executor logs' }
  $request = [ordered]@{
    schemaVersion = 2
    issueId = $IssueId
    readyPath = $ReadyPath
    contextBasePath = $ContextBasePath
    contextBaseHash = $ContextBaseHash
    contextDeltaPath = $ContextDeltaPath
    contextDeltaHash = $ContextDeltaHash
    diffPath = $DiffPath
    diffSha256 = (Get-FileHash -LiteralPath $DiffPath -Algorithm SHA256).Hash.ToLowerInvariant()
    evidencePaths = @($EvidencePaths)
    instructions = @(
      'Review only the verified context base, review delta, Ready contract, final diff, required source, project rules, and bound verification evidence.',
      'Return pass, needs_repair, blocked, or tool_blocked with file/line evidence.',
      'Set reviewedDiffHash to the exact diffSha256 from this request and preserve the exact issueId.',
      'Do not infer success from the Implementer report.'
    )
  }
  if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    [IO.File]::WriteAllText($OutputPath, ($request | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
  }
  return [pscustomobject]$request
}

function Assert-AutopilotReviewGate {
  param([object]$Route, [object]$ReviewResult)
  if ($Route.reviewRequired -and $null -eq $ReviewResult) { throw 'independent Reviewer evidence is required' }
  if ($null -ne $ReviewResult -and $ReviewResult.decision -ne 'pass') { throw "Reviewer decision blocks closeout: $($ReviewResult.decision)" }
  return $true
}

function Test-AutopilotRetryAllowed {
  param([string]$PreviousFingerprint, [string]$CurrentFingerprint, [int]$Attempt)
  if ($Attempt -ge 2) { return $false }
  if ($PreviousFingerprint -and $PreviousFingerprint -eq $CurrentFingerprint) { return $false }
  return $true
}

function Get-AutopilotReviewDisposition {
  param([Parameter(Mandatory)][object]$ReviewResult, [string]$ExpectedIssueId = '', [string]$ExpectedDiffHash = '')
  if ($ReviewResult.decision -eq 'pass' -and (!$ExpectedIssueId -or !$ExpectedDiffHash)) { throw 'Reviewer pass requires explicit Issue and diff hash expectations' }
  if ($ExpectedIssueId -and $ReviewResult.issueId -ne $ExpectedIssueId) { throw 'Reviewer result Issue ID mismatch' }
  if ($ReviewResult.decision -eq 'tool_blocked' -or ($ReviewResult.decision -eq 'blocked' -and (Test-AutopilotReviewerSandboxFailure -ReviewResult $ReviewResult))) { return [pscustomobject]@{ action='BLOCK_TOOL'; failureFingerprint=''; summary='independent Reviewer tool could not read the bound evidence' } }
  if ($ExpectedDiffHash -and ([string]$ReviewResult.reviewedDiffHash).ToLowerInvariant() -ne $ExpectedDiffHash.ToLowerInvariant()) { throw 'Reviewer result diff hash mismatch' }
  if ($ReviewResult.decision -eq 'pass') { return [pscustomobject]@{ action='PASS'; failureFingerprint=''; summary='' } }
  if ($ReviewResult.decision -eq 'blocked') { return [pscustomobject]@{ action='BLOCK'; failureFingerprint=''; summary='independent Reviewer blocked the issue' } }
  if ($ReviewResult.decision -ne 'needs_repair') { throw "unknown Reviewer decision: $($ReviewResult.decision)" }
  $findings = @($ReviewResult.findings)
  if ($findings.Count -eq 0) { throw 'Reviewer needs_repair requires at least one blocking finding' }
  foreach ($finding in $findings) {
    if ([string]$finding.severity -ne 'blocking' -or
        [string]::IsNullOrWhiteSpace([string]$finding.file) -or
        $null -eq $finding.line -or [int]$finding.line -lt 1 -or
        [string]::IsNullOrWhiteSpace([string]$finding.risk) -or
        [string]::IsNullOrWhiteSpace([string]$finding.requiredEvidence)) {
      throw 'Reviewer needs_repair finding must bind blocking severity, file, positive line, risk, and requiredEvidence'
    }
  }
  $summary = @($findings | ForEach-Object { "$($_.severity)|$($_.file)|$($_.line)|$($_.risk)|$($_.requiredEvidence)" }) -join "`n"
  $sha = [Security.Cryptography.SHA256]::Create()
  try { $fingerprint = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($summary)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
  return [pscustomobject]@{ action='REPAIR'; failureFingerprint=$fingerprint; summary=$summary }
}

function Restore-AutopilotReviewedResultForCloseout {
  param(
    [Parameter(Mandatory)][object]$Result,
    [Parameter(Mandatory)][object]$ReviewResult,
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$ExpectedDiffHash
  )
  $disposition = Get-AutopilotReviewDisposition -ReviewResult $ReviewResult -ExpectedIssueId $IssueId -ExpectedDiffHash $ExpectedDiffHash
  if ($disposition.action -ne 'PASS') { throw 'only a bound Reviewer PASS can restore a result for closeout' }
  foreach ($entry in @(
    @('status','done'),
    @('failureCategory','none'),
    @('nextAction','CHECKPOINT'),
    @('stopReason','')
  )) {
    $name = [string]$entry[0]
    if ($Result.PSObject.Properties.Name -contains $name) { $Result.$name = $entry[1] }
    else { $Result | Add-Member -NotePropertyName $name -NotePropertyValue $entry[1] }
  }
  $Result | Add-Member -NotePropertyName review -NotePropertyValue $ReviewResult -Force
  return $Result
}

function Test-AutopilotReviewerSandboxFailure {
  param([Parameter(Mandatory)][object]$ReviewResult)
  if ($ReviewResult.decision -eq 'tool_blocked') { return $true }
  if ($ReviewResult.decision -ne 'blocked') { return $false }
  $text = @($ReviewResult.findings | ForEach-Object { "$($_.risk)`n$($_.requiredEvidence)" }) -join "`n"
  return $text -match '(?i)orchestrator_helper_launch_failed|sandbox.{0,40}initialization failed|os error 3'
}

function New-AutopilotReviewerToolBlockedResult {
  param([Parameter(Mandatory)][string]$RequestPath, [Parameter(Mandatory)][string]$ResultPath, [Parameter(Mandatory)][string]$Reason)
  $request = Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $result = [ordered]@{
    schemaVersion = 1
    issueId = [string]$request.issueId
    decision = 'tool_blocked'
    findings = @([ordered]@{ severity='blocking'; file=''; line=$null; risk='Reviewer tool_config failure'; requiredEvidence=$Reason })
    reviewedDiffHash = [string]$request.diffSha256
    reviewedAt = [datetimeoffset]::Now.ToString('o')
  }
  [IO.File]::WriteAllText($ResultPath, ($result | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
  return [pscustomobject]$result
}

function Invoke-AutopilotReviewerProcess {
  param([string]$Worktree, [string]$RequestPath, [string]$ResultPath, [string]$SchemaPath, [string]$Model, [string]$Thinking, [int]$TimeoutSeconds, [string]$Sandbox, [string]$IssueId, [string]$RunId, [scriptblock]$InvocationWriter, [string]$ExecutionHost = 'cli-legacy')
  Assert-AutopilotLegacyModelProcessAllowed -ExecutionHost $ExecutionHost -Role 'REVIEWER' | Out-Null
  $codex = Resolve-AutopilotCodexInvocation
  $prompt = "Act as an independent reviewer. Read only the structured request at $RequestPath and files it references. Return JSON matching $SchemaPath."
  $arguments = @('exec','--ephemeral','--sandbox',$Sandbox,'--model',$Model,'-c',"model_reasoning_effort=$Thinking",'--cd',$Worktree,'--output-schema',$SchemaPath,'--output-last-message',$ResultPath,'-')
  $arguments = @(Get-AutopilotCodexRedirectedStdinArguments -Arguments $arguments)
  $startInfo = [Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $codex.fileName
  $startInfo.Arguments = (@($codex.argumentPrefix) + $arguments | ForEach-Object { if ($_ -match '[\s"]') { '"' + $_.Replace('"','\"') + '"' } else { $_ } }) -join ' '
  $startInfo.WorkingDirectory = $Worktree
  $startInfo.UseShellExecute = $false
  $startInfo.RedirectStandardInput = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo
  $startedAt = [datetimeoffset]::Now.ToString('o')
  [void]$process.Start()
  $invocationId = New-AutopilotInvocationId -Role REVIEWER -Scope ISSUE -ScopeId $IssueId -ProcessId $process.Id -StartedAt $startedAt
  $invocationEvent = New-AutopilotModelInvocationEvent -Role REVIEWER -Scope ISSUE -ScopeId $IssueId -InvocationId $invocationId -IssueId $IssueId -RunId $RunId -ProcessId $process.Id -StartedAt $startedAt
  if ($InvocationWriter) { & $InvocationWriter $invocationEvent }
  elseif (Get-Command Write-RunEvent -ErrorAction SilentlyContinue) { Write-RunEvent 'model.invocation' $invocationEvent }
  $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
  $process.StandardInput.Write($prompt); $process.StandardInput.Close()
  if (!$process.WaitForExit($TimeoutSeconds * 1000)) { $process.Kill($true); throw 'independent Reviewer timed out' }
  $process.WaitForExit(); $null = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
  if ($process.ExitCode -ne 0) { throw "independent Reviewer failed: $stderr" }
  if (!(Test-Path -LiteralPath $ResultPath)) { throw 'independent Reviewer result is missing' }
  return Get-Content -LiteralPath $ResultPath -Raw -Encoding UTF8 | ConvertFrom-Json
}

function Test-AutopilotCodeRepairAllowed {
  param([string]$FailureCategory, [string]$StopReason)
  return $FailureCategory -eq 'quality_security' -and $StopReason -in @('STOP_VERIFICATION_FAILED','STOP_REVIEW_NEEDS_REPAIR','STOP_CLOSEOUT_ARTIFACTS_MISSING')
}

function Invoke-AutopilotReviewer {
  param(
    [string]$Worktree,
    [string]$RequestPath,
    [string]$ResultPath,
    [string]$SchemaPath,
    [string]$Model = 'gpt-5.6-sol',
    [string]$Thinking = 'high',
    [int]$TimeoutSeconds = 1200,
    [string]$IssueId = '',
    [string]$RunId = '',
    [scriptblock]$InvocationWriter,
    [string]$ExecutionHost = 'cli-legacy'
)
  Assert-AutopilotLegacyModelProcessAllowed -ExecutionHost $ExecutionHost -Role 'REVIEWER' | Out-Null
  if (!$IssueId) { $IssueId = [string](Get-Content -LiteralPath $RequestPath -Raw -Encoding UTF8 | ConvertFrom-Json).issueId }
  $headBefore = (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
  $fingerprintBefore = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $headBefore
  try {
    $review = Invoke-AutopilotReviewerProcess -Worktree $Worktree -RequestPath $RequestPath -ResultPath $ResultPath -SchemaPath $SchemaPath -Model $Model -Thinking $Thinking -TimeoutSeconds $TimeoutSeconds -Sandbox 'read-only' -IssueId $IssueId -RunId $RunId -InvocationWriter $InvocationWriter -ExecutionHost $ExecutionHost
  } catch {
    if ($_.Exception.Message -notmatch '(?i)orchestrator_helper_launch_failed|sandbox.{0,80}initialization failed|os error 3') { throw }
    $review = New-AutopilotReviewerToolBlockedResult -RequestPath $RequestPath -ResultPath $ResultPath -Reason $_.Exception.Message
  }
  if (Test-AutopilotReviewerSandboxFailure -ReviewResult $review) {
    Remove-Item -LiteralPath $ResultPath -Force -ErrorAction SilentlyContinue
    try {
      $review = Invoke-AutopilotReviewerProcess -Worktree $Worktree -RequestPath $RequestPath -ResultPath $ResultPath -SchemaPath $SchemaPath -Model $Model -Thinking $Thinking -TimeoutSeconds $TimeoutSeconds -Sandbox 'danger-full-access' -IssueId $IssueId -RunId $RunId -InvocationWriter $InvocationWriter -ExecutionHost $ExecutionHost
    } catch {
      if ($_.Exception.Message -notmatch '(?i)orchestrator_helper_launch_failed|sandbox.{0,80}initialization failed|os error 3') { throw }
      $review = New-AutopilotReviewerToolBlockedResult -RequestPath $RequestPath -ResultPath $ResultPath -Reason $_.Exception.Message
    }
    $headAfter = (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
    $fingerprintAfter = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $headAfter
    if ($headAfter -ne $headBefore -or $fingerprintAfter -ne $fingerprintBefore) { throw 'independent Reviewer fallback modified the issue worktree' }
  }
  return $review
}
