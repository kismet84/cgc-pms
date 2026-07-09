Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$pluginRoot = Split-Path -Parent $PSScriptRoot

$requiredPaths = @(
    'artifacts/README.md',
    'schemas/loop-state.schema.json',
    'schemas/loop-event.schema.json',
    'schemas/classification-result.schema.json',
    'templates/repair-request.md',
    'templates/reflection-entry.md',
    'templates/loop-run-report.md',
    'examples/loop-state.example.json',
    'examples/loop-event.example.json',
    'examples/repair-request.example.md',
    'examples/reflection-entry.example.md',
    'examples/classification-environment.example.json',
    'examples/classification-preview.example.json',
    'examples/classification-ready-config.example.json',
    'examples/classification-real-quality.example.json',
    'references/loop-budget-policy.md',
    'references/rerun-policy.md',
    'references/role-contracts.md',
    'references/forward-test-scenarios.md',
    'references/classifier-rules.md',
    'scripts/autopilot-loop-runner.ps1',
    'scripts/test-failure-classifier.ps1',
    'skills/cgc-pms-autopilot-owner/SKILL.md'
)

$jsonPaths = @(
    'schemas/loop-state.schema.json',
    'schemas/loop-event.schema.json',
    'schemas/classification-result.schema.json',
    'examples/loop-state.example.json',
    'examples/loop-event.example.json',
    'examples/classification-environment.example.json',
    'examples/classification-preview.example.json',
    'examples/classification-ready-config.example.json',
    'examples/classification-real-quality.example.json'
)

$missing = New-Object System.Collections.Generic.List[string]
$invalidJson = New-Object System.Collections.Generic.List[string]

foreach ($relativePath in $requiredPaths) {
    $fullPath = Join-Path $pluginRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        $missing.Add($relativePath)
    }
}

foreach ($relativePath in $jsonPaths) {
    $fullPath = Join-Path $pluginRoot $relativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        continue
    }

    try {
        Get-Content -LiteralPath $fullPath -Raw | ConvertFrom-Json | Out-Null
    } catch {
        $invalidJson.Add($relativePath)
    }
}

$schemaExampleChecks = @(
    @{ Schema = 'schemas/classification-result.schema.json'; Example = 'examples/classification-environment.example.json' },
    @{ Schema = 'schemas/classification-result.schema.json'; Example = 'examples/classification-preview.example.json' },
    @{ Schema = 'schemas/classification-result.schema.json'; Example = 'examples/classification-ready-config.example.json' },
    @{ Schema = 'schemas/classification-result.schema.json'; Example = 'examples/classification-real-quality.example.json' }
)
$schemaMismatches = New-Object System.Collections.Generic.List[string]

foreach ($check in $schemaExampleChecks) {
    $schemaPath = Join-Path $pluginRoot $check.Schema
    $examplePath = Join-Path $pluginRoot $check.Example
    if (-not (Test-Path -LiteralPath $schemaPath -PathType Leaf) -or -not (Test-Path -LiteralPath $examplePath -PathType Leaf)) {
        continue
    }

    try {
        $schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
        $example = Get-Content -LiteralPath $examplePath -Raw | ConvertFrom-Json
        foreach ($requiredProperty in @($schema.required)) {
            if ($requiredProperty -notin $example.PSObject.Properties.Name) {
                $schemaMismatches.Add("$($check.Example) missing required property '$requiredProperty'")
            }
        }
    } catch {
        $schemaMismatches.Add("$($check.Example) schema precheck failed")
    }
}

$ok = ($missing.Count -eq 0 -and $invalidJson.Count -eq 0 -and $schemaMismatches.Count -eq 0)
$summary = if ($ok) {
    'All artifact governance docs, loop schemas, classification assets, templates, examples, references, scripts, and skill files passed presence and JSON parsing checks.'
} else {
    "Missing=$($missing.Count); InvalidJson=$($invalidJson.Count); SchemaMismatches=$($schemaMismatches.Count)"
}
$nextAction = if ($ok) {
    'Run plugin validation, classifier examples, loop runner dry-run scenarios, and git diff --check.'
} else {
    'Add missing files or fix invalid JSON before further validation.'
}

[ordered]@{
    ok = $ok
    missing = @($missing)
    invalidJson = @($invalidJson)
    schemaMismatches = @($schemaMismatches)
    summary = $summary
    nextAction = $nextAction
} | ConvertTo-Json -Depth 4
