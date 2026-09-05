[CmdletBinding()]
param([string]$RepoRoot = '', [ValidateSet('runtime','preflight')][string]$Component = 'runtime')

$ErrorActionPreference = 'Stop'
if (!$RepoRoot) { $RepoRoot = Join-Path $PSScriptRoot '../..' }
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot -ErrorAction Stop).Path
$contextPath = Join-Path $RepoRoot 'deploy/mysql'
$dockerfilePath = Join-Path $contextPath $(if ($Component -eq 'preflight') { 'Preflight.Dockerfile' } else { 'Dockerfile' })
if (!(Test-Path -LiteralPath $dockerfilePath -PathType Leaf)) {
    throw "MySQL runtime Dockerfile is missing: $dockerfilePath"
}
if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is required to build the MySQL runtime'
}

# Each Dockerfile revision gets a separate local tag. Do not overwrite a shared
# development tag or publish this image to a registry. Consumers use the image ID.
$dockerfileHash = (Get-FileHash -LiteralPath $dockerfilePath -Algorithm SHA256).Hash.ToLowerInvariant()
$imageTag = "cgc-pms-mysql-${Component}:build-$dockerfileHash"
& {
    # Docker writes progress to stderr even on success. Windows PowerShell 5.1
    # must not treat that redirected stream as a terminating build error.
    $ErrorActionPreference = 'Continue'
    & docker build --provenance=false --platform linux/amd64 --file $dockerfilePath --tag $imageTag $contextPath 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "MySQL runtime build failed (exit $LASTEXITCODE)" }
}
$imageIds = @(& docker image inspect --format '{{.Id}}' $imageTag)
if ($LASTEXITCODE -ne 0 -or $imageIds.Count -ne 1) {
    throw 'MySQL runtime image inspection failed'
}
$imageId = ([string]$imageIds[0]).Trim()
if ($imageId -cnotmatch '^sha256:[0-9a-f]{64}$') {
    throw 'MySQL runtime image inspection returned an invalid immutable ID'
}
$imageId
