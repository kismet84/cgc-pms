$ErrorActionPreference = 'Stop'
$sourceScriptDir = Split-Path -Parent $PSScriptRoot
$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-desktop-canary-' + [guid]::NewGuid().ToString('N'))
$scriptDir = Join-Path $root 'scripts\codex-autopilot'
$autoDir = Join-Path $root '.codex-autopilot'
$reportRelative = 'docs/quality/ISSUE-CANARY-001.md'
$reportPath = Join-Path $root $reportRelative

try {
  New-Item -ItemType Directory -Path $scriptDir,$autoDir,(Split-Path -Parent $reportPath) -Force | Out-Null
  foreach ($name in @('autopilot-state.ps1','autopilot-control-plane-fingerprint.ps1','autopilot-register-canary.ps1','autopilot-start.ps1')) {
    Copy-Item -LiteralPath (Join-Path $sourceScriptDir $name) -Destination (Join-Path $scriptDir $name)
  }
  'stable-control-plane' | Set-Content -LiteralPath (Join-Path $root 'control.txt') -Encoding UTF8
  '# canary report' | Set-Content -LiteralPath $reportPath -Encoding UTF8
  [ordered]@{
    repoRoot = $root
    autopilotDir = $autoDir
    executionHost = 'desktop-native'
    controlPlaneCanary = [ordered]@{ enabled=$true; fingerprintPaths=@('control.txt') }
  } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $scriptDir 'codex-autopilot.config.json') -Encoding UTF8

  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  & git -C $root add .
  & git -C $root commit -qm 'canary fixture'
  if ($LASTEXITCODE -ne 0) { throw 'desktop canary fixture commit failed' }
  $head = (& git -C $root rev-parse HEAD).Trim()

  . (Join-Path $scriptDir 'autopilot-state.ps1')
  $now = [datetimeoffset]::Now.ToString('o')
  $state = [ordered]@{
    schemaVersion=2;runId='desktop-canary';status='LIMIT_REACHED';phase='limit_reached';currentIssue='';attempt=0
    startedAt=$now;phaseStartedAt=$now;lastHeartbeatAt=$now;iterationLimit=1;completedImplementationIssues=1
    completedIssueIds=@('ISSUE-CANARY-001');worktree='';branch='master';executorPid=$null;lastCommit=$head;failureFingerprint=$null
  }
  Write-AutopilotStateAtomic -Path (Join-Path $autoDir 'state.json') -State $state -ExecutionHost 'desktop-native' | Out-Null
  . (Join-Path $scriptDir 'autopilot-control-plane-fingerprint.ps1')
  $fingerprint = Get-AutopilotControlPlaneFingerprint -RepoRoot $root -Paths @('control.txt')
  $reportHash = (Get-FileHash -LiteralPath $reportPath -Algorithm SHA256).Hash.ToLowerInvariant()
  [pscustomobject][ordered]@{
    schemaVersion=2;key='canary-key';issueId='ISSUE-CANARY-001';readyContentHash=('1'*64);outcome='PASSED'
    implementationCommit=('2'*40);closeoutCommit=$head;reportPath=$reportRelative;reportHash=$reportHash;resultHash=('4'*64)
    preCloseoutFactsHash=('5'*64);evidenceManifestHash=('6'*64);reviewRequired=$true;reviewDecision='PASS';verifiedDiffHash=('7'*64)
    followupsAdded=0;followupsClosed=0;followupsNetChange=0;metricsSummary=[pscustomobject]@{};metricsHash=('8'*64)
    controlPlaneFingerprint=$fingerprint;graphCursorRequired=$true;graphGitCursor=$head;registeredAt=$now
  } | ConvertTo-Json -Compress -Depth 8 | Set-Content -LiteralPath (Join-Path $autoDir 'closeouts.ndjson') -Encoding UTF8

  $registration = & pwsh -NoProfile -File (Join-Path $scriptDir 'autopilot-register-canary.ps1') -RepoRoot $root -IssueId 'ISSUE-CANARY-001' -ReportPath $reportRelative -GraphGitCursor $head | ConvertFrom-Json
  if ($registration.status -ne 'CANARY_PASSED' -or $registration.controlPlaneFingerprint -ne $fingerprint) { throw 'desktop canary registration did not return the bound fingerprint' }
  $registeredState = Read-AutopilotState -Path (Join-Path $autoDir 'state.json')
  if ($registeredState.lastCanaryFingerprint -ne $fingerprint -or $registeredState.lastCanaryReport -ne $reportRelative) { throw 'desktop canary state was not persisted' }

  $oldPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  $rejectionOutput = & pwsh -NoProfile -File (Join-Path $scriptDir 'autopilot-register-canary.ps1') -RepoRoot $root -IssueId 'ISSUE-CANARY-UNKNOWN' -ReportPath $reportRelative -GraphGitCursor $head 2>&1 | Out-String
  $rejectionExitCode = $LASTEXITCODE
  $ErrorActionPreference = $oldPreference
  $rejected = $rejectionExitCode -ne 0 -and $rejectionOutput -match 'CONTROL_PLANE_CANARY_ISSUE_NOT_COMPLETED'
  if (!$rejected) { throw 'desktop canary registration accepted an uncompleted Issue' }

  & pwsh -NoProfile -File (Join-Path $scriptDir 'autopilot-start.ps1') -Repo $root -MaxIterations 5 | Out-Null
  $restartedState = Read-AutopilotState -Path (Join-Path $autoDir 'state.json')
  if ($restartedState.lastCanaryFingerprint -ne $fingerprint -or (Test-AutopilotControlPlaneCanaryRequired -Enabled $true -IterationLimit 5 -CurrentFingerprint $fingerprint -LastCanaryFingerprint $restartedState.lastCanaryFingerprint)) {
    throw 'same fingerprint incorrectly required another single-Issue canary after a new batch start'
  }

  Write-Host 'desktop canary registration self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
