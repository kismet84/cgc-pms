param(
    [string]$IssueId = 'READY-DRY-RUN',
    [ValidateSet('dry-run', 'classify', 'closeout', 'full')]
    [string]$Scenario = 'dry-run',
    [string]$ErrorText = '',
    [int]$ExitCode = 1,
    [string]$ReadyIssuePath,
    [string]$OutputPath,
    [switch]$DryRun,
    [switch]$EnableLocalCommit,
    [switch]$AllowSyntheticIssue
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "AUTOPILOT_POWERSHELL7_REQUIRED: launch this plugin entry with pwsh, actual=$($PSVersionTable.PSVersion)."
}

if ($EnableLocalCommit) {
    throw 'autopilot-loop-runner.ps1 is preview-only. Use scripts/codex-autopilot/autopilot-run-continuous.ps1 for real execution.'
}

$pluginRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $pluginRoot '..\..')).Path
$loopId = "LOOP-$([DateTimeOffset]::Now.ToString('yyyyMMddHHmmss'))"
$effectiveDryRun = $true
$phases = @(
    'select',
    'checkpoint',
    'plan handoff',
    'act handoff',
    'observe',
    'classify',
    'repair-request',
    'verify',
    'closeout',
    'learn',
    'next'
)

function New-PreviewClassification {
    param(
        [string]$Reason,
        [string]$NextAction = 'wait_for_owner_decision',
        [string]$Evidence = 'Preview only.'
    )

    $sha = [Security.Cryptography.SHA256]::Create()
    try { $fingerprint = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes("none|preview|$Reason")))).Replace('-', '').ToLowerInvariant() } finally { $sha.Dispose() }
    return [ordered]@{
        category = 'none'
        subcategory = 'preview'
        confidence = 'high'
        evidence = @($Evidence)
        suggestedNextAction = $NextAction
        retryPolicy = 'no_retry'
        reason = $Reason
        failureFingerprint = $fingerprint
    }
}

function Resolve-ReadyIssueSelection {
    param(
        [string]$RequestedIssueId,
        [string]$RequestedPath,
        [switch]$AllowSynthetic
    )

    if ($RequestedPath) {
        if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
            return [ordered]@{
                issueId = $RequestedIssueId
                selectedFrom = 'ready_issue_path'
                ready = $false
                gate = 'select'
                reason = "Ready issue path not found: $RequestedPath"
                readyIssuePath = $RequestedPath
            }
        }

        $content = Get-Content -Encoding UTF8 -LiteralPath $RequestedPath -Raw
        $looksReady = ($content -match '(?m)^#{2,3}\s+') -and ($content -match '验证命令：')
        $isDryRunMarker = ($RequestedIssueId -eq 'READY-DRY-RUN')
        $matchesIssue = $isDryRunMarker -or ($content -match [regex]::Escape($RequestedIssueId))
        if (-not $looksReady) {
            return [ordered]@{
                issueId = $RequestedIssueId
                selectedFrom = 'ready_issue_path'
                ready = $false
                gate = 'select'
                reason = 'Ready issue file exists but does not match the expected minimal shape.'
                readyIssuePath = $RequestedPath
            }
        }

        return [ordered]@{
            issueId = $RequestedIssueId
            selectedFrom = 'ready_issue_path'
            ready = $true
            gate = 'open'
            reason = if ($isDryRunMarker) { 'Dry-run route marker accepted.' } elseif ($matchesIssue) { 'Ready issue evidence found.' } else { 'Ready issue file exists; issue id mismatch left for owner review.' }
            readyIssuePath = $RequestedPath
        }
    }

    if ($AllowSynthetic) {
        return [ordered]@{
            issueId = $RequestedIssueId
            selectedFrom = 'synthetic_allowlisted'
            ready = $true
            gate = 'open'
            reason = 'Synthetic issue explicitly allowed for preview or test routing.'
            readyIssuePath = $null
        }
    }

    return [ordered]@{
        issueId = $RequestedIssueId
        selectedFrom = 'explicit_input_without_ready_evidence'
        ready = $false
        gate = 'select'
        reason = 'No ready issue evidence provided. Pass -ReadyIssuePath or explicitly opt in with -AllowSyntheticIssue.'
        readyIssuePath = $null
    }
}

function Get-ObservationPayload {
    param(
        [string]$CurrentScenario,
        [string]$CurrentErrorText,
        [int]$CurrentExitCode,
        [bool]$CurrentDryRun
    )

    if ($CurrentScenario -eq 'closeout') {
        return [ordered]@{
            scenario = $CurrentScenario
            errorText = ''
            exitCode = 0
            dryRun = $CurrentDryRun
        }
    }

    if ([string]::IsNullOrWhiteSpace($CurrentErrorText)) {
        return [ordered]@{
            scenario = $CurrentScenario
            errorText = ''
            exitCode = 0
            dryRun = $CurrentDryRun
        }
    }

    return [ordered]@{
        scenario = $CurrentScenario
        errorText = $CurrentErrorText
        exitCode = $CurrentExitCode
        dryRun = $CurrentDryRun
    }
}

$selectedIssue = Resolve-ReadyIssueSelection -RequestedIssueId $IssueId -RequestedPath $ReadyIssuePath -AllowSynthetic:$AllowSyntheticIssue
$checkpoint = $null
$planHandoff = $null
$actHandoff = $null
$observation = $null
$classification = New-PreviewClassification -Reason 'Preview only; no failure evidence supplied yet.' -Evidence 'No command failure evidence supplied.'
$repairRequest = $null
$closeoutPreview = $null
$verify = [ordered]@{
    requiredCommands = @(
        'pwsh -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\validate-loop-artifacts.ps1',
        'git diff --check'
    )
}
$learn = [ordered]@{
    reflectionRule = 'Only stable rule-level lessons belong in reflection; no run ids or raw logs.'
}
$gateDecision = 'needs_owner_decision'
$gateReason = $selectedIssue.reason
$nextAction = 'wait_for_owner_decision'

if ($selectedIssue.ready) {
    $checkpoint = & (Join-Path $PSScriptRoot 'autopilot-checkpoint.ps1') -RepoRoot $repoRoot -AsJson | ConvertFrom-Json
    if ($checkpoint.decision -in @('stop', 'pause', 'disabled')) {
        $classification = New-PreviewClassification `
            -Reason "Checkpoint gate short-circuited the loop: $($checkpoint.decision)." `
            -Evidence (($checkpoint.reasons -join '; '))
        $gateDecision = [string]$checkpoint.decision
        $gateReason = ($checkpoint.reasons -join '; ')
    } else {
        $planHandoff = [ordered]@{
            role = 'A'
            summary = "目标=$IssueId；范围=plugins/cgc-pms-autopilot；禁止=业务代码、push、生产动作"
        }

        $actHandoff = [ordered]@{
            role = 'C'
            summary = if ($effectiveDryRun) {
                '只做插件内脚本、schema、模板和示例变更；当前为 dry-run 兜底。'
            } else {
                '只做插件内脚本、schema、模板和示例变更；真实本地 commit 仍需显式 owner 决策。'
            }
        }

        $observation = Get-ObservationPayload `
            -CurrentScenario $Scenario `
            -CurrentErrorText $ErrorText `
            -CurrentExitCode $ExitCode `
            -CurrentDryRun $effectiveDryRun

        if ($Scenario -eq 'closeout') {
            $classification = New-PreviewClassification `
                -Reason 'Closeout preview uses prior acceptance evidence and does not classify a fresh failure.' `
                -Evidence 'Closeout preview; no failure classification executed.'
        } elseif (-not $observation.errorText) {
            $classification = New-PreviewClassification `
                -Reason 'No failure evidence supplied; classify phase remains a preview.' `
                -Evidence 'No ErrorText provided.'
        } else {
            $classification = & (Join-Path $PSScriptRoot 'test-failure-classifier.ps1') `
                -ErrorText $observation.errorText `
                -ExitCode $observation.exitCode | ConvertFrom-Json
        }

        if ($Scenario -ne 'closeout' -and $classification.category -notin @('none', 'unknown', 'tool_config')) {
            $repairVariables = [ordered]@{
                issue_id = $IssueId
                failed_check = 'autopilot-loop-runner classify'
                evidence = ($classification.evidence -join '; ')
                failure_category = "$($classification.category)/$($classification.subcategory)"
                required_change = $classification.suggestedNextAction
                allowed_files = 'plugins/cgc-pms-autopilot/**'
                forbidden_files = 'backend/**, frontend-admin/**, deploy/**'
                reverify_command = 'pwsh -NoProfile -ExecutionPolicy Bypass -File plugins\cgc-pms-autopilot\scripts\autopilot-loop-runner.ps1 -DryRun -Scenario classify -AllowSyntheticIssue'
                stop_condition = 'Stop when the same classified failure repeats after the allowed retry policy is exhausted.'
            } | ConvertTo-Json -Depth 5 -Compress

            $repairRequest = & (Join-Path $PSScriptRoot 'render-template.ps1') `
                -TemplatePath (Join-Path $pluginRoot 'templates\repair-request.md') `
                -VariablesJson $repairVariables
        }

        if ($Scenario -in @('closeout', 'full')) {
            $closeoutPreview = & (Join-Path $PSScriptRoot 'local-commit-closeout.ps1') `
                -IssueId $IssueId `
                -ExpectedPaths 'plugins/cgc-pms-autopilot' `
                -DryRun:$effectiveDryRun | ConvertFrom-Json
        }

        $gateDecision = switch ($Scenario) {
            'closeout' { 'wait_for_owner_decision' }
            default {
                switch ($classification.category) {
                    'real_quality_or_security' { 'open_repair_request' }
                    'environment_prereq' { 'refresh_then_retry' }
                    'ready_issue_config' { 'fix_ready_then_retry' }
                    'tool_config' { 'fix_tooling_before_retry' }
                    'none' { 'preview_only' }
                    default { 'manual_review' }
                }
            }
        }
        $gateReason = [string]$classification.reason
        $nextAction = if ($Scenario -eq 'closeout') { 'wait_for_owner_decision' } else { [string]$classification.suggestedNextAction }
    }
} else {
    $classification = New-PreviewClassification -Reason $selectedIssue.reason -Evidence 'Select gate blocked execution before checkpoint.'
    $nextAction = 'wait_for_owner_decision'
}

$reportVariables = [ordered]@{
    issue_id = $IssueId
    loop_id = $loopId
    scenario = $Scenario
    dry_run = $effectiveDryRun.ToString().ToLowerInvariant()
    phases = ($phases -join ' -> ')
    checkpoint_decision = if ($null -ne $checkpoint) { [string]$checkpoint.decision } else { 'skipped' }
    classification = "$($classification.category)/$($classification.subcategory)"
    next_action = $nextAction
    artifacts = if ($closeoutPreview) { 'loop-run-report preview, local-commit dry-run result' } elseif ($repairRequest) { 'classification result, repair-request preview' } else { 'selection/checkpoint preview only' }
    risks = if ($effectiveDryRun) { 'Dry-run remained enabled; no real commit or push can happen.' } else { 'Local commit path was enabled, but owner review is still required before any real write.' }
} | ConvertTo-Json -Depth 5 -Compress

$loopRunReport = & (Join-Path $PSScriptRoot 'render-template.ps1') `
    -TemplatePath (Join-Path $pluginRoot 'templates\loop-run-report.md') `
    -VariablesJson $reportVariables

$result = [ordered]@{
    loopId = $loopId
    issueId = $IssueId
    scenario = $Scenario
    dryRun = $effectiveDryRun
    localCommitRequested = [bool]$EnableLocalCommit
    readyIssuePath = $ReadyIssuePath
    allowSyntheticIssue = [bool]$AllowSyntheticIssue
    phases = $phases
    select = $selectedIssue
    checkpoint = $checkpoint
    planHandoff = $planHandoff
    actHandoff = $actHandoff
    observe = $observation
    classify = $classification
    repairRequestPreview = $repairRequest
    verify = $verify
    closeoutPreview = $closeoutPreview
    learn = $learn
    next = [ordered]@{
        action = $nextAction
        decision = $gateDecision
        reason = $gateReason
        stopPauseEnabled = if ($null -ne $checkpoint) {
            [ordered]@{
                stopFlag = [bool]$checkpoint.stopFlag
                pauseFlag = [bool]$checkpoint.pauseFlag
                enabledFlag = [bool]$checkpoint.enabledFlag
            }
        } else {
            $null
        }
    }
    loopRunReportPreview = $loopRunReport
}

$json = $result | ConvertTo-Json -Depth 8
if ($OutputPath) {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($OutputPath, $json, [System.Text.UTF8Encoding]::new($false))
}

$json
