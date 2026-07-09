param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path,
    [string]$AutopilotDir = '.codex-autopilot',
    [switch]$CheckHealth,
    [switch]$CheckGit,
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Push-Location $RepoRoot
try {
    $branch = (& git branch --show-current).Trim()
    $gitStatus = @((& git status --short)) -join [Environment]::NewLine
    $flagsRoot = Join-Path $RepoRoot $AutopilotDir

    $stopPath = Join-Path $flagsRoot 'stop.flag'
    $pausePath = Join-Path $flagsRoot 'pause.flag'
    $enabledPath = Join-Path $flagsRoot 'enabled.flag'

    $health = $null
    if ($CheckHealth) {
        $health = [ordered]@{}
        foreach ($url in @(
            'http://localhost:8080/api/actuator/health',
            'http://localhost:5173/',
            'http://localhost:5173/api/auth/dev-login?redirect=/dashboard'
        )) {
            try {
                $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
                $health[$url] = @{ ok = $true; statusCode = [int]$resp.StatusCode }
            } catch {
                $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode.value__ } else { $null }
                $health[$url] = @{ ok = $false; statusCode = $code; message = $_.Exception.Message }
            }
        }
    }

    $decision = 'go'
    $reasons = @()
    if (Test-Path -LiteralPath $stopPath) {
        $decision = 'stop'
        $reasons += 'stop.flag present'
    }
    if (Test-Path -LiteralPath $pausePath) {
        $decision = 'pause'
        $reasons += 'pause.flag present'
    }
    if (-not (Test-Path -LiteralPath $enabledPath)) {
        if ($decision -eq 'go') { $decision = 'disabled' }
        $reasons += 'enabled.flag missing'
    }

    $result = [ordered]@{
        repoRoot = $RepoRoot
        branch = $branch
        gitStatus = $gitStatus
        stopFlag = Test-Path -LiteralPath $stopPath
        pauseFlag = Test-Path -LiteralPath $pausePath
        enabledFlag = Test-Path -LiteralPath $enabledPath
        checkGit = [bool]$CheckGit
        checkHealth = [bool]$CheckHealth
        healthGate = $health
        decision = $decision
        reasons = $reasons
    }

    if ($AsJson -or $true) {
        $result | ConvertTo-Json -Depth 5
    }
} finally {
    Pop-Location
}
