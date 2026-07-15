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

function ConvertTo-AutopilotCanonicalValue {
  param([AllowNull()][object]$Value)
  if ($null -eq $Value) { return $null }
  if ($Value -is [string] -or $Value -is [ValueType]) { return $Value }
  if ($Value -is [Collections.IDictionary]) {
    $ordered = [ordered]@{}
    foreach ($key in @($Value.Keys | ForEach-Object { [string]$_ } | Sort-Object)) { $ordered[$key] = ConvertTo-AutopilotCanonicalValue $Value[$key] }
    return [pscustomobject]$ordered
  }
  if ($Value -is [Collections.IEnumerable]) {
    $items = @($Value | ForEach-Object { ConvertTo-AutopilotCanonicalValue $_ })
    return ,$items
  }
  $properties = @($Value.PSObject.Properties | Where-Object MemberType -in @('NoteProperty','Property') | Sort-Object Name)
  if ($properties.Count -gt 0) {
    $ordered = [ordered]@{}
    foreach ($property in $properties) { $ordered[$property.Name] = ConvertTo-AutopilotCanonicalValue $property.Value }
    return [pscustomobject]$ordered
  }
  return [string]$Value
}

function Get-AutopilotCanonicalJson {
  param([Parameter(Mandatory)][object]$Value)
  return (ConvertTo-AutopilotCanonicalValue $Value) | ConvertTo-Json -Depth 20 -Compress
}

function Get-AutopilotCanonicalHash {
  param([Parameter(Mandatory)][object]$Value)
  return Get-AutopilotTextHash (Get-AutopilotCanonicalJson $Value)
}

function Write-AutopilotContextJson {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][object]$Value)
  $parent = Split-Path -Parent $Path
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
}

function Get-AutopilotContextIdentityPayload {
  param([Parameter(Mandatory)][object]$Context)
  $payload = [ordered]@{}
  $excluded = if ([string]$Context.contextType -eq 'delta') { @('deltaId','contentHash') } else { @('baseId','contentHash') }
  foreach ($property in @($Context.PSObject.Properties | Sort-Object Name)) {
    if ($property.Name -notin $excluded) { $payload[$property.Name] = $property.Value }
  }
  return [pscustomobject]$payload
}

function New-AutopilotContextBase {
  param(
    [Parameter(Mandatory)][object]$Issue,
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$OutputPath,
    [string[]]$RelevantSymbols = @(),
    [string[]]$AcceptedDecisions = @(),
    [string[]]$OpenRisks = @(),
    [object[]]$LongRunningCommands = @()
  )
  if (@($RelevantSymbols).Count -gt 12) { throw 'context source budget exceeded: max 12 relevant symbols/files' }
  $baseCommit = (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
  $policy = Get-AutopilotControlPlanePolicyDescriptor -RepoRoot $RepoRoot
  $payload = [ordered]@{
    schemaVersion = 3
    contextType = 'base'
    issueId = [string]$Issue.issueId
    baseCommit = $baseCommit
    executionBaseCommit = $baseCommit
    candidateEvidenceHead = if ($Issue.PSObject.Properties.Name -contains 'candidateEvidenceHead') { [string]$Issue.candidateEvidenceHead } else { '' }
    controlPlanePolicyVersion = [string]$policy.version
    controlPlanePolicyHash = [string]$policy.hash
    controlPlanePolicyRefs = @($policy.path)
    readyContentHash = [string]$Issue.readyContentHash
    goal = @($Issue.goal)
    nonGoals = @($Issue.nonGoals)
    acceptanceCriteria = @($Issue.acceptanceCriteria)
    allowedPaths = @($Issue.allowedPaths)
    forbiddenPaths = @($Issue.forbiddenPaths)
    requiredCommands = @($Issue.validationCommands)
    archiveReport = [string]$Issue.archiveReport
    relevantSymbols = @($RelevantSymbols)
    acceptedDecisions = @($AcceptedDecisions)
    openRisks = @($OpenRisks)
    longRunningCommands = @($LongRunningCommands | Where-Object { [int]$_.expectedSeconds -gt 600 } | ForEach-Object { [ordered]@{ command=[string]$_.command; expectedSeconds=[int]$_.expectedSeconds } })
  }
  $hash = Get-AutopilotCanonicalHash ([pscustomobject]$payload)
  $context = [ordered]@{ baseId=$hash; contentHash=$hash }
  foreach ($name in $payload.Keys) { $context[$name] = $payload[$name] }
  Write-AutopilotContextJson -Path $OutputPath -Value $context
  return [pscustomobject]$context
}

function New-AutopilotContextDelta {
  param(
    [Parameter(Mandatory)][object]$Base,
    [Parameter(Mandatory)][ValidateSet('implement','repair','validate','review')][string]$Phase,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$OutputPath,
    [string[]]$ChangedPaths = @(),
    [string]$PreviousPhaseSummary = '',
    [string[]]$AcceptedDecisions = @(),
    [string[]]$OpenRisks = @()
  )
  if ([Text.Encoding]::UTF8.GetByteCount($PreviousPhaseSummary) -gt 5120) { throw 'previous phase summary budget exceeded: max 5 KB' }
  if (@($ChangedPaths).Count -gt 20) { throw 'changed file budget exceeded: max 20 files' }
  $payload = [ordered]@{
    schemaVersion = 1
    contextType = 'delta'
    issueId = [string]$Base.issueId
    phase = $Phase
    baseId = [string]$Base.baseId
    baseHash = [string]$Base.contentHash
    baseCommit = [string]$Base.baseCommit
    readyContentHash = [string]$Base.readyContentHash
    diffHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit ([string]$Base.baseCommit)
    changedPaths = @($ChangedPaths | ForEach-Object { ([string]$_).Replace('\','/') } | Where-Object { $_ } | Sort-Object -Unique)
    previousPhaseSummary = if ($PreviousPhaseSummary) { $PreviousPhaseSummary } else { $null }
    acceptedDecisions = @($AcceptedDecisions)
    openRisks = @($OpenRisks)
  }
  $hash = Get-AutopilotCanonicalHash ([pscustomobject]$payload)
  $delta = [ordered]@{ deltaId=$hash; contentHash=$hash }
  foreach ($name in $payload.Keys) { $delta[$name] = $payload[$name] }
  Write-AutopilotContextJson -Path $OutputPath -Value $delta
  return [pscustomobject]$delta
}

function Assert-AutopilotContextPairCurrent {
  param(
    [Parameter(Mandatory)][object]$Base,
    [Parameter(Mandatory)][object]$Delta,
    [Parameter(Mandatory)][object]$Issue,
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$ExpectedBaseCommit
  )
  $baseHash = Get-AutopilotCanonicalHash (Get-AutopilotContextIdentityPayload $Base)
  if ($Base.schemaVersion -ne 3 -or $Base.contextType -ne 'base' -or $Base.baseId -ne $baseHash -or $Base.contentHash -ne $baseHash) { throw 'context base identity is invalid' }
  if ($Base.issueId -ne $Issue.issueId -or $Base.readyContentHash -ne $Issue.readyContentHash) { throw 'context base Ready identity mismatch' }
  if ($Base.baseCommit -ne $ExpectedBaseCommit -or $Base.executionBaseCommit -ne $ExpectedBaseCommit) { throw 'context base commit mismatch' }
  $expectedCandidateHead = if ($Issue.PSObject.Properties.Name -contains 'candidateEvidenceHead') { [string]$Issue.candidateEvidenceHead } else { '' }
  if ([string]$Base.candidateEvidenceHead -ne $expectedCandidateHead) { throw 'context base candidate evidence head mismatch' }
  $policy = Get-AutopilotControlPlanePolicyDescriptor -RepoRoot $Worktree -PolicyPath $Base.controlPlanePolicyRefs[0]
  if ($Base.controlPlanePolicyVersion -ne $policy.version -or $Base.controlPlanePolicyHash -ne $policy.hash) { throw 'context base policy binding is stale' }
  $deltaHash = Get-AutopilotCanonicalHash (Get-AutopilotContextIdentityPayload $Delta)
  if ($Delta.schemaVersion -ne 1 -or $Delta.contextType -ne 'delta' -or $Delta.deltaId -ne $deltaHash -or $Delta.contentHash -ne $deltaHash) { throw 'context delta identity is invalid' }
  if ($Delta.issueId -ne $Base.issueId -or $Delta.baseId -ne $Base.baseId -or $Delta.baseHash -ne $Base.contentHash) { throw 'context delta base binding mismatch' }
  if ($Delta.readyContentHash -ne $Base.readyContentHash -or $Delta.baseCommit -ne $Base.baseCommit) { throw 'context delta Ready or base commit mismatch' }
  $actualHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $Base.baseCommit
  if ($Delta.diffHash -ne $actualHash) { throw 'context delta diff hash is stale' }
  return $true
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
