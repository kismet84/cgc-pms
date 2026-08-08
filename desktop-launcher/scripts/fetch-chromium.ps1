[CmdletBinding()]
param(
  [string]$CacheRoot = (Join-Path $env:LOCALAPPDATA 'CGC-PMS\Desktop\cache')
)

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$lockPath = Join-Path $launcherRoot 'chromium.lock.json'
$lock = Get-Content -Raw -LiteralPath $lockPath | ConvertFrom-Json

function Assert-DedicatedCache([string]$Path) {
  $full = [IO.Path]::GetFullPath($Path)
  $allowed = @(
    [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'CGC-PMS\Desktop\cache')),
    [IO.Path]::GetFullPath((Join-Path $env:TEMP 'CGC-PMS\chromium-cache'))
  )
  $owner = $allowed | Where-Object {
    $full.Equals($_, [StringComparison]::OrdinalIgnoreCase) -or
      $full.StartsWith($_.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)
  } | Select-Object -First 1
  if (!$owner) { throw "Cache must stay under a dedicated CGC-PMS cache root: $full" }
  $cursor = $full
  while ($cursor.Length -ge $owner.Length) {
    if (Test-Path -LiteralPath $cursor) {
      $item = Get-Item -Force -LiteralPath $cursor
      if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Cache path contains a reparse point: $cursor"
      }
    }
    if ($cursor.Equals($owner, [StringComparison]::OrdinalIgnoreCase)) { break }
    $cursor = [IO.Path]::GetDirectoryName($cursor)
  }
  return $full
}

if ($lock.archiveSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'chromium.lock.json archiveSha256 is invalid.' }
if ($lock.licenseSha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'chromium.lock.json licenseSha256 is invalid.' }
$CacheRoot = Assert-DedicatedCache $CacheRoot
$revisionRoot = Join-Path $CacheRoot "chromium-$($lock.revision)"
New-Item -ItemType Directory -Force -Path $revisionRoot | Out-Null
$archive = Join-Path $revisionRoot $lock.archiveFileName
$partial = "$archive.partial"

if (Test-Path -LiteralPath $archive) {
  $current = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash
  if ($current -ne $lock.archiveSha256) { throw "Cached Chromium hash mismatch: $archive" }
} else {
  if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
  curl.exe -L --fail --retry 3 --output $partial $lock.sourceUrl
  if ($LASTEXITCODE -ne 0) { throw "Chromium download failed with exit code $LASTEXITCODE" }
  if ((Get-Item -LiteralPath $partial).Length -ne [long]$lock.archiveSize) {
    Remove-Item -LiteralPath $partial -Force
    throw 'Chromium archive size mismatch.'
  }
  $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $partial).Hash
  if ($actual -ne $lock.archiveSha256) {
    Remove-Item -LiteralPath $partial -Force
    throw 'Chromium archive SHA-256 mismatch.'
  }
  Move-Item -LiteralPath $partial -Destination $archive
}

$license = Join-Path $revisionRoot 'LICENSE'
if (!(Test-Path -LiteralPath $license) -or (Get-FileHash -Algorithm SHA256 -LiteralPath $license).Hash -ne $lock.licenseSha256) {
  $encoded = (Invoke-WebRequest -UseBasicParsing -Uri $lock.licenseUrl).Content.Trim()
  [IO.File]::WriteAllBytes($license, [Convert]::FromBase64String($encoded))
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $license).Hash -ne $lock.licenseSha256) {
  throw 'Chromium LICENSE SHA-256 mismatch.'
}

[pscustomobject]@{ Archive = $archive; License = $license; Revision = $lock.revision }
