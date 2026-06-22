$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$qualityDir = Join-Path $root 'docs\quality'
New-Item -ItemType Directory -Force $qualityDir | Out-Null

$backendTests = (Get-ChildItem "$root\backend\src\test" -Recurse -Filter *.java).Count
$frontendTests = (Get-ChildItem "$root\frontend-admin\src" -Recurse -File |
  Where-Object { $_.Name -match '\.(test|spec)\.ts$' }).Count
$e2eCases = (Select-String -Path "$root\frontend-admin\e2e\*.spec.ts" -Pattern '\btest\(' -AllMatches |
  ForEach-Object { $_.Matches.Count } | Measure-Object -Sum).Sum
$largeVue = Get-ChildItem "$root\frontend-admin\src" -Recurse -Filter *.vue |
  ForEach-Object { [pscustomobject]@{ path = $_.FullName.Replace($root + '\', ''); lines = (Get-Content $_.FullName).Count } } |
  Where-Object { $_.lines -gt 800 } | Sort-Object lines -Descending

[ordered]@{
  generatedAt = (Get-Date).ToString('s')
  backendTestFiles = $backendTests
  frontendTestFiles = $frontendTests
  e2eCases = $e2eCases
  vueFilesOver800Lines = @($largeVue)
} | ConvertTo-Json -Depth 4 | Set-Content "$qualityDir\baseline.json" -Encoding utf8
