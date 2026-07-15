param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-native-command.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-native-command-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $fixture = Join-Path $root 'native-fixture.cmd'
  [IO.File]::WriteAllText($fixture, "@echo off`r`necho normal-output`r`necho LF will be replaced by CRLF 1>&2`r`nexit /b %1`r`n", [Text.ASCIIEncoding]::new())

  $warningSuccess = Invoke-AutopilotNativeCommand -FilePath $fixture -Arguments @('0') -AcceptedExitCodes @(0) -ThrowOnFailure
  if (!$warningSuccess.succeeded -or $warningSuccess.exitCode -ne 0 -or $warningSuccess.stderr -notmatch 'CRLF') { throw 'exitCode=0 stderr warning was not preserved as a successful diagnostic' }

  $failure = Invoke-AutopilotNativeCommand -FilePath $fixture -Arguments @('7') -AcceptedExitCodes @(0)
  if ($failure.succeeded -or $failure.exitCode -ne 7) { throw 'non-zero exit code was accepted unexpectedly' }

  $acceptedBusinessCode = Invoke-AutopilotNativeCommand -FilePath $fixture -Arguments @('1') -AcceptedExitCodes @(0,1) -ThrowOnFailure
  if (!$acceptedBusinessCode.succeeded -or $acceptedBusinessCode.exitCode -ne 1) { throw 'explicit business exit code was rejected' }

  $a = Join-Path $root 'a.txt'; $b = Join-Path $root 'b.txt'
  [IO.File]::WriteAllText($a, 'a', [Text.UTF8Encoding]::new($false)); [IO.File]::WriteAllText($b, 'b', [Text.UTF8Encoding]::new($false))
  $diff = Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments @('diff','--no-index','--',$a,$b) -AcceptedExitCodes @(0,1) -ThrowOnFailure
  if ($diff.exitCode -ne 1 -or !$diff.succeeded) { throw 'git diff --no-index difference was not treated as a valid result' }

  $warningRepo = Join-Path $root 'warning-repo'
  New-Item -ItemType Directory -Path $warningRepo -Force | Out-Null
  Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('init','-q') -ThrowOnFailure | Out-Null
  Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('config','--local','core.autocrlf','true') -ThrowOnFailure | Out-Null
  Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('config','--local','core.safecrlf','warn') -ThrowOnFailure | Out-Null
  [IO.File]::WriteAllText((Join-Path $warningRepo 'lf-only.txt'), "line-one`nline-two`n", [Text.UTF8Encoding]::new($false))
  $gitWarning = Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('add','lf-only.txt') -ThrowOnFailure
  if (!$gitWarning.succeeded -or $gitWarning.exitCode -ne 0) { throw 'Git CRLF warning fixture was not classified as success' }
  if ($gitWarning.stderr -notmatch '(?i)LF will be replaced by CRLF|CRLF will be replaced by LF') { throw 'Git CRLF warning fixture did not produce the expected diagnostic' }

  Write-Host 'native command semantics self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
