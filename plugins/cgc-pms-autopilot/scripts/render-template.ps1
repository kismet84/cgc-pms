param(
    [Parameter(Mandatory = $true)][string]$TemplatePath,
    [Parameter(Mandatory = $true)][string]$VariablesJson,
    [string]$OutputPath,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $TemplatePath -PathType Leaf)) {
    throw "Template not found: $TemplatePath"
}

try {
    $variables = $VariablesJson | ConvertFrom-Json
} catch {
    throw "VariablesJson must be valid JSON"
}

if (-not $variables) {
    $variables = [pscustomobject]@{}
}

$template = Get-Content -Encoding UTF8 -LiteralPath $TemplatePath -Raw
$tokens = [regex]::Matches($template, '\{\{([a-zA-Z0-9_]+)\}\}') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
$missing = @($tokens | Where-Object {
    $property = $variables.PSObject.Properties[$_]
    $null -eq $property -or $null -eq $property.Value
})
if ($missing.Count -gt 0) {
    throw ("Missing template variables: " + ($missing -join ', '))
}

$rendered = $template
foreach ($token in $tokens) {
    $value = [string]$variables.PSObject.Properties[$token].Value
    $rendered = $rendered.Replace("{{${token}}}", $value)
}

if ($OutputPath) {
    if ((Test-Path -LiteralPath $OutputPath) -and -not $Force) {
        throw "Output already exists. Use -Force to overwrite: $OutputPath"
    }
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($OutputPath, $rendered, [System.Text.UTF8Encoding]::new($false))
}

$rendered
