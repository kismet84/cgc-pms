param()

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir 'autopilot-native-command.ps1')

$root = Join-Path ([IO.Path]::GetTempPath()) ('autopilot-native-command-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
try {
  $fixture = Join-Path $root 'native-fixture.cmd'
  [IO.File]::WriteAllText($fixture, "@echo off`r`necho normal-output`r`necho LF will be replaced by CRLF 1>&2`r`nexit /b %1`r`n", [Text.ASCIIEncoding]::new())

  $warningSuccess = Invoke-AutopilotNativeCommand -FilePath $fixture -Arguments @('0') -AcceptedExitCodes @(0) -ThrowOnFailure
  if (!$warningSuccess.succeeded -or $warningSuccess.exitCode -ne 0 -or $warningSuccess.stderr -notmatch 'CRLF') { throw 'exitCode=0 stderr warning was not preserved as a successful diagnostic' }

  $failure = Invoke-AutopilotNativeCommand -FilePath $fixture -Arguments @('7') -AcceptedExitCodes @(0)
  if ($failure.succeeded -or $failure.exitCode -ne 7) { throw 'non-zero exit code was accepted unexpectedly' }

  $acceptedBusinessCode = Invoke-AutopilotNativeCommand -FilePath $fixture -Arguments @('1') -AcceptedExitCodes @(0,1) -ThrowOnFailure
  if (!$acceptedBusinessCode.succeeded -or $acceptedBusinessCode.exitCode -ne 1) { throw 'explicit business exit code was rejected' }

  $a = Join-Path $root 'a.txt'; $b = Join-Path $root 'b.txt'
  [IO.File]::WriteAllText($a, 'a', [Text.UTF8Encoding]::new($false)); [IO.File]::WriteAllText($b, 'b', [Text.UTF8Encoding]::new($false))
  $diff = Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments @('diff','--no-index','--',$a,$b) -AcceptedExitCodes @(0,1) -ThrowOnFailure
  if ($diff.exitCode -ne 1 -or !$diff.succeeded) { throw 'git diff --no-index difference was not treated as a valid result' }

  $warningRepo = Join-Path $root 'warning-repo'
  New-Item -ItemType Directory -Path $warningRepo -Force | Out-Null
  Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments @('-C',$warningRepo,'init','-q') -ThrowOnFailure | Out-Null
  Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments @('-C',$warningRepo,'config','--local','user.email','autopilot@test.local') -ThrowOnFailure | Out-Null
  Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments @('-C',$warningRepo,'config','--local','user.name','AutoPilot Test') -ThrowOnFailure | Out-Null
  Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments @('-C',$warningRepo,'config','--local','core.autocrlf','true') -ThrowOnFailure | Out-Null
  Invoke-AutopilotNativeCommand -FilePath 'git' -Arguments @('-C',$warningRepo,'config','--local','core.safecrlf','warn') -ThrowOnFailure | Out-Null
  [IO.File]::WriteAllText((Join-Path $warningRepo 'lf-only.txt'), "line-one`nline-two`n", [Text.UTF8Encoding]::new($false))
  $script:AutopilotCommitAuthorized = $false
  $indexBefore = (& git -C $warningRepo diff --cached --name-only) -join "`n"
  $addRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('add','lf-only.txt') -ThrowOnFailure | Out-Null } catch { $addRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_COMMIT_AUTHORIZATION_REQUIRED' }
  $indexAfter = (& git -C $warningRepo diff --cached --name-only) -join "`n"
  if (!$addRejected -or $indexAfter -ne $indexBefore) { throw 'unauthorized Git add changed the index' }

  $script:AutopilotCommitAuthorized = $true
  $gitWarning = Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('add','lf-only.txt') -ThrowOnFailure
  if (!$gitWarning.succeeded -or $gitWarning.exitCode -ne 0) { throw 'Git CRLF warning fixture was not classified as success' }
  if ($gitWarning.stderr -notmatch '(?i)LF will be replaced by CRLF|CRLF will be replaced by LF') { throw 'Git CRLF warning fixture did not produce the expected diagnostic' }
  Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('commit','-m','base') -ThrowOnFailure | Out-Null
  $baseHead = (& git -C $warningRepo rev-parse HEAD).Trim()

  'next' | Add-Content -LiteralPath (Join-Path $warningRepo 'lf-only.txt') -Encoding UTF8
  & git -C $warningRepo add lf-only.txt
  $script:AutopilotCommitAuthorized = $false
  $commitRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('commit','-m','unauthorized') -ThrowOnFailure | Out-Null } catch { $commitRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_COMMIT_AUTHORIZATION_REQUIRED' }
  if (!$commitRejected -or (& git -C $warningRepo rev-parse HEAD).Trim() -ne $baseHead) { throw 'unauthorized Git commit changed HEAD' }

  $globalOptionRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('-C',$warningRepo,'commit','-m','global-option-bypass') -ThrowOnFailure | Out-Null } catch { $globalOptionRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_GLOBAL_OPTION_FORBIDDEN' }
  if (!$globalOptionRejected -or (& git -C $warningRepo rev-parse HEAD).Trim() -ne $baseHead) { throw 'Git global option bypass changed HEAD' }

  $indexBeforeReset = (& git -C $warningRepo diff --cached --name-only) -join "`n"
  $resetRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('reset','--hard','HEAD') -ThrowOnFailure | Out-Null } catch { $resetRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_COMMAND_NOT_ALLOWED' }
  if (!$resetRejected -or (& git -C $warningRepo rev-parse HEAD).Trim() -ne $baseHead -or ((& git -C $warningRepo diff --cached --name-only) -join "`n") -ne $indexBeforeReset) { throw 'unsupported Git reset changed HEAD or index' }
  & git -C $warningRepo reset --hard -q HEAD

  $updateRefRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('update-ref','refs/heads/codex/unauthorized-update-ref',$baseHead) -ThrowOnFailure | Out-Null } catch { $updateRefRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_COMMAND_NOT_ALLOWED' }
  & git -C $warningRepo show-ref --verify --quiet refs/heads/codex/unauthorized-update-ref
  if (!$updateRefRejected -or $LASTEXITCODE -eq 0) { throw 'unsupported Git update-ref changed refs' }

  $tagRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('tag','unauthorized-tag',$baseHead) -ThrowOnFailure | Out-Null } catch { $tagRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_COMMAND_NOT_ALLOWED' }
  & git -C $warningRepo show-ref --verify --quiet refs/tags/unauthorized-tag
  if (!$tagRejected -or $LASTEXITCODE -eq 0) { throw 'unsupported Git tag changed refs' }

  $script:AutopilotBranchAuthorized = $false
  $branchRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('branch','codex/unauthorized') -ThrowOnFailure | Out-Null } catch { $branchRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_BRANCH_AUTHORIZATION_REQUIRED' }
  & git -C $warningRepo show-ref --verify --quiet refs/heads/codex/unauthorized
  if (!$branchRejected -or $LASTEXITCODE -eq 0) { throw 'unauthorized Git branch changed refs' }
  $unauthorizedWorktree = Join-Path $warningRepo 'unauthorized-worktree'
  foreach ($mutationCase in @(
    [pscustomobject]@{ arguments=[string[]]@('worktree','add','--quiet',$unauthorizedWorktree,'-b','codex/unauthorized-worktree',$baseHead) },
    [pscustomobject]@{ arguments=[string[]]@('-c','core.longpaths=true','worktree','remove','--force',$unauthorizedWorktree) }
  )) {
    $arguments = [string[]]$mutationCase.arguments
    $worktreeMutationRejected = $false
    try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments $arguments -ThrowOnFailure | Out-Null } catch { $worktreeMutationRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_BRANCH_AUTHORIZATION_REQUIRED' }
    if (!$worktreeMutationRejected) { throw "unauthorized Git worktree mutation was accepted: $($arguments -join ' ')" }
  }
  if (Test-Path -LiteralPath $unauthorizedWorktree) { throw 'unauthorized Git worktree add changed filesystem or refs' }
  & git -C $warningRepo branch fixture/delete
  $deleteRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('branch','-D','fixture/delete') -ThrowOnFailure | Out-Null } catch { $deleteRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_BRANCH_AUTHORIZATION_REQUIRED' }
  & git -C $warningRepo show-ref --verify --quiet refs/heads/fixture/delete
  if (!$deleteRejected -or $LASTEXITCODE -ne 0) { throw 'unauthorized Git branch delete changed refs' }

  & git -C $warningRepo branch fixture/merge
  & git -C $warningRepo switch -q fixture/merge
  'feature' | Add-Content -LiteralPath (Join-Path $warningRepo 'lf-only.txt') -Encoding UTF8
  & git -C $warningRepo add lf-only.txt
  & git -C $warningRepo commit -qm 'feature'
  & git -C $warningRepo switch -q -
  $script:AutopilotMergeAuthorized = $false
  $mergeRejected = $false
  try { Invoke-AutopilotGit -RepoRoot $warningRepo -Arguments @('merge','--ff-only','fixture/merge') -ThrowOnFailure | Out-Null } catch { $mergeRejected = $_.Exception.Message -match 'AUTOPILOT_GIT_MERGE_AUTHORIZATION_REQUIRED' }
  if (!$mergeRejected -or (& git -C $warningRepo rev-parse HEAD).Trim() -ne $baseHead) { throw 'unauthorized Git merge changed HEAD' }

  $repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
  $hooks = Get-Content -Raw -LiteralPath (Join-Path $repoRoot '.codex\hooks.json') | ConvertFrom-Json
  $preToolHooks = @($hooks.hooks.PreToolUse | Where-Object { $_.matcher -eq 'Bash' })
  if ($preToolHooks.Count -ne 1 -or $preToolHooks[0].hooks[0].commandWindows -notmatch 'pre-tool-use-command-guard\.ps1') {
    throw 'PowerShell command guard is not registered exactly once for PreToolUse/Bash'
  }
  $guard = Join-Path $repoRoot '.codex\hooks\pre-tool-use-command-guard.ps1'
  function Invoke-CommandGuard([string]$Command) {
    $payload = @{ hook_event_name='PreToolUse'; tool_name='Bash'; cwd=$root; tool_input=@{ command=$Command } } | ConvertTo-Json -Depth 4 -Compress
    $output = $payload | & pwsh -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $guard
    if ($LASTEXITCODE -ne 0) { throw "command guard failed with exit=$LASTEXITCODE" }
    if (!$output) { return $null }
    return $output | ConvertFrom-Json
  }
  function Assert-CommandDenied([string]$Command, [string]$Reason) {
    $result = Invoke-CommandGuard $Command
    if ($result.hookSpecificOutput.permissionDecision -ne 'deny' -or $result.hookSpecificOutput.permissionDecisionReason -notmatch $Reason) {
      throw "command guard did not deny expected case: $Reason"
    }
  }

  Assert-CommandDenied '$items=@("x"); foreach($item in $items){$item} | ConvertTo-Json' 'COMMAND_GUARD_POWERSHELL_PARSE_ERROR'
  Assert-CommandDenied "Get-Content -LiteralPath 'missing-test-file.java'" 'COMMAND_GUARD_LITERAL_PATH_NOT_FOUND'
  Assert-CommandDenied @'
git grep -n -F -e "``" -- AGENTS.md
'@ 'COMMAND_GUARD_SEARCH_LITERAL_REQUIRES_SINGLE_QUOTES'
  Assert-CommandDenied "Get-Item -LiteralPath AGENTS.md; git grep -n -F -e 'AGENTS' -- AGENTS.md" 'COMMAND_GUARD_SEARCH_MUST_BE_ISOLATED'
  if (Invoke-CommandGuard '$results = foreach($item in @("x")){$item}; $results | ConvertTo-Json') { throw 'valid collect-then-serialize command was denied' }
  if (Invoke-CommandGuard "Get-Content -LiteralPath '$fixture'") { throw 'existing literal path was denied' }
  $validBacktickSearch = @'
git grep -n -F -e '`' -- AGENTS.md
'@
  if (Invoke-CommandGuard $validBacktickSearch) { throw 'single-quoted backtick search was denied' }

  Write-Host 'native command semantics self-test passed'
} finally {
  Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
