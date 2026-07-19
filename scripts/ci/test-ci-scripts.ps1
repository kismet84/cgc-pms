[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$bashCandidates = @(
  'C:\Program Files\Git\bin\bash.exe',
  (Get-Command bash -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -ErrorAction SilentlyContinue)
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
$bash = $bashCandidates | Select-Object -First 1
if (-not $bash) {
  throw 'Git Bash is required to validate CI shell scripts.'
}

function Invoke-Bash {
  param(
    [Parameter(Mandatory)]
    [string[]]$Arguments,
    [int]$ExpectedExitCode = 0
  )

  & $bash @Arguments
  if ($LASTEXITCODE -ne $ExpectedExitCode) {
    throw "bash exit code $LASTEXITCODE; expected $ExpectedExitCode; arguments: $($Arguments -join ' ')"
  }
}

$shellScripts = Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.sh' -File | Sort-Object Name
if ($shellScripts.Count -eq 0) {
  throw 'No CI shell scripts found.'
}

foreach ($script in $shellScripts) {
  Invoke-Bash -Arguments @('-n', $script.FullName)
}

Push-Location $repoRoot
try {
  Invoke-Bash -Arguments @('./scripts/ci/verify-mysql-grants.sh') -ExpectedExitCode 1

  Invoke-Bash -Arguments @('./scripts/ci/start-e2e-backend.sh') -ExpectedExitCode 1
}
finally {
  Pop-Location
}

[pscustomobject]@{
  ok = $true
  bash = $bash
  syntaxChecked = $shellScripts.Count
  negativeExitContracts = 2
} | ConvertTo-Json
