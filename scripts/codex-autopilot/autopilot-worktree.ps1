$ErrorActionPreference = 'Stop'
$nativeLibrary = Join-Path $PSScriptRoot 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $nativeLibrary)) { . $nativeLibrary }

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
  $ignored = Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('check-ignore','-q',$relativeRoot) -AcceptedExitCodes @(0,1)
  if ($ignored.exitCode -ne 0) { throw "worktree root is not git-ignored: $relativeRoot" }
  $slug = $IssueId.ToLowerInvariant()
  $path = Join-Path $rootFull $slug
  $branch = "codex/autopilot/$slug"
  if (Test-Path -LiteralPath $path) {
    $actualBranch = (Invoke-AutopilotGit -RepoRoot $path -Arguments @('branch','--show-current') -ThrowOnFailure).stdout.Trim()
    if ($actualBranch -ne $branch) { throw "worktree path belongs to another branch: $actualBranch" }
    $actualHead = (Invoke-AutopilotGit -RepoRoot $path -Arguments @('rev-parse','HEAD') -ThrowOnFailure).stdout.Trim()
    if ($actualHead -ne $BaseCommit) { throw "worktree HEAD does not match requested base: actual=$actualHead expected=$BaseCommit" }
    $changes = @(Get-AutopilotNativeOutputLines (Invoke-AutopilotGit -RepoRoot $path -Arguments @('status','--porcelain=v1','--untracked-files=all') -ThrowOnFailure).stdout)
    if ($changes.Count -gt 0 -and !$AllowDirtyReuse) { throw 'worktree contains residual changes and cannot be reused for a fresh implementation attempt' }
    return [pscustomobject]@{ path = $path; branch = $branch; baseCommit = $BaseCommit; reused = $true }
  }
  New-Item -ItemType Directory -Path $rootFull -Force | Out-Null
  Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments @('worktree','add','--quiet',$path,'-b',$branch,$BaseCommit) -ThrowOnFailure | Out-Null
  return [pscustomobject]@{ path = $path; branch = $branch; baseCommit = $BaseCommit; reused = $false }
}

function Get-AutopilotWorktreeChanges {
  param([Parameter(Mandatory)][string]$Worktree)
  $lines = Get-AutopilotNativeOutputLines (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('status','--porcelain=v1','--untracked-files=all') -ThrowOnFailure).stdout
  return @($lines | ForEach-Object {
    $raw = if ($_.Length -ge 4) { $_.Substring(3).Trim() } else { '' }
    if ($raw -match '\s+->\s+') { ($raw -split '\s+->\s+')[-1].Trim('"') } elseif ($raw) { $raw.Trim('"') }
  } | Where-Object { $_ } | Select-Object -Unique)
}

function Get-AutopilotIssueChanges {
  param(
    [Parameter(Mandatory)][string]$Worktree,
    [Parameter(Mandatory)][string]$BaseCommit
  )

  $committed = @(Get-AutopilotNativeOutputLines (Invoke-AutopilotGit -RepoRoot $Worktree -Arguments @('diff','--name-only',$BaseCommit,'HEAD','--') -ThrowOnFailure).stdout)
  return @($committed + @(Get-AutopilotWorktreeChanges -Worktree $Worktree) | Where-Object { $_ } | ForEach-Object { ConvertTo-AutopilotPath $_ } | Select-Object -Unique)
}
