$ErrorActionPreference = 'Stop'

$nativeCommandLibrary = Join-Path $PSScriptRoot 'autopilot-native-command.ps1'
if (!(Get-Command Invoke-AutopilotGit -ErrorAction SilentlyContinue)) { . $nativeCommandLibrary }
$reportProjectionLibrary = Join-Path $PSScriptRoot 'autopilot-report-projection.ps1'
if (!(Get-Command New-AutopilotPreCloseoutFacts -ErrorAction SilentlyContinue)) { . $reportProjectionLibrary }

function Invoke-AutopilotCloseoutGit {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string[]]$Arguments,
    [int[]]$AcceptedExitCodes = @(0),
    [switch]$Mutating
  )
  if ($Mutating -and (Get-Command Assert-CurrentControlPlaneFence -ErrorAction SilentlyContinue)) {
    Assert-CurrentControlPlaneFence | Out-Null
  }
  return Invoke-AutopilotGit -RepoRoot $RepoRoot -Arguments $Arguments -AcceptedExitCodes $AcceptedExitCodes -ThrowOnFailure
}

function Get-AutopilotCloseoutGitText {
  param([Parameter(Mandatory)][object]$Result)
  return ([string]$Result.stdout).Trim()
}

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

function Test-AutopilotReadyDone {
  param([string]$ReadyPath, [string]$IssueTitle)
  $text = Get-Content -LiteralPath $ReadyPath -Raw -Encoding UTF8
  $pattern = '(?ms)^###\s+' + [regex]::Escape($IssueTitle) + '\s*\r?\n(?<body>.*?)(?=^###\s+ISSUE-|\z)'
  $match = [regex]::Match($text, $pattern)
  if (!$match.Success) { return $false }
  return $match.Groups['body'].Value -match '(?m)^状态[：:]\s*Done\s*$'
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

function Assert-AutopilotImplementationCloseoutArtifacts {
  param([Parameter(Mandatory)][string]$Worktree, [Parameter(Mandatory)][object]$Issue)
  $archivePath = Join-Path $Worktree $Issue.archiveReport
  if (!(Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw "formal archive report is missing: $($Issue.archiveReport)" }
  $report = Get-Content -LiteralPath $archivePath -Raw -Encoding UTF8
  foreach ($field in @('新增后续项','关闭后续项','后续项净变化')) {
    if ($report -notmatch ('(?m)(?:本轮)?' + [regex]::Escape($field) + '[：:]')) { throw "formal archive report is missing $field" }
  }
  Assert-AutopilotStockIssueClosed -Worktree $Worktree -ReadyPath (Join-Path $Worktree 'docs\backlog\ready-issues.md') -IssueTitle $Issue.title
  return $true
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
    [object]$TaskScoringConfig = $null,
    [object]$CloseoutFactsInput = $null
  )
  $currentBranch = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('branch','--show-current'))
  if ($currentBranch -ne $BaseBranch) { throw "closeout requires base branch $BaseBranch, actual=$currentBranch" }
  $archivePath = Join-Path $Worktree $Issue.archiveReport
  if (!(Test-Path -LiteralPath $archivePath -PathType Leaf)) { throw "formal archive report is missing: $($Issue.archiveReport)" }
  $readyPath = Join-Path $Worktree 'docs\backlog\ready-issues.md'
  $donePath = Join-Path $Worktree 'docs\backlog\done-issues.md'
  $worktreeHead = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD'))
  $scoringActive = $null -ne $TaskScoringConfig -and (Test-AutopilotTaskScoringActive $TaskScoringConfig)
  $activeScoringVersion = if ($scoringActive) { [string](Get-AutopilotScoreProperty $TaskScoringConfig 'activeVersion' '') } else { '' }
  $ancestorResult = Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('merge-base','--is-ancestor',$worktreeHead,'HEAD') -AcceptedExitCodes @(0,1)
  if ($ancestorResult.exitCode -eq 0 -and (Test-AutopilotReadyDone -ReadyPath (Join-Path $RepoRoot 'docs\backlog\ready-issues.md') -IssueTitle $Issue.title)) {
    $score = if ($scoringActive) { Get-AutopilotTaskScoreFromReport -ReportPath $archivePath } else { $null }
    $implementationCommit = if ($score) { [string]$score.implementationCommit } else { Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('rev-parse',"$worktreeHead^")) }
    $scoreShadow = if ($scoringActive) { Get-AutopilotTaskScoreFromReport -ReportPath $archivePath -Shadow } else { $null }
    return [pscustomobject]@{ commit = $worktreeHead; implementationCommit = $implementationCommit; closeoutCommit = $worktreeHead; merged = $true; idempotent = $true; score = $score; scoreShadow = $scoreShadow; preCloseoutFacts=$null; projection=$null }
  }
  $existingSubject = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('log','-1','--pretty=%s'))
  $expectedCloseoutSubject = if ($scoringActive) { "chore(autopilot): score and close $($Issue.issueId.ToLowerInvariant())" } else { "chore(autopilot): close $($Issue.issueId.ToLowerInvariant())" }
  if ($existingSubject -eq $expectedCloseoutSubject) {
    $implementationCommit = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('rev-parse',"$worktreeHead^"))
    $score = if ($scoringActive) { Get-AutopilotTaskScoreFromReport -ReportPath $archivePath } else { $null }
    if ($scoringActive -and ($null -eq $score -or [string]$score.implementationCommit -ne $implementationCommit -or [string]$score.scoringVersion -ne $activeScoringVersion)) { throw 'existing closeout commit lacks its bound active task score' }
    $scoreShadow = Get-AutopilotTaskScoreFromReport -ReportPath $archivePath -Shadow
    $merged = $false
    if ($AutoMerge) {
      $mainChanges = @(Get-AutopilotNativeOutputLines (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('status','--porcelain=v1')).stdout)
      if ($mainChanges.Count -gt 0) { throw 'main worktree is dirty; local merge refused' }
      if ($ExpectedBaseCommit -and (Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD'))) -ne $ExpectedBaseCommit) { throw 'base branch advanced before recovered merge; local merge refused' }
      Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('merge','--ff-only',$worktreeHead) -Mutating | Out-Null
      $merged = $true
    }
    return [pscustomobject]@{ commit=$worktreeHead; implementationCommit=$implementationCommit; closeoutCommit=$worktreeHead; merged=$merged; idempotent=$true; score=$score; scoreShadow=$scoreShadow; preCloseoutFacts=$null; projection=$null }
  }
  if ($ExpectedBaseCommit -and (Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD'))) -ne $ExpectedBaseCommit) { throw 'base branch advanced during Issue execution; closeout requires a fresh isolated retry' }
  Assert-AutopilotStockIssueClosed -Worktree $Worktree -ReadyPath $readyPath -IssueTitle $Issue.title
  $implementationCommit = $null
  $score = $null
  $scoreShadow = $null
  $subject = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('log','-1','--pretty=%s'))
  if ($subject -eq "feat(autopilot): implement $($Issue.issueId.ToLowerInvariant())") {
    $implementationCommit = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD'))
  } else {
    Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('diff','--check') | Out-Null
    Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('add','-A') -Mutating | Out-Null
    $cachedDiff = Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('diff','--cached','--quiet') -AcceptedExitCodes @(0,1)
    if ($cachedDiff.exitCode -eq 0) {
      $implementationCommit = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD'))
      if ($implementationCommit -eq $ExpectedBaseCommit) { throw 'closeout has no implementation artifacts to commit' }
    } else {
      Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('commit','-m',"feat(autopilot): implement $($Issue.issueId.ToLowerInvariant())") -Mutating | Out-Null
      $implementationCommit = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD'))
    }
  }
  $preCloseoutFacts = $null
  $projection = $null
  if ($null -ne $CloseoutFactsInput) {
    $followups = Get-AutopilotReportFollowupSummary -ReportPath $archivePath
    $preCloseoutFacts = New-AutopilotPreCloseoutFacts -Issue $Issue -ImplementationCommit $implementationCommit -EvidencePaths @($CloseoutFactsInput.evidencePaths) -ReviewRequired ([bool]$CloseoutFactsInput.reviewRequired) -ReviewDecision ([string]$CloseoutFactsInput.reviewDecision) -VerifiedDiffHash ([string]$CloseoutFactsInput.verifiedDiffHash) -Followups $followups -MetricsSummary $CloseoutFactsInput.metricsSummary -ControlPlaneFingerprint ([string]$CloseoutFactsInput.controlPlaneFingerprint)
    $projection = Set-AutopilotReportProjection -ReportPath $archivePath -Facts $preCloseoutFacts
  }
  if ($scoringActive) {
    if ($null -eq $ScoreEvidence) { throw 'active task scoring requires deterministic ScoreEvidence' }
    Set-AutopilotScoreProperty $ScoreEvidence 'implementationCommit' $implementationCommit
    if ($activeScoringVersion -eq $script:AutopilotTaskScoreVersion -and $null -ne $ScoreShadowEvidence) {
      Set-AutopilotScoreProperty $ScoreShadowEvidence 'implementationCommit' $implementationCommit
      $scoreShadow = New-AutopilotTaskScoreV2Shadow $ScoreShadowEvidence
      Add-AutopilotTaskScoreV2ShadowToReport -ReportPath $archivePath -Score $scoreShadow | Out-Null
    }
    $score = if ($activeScoringVersion -eq $script:AutopilotTaskScoreV2Version) {
      New-AutopilotTaskScoreV2 $ScoreEvidence
    } else {
      New-AutopilotTaskScore $ScoreEvidence
    }
    Add-AutopilotTaskScoreToReport -ReportPath $archivePath -Score $score | Out-Null
  }
  Set-AutopilotReadyDone -ReadyPath $readyPath -IssueTitle $Issue.title | Out-Null
  if (!(Test-Path -LiteralPath $donePath)) { [IO.File]::WriteAllText($donePath, "# Done Issues`n", [Text.UTF8Encoding]::new($false)) }
  $doneText = Get-Content -LiteralPath $donePath -Raw -Encoding UTF8
  if ($doneText -notmatch [regex]::Escape($Issue.issueId)) {
    [IO.File]::AppendAllText($donePath, "`n### $($Issue.title)`n`n状态：Done`n归档报告：``$($Issue.archiveReport)```n", [Text.UTF8Encoding]::new($false))
  }
  Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('diff','--check') | Out-Null
  Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('add','-A') -Mutating | Out-Null
  $commitMessage = $expectedCloseoutSubject
  Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('commit','-m',$commitMessage) -Mutating | Out-Null
  $commit = Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $Worktree -Arguments @('rev-parse','HEAD'))
  if ($null -ne $projection) {
    $projection | Add-Member -NotePropertyName reportHash -NotePropertyValue ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()) -Force
  }
  $merged = $false
  if ($AutoMerge) {
    $mainChanges = @(Get-AutopilotNativeOutputLines (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('status','--porcelain=v1')).stdout)
    if ($mainChanges.Count -gt 0) { throw 'main worktree is dirty; local merge refused' }
    if ($ExpectedBaseCommit -and (Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD'))) -ne $ExpectedBaseCommit) { throw 'base branch advanced before merge; local merge refused' }
    Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('merge','--ff-only',$commit) -Mutating | Out-Null
    $merged = $true
  }
  return [pscustomobject]@{ commit = $commit; implementationCommit = $implementationCommit; closeoutCommit = $commit; merged = $merged; idempotent = $false; score = $score; scoreShadow = $scoreShadow; preCloseoutFacts=$preCloseoutFacts; projection=$projection }
}

function Merge-AutopilotIssueCloseoutCommit {
  param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$Commit,
    [string]$ExpectedBaseCommit = ''
  )
  $ancestorResult = Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('merge-base','--is-ancestor',$Commit,'HEAD') -AcceptedExitCodes @(0,1)
  if ($ancestorResult.exitCode -eq 0) { return [pscustomobject]@{ merged=$true; idempotent=$true; commit=$Commit } }
  $mainChanges = @(Get-AutopilotNativeOutputLines (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('status','--porcelain=v1')).stdout)
  if ($mainChanges.Count -gt 0) { throw 'main worktree is dirty; local merge refused' }
  if ($ExpectedBaseCommit -and (Get-AutopilotCloseoutGitText (Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('rev-parse','HEAD'))) -ne $ExpectedBaseCommit) { throw 'base branch advanced before durable closeout merge; local merge refused' }
  Invoke-AutopilotCloseoutGit -RepoRoot $RepoRoot -Arguments @('merge','--ff-only',$Commit) -Mutating | Out-Null
  return [pscustomobject]@{ merged=$true; idempotent=$false; commit=$Commit }
}
