param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-recover.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-recovery-test-' + [guid]::NewGuid().ToString('N'))
$autoDir = Join-Path $root '.codex-autopilot'
New-Item -ItemType Directory -Path $autoDir -Force | Out-Null

try {
  $none = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($none.action -ne 'NEW_RUN') { throw 'missing lock did not allow a new run' }

  [ordered]@{ pid = $PID; heartbeatAt = [datetimeoffset]::Now.ToString('o'); runId = 'run-active'; issueId = 'ISSUE-1' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $autoDir 'run.lock') -Encoding UTF8
  $active = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($active.action -ne 'REFUSE_SECOND_INSTANCE') { throw 'active lock did not refuse second instance' }

  [ordered]@{ pid = 999999; heartbeatAt = [datetimeoffset]::Now.AddMinutes(-11).ToString('o'); runId = 'run-dead'; issueId = 'ISSUE-1' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $autoDir 'run.lock') -Encoding UTF8
  $dead = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($dead.action -ne 'RESUME_FROM_CHECKPOINT') { throw 'dead executor without worktree did not resume safely' }

  if ((Get-AutopilotStallLevel -LastProgressAt ([datetimeoffset]::Now.AddMinutes(-6))) -ne 'INSPECT') { throw '5-minute stall was not detected' }
  if ((Get-AutopilotStallLevel -LastProgressAt ([datetimeoffset]::Now.AddMinutes(-11))) -ne 'TERMINATE') { throw '10-minute stall was not detected' }

  $key1 = Get-AutopilotCloseoutKey -IssueId 'ISSUE-1' -Commit 'abc' -ReportPath 'docs/quality/a.md'
  $key2 = Get-AutopilotCloseoutKey -IssueId 'ISSUE-1' -Commit 'abc' -ReportPath 'docs/quality/a.md'
  if ($key1 -ne $key2) { throw 'closeout idempotency key is unstable' }
  $ledger = Join-Path $autoDir 'closeouts.ndjson'
  Register-AutopilotCloseout -LedgerPath $ledger -Key $key1 | Out-Null
  Register-AutopilotCloseout -LedgerPath $ledger -Key $key1 | Out-Null
  if (@(Get-Content -LiteralPath $ledger).Count -ne 1) { throw 'duplicate closeout was registered' }

  Write-Host 'recovery self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
