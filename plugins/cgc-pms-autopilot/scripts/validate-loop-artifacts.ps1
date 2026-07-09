Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$pluginRoot = Split-Path -Parent $PSScriptRoot

$requiredPaths = @(
    'schemas/loop-state.schema.json',
    'schemas/loop-event.schema.json',
    'templates/repair-request.md',
    'templates/reflection-entry.md',
    'examples/loop-state.example.json',
    'examples/loop-event.example.json',
    'examples/repair-request.example.md',
    'examples/reflection-entry.example.md',
    'references/loop-budget-policy.md',
    'references/rerun-policy.md',
    'references/role-contracts.md',
    'references/forward-test-scenarios.md',
    'skills/cgc-pms-autopilot-owner/SKILL.md'
)

$jsonPaths = @(
    'schemas/loop-state.schema.json',
    'schemas/loop-event.schema.json',
    'examples/loop-state.example.json',
    'examples/loop-event.example.json'
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

$ok = ($missing.Count -eq 0 -and $invalidJson.Count -eq 0)
$summary = if ($ok) {
    'All loop schemas, templates, examples, references, and skill files passed presence and JSON parsing checks.'
} else {
    "Missing=$($missing.Count); InvalidJson=$($invalidJson.Count)"
}
$nextAction = if ($ok) {
    'Run plugin validation, template renders, and git diff --check.'
} else {
    'Add missing files or fix invalid JSON before further validation.'
}

[ordered]@{
    ok = $ok
    missing = @($missing)
    invalidJson = @($invalidJson)
    summary = $summary
    nextAction = $nextAction
} | ConvertTo-Json -Depth 4
