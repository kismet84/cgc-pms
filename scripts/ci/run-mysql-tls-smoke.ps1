[CmdletBinding()]
param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [string]$MysqlImage,
  [string]$PreflightImage
)

$ErrorActionPreference = 'Stop'

function Resolve-RequiredCommand([string]$Name, [string[]]$Fallbacks = @()) {
  $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($command) { return $command.Source }
  foreach ($fallback in $Fallbacks) {
    if (Test-Path -LiteralPath $fallback -PathType Leaf) { return $fallback }
  }
  throw "Missing required command for MySQL TLS smoke: $Name"
}

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
  $output = & $Command @Arguments 2>&1
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed: $([IO.Path]::GetFileName($Command)) $($Arguments[0]) (exit=$LASTEXITCODE)"
  }
  return @($output)
}

$docker = Resolve-RequiredCommand 'docker'
if (!$MysqlImage) { $MysqlImage = & (Join-Path $PSScriptRoot 'build-mysql-runtime.ps1') -RepoRoot $RepoRoot }
if (!$PreflightImage) { $PreflightImage = & (Join-Path $PSScriptRoot 'build-mysql-runtime.ps1') -RepoRoot $RepoRoot -Component preflight }
$mysqlIdentity = (Invoke-Checked $docker @('run','--rm','--entrypoint','sh',$MysqlImage,'-c','printf "%s:%s" "$(id -u mysql)" "$(id -g mysql)"')) -join ''
if ($mysqlIdentity -ne '27:27') { throw 'MySQL runtime UID:GID does not match the TLS preflight 27:27 contract' }
$openssl = Resolve-RequiredCommand 'openssl' @('C:\Program Files\Git\usr\bin\openssl.exe')
$keytool = Resolve-RequiredCommand 'keytool'
$isWindowsHost = $env:OS -eq 'Windows_NT'
$maven = Join-Path $RepoRoot $(if ($isWindowsHost) { 'backend\mvnw.cmd' } else { 'backend/mvnw' })
if (!(Test-Path -LiteralPath $maven -PathType Leaf)) { throw "Maven wrapper is missing: $maven" }

$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$tempRoot = Join-Path $tempBase ("cgc-pms-mysql-tls-smoke-{0}" -f [guid]::NewGuid().ToString('N'))
$containerName = "cgc-pms-mysql-tls-smoke-$([guid]::NewGuid().ToString('N').Substring(0, 12))"
$containerCreated = $false
$environmentKeys = @(
  'CGCPMS_MYSQL_TLS_SMOKE', 'CGCPMS_MYSQL_TLS_PORT', 'CGCPMS_MYSQL_TLS_DATABASE',
  'CGCPMS_MYSQL_TLS_USER', 'CGCPMS_MYSQL_TLS_PASSWORD', 'CGCPMS_MYSQL_TLS_TRUSTSTORE',
  'CGCPMS_MYSQL_TLS_WRONG_TRUSTSTORE', 'CGCPMS_MYSQL_TLS_TRUSTSTORE_PASSWORD',
  'MYSQL_TRUSTSTORE_PASSWORD','MYSQL_ROOT_PASSWORD','MYSQL_DATABASE','MYSQL_USER','MYSQL_PASSWORD','MYSQL_PWD'
)
$previousEnvironment = @{}
foreach ($key in $environmentKeys) { $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key) }

try {
  New-Item -ItemType Directory -Path $tempRoot -ErrorAction Stop | Out-Null
  $caKey = Join-Path $tempRoot 'ca-key.pem'
  $ca = Join-Path $tempRoot 'ca.pem'
  $serverKey = Join-Path $tempRoot 'server-key.pem'
  $serverCsr = Join-Path $tempRoot 'server.csr'
  $serverCert = Join-Path $tempRoot 'server-cert.pem'
  $serverExtensions = Join-Path $tempRoot 'server.ext'
  $wrongCaKey = Join-Path $tempRoot 'wrong-ca-key.pem'
  $wrongCa = Join-Path $tempRoot 'wrong-ca.pem'
  $trustStore = Join-Path $tempRoot 'mysql-truststore.p12'
  $wrongTrustStore = Join-Path $tempRoot 'wrong-truststore.p12'
  $extraTrustStore = Join-Path $tempRoot 'extra-ca-truststore.p12'
  $plainCertBag = Join-Path $tempRoot 'plain-cert-bag.p12'
  $trustStorePassword = "tls-smoke-$([guid]::NewGuid().ToString('N'))"
  $rootPassword = "root-$([guid]::NewGuid().ToString('N'))"
  $databasePassword = "app-$([guid]::NewGuid().ToString('N'))"
  $database = 'cgc_pms_tls_smoke'
  $databaseUser = 'cgc_tls'
  $env:MYSQL_TRUSTSTORE_PASSWORD = $trustStorePassword
  $env:MYSQL_ROOT_PASSWORD = $rootPassword
  $env:MYSQL_DATABASE = $database
  $env:MYSQL_USER = $databaseUser
  $env:MYSQL_PASSWORD = $databasePassword
  $env:MYSQL_PWD = $rootPassword

  [IO.File]::WriteAllText($serverExtensions, @"
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:mysql,IP:127.0.0.1
"@)

  Invoke-Checked $openssl @('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', $caKey) | Out-Null
  Invoke-Checked $openssl @('req', '-x509', '-new', '-key', $caKey, '-sha256', '-days', '60',
    '-subj', '/CN=CGC-PMS TLS Smoke CA', '-addext', 'basicConstraints=critical,CA:TRUE',
    '-addext', 'keyUsage=critical,keyCertSign,cRLSign', '-out', $ca) | Out-Null
  Invoke-Checked $openssl @('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', $serverKey) | Out-Null
  Invoke-Checked $openssl @('req', '-new', '-key', $serverKey, '-subj', '/CN=mysql', '-out', $serverCsr) | Out-Null
  Invoke-Checked $openssl @('x509', '-req', '-in', $serverCsr, '-CA', $ca, '-CAkey', $caKey,
    '-CAcreateserial', '-days', '45', '-sha256', '-extfile', $serverExtensions, '-out', $serverCert) | Out-Null
  Invoke-Checked $openssl @('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', $wrongCaKey) | Out-Null
  Invoke-Checked $openssl @('req', '-x509', '-new', '-key', $wrongCaKey, '-sha256', '-days', '60',
    '-subj', '/CN=CGC-PMS Wrong TLS Smoke CA', '-addext', 'basicConstraints=critical,CA:TRUE',
    '-addext', 'keyUsage=critical,keyCertSign,cRLSign', '-out', $wrongCa) | Out-Null

  Invoke-Checked $openssl @('verify', '-CAfile', $ca, $serverCert) | Out-Null
  Invoke-Checked $openssl @('x509', '-in', $serverCert, '-checkend', '2592000', '-noout') | Out-Null
  $san = (Invoke-Checked $openssl @('x509', '-in', $serverCert, '-noout', '-ext', 'subjectAltName')) -join "`n"
  if (!$san.Contains('DNS:mysql') -or !$san.Contains('IP Address:127.0.0.1')) {
    throw 'Generated MySQL server certificate lacks required DNS:mysql and IP:127.0.0.1 SAN values'
  }
  $certModulus = (Invoke-Checked $openssl @('x509', '-in', $serverCert, '-noout', '-modulus')) -join ''
  $keyModulus = (Invoke-Checked $openssl @('rsa', '-in', $serverKey, '-noout', '-modulus')) -join ''
  if ($certModulus -ne $keyModulus) { throw 'Generated MySQL certificate and private key do not match' }

  Invoke-Checked $keytool @('-importcert', '-noprompt', '-alias', 'mysql-ca', '-file', $ca,
    '-keystore', $trustStore, '-storetype', 'PKCS12', '-storepass:env', 'MYSQL_TRUSTSTORE_PASSWORD') | Out-Null
  Invoke-Checked $keytool @('-importcert', '-noprompt', '-alias', 'wrong-ca', '-file', $wrongCa,
    '-keystore', $wrongTrustStore, '-storetype', 'PKCS12', '-storepass:env', 'MYSQL_TRUSTSTORE_PASSWORD') | Out-Null
  Copy-Item -LiteralPath $trustStore -Destination $extraTrustStore
  Invoke-Checked $keytool @('-importcert', '-noprompt', '-alias', 'extra-ca', '-file', $wrongCa,
    '-keystore', $extraTrustStore, '-storetype', 'PKCS12', '-storepass:env', 'MYSQL_TRUSTSTORE_PASSWORD') | Out-Null
  Invoke-Checked $openssl @('pkcs12', '-export', '-nokeys', '-in', $ca, '-out', $plainCertBag,
    '-passout', 'env:MYSQL_TRUSTSTORE_PASSWORD') | Out-Null

  # Exercise the actual Compose preflight function, not a second implementation.
  $composeText = Get-Content -LiteralPath (Join-Path $RepoRoot 'deploy/docker-compose.prod.yml') -Raw
  $tlsFunction = [regex]::Match($composeText, '(?s)        check_mysql_tls_material\(\) \{.*?\r?\n        \}')
  if (!$tlsFunction.Success) { throw 'Cannot locate the Compose MySQL TLS preflight function' }
  $preflightFunction = $tlsFunction.Value.Replace('$$', '$')
  $preparePreflight = @'
set -eu
fail() { echo "[FAIL] $1"; exit 1; }
pass() { echo "[PASS] $1"; }
mkdir -p /run/secrets/mysql
cp /run/tls-input/ca.pem /run/secrets/mysql/ca.pem
cp /run/tls-input/server-cert.pem /run/secrets/mysql/server-cert.pem
cp /run/tls-input/server-key.pem /run/secrets/mysql/server-key.pem
chown -R 27:27 /run/secrets/mysql
chmod 0500 /run/secrets/mysql
chmod 0400 /run/secrets/mysql/*.pem
'@
  foreach ($case in @(
    @{ name = 'valid'; store = $trustStore; expected = 0; error = ''; setup = '' },
    @{ name = 'extra-ca'; store = $extraTrustStore; expected = 1; error = 'exactly one CA certificate'; setup = '' },
    @{ name = 'plain-cert-bag'; store = $plainCertBag; expected = 1; error = 'Java trustedCertEntry'; setup = '' },
    @{ name = 'unreadable-key'; store = $trustStore; expected = 1; error = 'readable by the MySQL image user'; setup = 'chown root:root /run/secrets/mysql/server-key.pem' }
  )) {
    $preflightCommand = $preparePreflight + "`n" + $case.setup + "`n" + $preflightFunction + "`ncheck_mysql_tls_material"
    $preflightOutput = & $docker run --rm --entrypoint sh `
      --env MYSQL_TRUSTSTORE_PASSWORD `
      --mount "type=bind,source=$ca,target=/run/tls-input/ca.pem,readonly" `
      --mount "type=bind,source=$serverCert,target=/run/tls-input/server-cert.pem,readonly" `
      --mount "type=bind,source=$serverKey,target=/run/tls-input/server-key.pem,readonly" `
      --mount "type=bind,source=$($case.store),target=/run/secrets/mysql-truststore.p12,readonly" `
      $PreflightImage -c $preflightCommand 2>&1
    $preflightExit = $LASTEXITCODE
    if ($preflightExit -ne $case.expected -or
        ($case.error -and !(($preflightOutput -join "`n").Contains($case.error)))) {
      throw "Compose TLS preflight case $($case.name) failed its expected exit/reason contract (exit=$preflightExit)"
    }
    Write-Host "Compose TLS preflight case $($case.name): PASS"
  }

  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
  $listener.Start()
  $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
  $listener.Stop()

  # Mount only public chain material and the server key; never expose either CA key.
  # Linux host keys are normally 0600 under the runner UID. Copy them as root to a
  # container-private directory, then restrict ownership before MySQL drops privileges.
  $mysqlEntrypoint = @'
set -eu
mkdir -p /run/mysql-tls
cp /run/tls-input/ca.pem /run/mysql-tls/ca.pem
cp /run/tls-input/server-cert.pem /run/mysql-tls/server-cert.pem
cp /run/tls-input/server-key.pem /run/mysql-tls/server-key.pem
chown -R 27:27 /run/mysql-tls
chmod 0500 /run/mysql-tls
chmod 0400 /run/mysql-tls/*.pem
exec /entrypoint.sh mysqld --require-secure-transport=ON --ssl-ca=/run/mysql-tls/ca.pem --ssl-cert=/run/mysql-tls/server-cert.pem --ssl-key=/run/mysql-tls/server-key.pem
'@
  $containerId = (Invoke-Checked $docker @(
    'run', '--detach', '--name', $containerName, '--publish', "127.0.0.1:${port}:3306",
    '--env', 'MYSQL_ROOT_PASSWORD', '--env', 'MYSQL_DATABASE',
    '--env', 'MYSQL_USER', '--env', 'MYSQL_PASSWORD',
    '--mount', "type=bind,source=$ca,target=/run/tls-input/ca.pem,readonly",
    '--mount', "type=bind,source=$serverCert,target=/run/tls-input/server-cert.pem,readonly",
    '--mount', "type=bind,source=$serverKey,target=/run/tls-input/server-key.pem,readonly",
    '--entrypoint', 'sh', $MysqlImage, '-c', $mysqlEntrypoint
  )) -join ''
  if ([string]::IsNullOrWhiteSpace($containerId)) { throw 'Docker did not return a MySQL TLS smoke container id' }
  $containerCreated = $true

  # Probe the same application account as JDBC, not root's image-specific host grants.
  $env:MYSQL_PWD = $databasePassword
  $ready = $false
  $probeOutput = @()
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $probeOutput = & $docker exec --env MYSQL_PWD $containerName mysql "--user=$databaseUser" --host=127.0.0.1 --connect-timeout=3 --ssl-mode=REQUIRED --batch --skip-column-names -e 'SELECT 1' $database 2>&1
    if ($LASTEXITCODE -eq 0 -and ($probeOutput -join '').Trim() -eq '1') { $ready = $true; break }
    Start-Sleep -Seconds 2
  }
  if (!$ready) {
    $diagnostic = ((@($probeOutput) + @(& $docker logs --tail 40 $containerName 2>&1)) -join "`n")
    foreach ($secret in @($rootPassword,$databasePassword,$trustStorePassword)) {
      $diagnostic = $diagnostic.Replace($secret,'[redacted]')
    }
    Write-Warning $diagnostic
    throw 'MySQL TLS smoke container did not become ready within 120 seconds'
  }

  $env:CGCPMS_MYSQL_TLS_SMOKE = 'true'
  $env:CGCPMS_MYSQL_TLS_PORT = [string]$port
  $env:CGCPMS_MYSQL_TLS_DATABASE = $database
  $env:CGCPMS_MYSQL_TLS_USER = $databaseUser
  $env:CGCPMS_MYSQL_TLS_PASSWORD = $databasePassword
  $env:CGCPMS_MYSQL_TLS_TRUSTSTORE = $trustStore
  $env:CGCPMS_MYSQL_TLS_WRONG_TRUSTSTORE = $wrongTrustStore
  $env:CGCPMS_MYSQL_TLS_TRUSTSTORE_PASSWORD = $trustStorePassword

  Push-Location (Join-Path $RepoRoot 'backend')
  try {
    & $maven -C '-Dtest=MySqlTlsSmokeTest' test
    if ($LASTEXITCODE -ne 0) { throw "MySQL Connector/J TLS smoke failed (exit=$LASTEXITCODE)" }
  }
  finally {
    Pop-Location
  }

  [pscustomobject]@{
    ok = $true
    image = $MysqlImage
    sslMode = 'VERIFY_IDENTITY'
    cipherVerified = $true
    negativeCases = @('missing-truststore', 'wrong-password', 'wrong-ca', 'hostname-mismatch')
  } | ConvertTo-Json -Depth 4
}
finally {
  foreach ($key in $environmentKeys) {
    if ($null -eq $previousEnvironment[$key]) { Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue }
    else { [Environment]::SetEnvironmentVariable($key,$previousEnvironment[$key]) }
  }
  if ($containerCreated) { & $docker rm --force $containerName *> $null }
  $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
  if ($resolvedTempRoot.StartsWith($tempBase + [IO.Path]::DirectorySeparatorChar) -and
      [IO.Path]::GetFileName($resolvedTempRoot).StartsWith('cgc-pms-mysql-tls-smoke-')) {
    Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force -ErrorAction SilentlyContinue
  }
}
