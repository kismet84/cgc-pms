param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-verify.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-evidence-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null

try {
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  "*.json`r`n*.log" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'base' | Set-Content -LiteralPath (Join-Path $root 'file.txt') -Encoding UTF8
  & git -C $root add .
  & git -C $root commit -qm 'base'
  $base = (& git -C $root rev-parse HEAD).Trim()
  'changed' | Set-Content -LiteralPath (Join-Path $root 'file.txt') -Encoding UTF8

  $evidencePath = Join-Path $root 'evidence.json'
  $evidence = Invoke-AutopilotVerificationCommand -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base -Command 'powershell -NoProfile -Command "exit 0"' -EvidencePath $evidencePath -LogPath (Join-Path $root 'pass.log') -TimeoutSeconds 20
  if ($evidence.exitCode -ne 0 -or $evidence.classification -ne 'pass') { throw 'passing command was not recorded as pass' }
  Assert-AutopilotEvidenceCurrent -Evidence $evidence -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base | Out-Null

  'changed-again' | Set-Content -LiteralPath (Join-Path $root 'file.txt') -Encoding UTF8
  $staleRejected = $false
  try { Assert-AutopilotEvidenceCurrent -Evidence $evidence -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base | Out-Null } catch { $staleRejected = $true }
  if (!$staleRejected) { throw 'stale evidence was accepted after diff change' }

  $fresh = Invoke-AutopilotVerificationCommand -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base -Command 'git diff --check' -EvidencePath (Join-Path $root 'fresh-evidence.json') -LogPath (Join-Path $root 'fresh.log') -TimeoutSeconds 20
  'untracked' | Set-Content -LiteralPath (Join-Path $root 'new-file.txt') -Encoding UTF8
  $untrackedRejected = $false
  try { Assert-AutopilotEvidenceCurrent -Evidence $fresh -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base | Out-Null } catch { $untrackedRejected = $true }
  if (!$untrackedRejected) { throw 'untracked content did not invalidate evidence' }

  '验收' | Set-Content -LiteralPath (Join-Path $root '中文报告.md') -Encoding UTF8
  $unicodeDiffHash = Get-AutopilotDiffHash -Worktree $root -BaseCommit $base
  if ($unicodeDiffHash -notmatch '^[a-f0-9]{64}$') { throw 'Unicode untracked path did not produce a stable diff hash' }

  $failed = Invoke-AutopilotVerificationCommand -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base -Command 'powershell -NoProfile -Command "exit 7"' -EvidencePath (Join-Path $root 'failed-evidence.json') -LogPath (Join-Path $root 'fail.log') -TimeoutSeconds 20
  if ($failed.exitCode -ne 7 -or $failed.classification -eq 'pass') { throw 'nonzero exit code was recorded as pass' }

  if (Test-AutopilotPostExecutionVerificationRequired -Command 'powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/ready-lint.ps1 -RepoRoot .') { throw 'post-execution verification attempted to rerun the pre-dispatch Ready lint' }
  if (!(Test-AutopilotPostExecutionVerificationRequired -Command 'git diff --check')) { throw 'post-execution verification skipped a required non-lint command' }

  $readyHash = ('a' * 64)
  $readyEvidence = New-AutopilotReadyLintEvidence -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base -Command 'ready-lint.ps1' -ReadyContentHash $readyHash -ExpectedReadyContentHash $readyHash -EvidencePath (Join-Path $root 'ready-evidence.json') -LogPath (Join-Path $root 'ready.log')
  Assert-AutopilotEvidenceCurrent -Evidence $readyEvidence -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base | Out-Null
  if ($readyEvidence.readyContentHash -ne $readyHash -or $readyEvidence.summary -notmatch 'production parser') { throw 'normalized terminal Ready lint evidence is incomplete' }
  $mismatchRejected = $false
  try { New-AutopilotReadyLintEvidence -IssueId 'ISSUE-900-020' -Worktree $root -BaseCommit $base -Command 'ready-lint.ps1' -ReadyContentHash $readyHash -ExpectedReadyContentHash ('b' * 64) -EvidencePath (Join-Path $root 'bad-ready-evidence.json') -LogPath (Join-Path $root 'bad-ready.log') | Out-Null } catch { $mismatchRejected = $true }
  if (!$mismatchRejected) { throw 'mismatched terminal Ready contract hash produced pass evidence' }

  $secondEvidencePath = Join-Path $root 'evidence-02.json'
  Copy-Item -LiteralPath $evidencePath -Destination $secondEvidencePath
  $recoveredEvidencePaths = @(Get-AutopilotConcatenatedEvidencePaths -Message ("Cannot find path '$evidencePath$secondEvidencePath' because it does not exist."))
  if ($recoveredEvidencePaths.Count -ne 2 -or $recoveredEvidencePaths[0] -ne $evidencePath -or $recoveredEvidencePaths[1] -ne $secondEvidencePath) { throw 'concatenated verification evidence paths were not recovered independently' }

  $classifier = Join-Path (Resolve-Path (Join-Path $scriptDir '..\..')) 'plugins\cgc-pms-autopilot\scripts\test-failure-classifier.ps1'
  $classification = & $classifier -ErrorText 'No tests matching selector MissingTest' -ExitCode 1 | ConvertFrom-Json
  if ($classification.category -ne 'ready_issue_config' -or !$classification.failureFingerprint) { throw 'selector failure classification or fingerprint is missing' }
  $classification2 = & $classifier -ErrorText 'No tests matching selector MissingTest' -ExitCode 1 | ConvertFrom-Json
  if ($classification.failureFingerprint -ne $classification2.failureFingerprint) { throw 'failure fingerprint is not stable' }
  $multilineClassification = & $classifier -ErrorText "status: fail`ntaskNature: 缺口修复`nReady Issue 不存在或状态不是 Ready" -ExitCode 1 | ConvertFrom-Json
  if ($multilineClassification.category -ne 'ready_issue_config') { throw 'multiline classifier evidence was not passed as one string argument' }

  Write-Host 'evidence verification self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
