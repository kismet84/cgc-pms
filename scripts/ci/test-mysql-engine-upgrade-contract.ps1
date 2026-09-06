[CmdletBinding()]
param([string]$RepoRoot = '')

$ErrorActionPreference = 'Stop'
if (!$RepoRoot) { $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path }
$sourcePath = Join-Path $RepoRoot 'scripts/ci/run-mysql-engine-upgrade.ps1'
$sourceText = Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8
$parseTokens = $null
$parseErrors = $null
$sourceAst = [Management.Automation.Language.Parser]::ParseInput($sourceText, [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'MySQL engine upgrade source did not parse' }
$mainTries = @($sourceAst.EndBlock.Statements | Where-Object { $_ -is [Management.Automation.Language.TryStatementAst] })
if ($mainTries.Count -ne 1 -or !$mainTries[0].Finally) { throw 'Expected exactly one top-level upgrade try/finally' }
$finallyText = $mainTries[0].Finally.Extent.Text
# Execute the production finally body itself, not a test-side copy of its logic.
$cleanupBlock = [scriptblock]::Create($finallyText.Substring(1, $finallyText.Length - 2))

foreach ($required in @(
  'generation_expression FROM information_schema.columns',
  'constraint_type,enforced FROM information_schema.table_constraints',
  "SELECT 'CHECK',constraint_name,check_clause FROM information_schema.check_constraints",
  "SELECT 'FK_ACTION',table_name,constraint_name,unique_constraint_name,match_option,update_rule,delete_rule",
  'Unreviewed Upgrade Checker finding:',
  'Upgrade Checker requires unreviewed manual checks',
  '$null -eq $checker.errorCount',
  "Where-Object status -ne 'OK'",
  '$before.objects -cne $after.objects'
)) {
  if (!$sourceText.Contains($required)) { throw "Engine upgrade source contract is missing: $required" }
}
$startDatabase = $sourceAst.Find({ param($node)
  $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Start-Database'
}, $true)
if (!$startDatabase -or $startDatabase.Extent.Text.IndexOf('$containers.Add($name)') -lt 0 -or
    $startDatabase.Extent.Text.IndexOf('$containers.Add($name)') -gt $startDatabase.Extent.Text.IndexOf("Invoke-Docker @('run'")) {
  throw 'Database container must be registered before docker run can partially fail'
}

$environmentKeys = @('MYSQL_ROOT_PASSWORD', 'MYSQL_ROOT_HOST', 'MYSQL_DATABASE', 'MYSQL_PWD')
$callerEnvironment = @{}
foreach ($key in $environmentKeys) { $callerEnvironment[$key] = [Environment]::GetEnvironmentVariable($key) }
$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testRoot = [IO.Path]::GetFullPath((Join-Path $tempBase ('cgc-pms-engine-cleanup-contract-' + [guid]::NewGuid().ToString('N'))))
$tempPrefix = $tempBase.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (!$testRoot.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $testRoot)) {
  throw 'Unsafe or existing contract fixture root'
}
New-Item -ItemType Directory -Path $testRoot | Out-Null
$passed = 0

function Assert-Call([string]$Call, [bool]$Expected = $true) {
  if ($calls.Contains($Call) -ne $Expected) { throw "Unexpected cleanup call presence: $Call" }
}

function Invoke-Docker([string[]]$Arguments) {
  $calls.Add(($Arguments -join '|'))
  if ($Arguments[0] -eq 'ps') {
    foreach ($candidate in $containers) {
      if ($Arguments -contains "name=^/${candidate}$") {
        if ($scenario -eq 'container-missing' -and $candidate -eq $containers[0]) { return '' }
        return $candidate
      }
    }
    throw 'Mock received an unexpected container lookup'
  }
  if ($Arguments[0] -eq 'inspect') {
    if ($scenario -eq 'container-owner-mismatch' -and $Arguments[-1] -eq $containers[0]) { return 'another-task' }
    return $batch
  }
  if ($Arguments[0] -eq 'rm') {
    if ($scenario -eq 'container-rm-failure' -and $Arguments[-1] -eq $containers[0]) { throw 'mock container remove failed' }
    return ''
  }
  if ($Arguments[0] -eq 'volume') {
    if ($Arguments[1] -eq 'inspect') {
      if ($scenario -eq 'volume-owner-mismatch' -and $Arguments[-1] -eq $volumes[0]) { return 'another-task' }
      return $batch
    }
    if ($Arguments[1] -eq 'rm') { return '' }
  }
  if ($Arguments[0] -eq 'network') {
    if ($Arguments[1] -eq 'inspect') {
      if ($scenario -eq 'network-owner-mismatch') { return 'another-task' }
      return $batch
    }
    if ($Arguments[1] -eq 'rm') {
      if ($scenario -eq 'network-rm-failure') { throw 'mock network remove failed' }
      return ''
    }
  }
  throw 'Unexpected Docker operation in cleanup contract'
}

# A future direct invocation must never escape the command double above.
function docker { throw 'Real Docker invocation is prohibited in this contract' }

try {
  foreach ($scenario in @('success', 'container-missing', 'container-rm-failure', 'container-owner-mismatch',
                          'volume-owner-mismatch', 'network-rm-failure', 'network-owner-mismatch',
                          'keep-resources', 'artifact-write-failure')) {
    foreach ($originalPresent in @($true, $false)) {
      $batch = "contract-$scenario-$originalPresent"
      $network = "$batch-net"
      $containers = @("$batch-first", "$batch-second")
      $volumes = @("$batch-first-data", "$batch-second-data")
      $networkCreated = $true
      $KeepResources = $scenario -eq 'keep-resources'
      $artifacts = Join-Path $testRoot $batch
      $calls = [Collections.Generic.List[string]]::new()
      $environment = @{}
      foreach ($key in $environmentKeys) {
        $environment[$key] = if ($originalPresent) { "original-$key" } else { $null }
        [Environment]::SetEnvironmentVariable($key, 'temporary-contract-value')
      }
      if ($scenario -eq 'artifact-write-failure') { [IO.File]::WriteAllText($artifacts, 'not-a-directory') }
      $caught = $null
      try { & $cleanupBlock | Out-Null } catch { $caught = $_.Exception.Message }
      $mustFail = $scenario -notin @('success', 'container-missing', 'keep-resources')
      if ([bool]$caught -ne $mustFail) { throw "Unexpected cleanup outcome for $scenario (failure=$([bool]$caught))" }
      if ($mustFail -and $scenario -ne 'artifact-write-failure' -and !$caught.Contains('Batch cleanup incomplete:')) {
        throw 'Cleanup must collect resource errors before failing closed'
      }
      foreach ($key in $environmentKeys) {
        if ([Environment]::GetEnvironmentVariable($key) -cne $environment[$key]) {
          throw "Cleanup did not restore $key for $scenario / originalPresent=$originalPresent"
        }
      }
      if ($scenario -in @('keep-resources', 'artifact-write-failure')) {
        if ($calls.Count -ne 0) { throw "$scenario must not perform resource removal" }
      }
      else {
        Assert-Call "rm|-f|$($containers[0])" ($scenario -notin @('container-missing', 'container-owner-mismatch'))
        Assert-Call "rm|-f|$($containers[1])"
        Assert-Call "volume|rm|$($volumes[0])" ($scenario -ne 'volume-owner-mismatch')
        Assert-Call "volume|rm|$($volumes[1])"
        Assert-Call "network|rm|$network" ($scenario -ne 'network-owner-mismatch')
        if ($scenario -eq 'container-missing') {
          Assert-Call ('inspect|--format|{{index .Config.Labels "cgc-pms.mainline100"}}|' + $containers[0]) $false
        }
      }
      if ($scenario -ne 'artifact-write-failure') {
        $resourceRecord = Get-Content -LiteralPath (Join-Path $artifacts 'resources.json') -Raw | ConvertFrom-Json
        if ($resourceRecord.batch -cne $batch -or $resourceRecord.retained -ne $KeepResources) {
          throw 'Cleanup resource inventory does not identify the tested batch/retention state'
        }
      }
      $passed++
    }
  }
}
finally {
  foreach ($key in $environmentKeys) {
    if ($null -eq $callerEnvironment[$key]) { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
    else { [Environment]::SetEnvironmentVariable($key, $callerEnvironment[$key]) }
  }
  $resolvedRoot = [IO.Path]::GetFullPath($testRoot)
  if ($resolvedRoot -ne $testRoot -or !$resolvedRoot.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing unsafe cleanup of contract fixtures'
  }
  if (Test-Path -LiteralPath $resolvedRoot) { Remove-Item -LiteralPath $resolvedRoot -Recurse -Force }
}

[pscustomobject]@{
  ok = $true
  actualFinallyAstExecuted = $true
  cleanupAndEnvironmentCases = $passed
  environmentKeys = $environmentKeys
  schemaAndCheckerStaticContract = $true
  realDockerInvocations = 0
} | ConvertTo-Json
exit 0
