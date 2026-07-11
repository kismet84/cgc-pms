param(
    [string]$ErrorText = '',
    [int]$ExitCode = 1,
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$text = if ($ErrorText) { $ErrorText } else { '' }
$normalizedText = $text.ToLowerInvariant()

function Get-EvidenceSnippet {
    param(
        [string]$SourceText,
        [string[]]$Patterns
    )

    $lines = @($SourceText -split "\r?\n")
    foreach ($line in $lines) {
        foreach ($pattern in $Patterns) {
            if ($line -match $pattern) {
                $snippet = $line.Trim()
                if ($snippet.Length -gt 180) {
                    return $snippet.Substring(0, 180)
                }
                return $snippet
            }
        }
    }

    $fallback = $SourceText.Trim()
    if (-not $fallback) {
        return 'No direct evidence captured.'
    }
    if ($fallback.Length -gt 180) {
        return $fallback.Substring(0, 180)
    }
    return $fallback
}

function New-Classification {
    param(
        [string]$Category,
        [string]$Subcategory,
        [string]$Confidence,
        [string[]]$EvidencePatterns,
        [string]$Reason,
        [string]$SuggestedNextAction,
        [string]$RetryPolicy
    )

    $fingerprintSource = "$Category|$Subcategory|$normalizedText|$ExitCode"
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $fingerprint = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($fingerprintSource)))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
    [ordered]@{
        category = $Category
        subcategory = $Subcategory
        confidence = $Confidence
        evidence = @(
            (Get-EvidenceSnippet -SourceText $text -Patterns $EvidencePatterns),
            "exitCode=$ExitCode"
        )
        suggestedNextAction = $SuggestedNextAction
        retryPolicy = $RetryPolicy
        reason = $Reason
        failureFingerprint = $fingerprint
    }
}

$result = $null

if ($normalizedText -match 'parsererror|unexpected argument|commandnotfound|missing argument|cannot process argument|parameterbinding') {
    $result = New-Classification `
        -Category 'tool_config' `
        -Subcategory 'powershell_parser_error' `
        -Confidence 'high' `
        -EvidencePatterns @('parsererror', 'unexpected argument', 'cannot process argument', 'parameterbinding') `
        -Reason 'PowerShell invocation failed before the target command ran.' `
        -SuggestedNextAction 'fix_command_syntax' `
        -RetryPolicy 'no_retry'
} elseif ($normalizedText -match 'script.*not found|is not recognized|not loaded|entrypoint|command not found') {
    $result = New-Classification `
        -Category 'tool_config' `
        -Subcategory 'command_or_entrypoint_missing' `
        -Confidence 'high' `
        -EvidencePatterns @('script.*not found', 'is not recognized', 'not loaded', 'command not found') `
        -Reason 'Tool entrypoint or shell loading is incorrect.' `
        -SuggestedNextAction 'verify_script_entrypoint' `
        -RetryPolicy 'no_retry'
} elseif ($normalizedText -match 'econnrefused|connection refused' -and $normalizedText -match '172\.19\.|vite|proxy') {
    $result = New-Classification `
        -Category 'environment_prereq' `
        -Subcategory 'vite_proxy_stale_backend' `
        -Confidence 'high' `
        -EvidencePatterns @('econnrefused', '172\.19\.', 'vite', 'proxy') `
        -Reason 'Frontend dev server is likely still proxying to an old backend container address.' `
        -SuggestedNextAction 'refresh_frontend_runtime' `
        -RetryPolicy 'rerun_after_refresh'
} elseif ($normalizedText -match 'dev-login') {
    $result = New-Classification `
        -Category 'environment_prereq' `
        -Subcategory 'dev_login_unreachable' `
        -Confidence 'medium' `
        -EvidencePatterns @('dev-login', '/login') `
        -Reason 'Dev login endpoint is unavailable, so browser acceptance cannot start from the standard shortcut path.' `
        -SuggestedNextAction 'restore_dev_login_path' `
        -RetryPolicy 'rerun_after_refresh'
} elseif ($normalizedText -match 'docker|wsl|dockerdesktoplinuxengine|cannot connect to the docker daemon') {
    $result = New-Classification `
        -Category 'environment_prereq' `
        -Subcategory 'docker_not_ready' `
        -Confidence 'high' `
        -EvidencePatterns @('docker', 'wsl', 'dockerdesktoplinuxengine', 'cannot connect to the docker daemon') `
        -Reason 'Container runtime is not ready.' `
        -SuggestedNextAction 'refresh_runtime_state' `
        -RetryPolicy 'rerun_after_refresh'
} elseif ($normalizedText -match 'actuator/health|localhost:8080|port 8080|backend.*not ready|service unavailable') {
    $result = New-Classification `
        -Category 'environment_prereq' `
        -Subcategory 'backend_not_ready' `
        -Confidence 'medium' `
        -EvidencePatterns @('actuator/health', 'localhost:8080', 'port 8080', 'service unavailable') `
        -Reason 'Backend service is unavailable or has not reached readiness yet.' `
        -SuggestedNextAction 'wait_and_retry_backend_health' `
        -RetryPolicy 'rerun_after_refresh'
} elseif ($normalizedText -match 'localhost:5173|vite ready|frontend.*not ready|port 5173') {
    $result = New-Classification `
        -Category 'environment_prereq' `
        -Subcategory 'frontend_not_ready' `
        -Confidence 'medium' `
        -EvidencePatterns @('localhost:5173', 'vite ready', 'frontend.*not ready', 'port 5173') `
        -Reason 'Frontend dev server is unavailable or has not reached readiness yet.' `
        -SuggestedNextAction 'wait_and_retry_frontend_health' `
        -RetryPolicy 'rerun_after_refresh'
} elseif ($normalizedText -match 'no tests matching|class not found|method not found|unknown lifecycle phase|selector') {
    $result = New-Classification `
        -Category 'ready_issue_config' `
        -Subcategory 'test_selector_missing_or_invalid' `
        -Confidence 'high' `
        -EvidencePatterns @('no tests matching', 'class not found', 'method not found', 'selector', 'unknown lifecycle phase') `
        -Reason 'Verification selector does not point to a valid existing test target.' `
        -SuggestedNextAction 'fix_ready_selector' `
        -RetryPolicy 'retry_after_ready_fix'
} elseif ($normalizedText -match 'ready issue|verify command|outputpath is required|expectedpaths|allowed_files|forbidden_files') {
    $result = New-Classification `
        -Category 'ready_issue_config' `
        -Subcategory 'ready_issue_verification_config' `
        -Confidence 'medium' `
        -EvidencePatterns @('ready issue', 'verify command', 'outputpath is required', 'expectedpaths', 'allowed_files', 'forbidden_files') `
        -Reason 'Ready issue metadata or verification wiring is incomplete.' `
        -SuggestedNextAction 'fix_ready_issue_metadata' `
        -RetryPolicy 'retry_after_ready_fix'
} elseif ($normalizedText -match 'testcompile|compilation error|cannot find symbol|compil(ation|e) failed') {
    $result = New-Classification `
        -Category 'real_quality_or_security' `
        -Subcategory 'maven_test_compile' `
        -Confidence 'high' `
        -EvidencePatterns @('testcompile', 'compilation error', 'cannot find symbol', 'compile failed') `
        -Reason 'Compilation failed in the test or build phase and points to a real code issue.' `
        -SuggestedNextAction 'open_repair_request' `
        -RetryPolicy 'manual_review_required'
} elseif ($normalizedText -match 'surefire|there are test failures|tests run:|failures:|errors:') {
    $result = New-Classification `
        -Category 'real_quality_or_security' `
        -Subcategory 'maven_surefire' `
        -Confidence 'high' `
        -EvidencePatterns @('surefire', 'there are test failures', 'failures:', 'errors:') `
        -Reason 'Test execution completed and reported real failures.' `
        -SuggestedNextAction 'open_repair_request' `
        -RetryPolicy 'manual_review_required'
} elseif ($normalizedText -match 'assert|expected:|but was:|comparisonfailure|test failed') {
    $result = New-Classification `
        -Category 'real_quality_or_security' `
        -Subcategory 'real_test_failure' `
        -Confidence 'high' `
        -EvidencePatterns @('assert', 'expected:', 'but was:', 'comparisonfailure', 'test failed') `
        -Reason 'Assertions failed with reproducible evidence.' `
        -SuggestedNextAction 'open_repair_request' `
        -RetryPolicy 'manual_review_required'
} elseif ($normalizedText -match 'unauthorized|forbidden|access denied|permission|security|tenant') {
    $result = New-Classification `
        -Category 'real_quality_or_security' `
        -Subcategory 'real_permission_or_security_failure' `
        -Confidence 'high' `
        -EvidencePatterns @('unauthorized', 'forbidden', 'access denied', 'permission', 'security', 'tenant') `
        -Reason 'Evidence points to a permission, tenant, or security boundary failure.' `
        -SuggestedNextAction 'mark_blocked' `
        -RetryPolicy 'manual_review_required'
} elseif ($ExitCode -ne 0) {
    $result = New-Classification `
        -Category 'real_quality_or_security' `
        -Subcategory 'real_build_failure' `
        -Confidence 'medium' `
        -EvidencePatterns @('failed', 'error', 'exception') `
        -Reason 'Command failed with non-zero exit code and no safer classification matched first.' `
        -SuggestedNextAction 'open_repair_request' `
        -RetryPolicy 'manual_review_required'
} else {
    $result = New-Classification `
        -Category 'unknown' `
        -Subcategory 'unknown' `
        -Confidence 'low' `
        -EvidencePatterns @('.') `
        -Reason 'No known rule matched the current evidence.' `
        -SuggestedNextAction 'collect_more_evidence' `
        -RetryPolicy 'manual_review_required'
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
