param()

$ErrorActionPreference = "Stop"
$StartScript = Join-Path $PSScriptRoot "autopilot-start.ps1"
$Repo = Join-Path ([System.IO.Path]::GetTempPath()) ("cgc-pms-autopilot-start-test-" + [guid]::NewGuid().ToString("N"))
$AutoDir = Join-Path $Repo ".codex-autopilot"

try {
  New-Item -ItemType Directory -Path $AutoDir -Force | Out-Null
  @'
{
  "enabled": false,
  "startedAt": "2026-07-09T13:29:13",
  "status": "STOPPED",
  "iterationLimit": 10,
  "iterationCompleted": 10,
  "remainingIterations": 0,
  "iterationLastCountedIssue": "ISSUE-008-021"
}
'@ | Out-File -Encoding utf8 (Join-Path $AutoDir "state.json")

  & powershell -NoProfile -ExecutionPolicy Bypass -File $StartScript -Repo $Repo -MaxIterations 10 | Out-Null
  $State = Get-Content -Raw (Join-Path $AutoDir "state.json") | ConvertFrom-Json

  if ($State.iterationLimit -ne 10) { throw "Expected iterationLimit=10" }
  if ($State.iterationCompleted -ne 0) { throw "Expected iterationCompleted=0" }
  if ($State.remainingIterations -ne 10) { throw "Expected remainingIterations=10" }
  if ($State.iterationLastCountedIssue) { throw "Expected iterationLastCountedIssue empty" }
  if ($State.startedAt -eq "2026-07-09T13:29:13") { throw "Expected startedAt refreshed" }
  if (!(Test-Path (Join-Path $AutoDir "enabled.flag"))) { throw "Expected enabled.flag" }

  Write-Host "autopilot new iteration start test passed"
} finally {
  Remove-Item -LiteralPath $Repo -Recurse -Force -ErrorAction SilentlyContinue
}
