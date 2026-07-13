param(
    [ValidateSet('manual', 'scheduled', 'closeout')]
    [string]$Trigger = 'manual'
)

$ErrorActionPreference = 'Stop'
$toolRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $toolRoot '..\..')).Path
$runtimeDir = Join-Path $repoRoot '.agent-runtime\knowledge-graph'
$lockPath = Join-Path $runtimeDir 'collect.lock'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

$lock = $null
try {
    $lock = [System.IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None')
} catch {
    [Console]::Error.WriteLine('Knowledge graph collection is already running.')
    exit 2
}

try {
    Set-Location $toolRoot
    $env:CGC_KG_TRIGGER = $Trigger
    node src/cli.js collect --trigger $Trigger
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    if ($lock) { $lock.Dispose() }
    Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
}
