param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$config = Get-Content -LiteralPath (Join-Path $scriptDir 'codex-autopilot.config.json') -Raw | ConvertFrom-Json

if ($config.controlPlane -ne 'scripts\codex-autopilot\autopilot-run-continuous.ps1') { throw 'single controlPlane is not configured' }
if ([int]$config.maxParallel -ne 1 -or [int]$config.maxParallelIssues -ne 1) { throw 'unattended rollout must start with maxParallel=1' }
if (@($config.issueExecutor.args) -contains 'gpt-5.5') { throw 'executor still pins a legacy model' }
foreach ($profile in 'mechanical','normal','highRisk','formalReview') {
  if ($config.modelPolicy.PSObject.Properties.Name -notcontains $profile) { throw "missing model policy: $profile" }
}

$pluginRunner = Join-Path $repoRoot 'plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1'
$process = Start-Process -FilePath 'powershell' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',$pluginRunner,'-DryRun','-EnableLocalCommit','-AllowSyntheticIssue') -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $env:TEMP 'autopilot-control-plane.out') -RedirectStandardError (Join-Path $env:TEMP 'autopilot-control-plane.err')
$output = ((Get-Content -LiteralPath (Join-Path $env:TEMP 'autopilot-control-plane.out') -Raw -ErrorAction SilentlyContinue) + (Get-Content -LiteralPath (Join-Path $env:TEMP 'autopilot-control-plane.err') -Raw -ErrorAction SilentlyContinue))
if ($process.ExitCode -eq 0) { throw 'plugin preview runner accepted real execution mode' }
if ($output -notmatch 'preview-only') { throw "plugin runner did not explain the control-plane boundary: $output" }

$readinessScript = Join-Path $scriptDir 'autopilot-readiness-check.ps1'
$readiness = & powershell -NoProfile -ExecutionPolicy Bypass -File $readinessScript -RepoRoot $repoRoot -ConfigPath (Join-Path $scriptDir 'codex-autopilot.config.json') -AllowStopped | ConvertFrom-Json
if (!(($readiness.gates | Where-Object name -eq 'executor.realExecution').status -eq 'pass')) { throw 'bundled Codex CLI was not resolved for unattended execution' }

Write-Host 'control plane self-test passed'
