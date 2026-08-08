[CmdletBinding()]
param(
  [ValidateSet('Release')]
  [string]$Configuration = 'Release',
  [ValidateSet('x64')]
  [string]$Architecture = 'x64',
  [switch]$Contract
)

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $launcherRoot '..')).Path
$outRoot = Join-Path $launcherRoot 'out'

function Import-VsDevEnvironment {
  $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
  if (!(Test-Path -LiteralPath $vswhere -PathType Leaf)) {
    throw 'Visual Studio Build Tools 2022 is required (vswhere.exe missing).'
  }
  $installation = (& $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath).Trim()
  if (!$installation) { throw 'MSVC x64 workload is not installed.' }
  $devCmd = Join-Path $installation 'Common7\Tools\VsDevCmd.bat'
  $lines = & cmd.exe /d /s /c "`"$devCmd`" -arch=x64 -host_arch=x64 >nul && set"
  if ($LASTEXITCODE -ne 0) { throw 'VsDevCmd failed.' }
  foreach ($line in $lines) {
    $separator = $line.IndexOf('=')
    if ($separator -le 0) { continue }
    [Environment]::SetEnvironmentVariable($line.Substring(0, $separator), $line.Substring($separator + 1), 'Process')
  }
  foreach ($tool in 'cl.exe', 'link.exe', 'rc.exe') {
    if (!(Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool is unavailable after VsDevCmd." }
  }
}

function Assert-UnderLauncher([string]$Path) {
  $full = [IO.Path]::GetFullPath($Path)
  $owner = [IO.Path]::GetFullPath((Join-Path $launcherRoot 'out'))
  if (!$full.Equals($owner, [StringComparison]::OrdinalIgnoreCase) -and
      !$full.StartsWith($owner.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing path outside desktop-launcher: $full"
  }
  $cursor = $full
  while ($cursor.Length -ge $owner.Length) {
    if (Test-Path -LiteralPath $cursor) {
      $item = Get-Item -Force -LiteralPath $cursor
      if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point is not allowed: $cursor" }
    }
    if ($cursor.Equals($owner, [StringComparison]::OrdinalIgnoreCase)) { break }
    $cursor = [IO.Path]::GetDirectoryName($cursor)
  }
}

function Invoke-Native([string]$File, [string[]]$Arguments) {
  & $File @Arguments
  if ($LASTEXITCODE -ne 0) { throw "$File failed with exit code $LASTEXITCODE" }
}

Import-VsDevEnvironment
New-Item -ItemType Directory -Force -Path $outRoot | Out-Null
& (Join-Path $PSScriptRoot 'generate-icon.ps1') | Out-Null

$resourceRoot = Join-Path $launcherRoot 'resources'
$source = Join-Path $launcherRoot 'src\main.cpp'
$common = @('/nologo', '/EHsc', '/std:c++17', '/W4', '/WX', '/permissive-', '/utf-8', '/MT', '/O2')

if ($Contract) {
  $contractRoot = Join-Path $outRoot 'contract'
  Assert-UnderLauncher $contractRoot
  if (Test-Path -LiteralPath $contractRoot) { Remove-Item -LiteralPath $contractRoot -Recurse -Force }
  $packageRoot = Join-Path $contractRoot '中文 空格'
  $chromiumRoot = Join-Path $packageRoot 'chromium'
  New-Item -ItemType Directory -Force -Path $chromiumRoot | Out-Null

  $launcherRes = Join-Path $contractRoot 'launcher.res'
  Push-Location $resourceRoot
  try { Invoke-Native 'rc.exe' @('/nologo', "/fo$launcherRes", 'launcher.rc') } finally { Pop-Location }
  $launcherExe = Join-Path $packageRoot 'CGC-PMS.exe'
  Invoke-Native 'cl.exe' ($common + @(
      '/DCGCPMS_CONTRACT_TEST', '/DCGCPMS_HEALTH_PORT=55173', '/DCGCPMS_HEALTH_ATTEMPTS=2',
      '/DCGCPMS_HEALTH_DELAY_MS=100', $source, $launcherRes,
      "/Fo$(Join-Path $contractRoot 'launcher.obj')", "/Fe$launcherExe", '/link', '/SUBSYSTEM:CONSOLE',
      'winhttp.lib', 'shell32.lib', 'version.lib'))

  $fakeRes = Join-Path $contractRoot 'fake-chromium.res'
  $testsRoot = Join-Path $launcherRoot 'tests'
  Push-Location $testsRoot
  try { Invoke-Native 'rc.exe' @('/nologo', "/fo$fakeRes", 'fake-chromium.rc') } finally { Pop-Location }
  Invoke-Native 'cl.exe' ($common + @(
      (Join-Path $testsRoot 'fake-chromium.cpp'), $fakeRes,
      "/Fo$(Join-Path $contractRoot 'fake-chromium.obj')", "/Fe$(Join-Path $chromiumRoot 'chrome.exe')",
      '/link', '/SUBSYSTEM:CONSOLE'))
  Invoke-Native 'cl.exe' ($common + @(
      (Join-Path $testsRoot 'process-harness.cpp'),
      "/Fo$(Join-Path $contractRoot 'process-harness.obj')", "/Fe$(Join-Path $contractRoot 'process-harness.exe')",
      '/link', '/SUBSYSTEM:CONSOLE'))
  Write-Output $packageRoot
  exit 0
}

$releaseRoot = Join-Path $outRoot $Configuration
Assert-UnderLauncher $releaseRoot
if (Test-Path -LiteralPath $releaseRoot) { Remove-Item -LiteralPath $releaseRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path $releaseRoot | Out-Null
$resource = Join-Path $releaseRoot 'launcher.res'
Push-Location $resourceRoot
try { Invoke-Native 'rc.exe' @('/nologo', "/fo$resource", 'launcher.rc') } finally { Pop-Location }
$exe = Join-Path $releaseRoot 'CGC-PMS.exe'
Invoke-Native 'cl.exe' ($common + @(
    $source, $resource, "/Fo$(Join-Path $releaseRoot 'launcher.obj')", "/Fe$exe", '/link',
    '/SUBSYSTEM:WINDOWS', 'winhttp.lib', 'shell32.lib', 'version.lib'))
Write-Output $exe
