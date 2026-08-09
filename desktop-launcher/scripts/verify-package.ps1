[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [string]$PackagePath,
  [switch]$RuntimeSource
)

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$package = (Resolve-Path -LiteralPath $PackagePath).Path
$lock = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'chromium.lock.json') | ConvertFrom-Json

foreach ($relative in 'CGC-PMS.exe', 'chromium\chrome.exe', 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'BUILD-METADATA.json', 'checksums.sha256') {
  if (!(Test-Path -LiteralPath (Join-Path $package $relative) -PathType Leaf)) { throw "Package file missing: $relative" }
}

if (!$RuntimeSource) {
  $metadata = Get-Content -Raw -LiteralPath (Join-Path $package 'BUILD-METADATA.json') | ConvertFrom-Json
  if ([string]$metadata.launcherVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Launcher version metadata is invalid.' }
  $expectedLauncherVersion = "$($metadata.launcherVersion).0"
  $launcher = Get-Item -LiteralPath (Join-Path $package 'CGC-PMS.exe')
  if ($launcher.VersionInfo.FileVersion -ne $expectedLauncherVersion -or
      $launcher.VersionInfo.ProductVersion -ne $expectedLauncherVersion) {
    throw "Launcher version mismatch: expected $expectedLauncherVersion, got $($launcher.VersionInfo.FileVersion)/$($launcher.VersionInfo.ProductVersion)"
  }
}

$chrome = Get-Item -LiteralPath (Join-Path $package 'chromium\chrome.exe')
$actualVersion = $chrome.VersionInfo.FileVersion
if ($actualVersion -ne $lock.version) { throw "Chromium version mismatch: expected $($lock.version), got $actualVersion" }
$licenseHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $package 'LICENSE')).Hash
if ($licenseHash -ne $lock.licenseSha256) { throw 'Packaged Chromium LICENSE hash mismatch.' }

$forbidden = Get-ChildItem -LiteralPath $package -Recurse -Force | Where-Object {
  $_.Attributes -band [IO.FileAttributes]::ReparsePoint -or
  $_.Name -match '\.(pdb|log|tmp)$' -or
  $_.FullName -match '(?i)[\\/](profile|profiles|user data|logs|runtime)([\\/]|$)'
}
if ($forbidden) { throw "Forbidden package content: $($forbidden[0].FullName)" }

$checksumPath = Join-Path $package 'checksums.sha256'
foreach ($line in Get-Content -LiteralPath $checksumPath) {
  if ($line -notmatch '^([a-f0-9]{64})  (.+)$') { throw "Invalid checksum line: $line" }
  $relative = $Matches[2].Replace('/', '\')
  if ($relative -eq 'checksums.sha256') { throw 'checksums.sha256 must not hash itself.' }
  $target = Join-Path $package $relative
  if (!(Test-Path -LiteralPath $target -PathType Leaf)) { throw "Checksummed file missing: $relative" }
  $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $target).Hash.ToLowerInvariant()
  if ($actual -ne $Matches[1]) { throw "Checksum mismatch: $relative" }
}

$expected = Get-ChildItem -LiteralPath $package -File -Recurse | Where-Object Name -ne 'checksums.sha256' | ForEach-Object {
  $_.FullName.Substring($package.Length + 1).Replace('\', '/')
}
$listed = Get-Content -LiteralPath $checksumPath | ForEach-Object { ($_ -split '  ', 2)[1] }
if (@(Compare-Object $expected $listed).Count -ne 0) { throw 'Checksum manifest does not cover package exactly.' }

Write-Output "package verification: PASS ($actualVersion)"
