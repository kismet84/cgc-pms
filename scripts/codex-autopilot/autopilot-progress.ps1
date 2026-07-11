$ErrorActionPreference = 'Stop'

function Get-AutopilotProcessTreeSnapshot {
  param([int]$RootPid)
  if ($RootPid -le 0) { return '' }
  $processRows = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Select-Object ProcessId,ParentProcessId)
  $ids = [Collections.Generic.HashSet[int]]::new()
  [void]$ids.Add($RootPid)
  $changed = $true
  while ($changed) {
    $changed = $false
    foreach ($row in $processRows) {
      if ($ids.Contains([int]$row.ParentProcessId) -and $ids.Add([int]$row.ProcessId)) { $changed = $true }
    }
  }
  return (@($ids | Sort-Object | ForEach-Object {
    $process = Get-Process -Id $_ -ErrorAction SilentlyContinue
    if ($process) { '{0}:{1:F3}' -f $process.Id, $process.CPU }
  }) -join '|')
}

function Get-AutopilotWorktreeContentSnapshot {
  param([Parameter(Mandatory)][string]$Worktree)
  $rows = @()
  foreach ($line in @(& git -C $Worktree status --porcelain=v1 --untracked-files=all 2>$null)) {
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
  param([Parameter(Mandatory)][string]$Worktree, [int]$RootPid = 0)
  $source = (Get-AutopilotWorktreeContentSnapshot -Worktree $Worktree) + "`nprocess=" + (Get-AutopilotProcessTreeSnapshot -RootPid $RootPid)
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($source)))).Replace('-', '') } finally { $sha.Dispose() }
}
