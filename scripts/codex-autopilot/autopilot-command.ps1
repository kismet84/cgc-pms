$ErrorActionPreference = 'Stop'

function Resolve-AutopilotCodexInvocation {
  param([string]$Command = 'codex')

  if ($env:OS -eq 'Windows_NT') {
    $explicitPath = if ((Split-Path -Leaf $Command) -ne $Command -and (Test-Path -LiteralPath $Command)) { (Resolve-Path -LiteralPath $Command).Path } else { $null }
    $shim = if ($explicitPath -and [IO.Path]::GetExtension($explicitPath) -ieq '.ps1') {
      $explicitPath
    } else {
      $resolved = Get-Command "$Command.ps1" -CommandType ExternalScript -ErrorAction SilentlyContinue | Select-Object -First 1
      if (!$resolved -and $Command -eq 'codex') { $resolved = Get-Command codex.ps1 -CommandType ExternalScript -ErrorAction SilentlyContinue | Select-Object -First 1 }
      if ($resolved) { $resolved.Source } else { $null }
    }
    if ($shim) {
      $hostCommand = Get-Command powershell.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1
      return [pscustomobject]@{
        fileName = $hostCommand.Source
        argumentPrefix = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$shim)
      }
    }
    throw 'Codex PowerShell shim is unavailable on Windows'
  }

  $native = Get-Command $Command -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($native) { return [pscustomobject]@{ fileName = $native.Source; argumentPrefix = @() } }
  throw 'Codex CLI command is unavailable'
}
