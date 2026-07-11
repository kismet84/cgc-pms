$ErrorActionPreference = 'Stop'
$readyLibrary = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'autopilot-ready.ps1'
if (Test-Path -LiteralPath $readyLibrary) { . $readyLibrary }

function Test-AutopilotDomainContinuationAllowed {
  param([string[]]$RecentTitles, [string]$Domain, [string]$FocusText)
  $streak = 0
  foreach ($title in $RecentTitles) { if ($title -match [regex]::Escape($Domain)) { $streak++ } else { break } }
  if ($streak -ge 5) { return $FocusText -match '候选域对比' -and $FocusText -match '为何仍需继续当前域' -and $FocusText -match '为何其他候选域不应先做' }
  if ($streak -ge 3) { return $FocusText -match '候选域对比' }
  return $true
}

function Test-AutopilotReadyPlanningAllowed {
  param([string]$Action)
  return $Action -eq 'PLAN_READY'
}

function Get-AutopilotRefillDecision {
  param([Parameter(Mandatory)][string]$RepoRoot)
  $autoDir = Join-Path $RepoRoot '.codex-autopilot'
  $backlog = Join-Path $RepoRoot 'docs\backlog'
  if (Test-Path -LiteralPath (Join-Path $autoDir 'stop.flag')) { return [pscustomobject]@{ action = 'STOP'; targetReadyCount = 0; candidates = @(); reason = 'stop.flag present' } }
  if (Test-Path -LiteralPath (Join-Path $autoDir 'pause.flag')) { return [pscustomobject]@{ action = 'PAUSE'; targetReadyCount = 0; candidates = @(); reason = 'pause.flag present' } }
  $readyPath = Join-Path $backlog 'ready-issues.md'
  $readyText = if (Test-Path -LiteralPath $readyPath) { Get-Content -LiteralPath $readyPath -Raw -Encoding UTF8 } else { '' }
  $readyCount = [regex]::Matches($readyText, '(?m)^状态[：:]\s*Ready\s*$').Count
  if ($readyCount -ge 3) { return [pscustomobject]@{ action = 'READY_SUFFICIENT'; targetReadyCount = $readyCount; candidates = @(); reason = 'Ready queue already has at least three items' } }

  $focusPath = Join-Path $backlog 'current-focus.md'; $blockedPath = Join-Path $backlog 'blocked-issues.md'
  $focusText = if (Test-Path -LiteralPath $focusPath) { Get-Content -LiteralPath $focusPath -Raw -Encoding UTF8 } else { '' }
  $blockedText = if (Test-Path -LiteralPath $blockedPath) { Get-Content -LiteralPath $blockedPath -Raw -Encoding UTF8 } else { '' }
  $focusMatch = [regex]::Match($focusText, '(?im)当前\s*focus[：:]\s*([^\r\n]+)')
  if ($focusMatch.Success -and $blockedText -match [regex]::Escape($focusMatch.Groups[1].Value.Trim())) {
    return [pscustomobject]@{ action = 'UNBLOCK_FIRST'; targetReadyCount = [Math]::Min(5, [Math]::Max(3, $readyCount)); candidates = @([pscustomobject]@{ name = $focusMatch.Groups[1].Value.Trim(); status = 'BlockedPrerequisite'; source = 'blocked-issues.md' }); reason = 'current focus prerequisite is blocked' }
  }

  $adHocPath = Join-Path $backlog 'ad-hoc-plan.md'
  $candidates = @()
  if (Test-Path -LiteralPath $adHocPath) {
    $adHoc = Get-Content -LiteralPath $adHocPath -Raw -Encoding UTF8
    foreach ($match in [regex]::Matches($adHoc, '(?m)^\|\s*([^|]+?)\s*\|\s*高\s*\|\s*(ReadyToSplit|Candidate)\s*\|')) {
      $candidates += [pscustomobject]@{ name = $match.Groups[1].Value.Trim(); status = $match.Groups[2].Value; source = 'ad-hoc-plan.md' }
    }
    $candidates = @($candidates | Sort-Object @{Expression={ if ($_.status -eq 'ReadyToSplit') { 0 } else { 1 } }} | Select-Object -Unique name,status,source)
  }
  $needed = [Math]::Max(0, 3 - $readyCount)
  if ($candidates.Count -lt $needed) {
    $planPath = Join-Path $backlog 'cgc-pms-production-enhancement-plan.md'
    if (Test-Path -LiteralPath $planPath) {
      $planText = Get-Content -LiteralPath $planPath -Raw -Encoding UTF8
      foreach ($match in [regex]::Matches($planText, '(?m)^###\s+([0-9]+(?:\.[0-9]+)+)\s+(.+?)\s*$')) {
        $name = $match.Groups[2].Value.Trim()
        if ($candidates.name -notcontains $name) { $candidates += [pscustomobject]@{ name = $name; status = 'Candidate'; source = "long-term:$($match.Groups[1].Value)" } }
      }
    }
  }
  $selected = @($candidates | Select-Object -First ([Math]::Min($needed, 5 - $readyCount)))
  if ($selected.Count -eq 0) { return [pscustomobject]@{ action = 'NO_CANDIDATES'; targetReadyCount = $readyCount; candidates = @(); reason = 'no safe candidate is available for planner split' } }
  return [pscustomobject]@{ action = 'PLAN_READY'; targetReadyCount = $readyCount + $selected.Count; candidates = $selected; reason = 'fresh Planner must turn candidates into strict Ready contracts before implementation' }
}

function Import-AutopilotReadyPlan {
  param([string]$PlanPath, [string]$ReadyPath, [string]$RepoRoot)
  $plan = Get-Content -LiteralPath $PlanPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $blocks = @($plan.readyBlocks)
  if ($blocks.Count -lt 1 -or $blocks.Count -gt 5) { throw 'ready planner must return 1..5 blocks' }
  $existing = if (Test-Path -LiteralPath $ReadyPath) { Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8 } else { '# Ready Issues' }
  $tempPath = "$ReadyPath.$([guid]::NewGuid().ToString('N')).tmp"
  try {
    [IO.File]::WriteAllText($tempPath, ($existing.TrimEnd() + [Environment]::NewLine + [Environment]::NewLine + ($blocks -join ([Environment]::NewLine + [Environment]::NewLine))), [Text.UTF8Encoding]::new($false))
    $issues = @(Get-AutopilotReadyIssues -Path $tempPath -RepoRoot $RepoRoot)
    if ($issues.Count -gt 5) { throw 'refill would exceed five Ready Issues' }
    [IO.File]::Copy($tempPath, $ReadyPath, $true)
    return [pscustomobject]@{ createdCount = $blocks.Count; totalReadyCount = $issues.Count; issueIds = @($issues.issueId) }
  } finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
  }
}

function Invoke-AutopilotReadyPlanner {
  param([string]$RepoRoot, [object[]]$Candidates, [string]$OutputPath, [string]$SchemaPath, [string]$Model = 'gpt-5.6-sol', [string]$Thinking = 'high', [int]$TimeoutSeconds = 1200)
  $codex = if (Get-Command Resolve-AutopilotCodexCommand -ErrorAction SilentlyContinue) { Resolve-AutopilotCodexCommand } else {
    $command = Get-Command codex -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { $command.Source } else {
      $package = Get-AppxPackage -Name 'OpenAI.Codex' -ErrorAction SilentlyContinue
      if ($package) { Join-Path $package.InstallLocation 'app\resources\codex.exe' } else { throw 'Codex CLI command is unavailable' }
    }
  }
  $candidateJson = $Candidates | ConvertTo-Json -Depth 5 -Compress
  $prompt = @"
Act as the fresh AutoPilot Planner for cgc-pms. Read AGENTS.override.md, AGENTS.md, docs/backlog/current-focus.md,
docs/backlog/ready-issues.md, docs/backlog/blocked-issues.md, docs/backlog/ad-hoc-plan.md and the long-term plan.
Turn only these candidates into 1..5 strict Ready Issue Markdown blocks: $candidateJson
Each block must satisfy scripts/codex-autopilot/autopilot-ready.ps1, use real existing paths and validation entrypoints,
state explicit non-goals/migration/risk/runtime/reviewer requirements, and must not modify business code.
Return JSON matching $SchemaPath.
"@
  $args = @('exec','--ephemeral','--sandbox','danger-full-access','--model',$Model,'-c',"model_reasoning_effort=$Thinking",'--cd',$RepoRoot,'--output-schema',$SchemaPath,'--output-last-message',$OutputPath,'-')
  $startInfo = [Diagnostics.ProcessStartInfo]::new(); $startInfo.FileName = $codex
  $startInfo.Arguments = ($args | ForEach-Object { if ($_ -match '[\s"]') { '"' + $_.Replace('"','\"') + '"' } else { $_ } }) -join ' '
  $startInfo.WorkingDirectory = $RepoRoot; $startInfo.UseShellExecute = $false; $startInfo.RedirectStandardInput = $true; $startInfo.RedirectStandardOutput = $true; $startInfo.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $startInfo; [void]$process.Start()
  $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync(); $process.StandardInput.Write($prompt); $process.StandardInput.Close()
  if (!$process.WaitForExit($TimeoutSeconds * 1000)) { $process.Kill($true); throw 'Ready Planner timed out' }
  $process.WaitForExit(); $null = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
  if ($process.ExitCode -ne 0) { throw "Ready Planner failed: $stderr" }
  return Get-Content -LiteralPath $OutputPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
