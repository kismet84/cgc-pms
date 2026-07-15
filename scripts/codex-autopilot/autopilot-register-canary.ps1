param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [string]$ConfigPath = '',
  [Parameter(Mandatory)][string]$IssueId,
  [Parameter(Mandatory)][string]$ReportPath,
  [Parameter(Mandatory)][string]$GraphGitCursor,
  [string]$LedgerPath = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-state.ps1')
. (Join-Path $PSScriptRoot 'autopilot-control-plane-fingerprint.ps1')

if (!$ConfigPath) { $ConfigPath = Join-Path $PSScriptRoot 'codex-autopilot.config.json' }
if (!(Test-Path -LiteralPath $ConfigPath -PathType Leaf)) { throw "AutoPilot config is missing: $ConfigPath" }
$config = Get-Content -LiteralPath $ConfigPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]$config.executionHost -ne 'desktop-native') { throw 'CONTROL_PLANE_CANARY_REGISTRATION_REQUIRES_DESKTOP_NATIVE' }
if (!$config.controlPlaneCanary -or $config.controlPlaneCanary.enabled -ne $true) { throw 'CONTROL_PLANE_CANARY_REGISTRATION_DISABLED' }

$autoDir = if ($config.autopilotDir) { [string]$config.autopilotDir } else { Join-Path $RepoRoot '.codex-autopilot' }
$statePath = Join-Path $autoDir 'state.json'
if (!(Test-Path -LiteralPath $statePath -PathType Leaf)) { throw 'CONTROL_PLANE_CANARY_STATE_MISSING' }
$state = Read-AutopilotState -Path $statePath
if ($null -eq $state.iterationLimit -or [int]$state.iterationLimit -ne 1) { throw 'CONTROL_PLANE_CANARY_REQUIRES_SINGLE_ISSUE_BATCH' }
if (@($state.completedIssueIds) -notcontains $IssueId) { throw "CONTROL_PLANE_CANARY_ISSUE_NOT_COMPLETED: $IssueId" }

$repoFull = [IO.Path]::GetFullPath($RepoRoot).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$normalizedReport = $ReportPath.Replace('\','/').TrimStart('/')
$reportFull = [IO.Path]::GetFullPath((Join-Path $repoFull $normalizedReport))
$repoPrefix = $repoFull + [IO.Path]::DirectorySeparatorChar
if (!$reportFull.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'CONTROL_PLANE_CANARY_REPORT_OUTSIDE_REPOSITORY' }
if (!(Test-Path -LiteralPath $reportFull -PathType Leaf)) { throw "CONTROL_PLANE_CANARY_REPORT_MISSING: $normalizedReport" }
$reportHash = (Get-FileHash -LiteralPath $reportFull -Algorithm SHA256).Hash.ToLowerInvariant()

$fingerprint = Get-AutopilotControlPlaneFingerprint -RepoRoot $repoFull -Paths @($config.controlPlaneCanary.fingerprintPaths)
$head = (& git -C $repoFull rev-parse HEAD 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[a-f0-9]{40}$') { throw 'CONTROL_PLANE_CANARY_GIT_HEAD_UNAVAILABLE' }
$cursor = $GraphGitCursor.Trim().ToLowerInvariant()
if ($cursor -notmatch '^[a-f0-9]{40}$' -or $cursor -ne $head.ToLowerInvariant()) { throw "CONTROL_PLANE_CANARY_GRAPH_CURSOR_MISMATCH: expected=$head actual=$cursor" }

if (!$LedgerPath) { $LedgerPath = Join-Path $autoDir 'closeouts.ndjson' }
if (!(Test-Path -LiteralPath $LedgerPath -PathType Leaf)) { throw 'CONTROL_PLANE_CANARY_CLOSEOUT_LEDGER_MISSING' }
$matchingRecord = $null
foreach ($line in @(Get-Content -LiteralPath $LedgerPath -Encoding UTF8)) {
  if ([string]::IsNullOrWhiteSpace($line)) { continue }
  try { $record = $line | ConvertFrom-Json } catch { throw 'CONTROL_PLANE_CANARY_CLOSEOUT_LEDGER_INVALID' }
  if ([int]$record.schemaVersion -eq 2 -and [string]$record.issueId -eq $IssueId -and [string]$record.outcome -eq 'PASSED' -and
      ([string]$record.reportPath).Replace('\','/').TrimStart('/') -eq $normalizedReport -and [bool]$record.graphCursorRequired -and
      ([string]$record.reportHash).ToLowerInvariant() -eq $reportHash -and ([string]$record.implementationCommit) -match '^[a-f0-9]{40}$' -and
      ([string]$record.closeoutCommit).ToLowerInvariant() -eq $cursor -and ([string]$record.implementationCommit).ToLowerInvariant() -ne $cursor -and
      ([string]$record.graphGitCursor).ToLowerInvariant() -eq $cursor -and ([string]$record.controlPlaneFingerprint).ToLowerInvariant() -eq $fingerprint) {
    $matchingRecord = $record
  }
}
if (!$matchingRecord) { throw 'CONTROL_PLANE_CANARY_CLOSEOUT_RECORD_NOT_FOUND' }

$now = [datetimeoffset]::Now.ToString('o')
Set-AutopilotProperty $state 'lastCanaryFingerprint' $fingerprint
Set-AutopilotProperty $state 'lastCanaryReport' $normalizedReport
Set-AutopilotProperty $state 'controlPlaneFingerprint' $fingerprint
Set-AutopilotProperty $state 'lastHeartbeatAt' $now
Set-AutopilotProperty $state 'lastAction' 'CONTROL_PLANE_CANARY_PASSED'
Set-AutopilotProperty $state 'lastReason' 'desktop-native canary evidence registered and read back'
$written = Write-AutopilotStateAtomic -Path $statePath -State $state -ExecutionHost 'desktop-native'
$readBack = Read-AutopilotState -Path $statePath
if ([string]$readBack.lastCanaryFingerprint -ne $fingerprint -or [string]$readBack.lastCanaryReport -ne $normalizedReport) {
  throw 'CONTROL_PLANE_CANARY_STATE_READBACK_FAILED'
}

[pscustomobject][ordered]@{
  status = 'CANARY_PASSED'
  issueId = $IssueId
  controlPlaneFingerprint = $fingerprint
  reportPath = $normalizedReport
  graphGitCursor = $cursor
  stateGeneration = [int]$written.generation
} | ConvertTo-Json -Depth 4
