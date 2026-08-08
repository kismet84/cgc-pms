[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$launcherRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$buildScript = Join-Path $launcherRoot 'scripts\build.ps1'
$buildOutput = & $buildScript -Configuration Release -Architecture x64 -Contract
if ($LASTEXITCODE -ne 0) { throw 'Contract build failed.' }
$packageRoot = [string]($buildOutput | Select-Object -Last 1)
$launcher = Join-Path $packageRoot 'CGC-PMS.exe'
$chromium = Join-Path $packageRoot 'chromium\chrome.exe'
$contractRoot = Join-Path $launcherRoot 'out\contract'
$dataRoot = Join-Path $env:LOCALAPPDATA 'CGC-PMS\Desktop'
$evidenceRoot = Join-Path $dataRoot 'contract-evidence'
$stateRoot = Join-Path $dataRoot 'contract-runtime'
$profileRoot = Join-Path $dataRoot 'contract-profiles'
$logRoot = Join-Path $dataRoot 'contract-logs'
$modePath = Join-Path $contractRoot 'health-mode.txt'
$readyPath = Join-Path $contractRoot 'health-ready.txt'
$serverScript = Join-Path $PSScriptRoot 'health-server.mjs'
$processHarness = Join-Path $contractRoot 'process-harness.exe'
$harnessPidPath = Join-Path $contractRoot 'harness-child-pid.txt'

function Assert-Equal($Expected, $Actual, [string]$Message) {
  if ($Expected -ne $Actual) { throw "$Message expected=$Expected actual=$Actual" }
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (!$Condition) { throw $Message }
}

$sourceText = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'src\main.cpp')
Assert-True ($sourceText.Contains('WinHttpCloseHandle(value)')) 'WinHTTP handles must use WinHttpCloseHandle.'
$packageText = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'scripts\package.ps1')
$stageVerify = $packageText.IndexOf("verify-package.ps1') -PackagePath `$stagedPackage", [StringComparison]::Ordinal)
$publishMove = $packageText.IndexOf('Move-Item -LiteralPath $stagedPackage -Destination $package', [StringComparison]::Ordinal)
Assert-True ($stageVerify -ge 0 -and $publishMove -gt $stageVerify) 'Package must verify staging before publishing a new version path.'
Assert-True ($packageText.Contains("verify-package.ps1') -PackagePath `$zipVerify")) 'ZIP extraction must be verified before publication.'
Assert-True ($packageText.Contains('Versioned package already exists')) 'Existing versioned packages must fail closed instead of being replaced.'
Assert-True (!$packageText.Contains('Remove-Item -LiteralPath $package')) 'Publisher must never delete a canonical versioned package.'
$fetchText = Get-Content -Raw -LiteralPath (Join-Path $launcherRoot 'scripts\fetch-chromium.ps1')
Assert-True ($fetchText.Contains("Join-Path `$env:LOCALAPPDATA 'CGC-PMS\Desktop\cache'")) 'Default Chromium cache must stay outside repository.'

$longRoot = $contractRoot
foreach ($segment in @((('长路径' * 18) -join ''), (('空格 path ' * 8) -join ''), ('nested-' + (('x' * 72) -join '')), ('deep-' + (('y' * 72) -join '')))) {
  $longRoot = Join-Path $longRoot $segment
}
New-Item -ItemType Directory -Force -Path $longRoot | Out-Null
Copy-Item -Path (Join-Path $packageRoot '*') -Destination $longRoot -Recurse
$packageRoot = $longRoot
$launcher = Join-Path $packageRoot 'CGC-PMS.exe'
$chromium = Join-Path $packageRoot 'chromium\chrome.exe'
Assert-True ($packageRoot.Length -gt 260) 'Contract package path must exercise Windows long-path handling.'

function Wait-For([scriptblock]$Condition, [string]$Message, [int]$TimeoutSeconds = 10) {
  $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
  do {
    if (& $Condition) { return }
    Start-Sleep -Milliseconds 100
  } while ([DateTime]::UtcNow -lt $deadline)
  throw $Message
}

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

function Remove-TestState {
  foreach ($path in $evidenceRoot, $stateRoot, $profileRoot, $logRoot) {
    $full = [IO.Path]::GetFullPath($path)
    $prefix = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'CGC-PMS\Desktop')).TrimEnd('\') + '\'
    if (!$full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw "Unsafe cleanup path: $full" }
    if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Recurse -Force }
  }
}

Remove-TestState
Set-Content -LiteralPath $modePath -Value 'up' -NoNewline -Encoding utf8
if (Test-Path -LiteralPath $readyPath) { Remove-Item -LiteralPath $readyPath -Force }
$server = Start-Process -FilePath (Get-Command node.exe).Source `
  -ArgumentList @($serverScript, $modePath, $readyPath, '55173') -PassThru -WindowStyle Hidden
try {
  Wait-For { Test-Path -LiteralPath $readyPath } 'Health fixture did not start.'

  Assert-Equal 10 (Invoke-Launcher @('--app=https://example.invalid')) 'Unknown arguments must fail.'

  Assert-Equal 0 (Invoke-Launcher) 'Healthy launch must succeed.'
  $argsPath = Join-Path $evidenceRoot 'fake-argv.txt'
  Assert-True (Test-Path -LiteralPath $argsPath) 'Fake browser argv evidence missing.'
  $argsText = Get-Content -Raw -LiteralPath $argsPath
  Assert-True ($argsText.Contains('--app=http://127.0.0.1:5173/')) 'Application URL is not fixed.'
  Assert-True ($argsText.Contains('--user-data-dir=')) 'Dedicated profile argument missing.'
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
  Write-Output 'launcher contract: PASS'
} finally {
  if ($server -and !$server.HasExited) { Stop-Process -Id $server.Id -Force }
  Remove-TestState
}
