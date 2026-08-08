[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$base64Path = Join-Path $launcherRoot 'resources\cgc-pms.ico.b64'
$iconPath = Join-Path $launcherRoot 'resources\cgc-pms.ico'
$bytes = [Convert]::FromBase64String((Get-Content -LiteralPath $base64Path -Raw).Trim())
[IO.File]::WriteAllBytes($iconPath, $bytes)
Write-Output $iconPath
