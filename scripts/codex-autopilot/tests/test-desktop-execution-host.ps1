$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$configPath = Join-Path $scriptDir 'codex-autopilot.config.json'
$config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
. (Join-Path $scriptDir 'autopilot-execution-host.ps1')

if ((Get-AutopilotExecutionHost -Config $config) -ne 'desktop-native') { throw 'production execution host is not desktop-native' }
if ((Get-AutopilotExecutionHost -Config ([pscustomobject]@{})) -ne 'cli-legacy') { throw 'legacy fixture compatibility default changed' }

$raw = & pwsh -NoProfile -File (Join-Path $scriptDir 'autopilot-run-continuous.ps1') -RepoRoot $repoRoot -MaxIterations 1
if ($LASTEXITCODE -ne 0) { throw 'desktop runner handoff failed' }
$handoff = ($raw -join "`n") | ConvertFrom-Json
if ($handoff.decision -ne 'DESKTOP_NATIVE_HANDOFF_REQUIRED' -or $handoff.executionHost -ne 'desktop-native') { throw 'desktop handoff contract is invalid' }
if ([int]$handoff.nestedModelCliInvocationCount -ne 0) { throw 'desktop handoff reported a nested model invocation' }
if ($handoff.maxIterations -ne 1) { throw 'desktop handoff lost the iteration limit' }
if ([string]$handoff.registerCanaryCommand -notmatch 'autopilot-register-canary\.ps1' -or [string]$handoff.registerCanaryCommand -notmatch 'GraphGitCursor') { throw 'desktop handoff omitted the deterministic canary registration command' }

Write-Host 'desktop execution host self-test passed'
