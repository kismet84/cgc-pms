[CmdletBinding()]
param([string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)

$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $RepoRoot 'scripts\mysql-backup.ps1'
$scriptText = Get-Content -LiteralPath $scriptPath -Raw -Encoding UTF8

function Assert-Contains([string[]]$Patterns, [string]$Name) {
  foreach ($pattern in $Patterns) {
    if (!$scriptText.Contains($pattern)) { throw "$Name is missing: $pattern" }
  }
}

Assert-Contains @(
  "-cnotmatch '^[A-Za-z0-9_]+$'",
  '[IO.Path]::GetFullPath',
  '[IO.Path]::GetPathRoot',
  '[IO.FileAttributes]::ReparsePoint',
  '[IO.Directory]::CreateDirectory',
  'Backup path escapes BackupDir',
  '$partialBackupFile = "$backupFile.partial"',
  '[System.IO.FileMode]::CreateNew',
  '[System.IO.Compression.CompressionMode]::Decompress',
  'Move-Item -LiteralPath $partialBackupFile -Destination $backupFile',
  'Remove-Item -LiteralPath $partialBackupFile',
  "'-e', 'MYSQL_PWD'",
  'mysql:8.0@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b',
  'Get-ChildItem -LiteralPath $BackupDir',
  'Remove-Item -LiteralPath $f.FullName'
) 'Windows MySQL atomic backup contract'

if ($scriptText -match '[''"]MYSQL_PWD=\$MysqlPassword' -or $scriptText -match '--password(?:=|\s)') {
  throw 'Windows MySQL backup must not place a secret value in process arguments'
}
if ($scriptText -match '[''"]mysql:8[.]0[''"]') {
  throw 'Windows MySQL backup must not use a mutable image tag'
}

$pwsh = (Get-Process -Id $PID).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("cgc-pms-windows-backup-contract-{0}" -f $PID)
if (Test-Path -LiteralPath $testRoot) { throw "Contract test root already exists: $testRoot" }
New-Item -ItemType Directory -Path $testRoot | Out-Null
$previousPassword = $env:MYSQL_ROOT_PASSWORD
try {
  $env:MYSQL_ROOT_PASSWORD = 'contract-only-password'
  $invalidDatabase = & $pwsh -NoProfile -File $scriptPath -BackupDir $testRoot -MysqlDatabase '..\escape' 2>&1
  if ($LASTEXITCODE -eq 0) { throw 'Unsafe MysqlDatabase was accepted' }
  if (@(Get-ChildItem -LiteralPath $testRoot -Force).Count -ne 0) {
    throw 'Unsafe MysqlDatabase created backup artifacts'
  }

  $filesystemRoot = [IO.Path]::GetPathRoot([IO.Path]::GetFullPath($testRoot))
  $unsafeRoot = & $pwsh -NoProfile -File $scriptPath -BackupDir $filesystemRoot -MysqlDatabase 'cgc_pms' 2>&1
  if ($LASTEXITCODE -eq 0) { throw 'Filesystem-root BackupDir was accepted' }

  $linkPath = Join-Path ([IO.Path]::GetDirectoryName($testRoot)) ("cgc-pms-windows-backup-link-{0}" -f $PID)
  $linkCreated = $false
  try {
    New-Item -ItemType SymbolicLink -Path $linkPath -Target $testRoot -ErrorAction Stop | Out-Null
    $linkCreated = $true
  }
  catch {
    # Windows without Developer Mode cannot create this negative-test fixture.
  }
  if ($linkCreated) {
    try {
    $unsafeLink = & $pwsh -NoProfile -File $scriptPath -BackupDir $linkPath -MysqlDatabase 'cgc_pms' 2>&1
    if ($LASTEXITCODE -eq 0) { throw 'Symbolic-link BackupDir was accepted' }
    }
    finally {
      if (Test-Path -LiteralPath $linkPath) { Remove-Item -LiteralPath $linkPath -Force }
    }
  }
}
finally {
  if ($null -eq $previousPassword) { Remove-Item -LiteralPath Env:MYSQL_ROOT_PASSWORD -ErrorAction SilentlyContinue }
  else { $env:MYSQL_ROOT_PASSWORD = $previousPassword }
  if ($linkPath -and (Test-Path -LiteralPath $linkPath)) { Remove-Item -LiteralPath $linkPath -Force }
  if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}

[pscustomobject]@{
  ok = $true
  immutableMysqlImage = $true
  secretFreeArguments = $true
  unsafeDatabaseRejected = $true
  filesystemRootRejected = $true
  reparsePointRejected = $true
} | ConvertTo-Json
