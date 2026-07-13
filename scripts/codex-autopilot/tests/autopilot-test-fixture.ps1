function New-AutopilotTestRepository {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$Name)

  $root = Join-Path ([IO.Path]::GetTempPath()) ("cgc-pms-autopilot-$Name-" + [guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Path $root -Force | Out-Null
  & git -C $root init -q
  if ($LASTEXITCODE -ne 0) { throw "test repository init failed: $Name" }
  & git -C $root config --local user.email 'autopilot@test.local'
  & git -C $root config --local user.name 'AutoPilot Test'
  & git -C $root config --local core.autocrlf false
  & git -C $root config --local core.safecrlf false
  [IO.File]::WriteAllText((Join-Path $root '.gitattributes'), "* text=auto eol=lf`n*.cmd text eol=crlf`n", [Text.UTF8Encoding]::new($false))
  return $root
}

function Remove-AutopilotTestRepository {
  param([Parameter(Mandatory)][string]$Path)
  if ($Path -and (Test-Path -LiteralPath $Path)) { Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue }
}

function Invoke-AutopilotThemeTest {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string[]]$Scripts)

  $root = Split-Path -Parent $PSScriptRoot
  foreach ($scriptName in $Scripts) {
    & pwsh -NoProfile -File (Join-Path $root $scriptName)
    if ($LASTEXITCODE -ne 0) { throw "AutoPilot theme dependency failed: $scriptName (exit=$LASTEXITCODE)" }
  }
}
