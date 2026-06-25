# check-sql-safety.ps1
# Static analysis gate to detect SQL injection patterns in MyBatis XML and Java code.
# Exit 0 = PASS, Exit 1 = violations found.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1
#
# Exemption: add a supported "SQL-SAFETY: ..." marker to the comment on the same line.

param(
    [string]$RootDir = ""
)

$ErrorActionPreference = 'Stop'

if (-not $RootDir) {
    $RootDir = Split-Path -Parent $PSScriptRoot
}

$exitCode = 0
$violations = [System.Collections.ArrayList]::new()

# -------------------------------------------------------------------
# Pattern definitions
# -------------------------------------------------------------------
$patterns = @(
    @{ Name = '${} MyBatis string substitution'; Regex = '\$\{' },
    @{ Name = '@Select/@Update/@Delete/@Insert with string concat'; Regex = '@(Select|Update|Delete|Insert)\s*\(.*"\s*\+\s*[^"\r\n]' },
    @{ Name = 'MyBatis-Plus .apply()'; Regex = '\.apply\s*\(\s*"' },
    @{ Name = 'MyBatis-Plus .last()'; Regex = '\.last\s*\(\s*"' },
    @{ Name = 'MyBatis-Plus .having()'; Regex = '\.having\s*\(\s*"' },
    @{ Name = 'Raw JDBC Statement'; Regex = '\bStatement\s+\w+\s*=\s*[\w.]+\.createStatement\s*\(' }
)

# -------------------------------------------------------------------
# Exemption markers
# -------------------------------------------------------------------
$exemptionMarkers = @(
    'SQL-SAFETY: server-side-enum',
    'SQL-SAFETY: parameterized-like',
    'SQL-SAFETY: parameterized-exists',
    'SQL-SAFETY: fixed-sql-fragment',
    'SQL-SAFETY: migration-ddl'
)

# -------------------------------------------------------------------
# Scan targets
# -------------------------------------------------------------------
$scanTargets = @()

# 1) MyBatis XML mapper directory (if exists)
$xmlMapperDir = Join-Path $RootDir 'backend\src\main\resources\mapper'
if (Test-Path $xmlMapperDir) {
    $xmlFiles = Get-ChildItem -Path $xmlMapperDir -Recurse -Filter *.xml -ErrorAction SilentlyContinue
    foreach ($f in $xmlFiles) { $scanTargets += $f.FullName }
}

# 2) Java source under backend src/main/java
$javaSrcDir = Join-Path $RootDir 'backend\src\main\java\com\cgcpms'
if (Test-Path $javaSrcDir) {
    $javaFiles = Get-ChildItem -Path $javaSrcDir -Recurse -Filter *.java -ErrorAction SilentlyContinue
    foreach ($f in $javaFiles) { $scanTargets += $f.FullName }
}

if ($scanTargets.Count -eq 0) {
    Write-Host 'SQL injection scan PASS — no mapper files found to scan.'
    exit 0
}

# -------------------------------------------------------------------
# Scan loop
# -------------------------------------------------------------------
foreach ($file in $scanTargets) {
    $ext = [System.IO.Path]::GetExtension($file)
    $shortPath = $file.Replace($RootDir + '\', '')

    $lines = @(Get-Content -Path $file -ErrorAction SilentlyContinue)
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $lineNum = $i + 1
        $line = $lines[$i]

        # Skip comment lines (// or /* or * or javadoc /**)
        $trimmed = $line.TrimStart()
        if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('*')) {
            continue
        }
        if ($trimmed.StartsWith('import ') -or $trimmed.StartsWith('@Value(')) {
            continue
        }

        # Skip lines with a supported exemption marker.
        $hasExemption = $false
        foreach ($marker in $exemptionMarkers) {
            if ($line -match [regex]::Escape($marker)) {
                $hasExemption = $true
                break
            }
        }
        if ($hasExemption) {
            continue
        }

        foreach ($p in $patterns) {
            if ($line -match $p.Regex) {
                $trimmed = $line.Trim()
                [void]$violations.Add(@{
                    File    = $shortPath
                    Line    = $lineNum
                    Pattern = $p.Name
                    Content = $trimmed
                })
                break  # one violation per line is enough
            }
        }
    }
}

# -------------------------------------------------------------------
# Report
# -------------------------------------------------------------------
if ($violations.Count -eq 0) {
    Write-Host 'SQL injection scan PASS'
    exit 0
}

Write-Host ''
Write-Host '=== SQL INJECTION RISK DETECTED ==='
Write-Host ''

foreach ($v in $violations) {
    Write-Host "$($v.File):$($v.Line)  [$($v.Pattern)]"
    Write-Host "  $($v.Content)"
    Write-Host ''
}

Write-Host "Total: $($violations.Count) potential SQL injection site(s)"
Write-Host ''
Write-Host 'If these are false positives (e.g. server-side enum interpolation),'
Write-Host 'add a supported SQL-SAFETY marker on the same line with a concrete reason.'
Write-Host ''

exit 1
