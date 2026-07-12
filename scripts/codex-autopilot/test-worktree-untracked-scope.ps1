param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-worktree.ps1')
$root = Join-Path ([System.IO.Path]::GetTempPath()) "autopilot-untracked-$([guid]::NewGuid().ToString('N'))"

try {
  New-Item -ItemType Directory -Path $root -Force | Out-Null
  & git -C $root init -q
  & git -C $root config user.email test@example.com
  & git -C $root config user.name test
  'base' | Set-Content (Join-Path $root 'README.md')
  & git -C $root add README.md
  & git -C $root commit -qm base
  $nested = Join-Path $root 'backend\src\test\java\com\cgcpms\common\entity'
  New-Item -ItemType Directory -Path $nested -Force | Out-Null
  'test' | Set-Content (Join-Path $nested 'BaseEntityJsonContractTest.java')

  $changes = @(Get-AutopilotWorktreeChanges -Worktree $root)
  $expected = 'backend/src/test/java/com/cgcpms/common/entity/BaseEntityJsonContractTest.java'
  if ($changes.Count -ne 1 -or $changes[0] -ne $expected) {
    throw "untracked file path was collapsed: $($changes -join ', ')"
  }
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host 'worktree untracked scope self-test passed'
