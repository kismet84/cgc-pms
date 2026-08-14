[CmdletBinding()]
param(
  [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$base64Path = Join-Path $launcherRoot 'resources\cgc-pms.ico.b64'
$iconPath = if ([string]::IsNullOrWhiteSpace($OutputPath)) {
  Join-Path $launcherRoot 'resources\cgc-pms.ico'
} else {
  [IO.Path]::GetFullPath($OutputPath)
}
$launcherPrefix = [IO.Path]::GetFullPath($launcherRoot).TrimEnd('\') + '\'
if (!$iconPath.StartsWith($launcherPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Refusing icon output outside desktop-launcher: $iconPath"
}
$currentPath = [IO.Path]::GetFullPath($launcherRoot)
$relativePath = [IO.Path]::GetRelativePath($currentPath, $iconPath)
foreach ($segment in $relativePath -split '[\\/]') {
  $currentPath = Join-Path $currentPath $segment
  if ((Test-Path -LiteralPath $currentPath) -and
      (((Get-Item -Force -LiteralPath $currentPath).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
    throw "Refusing icon output through reparse point: $currentPath"
  }
}
$bytes = [Convert]::FromBase64String((Get-Content -LiteralPath $base64Path -Raw).Trim())
New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($iconPath)) | Out-Null
[IO.File]::WriteAllBytes($iconPath, $bytes)
Write-Output $iconPath
