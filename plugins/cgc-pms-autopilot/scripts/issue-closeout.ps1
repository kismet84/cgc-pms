param(
    [Parameter(Mandatory = $true)][ValidateSet('done', 'blocked')][string]$CloseoutType,
    [Parameter(Mandatory = $true)][string]$VariablesJson,
    [string]$OutputPath,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $OutputPath) {
    throw "OutputPath is required"
}

$templateName = if ($CloseoutType -eq 'done') { 'done-issue.md' } else { 'blocked-issue.md' }
$templatePath = Join-Path $PSScriptRoot ("..\templates\" + $templateName)

& (Join-Path $PSScriptRoot 'render-template.ps1') -TemplatePath $templatePath -VariablesJson $VariablesJson -OutputPath $OutputPath -Force:$Force
