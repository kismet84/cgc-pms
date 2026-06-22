# check-sql-safety.ps1
# Static analysis gate to detect SQL injection patterns in MyBatis XML and Java mapper code.
# Scans only mapper-related paths to avoid @Value false positives.
# Exit 0 = PASS, Exit 1 = violations found.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\check-sql-safety.ps1
#
# Exemption: add "SQL-SAFETY: server-side-enum" to the comment on the same line.

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
    @{ Name = 'MyBatis-Plus .apply()'; Regex = '\.apply\s*\(' },
    @{ Name = 'MyBatis-Plus .last()'; Regex = '\.last\s*\(' },
    @{ Name = 'MyBatis-Plus .having()'; Regex = '\.having\s*\(' },
    @{ Name = 'Raw JDBC Statement'; Regex = '\bStatement\b' }
)

# -------------------------------------------------------------------
# Exemption marker
# -------------------------------------------------------------------
$exemptionMarker = 'SQL-SAFETY: server-side-enum'

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

# 2) Java source under backend src/main/java (mapper packages + annotations)
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

    # Skip non-mapper Java files: only care about files in mapper/ packages or with mapper-relevant annotations
    if ($ext -eq '.java') {
        $isMapper = ($shortPath -match '[\\/]mapper[\\/]')
        if (-not $isMapper) {
            # Also scan any .java that has actual annotation SQL (e.g. @Select(, not just mentioned in comments)
            $content = Get-Content -Path $file -Raw -ErrorAction SilentlyContinue
            if ($content -match '@(Select|Update|Delete|Insert)\s*\(') {
                # keep — it has SQL annotations as actual method annotations
            } else {
                continue
            }
        }
    }

    $lines = Get-Content -Path $file -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $lineNum = $i + 1
        $line = $lines[$i]

        # Skip comment lines (// or /* or * or javadoc /**)
        $trimmed = $line.TrimStart()
        if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('*')) {
            continue
        }

        # Skip lines with exemption marker
        if ($line -match [regex]::Escape($exemptionMarker)) {
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
Write-Host "add the comment // $exemptionMarker on the same line."
Write-Host ''

exit 1
