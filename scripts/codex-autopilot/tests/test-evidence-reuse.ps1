param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $scriptDir 'autopilot-verify.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-evidence-reuse-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  & git -C $root config core.autocrlf false
  & git -C $root config core.eol lf
  "*.json`n*.log" | Set-Content -LiteralPath (Join-Path $root '.gitignore') -Encoding UTF8
  'base' | Set-Content -LiteralPath (Join-Path $root 'file.txt') -Encoding UTF8
  & git -C $root add .
  & git -C $root commit -qm 'base'
  $base = (& git -C $root rev-parse HEAD).Trim()
  'changed' | Set-Content -LiteralPath (Join-Path $root 'file.txt') -Encoding UTF8
  $command = 'pwsh -NoProfile -Command "exit 0"'
  $identity = @{
    ReadyContentHash=('a' * 64); ContextBaseId=('b' * 64); ContextBaseHash=('b' * 64)
    ContextDeltaId=('c' * 64); ContextDeltaHash=('c' * 64); CandidateEvidenceHead=('d' * 40)
    ExecutionBaseCommit=$base; ControlPlanePolicyHash=('e' * 64); AcceptanceRef='ready:test'
  }
  $validationIdentity = @{}
  foreach ($name in @('ReadyContentHash','ContextBaseId','ContextBaseHash','ContextDeltaId','ContextDeltaHash','CandidateEvidenceHead','ExecutionBaseCommit','ControlPlanePolicyHash')) { $validationIdentity[$name] = $identity[$name] }
  $sourcePath = Join-Path $root 'source-evidence.json'
  $source = Invoke-AutopilotVerificationCommand -IssueId 'ISSUE-900-046' -Worktree $root -BaseCommit $base -Command $command -EvidencePath $sourcePath -LogPath (Join-Path $root 'source.log') -EvidenceCategory UNIT_BUILD -TimeoutSeconds 20 @identity
  $sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
  $environment = Get-AutopilotVerificationEnvironment -Worktree $root
  $decision = Test-AutopilotEvidenceReusable -Evidence $source -IssueId 'ISSUE-900-046' -Command $command -DiffHash $source.diffHash -EnvironmentFingerprint $environment.fingerprint @validationIdentity
  if (!$decision.reusable -or $decision.reasonCode -ne 'REUSABLE') { throw 'valid UNIT_BUILD evidence was not reusable' }
  $reusedPath = Join-Path $root 'reused-evidence.json'
  $reused = New-AutopilotReusedEvidence -SourceEvidence $source -EvidencePath $reusedPath
  if ($reused.executionMode -ne 'REUSED' -or $reused.sourceEvidenceId -ne $source.evidenceId -or $reused.evidenceId -eq $source.evidenceId) { throw 'reused evidence identity is invalid' }
  if ((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash -ne $sourceHash) { throw 'source evidence was modified by reuse' }
  Assert-AutopilotEvidenceCurrent -Evidence $reused -IssueId 'ISSUE-900-046' -Worktree $root -BaseCommit $base @validationIdentity | Out-Null

  $commandChanged = Test-AutopilotEvidenceReusable -Evidence $source -IssueId 'ISSUE-900-046' -Command "$command # changed" -DiffHash $source.diffHash -EnvironmentFingerprint $environment.fingerprint @validationIdentity
  if ($commandChanged.reasonCode -ne 'COMMAND_CHANGED') { throw 'command identity change was not classified' }
  $environmentChanged = Test-AutopilotEvidenceReusable -Evidence $source -IssueId 'ISSUE-900-046' -Command $command -DiffHash $source.diffHash -EnvironmentFingerprint ('f' * 64) @validationIdentity
  if ($environmentChanged.reasonCode -ne 'ENVIRONMENT_CHANGED') { throw 'environment identity change was not classified' }
  $static = $source.PSObject.Copy(); $static.evidenceCategory = 'STATIC_CHEAP'
  $staticDecision = Test-AutopilotEvidenceReusable -Evidence $static -IssueId 'ISSUE-900-046' -Command $command -DiffHash $source.diffHash -EnvironmentFingerprint $environment.fingerprint @validationIdentity
  if ($staticDecision.reasonCode -ne 'CATEGORY_NOT_REUSABLE') { throw 'STATIC_CHEAP evidence was incorrectly reusable' }
  $legacy = [pscustomobject]@{schemaVersion=1;issueId='ISSUE-900-046';baseCommit=$base;commit=$base;diffHash=$source.diffHash;command=$command;startedAt=[datetimeoffset]::Now.ToString('o');durationSeconds=0;exitCode=0;classification='pass';summary='legacy';rawLogPath='legacy.log'}
  $legacyDecision = Test-AutopilotEvidenceReusable -Evidence $legacy -IssueId 'ISSUE-900-046' -Command $command -DiffHash $source.diffHash -EnvironmentFingerprint $environment.fingerprint @validationIdentity
  if ($legacyDecision.reasonCode -ne 'EVIDENCE_V1_NOT_REUSABLE') { throw 'Evidence v1 was promoted to reusable v2 evidence' }
  Write-Host 'evidence reuse self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
