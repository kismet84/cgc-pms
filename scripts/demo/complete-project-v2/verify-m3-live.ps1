$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '../../..')
$frontendRoot = Join-Path $repoRoot 'frontend-admin-v2'

$env:V2_LIVE_PROJECTS = '1'
$env:V2_LIVE_DELIVERY = '1'
$env:V2_LIVE_QUALITY = '1'
$env:V2_LIVE_TECHNICAL = '1'
$env:V2_LIVE_CLOSEOUT = '1'
$env:V2_SCHEDULE_READONLY_USER = 'demo.schedule.query'
$env:V2_DELIVERY_PROJECT_ID = '520000000000009002'

Push-Location $frontendRoot
try {
    pnpm exec playwright test e2e/m3-projects.spec.ts e2e/m3-delivery.spec.ts e2e/m3-quality-safety.spec.ts e2e/m3-technical.spec.ts e2e/m3-closeout.spec.ts
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
