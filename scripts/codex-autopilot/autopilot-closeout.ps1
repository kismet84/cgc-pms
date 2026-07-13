$ErrorActionPreference = 'Stop'

function Set-AutopilotReadyDone {
  param([string]$ReadyPath, [string]$IssueTitle)
  $text = Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8
  $pattern = '(?ms)(^###\s+' + [regex]::Escape($IssueTitle) + '\s*\r?\n)(.*?)(?=^###\s+ISSUE-|\z)'
  $match = [regex]::Match($text, $pattern)
  if (!$match.Success) { throw "Ready block not found for closeout: $IssueTitle" }
  $body = $match.Groups[2].Value
  if ($body -notmatch '(?m)^状态[：:]\s*Ready\s*$') {
    if ($body -match '(?m)^状态[：:]\s*Done\s*$') { return $false }
    throw 'Ready block is not in Ready state'
  }
  $updatedBody = [regex]::Replace($body, '(?m)^状态[：:]\s*Ready\s*$', '状态：Done', 1)
  $updated = $text.Substring(0, $match.Index) + $match.Groups[1].Value + $updatedBody + $text.Substring($match.Index + $match.Length)
  [IO.File]::WriteAllText($ReadyPath, $updated, [Text.UTF8Encoding]::new($false))
  return $true
}

function Assert-AutopilotStockIssueClosed {
  param([string]$Worktree, [string]$ReadyPath, [string]$IssueTitle)
  $readyText = Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8
  $pattern = '(?ms)^###\s+' + [regex]::Escape($IssueTitle) + '\s*\r?\n(.*?)(?=^###\s+ISSUE-|\z)'
  $match = [regex]::Match($readyText, $pattern)
  if (!$match.Success) { throw "Ready block not found for stock issue closeout: $IssueTitle" }
  $marker = [regex]::Match($match.Groups[1].Value, '\[stock:([^\]]+)\]')
  if (!$marker.Success) { return }

  $issueKey = $marker.Groups[1].Value.Trim()
  $registryPath = Join-Path $Worktree 'docs\backlog\current-issues.json'
  if (!(Test-Path -LiteralPath $registryPath -PathType Leaf)) {
    throw "stock issue registry is missing during closeout: $issueKey"
  }
  $registry = Get-Content -LiteralPath $registryPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $current = @($registry.issues | Where-Object { [string]$_.issueKey -eq $issueKey })
  if ($current.Count -eq 0) { return }

  $eligibleStatuses = @('OPEN', 'OBSERVATION')
  $eligibleClassifications = @('STILL_APPLICABLE', 'NON_BLOCKING_OBSERVATION', 'OPERATIONAL_RISK')
  $stillEligible = @($current | Where-Object {
    -not [bool]$_.blocking -and
    $eligibleStatuses -contains ([string]$_.status).ToUpperInvariant() -and
    $eligibleClassifications -contains ([string]$_.classification).ToUpperInvariant()
  })
  if ($stillEligible.Count -gt 0) {
    throw "stock issue remains eligible after implementation; remove or formally reclassify it before closeout: $issueKey"
  }
}

function Complete-AutopilotIssueCloseout {
  param(
    [string]$RepoRoot,
    [string]$Worktree,
    [object]$Issue,
    [bool]$AutoMerge = $true,
    [string]$BaseBranch = 'master',
    [string]$ExpectedBaseCommit = '',
    [object]$ScoreEvidence = $null,
    [object]$ScoreShadowEvidence = $null,
    [object]$TaskScoringConfig = $null
  )
  $currentBranch = (& git -C $RepoRoot branch --show-current).Trim()
  if ($currentBranch -ne $BaseBranch) { throw "closeout requires base branch $BaseBranch, actual=$currentBranch" }
  $archivePath = Join-Path $Worktree $Issue.archiveReport
  if (!(Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw "formal archive report is missing: $($Issue.archiveReport)" }
  $readyPath = Join-Path $Worktree 'docs\backlog\ready-issues.md'
  $donePath = Join-Path $Worktree 'docs\backlog\done-issues.md'
  $worktreeHead = (& git -C $Worktree rev-parse HEAD).Trim()
  $scoringActive = $null -ne $TaskScoringConfig -and (Test-AutopilotTaskScoringActive $TaskScoringConfig)
  & git -C $RepoRoot merge-base --is-ancestor $worktreeHead HEAD 2>$null
  if ($LASTEXITCODE -eq 0 -and (Get-Content -LiteralPath (Join-Path $RepoRoot 'docs\backlog\ready-issues.md') -Raw -Encoding UTF8) -match ('(?ms)^###\s+' + [regex]::Escape($Issue.title) + '.*?^状态：Done\s*$')) {
    $score = if ($scoringActive) { Get-AutopilotTaskScoreFromReport -ReportPath $archivePath } else { $null }
    $implementationCommit = if ($score) { [string]$score.implementationCommit } elseif ($scoringActive) { (& git -C $Worktree rev-parse "$worktreeHead^" 2>$null | Select-Object -First 1).Trim() } else { $worktreeHead }
    $scoreShadow = if ($scoringActive) { Get-AutopilotTaskScoreFromReport -ReportPath $archivePath -Shadow } else { $null }
    return [pscustomobject]@{ commit = $worktreeHead; implementationCommit = $implementationCommit; closeoutCommit = $worktreeHead; merged = $true; idempotent = $true; score = $score; scoreShadow = $scoreShadow }
  }
  $existingSubject = (& git -C $Worktree log -1 --pretty=%s 2>$null | Select-Object -First 1).Trim()
  if ($scoringActive -and $existingSubject -eq "chore(autopilot): score and close $($Issue.issueId.ToLowerInvariant())") {
    $implementationCommit = (& git -C $Worktree rev-parse "$worktreeHead^" 2>$null | Select-Object -First 1).Trim()
    $score = Get-AutopilotTaskScoreFromReport -ReportPath $archivePath
    if ($null -eq $score -or [string]$score.implementationCommit -ne $implementationCommit) { throw 'existing closeout commit lacks its bound v1 task score' }
    $scoreShadow = Get-AutopilotTaskScoreFromReport -ReportPath $archivePath -Shadow
    $merged = $false
    if ($AutoMerge) {
      $mainChanges = @(& git -C $RepoRoot status --porcelain=v1)
      if ($mainChanges.Count -gt 0) { throw 'main worktree is dirty; local merge refused' }
      if ($ExpectedBaseCommit -and (& git -C $RepoRoot rev-parse HEAD).Trim() -ne $ExpectedBaseCommit) { throw 'base branch advanced before recovered merge; local merge refused' }
      & git -C $RepoRoot merge --ff-only $worktreeHead | Out-Null
      if ($LASTEXITCODE -ne 0) { throw 'recovered local fast-forward merge failed' }
      $merged = $true
    }
    return [pscustomobject]@{ commit=$worktreeHead; implementationCommit=$implementationCommit; closeoutCommit=$worktreeHead; merged=$merged; idempotent=$true; score=$score; scoreShadow=$scoreShadow }
  }
  if ($ExpectedBaseCommit -and (& git -C $RepoRoot rev-parse HEAD).Trim() -ne $ExpectedBaseCommit) { throw 'base branch advanced during Issue execution; closeout requires a fresh isolated retry' }
  Assert-AutopilotStockIssueClosed -Worktree $Worktree -ReadyPath $readyPath -IssueTitle $Issue.title
  $implementationCommit = $null
  $score = $null
  $scoreShadow = $null
  if ($scoringActive) {
    if ($null -eq $ScoreEvidence) { throw 'active task scoring requires deterministic ScoreEvidence' }
    $subject = (& git -C $Worktree log -1 --pretty=%s 2>$null | Select-Object -First 1).Trim()
    if ($subject -eq "feat(autopilot): implement $($Issue.issueId.ToLowerInvariant())") {
      $implementationCommit = (& git -C $Worktree rev-parse HEAD).Trim()
    } else {
      & git -C $Worktree diff --check
      if ($LASTEXITCODE -ne 0) { throw 'implementation commit failed git diff --check' }
      & git -C $Worktree add -A
      if ($LASTEXITCODE -ne 0) { throw 'implementation commit failed to stage changes' }
      & git -C $Worktree diff --cached --quiet
      if ($LASTEXITCODE -eq 0) {
        $implementationCommit = (& git -C $Worktree rev-parse HEAD).Trim()
        if ($implementationCommit -eq $ExpectedBaseCommit) { throw 'active scoring closeout has no implementation artifacts to commit' }
      } else {
        & git -C $Worktree commit -m "feat(autopilot): implement $($Issue.issueId.ToLowerInvariant())" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'implementation local commit failed' }
        $implementationCommit = (& git -C $Worktree rev-parse HEAD).Trim()
      }
    }
    Set-AutopilotScoreProperty $ScoreEvidence 'implementationCommit' $implementationCommit
    if ($null -ne $ScoreShadowEvidence) {
      Set-AutopilotScoreProperty $ScoreShadowEvidence 'implementationCommit' $implementationCommit
      $scoreShadow = New-AutopilotTaskScoreV2Shadow $ScoreShadowEvidence
      Add-AutopilotTaskScoreV2ShadowToReport -ReportPath $archivePath -Score $scoreShadow | Out-Null
    }
    $score = New-AutopilotTaskScore $ScoreEvidence
    Add-AutopilotTaskScoreToReport -ReportPath $archivePath -Score $score | Out-Null
  }
  Set-AutopilotReadyDone -ReadyPath $readyPath -IssueTitle $Issue.title | Out-Null
  if (!(Test-Path -LiteralPath $donePath)) { '# Done Issues' | Set-Content -LiteralPath $donePath -Encoding UTF8 }
  $doneText = Get-Content -LiteralPath $donePath -Raw -Encoding UTF8
  if ($doneText -notmatch [regex]::Escape($Issue.issueId)) {
    Add-Content -LiteralPath $donePath -Encoding UTF8 -Value "`r`n### $($Issue.title)`r`n`r`n状态：Done`r`n归档报告：``$($Issue.archiveReport)``"
  }
  & git -C $Worktree diff --check
  if ($LASTEXITCODE -ne 0) { throw 'closeout failed git diff --check' }
  & git -C $Worktree add -A
  if ($LASTEXITCODE -ne 0) { throw 'closeout failed to stage changes' }
  $commitMessage = if ($scoringActive) { "chore(autopilot): score and close $($Issue.issueId.ToLowerInvariant())" } else { "feat(autopilot): complete $($Issue.issueId.ToLowerInvariant())" }
  & git -C $Worktree commit -m $commitMessage | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'closeout local commit failed' }
  $commit = (& git -C $Worktree rev-parse HEAD).Trim()
  $merged = $false
  if ($AutoMerge) {
    $mainChanges = @(& git -C $RepoRoot status --porcelain=v1)
    if ($mainChanges.Count -gt 0) { throw 'main worktree is dirty; local merge refused' }
    if ($ExpectedBaseCommit -and (& git -C $RepoRoot rev-parse HEAD).Trim() -ne $ExpectedBaseCommit) { throw 'base branch advanced before merge; local merge refused' }
    & git -C $RepoRoot merge --ff-only $commit | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'local fast-forward merge failed' }
    $merged = $true
  }
  if (!$implementationCommit) { $implementationCommit = $commit }
  return [pscustomobject]@{ commit = $commit; implementationCommit = $implementationCommit; closeoutCommit = $commit; merged = $merged; idempotent = $false; score = $score; scoreShadow = $scoreShadow }
}

function Merge-AutopilotIssueCloseoutCommit {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$Commit,
    [string]$ExpectedBaseCommit = ''
  )
  & git -C $RepoRoot merge-base --is-ancestor $Commit HEAD 2>$null
  if ($LASTEXITCODE -eq 0) { return [pscustomobject]@{ merged=$true; idempotent=$true; commit=$Commit } }
  $mainChanges = @(& git -C $RepoRoot status --porcelain=v1)
  if ($mainChanges.Count -gt 0) { throw 'main worktree is dirty; local merge refused' }
  if ($ExpectedBaseCommit -and (& git -C $RepoRoot rev-parse HEAD).Trim() -ne $ExpectedBaseCommit) { throw 'base branch advanced before durable closeout merge; local merge refused' }
  & git -C $RepoRoot merge --ff-only $Commit | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'durable closeout fast-forward merge failed' }
  return [pscustomobject]@{ merged=$true; idempotent=$false; commit=$Commit }
}
