param(
    [Parameter(Mandatory = $true)][string]$VariablesJson,
    [string]$TemplatePath,
    [string]$OutputPath,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $TemplatePath) {
    $TemplatePath = Join-Path $PSScriptRoot '..\templates\ready-issue.md'
}

if (-not $OutputPath) {
    throw "OutputPath is required"
}

& (Join-Path $PSScriptRoot 'render-template.ps1') -TemplatePath $TemplatePath -VariablesJson $VariablesJson -OutputPath $OutputPath -Force:$Force
