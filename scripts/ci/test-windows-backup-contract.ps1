[CmdletBinding()]
param([string]$RepoRoot = '')

$ErrorActionPreference = 'Stop'
if (!$RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }

function Format-ContractFailure([object[]]$OutputItems) {
  $detail = ($OutputItems | ForEach-Object { [string]$_ }) -join "`n"
  foreach ($secret in @($env:MYSQL_ROOT_PASSWORD, $env:MYSQL_PWD, $previousPassword, $previousPwd,
                        'contract-only-password', 'preexisting-caller-value')) {
    if ($secret) { $detail = $detail.Replace([string]$secret, '<redacted>') }
  }
  if ($detail.Length -gt 4000) { $detail = $detail.Substring(0, 4000) + ' [truncated]' }
  return $detail
}

function Invoke-ExpectedFailure([string[]]$ScriptArguments, [string]$TargetScriptPath = $scriptPath) {
  # Windows PowerShell 5.1 turns redirected native stderr into error records.
  # Expected validation errors must not abort the negative-test harness.
  $ErrorActionPreference = 'Continue'
  & $pwsh -NoProfile -File $TargetScriptPath @ScriptArguments 2>&1 | Out-Null
  if ($LASTEXITCODE -eq 0) { throw 'Expected script validation failure was accepted' }
}

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
  'ci/build-mysql-runtime.ps1',
  '[string]$MysqlContainer',
  "-cnotmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'",
  "@('exec', '-e', 'MYSQL_PWD', `$MysqlContainer, 'mysqldump', '--protocol=SOCKET', '--host=localhost')",
  'Get-ChildItem -LiteralPath $BackupDir',
  'Remove-Item -LiteralPath $f.FullName'
) 'Windows MySQL atomic backup contract'

if ($scriptText -match '[''"]MYSQL_PWD=\$MysqlPassword' -or $scriptText -match '--password(?:=|\s)') {
  throw 'Windows MySQL backup must not place a secret value in process arguments'
}
if ($scriptText -match '[''"]mysql:[^@''"]+[''"]') {
  throw 'Windows MySQL backup must not use a mutable image tag'
}

$restoreScriptPath = Join-Path $RepoRoot 'scripts\mysql-restore.ps1'
$scriptText = Get-Content -LiteralPath $restoreScriptPath -Raw -Encoding UTF8
Assert-Contains @(
  "-cnotmatch '^[A-Za-z0-9_]+$'",
  'Test-Path -LiteralPath $BackupFile -PathType Leaf',
  'ci/build-mysql-runtime.ps1',
  '[string]$MysqlContainer',
  "-cnotmatch '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'",
  "@('exec', '-e', 'MYSQL_PWD', `$MysqlContainer, 'mysql', '--protocol=SOCKET', '--host=localhost', '-u', 'root')",
  "'-e', 'MYSQL_PWD'",
  '$previousMysqlPwdExists = Test-Path -LiteralPath Env:MYSQL_PWD',
  'if ($previousMysqlPwdExists) { $env:MYSQL_PWD = $previousMysqlPwd }',
  'else { Remove-Item -LiteralPath Env:MYSQL_PWD -ErrorAction SilentlyContinue }',
  'Read-Host "Type ''$TargetDatabase'' to confirm restore"',
  '--default-character-set=utf8mb4',
  'if ($LASTEXITCODE -ne 0)',
  'if ($restoreProcess.ExitCode -ne 0)',
  'Restore health check did not verify a positive table count',
  'if ($restoreFailed) { exit 1 }'
) 'Windows MySQL fail-closed restore contract'
if ($scriptText -match '[''"]MYSQL_PWD=\$MysqlPassword' -or $scriptText -match '--password(?:=|\s)') {
  throw 'Windows MySQL restore must not place a secret value in process arguments'
}
if ($scriptText -match '[''"]mysql:[^@''"]+[''"]' -or $scriptText.Contains('Health check WARNING')) {
  throw 'Windows MySQL restore must use an immutable image and fail closed on health-check failure'
}

$pwsh = (Get-Process -Id $PID).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("cgc-pms-windows-backup-contract-{0}" -f $PID)
if (Test-Path -LiteralPath $testRoot) { throw "Contract test root already exists: $testRoot" }
New-Item -ItemType Directory -Path $testRoot | Out-Null
$previousPassword = $env:MYSQL_ROOT_PASSWORD
$previousPwdExists = Test-Path -LiteralPath Env:MYSQL_PWD
$previousPwd = $env:MYSQL_PWD
$restoreCasesPassed = 0
$backupCasesPassed = 0
try {
  $env:MYSQL_ROOT_PASSWORD = 'contract-only-password'
  Invoke-ExpectedFailure @('-BackupDir', $testRoot, '-MysqlDatabase', '..\escape')
  Invoke-ExpectedFailure @('-BackupDir', $testRoot, '-MysqlContainer', 'container --privileged')
  if (@(Get-ChildItem -LiteralPath $testRoot -Force).Count -ne 0) {
    throw 'Unsafe MysqlDatabase created backup artifacts'
  }

  $filesystemRoot = [IO.Path]::GetPathRoot([IO.Path]::GetFullPath($testRoot))
  Invoke-ExpectedFailure @('-BackupDir', $filesystemRoot, '-MysqlDatabase', 'cgc_pms')

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
    Invoke-ExpectedFailure @('-BackupDir', $linkPath, '-MysqlDatabase', 'cgc_pms')
    }
    finally {
      if (Test-Path -LiteralPath $linkPath) { Remove-Item -LiteralPath $linkPath -Force }
    }
  }

  # Invoke the real restore script with in-process command doubles: no Docker,
  # database, password-bearing command line, or external process is involved.
  $restoreFixture = Join-Path $testRoot 'restore.sql'
  $dumpText = "CREATE TABLE restore_fixture (id INT, payload TEXT, raw_bytes BLOB); INSERT INTO restore_fixture VALUES (1, '$([char]0x4E2D)$([char]0x6587)', X'00FF7F');"
  $dumpBytes = [Text.Encoding]::UTF8.GetBytes($dumpText)
  [IO.File]::WriteAllBytes($restoreFixture, $dumpBytes)
  Invoke-ExpectedFailure @('-BackupFile', $restoreFixture, '-MysqlContainer', '-i') $restoreScriptPath
  function Assert-RestoreClientArguments([object[]]$ClientArguments) {
    if ($ClientArguments -contains 'MYSQL_PWD=contract-only-password' -or
        (($ClientArguments -join ' ').Contains($env:MYSQL_ROOT_PASSWORD))) {
      throw 'Restore exposed its password in client arguments'
    }
    if ($ClientArguments -notcontains 'MYSQL_PWD' -or $env:MYSQL_PWD -ne $env:MYSQL_ROOT_PASSWORD) {
      throw 'Restore did not pass MYSQL_PWD by environment name'
    }
    if ($transport -eq 'container') {
      if ($ClientArguments[0] -ne 'exec' -or $ClientArguments -notcontains 'contract-mysql' -or
          $ClientArguments -notcontains '--protocol=SOCKET' -or $ClientArguments -notcontains '--host=localhost' -or
          $ClientArguments -contains '-h' -or $ClientArguments -contains '-P' -or $ClientArguments -contains 'ignored-host.invalid') {
        throw 'Container transport must use only its own local socket/client'
      }
    }
    elseif ($ClientArguments[0] -ne 'run' -or $ClientArguments -notcontains ('sha256:' + ('a' * 64))) {
      throw 'Host transport must use the immutable built client'
    }
  }
  function docker {
    if ($args[0] -eq 'build') {
      if ($transport -eq 'container') { throw 'Container transport must not build another client' }
      $global:LASTEXITCODE = 0; return
    }
    if ($args[0] -eq 'image' -and $args[1] -eq 'inspect') {
      $global:LASTEXITCODE = 0
      'sha256:' + ('a' * 64)
      return
    }
    Assert-RestoreClientArguments $args
    $isCreate = ($args -join ' ').Contains('CREATE DATABASE')
    $global:LASTEXITCODE = if (($isCreate -and $restoreCase -eq 'create-failure') -or
                             (!$isCreate -and $restoreCase -eq 'health-failure')) { 1 } else { 0 }
    if (!$isCreate -and $global:LASTEXITCODE -eq 0) {
      if ($restoreCase -eq 'empty-target') { '0' } else { '2' }
    }
  }
  function Start-Process {
    param($FilePath, $ArgumentList, [switch]$NoNewWindow, [switch]$Wait, [switch]$PassThru,
      $RedirectStandardInput, $RedirectStandardOutput, $RedirectStandardError)
    if ($FilePath -ne 'docker') { throw 'Unexpected restore process' }
    Assert-RestoreClientArguments $ArgumentList
    if ($ArgumentList -contains 'mysqldump') {
      if ($backupCase -eq 'success') {
        [IO.File]::WriteAllBytes($RedirectStandardOutput, $dumpBytes)
        # Unix dotfiles are hidden to the FileSystem provider. Give Windows the
        # same temporary-file semantics so this contract detects missing -Force.
        if ([IO.Path]::DirectorySeparatorChar -eq '\') {
          [IO.File]::SetAttributes($RedirectStandardOutput, [IO.FileAttributes]::Hidden)
        }
      }
      [pscustomobject]@{ ExitCode = $(if ($backupCase -eq 'dump-failure') { 1 } else { 0 }) }
      return
    }
    if ($ArgumentList -notcontains '-i') { throw 'Restore must pass SQL through standard input' }
    if ([Convert]::ToBase64String([IO.File]::ReadAllBytes($RedirectStandardInput)) -cne [Convert]::ToBase64String($dumpBytes)) {
      throw 'Restore changed SQL input bytes'
    }
    [pscustomobject]@{ ExitCode = $(if ($restoreCase -eq 'restore-failure') { 1 } else { 0 }) }
  }
  foreach ($transport in @('host', 'container')) {
    $transportArgs = if ($transport -eq 'container') {
      @{ MysqlContainer = 'contract-mysql'; MysqlHost = 'ignored-host.invalid'; MysqlPort = 'ignored-port' }
    } else { @{ MysqlHost = '127.0.0.1'; MysqlPort = '3307' } }
  foreach ($restoreCase in @('success', 'create-failure', 'restore-failure', 'health-failure', 'empty-target')) {
    foreach ($preserveExisting in @($true, $false)) {
      if ($preserveExisting) { $env:MYSQL_PWD = 'preexisting-caller-value' }
      else { Remove-Item -LiteralPath Env:MYSQL_PWD -ErrorAction SilentlyContinue }
      $restoreResult = & $restoreScriptPath -BackupFile $restoreFixture -TargetDatabase cgc_pms_restore_test @transportArgs 2>&1 6>$null
      $expectedExit = if ($restoreCase -eq 'success') { 0 } else { 1 }
      if ($LASTEXITCODE -ne $expectedExit) {
        throw "Restore case $transport/$restoreCase returned $LASTEXITCODE, expected $expectedExit. $(Format-ContractFailure $restoreResult)"
      }
      if ($preserveExisting -and $env:MYSQL_PWD -ne 'preexisting-caller-value') {
        throw "Restore case $restoreCase changed the caller's MYSQL_PWD"
      }
      if (!$preserveExisting -and (Test-Path -LiteralPath Env:MYSQL_PWD)) {
        throw "Restore case $restoreCase leaked MYSQL_PWD into the caller"
      }
      $restoreCasesPassed++
    }
  }
  foreach ($backupCase in @('success', 'dump-failure')) {
    foreach ($preserveExisting in @($true, $false)) {
      if ($preserveExisting) { $env:MYSQL_PWD = 'preexisting-caller-value' }
      else { Remove-Item -LiteralPath Env:MYSQL_PWD -ErrorAction SilentlyContinue }
      $backupCaseDir = Join-Path $testRoot "backup-$transport-$backupCase-$preserveExisting"
      $backupResult = & $scriptPath -BackupDir $backupCaseDir -MysqlDatabase contract_probe -SkipRetention @transportArgs 2>&1 6>$null
      $expectedExit = if ($backupCase -eq 'success') { 0 } else { 1 }
      if ($LASTEXITCODE -ne $expectedExit) {
        throw "Backup case $transport/$backupCase returned $LASTEXITCODE, expected $expectedExit. $(Format-ContractFailure $backupResult)"
      }
      if ($preserveExisting -and $env:MYSQL_PWD -ne 'preexisting-caller-value') { throw 'Backup changed caller MYSQL_PWD' }
      if (!$preserveExisting -and (Test-Path -LiteralPath Env:MYSQL_PWD)) { throw 'Backup leaked MYSQL_PWD into the caller' }
      $archives = @(Get-ChildItem -LiteralPath $backupCaseDir -File -Force)
      if ($backupCase -eq 'success') {
        if ($archives.Count -ne 1 -or $archives[0].Extension -ne '.gz') { throw 'Backup did not atomically publish one gzip' }
        $compressed = [IO.File]::OpenRead($archives[0].FullName)
        $decoded = [IO.MemoryStream]::new()
        $gzip = [IO.Compression.GZipStream]::new($compressed, [IO.Compression.CompressionMode]::Decompress)
        try {
          $gzip.CopyTo($decoded)
          if ([Convert]::ToBase64String($decoded.ToArray()) -cne [Convert]::ToBase64String($dumpBytes)) { throw 'Backup changed Unicode/binary SQL bytes' }
        }
        finally { $gzip.Dispose(); $decoded.Dispose(); $compressed.Dispose() }
      }
      elseif ($archives.Count -ne 0) { throw 'Failed backup published an archive or retained partial files' }
      $backupCasesPassed++
    }
  }
  }
}
finally {
  if ($null -eq $previousPassword) { Remove-Item -LiteralPath Env:MYSQL_ROOT_PASSWORD -ErrorAction SilentlyContinue }
  else { $env:MYSQL_ROOT_PASSWORD = $previousPassword }
  if ($previousPwdExists) { $env:MYSQL_PWD = $previousPwd }
  else { Remove-Item -LiteralPath Env:MYSQL_PWD -ErrorAction SilentlyContinue }
  if ($restoreFixture -and (Test-Path -LiteralPath $restoreFixture)) { Remove-Item -LiteralPath $restoreFixture -Force }
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
  restoreFailureAndEnvironmentCases = $restoreCasesPassed
  backupFailureAndEnvironmentCases = $backupCasesPassed
  transports = @('host', 'container-local-socket')
} | ConvertTo-Json
exit 0
