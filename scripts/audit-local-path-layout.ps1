[CmdletBinding()]
param(
    [string]$RepoRoot = (Join-Path $PSScriptRoot '..'),
    [string]$DriveRoot,
    [ValidateRange(0, 4)]
    [int]$MaxDepth = 4,
    [switch]$AsJson,
    [switch]$Strict,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$statusRank = @{
    COMPLIANT        = 0
    LEGACY_REVIEW    = 1
    EVIDENCE_REQUIRED = 2
    FORBIDDEN_NEW    = 3
}
$protectedNames = @(
    '.omc', '.omo', '.opencode', '.claude', '.mimocode', 'graphify-out', '.sisyphus', '.archive',
    '.git', 'node_modules', 'target', 'dist', 'build', 'coverage', 'test-results', 'playwright-report', '.pnpm-store'
)
$allowedRepoRootEntries = @(
    '.git', '.github', '.agents', '.codex', '.githooks', '.worktrees', '.agent-runtime', '.codex-autopilot', '.codegraph',
    'local-evidence', 'backend', 'frontend-admin-v2', 'desktop-launcher', 'packages', 'deploy', 'scripts', 'tools',
    'plugins', 'docs', 'archive', '.gitattributes', '.gitignore', 'AGENTS.md', 'CHANGELOG.md', 'README.md', 'skills-lock.json'
)
$legacyRepoRootEntries = @('design-qa.md', 'output', 'memory', 'mobile', 'frontend-admin')

function Get-NormalPath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetPathRoot($full)
    if ([string]::Equals($full, $root, [StringComparison]::OrdinalIgnoreCase)) { return $root }
    return $full.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
}

function Test-PathUnder([string]$Path, [string]$Root) {
    $pathValue = (Get-NormalPath $Path) + [IO.Path]::DirectorySeparatorChar
    $rootValue = (Get-NormalPath $Root) + [IO.Path]::DirectorySeparatorChar
    return $pathValue.StartsWith($rootValue, [StringComparison]::OrdinalIgnoreCase)
}

function Test-SamePath([string]$Left, [string]$Right) {
    return [string]::Equals((Get-NormalPath $Left), (Get-NormalPath $Right), [StringComparison]::OrdinalIgnoreCase)
}

function Test-PathAtOrUnder([string]$Path, [string]$Root) {
    return (Test-SamePath $Path $Root) -or (Test-PathUnder $Path $Root)
}

function Invoke-Git([string]$At, [string[]]$Arguments, [switch]$AllowFailure) {
    $output = @(& git -C $At @Arguments 2>$null)
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $AllowFailure) {
        throw "git -C '$At' $($Arguments -join ' ') failed with exit code $code"
    }
    return [pscustomobject]@{ Code = $code; Output = @($output) }
}

function Get-RepositoryIdentity([string]$Path) {
    $origin = Invoke-Git $Path @('config', '--get', 'remote.origin.url') -AllowFailure
    if ($origin.Code -eq 0 -and $origin.Output.Count -gt 0) {
        $value = [string]$origin.Output[0]
        if ($value -match '^(?:[A-Za-z]:[\\/]|\\\\|/)') {
            return (Get-NormalPath $value).ToLowerInvariant()
        }
        return $value.TrimEnd('/').ToLowerInvariant()
    }
    return (Get-NormalPath $Path).ToLowerInvariant()
}

function Get-GitHealth([string]$Path, [switch]$AllowNoUpstream) {
    $reasons = [Collections.Generic.List[string]]::new()
    $status = Invoke-Git $Path @('status', '--porcelain=v1', '--untracked-files=normal') -AllowFailure
    if ($status.Code -ne 0) {
        $reasons.Add('Git snapshot unavailable.')
        return [pscustomobject]@{ NeedsEvidence = $true; Reasons = @($reasons) }
    }
    if ($status.Output.Count -gt 0) { $reasons.Add('Worktree is dirty.') }

    $branch = Invoke-Git $Path @('symbolic-ref', '-q', 'HEAD') -AllowFailure
    if ($branch.Code -ne 0) { $reasons.Add('HEAD is detached.') }

    $ahead = Invoke-Git $Path @('rev-list', '--count', '@{upstream}..HEAD') -AllowFailure
    if ($ahead.Code -ne 0 -and -not $AllowNoUpstream) {
        $reasons.Add('Upstream is unavailable; unpushed commits cannot be ruled out.')
    } elseif ($ahead.Output.Count -gt 0 -and [int]$ahead.Output[0] -gt 0) {
        $reasons.Add("HEAD has $($ahead.Output[0]) commit(s) not absorbed by upstream.")
    }
    return [pscustomobject]@{ NeedsEvidence = ($reasons.Count -gt 0); Reasons = @($reasons) }
}

function Get-RegisteredWorktrees([string]$Path) {
    $result = Invoke-Git $Path @('worktree', 'list', '--porcelain')
    $paths = [Collections.Generic.List[string]]::new()
    foreach ($line in $result.Output) {
        if ($line -like 'worktree *') { $paths.Add((Get-NormalPath $line.Substring(9))) }
    }
    return @($paths)
}

function Test-ProtectedRelativePath([string]$RelativePath) {
    $parts = @($RelativePath -split '[\\/]')
    foreach ($part in $parts) {
        if ($protectedNames -contains $part) { return $true }
    }
    return $RelativePath -match '(?i)(^|[\\/])archive[\\/]v1\.0[\\/]private($|[\\/])'
}

function Get-ReparsePointInPath([string]$Path) {
    $full = Get-NormalPath $Path
    $root = [IO.Path]::GetPathRoot($full)
    $relative = $full.Substring($root.Length).TrimStart([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $current = $root
    foreach ($segment in @($relative -split '[\\/]' | Where-Object { $_ })) {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current)) { break }
        $item = Get-Item -LiteralPath $current -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { return (Get-NormalPath $current) }
    }
    return $null
}

function Get-TrackedAbsolutePaths([string]$Path) {
    $extensions = @('.ps1', '.psm1', '.psd1', '.cmd', '.bat', '.sh', '.py', '.js', '.mjs', '.cjs', '.ts', '.tsx')
    $files = Invoke-Git $Path @('ls-files', '-z')
    $joined = $files.Output -join "`n"
    foreach ($relative in @($joined -split "`0")) {
        if ([string]::IsNullOrWhiteSpace($relative) -or (Test-ProtectedRelativePath $relative)) { continue }
        if ($extensions -notcontains [IO.Path]::GetExtension($relative).ToLowerInvariant()) { continue }
        $full = Join-Path $Path $relative
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { continue }
        $lineNumber = 0
        foreach ($line in [IO.File]::ReadLines($full)) {
            $lineNumber++
            if ($line -match '(?i)(?<![A-Za-z0-9])D:\\(?:[^\s"''`]*cgc-pms[^\s"''`]*)') {
                [pscustomobject]@{ Path = $full; Line = $lineNumber; Text = $Matches[0] }
            }
        }
    }
}

function Invoke-PathAudit([string]$Repository, [string]$ScanRoot, [int]$DepthLimit) {
    $repo = Get-NormalPath $Repository
    $drive = Get-NormalPath $ScanRoot
    if (-not (Test-Path -LiteralPath $repo -PathType Container)) { throw "RepoRoot not found: $repo" }
    if (-not (Test-Path -LiteralPath $drive -PathType Container)) { throw "DriveRoot not found: $drive" }
    foreach ($root in @($repo, $drive)) {
        if (Test-ProtectedRelativePath $root) { throw "Protected audit root is forbidden: $root" }
        $reparsePoint = Get-ReparsePointInPath $root
        if ($reparsePoint) { throw "Reparse-point audit root is forbidden: $root (ancestor: $reparsePoint)" }
    }
    if ((Invoke-Git $repo @('rev-parse', '--is-inside-work-tree') -AllowFailure).Code -ne 0) {
        throw "RepoRoot is not a Git worktree: $repo"
    }

    $repoParent = Split-Path $repo -Parent
    $standardCloneRoot = Join-Path $repoParent '_clones\cgc-pms'
    $standardWorktreeRoot = Join-Path $repoParent '_worktrees\cgc-pms'
    $autoPilotRoot = Join-Path $repo '.worktrees\autopilot'
    $controlledRepoRoots = @((Join-Path $repo 'deploy\backup'))
    $standardDataRoots = @(
        (Join-Path $drive 'cache\cgc-pms'),
        (Join-Path $drive 'backups\cgc-pms'),
        (Join-Path $drive 'var\log\cgc-pms'),
        (Join-Path $drive 'var\lib\cgc-pms')
    )
    $standardContainerRoots = @(
        $standardCloneRoot,
        $standardWorktreeRoot,
        (Join-Path $drive 'cache'),
        (Join-Path $drive 'backups'),
        (Join-Path $drive 'var'),
        (Join-Path $drive 'var\log'),
        (Join-Path $drive 'var\lib')
    )
    $repoIdentity = Get-RepositoryIdentity $repo
    $registered = @(Get-RegisteredWorktrees $repo)
    $registeredSet = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($path in $registered) { [void]$registeredSet.Add((Get-NormalPath $path)) }

    $findings = @{}
    function Add-Finding([string]$Target, [string]$Kind, [string]$Status, [string]$Reason) {
        $key = Get-NormalPath $Target
        if (-not $findings.ContainsKey($key)) {
            $findings[$key] = [pscustomobject]@{
                Path = $key
                Kinds = [Collections.Generic.List[string]]::new()
                Status = $Status
                Reasons = [Collections.Generic.List[string]]::new()
            }
        }
        $item = $findings[$key]
        if (-not $item.Kinds.Contains($Kind)) { $item.Kinds.Add($Kind) }
        if (-not $item.Reasons.Contains($Reason)) { $item.Reasons.Add($Reason) }
        if ($statusRank[$Status] -gt $statusRank[$item.Status]) { $item.Status = $Status }
    }

    foreach ($entry in @(Get-ChildItem -LiteralPath $repo -Force | Sort-Object Name)) {
        $isProtected = $protectedNames -contains $entry.Name
        $isReparsePoint = ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
        if ($allowedRepoRootEntries -contains $entry.Name) {
            Add-Finding $entry.FullName 'REPO_ROOT_ENTRY' 'COMPLIANT' 'Repository root entry is allowlisted.'
        } elseif ($legacyRepoRootEntries -contains $entry.Name) {
            Add-Finding $entry.FullName 'REPO_ROOT_ENTRY' 'LEGACY_REVIEW' 'Known legacy repository root entry requires explicit review.'
        } else {
            Add-Finding $entry.FullName 'REPO_ROOT_ENTRY' 'FORBIDDEN_NEW' 'Unknown repository root entry is forbidden.'
        }
        if ($isProtected) {
            Add-Finding $entry.FullName 'PROTECTED_ROOT' 'COMPLIANT' 'Protected repository root entry recorded and skipped; contents were not traversed by root-entry audit.'
        }
        if ($isReparsePoint) {
            Add-Finding $entry.FullName 'REPARSE_POINT' 'EVIDENCE_REQUIRED' 'Repository root reparse point recorded but not followed.'
        }
    }

    foreach ($worktree in $registered) {
        $underAutoPilot = Test-PathAtOrUnder $worktree $autoPilotRoot
        $isStandard = (Test-SamePath $worktree $repo) -or (Test-PathAtOrUnder $worktree $standardWorktreeRoot) -or $underAutoPilot
        if (Test-SamePath $worktree $repo) {
            Add-Finding $worktree 'REGISTERED_WORKTREE' 'COMPLIANT' 'Canonical primary clone is the audit anchor, not a cleanup candidate.'
            continue
        }
        $health = Get-GitHealth $worktree -AllowNoUpstream:$underAutoPilot
        if ($health.NeedsEvidence) {
            Add-Finding $worktree 'REGISTERED_WORKTREE' 'EVIDENCE_REQUIRED' ($health.Reasons -join ' ')
        } elseif ($isStandard) {
            Add-Finding $worktree 'REGISTERED_WORKTREE' 'COMPLIANT' 'Registered worktree is in an approved root.'
        } else {
            Add-Finding $worktree 'REGISTERED_WORKTREE' 'LEGACY_REVIEW' 'Clean registered worktree is outside approved roots.'
        }
    }

    foreach ($dataRoot in $standardDataRoots) {
        if (Test-Path -LiteralPath $dataRoot -PathType Container) {
            Add-Finding $dataRoot 'STANDARD_DATA_ROOT' 'COMPLIANT' 'Approved cache, backup, or var root.'
        }
    }

    $queue = [Collections.Generic.Queue[object]]::new()
    $queue.Enqueue([pscustomobject]@{ Path = $drive; Depth = 0 })
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        if ($current.Depth -ge $DepthLimit) { continue }
        $children = @(Get-ChildItem -LiteralPath $current.Path -Directory -Force -ErrorAction SilentlyContinue | Sort-Object Name)
        foreach ($child in $children) {
            $relative = [IO.Path]::GetRelativePath($drive, $child.FullName)
            if (Test-ProtectedRelativePath $relative) {
                if ($child.Name -ieq '.archive') {
                    Add-Finding $child.FullName 'PROTECTED_ROOT' 'LEGACY_REVIEW' 'Protected .archive root recorded and skipped; contents were not traversed.'
                }
                continue
            }
            $nextDepth = $current.Depth + 1
            $attributes = $child.Attributes
            if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                Add-Finding $child.FullName 'REPARSE_POINT' 'EVIDENCE_REQUIRED' 'Reparse point recorded but not followed.'
                continue
            }

            $full = Get-NormalPath $child.FullName
            $name = $child.Name.ToLowerInvariant()
            $hasGit = (Test-Path -LiteralPath (Join-Path $full '.git'))
            $isRegistered = $registeredSet.Contains($full)
            $underStandardClone = Test-PathAtOrUnder $full $standardCloneRoot
            $underStandardWorktree = Test-PathAtOrUnder $full $standardWorktreeRoot
            $underAutoPilot = Test-PathAtOrUnder $full $autoPilotRoot
            $isControlledRepoRoot = $false
            foreach ($controlledRoot in $controlledRepoRoots) {
                if (Test-PathAtOrUnder $full $controlledRoot) { $isControlledRepoRoot = $true; break }
            }
            $isStandardData = $false
            foreach ($dataRoot in $standardDataRoots) {
                if ((Test-SamePath $full $dataRoot) -or (Test-PathUnder $full $dataRoot)) { $isStandardData = $true; break }
            }
            $isStandardContainer = $false
            foreach ($containerRoot in $standardContainerRoots) {
                if (Test-SamePath $full $containerRoot) { $isStandardContainer = $true; break }
            }
            $partialCandidate = -not $hasGit -and (
                ($underStandardClone -and -not (Test-SamePath $full $standardCloneRoot)) -or
                ($underStandardWorktree -and -not (Test-SamePath $full $standardWorktreeRoot)) -or
                ($underAutoPilot -and -not (Test-SamePath $full $autoPilotRoot))
            )

            if ($hasGit -and -not $isRegistered) {
                $identity = Get-RepositoryIdentity $full
                if ($identity -eq $repoIdentity -or $identity -eq (Get-NormalPath $repo).ToLowerInvariant()) {
                    $health = Get-GitHealth $full
                    if ($health.NeedsEvidence) {
                        Add-Finding $full 'SAME_SOURCE_CLONE' 'EVIDENCE_REQUIRED' ($health.Reasons -join ' ')
                    } elseif ($underStandardClone) {
                        Add-Finding $full 'SAME_SOURCE_CLONE' 'COMPLIANT' 'Clean same-source clone is in approved _clones root.'
                    } else {
                        Add-Finding $full 'SAME_SOURCE_CLONE' 'LEGACY_REVIEW' 'Clean same-source clone is identifiable but outside approved _clones root.'
                    }
                }
            }

            if ($partialCandidate) {
                Add-Finding $full 'PARTIAL_COPY' 'EVIDENCE_REQUIRED' 'Approved clone or worktree root contains a directory without a usable Git snapshot.'
            }

            $namedCopy = $name -like '*cgc-pms*'
            $insideRepo = Test-PathUnder $full $repo
            $normalRepoModule = $insideRepo -and $name -notmatch 'backup|bak|copy'
            if ($namedCopy -and -not $hasGit -and -not $partialCandidate -and -not $isStandardData -and -not $isStandardContainer -and -not $isControlledRepoRoot -and -not $normalRepoModule) {
                if ((Test-PathUnder $full $repo) -and $name -match 'backup|bak|copy') {
                    Add-Finding $full 'IN_REPO_BACKUP' 'FORBIDDEN_NEW' 'Repository-internal backup or copy has no approved governance identity.'
                } else {
                    Add-Finding $full 'NAMED_COPY' 'EVIDENCE_REQUIRED' 'Named copy is outside approved roots; age and ownership require evidence.'
                }
            }

            $hasCgcAncestor = $relative -match '(?i)(^|[\\/])[^\\/]*cgc-pms[^\\/]*($|[\\/])'
            if (($name -match '^(?:cache|caches|backup|backups|clone|clones)$') -and ($insideRepo -or $hasCgcAncestor) -and -not $isStandardData -and -not $isStandardContainer -and -not $isControlledRepoRoot -and -not $underStandardClone -and -not $underStandardWorktree -and -not $underAutoPilot) {
                if (Test-PathUnder $full $repo) {
                    Add-Finding $full 'UNREGISTERED_STORAGE_ROOT' 'FORBIDDEN_NEW' 'Unapproved cache, backup, or clone root exists inside repository.'
                } else {
                    Add-Finding $full 'UNREGISTERED_STORAGE_ROOT' 'EVIDENCE_REQUIRED' 'Unapproved storage root requires ownership and retention evidence.'
                }
            }

            $insideBackupRoot = $relative -match '(?i)(^|[\\/])backups?($|[\\/])'
            if ($insideBackupRoot -and -not $insideRepo -and -not $isStandardData) {
                $legacyBackupFile = Get-ChildItem -LiteralPath $full -File -Force -Filter 'cgc_pms*' -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($legacyBackupFile) {
                    Add-Finding $full 'LEGACY_BACKUP_CONTENT' 'LEGACY_REVIEW' "Non-standard backup root contains cgc_pms* content ($($legacyBackupFile.Name))."
                }
            }

            $crossesGitBoundary = $hasGit -and -not (Test-SamePath $full $repo)
            $crossesNamedCopyBoundary = $namedCopy -and -not $insideRepo -and -not $isStandardContainer -and -not $underStandardClone -and -not $underStandardWorktree -and -not $underAutoPilot
            if ($nextDepth -lt $DepthLimit -and -not $crossesGitBoundary -and -not $crossesNamedCopyBoundary -and -not $partialCandidate) {
                $queue.Enqueue([pscustomobject]@{ Path = $full; Depth = $nextDepth })
            }
        }
    }

    foreach ($managedRoot in @($standardCloneRoot, $standardWorktreeRoot, $autoPilotRoot)) {
        if (-not (Test-Path -LiteralPath $managedRoot -PathType Container)) { continue }
        $managedRootReparse = Get-ReparsePointInPath $managedRoot
        if ($managedRootReparse) {
            Add-Finding $managedRootReparse 'REPARSE_POINT' 'EVIDENCE_REQUIRED' 'Managed root or its ancestor is a reparse point; recorded but not followed.'
            continue
        }
        foreach ($child in @(Get-ChildItem -LiteralPath $managedRoot -Directory -Force -ErrorAction SilentlyContinue)) {
            $relative = [IO.Path]::GetRelativePath($drive, $child.FullName)
            if (Test-ProtectedRelativePath $relative) { continue }
            if (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                Add-Finding $child.FullName 'REPARSE_POINT' 'EVIDENCE_REQUIRED' 'Reparse point recorded but not followed.'
                continue
            }
            if (-not (Test-Path -LiteralPath (Join-Path $child.FullName '.git'))) {
                Add-Finding $child.FullName 'PARTIAL_COPY' 'EVIDENCE_REQUIRED' 'Approved clone or worktree root contains a directory without a usable Git snapshot.'
            }
        }
    }

    foreach ($match in @(Get-TrackedAbsolutePaths $repo)) {
        $literal = $match.Text.TrimEnd('.', ',', ';', ':', ')', ']', '}')
        $approvedLiteral = (Test-PathAtOrUnder $literal $repo) -or
            (Test-PathAtOrUnder $literal $standardCloneRoot) -or
            (Test-PathAtOrUnder $literal $standardWorktreeRoot)
        foreach ($dataRoot in $standardDataRoots) {
            if (Test-PathAtOrUnder $literal $dataRoot) { $approvedLiteral = $true; break }
        }
        if ($approvedLiteral) {
            Add-Finding $match.Path 'TRACKED_ABSOLUTE_D_PATH' 'COMPLIANT' "Tracked cgc-pms D:\ path at line $($match.Line) points to an approved root."
        } else {
            Add-Finding $match.Path 'TRACKED_ABSOLUTE_D_PATH' 'LEGACY_REVIEW' "Tracked cgc-pms D:\ path at line $($match.Line) is outside approved roots."
        }
    }

    $ordered = @($findings.Values | Sort-Object Path | ForEach-Object {
        [pscustomobject][ordered]@{
            path = $_.Path
            kinds = @($_.Kinds | Sort-Object)
            status = $_.Status
            reasons = @($_.Reasons | Sort-Object)
        }
    })
    $overall = 'COMPLIANT'
    foreach ($finding in $ordered) {
        if ($statusRank[$finding.status] -gt $statusRank[$overall]) { $overall = $finding.status }
    }
    return [pscustomobject][ordered]@{
        status = $overall
        repoRoot = $repo
        driveRoot = $drive
        maxDepth = $DepthLimit
        findings = $ordered
    }
}

function Assert-SelfTest([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "SelfTest failed: $Message" }
}

function Invoke-SelfTest {
    $testRoot = Join-Path ([IO.Path]::GetTempPath()) ("cgc-pms-path-audit-" + [Guid]::NewGuid().ToString('N'))
    try {
        $drive = Join-Path $testRoot 'drive'
        $projects = Join-Path $drive 'projects-test'
        $repo = Join-Path $projects 'cgc-pms'
        New-Item -ItemType Directory -Path $repo -Force | Out-Null
        & git -C $repo init -q
        & git -C $repo config user.email 'selftest@example.invalid'
        & git -C $repo config user.name 'Path Audit SelfTest'
        Set-Content -LiteralPath (Join-Path $repo 'README.txt') -Value 'selftest'
        Set-Content -LiteralPath (Join-Path $repo 'README.md') -Value 'allowlisted'
        & git -C $repo add README.txt
        & git -C $repo commit -q -m 'selftest base'

        $legacyRootEntries = @(
            (Join-Path $repo 'design-qa.md'),
            (Join-Path $repo 'output'),
            (Join-Path $repo 'memory'),
            (Join-Path $repo 'mobile'),
            (Join-Path $repo 'frontend-admin')
        )
        Set-Content -LiteralPath $legacyRootEntries[0] -Value 'legacy'
        New-Item -ItemType Directory -Path $legacyRootEntries[1..4] -Force | Out-Null
        $unknownRootEntry = Join-Path $repo 'unexpected-root'
        New-Item -ItemType Directory -Path $unknownRootEntry -Force | Out-Null

        $cloneRoot = Join-Path $projects '_clones\cgc-pms'
        New-Item -ItemType Directory -Path $cloneRoot -Force | Out-Null
        $standardClone = Join-Path $cloneRoot 'cgc-pms-clean'
        & git clone -q $repo $standardClone

        $externalWorktree = Join-Path $drive 'external-cgc-pms-worktree'
        & git -C $repo worktree add -q -b selftest-external $externalWorktree
        $legacyClone = Join-Path $drive 'legacy-cgc-pms-clone'
        & git clone -q $repo $legacyClone
        $autoPilotWorktree = Join-Path $repo '.worktrees\autopilot\issue-001'
        New-Item -ItemType Directory -Path (Split-Path $autoPilotWorktree -Parent) -Force | Out-Null
        & git -C $repo worktree add -q -b selftest-autopilot $autoPilotWorktree
        $partialClone = Join-Path $cloneRoot 'failed-20260809-010000'
        $partialWorktree = Join-Path $projects '_worktrees\cgc-pms\failed-20260809-010000'
        $partialAutoPilot = Join-Path $repo '.worktrees\autopilot\issue-002'
        New-Item -ItemType Directory -Path $partialClone, $partialWorktree, $partialAutoPilot -Force | Out-Null

        $inRepoBackup = Join-Path $repo 'backups\cgc-pms-copy'
        New-Item -ItemType Directory -Path $inRepoBackup -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $inRepoBackup 'partial.txt') -Value 'partial'
        $rootScatter = Join-Path $drive 'cgc-pms-scattered'
        New-Item -ItemType Directory -Path $rootScatter -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $rootScatter 'partial.txt') -Value 'partial'
        $unrelatedBackup = Join-Path $drive 'unrelated-app\backup'
        New-Item -ItemType Directory -Path $unrelatedBackup -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $unrelatedBackup 'other_app.sql') -Value 'unrelated'
        $legacyBackup = Join-Path $drive 'backups\mysql'
        New-Item -ItemType Directory -Path $legacyBackup -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $legacyBackup 'cgc_pms_20260809.sql.gz') -Value 'legacy'
        $repoNamedModule = Join-Path $repo 'plugins\cgc-pms-autopilot'
        New-Item -ItemType Directory -Path $repoNamedModule -Force | Out-Null

        $protectedCopy = Join-Path $drive '.archive\cgc-pms-hidden'
        New-Item -ItemType Directory -Path $protectedCopy -Force | Out-Null
        $nestedProtectedCopy = Join-Path $drive 'ordinary\.claude\cgc-pms-hidden'
        New-Item -ItemType Directory -Path $nestedProtectedCopy -Force | Out-Null
        $junction = Join-Path $drive 'linked-cgc-pms'
        $junctionCreated = $false
        try {
            New-Item -ItemType Junction -Path $junction -Target $inRepoBackup -ErrorAction Stop | Out-Null
            $junctionCreated = $true
        } catch {
            Write-Verbose "Junction test skipped: $($_.Exception.Message)"
        }
        if ($junctionCreated) {
            $repoJunctionTarget = Join-Path $testRoot 'repo-junction-target'
            New-Item -ItemType Directory -Path (Join-Path $repoJunctionTarget 'hidden-child') -Force | Out-Null
            $repoJunction = Join-Path $repo 'linked-root'
            New-Item -ItemType Junction -Path $repoJunction -Target $repoJunctionTarget | Out-Null
        }

        $result = Invoke-PathAudit $repo $drive 4
        $byPath = @{}
        foreach ($finding in $result.findings) { $byPath[$finding.path] = $finding }
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $repo)) -and $byPath[(Get-NormalPath $repo)].status -eq 'COMPLIANT') 'canonical RepoRoot must remain compliant'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $standardClone)) -and $byPath[(Get-NormalPath $standardClone)].status -eq 'COMPLIANT') 'standard clone must be compliant'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $externalWorktree)) -and $byPath[(Get-NormalPath $externalWorktree)].status -eq 'EVIDENCE_REQUIRED') 'external worktree without upstream must require evidence'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $legacyClone)) -and $byPath[(Get-NormalPath $legacyClone)].status -eq 'LEGACY_REVIEW') 'clean same-source clone outside approved root must be legacy review'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $autoPilotWorktree)) -and $byPath[(Get-NormalPath $autoPilotWorktree)].status -eq 'COMPLIANT') 'AutoPilot worktree exception must be compliant'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $inRepoBackup)) -and $byPath[(Get-NormalPath $inRepoBackup)].status -eq 'FORBIDDEN_NEW') 'in-repository backup must be forbidden for new use'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $rootScatter)) -and $byPath[(Get-NormalPath $rootScatter)].status -eq 'EVIDENCE_REQUIRED') 'drive-root partial copy must require evidence'
        Assert-SelfTest (-not $byPath.ContainsKey((Get-NormalPath $unrelatedBackup))) 'unrelated backup directory must be ignored'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $legacyBackup)) -and $byPath[(Get-NormalPath $legacyBackup)].status -eq 'LEGACY_REVIEW' -and ($byPath[(Get-NormalPath $legacyBackup)].kinds -contains 'LEGACY_BACKUP_CONTENT')) 'non-standard backup containing cgc_pms files must be legacy review'
        Assert-SelfTest (-not $byPath.ContainsKey((Get-NormalPath $repoNamedModule))) 'repository cgc-pms module must not be treated as a copy'
        $protectedRoot = Split-Path $protectedCopy -Parent
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $protectedRoot)) -and $byPath[(Get-NormalPath $protectedRoot)].status -eq 'LEGACY_REVIEW' -and ($byPath[(Get-NormalPath $protectedRoot)].kinds -contains 'PROTECTED_ROOT')) '.archive root must be recorded as legacy review'
        Assert-SelfTest (-not $byPath.ContainsKey((Get-NormalPath $protectedCopy))) 'protected directory must not be traversed'
        Assert-SelfTest (-not $byPath.ContainsKey((Get-NormalPath $nestedProtectedCopy))) 'nested protected directory must not be traversed'
        if ($junctionCreated) {
            Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $junction)) -and ($byPath[(Get-NormalPath $junction)].kinds -contains 'REPARSE_POINT')) 'junction must be recorded without traversal'
        }
        foreach ($partial in @($partialClone, $partialWorktree, $partialAutoPilot)) {
            Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $partial)) -and $byPath[(Get-NormalPath $partial)].status -eq 'EVIDENCE_REQUIRED' -and ($byPath[(Get-NormalPath $partial)].kinds -contains 'PARTIAL_COPY')) "partial clone/worktree must require evidence: $partial"
        }
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath (Join-Path $repo 'README.md'))) -and $byPath[(Get-NormalPath (Join-Path $repo 'README.md'))].status -eq 'COMPLIANT') 'allowlisted repository root entry must be compliant'
        foreach ($legacyRootEntry in $legacyRootEntries) {
            Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $legacyRootEntry)) -and $byPath[(Get-NormalPath $legacyRootEntry)].status -eq 'LEGACY_REVIEW') "known legacy repository root entry must require review: $legacyRootEntry"
        }
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $unknownRootEntry)) -and $byPath[(Get-NormalPath $unknownRootEntry)].status -eq 'FORBIDDEN_NEW') 'unknown repository root entry must be forbidden'
        $gitRoot = Join-Path $repo '.git'
        Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $gitRoot)) -and $byPath[(Get-NormalPath $gitRoot)].status -eq 'COMPLIANT' -and ($byPath[(Get-NormalPath $gitRoot)].kinds -contains 'PROTECTED_ROOT')) 'protected repository root entry must be recorded and skipped'
        if ($junctionCreated) {
            Assert-SelfTest ($byPath.ContainsKey((Get-NormalPath $repoJunction)) -and ($byPath[(Get-NormalPath $repoJunction)].kinds -contains 'REPARSE_POINT')) 'repository root junction must be recorded'
            Assert-SelfTest (-not $byPath.ContainsKey((Get-NormalPath (Join-Path $repoJunction 'hidden-child')))) 'repository root junction contents must not be followed'
        }

        $protectedRootRejected = $false
        try { Invoke-PathAudit $repo (Split-Path $protectedCopy -Parent) 1 | Out-Null } catch { $protectedRootRejected = $_.Exception.Message -like 'Protected audit root is forbidden:*' }
        Assert-SelfTest $protectedRootRejected 'protected DriveRoot must be rejected before traversal'
        $protectedRepoRejected = $false
        try { Invoke-PathAudit $protectedCopy $drive 1 | Out-Null } catch { $protectedRepoRejected = $_.Exception.Message -like 'Protected audit root is forbidden:*' }
        Assert-SelfTest $protectedRepoRejected 'protected RepoRoot must be rejected before Git inspection'
        if ($junctionCreated) {
            $junctionRootRejected = $false
            try { Invoke-PathAudit $repo $junction 1 | Out-Null } catch { $junctionRootRejected = $_.Exception.Message -like 'Reparse-point audit root is forbidden:*' }
            Assert-SelfTest $junctionRootRejected 'junction DriveRoot must be rejected before traversal'

            $linkedProjects = Join-Path $drive 'linked-projects'
            New-Item -ItemType Junction -Path $linkedProjects -Target $projects | Out-Null
            $linkedRepoRejected = $false
            try { Invoke-PathAudit (Join-Path $linkedProjects 'cgc-pms') $drive 1 | Out-Null } catch { $linkedRepoRejected = $_.Exception.Message -like 'Reparse-point audit root is forbidden:*' }
            Assert-SelfTest $linkedRepoRejected 'RepoRoot below a junction ancestor must be rejected before Git inspection'

            $worktreeParent = Join-Path $projects '_worktrees'
            Assert-SelfTest (Test-PathUnder $worktreeParent $testRoot) 'managed-root fixture must stay under SelfTest root'
            Remove-Item -LiteralPath $worktreeParent -Recurse -Force
            $managedTarget = Join-Path $testRoot 'managed-worktrees-target'
            $hiddenPartial = Join-Path $managedTarget 'cgc-pms\hidden-partial'
            New-Item -ItemType Directory -Path $hiddenPartial -Force | Out-Null
            New-Item -ItemType Junction -Path $worktreeParent -Target $managedTarget | Out-Null
            $junctionManagedAudit = Invoke-PathAudit $repo $drive 4
            $junctionManagedByPath = @{}
            foreach ($finding in $junctionManagedAudit.findings) { $junctionManagedByPath[$finding.path] = $finding }
            Assert-SelfTest ($junctionManagedByPath.ContainsKey((Get-NormalPath $worktreeParent)) -and ($junctionManagedByPath[(Get-NormalPath $worktreeParent)].kinds -contains 'REPARSE_POINT')) 'managed-root junction must be recorded'
            Assert-SelfTest (-not $junctionManagedByPath.ContainsKey((Get-NormalPath (Join-Path $worktreeParent 'cgc-pms\hidden-partial')))) 'managed-root junction contents must not be followed'
        }

        $json = $result | ConvertTo-Json -Depth 8 -Compress
        $parsed = $json | ConvertFrom-Json
        Assert-SelfTest ($parsed.status -eq 'FORBIDDEN_NEW' -and $parsed.findings.Count -gt 0) 'JSON output must preserve status and findings'

        $pwsh = (Get-Process -Id $PID).Path
        & $pwsh -NoProfile -File $PSCommandPath -RepoRoot $repo -DriveRoot $drive -MaxDepth 4 -AsJson *> $null
        Assert-SelfTest ($LASTEXITCODE -eq 0) 'default mode must not fail on legacy findings'
        & $pwsh -NoProfile -File $PSCommandPath -RepoRoot $repo -DriveRoot $drive -MaxDepth 4 -AsJson -Strict *> $null
        Assert-SelfTest ($LASTEXITCODE -eq 2) 'Strict mode must exit 2 for any non-compliant finding'

        [pscustomobject][ordered]@{ status = 'PASS'; tests = $(if ($junctionCreated) { 37 } else { 29 }); junctionTested = $junctionCreated }
    } finally {
        $systemTemp = Get-NormalPath ([IO.Path]::GetTempPath())
        if ((Test-Path -LiteralPath $testRoot) -and (Test-PathUnder $testRoot $systemTemp)) {
            Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

try {
    if ($SelfTest) {
        $selfTestResult = Invoke-SelfTest
        if ($AsJson) { $selfTestResult | ConvertTo-Json -Compress } else { "SELFTEST PASS ($($selfTestResult.tests) checks; junction=$($selfTestResult.junctionTested))" }
        exit 0
    }

    $resolvedRepo = Get-NormalPath $RepoRoot
    if ([string]::IsNullOrWhiteSpace($DriveRoot)) { $DriveRoot = [IO.Path]::GetPathRoot($resolvedRepo) }
    $audit = Invoke-PathAudit $resolvedRepo $DriveRoot $MaxDepth
    if ($AsJson) {
        $audit | ConvertTo-Json -Depth 8
    } else {
        "STATUS $($audit.status)"
        "REPO   $($audit.repoRoot)"
        "SCAN   $($audit.driveRoot) (depth <= $($audit.maxDepth))"
        foreach ($finding in $audit.findings) {
            "[$($finding.status)] $($finding.path)"
            "  kind: $($finding.kinds -join ', ')"
            "  reason: $($finding.reasons -join ' ')"
        }
    }
    if ($Strict -and $audit.status -ne 'COMPLIANT') { exit 2 }
    exit 0
} catch {
    if ($AsJson) {
        [pscustomobject][ordered]@{ status = 'ERROR'; message = $_.Exception.Message } | ConvertTo-Json -Compress
    } else {
        [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    }
    exit 1
}
