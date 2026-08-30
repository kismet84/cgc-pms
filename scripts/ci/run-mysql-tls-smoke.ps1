[CmdletBinding()]
param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
  [string]$MysqlImage = 'mysql:8.0@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b'
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
  'CGCPMS_MYSQL_TLS_WRONG_TRUSTSTORE', 'CGCPMS_MYSQL_TLS_TRUSTSTORE_PASSWORD'
)

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
  $trustStorePassword = "tls-smoke-$([guid]::NewGuid().ToString('N'))"
  $rootPassword = "root-$([guid]::NewGuid().ToString('N'))"
  $databasePassword = "app-$([guid]::NewGuid().ToString('N'))"
  $database = 'cgc_pms_tls_smoke'
  $databaseUser = 'cgc_tls'

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
    '-keystore', $trustStore, '-storetype', 'PKCS12', '-storepass', $trustStorePassword) | Out-Null
  Invoke-Checked $keytool @('-importcert', '-noprompt', '-alias', 'wrong-ca', '-file', $wrongCa,
    '-keystore', $wrongTrustStore, '-storetype', 'PKCS12', '-storepass', $trustStorePassword) | Out-Null

  $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
  $listener.Start()
  $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
  $listener.Stop()

  $mountSource = $tempRoot
  $containerId = (Invoke-Checked $docker @(
    'run', '--detach', '--name', $containerName, '--publish', "127.0.0.1:${port}:3306",
    '--env', "MYSQL_ROOT_PASSWORD=$rootPassword", '--env', "MYSQL_DATABASE=$database",
    '--env', "MYSQL_USER=$databaseUser", '--env', "MYSQL_PASSWORD=$databasePassword",
    '--mount', "type=bind,source=$mountSource,target=/run/secrets/mysql,readonly",
    $MysqlImage, '--require-secure-transport=ON', '--ssl-ca=/run/secrets/mysql/ca.pem',
    '--ssl-cert=/run/secrets/mysql/server-cert.pem', '--ssl-key=/run/secrets/mysql/server-key.pem'
  )) -join ''
  if ([string]::IsNullOrWhiteSpace($containerId)) { throw 'Docker did not return a MySQL TLS smoke container id' }
  $containerCreated = $true

  $ready = $false
  for ($attempt = 0; $attempt -lt 60; $attempt++) {
    & $docker exec --env "MYSQL_PWD=$rootPassword" $containerName mysqladmin --user=root --silent ping *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
  }
  if (!$ready) { throw 'MySQL TLS smoke container did not become ready within 120 seconds' }

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
  foreach ($key in $environmentKeys) { Remove-Item -Path "Env:$key" -ErrorAction SilentlyContinue }
  if ($containerCreated) { & $docker rm --force $containerName *> $null }
  $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot)
  if ($resolvedTempRoot.StartsWith($tempBase + [IO.Path]::DirectorySeparatorChar) -and
      [IO.Path]::GetFileName($resolvedTempRoot).StartsWith('cgc-pms-mysql-tls-smoke-')) {
    Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force -ErrorAction SilentlyContinue
  }
}
