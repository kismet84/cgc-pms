$ErrorActionPreference = 'Stop'

function Get-AutopilotStallLevel {
  param([Parameter(Mandatory)][datetimeoffset]$LastProgressAt)
  $minutes = ([datetimeoffset]::Now - $LastProgressAt).TotalMinutes
  if ($minutes -ge 10) { return 'TERMINATE' }
  if ($minutes -ge 5) { return 'INSPECT' }
  return 'HEALTHY'
}

function Get-AutopilotRecoveryDecision {
  param([Parameter(Mandatory)][string]$AutoDir)
  $lockPath = Join-Path $AutoDir 'run.lock'
  $statePath = Join-Path $AutoDir 'state.json'
  if (!(Test-Path -LiteralPath $lockPath)) { return [pscustomobject]@{ action = 'NEW_RUN'; reason = 'no run.lock' } }
  try { $lock = Get-Content -LiteralPath $lockPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { return [pscustomobject]@{ action = 'QUARANTINE'; reason = 'run.lock is unreadable' } }
  $process = if ($lock.pid) { Get-Process -Id ([int]$lock.pid) -ErrorAction SilentlyContinue } else { $null }
  if ($process) { return [pscustomobject]@{ action = 'REFUSE_SECOND_INSTANCE'; reason = 'owner PID is alive'; runId = $lock.runId } }
  $state = $null
  if (Test-Path -LiteralPath $statePath) { try { $state = Get-Content -LiteralPath $statePath -Raw -Encoding UTF8 | ConvertFrom-Json } catch {} }
  if ($state -and $state.lastCommit -and $state.status -in @('CLOSING','COMMITTING','MERGING')) { return [pscustomobject]@{ action = 'RESUME_CLOSEOUT'; reason = 'commit exists but closeout is incomplete'; runId = $lock.runId } }
  if ($state -and $state.worktree -and (Test-Path -LiteralPath $state.worktree)) {
    $changes = @(& git -C $state.worktree status --porcelain=v1 2>$null)
    if ($changes.Count -gt 0) { return [pscustomobject]@{ action = 'VERIFY_UNCOMMITTED'; reason = 'dead executor left a diff'; runId = $lock.runId; worktree = $state.worktree } }
  }
  return [pscustomobject]@{ action = 'RESUME_FROM_CHECKPOINT'; reason = 'owner PID is gone and no unsafe diff was found'; runId = $lock.runId }
}

function Get-AutopilotCloseoutKey {
  param([string]$IssueId, [string]$Commit, [string]$ReportPath)
  $source = "$IssueId|$Commit|$($ReportPath.Replace('\','/').ToLowerInvariant())"
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($source)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
}

function Register-AutopilotCloseout {
  param([string]$LedgerPath, [string]$Key)
  $parent = Split-Path -Parent $LedgerPath
  if ($parent -and !(Test-Path -LiteralPath $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
  if (Test-Path -LiteralPath $LedgerPath) {
    foreach ($line in Get-Content -LiteralPath $LedgerPath) {
      try { if (($line | ConvertFrom-Json).key -eq $Key) { return $false } } catch {}
    }
  }
  $entry = [ordered]@{ key = $Key; registeredAt = [datetimeoffset]::Now.ToString('o') } | ConvertTo-Json -Compress
  [IO.File]::AppendAllText($LedgerPath, $entry + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
  return $true
}
