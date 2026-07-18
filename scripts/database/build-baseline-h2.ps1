param(
    [Parameter(Mandatory = $true)]
    [string]$MySqlBaseline,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $MySqlBaseline).Path
$target = [System.IO.Path]::GetFullPath($OutputPath)
$targetDirectory = Split-Path -Parent $target
if (-not (Test-Path -LiteralPath $targetDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $targetDirectory | Out-Null
}

$output = [System.Collections.Generic.List[string]]::new()
$foreignKeys = [System.Collections.Generic.List[string]]::new()
$tableBuffer = $null
$currentTable = $null
$skippingViewStub = $false
$foreignKeysEmitted = $false

$output.Add('-- Generated from the MySQL B215 reference schema for H2 MySQL mode.')
$output.Add('SET REFERENTIAL_INTEGRITY FALSE;')

function Convert-Line([string]$line) {
    $converted = $line -replace '_utf8mb4', ''
    $converted = $converted -replace ' CHARACTER SET [A-Za-z0-9_]+ COLLATE [A-Za-z0-9_]+', ''
    $converted = $converted -replace ' COLLATE [A-Za-z0-9_]+', ''
    $converted = $converted -replace ' ON UPDATE CURRENT_TIMESTAMP', ''
    $converted = $converted -replace 'json_valid\((`[^`]+`)\)', '($1 IS JSON)'
    $converted = $converted -replace 'GENERATED ALWAYS AS \(\(', 'GENERATED ALWAYS AS ('
    $converted = $converted -replace '\)\) STORED', ')'
    if ($converted -match '^\) ENGINE=InnoDB.*;$') {
        return ');'
    }
    return $converted
}

foreach ($rawLine in [System.IO.File]::ReadLines($source)) {
    $line = [string]$rawLine

    if ($skippingViewStub) {
        if ($line -match '\*/;$') {
            $skippingViewStub = $false
        }
        continue
    }
    if ($line -match '^/\*!50001 CREATE VIEW ') {
        if ($line -notmatch '\*/;$') {
            $skippingViewStub = $true
        }
        continue
    }
    if ($line -match '^/\*!50001 VIEW (.+) \*/;$') {
        $output.Add('CREATE VIEW ' + $Matches[1] + ';')
        continue
    }
    if ($line -match '^/\*!' -or
        $line -match '^SET @' -or
        $line -match '^SET character_set' -or
        $line -match '^LOCK TABLES' -or
        $line -match '^UNLOCK TABLES' -or
        $line -match '^DROP TABLE IF EXISTS `v_' -or
        $line -match '^DROP VIEW IF EXISTS `v_') {
        continue
    }

    if ($line -eq 'SET FOREIGN_KEY_CHECKS=0;') {
        continue
    }
    if ($line -eq 'SET FOREIGN_KEY_CHECKS=1;') {
        if (-not $foreignKeysEmitted) {
            $output.Add('')
            $output.Add('-- Foreign keys are added after every table and deterministic seed row exist.')
            foreach ($foreignKey in $foreignKeys) {
                $output.Add($foreignKey)
            }
            $foreignKeysEmitted = $true
        }
        $output.Add('SET REFERENTIAL_INTEGRITY TRUE;')
        continue
    }

    if ($null -eq $tableBuffer -and $line -match '^CREATE TABLE (?:IF NOT EXISTS )?(`[^`]+`)') {
        $currentTable = $Matches[1]
        $tableBuffer = [System.Collections.Generic.List[string]]::new()
        $tableBuffer.Add((Convert-Line $line))
        continue
    }

    if ($null -ne $tableBuffer) {
        if ($line -match '^\s*CONSTRAINT\s+.+\s+FOREIGN KEY\s+') {
            $constraint = $line.Trim().TrimEnd(',')
            $foreignKeys.Add("ALTER TABLE $currentTable ADD $constraint;")
            continue
        }

        $converted = Convert-Line $line
        if ($converted -eq ');') {
            for ($index = $tableBuffer.Count - 1; $index -ge 0; $index--) {
                if (-not [string]::IsNullOrWhiteSpace($tableBuffer[$index])) {
                    $tableBuffer[$index] = $tableBuffer[$index].TrimEnd(',')
                    break
                }
            }
            $tableBuffer.Add($converted)
            foreach ($tableLine in $tableBuffer) {
                $output.Add($tableLine)
            }
            $tableBuffer = $null
            $currentTable = $null
            continue
        }
        $tableBuffer.Add($converted)
        continue
    }

    $output.Add((Convert-Line $line))
}

if ($null -ne $tableBuffer) {
    throw 'Unclosed CREATE TABLE block in MySQL baseline'
}
if (-not $foreignKeysEmitted) {
    foreach ($foreignKey in $foreignKeys) {
        $output.Add($foreignKey)
    }
    $output.Add('SET REFERENTIAL_INTEGRITY TRUE;')
}

[System.IO.File]::WriteAllLines($target, $output, [System.Text.UTF8Encoding]::new($false))
Write-Output ([pscustomobject]@{
    input = $source
    output = $target
    lines = $output.Count
    foreignKeys = $foreignKeys.Count
})
