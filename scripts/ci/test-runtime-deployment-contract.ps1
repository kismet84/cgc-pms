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
$prodConfig = Read-RepoText 'backend\src\main\resources\application-prod.yml'
$envExample = Read-RepoText 'deploy\.env.example'
$monitoringCompose = Read-RepoText 'deploy\docker-compose.monitoring.yml'
$prometheus = Read-RepoText 'deploy\monitoring\prometheus.yml'
$gitignore = Read-RepoText '.gitignore'
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
$mysql = Get-ComposeServiceBlock $compose 'mysql'
$preflight = Get-ComposeServiceBlock $compose 'preflight'
$monitoringBackend = Get-ComposeServiceBlock $monitoringCompose 'backend'
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

if ($mysql -notmatch '(?m)^      MYSQL_DATABASE:\s*\$\{MYSQL_DATABASE:-cgc_pms\}\s*$') {
  throw 'Production MySQL service must use MYSQL_DATABASE with the cgc_pms default'
}
if ($backend -notmatch 'jdbc:mysql://mysql:3306/\$\{MYSQL_DATABASE:-cgc_pms\}\?') {
  throw 'Production backend JDBC URL must use the same MYSQL_DATABASE parameter as MySQL initialization'
}
foreach ($legacyParameter in @('useSSL=', 'requireSSL=', 'verifyServerCertificate=', 'allowPublicKeyRetrieval=')) {
  if ($backend.Contains($legacyParameter)) {
    throw "Production JDBC URL must not duplicate legacy TLS parameter: $legacyParameter"
  }
}
foreach ($mysqlTlsArgument in @(
  '--require-secure-transport=ON',
  '--ssl-ca=/run/secrets/mysql/ca.pem',
  '--ssl-cert=/run/secrets/mysql/server-cert.pem',
  '--ssl-key=/run/secrets/mysql/server-key.pem'
)) {
  if (!$mysql.Contains($mysqlTlsArgument)) { throw "Production MySQL TLS argument is missing: $mysqlTlsArgument" }
}
foreach ($mysqlMount in @(
  '/run/secrets/mysql/ca.pem:ro',
  '/run/secrets/mysql/server-cert.pem:ro',
  '/run/secrets/mysql/server-key.pem:ro'
)) {
  if (!$mysql.Contains($mysqlMount)) { throw "Production MySQL TLS read-only mount is missing: $mysqlMount" }
}
if (!$backend.Contains('/run/secrets/mysql-truststore.p12:ro')) {
  throw 'Production backend must mount the explicit PKCS12 MySQL truststore read-only'
}
if ($backend.Contains('/run/secrets/mysql/server-key.pem') -or $backend.Contains('/run/secrets/mysql/ca.pem')) {
  throw 'Production backend must not receive the MySQL server key or raw CA mount'
}
foreach ($preflightContract in @(
  'image: mysql:8.0@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b',
  'MYSQL_TRUSTSTORE_PASSWORD',
  'check_file "/run/secrets/mysql/ca.pem"',
  'check_file "/run/secrets/mysql/server-cert.pem"',
  'check_file "/run/secrets/mysql/server-key.pem"',
  'check_file "/run/secrets/mysql-truststore.p12"',
  'openssl verify -CAfile "$$ca" "$$cert"',
  "grep -q 'CA:TRUE'",
  "grep -q 'SSL server : Yes'",
  'checkend 2592000',
  'grep -Eq ''DNS:mysql([,[:space:]]|$$)''',
  'MySQL server certificate and key do not match',
  "grep -q 'Trusted key usage'",
  'MySQL truststore CA must be a Java trustedCertEntry',
  'MySQL truststore CA certificate does not match the MySQL CA'
)) {
  if (!$preflight.Contains($preflightContract)) { throw "Production preflight MySQL TLS contract is missing: $preflightContract" }
}
foreach ($hikariContract in @(
  'sslMode: VERIFY_IDENTITY',
  'trustCertificateKeyStoreUrl: ${DB_TRUSTSTORE_URL}',
  'trustCertificateKeyStoreType: ${DB_TRUSTSTORE_TYPE:PKCS12}',
  'trustCertificateKeyStorePassword: ${DB_TRUSTSTORE_PASSWORD}',
  'fallbackToSystemTrustStore: false',
  'allowPublicKeyRetrieval: false'
)) {
  if (!$prodConfig.Contains($hikariContract)) { throw "Production Hikari TLS contract is missing: $hikariContract" }
}
foreach ($envContract in @(
  'MYSQL_CA_FILE=./secrets/mysql-ca.pem',
  'MYSQL_SERVER_CERT_FILE=./secrets/mysql-server-cert.pem',
  'MYSQL_SERVER_KEY_FILE=./secrets/mysql-server-key.pem',
  'MYSQL_TRUSTSTORE_FILE=./secrets/mysql-truststore.p12',
  'MYSQL_TRUSTSTORE_PASSWORD=CHANGE-ME-MYSQL-TRUSTSTORE-PASSWORD'
)) {
  if (!$envExample.Contains($envContract)) { throw "Deployment environment template is missing: $envContract" }
}
if ($dockerfile.Contains('MINIO_ACCESS_KEY — injected by docker-compose from MINIO_ROOT_USER') -or
    $dockerfile.Contains('MINIO_SECRET_KEY — injected by docker-compose from MINIO_ROOT_PASSWORD')) {
  throw 'Backend Dockerfile must describe the independent MinIO application account'
}
if ($backend -notmatch '(?m)^      -\s*backend-logs:/var/log/cgc-pms\s*$') {
  throw 'Production backend must persist the configured /var/log/cgc-pms log directory'
}
if ($compose -notmatch '(?m)^  backend-logs:\s*$') {
  throw 'Production Compose must declare the backend-logs volume'
}
if ($monitoringCompose -notmatch '(?m)^      - "127\.0\.0\.1:9090:9090"\s*$') {
  throw 'Prometheus UI must bind to loopback instead of all host interfaces'
}
if ($monitoringCompose -notmatch '/run/secrets/monitoring_scrape_password:ro') {
  throw 'Prometheus must receive its scrape password through a read-only secret file mount'
}
if ($backend -notmatch '(?m)^      MONITORING_SCRAPE_PASSWORD_FILE:\s*/run/secrets/monitoring_scrape_password\s*$' -or
    $monitoringBackend -notmatch '(?m)^      MONITORING_SCRAPE_PASSWORD_FILE:\s*/run/secrets/monitoring_scrape_password\s*$') {
  throw 'Backend monitoring identity must read the same mounted secret file in production and local monitoring overlays'
}
if ($compose -match '(?m)^\s*MONITORING_SCRAPE_PASSWORD:\s*\S+' -or
    $monitoringCompose -match '(?m)^\s*MONITORING_SCRAPE_PASSWORD:\s*\S+') {
  throw 'Compose files must not carry a plaintext monitoring scrape password'
}
if ($prometheus -notmatch '(?m)^    basic_auth:\s*$' -or
    $prometheus -notmatch "(?m)^      password_file: '/run/secrets/monitoring_scrape_password'\s*$") {
  throw 'Prometheus backend scrape must use Basic authentication with password_file'
}
if ($gitignore -notmatch '(?m)^deploy/secrets/\s*$') {
  throw 'Local deployment secret files must be excluded from Git'
}

[pscustomobject]@{
  ok = $true
  maxRamPercentage = $maxRamPercentage
  heapDumpPath = $heapDumpPath
  backendMemoryLimit = '1G'
  businessTimezone = 'Asia/Shanghai'
  datasourceFallback = $false
  databaseNameSingleSource = $true
  mysqlTlsTrustChain = $true
  mysqlIdentityVerification = $true
  persistentLogPath = '/var/log/cgc-pms'
  prometheusMachineAuth = $true
} | ConvertTo-Json
