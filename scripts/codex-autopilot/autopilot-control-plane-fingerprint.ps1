$ErrorActionPreference = 'Stop'

function Get-AutopilotControlPlaneFingerprint {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string[]]$Paths)
  $items = foreach ($relative in @($Paths | ForEach-Object { ([string]$_).Replace('\','/').Trim() } | Where-Object { $_ } | Sort-Object -Unique)) {
    $full = Join-Path $RepoRoot $relative
    if (!(Test-Path -LiteralPath $full -PathType Leaf)) { throw "control-plane fingerprint file is missing: $relative" }
    "$relative|$((Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant())"
  }
  if (@($items).Count -eq 0) { throw 'control-plane fingerprint requires at least one stable file' }
  $text = @($items) -join "`n"
  $sha = [Security.Cryptography.SHA256]::Create()
  try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($text)))).Replace('-', '').ToLowerInvariant() }
  finally { $sha.Dispose() }
}

function Test-AutopilotControlPlaneCanaryRequired {
  param(
    [bool]$Enabled,
    [Nullable[int]]$IterationLimit,
    [Parameter(Mandatory)][string]$CurrentFingerprint,
    [string]$LastCanaryFingerprint = ''
  )
  if (!$Enabled) { return $false }
  if ($null -ne $IterationLimit -and [int]$IterationLimit -eq 1) { return $false }
  return !$LastCanaryFingerprint -or $LastCanaryFingerprint -ne $CurrentFingerprint
}
