$ErrorActionPreference = 'Stop'

function Get-AutopilotExecutionHost {
  param([object]$Config)

  $hostName = if ($null -ne $Config -and $Config.PSObject.Properties.Name -contains 'executionHost') {
    [string]$Config.executionHost
  } else {
    'cli-legacy'
  }
  if ($hostName -notin @('desktop-native', 'cli-legacy')) {
    throw "AUTOPILOT_EXECUTION_HOST_INVALID: $hostName"
  }
  return $hostName
}

function Test-AutopilotDesktopNativeHost {
  param([object]$Config)
  return (Get-AutopilotExecutionHost -Config $Config) -eq 'desktop-native'
}

function Assert-AutopilotLegacyModelProcessAllowed {
  param([string]$ExecutionHost = 'cli-legacy', [string]$Role = 'MODEL')
  if ($ExecutionHost -eq 'desktop-native') {
    throw "DESKTOP_NATIVE_MODEL_PROCESS_FORBIDDEN: role=$Role"
  }
  if ($ExecutionHost -ne 'cli-legacy') {
    throw "AUTOPILOT_EXECUTION_HOST_INVALID: $ExecutionHost"
  }
  return $true
}

function New-AutopilotDesktopHandoff {
  param(
    [string]$RepoRoot,
    [string]$ConfigPath,
    [Nullable[int]]$MaxIterations = $null,
    [bool]$DryRun = $false,
    [bool]$ApplyBacklogSplit = $false,
    [bool]$ExplainNextAction = $false
  )

  return [pscustomobject][ordered]@{
    schemaVersion = 1
    decision = 'DESKTOP_NATIVE_HANDOFF_REQUIRED'
    executionHost = 'desktop-native'
    repoRoot = $RepoRoot
    configPath = $ConfigPath
    maxIterations = $MaxIterations
    dryRun = $DryRun
    applyBacklogSplit = $ApplyBacklogSplit
    explainNextAction = $ExplainNextAction
    nestedModelCliInvocationCount = 0
    checkpointCommand = "pwsh -NoProfile -File scripts/codex-autopilot/autopilot-checkpoint.ps1 -RepoRoot `"$RepoRoot`""
    nextAction = 'Return control to the current Codex desktop main thread and continue from the durable checkpoint.'
  }
}
