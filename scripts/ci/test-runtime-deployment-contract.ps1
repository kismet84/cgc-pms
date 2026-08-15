[CmdletBinding()]
param([string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path)

$ErrorActionPreference = 'Stop'

function Read-RepoText([string]$RelativePath) {
  $path = Join-Path $RepoRoot $RelativePath
  if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Runtime deployment contract file is missing: $RelativePath"
  }
  return Get-Content -LiteralPath $path -Raw -Encoding UTF8
}

function Get-ComposeServiceBlock([string]$Compose, [string]$ServiceName) {
  $service = [regex]::Escape($ServiceName)
  $match = [regex]::Match(
    $Compose,
    "(?ms)^  ${service}:\r?\n(?<body>.*?)(?=^  [a-zA-Z0-9][a-zA-Z0-9_-]*:\r?$|^networks:\r?$|^volumes:\r?$|\z)"
  )
  if (!$match.Success) { throw "Production Compose service is missing: $ServiceName" }
  return $match.Value
}

$dockerfile = Read-RepoText 'backend\Dockerfile'
$compose = Read-RepoText 'deploy\docker-compose.prod.yml'
$logicalDockerfile = [regex]::Replace($dockerfile, '\\\r?\n\s*', ' ')
$javaOptsMatch = [regex]::Match(
  $logicalDockerfile,
  '(?m)^\s*ENV\s+JAVA_OPTS=(?:"(?<opts>[^"]*)"|(?<opts>[^\r\n]*))'
)
if (!$javaOptsMatch.Success) { throw 'backend/Dockerfile must declare JAVA_OPTS' }
$javaOpts = $javaOptsMatch.Groups['opts'].Value

if ($javaOpts -match '(?:^|\s)-Xmx\S+') {
  throw 'JAVA_OPTS must not pin -Xmx; container-aware MaxRAMPercentage owns heap sizing'
}
if ($javaOpts -notmatch '(?:^|\s)-XX:\+UseContainerSupport(?:\s|$)') {
  throw 'JAVA_OPTS must keep explicit container support'
}
if ($javaOpts -notmatch '(?:^|\s)-Duser\.timezone=Asia/Shanghai(?:\s|$)') {
  throw 'JAVA_OPTS must keep the Asia/Shanghai business timezone'
}

$devCompose = Read-RepoText 'deploy\docker-compose.dev.yml'
$devBackend = Get-ComposeServiceBlock $devCompose 'backend'
if ($devBackend -notmatch '(?mi)^      JAVA_TOOL_OPTIONS:\s*["'']?-Duser\.timezone=Asia/Shanghai["'']?\s*$') {
  throw 'Development backend service must keep the Asia/Shanghai business timezone'
}

$maxRamMatch = [regex]::Match($javaOpts, '(?:^|\s)-XX:MaxRAMPercentage=(?<value>\d+(?:\.\d+)?)(?:\s|$)')
if (!$maxRamMatch.Success) { throw 'JAVA_OPTS must declare MaxRAMPercentage' }
$maxRamPercentage = [double]::Parse(
  $maxRamMatch.Groups['value'].Value,
  [System.Globalization.CultureInfo]::InvariantCulture
)
if ($maxRamPercentage -le 0 -or $maxRamPercentage -gt 70) {
  throw "MaxRAMPercentage must reserve at least 30 percent for metaspace, stacks, direct memory, code cache, and native libraries; actual=$maxRamPercentage"
}

$heapDumpMatch = [regex]::Match($javaOpts, '(?:^|\s)-XX:HeapDumpPath=(?<path>[^\s"]+)(?:\s|$)')
if (!$heapDumpMatch.Success) { throw 'JAVA_OPTS must declare HeapDumpPath' }
$heapDumpPath = $heapDumpMatch.Groups['path'].Value.TrimEnd('/')
if (!$heapDumpPath.StartsWith('/') -or $heapDumpPath -eq '/tmp' -or $heapDumpPath.StartsWith('/tmp/')) {
  throw "HeapDumpPath must use a dedicated persistent container path, not a temporary layer; actual=$heapDumpPath"
}

if ($dockerfile -match '(?m)^\s*ENV\s+SPRING_DATASOURCE_URL(?:\s|=)') {
  throw 'backend/Dockerfile must not provide a SPRING_DATASOURCE_URL fallback; deployment configuration must fail closed'
}

$backend = Get-ComposeServiceBlock $compose 'backend'
if ($backend -notmatch '(?mi)^    mem_limit:\s*["'']?1g["'']?\s*$') {
  throw 'Production backend service must declare service-level mem_limit: 1G'
}
if ($backend -notmatch '(?ms)^    deploy:\r?\n.*?^      resources:\r?\n.*?^        limits:\r?\n.*?^          memory:\s*["'']?1g["'']?\s*$') {
  throw 'Production backend service must keep deploy.resources.limits.memory: 1G'
}

$escapedHeapDumpPath = [regex]::Escape($heapDumpPath)
if ($backend -notmatch "(?m)^      -\s*[^#\r\n]+:${escapedHeapDumpPath}(?::[a-z,]+)?\s*(?:#.*)?$") {
  throw "Production backend service must mount persistent storage at HeapDumpPath: $heapDumpPath"
}

[pscustomobject]@{
  ok = $true
  maxRamPercentage = $maxRamPercentage
  heapDumpPath = $heapDumpPath
  backendMemoryLimit = '1G'
  businessTimezone = 'Asia/Shanghai'
  datasourceFallback = $false
} | ConvertTo-Json
