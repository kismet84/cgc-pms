param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$config = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $scriptDir 'codex-autopilot.config.json') -Raw | ConvertFrom-Json
. (Join-Path $scriptDir 'autopilot-command.ps1')

$normalizedStdinArgs = @(Get-AutopilotCodexRedirectedStdinArguments -Arguments @('exec','--ephemeral','-','--model','gpt-5.6-sol'))
if (($normalizedStdinArgs -join '|') -ne 'exec|--ephemeral|--model|gpt-5.6-sol') { throw 'redirected stdin arguments must omit the Codex prompt marker even when route arguments are appended later' }
$unchangedArgs = @(Get-AutopilotCodexRedirectedStdinArguments -Arguments @('exec','--help'))
if (($unchangedArgs -join '|') -ne 'exec|--help') { throw 'Codex arguments without a trailing stdin marker must remain unchanged' }

if ($env:OS -eq 'Windows_NT') {
  $codexInvocation = Resolve-AutopilotCodexInvocation
  if ([IO.Path]::GetFileName($codexInvocation.fileName) -notin @('powershell.exe','powershell')) { throw "AutoPilot must use the PowerShell host for the npm Codex shim on Windows, actual=$($codexInvocation.fileName)" }
  if (@($codexInvocation.argumentPrefix) -notcontains '-File' -or [IO.Path]::GetExtension([string]$codexInvocation.argumentPrefix[-1]) -ine '.ps1') { throw 'Codex invocation must include the npm codex.ps1 shim' }
  if ([string]$config.issueExecutor.command -ne 'codex') { throw "issueExecutor.command must stay portable and resolve through the shared launcher, actual=$($config.issueExecutor.command)" }
  $versionStart = [Diagnostics.ProcessStartInfo]::new()
  $versionStart.FileName = $codexInvocation.fileName
  $versionStart.Arguments = (@($codexInvocation.argumentPrefix) + '--version' | ForEach-Object { if ($_ -match '[\s"]') { '"' + $_.Replace('"','\"') + '"' } else { $_ } }) -join ' '
  $versionStart.UseShellExecute = $false
  $versionStart.RedirectStandardOutput = $true
  $versionProcess = [Diagnostics.Process]::new(); $versionProcess.StartInfo = $versionStart; [void]$versionProcess.Start()
  $versionOutput = $versionProcess.StandardOutput.ReadToEnd(); $versionProcess.WaitForExit()
  if ($versionProcess.ExitCode -ne 0 -or $versionOutput -notmatch '^codex-cli ') { throw "resolved Codex invocation is not executable: exit=$($versionProcess.ExitCode), output=$versionOutput" }
}

$autopilotScriptRoots = @(
  $scriptDir
  (Join-Path $repoRoot 'plugins\cgc-pms-autopilot\scripts')
)
$parseFailures = @(
  $autopilotScriptRoots | ForEach-Object { Get-ChildItem -LiteralPath $_ -Filter '*.ps1' -File } | ForEach-Object {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -gt 0) {
      [pscustomobject]@{ file = $_.Name; errors = @($errors | ForEach-Object Message) }
    }
  }
)
if ($parseFailures.Count -gt 0) { throw "AutoPilot scripts must parse through Windows PowerShell -File defaults: $($parseFailures | ConvertTo-Json -Compress -Depth 4)" }

if ($config.controlPlane -ne 'scripts\codex-autopilot\autopilot-run-continuous.ps1') { throw 'single controlPlane is not configured' }
if (!$config.issueGraph -or $config.issueGraph.enabled -ne $true -or $config.issueGraph.allowRegistryFallback -ne $false -or [int]$config.issueGraph.queryLimit -gt 200) { throw 'knowledge-graph-first refill fail-close config is invalid' }
if ([int]$config.maxParallel -ne 1 -or [int]$config.maxParallelIssues -ne 1) { throw 'unattended rollout must start with maxParallel=1' }
if (@($config.issueExecutor.args) -contains 'gpt-5.5') { throw 'executor still pins a legacy model' }
foreach ($profile in 'mechanical','normal','highRisk','formalReview') {
  if ($config.modelPolicy.PSObject.Properties.Name -notcontains $profile) { throw "missing model policy: $profile" }
}

$pluginRunner = Join-Path $repoRoot 'plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1'
$process = Start-Process -FilePath 'powershell' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',$pluginRunner,'-DryRun','-EnableLocalCommit','-AllowSyntheticIssue') -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $env:TEMP 'autopilot-control-plane.out') -RedirectStandardError (Join-Path $env:TEMP 'autopilot-control-plane.err')
$output = ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $env:TEMP 'autopilot-control-plane.out') -Raw -ErrorAction SilentlyContinue) + (Get-Content -Encoding UTF8 -LiteralPath (Join-Path $env:TEMP 'autopilot-control-plane.err') -Raw -ErrorAction SilentlyContinue))
if ($process.ExitCode -eq 0) { throw 'plugin preview runner accepted real execution mode' }
if ($output -notmatch 'preview-only') { throw "plugin runner did not explain the control-plane boundary: $output" }

$readinessScript = Join-Path $scriptDir 'autopilot-readiness-check.ps1'
$readiness = & powershell -NoProfile -ExecutionPolicy Bypass -File $readinessScript -RepoRoot $repoRoot -ConfigPath (Join-Path $scriptDir 'codex-autopilot.config.json') -AllowStopped | ConvertFrom-Json
if (!(($readiness.gates | Where-Object name -eq 'executor.realExecution').status -eq 'pass')) { throw 'bundled Codex CLI was not resolved for unattended execution' }

Write-Host 'control plane self-test passed'
