param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-progress.ps1')
. (Join-Path $scriptDir 'autopilot-context.ps1')
$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-progress-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  & git -C $root init -q
  & git -C $root config user.email 'autopilot@test.local'
  & git -C $root config user.name 'AutoPilot Test'
  'base' | Set-Content -LiteralPath (Join-Path $root 'tracked.txt') -Encoding UTF8
  & git -C $root add .; & git -C $root commit -qm 'base'
  'change-one' | Set-Content -LiteralPath (Join-Path $root 'tracked.txt') -Encoding UTF8
  $first = Get-AutopilotProgressFingerprint -Worktree $root -RootPid $PID
  Start-Sleep -Milliseconds 20
  'change-two' | Set-Content -LiteralPath (Join-Path $root 'tracked.txt') -Encoding UTF8
  $second = Get-AutopilotProgressFingerprint -Worktree $root -RootPid $PID
  if ($first -eq $second) { throw 'same status with different file content was treated as no progress' }
  & git -C $root config core.autocrlf true
  [IO.File]::WriteAllText((Join-Path $root 'tracked.txt'), "lf-only`n", [Text.UTF8Encoding]::new($false))
  $diff = Get-AutopilotDiffText -Worktree $root -BaseCommit ((& git -C $root rev-parse HEAD).Trim())
  if (!$diff.Contains('lf-only')) { throw 'diff text did not include the tracked LF-only change' }
  Write-Host 'progress fingerprint self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
