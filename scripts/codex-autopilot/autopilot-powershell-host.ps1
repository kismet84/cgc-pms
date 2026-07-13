$ErrorActionPreference = 'Stop'

function Resolve-AutopilotPowerShellHost {
  $command = Get-Command pwsh -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if (!$command) { throw 'AUTOPILOT_POWERSHELL7_REQUIRED: pwsh is not installed or not available on PATH.' }
  $versionText = & $command.Source -NoProfile -Command '$PSVersionTable.PSVersion.ToString()'
  if ($LASTEXITCODE -ne 0) { throw 'AUTOPILOT_POWERSHELL7_REQUIRED: pwsh version probe failed.' }
  [version]$version = ([string]$versionText).Trim()
  if ($version.Major -lt 7) { throw "AUTOPILOT_POWERSHELL7_REQUIRED: pwsh 7+ is required, actual=$version" }
  return [pscustomobject]@{ path=$command.Source; version=$version.ToString(); major=$version.Major }
}

function Assert-AutopilotPowerShell7 {
  if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "AUTOPILOT_POWERSHELL7_REQUIRED: launch this control-plane script with pwsh, actual=$($PSVersionTable.PSVersion)."
  }
  return $true
}
