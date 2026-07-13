param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-control-plane-fingerprint.ps1')

$realConfig = Get-Content -LiteralPath (Join-Path $scriptDir 'codex-autopilot.config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (@($realConfig.controlPlaneCanary.fingerprintPaths) -notcontains 'scripts/codex-autopilot/codex-autopilot.config.json') { throw 'control-plane fingerprint does not cover its behavior configuration' }

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-control-plane-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  'alpha' | Set-Content -LiteralPath (Join-Path $root 'a.ps1') -Encoding UTF8
  'beta' | Set-Content -LiteralPath (Join-Path $root 'b.json') -Encoding UTF8
  $first = Get-AutopilotControlPlaneFingerprint -RepoRoot $root -Paths @('b.json','a.ps1')
  $again = Get-AutopilotControlPlaneFingerprint -RepoRoot $root -Paths @('a.ps1','b.json')
  if ($first -ne $again -or $first -notmatch '^[a-f0-9]{64}$') { throw 'control-plane fingerprint is not deterministic' }
  if (Test-AutopilotControlPlaneCanaryRequired -Enabled $true -IterationLimit 1 -CurrentFingerprint $first -LastCanaryFingerprint '') { throw 'single-Issue canary was blocked by its own gate' }
  if (!(Test-AutopilotControlPlaneCanaryRequired -Enabled $true -IterationLimit 10 -CurrentFingerprint $first -LastCanaryFingerprint '')) { throw 'N>1 was allowed without canary evidence' }
  if (Test-AutopilotControlPlaneCanaryRequired -Enabled $true -IterationLimit 10 -CurrentFingerprint $first -LastCanaryFingerprint $first) { throw 'matching canary fingerprint did not unlock N>1' }
  '{"dispatch":"changed"}' | Set-Content -LiteralPath (Join-Path $root 'b.json') -Encoding UTF8
  $configOnlyChanged = Get-AutopilotControlPlaneFingerprint -RepoRoot $root -Paths @('a.ps1','b.json')
  if ($configOnlyChanged -eq $first -or !(Test-AutopilotControlPlaneCanaryRequired -Enabled $true -IterationLimit 10 -CurrentFingerprint $configOnlyChanged -LastCanaryFingerprint $first)) { throw 'configuration-only change did not require a new canary' }
  'changed' | Add-Content -LiteralPath (Join-Path $root 'a.ps1') -Encoding UTF8
  $changed = Get-AutopilotControlPlaneFingerprint -RepoRoot $root -Paths @('a.ps1','b.json')
  if ($changed -eq $first -or !(Test-AutopilotControlPlaneCanaryRequired -Enabled $true -IterationLimit $null -CurrentFingerprint $changed -LastCanaryFingerprint $first)) { throw 'control-plane change did not invalidate the prior canary' }
  if (Test-AutopilotControlPlaneCanaryRequired -Enabled $false -IterationLimit 10 -CurrentFingerprint $changed -LastCanaryFingerprint '') { throw 'disabled fixture gate blocked execution' }
  Write-Host 'control-plane fingerprint self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
