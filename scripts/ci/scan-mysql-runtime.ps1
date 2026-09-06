[CmdletBinding()]
param(
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path,
  [Parameter(Mandatory)][ValidatePattern('^sha256:[0-9a-f]{64}$')][string]$RuntimeImage,
  [Parameter(Mandatory)][ValidatePattern('^sha256:[0-9a-f]{64}$')][string]$PreflightImage
)
$ErrorActionPreference = 'Stop'
$output = Join-Path $RepoRoot 'backend/target/mysql-runtime-security'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$metadata = @()
foreach ($component in @('runtime','preflight')) {
  $image = if ($component -eq 'runtime') { $RuntimeImage } else { $PreflightImage }
  & docker run --rm --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock `
    --mount "type=bind,source=$output,target=/output" `
    aquasec/trivy:0.65.0@sha256:a22415a38938a56c379387a8163fcb0ce38b10ace73e593475d3658d578b2436 `
    image --cache-dir /output/cache --scanners vuln --list-all-pkgs --severity HIGH,CRITICAL `
    --exit-code 1 --timeout 10m --format json --output "/output/$component.json" $image
  if ($LASTEXITCODE -ne 0) { throw "MySQL $component image security scan failed" }
  $report = Get-Content -LiteralPath (Join-Path $output "$component.json") -Raw | ConvertFrom-Json
  if ($report.Metadata.ImageID -cne $image) { throw "MySQL $component scan image ID differs from the runtime input" }
  if (@($report.Results.Packages | Where-Object Name).Count -eq 0 -or
      @($report.Results.Vulnerabilities | Where-Object VulnerabilityID).Count -ne 0) {
    throw "MySQL $component image scan is empty or contains high/critical findings"
  }
  $metadata += @{component=$component;imageId=$image;report="$component.json";highCritical=0}
}
$sha = (& git -C $RepoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot bind MySQL security evidence to HEAD' }
[IO.File]::WriteAllText((Join-Path $output 'metadata.json'),(@{gitSha=$sha;images=$metadata} | ConvertTo-Json -Depth 5))
Write-Host 'MySQL runtime and TLS preflight high/critical scan: PASS'
