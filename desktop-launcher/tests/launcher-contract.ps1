[CmdletBinding()]
param(
  [switch]$Worker
)

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$buildScript = Join-Path $launcherRoot 'scripts\build.ps1'

if (!$Worker) {
  $jobs = @(1..2 | ForEach-Object {
    Start-Job -ScriptBlock { param($scriptPath) & $scriptPath -Worker } -ArgumentList $PSCommandPath
  })
  try {
    $jobs | Wait-Job -Timeout 300 | Out-Null
    $failures = [Collections.Generic.List[string]]::new()
    foreach ($job in $jobs) {
      $output = @(Receive-Job -Job $job -Keep -ErrorAction SilentlyContinue 2>&1 | ForEach-Object { [string]$_ })
      if ($job.State -ne 'Completed' -or $output -notcontains 'launcher contract worker: PASS') {
        $reason = if ($job.JobStateInfo.Reason) { $job.JobStateInfo.Reason.Message } else { 'missing PASS marker' }
        $failures.Add("worker=$($job.Id) state=$($job.State) reason=$reason output=$($output -join ' | ')")
      }
    }
    if ($failures.Count -gt 0) { throw "Concurrent launcher contract failed:`n$($failures -join "`n")" }
    Write-Output 'launcher contract: PASS (2 concurrent workers)'
  } finally {
    $jobs | Where-Object State -eq 'Running' | Stop-Job -ErrorAction SilentlyContinue
    $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
  }
  return
}

$runId = [Guid]::NewGuid().ToString('N')
$contractOwner = Join-Path $launcherRoot 'out\contract'
$testRoot = Join-Path $contractOwner $runId
$contractRoot = Join-Path $testRoot 'build'
$fixtureRoot = Join-Path $testRoot 'fixture'
$tempRoot = Join-Path $testRoot 'temp'
$localAppDataRoot = Join-Path $testRoot 'local-app-data'
$dataRoot = Join-Path $localAppDataRoot 'CGC-PMS\Desktop'
$evidenceRoot = Join-Path $dataRoot 'contract-evidence'
$stateRoot = Join-Path $dataRoot 'contract-runtime'
$profileRoot = Join-Path $dataRoot 'contract-profiles'
$logRoot = Join-Path $dataRoot 'contract-logs'
$modePath = Join-Path $fixtureRoot 'health-mode.txt'
$readyPath = Join-Path $fixtureRoot 'health-ready.txt'
$serverScript = Join-Path $PSScriptRoot 'health-server.mjs'
$processHarness = Join-Path $contractRoot 'process-harness.exe'
$harnessPidPath = Join-Path $contractRoot 'harness-child-pid.txt'
$originalLocalAppData = $env:LOCALAPPDATA
$originalTemp = $env:TEMP
$originalTmp = $env:TMP
$originalRunId = $env:CGCPMS_CONTRACT_RUN_ID
$originalDataRoot = $env:CGCPMS_CONTRACT_DATA_ROOT
$server = $null

function Assert-Equal($Expected, $Actual, [string]$Message) {
  if ($Expected -ne $Actual) {
    $logPath = Join-Path $logRoot 'launcher.log'
    $diagnostics = if (Test-Path -LiteralPath $logPath) {
      (Get-Content -LiteralPath $logPath -Tail 30 | Out-String).Trim()
    } else {
      'launcher log unavailable'
    }
    throw "$Message expected=$Expected actual=$Actual`n$diagnostics"
  }
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (!$Condition) { throw $Message }
}

function Wait-For([scriptblock]$Condition, [string]$Message, [int]$TimeoutSeconds = 10) {
  $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
  do {
    if (& $Condition) { return }
    Start-Sleep -Milliseconds 100
  } while ([DateTime]::UtcNow -lt $deadline)
  throw $Message
}

function Remove-TestRoot {
  $full = [IO.Path]::GetFullPath($testRoot)
  $expected = [IO.Path]::GetFullPath((Join-Path $contractOwner $runId))
  $outOwner = [IO.Path]::GetFullPath((Join-Path $launcherRoot 'out'))
  if (!$full.Equals($expected, [StringComparison]::OrdinalIgnoreCase) -or
      !$full.StartsWith($outOwner.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe contract cleanup root: $full"
  }
  foreach ($ownerPath in $outOwner, $contractOwner) {
    if ((Test-Path -LiteralPath $ownerPath) -and
        (((Get-Item -Force -LiteralPath $ownerPath).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
      throw "Refusing cleanup through reparse point: $ownerPath"
    }
  }
  if (!(Test-Path -LiteralPath $full)) { return }
  $cleanupScript = @'
const fs = require('node:fs')
const path = require('node:path')
const root = process.argv[1]
function scan(current) {
  const stat = fs.lstatSync(current)
  if (stat.isSymbolicLink()) throw new Error(`Refusing cleanup through reparse point: ${current}`)
  if (stat.isDirectory()) for (const child of fs.readdirSync(current)) scan(path.join(current, child))
}
scan(root)
fs.rmSync(root, { recursive: true, force: true })
'@
  & (Get-Command node.exe).Source -e $cleanupScript $full
  if ($LASTEXITCODE -ne 0 -or (Test-Path -LiteralPath $full)) { throw "Contract cleanup failed: $full" }
}

$sourceText = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'src\main.cpp')
Assert-True ($sourceText.Contains('WinHttpCloseHandle(value)')) 'WinHTTP handles must use WinHttpCloseHandle.'
Assert-True ($sourceText.Contains('availableWidth < width || availableHeight < height')) 'Small work areas must use the maximize fallback.'
Assert-True ($sourceText.Contains('ShowWindow(window, SW_MAXIMIZE)')) 'Small work area fallback must maximize the app window.'
$packageText = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'scripts\package.ps1')
$stageVerify = $packageText.IndexOf("verify-package.ps1') -PackagePath `$stagedPackage", [StringComparison]::Ordinal)
$publishMove = $packageText.IndexOf('Move-Item -LiteralPath $stagedPackage -Destination $package', [StringComparison]::Ordinal)
Assert-True ($stageVerify -ge 0 -and $publishMove -gt $stageVerify) 'Package must verify staging before publishing a new version path.'
Assert-True ($packageText.Contains("verify-package.ps1') -PackagePath `$zipVerify")) 'ZIP extraction must be verified before publication.'
Assert-True ($packageText.Contains('Versioned package already exists')) 'Existing versioned packages must fail closed instead of being replaced.'
Assert-True (!$packageText.Contains('Remove-Item -LiteralPath $package')) 'Publisher must never delete a canonical versioned package.'
$fetchText = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'scripts\fetch-chromium.ps1')
Assert-True ($fetchText.Contains("Join-Path `$env:LOCALAPPDATA 'CGC-PMS\Desktop\cache'")) 'Default Chromium cache must stay outside repository.'

function Start-LauncherProcess([string[]]$Arguments = @()) {
  $start = [Diagnostics.ProcessStartInfo]::new()
  $start.FileName = $processHarness
  $start.UseShellExecute = $false
  $start.CreateNoWindow = $true
  [void]$start.ArgumentList.Add($launcher)
  foreach ($argument in $Arguments) { [void]$start.ArgumentList.Add($argument) }
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $start
  if (!$process.Start()) { throw 'Launcher process did not start.' }
  return $process
}

function Invoke-Launcher([string[]]$Arguments = @()) {
  $process = Start-LauncherProcess $Arguments
  $process.WaitForExit()
  return $process.ExitCode
}

New-Item -ItemType Directory -Force -Path $fixtureRoot, $dataRoot, $tempRoot | Out-Null
$env:LOCALAPPDATA = $localAppDataRoot
$env:TEMP = $tempRoot
$env:TMP = $tempRoot
$env:CGCPMS_CONTRACT_RUN_ID = $runId
$env:CGCPMS_CONTRACT_DATA_ROOT = $dataRoot
try {
  $junctionTarget = Join-Path $testRoot 'junction-target'
  $junctionPath = Join-Path $testRoot 'icon-junction'
  New-Item -ItemType Directory -Force -Path $junctionTarget | Out-Null
  New-Item -ItemType Junction -Path $junctionPath -Target $junctionTarget | Out-Null
  try {
    $junctionRejected = $false
    try {
      & (Join-Path $launcherRoot 'scripts\generate-icon.ps1') -OutputPath (Join-Path $junctionPath 'escape.ico') | Out-Null
    } catch {
      $junctionRejected = $_.Exception.Message -match 'reparse point'
    }
    Assert-True $junctionRejected 'Icon generation must reject a junction in the output path.'
  } finally {
    if (Test-Path -LiteralPath $junctionPath) { Remove-Item -LiteralPath $junctionPath -Force }
  }

  Set-Content -LiteralPath $modePath -Value 'up' -NoNewline -Encoding utf8
  $server = Start-Process -FilePath (Get-Command node.exe).Source `
    -ArgumentList @($serverScript, $modePath, $readyPath, '0') -PassThru -WindowStyle Hidden
  Wait-For { (Test-Path -LiteralPath $readyPath) -or $server.HasExited } 'Health fixture did not start.' 30
  if ($server.HasExited) { throw "Health fixture exited before readiness with code $($server.ExitCode)." }
  $readyText = Get-Content -Raw -LiteralPath $readyPath
  if ($readyText.StartsWith('ERROR:', [StringComparison]::Ordinal)) { throw "Health fixture failed: $readyText" }
  $healthPort = 0
  if (![int]::TryParse($readyText, [ref]$healthPort) -or $healthPort -lt 1024 -or $healthPort -gt 65535) {
    throw "Health fixture returned invalid bound port: $readyText"
  }

  $buildOutput = & $buildScript -Configuration Release -Architecture x64 -Contract -HealthPort $healthPort -ContractRunId $runId
  if ($LASTEXITCODE -ne 0) { throw 'Contract build failed.' }
  $packageRoot = [string]($buildOutput | Select-Object -Last 1)
  $launcher = Join-Path $packageRoot 'CGC-PMS.exe'
  $chromium = Join-Path $packageRoot 'chromium\chrome.exe'

  $longRoot = Join-Path $testRoot 'long-path'
  foreach ($segment in @((('长路径' * 18) -join ''), (('空格 path ' * 8) -join ''), ('nested-' + (('x' * 72) -join '')), ('deep-' + (('y' * 72) -join '')))) {
    $longRoot = Join-Path $longRoot $segment
  }
  New-Item -ItemType Directory -Force -Path $longRoot | Out-Null
  Copy-Item -Path (Join-Path $packageRoot '*') -Destination $longRoot -Recurse
  $packageRoot = $longRoot
  $launcher = Join-Path $packageRoot 'CGC-PMS.exe'
  $chromium = Join-Path $packageRoot 'chromium\chrome.exe'
  Assert-True ($packageRoot.Length -gt 260) 'Contract package path must exercise Windows long-path handling.'

  Assert-Equal 10 (Invoke-Launcher @('--app=https://example.invalid')) 'Unknown arguments must fail.'

  Assert-Equal 0 (Invoke-Launcher) 'Healthy launch must succeed.'
  $argsPath = Join-Path $evidenceRoot 'fake-argv.txt'
  Assert-True (Test-Path -LiteralPath $argsPath) 'Fake browser argv evidence missing.'
  $argsText = Get-Content -Raw -LiteralPath $argsPath
  Assert-True ($argsText.Contains('--app=http://127.0.0.1:5173/?desktop=1')) 'Desktop application URL is not fixed.'
  Assert-True ($argsText.Contains('--user-data-dir=')) 'Dedicated profile argument missing.'
  Assert-True (!$argsText.Contains('--force-app-mode')) 'Forced app mode is incompatible with URL app windows.'
  Assert-True ($argsText.Contains('--window-size=1440,1080')) 'Fixed Chromium window size argument missing.'
  $windowPath = Join-Path $evidenceRoot 'fake-window.txt'
  Assert-True (Test-Path -LiteralPath $windowPath) 'Fake browser window evidence missing.'
  $windowEvidence = @{}
  Get-Content -LiteralPath $windowPath | ForEach-Object {
    $parts = $_ -split '=', 2
    if ($parts.Count -eq 2) { $windowEvidence[$parts[0]] = [long]$parts[1] }
  }
  Assert-Equal 1 $windowEvidence.configured 'Launcher must configure browser frame.'
  Assert-True (($windowEvidence.style -band 0x00040000) -eq 0) 'Resizable WS_THICKFRAME must be removed.'
  Assert-True (($windowEvidence.style -band 0x00010000) -ne 0) 'WS_MAXIMIZEBOX must remain enabled.'
  Assert-True (($windowEvidence.style -band 0x00020000) -ne 0) 'WS_MINIMIZEBOX must remain enabled.'
  Assert-Equal 1 $windowEvidence.statusBubbleUnchanged 'Chromium status bubble must never be configured as the browser root.'
  Assert-Equal 1 $windowEvidence.maximized 'Window must support maximize.'
  if ($windowEvidence.smallWorkArea -eq 1) {
    Assert-Equal 1 $windowEvidence.initialMaximized 'Small work areas must launch maximized.'
    Assert-True ($windowEvidence.width -gt 0 -and $windowEvidence.height -gt 0) 'Small-work-area window evidence must remain valid.'
  } else {
    Assert-Equal 0 $windowEvidence.initialMaximized 'Sufficient work areas must launch in the normal state.'
    Assert-True ([Math]::Abs($windowEvidence.width - 1440) -le 2) "Normal window width must be 1440 logical pixels; actual=$($windowEvidence.width)."
    Assert-True ([Math]::Abs($windowEvidence.height - 1080) -le 2) "Normal window height must be 1080 logical pixels; actual=$($windowEvidence.height)."
    Assert-True ([Math]::Abs($windowEvidence.restoredWidth - 1440) -le 2) "Restore width must return to 1440 logical pixels; actual=$($windowEvidence.restoredWidth)."
    Assert-True ([Math]::Abs($windowEvidence.restoredHeight - 1080) -le 2) "Restore height must return to 1080 logical pixels; actual=$($windowEvidence.restoredHeight)."
  }
  foreach ($forbidden in '--disable-web-security', '--ignore-certificate-errors', '--no-sandbox', '--remote-debugging-port') {
    Assert-True (!$argsText.Contains($forbidden)) "Forbidden Chromium flag present: $forbidden"
  }

  $env:CGCPMS_APP_URL = 'https://example.invalid/'
  try { Assert-Equal 0 (Invoke-Launcher) 'Environment must not override application URL.' } finally { Remove-Item Env:CGCPMS_APP_URL -ErrorAction SilentlyContinue }
  $argsText = Get-Content -Raw -LiteralPath $argsPath
  Assert-True (!$argsText.Contains('example.invalid')) 'Environment URL leaked into browser argv.'

  Set-Content -LiteralPath $modePath -Value 'bad' -NoNewline -Encoding utf8
  Assert-Equal 31 (Invoke-Launcher) 'DOWN health must fail closed.'
  Set-Content -LiteralPath $modePath -Value 'nested' -NoNewline -Encoding utf8
  Assert-Equal 31 (Invoke-Launcher) 'Nested UP must not satisfy top-level health.'
  Set-Content -LiteralPath $modePath -Value 'up' -NoNewline -Encoding utf8

  $missing = "$chromium.missing"
  Move-Item -LiteralPath $chromium -Destination $missing
  try { Assert-Equal 30 (Invoke-Launcher) 'Missing Chromium must fail.' } finally { Move-Item -LiteralPath $missing -Destination $chromium }

  Set-Content -LiteralPath (Join-Path $evidenceRoot 'exit-code.txt') -Value '7' -NoNewline -Encoding ascii
  try { Assert-Equal 40 (Invoke-Launcher) 'Browser non-zero exit must be recorded as launcher failure.' } finally { Remove-Item -LiteralPath (Join-Path $evidenceRoot 'exit-code.txt') -Force }

  New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
  [IO.File]::WriteAllBytes((Join-Path $logRoot 'launcher.log'), [byte[]]::new(1048576))
  Assert-Equal 0 (Invoke-Launcher) 'Log rotation launch must succeed.'
  Assert-True (Test-Path -LiteralPath (Join-Path $logRoot 'launcher.log.1')) 'Log rotation did not retain prior log.'

  Set-Content -LiteralPath (Join-Path $evidenceRoot 'hold.flag') -Value 'hold' -NoNewline -Encoding ascii
  Remove-Item -LiteralPath (Join-Path $evidenceRoot 'fake-pid.txt') -Force -ErrorAction SilentlyContinue
  $first = Start-LauncherProcess
  Wait-For { Test-Path -LiteralPath (Join-Path $evidenceRoot 'fake-pid.txt') } 'Fake browser did not start.'
  for ($i = 0; $i -lt 9; $i++) { Assert-Equal 20 (Invoke-Launcher) "Duplicate launch $($i + 2) must be rejected." }
  if (!$first.WaitForExit(12000)) { throw 'Primary launcher did not exit after fake browser hold.' }
  Assert-Equal 0 $first.ExitCode 'Primary held launcher must exit cleanly.'
  Remove-Item -LiteralPath (Join-Path $evidenceRoot 'hold.flag') -Force

  Set-Content -LiteralPath (Join-Path $evidenceRoot 'hold.flag') -Value 'hold' -NoNewline -Encoding ascii
  Remove-Item -LiteralPath (Join-Path $evidenceRoot 'fake-pid.txt') -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $harnessPidPath -Force -ErrorAction SilentlyContinue
  $env:CGCPMS_PROCESS_HARNESS_PID_FILE = $harnessPidPath
  try { $orphanLauncher = Start-LauncherProcess } finally { Remove-Item Env:CGCPMS_PROCESS_HARNESS_PID_FILE -ErrorAction SilentlyContinue }
  Wait-For { Test-Path -LiteralPath $harnessPidPath } 'Harness did not record launcher child PID.'
  Wait-For { Test-Path -LiteralPath (Join-Path $evidenceRoot 'fake-pid.txt') } 'Orphan fixture browser did not start.'
  Wait-For { Test-Path -LiteralPath (Join-Path $stateRoot 'launcher-state.json') } 'Launcher did not persist browser state.'
  $fakePid = [int](Get-Content -Raw -LiteralPath (Join-Path $evidenceRoot 'fake-pid.txt'))
  $launcherPid = [int](Get-Content -Raw -LiteralPath $harnessPidPath)
  Stop-Process -Id $launcherPid -Force
  $orphanLauncher.WaitForExit()
  Assert-Equal 21 (Invoke-Launcher) 'Live orphan browser must prevent duplicate profile launch.'
  Stop-Process -Id $fakePid -Force
  Wait-For { !(Get-Process -Id $fakePid -ErrorAction SilentlyContinue) } 'Fake orphan browser did not stop.'
  Remove-Item -LiteralPath (Join-Path $evidenceRoot 'hold.flag') -Force
  Assert-Equal 0 (Invoke-Launcher) 'Stale state must recover after orphan browser exits.'

  $before = Get-ChildItem -LiteralPath $packageRoot -File -Recurse | ForEach-Object { "$($_.FullName.Substring($packageRoot.Length))=$((Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash)" }
  Assert-Equal 0 (Invoke-Launcher) 'Package immutability launch must succeed.'
  $after = Get-ChildItem -LiteralPath $packageRoot -File -Recurse | ForEach-Object { "$($_.FullName.Substring($packageRoot.Length))=$((Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash)" }
  Assert-Equal ($before -join "`n") ($after -join "`n") 'Launcher or browser wrote into package directory.'

  Assert-True (Test-Path -LiteralPath (Join-Path $profileRoot 'chromium-123\UserData')) 'Chromium major profile isolation missing.'
  Write-Output 'launcher contract worker: PASS'
} finally {
  if ($server -and !$server.HasExited) {
    Stop-Process -Id $server.Id -Force
    [void]$server.WaitForExit(5000)
  }
  if ($null -eq $originalLocalAppData) { Remove-Item Env:LOCALAPPDATA -ErrorAction SilentlyContinue } else { $env:LOCALAPPDATA = $originalLocalAppData }
  if ($null -eq $originalTemp) { Remove-Item Env:TEMP -ErrorAction SilentlyContinue } else { $env:TEMP = $originalTemp }
  if ($null -eq $originalTmp) { Remove-Item Env:TMP -ErrorAction SilentlyContinue } else { $env:TMP = $originalTmp }
  if ($null -eq $originalRunId) { Remove-Item Env:CGCPMS_CONTRACT_RUN_ID -ErrorAction SilentlyContinue } else { $env:CGCPMS_CONTRACT_RUN_ID = $originalRunId }
  if ($null -eq $originalDataRoot) { Remove-Item Env:CGCPMS_CONTRACT_DATA_ROOT -ErrorAction SilentlyContinue } else { $env:CGCPMS_CONTRACT_DATA_ROOT = $originalDataRoot }
  Remove-TestRoot
}
