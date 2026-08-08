[CmdletBinding()]
param(
  [string]$ChromiumArchive
)

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$lock = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'chromium.lock.json') | ConvertFrom-Json
$outRoot = Join-Path $launcherRoot 'out'
$launcherVersion = '1.0.1'

function Assert-UnderLauncher([string]$Path) {
  $full = [IO.Path]::GetFullPath($Path)
  $owners = @((Join-Path $launcherRoot 'out'), (Join-Path $launcherRoot 'dist')) | ForEach-Object { [IO.Path]::GetFullPath($_) }
  $owner = $owners | Where-Object {
    $full.Equals($_, [StringComparison]::OrdinalIgnoreCase) -or
      $full.StartsWith($_.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)
  } | Select-Object -First 1
  if (!$owner) { throw "Unsafe path: $full" }
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

function Remove-AbandonedTemporaryDirectories([string]$Root, [string]$Pattern) {
  if (!(Test-Path -LiteralPath $Root -PathType Container)) { return }
  foreach ($item in Get-ChildItem -Force -LiteralPath $Root -Directory -Filter $Pattern) {
    if ($item.Name -notmatch '^\.(?:package-)?(?<pid>[0-9]+)-[a-f0-9]+-staging$' -and
        $item.Name -notmatch '^package-temp-(?<pid>[0-9]+)$') { continue }
    if (Get-Process -Id ([int]$Matches.pid) -ErrorAction SilentlyContinue) { continue }
    Assert-UnderLauncher $item.FullName
    Remove-Item -LiteralPath $item.FullName -Recurse -Force
  }
}

Remove-AbandonedTemporaryDirectories (Join-Path $launcherRoot 'dist') '.package-*-staging'
Remove-AbandonedTemporaryDirectories (Join-Path $launcherRoot 'out') 'package-temp-*'

$packageName = "CGC-PMS-Desktop-$launcherVersion-Chromium-$($lock.major)"
$distRoot = Join-Path $launcherRoot 'dist'
$package = Join-Path $distRoot $packageName
$zip = "$package.zip"
foreach ($path in $package, $zip) { Assert-UnderLauncher $path }
if ((Test-Path -LiteralPath $package) -or (Test-Path -LiteralPath $zip)) {
  throw "Versioned package already exists; preserve it and bump launcherVersion before rebuilding: $packageName"
}

if (!$ChromiumArchive) {
  $fetched = & (Join-Path $PSScriptRoot 'fetch-chromium.ps1')
  $ChromiumArchive = [string]$fetched.Archive
}
$archive = (Resolve-Path -LiteralPath $ChromiumArchive).Path
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash -ne $lock.archiveSha256) {
  throw 'Chromium archive SHA-256 does not match lock.'
}

$launcherExe = [string](& (Join-Path $PSScriptRoot 'build.ps1') -Configuration Release -Architecture x64 | Select-Object -Last 1)
$temp = Join-Path $outRoot "package-temp-$PID"
$transaction = "package-$PID-$([Guid]::NewGuid().ToString('N'))"
$stageRoot = Join-Path $distRoot ".$transaction-staging"
$stagedPackage = Join-Path $stageRoot $packageName
$stagedZip = Join-Path $stageRoot "$packageName.zip"
foreach ($path in $temp, $stageRoot) { Assert-UnderLauncher $path }
if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
if (Test-Path -LiteralPath $stageRoot) { Remove-Item -LiteralPath $stageRoot -Recurse -Force }

try {
  New-Item -ItemType Directory -Force -Path $temp, $stagedPackage | Out-Null
  Expand-Archive -LiteralPath $archive -DestinationPath $temp
  $runtime = Join-Path $temp $lock.archiveRoot
  $chrome = Join-Path $runtime 'chrome.exe'
  if (!(Test-Path -LiteralPath $chrome -PathType Leaf)) { throw 'Chromium archive root does not contain chrome.exe.' }
  $actualVersion = (Get-Item -LiteralPath $chrome).VersionInfo.FileVersion
  if ($actualVersion -ne $lock.version) { throw "Locked Chromium version mismatch: $actualVersion" }

  Copy-Item -LiteralPath $launcherExe -Destination (Join-Path $stagedPackage 'CGC-PMS.exe')
  Copy-Item -LiteralPath $runtime -Destination (Join-Path $stagedPackage 'chromium') -Recurse
  $license = Join-Path (Split-Path -Parent $archive) 'LICENSE'
  if (!(Test-Path -LiteralPath $license -PathType Leaf)) {
    $fetched = & (Join-Path $PSScriptRoot 'fetch-chromium.ps1')
    $license = [string]$fetched.License
  }
  Copy-Item -LiteralPath $license -Destination (Join-Path $stagedPackage 'LICENSE')
  Copy-Item -LiteralPath (Join-Path $launcherRoot 'THIRD_PARTY_NOTICES.md') -Destination $stagedPackage

  $metadata = [ordered]@{
    launcherVersion = $launcherVersion
    chromiumVersion = $lock.version
    chromiumRevision = [string]$lock.revision
    chromiumCommit = $lock.chromiumCommit
    gitSha = (& git -C (Resolve-Path -LiteralPath (Join-Path $launcherRoot '..')).Path rev-parse HEAD).Trim()
    builtAtUtc = [DateTime]::UtcNow.ToString('o')
    archiveSha256 = $lock.archiveSha256.ToLowerInvariant()
    lockSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $launcherRoot 'chromium.lock.json')).Hash.ToLowerInvariant()
  }
  $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stagedPackage 'BUILD-METADATA.json') -Encoding utf8

  $lines = Get-ChildItem -LiteralPath $stagedPackage -File -Recurse | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($stagedPackage.Length + 1).Replace('\', '/')
    "$((Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant())  $relative"
  }
  [IO.File]::WriteAllLines((Join-Path $stagedPackage 'checksums.sha256'), $lines, [Text.UTF8Encoding]::new($false))
  & (Join-Path $PSScriptRoot 'verify-package.ps1') -PackagePath $stagedPackage
  Compress-Archive -Path (Join-Path $stagedPackage '*') -DestinationPath $stagedZip -CompressionLevel Optimal

  $zipVerify = Join-Path $temp 'zip-verify'
  Expand-Archive -LiteralPath $stagedZip -DestinationPath $zipVerify
  & (Join-Path $PSScriptRoot 'verify-package.ps1') -PackagePath $zipVerify

  Move-Item -LiteralPath $stagedPackage -Destination $package
  Move-Item -LiteralPath $stagedZip -Destination $zip
  & (Join-Path $PSScriptRoot 'verify-package.ps1') -PackagePath $package
  [pscustomobject]@{
    Package = $package
    Zip = $zip
    ZipSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $zip).Hash
    ChromiumVersion = $lock.version
  }
} finally {
  if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
  if (Test-Path -LiteralPath $stageRoot) { Remove-Item -LiteralPath $stageRoot -Recurse -Force }
}
