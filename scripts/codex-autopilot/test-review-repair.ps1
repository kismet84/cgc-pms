param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-review.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-review-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $route = [pscustomobject]@{ reviewRequired = $true }
  $missingReviewRejected = $false
  try { Assert-AutopilotReviewGate -Route $route -ReviewResult $null | Out-Null } catch { $missingReviewRejected = $true }
  if (!$missingReviewRejected) { throw 'high-risk route closed without Reviewer' }

  $leakRejected = $false
  try { New-AutopilotReviewRequest -IssueId 'ISSUE-900-030' -ReadyPath 'ready.md' -DiffPath 'diff.patch' -EvidencePaths @('executor.log') -OutputPath (Join-Path $root 'review-request.json') | Out-Null } catch { $leakRejected = $true }
  if (!$leakRejected) { throw 'Reviewer accepted Implementer conversation/log history' }

  $request = New-AutopilotReviewRequest -IssueId 'ISSUE-900-030' -ReadyPath 'ready.md' -DiffPath 'diff.patch' -EvidencePaths @('evidence.json') -OutputPath (Join-Path $root 'review-request.json')
  if ($request.PSObject.Properties.Name -contains 'implementerReasoning') { throw 'review request leaked Implementer reasoning' }

  $pass = [pscustomobject]@{ schemaVersion = 1; issueId = 'ISSUE-900-030'; decision = 'pass'; findings = @(); reviewedDiffHash = 'abc'; reviewedAt = [datetimeoffset]::Now.ToString('o') }
  Assert-AutopilotReviewGate -Route $route -ReviewResult $pass | Out-Null

  if (Test-AutopilotRetryAllowed -PreviousFingerprint 'same' -CurrentFingerprint 'same' -Attempt 1) { throw 'same failure fingerprint was retried' }
  if (!(Test-AutopilotRetryAllowed -PreviousFingerprint 'old' -CurrentFingerprint 'new' -Attempt 1)) { throw 'new failure fingerprint was not allowed within budget' }
  if (Test-AutopilotRetryAllowed -PreviousFingerprint 'old' -CurrentFingerprint 'new' -Attempt 2) { throw 'third repair loop was allowed' }

  Write-Host 'review repair self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
