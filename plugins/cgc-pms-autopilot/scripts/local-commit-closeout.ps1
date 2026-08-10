param(
    [Parameter(Mandatory = $true)][string]$IssueId,
    [string]$CommitMessage = '',
    [string[]]$ExpectedPaths = @(),
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Normalize-RepoPath {
    param([string]$Value)

    $normalized = $Value.Trim()
    if ($normalized.Length -ge 2 -and $normalized.StartsWith('"') -and $normalized.EndsWith('"')) {
        $normalized = $normalized.Substring(1, $normalized.Length - 2)
    }
    $normalized = $normalized.Replace('\', '/').Trim()
    return $normalized.TrimEnd('/')
}

function Test-ExpectedPathMatch {
    param(
        [string]$Path,
        [string]$Expected
    )

    $actual = Normalize-RepoPath $Path
    $target = Normalize-RepoPath $Expected
    if (-not $actual -or -not $target) {
        return $false
    }

    return $actual -eq $target `
        -or $actual.StartsWith($target + '/') `
        -or $target.StartsWith($actual + '/')
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Push-Location $repoRoot
try {
    $statusLines = @((& git status --short))
    $diffCheck = @(& git -c core.autocrlf=false -c core.safecrlf=false diff --check 2>&1)
    $diffExit = $LASTEXITCODE

    $unexpected = @()
    if ($ExpectedPaths.Count -gt 0) {
        foreach ($line in $statusLines) {
            if ($line.Length -lt 4) { continue }
            $path = $line.Substring(3).Trim()
            $matched = $false
            foreach ($expected in $ExpectedPaths) {
                if (Test-ExpectedPathMatch -Path $path -Expected $expected) {
                    $matched = $true
                    break
                }
            }
            if (-not $matched) {
                $unexpected += $path
            }
        }
    }

    $result = [ordered]@{
        issueId = $IssueId
        dryRun = $true
        previewOnly = $true
        diffCheckPassed = ($diffExit -eq 0)
        diffCheckOutput = @($diffCheck) -join [Environment]::NewLine
        expectedPaths = $ExpectedPaths
        unexpectedPaths = $unexpected
        gitStatus = $statusLines
        willCommit = $false
    }

    $result | ConvertTo-Json -Depth 5
} finally {
    Pop-Location
}
