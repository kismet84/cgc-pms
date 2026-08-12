[CmdletBinding()]
param([string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)

$ErrorActionPreference = 'Stop'

$pomPath = Join-Path $RepoRoot 'backend\pom.xml'
$reportPath = Join-Path $RepoRoot 'backend\target\site\jacoco\jacoco.xml'
if (!(Test-Path -LiteralPath $pomPath -PathType Leaf)) { throw 'backend/pom.xml is missing' }
if (!(Test-Path -LiteralPath $reportPath -PathType Leaf)) { throw 'JaCoCo XML report is missing' }

[xml]$pom = Get-Content -LiteralPath $pomPath -Raw -Encoding UTF8
$namespace = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
$namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
$rules = @($pom.SelectNodes("//m:execution[m:id='jacoco-check']/m:configuration/m:rules/m:rule", $namespace))
if ($rules.Count -lt 2) { throw 'JaCoCo must define bundle and package rules' }

foreach ($executionId in @('default-prepare-agent','jacoco-report','jacoco-check')) {
  $excludes = @($pom.SelectNodes(
    "//m:execution[m:id='$executionId']/m:configuration/m:excludes/m:exclude",
    $namespace
  ) | ForEach-Object { $_.InnerText })
  if ($excludes.Count -ne 1 -or $excludes[0] -ne 'net/sf/jsqlparser/**') {
    throw "$executionId exclusions must remain limited to net/sf/jsqlparser/**"
  }
}

function Assert-Limit([System.Xml.XmlElement]$Rule,[string]$Counter,[decimal]$Minimum,[string]$Name) {
  $limit = @(@($Rule.SelectNodes('m:limits/m:limit', $namespace)) | Where-Object {
    [string]$_.counter -eq $Counter -and [string]$_.value -eq 'COVEREDRATIO'
  })
  if ($limit.Count -ne 1 -or [decimal]([string]$limit[0].minimum) -ne $Minimum) {
    throw "$Name $Counter minimum must remain $Minimum"
  }
}

$bundle = @($rules | Where-Object { [string]$_.element -eq 'BUNDLE' })
if ($bundle.Count -ne 1) { throw 'JaCoCo must define exactly one BUNDLE rule' }
Assert-Limit $bundle[0] 'INSTRUCTION' 0.73 'bundle'
Assert-Limit $bundle[0] 'BRANCH' 0.60 'bundle'

$package = @($rules | Where-Object { [string]$_.element -eq 'PACKAGE' })
if ($package.Count -ne 1) { throw 'JaCoCo must define exactly one PACKAGE rule' }
$includes = @($package[0].SelectNodes('m:includes/m:include', $namespace) | ForEach-Object { $_.InnerText })
$expectedIncludes = @('com.cgcpms.auth.config','com.cgcpms.config')
$missingIncludes = @($expectedIncludes | Where-Object { $includes -notcontains $_ })
if ($missingIncludes.Count -or $includes.Count -ne $expectedIncludes.Count) {
  throw "JaCoCo package gate must contain exactly: $($expectedIncludes -join ',')"
}
Assert-Limit $package[0] 'INSTRUCTION' 0.05 'package'
Assert-Limit $package[0] 'BRANCH' 0.03 'package'

[xml]$report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8
function Get-Ratio([string]$PackageName,[string]$Counter) {
  $node = @($report.report.package | Where-Object { $_.name -eq $PackageName.Replace('.', '/') })
  if ($node.Count -ne 1) { throw "JaCoCo report missing package: $PackageName" }
  $counterNode = @($node[0].counter | Where-Object { $_.type -eq $Counter })
  if ($counterNode.Count -ne 1) { throw "JaCoCo report missing $Counter for $PackageName" }
  $covered = [decimal]$counterNode[0].covered
  $total = $covered + [decimal]$counterNode[0].missed
  if ($total -eq 0) { return [decimal]1 }
  return $covered / $total
}

$packageEvidence = [ordered]@{}
foreach ($packageName in $expectedIncludes) {
  $instruction = Get-Ratio $packageName 'INSTRUCTION'
  $branch = Get-Ratio $packageName 'BRANCH'
  if ($instruction -lt 0.05 -or $branch -lt 0.03) {
    throw "$packageName coverage is below package gate: instruction=$instruction branch=$branch"
  }
  $packageEvidence[$packageName] = [ordered]@{
    instruction = [math]::Round([double]$instruction, 4)
    branch = [math]::Round([double]$branch, 4)
  }
}

[ordered]@{
  ok = $true
  bundleMinimum = [ordered]@{ instruction = 0.73; branch = 0.60 }
  packageMinimum = [ordered]@{ instruction = 0.05; branch = 0.03 }
  packages = $packageEvidence
} | ConvertTo-Json -Depth 5
