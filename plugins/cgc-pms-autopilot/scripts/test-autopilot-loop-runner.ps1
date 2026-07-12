param()

$ErrorActionPreference = 'Stop'
$readyPath = Join-Path ([System.IO.Path]::GetTempPath()) "cgc-pms-ready-$([guid]::NewGuid().ToString('N')).md"

try {
    @'
# Ready Issues

### ISSUE-TEST-001：合法 Ready

状态：Ready
验证命令：
- `git diff --check`
'@ | Set-Content -LiteralPath $readyPath -Encoding UTF8

    $result = & (Join-Path $PSScriptRoot 'autopilot-loop-runner.ps1') -DryRun -ReadyIssuePath $readyPath | ConvertFrom-Json
    if (-not $result.select.ready) {
        throw "合法 Ready 文档未通过插件选择门：$($result.select.reason)"
    }
} finally {
    Remove-Item -LiteralPath $readyPath -Force -ErrorAction SilentlyContinue
}

Write-Output 'test-autopilot-loop-runner: PASS'
