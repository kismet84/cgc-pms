param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$config = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $scriptDir 'codex-autopilot.config.json') -Raw | ConvertFrom-Json
$runnerSource = @(
  'autopilot-run-continuous.ps1'
  'autopilot-run-coordinator.ps1'
  'autopilot-run-coordinator-support.ps1'
  'autopilot-issue-lifecycle.ps1'
  'autopilot-executor-supervisor.ps1'
  'autopilot-runtime-context.ps1'
  'autopilot-transition.ps1'
) | ForEach-Object { Get-Content -Encoding UTF8 -LiteralPath (Join-Path $scriptDir $_) -Raw } | Join-String -Separator "`n"
. (Join-Path $scriptDir 'autopilot-command.ps1')
. (Join-Path $scriptDir 'autopilot-task-score.ps1')

if ($runnerSource -notmatch '\[System\.Collections\.Generic\.List\[string\]\]\$evidencePaths\s*=\s*\[System\.Collections\.Generic\.List\[string\]\]::new\(\)') { throw 'verification evidence paths must retain list semantics from the first item' }
if ($runnerSource -match '\$evidencePaths\s*\+=\s*\$evidencePath') { throw 'verification evidence paths must not use scalar string concatenation' }
if ($runnerSource -match 'if\s*\(\$DryRun\s+-or\s+\$batchPlan\.parallel\)') { throw 'switchless dry-run must not bypass applyMode and dispatch an executor' }
if ($runnerSource -notmatch 'if\s*\(\$dryRunMode\s+-or\s+\$batchPlan\.parallel\)') { throw 'executor dispatch must be gated by the derived apply mode' }

$normalizedStdinArgs = @(Get-AutopilotCodexRedirectedStdinArguments -Arguments @('exec','--ephemeral','-','--model','gpt-5.6-sol'))
if (($normalizedStdinArgs -join '|') -ne 'exec|--ephemeral|--model|gpt-5.6-sol') { throw 'redirected stdin arguments must omit the Codex prompt marker even when route arguments are appended later' }
$unchangedArgs = @(Get-AutopilotCodexRedirectedStdinArguments -Arguments @('exec','--help'))
if (($unchangedArgs -join '|') -ne 'exec|--help') { throw 'Codex arguments without a trailing stdin marker must remain unchanged' }

if ($env:OS -eq 'Windows_NT') {
  $codexInvocation = Resolve-AutopilotCodexInvocation
  if ([IO.Path]::GetFileName($codexInvocation.fileName) -notin @('pwsh.exe','pwsh')) { throw "AutoPilot must use the PowerShell 7 host for the npm Codex shim on Windows, actual=$($codexInvocation.fileName)" }
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
if ($parseFailures.Count -gt 0) { throw "AutoPilot scripts must parse through PowerShell 7 -File defaults: $($parseFailures | ConvertTo-Json -Compress -Depth 4)" }

if ($config.controlPlane -ne 'scripts\codex-autopilot\autopilot-run-continuous.ps1') { throw 'single controlPlane is not configured' }
if (!$config.issueGraph -or $config.issueGraph.enabled -ne $true -or $config.issueGraph.allowRegistryFallback -ne $false -or [int]$config.issueGraph.queryLimit -gt 200) { throw 'knowledge-graph-first refill fail-close config is invalid' }
if (!$config.taskScoring -or $config.taskScoring.enabled -ne $true -or $config.taskScoring.activeVersion -ne 'autopilot-task-score/v2' -or $config.taskScoring.approvalStatus -ne 'APPROVED') { throw 'approved task scoring v2 must be active' }
$weights = $config.taskScoring.weights
if ([int]$weights.deliveryCorrectness -ne 35 -or [int]$weights.zeroDanglingIssues -ne 25 -or [int]$weights.firstPassAcceptance -ne 20 -or [int]$weights.taskExecutionEfficiency -ne 10 -or [int]$weights.stockIssueReduction -ne 10) { throw 'approved task scoring v2 weights must remain 35/25/20/10/10' }
if ($config.taskScoring.effectiveFrom -ne 'NEXT_NEW_IMPLEMENTATION_READY' -or !$config.taskScoring.approvalSource) { throw 'task scoring approval evidence or effective boundary is missing' }
if ($null -ne $config.taskScoring.candidateVersion -or $config.taskScoring.candidateEnabled -ne $false -or $null -ne $config.taskScoring.candidateApprovalStatus) { throw 'promoted v2 must no longer remain as a pending candidate' }
if ($config.taskScoring.previousVersion -ne 'autopilot-task-score/v1') { throw 'v1 history boundary is missing after v2 activation' }
if (!$config.controlPlaneCanary -or $config.controlPlaneCanary.enabled -ne $true -or @($config.controlPlaneCanary.fingerprintPaths).Count -lt 5) { throw 'control-plane single-Issue canary gate is not enabled' }
if (@($config.controlPlaneCanary.fingerprintPaths) -notcontains '.gitignore') { throw 'control-plane fingerprint must cover local checkpoint ignore rules' }
& git -C $repoRoot check-ignore -q -- '.codex-autopilot/checkpoints/issue-test.json'
if ($LASTEXITCODE -ne 0) { throw 'durable local checkpoints must not dirty the base worktree' }
if (!$config.retrospective -or $config.retrospective.enabled -ne $true -or [int]$config.retrospective.threshold -ne 20) { throw 'approved retrospective config is invalid' }
if (!(Test-AutopilotRetrospectiveActive -TaskScoringConfig $config.taskScoring -RetrospectiveConfig $config.retrospective)) { throw 'approved scoring and retrospective config did not activate together' }
if ([int]$config.maxParallel -ne 1 -or [int]$config.maxParallelIssues -ne 1) { throw 'unattended rollout must start with maxParallel=1' }
if (@($config.issueExecutor.args) -contains 'gpt-5.5') { throw 'executor still pins a legacy model' }
foreach ($profile in 'mechanical','normal','highRisk','formalReview') {
  if ($config.modelPolicy.PSObject.Properties.Name -notcontains $profile) { throw "missing model policy: $profile" }
}

$pluginRunner = Join-Path $repoRoot 'plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1'
$process = Start-Process -FilePath 'pwsh' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',$pluginRunner,'-DryRun','-EnableLocalCommit','-AllowSyntheticIssue') -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $env:TEMP 'autopilot-control-plane.out') -RedirectStandardError (Join-Path $env:TEMP 'autopilot-control-plane.err')
$output = ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $env:TEMP 'autopilot-control-plane.out') -Raw -ErrorAction SilentlyContinue) + (Get-Content -Encoding UTF8 -LiteralPath (Join-Path $env:TEMP 'autopilot-control-plane.err') -Raw -ErrorAction SilentlyContinue))
if ($process.ExitCode -eq 0) { throw 'plugin preview runner accepted real execution mode' }
if ($output -notmatch 'preview-only') { throw "plugin runner did not explain the control-plane boundary: $output" }

$readinessScript = Join-Path $scriptDir 'autopilot-readiness-check.ps1'
$readiness = & pwsh -NoProfile -ExecutionPolicy Bypass -File $readinessScript -RepoRoot $repoRoot -ConfigPath (Join-Path $scriptDir 'codex-autopilot.config.json') -AllowStopped | ConvertFrom-Json
if (!(($readiness.gates | Where-Object name -eq 'executor.realExecution').status -eq 'pass')) { throw 'bundled Codex CLI was not resolved for unattended execution' }
if (!(($readiness.gates | Where-Object name -eq 'runner.capabilities').status -eq 'pass')) { throw 'modular runner capabilities were not detected by readiness' }
if (!(($readiness.gates | Where-Object name -eq 'selfTest.coverage').status -eq 'pass')) { throw 'modular runner test coverage was not detected by readiness' }

Write-Host 'control plane self-test passed'
