param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'autopilot-test-fixture.ps1')
$autoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $autoRoot 'autopilot-issue-checkpoint.ps1')
. (Join-Path $autoRoot 'autopilot-transition.ps1')

$repo = New-AutopilotTestRepository -Name 'transition'
try {
  New-Item -ItemType Directory -Path (Join-Path $repo 'docs\backlog') -Force | Out-Null
  [IO.File]::WriteAllText((Join-Path $repo 'docs\backlog\ready-issues.md'), "# Ready Issues`n`n### ISSUE-040-001：transition writer`n`n状态：Ready`n", [Text.UTF8Encoding]::new($false))
  & git -C $repo add .
  & git -C $repo commit -qm 'base'
  $base = (& git -C $repo rev-parse HEAD).Trim()
  $autoDir = Join-Path $repo '.codex-autopilot'
  $issueId = 'ISSUE-040-001'
  $artifactDir = Join-Path $autoDir "runs\test\$issueId"
  New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
  New-AutopilotIssueCheckpoint -AutoDir $autoDir -IssueId $issueId -ReadyPath (Join-Path $repo 'docs\backlog\ready-issues.md') -BaseCommit $base -Worktree $repo -Branch 'test' -AllowedPaths @('docs/**') -ArtifactDirectory $artifactDir | Out-Null
  $path = Get-AutopilotIssueCheckpointPath -AutoDir $autoDir -IssueId $issueId
  $implementing = Move-AutopilotIssuePhase -Path $path -Phase IMPLEMENTING -IncrementDispatch implementation
  $implemented = Move-AutopilotIssuePhase -Path $path -Phase IMPLEMENTED -Evidence @{diffHash=('a' * 64)}
  if ($implemented.generation -le $implementing.generation -or !$implemented.transitionId -or $implemented.phase -ne 'IMPLEMENTED') { throw 'transition writer did not produce a readable monotonic transition' }
  $illegalRejected = $false
  try { Move-AutopilotIssuePhase -Path $path -Phase REGISTERED | Out-Null } catch { $illegalRejected = $true }
  if (!$illegalRejected) { throw 'transition writer accepted an illegal phase edge' }
  Write-Host 'transition writer self-test passed'
} finally {
  Remove-AutopilotTestRepository -Path $repo
}
