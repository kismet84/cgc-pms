$ErrorActionPreference = 'Stop'
$nativeLibrary = Join-Path $PSScriptRoot 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $nativeLibrary)) { . $nativeLibrary }
$fingerprintLibrary = Join-Path $PSScriptRoot 'autopilot-control-plane-fingerprint.ps1'
if (!(Get-Command Get-AutopilotControlPlanePolicyDescriptor -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $fingerprintLibrary)) { . $fingerprintLibrary }

function Get-AutopilotTextHash {
  param([string]$Text)
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}

function Get-AutopilotDiffHash {
  param([string]$Worktree, [string]$BaseCommit)
  $diff = Get-AutopilotDiffText -Worktree $Worktree -BaseCommit $BaseCommit
  return Get-AutopilotTextHash $diff
}

function Get-AutopilotDiffText {
  param([string]$Worktree, [string]$BaseCommit)
  # Keep the repository's checkout normalization active. Forcing autocrlf=false on
  # Windows compares checked-out CRLF bytes with LF blobs and turns small document
  # edits into whole-file review noise.
  $diffResult = Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('-c','core.safecrlf=false','diff','--binary',$BaseCommit,'--') -ThrowOnFailure
  $diff = $diffResult.stdout
  $untrackedResult = Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('-c','core.autocrlf=false','-c','core.safecrlf=false','ls-files','--others','--exclude-standard') -ThrowOnFailure
  foreach ($path in @(Get-AutopilotNativeOutputLines $untrackedResult.stdout)) {
    $untrackedDiff = Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('-c','core.safecrlf=false','diff','--no-index','--binary','--','NUL',$path) -AcceptedExitCodes @(0,1) -ThrowOnFailure
    $diff += $untrackedDiff.stdout
  }
  return $diff
}

function New-AutopilotContextPack {
  param(
    [Parameter(Mandatory)][object]$Issue,
    [Parameter(Mandatory)][ValidateSet('implement','repair','review')][string]$Phase,
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$Worktree,
    [string]$OutputPath = '',
    [string[]]$RelevantSymbols = @(),
    [string]$PreviousPhaseSummary = '',
    [string[]]$ChangedPaths = @(),
    [string[]]$AcceptedDecisions = @(),
    [string[]]$OpenRisks = @(),
    [object[]]$LongRunningCommands = @()
  )
  if (@($RelevantSymbols).Count -gt 12) { throw 'context source budget exceeded: max 12 relevant symbols/files' }
  if ([Text.Encoding]::UTF8.GetByteCount($PreviousPhaseSummary) -gt 5120) { throw 'previous phase summary budget exceeded: max 5 KB' }
  if (@($ChangedPaths).Count -gt 20) { throw 'changed file budget exceeded: max 20 files' }
  $baseCommit = (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
  $policy = Get-AutopilotControlPlanePolicyDescriptor -RepoRoot $RepoRoot
  $context = [ordered]@{
    schemaVersion = 2
    issueId = $Issue.issueId
    phase = $Phase
    baseCommit = $baseCommit
    executionBaseCommit = $baseCommit
    candidateEvidenceHead = if ($Issue.PSObject.Properties.Name -contains 'candidateEvidenceHead') { [string]$Issue.candidateEvidenceHead } else { '' }
    controlPlanePolicyVersion = $policy.version
    controlPlanePolicyHash = $policy.hash
    controlPlanePolicyRefs = @($policy.path)
    contextGeneratedAt = [datetimeoffset]::Now.ToString('o')
    readyContentHash = $Issue.readyContentHash
    diffHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $baseCommit
    goal = @($Issue.goal)
    nonGoals = @($Issue.nonGoals)
    acceptanceCriteria = @($Issue.acceptanceCriteria)
    allowedPaths = @($Issue.allowedPaths)
    forbiddenPaths = @($Issue.forbiddenPaths)
    requiredCommands = @($Issue.validationCommands)
    archiveReport = $Issue.archiveReport
    relevantSymbols = @($RelevantSymbols)
    acceptedDecisions = @($AcceptedDecisions)
    openRisks = @($OpenRisks)
    longRunningCommands = @($LongRunningCommands | Where-Object { [int]$_.expectedSeconds -gt 600 } | ForEach-Object {
      [ordered]@{ command = [string]$_.command; expectedSeconds = [int]$_.expectedSeconds }
    })
    previousPhaseSummary = if ($PreviousPhaseSummary) { $PreviousPhaseSummary } else { $null }
  }
  if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    [IO.File]::WriteAllText($OutputPath, ($context | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
  }
  return [pscustomobject]$context
}

function Assert-AutopilotContextCurrent {
  param([object]$Context, [object]$Issue, [string]$Worktree, [string]$ExpectedBaseCommit)
  if ($Context.issueId -ne $Issue.issueId) { throw 'context Issue ID mismatch' }
  if ($Context.readyContentHash -ne $Issue.readyContentHash) { throw 'context Ready hash mismatch' }
  if ($Context.baseCommit -ne $ExpectedBaseCommit) { throw 'context base commit mismatch' }
  if ($Context.executionBaseCommit -ne $ExpectedBaseCommit) { throw 'context execution base commit mismatch' }
  $expectedCandidateHead = if ($Issue.PSObject.Properties.Name -contains 'candidateEvidenceHead') { [string]$Issue.candidateEvidenceHead } else { '' }
  if ([string]$Context.candidateEvidenceHead -ne $expectedCandidateHead) { throw 'context candidate evidence head mismatch' }
  $policy = Get-AutopilotControlPlanePolicyDescriptor -RepoRoot $(if ($Worktree) { $Worktree } else { Split-Path -Parent $Context.controlPlanePolicyRefs[0] }) -PolicyPath $Context.controlPlanePolicyRefs[0]
  if ($Context.controlPlanePolicyVersion -ne $policy.version -or $Context.controlPlanePolicyHash -ne $policy.hash) { throw 'context control-plane policy binding is stale' }
  $actualHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $Context.baseCommit
  if ($Context.diffHash -ne $actualHash) { throw 'context diff hash is stale' }
  return $true
}
