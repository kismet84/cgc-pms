$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$policyPath = Join-Path $repoRoot 'plugins\cgc-pms-autopilot\references\desktop-execution-policy.md'
$ownerPath = Join-Path $repoRoot 'plugins\cgc-pms-autopilot\references\owner-boundary.md'
$policy = Get-Content -LiteralPath $policyPath -Raw -Encoding UTF8
$owner = Get-Content -LiteralPath $ownerPath -Raw -Encoding UTF8
foreach ($required in @('短生命周期','不得拥有跨轮循环','DESKTOP_NATIVE_MODEL_PROCESS_FORBIDDEN','不得启动 Planner、Executor 或 Reviewer 模型进程')) {
  if ($policy -notmatch [regex]::Escape($required)) { throw "desktop policy missing boundary: $required" }
}
if ($owner -notmatch '不得持有跨轮循环' -or $owner -notmatch '不得在 `desktop-native` 下启动嵌套 `codex exec`') { throw 'owner boundary did not adopt desktop-native constraints' }
Write-Host 'desktop subagent boundary self-test passed'
