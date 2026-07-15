$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $PSScriptRoot
$temp = Join-Path ([IO.Path]::GetTempPath()) ('cgc-pms-no-nested-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
  $marker = Join-Path $temp 'codex-invoked.txt'
  $fakeCodex = Join-Path $temp 'codex.cmd'
  [IO.File]::WriteAllText($fakeCodex, "@echo invoked>`"$marker`"`r`nexit /b 99`r`n", [Text.UTF8Encoding]::new($false))
  $oldPath = $env:PATH
  $env:PATH = "$temp;$oldPath"

  . (Join-Path $scriptDir 'autopilot-refill.ps1')
  $plannerBlocked = $false
  try { Invoke-AutopilotReadyPlanner -RepoRoot $temp -Candidates @() -OutputPath (Join-Path $temp 'plan.json') -SchemaPath 'unused' -RunId 'test' -ExecutionHost 'desktop-native' | Out-Null }
  catch { $plannerBlocked = $_.Exception.Message -match 'DESKTOP_NATIVE_MODEL_PROCESS_FORBIDDEN' }
  if (!$plannerBlocked) { throw 'desktop Planner model process was not blocked' }

  . (Join-Path $scriptDir 'autopilot-review.ps1')
  $reviewerBlocked = $false
  try { Invoke-AutopilotReviewerProcess -ExecutionHost 'desktop-native' | Out-Null }
  catch { $reviewerBlocked = $_.Exception.Message -match 'DESKTOP_NATIVE_MODEL_PROCESS_FORBIDDEN' }
  if (!$reviewerBlocked) { throw 'desktop Reviewer model process was not blocked' }
  if (Test-Path -LiteralPath $marker) { throw 'a nested codex process was launched' }

  $execText = Get-Content -LiteralPath (Join-Path $scriptDir 'autopilot-exec-issue.ps1') -Raw -Encoding UTF8
  if ($execText -notmatch 'Test-AutopilotDesktopNativeHost' -or $execText -notmatch 'Assert-AutopilotLegacyModelProcessAllowed') { throw 'Executor host guard is missing' }
  $refillText = Get-Content -LiteralPath (Join-Path $scriptDir 'autopilot-refill.ps1') -Raw -Encoding UTF8
  $reviewText = Get-Content -LiteralPath (Join-Path $scriptDir 'autopilot-review.ps1') -Raw -Encoding UTF8
  $supervisorText = Get-Content -LiteralPath (Join-Path $scriptDir 'autopilot-executor-supervisor.ps1') -Raw -Encoding UTF8
  if ($refillText.IndexOf('Assert-AutopilotLegacyModelProcessAllowed') -gt $refillText.IndexOf('Resolve-AutopilotCodexInvocation')) { throw 'Planner guard runs after Codex resolution' }
  if ($reviewText.IndexOf('Assert-AutopilotLegacyModelProcessAllowed') -gt $reviewText.IndexOf('Resolve-AutopilotCodexInvocation')) { throw 'Reviewer guard runs after Codex resolution' }
  if ($supervisorText -notmatch "Role 'EXECUTOR_SUPERVISOR'") { throw 'Executor supervisor host guard is missing' }
  Write-Host 'no nested codex self-test passed'
} finally {
  $env:PATH = $oldPath
  Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
