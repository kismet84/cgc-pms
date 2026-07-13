$ErrorActionPreference = 'Stop'

$nativeCommandLibrary = Join-Path $PSScriptRoot 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue)) { . $nativeCommandLibrary }

function Get-AutopilotWorktreeContentSnapshot {
  param([Parameter(Mandatory)][string]$Worktree)
  $rows = @()
  $status = Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('status','--porcelain=v1','--untracked-files=all') -ThrowOnFailure
  foreach ($line in @(Get-AutopilotNativeOutputLines $status.stdout)) {
    if (!$line -or $line.Length -lt 4) { continue }
    $raw = $line.Substring(3).Trim().Trim('"')
    if ($raw -match '\s+->\s+') { $raw = ($raw -split '\s+->\s+')[-1].Trim('"') }
    $path = Join-Path $Worktree ($raw.Replace('/','\'))
    if (Test-Path -LiteralPath $path -PathType Leaf) {
      $item = Get-Item -LiteralPath $path
      $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
      $rows += "$line|$($item.Length)|$($item.LastWriteTimeUtc.Ticks)|$hash"
    } else {
      $rows += "$line|missing"
    }
  }
  return ($rows | Sort-Object) -join "`n"
}

function Get-AutopilotProgressFingerprint {
  param(
    [Parameter(Mandatory)][string]$Worktree,
    [int]$RootPid = 0,
    [string[]]$SemanticEvidencePaths = @()
  )
  $rows = @('worktree:' + (Get-AutopilotWorktreeContentSnapshot -Worktree $Worktree))
  foreach ($path in @($SemanticEvidencePaths | Where-Object { $_ } | Sort-Object -Unique)) {
    if (Test-Path -LiteralPath $path -PathType Leaf) {
      $item = Get-Item -LiteralPath $path
      $rows += "evidence:$path|$($item.Length)|$((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash)"
    } else {
      $rows += "evidence:$path|missing"
    }
  }
  $source = $rows -join "`n"
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($source)))).Replace('-', '') } finally { $sha.Dispose() }
}

function Get-AutopilotActiveLongCommand {
  param([int]$RootPid, [object[]]$Declarations, [datetimeoffset]$StartedAt)
  if ($RootPid -le 0 -or @($Declarations).Count -eq 0) { return $null }
  $rows = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)
  $descendants = [Collections.Generic.HashSet[int]]::new()
  [void]$descendants.Add($RootPid)
  $changed = $true
  while ($changed) {
    $changed = $false
    foreach ($row in $rows) {
      if ($descendants.Contains([int]$row.ParentProcessId) -and $descendants.Add([int]$row.ProcessId)) { $changed = $true }
    }
  }
  foreach ($declaration in @($Declarations)) {
    $expectedSeconds = [int]$declaration.expectedSeconds
    if ($expectedSeconds -le 600) { continue }
    $command = [string]$declaration.command
    $match = $rows | Where-Object { $_.ProcessId -ne $RootPid -and $descendants.Contains([int]$_.ProcessId) -and $_.CommandLine -and $_.CommandLine.IndexOf($command, [StringComparison]::OrdinalIgnoreCase) -ge 0 } | Select-Object -First 1
    if ($match -and ([datetimeoffset]::Now - [datetimeoffset]$match.CreationDate).TotalSeconds -lt $expectedSeconds) {
      return [pscustomobject]@{ command = $command; expectedSeconds = $expectedSeconds; processId = [int]$match.ProcessId }
    }
  }
  return $null
}
