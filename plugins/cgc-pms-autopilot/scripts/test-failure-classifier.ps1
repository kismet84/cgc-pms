param(
    [string]$ErrorText = '',
    [int]$ExitCode = 1,
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$text = $ErrorText.ToLowerInvariant()
$category = 'unknown'
$reason = 'No known pattern matched.'
$suggestedNextAction = 'Collect more evidence and review manually.'

if ($text -match 'parsererror|unexpected argument|is not recognized|commandnotfound|not loaded|script.*not found') {
    $category = 'tool_config'
    $reason = 'Command invocation or tool configuration failed.'
    $suggestedNextAction = 'Fix command syntax, install missing tool, or verify script entrypoint.'
} elseif ($text -match 'econnrefused|connection refused|timed out|docker|wsl|actuator/health|dev-login|port .* refused|service unavailable') {
    $category = 'environment_prereq'
    $reason = 'Runtime dependency or service readiness is missing.'
    $suggestedNextAction = 'Refresh runtime state, wait for readiness, then retry verification.'
} elseif ($text -match 'no tests matching|class not found|method not found|selector|ready issue|verify command|outputpath is required') {
    $category = 'ready_issue_config'
    $reason = 'Ready issue metadata or verification selector is invalid.'
    $suggestedNextAction = 'Fix ready issue fields or replace the invalid selector with an existing target.'
} elseif ($ExitCode -ne 0 -or $text -match 'assert|failed|compilation error|test failed|unauthorized|forbidden|tenant|security|schema') {
    $category = 'real_quality_or_security'
    $reason = 'Evidence points to a real quality or security failure.'
    $suggestedNextAction = 'Escalate to implementation or blocking review with concrete evidence.'
}

$result = [ordered]@{
    category = $category
    reason = $reason
    suggestedNextAction = $suggestedNextAction
}

$json = $result | ConvertTo-Json -Depth 3
if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false))
}

$json
