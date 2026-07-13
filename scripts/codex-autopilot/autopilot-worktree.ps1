$ErrorActionPreference = 'Stop'

function ConvertTo-AutopilotPath {
  param([string]$Path)
  return ($Path -replace '\\','/').TrimStart('./')
}

function Test-AutopilotPathPattern {
  param([string]$Path, [string]$Pattern)
  $normalizedPath = ConvertTo-AutopilotPath $Path
  $normalizedPattern = ConvertTo-AutopilotPath $Pattern
  return [System.Management.Automation.WildcardPattern]::new($normalizedPattern, [System.Management.Automation.WildcardOptions]::IgnoreCase).IsMatch($normalizedPath)
}

function Assert-AutopilotAllowedChanges {
  param([string[]]$ChangedPaths, [string[]]$AllowedPaths, [string[]]$ForbiddenPaths = @())
  foreach ($path in @($ChangedPaths)) {
    if (@($ForbiddenPaths | Where-Object { Test-AutopilotPathPattern $path $_ }).Count -gt 0) { throw "forbidden changed path: $path" }
    if (@($AllowedPaths | Where-Object { Test-AutopilotPathPattern $path $_ }).Count -eq 0) { throw "changed path outside allowlist: $path" }
  }
  return $true
}

function New-AutopilotIssueWorktree {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$IssueId,
    [Parameter(Mandatory)][string]$BaseCommit,
    [string]$WorktreeRoot = '',
    [switch]$AllowDirtyReuse
  )
  if ($IssueId -notmatch '^ISSUE-[0-9-]+$') { throw "invalid Issue ID for worktree: $IssueId" }
  if (!$WorktreeRoot) { $WorktreeRoot = Join-Path $RepoRoot '.worktrees\autopilot' }
  $repoFull = [System.IO.Path]::GetFullPath($RepoRoot).TrimEnd('\')
  $rootFull = [System.IO.Path]::GetFullPath($WorktreeRoot).TrimEnd('\')
  if (!$rootFull.StartsWith($repoFull + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'worktree root must stay inside repository' }
  $relativeRoot = $rootFull.Substring($repoFull.Length + 1).Replace('\','/')
  & git -C $RepoRoot check-ignore -q $relativeRoot
  if ($LASTEXITCODE -ne 0) { throw "worktree root is not git-ignored: $relativeRoot" }
  $slug = $IssueId.ToLowerInvariant()
  $path = Join-Path $rootFull $slug
  $branch = "codex/autopilot/$slug"
  if (Test-Path -LiteralPath $path) {
    $actualBranch = (& git -C $path branch --show-current 2>$null | Select-Object -First 1).Trim()
    if ($actualBranch -ne $branch) { throw "worktree path belongs to another branch: $actualBranch" }
    $actualHead = (& git -C $path rev-parse HEAD 2>$null | Select-Object -First 1).Trim()
    if ($actualHead -ne $BaseCommit) { throw "worktree HEAD does not match requested base: actual=$actualHead expected=$BaseCommit" }
    $changes = @(& git -C $path status --porcelain=v1 --untracked-files=all 2>$null)
    if ($changes.Count -gt 0 -and !$AllowDirtyReuse) { throw 'worktree contains residual changes and cannot be reused for a fresh implementation attempt' }
    return [pscustomobject]@{ path = $path; branch = $branch; baseCommit = $BaseCommit; reused = $true }
  }
  New-Item -ItemType Directory -Path $rootFull -Force | Out-Null
  & git -C $RepoRoot worktree add --quiet $path -b $branch $BaseCommit | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "git worktree add failed for $IssueId" }
  return [pscustomobject]@{ path = $path; branch = $branch; baseCommit = $BaseCommit; reused = $false }
}

function Get-AutopilotWorktreeChanges {
  param([Parameter(Mandatory)][string]$Worktree)
  return @(& git -C $Worktree status --porcelain=v1 --untracked-files=all | ForEach-Object {
    $raw = if ($_.Length -ge 4) { $_.Substring(3).Trim() } else { '' }
    if ($raw -match '\s+->\s+') { ($raw -split '\s+->\s+')[-1].Trim('"') } elseif ($raw) { $raw.Trim('"') }
  } | Where-Object { $_ } | Select-Object -Unique)
}
