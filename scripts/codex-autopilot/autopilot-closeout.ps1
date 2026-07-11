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

function Complete-AutopilotIssueCloseout {
  param([string]$RepoRoot, [string]$Worktree, [object]$Issue, [bool]$AutoMerge = $true)
  $archivePath = Join-Path $Worktree $Issue.archiveReport
  if (!(Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw "formal archive report is missing: $($Issue.archiveReport)" }
  $readyPath = Join-Path $Worktree 'docs\backlog\ready-issues.md'
  $donePath = Join-Path $Worktree 'docs\backlog\done-issues.md'
  $worktreeHead = (& git -C $Worktree rev-parse HEAD).Trim()
  & git -C $RepoRoot merge-base --is-ancestor $worktreeHead HEAD 2>$null
  if ($LASTEXITCODE -eq 0 -and (Get-Content -LiteralPath (Join-Path $RepoRoot 'docs\backlog\ready-issues.md') -Raw -Encoding UTF8) -match ('(?ms)^###\s+' + [regex]::Escape($Issue.title) + '.*?^状态：Done\s*$')) {
    return [pscustomobject]@{ commit = $worktreeHead; merged = $true; idempotent = $true }
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
  & git -C $Worktree commit -m "feat(autopilot): complete $($Issue.issueId.ToLowerInvariant())" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'closeout local commit failed' }
  $commit = (& git -C $Worktree rev-parse HEAD).Trim()
  $merged = $false
  if ($AutoMerge) {
    $mainChanges = @(& git -C $RepoRoot status --porcelain=v1)
    if ($mainChanges.Count -gt 0) { throw 'main worktree is dirty; local merge refused' }
    & git -C $RepoRoot merge --ff-only $commit | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'local fast-forward merge failed' }
    $merged = $true
  }
  return [pscustomobject]@{ commit = $commit; merged = $merged; idempotent = $false }
}
