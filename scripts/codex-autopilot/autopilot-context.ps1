$ErrorActionPreference = 'Stop'

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
  $diff = (& git -c core.safecrlf=false -C $Worktree diff --binary $BaseCommit -- 2>$null | Out-String)
  if ($LASTEXITCODE -ne 0) { throw 'cannot calculate worktree diff hash' }
  foreach ($path in @(& git -c core.quotePath=false -c core.autocrlf=false -c core.safecrlf=false -C $Worktree ls-files --others --exclude-standard)) {
    $untrackedDiff = (& git -c core.safecrlf=false -C $Worktree diff --no-index --binary -- NUL $path 2>$null | Out-String)
    if ($LASTEXITCODE -notin @(0,1)) { throw "cannot include untracked file in diff: $path" }
    $diff += $untrackedDiff
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
  $baseCommit = (& git -C $Worktree rev-parse HEAD).Trim()
  if ($LASTEXITCODE -ne 0) { throw 'cannot resolve worktree base commit' }
  $context = [ordered]@{
    schemaVersion = 1
    issueId = $Issue.issueId
    phase = $Phase
    baseCommit = $baseCommit
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
  $actualHash = Get-AutopilotDiffHash -Worktree $Worktree -BaseCommit $Context.baseCommit
  if ($Context.diffHash -ne $actualHash) { throw 'context diff hash is stale' }
  return $true
}
