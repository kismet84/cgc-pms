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

  & git -C $root init -q; & git -C $root config user.email 'autopilot@test.local'; & git -C $root config user.name 'AutoPilot Test'
  & git -C $root config core.autocrlf false; & git -C $root config core.eol lf; & git -C $root config core.safecrlf false
  [IO.File]::WriteAllText((Join-Path $root '.gitattributes'), "* text=auto eol=lf`n*.cmd text eol=crlf`n", [Text.UTF8Encoding]::new($false))
  ".worktrees/`r`n.codex-autopilot/" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'base' | Set-Content -LiteralPath (Join-Path $root 'base.txt') -Encoding UTF8
  & git -C $root add .; & git -C $root commit -qm 'base'
  $worktree = Join-Path $root '.worktrees\recover'; & git -C $root worktree add -q -b codex/recover $worktree HEAD
  'closed' | Set-Content -LiteralPath (Join-Path $worktree 'closed.txt') -Encoding UTF8
  & git -C $worktree add .; & git -C $worktree commit -qm 'closed issue'
  [ordered]@{ pid = 999999; heartbeatAt = [datetimeoffset]::Now.AddMinutes(-11).ToString('o'); runId = 'run-commit'; issueId = 'ISSUE-2' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $autoDir 'run.lock') -Encoding UTF8
  [ordered]@{ status='COMMITTING'; worktree=$worktree; lastCommit=$null } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $autoDir 'state.json') -Encoding UTF8
  $committed = Get-AutopilotRecoveryDecision -AutoDir $autoDir
  if ($committed.action -ne 'QUARANTINE' -or !$committed.commit) { throw 'uncertain committed worktree was not quarantined' }
  if (Test-Path -LiteralPath (Join-Path $root 'closed.txt')) { throw 'uncertain commit was merged without gate evidence' }
  if (!(Test-Path -LiteralPath $worktree)) { throw 'uncertain worktree was deleted instead of preserved' }

  if ((Get-AutopilotStallLevel -LastProgressAt ([datetimeoffset]::Now.AddMinutes(-6))) -ne 'INSPECT') { throw '5-minute stall was not detected' }
  if ((Get-AutopilotStallLevel -LastProgressAt ([datetimeoffset]::Now.AddMinutes(-11))) -ne 'TERMINATE') { throw '10-minute stall was not detected' }

  $key1 = Get-AutopilotCloseoutKey -IssueId 'ISSUE-1' -Commit 'abc' -ReportPath 'docs/quality/a.md'
  $key2 = Get-AutopilotCloseoutKey -IssueId 'ISSUE-1' -Commit 'abc' -ReportPath 'docs/quality/a.md'
  if ($key1 -ne $key2) { throw 'closeout idempotency key is unstable' }
  $ledger = Join-Path $autoDir 'closeouts.ndjson'
  Register-AutopilotCloseout -LedgerPath $ledger -Key $key1 | Out-Null
  Register-AutopilotCloseout -LedgerPath $ledger -Key $key1 | Out-Null
  if (@(Get-Content -Encoding UTF8 -LiteralPath $ledger).Count -ne 1) { throw 'duplicate closeout was registered' }
  $score10 = [pscustomobject]@{key='score-key';total=85;dimensions=[pscustomobject]@{taskExecutionEfficiency=[pscustomobject]@{score=10}}}
  $score0 = [pscustomobject]@{key='score-key';total=75;dimensions=[pscustomobject]@{taskExecutionEfficiency=[pscustomobject]@{score=0}}}
  $candidateLedger = Join-Path $autoDir 'candidate-score-shadows.ndjson'
  Set-AutopilotCloseoutCandidateScore -LedgerPath $candidateLedger -Key $key1 -Score $score10 -PhaseMetrics ([pscustomobject]@{runResumeCount=0}) | Out-Null
  Set-AutopilotCloseoutCandidateScore -LedgerPath $candidateLedger -Key $key1 -Score $score0 -PhaseMetrics ([pscustomobject]@{runResumeCount=1}) | Out-Null
  $candidateEntry = Get-Content -LiteralPath $candidateLedger -Raw -Encoding UTF8 | ConvertFrom-Json
  if ([int]$candidateEntry.candidateScoreV2.dimensions.taskExecutionEfficiency.score -ne 0 -or [int]$candidateEntry.phaseMetrics.runResumeCount -ne 1) { throw 'candidate efficiency ledger did not idempotently update after cross-run recovery' }

  Write-Host 'recovery self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
