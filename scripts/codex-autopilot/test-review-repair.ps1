param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-review.ps1')
. (Join-Path $scriptDir 'autopilot-context.ps1')

$reviewSchema = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $scriptDir '..\..\plugins\cgc-pms-autopilot\schemas\review-result.schema.json') -Raw | ConvertFrom-Json
if ($reviewSchema.properties.schemaVersion.type -ne 'integer' -or
    $reviewSchema.properties.decision.type -ne 'string' -or
    $reviewSchema.properties.findings.items.properties.severity.type -ne 'string') {
  throw 'review response schema enum/const properties must declare explicit JSON types'
}

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-review-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $canonicalDiff = "diff --git a/a b/a`n+中文`n"
  $canonicalPath = Join-Path $root 'canonical.diff'
  Write-AutopilotReviewDiff -Text $canonicalDiff -OutputPath $canonicalPath
  if ((Get-FileHash -LiteralPath $canonicalPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne (Get-AutopilotTextHash $canonicalDiff)) { throw 'review diff file hash does not match canonical verification hash' }
  $route = [pscustomobject]@{ reviewRequired = $true }
  $missingReviewRejected = $false
  try { Assert-AutopilotReviewGate -Route $route -ReviewResult $null | Out-Null } catch { $missingReviewRejected = $true }
  if (!$missingReviewRejected) { throw 'high-risk route closed without Reviewer' }

  $leakRejected = $false
  try { New-AutopilotReviewRequest -IssueId 'ISSUE-900-030' -ReadyPath 'ready.md' -DiffPath 'diff.patch' -EvidencePaths @('executor.log') -OutputPath (Join-Path $root 'review-request.json') | Out-Null } catch { $leakRejected = $true }
  if (!$leakRejected) { throw 'Reviewer accepted Implementer conversation/log history' }

  $diffPath = Join-Path $root 'diff.patch'; 'diff' | Set-Content -LiteralPath $diffPath -Encoding UTF8
  $request = New-AutopilotReviewRequest -IssueId 'ISSUE-900-030' -ReadyPath 'ready.md' -DiffPath $diffPath -EvidencePaths @('evidence.json') -OutputPath (Join-Path $root 'review-request.json')
  if ($request.PSObject.Properties.Name -contains 'implementerReasoning') { throw 'review request leaked Implementer reasoning' }

  $pass = [pscustomobject]@{ schemaVersion = 1; issueId = 'ISSUE-900-030'; decision = 'pass'; findings = @(); reviewedDiffHash = $request.diffSha256; reviewedAt = [datetimeoffset]::Now.ToString('o') }
  Assert-AutopilotReviewGate -Route $route -ReviewResult $pass | Out-Null
  $mismatchRejected = $false
  try { Get-AutopilotReviewDisposition -ReviewResult $pass -ExpectedIssueId 'ISSUE-OTHER' -ExpectedDiffHash $request.diffSha256 | Out-Null } catch { $mismatchRejected = $true }
  if (!$mismatchRejected) { throw 'Reviewer identity mismatch was accepted' }
  $unboundPassRejected = $false
  try { Get-AutopilotReviewDisposition -ReviewResult $pass | Out-Null } catch { $unboundPassRejected = $true }
  if (!$unboundPassRejected) { throw 'unbound Reviewer pass was accepted' }

  $needsRepair = [pscustomobject]@{ schemaVersion = 1; issueId = 'ISSUE-900-030'; decision = 'needs_repair'; findings = @([pscustomobject]@{ severity='blocking'; file='a.ps1'; line=10; risk='bug'; requiredEvidence='test' }); reviewedDiffHash = 'def'; reviewedAt = [datetimeoffset]::Now.ToString('o') }
  $needsRepair.reviewedDiffHash = $request.diffSha256
  $disposition = Get-AutopilotReviewDisposition -ReviewResult $needsRepair -ExpectedIssueId 'ISSUE-900-030' -ExpectedDiffHash $request.diffSha256
  if ($disposition.action -ne 'REPAIR' -or !$disposition.failureFingerprint) { throw 'needs_repair was not routed to a bounded repair' }
  $blockedDisposition = Get-AutopilotReviewDisposition -ReviewResult ([pscustomobject]@{ decision='blocked'; findings=@() })
  if ($blockedDisposition.action -ne 'BLOCK') { throw 'blocked review was incorrectly made repairable' }
  if (Test-AutopilotCodeRepairAllowed -FailureCategory 'environment' -StopReason 'STOP_VERIFICATION_FAILED') { throw 'environment failure was routed to code repair' }
  if (!(Test-AutopilotCodeRepairAllowed -FailureCategory 'quality_security' -StopReason 'STOP_VERIFICATION_FAILED')) { throw 'quality failure lost bounded repair' }

  if (Test-AutopilotRetryAllowed -PreviousFingerprint 'same' -CurrentFingerprint 'same' -Attempt 1) { throw 'same failure fingerprint was retried' }
  if (!(Test-AutopilotRetryAllowed -PreviousFingerprint 'old' -CurrentFingerprint 'new' -Attempt 1)) { throw 'new failure fingerprint was not allowed within budget' }
  if (Test-AutopilotRetryAllowed -PreviousFingerprint 'old' -CurrentFingerprint 'new' -Attempt 2) { throw 'third repair loop was allowed' }

  Write-Host 'review repair self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
