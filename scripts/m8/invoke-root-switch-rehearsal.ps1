[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 5180
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$composeFile = Join-Path $repoRoot 'deploy/docker-compose.m8-rehearsal.yml'
$project = 'cgc-pms-m8-rehearsal'
$network = 'deploy_cgc-pms-dev-net'
$baseUrl = "http://127.0.0.1:$Port"
$phases = [System.Collections.Generic.List[object]]::new()

function Invoke-Compose {
    param([Parameter(Mandatory)][string[]]$Command)

    & docker compose -p $project -f $composeFile @Command
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Command -join ' ')"
    }
}

function Wait-HttpOk {
    param([Parameter(Mandatory)][string]$Uri)

    $lastError = ''
    foreach ($attempt in 1..30) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                return $response
            }
            $lastError = "HTTP $($response.StatusCode)"
        }
        catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 1
    }
    throw "$Uri did not become ready: $lastError"
}

function Assert-SseHeaders {
    param(
        [Parameter(Mandatory)][Microsoft.PowerShell.Commands.WebRequestSession]$Session
    )

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.CookieContainer = $Session.Cookies
    $client = [System.Net.Http.HttpClient]::new($handler)
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Get,
        "$baseUrl/api/notifications/stream"
    )
    $timeout = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds(5))
    try {
        $response = $client.SendAsync(
            $request,
            [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead,
            $timeout.Token
        ).GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "SSE endpoint returned HTTP $([int]$response.StatusCode)"
        }
        if ($response.Content.Headers.ContentType.MediaType -ne 'text/event-stream') {
            throw "SSE content type was $($response.Content.Headers.ContentType)"
        }
        $response.Dispose()
    }
    finally {
        $timeout.Dispose()
        $request.Dispose()
        $client.Dispose()
        $handler.Dispose()
    }
}

function Assert-Frontend {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$ExpectedTitle,
        [Parameter(Mandatory)][string[]]$Paths
    )

    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $login = Invoke-WebRequest -UseBasicParsing -WebSession $session `
        -Uri "$baseUrl/api/auth/dev-login?username=admin" -TimeoutSec 10
    if ($login.StatusCode -ne 200) {
        throw "$Name dev-login returned HTTP $($login.StatusCode)"
    }

    $userinfo = Invoke-WebRequest -UseBasicParsing -WebSession $session `
        -Uri "$baseUrl/api/auth/userinfo" -TimeoutSec 10
    if ($userinfo.StatusCode -ne 200) {
        throw "$Name userinfo returned HTTP $($userinfo.StatusCode)"
    }

    $root = Invoke-WebRequest -UseBasicParsing -WebSession $session `
        -Uri "$baseUrl/" -TimeoutSec 10
    if ($root.Content -notmatch [regex]::Escape("<title>$ExpectedTitle</title>")) {
        throw "$Name root did not return expected title '$ExpectedTitle'"
    }

    $assetMatch = [regex]::Match($root.Content, '<script[^>]+src="(?<path>/[^"]+)"')
    if (-not $assetMatch.Success) {
        throw "$Name root did not expose a script asset"
    }
    $asset = Invoke-WebRequest -UseBasicParsing -WebSession $session `
        -Uri "$baseUrl$($assetMatch.Groups['path'].Value)" -TimeoutSec 10
    if ($asset.StatusCode -ne 200) {
        throw "$Name asset returned HTTP $($asset.StatusCode)"
    }

    foreach ($path in $Paths) {
        $page = Invoke-WebRequest -UseBasicParsing -WebSession $session `
            -Uri "$baseUrl$path" -TimeoutSec 10
        if ($page.StatusCode -ne 200 -or $page.Content -notmatch [regex]::Escape("<title>$ExpectedTitle</title>")) {
            throw "$Name deep link failed: $path"
        }
    }

    Assert-SseHeaders -Session $session
    return [ordered]@{
        name = $Name
        title = $ExpectedTitle
        asset = $assetMatch.Groups['path'].Value
        paths = $Paths
        api = 'UP'
        sse = 'text/event-stream'
    }
}

function Assert-V2Browser {
    $previousBaseUrl = $env:PLAYWRIGHT_BASE_URL
    $previousGate = $env:V2_M8_ROOT_REHEARSAL
    try {
        $env:PLAYWRIGHT_BASE_URL = $baseUrl
        $env:V2_M8_ROOT_REHEARSAL = '1'
        & pnpm --dir (Join-Path $repoRoot 'frontend-admin-v2') exec playwright test `
            e2e/m8-root-rehearsal.spec.ts --project=chromium --workers=1 --reporter=line
        if ($LASTEXITCODE -ne 0) {
            throw 'V2 root-path Playwright rehearsal failed'
        }
    }
    finally {
        if ($null -eq $previousBaseUrl) {
            Remove-Item Env:PLAYWRIGHT_BASE_URL -ErrorAction SilentlyContinue
        }
        else {
            $env:PLAYWRIGHT_BASE_URL = $previousBaseUrl
        }
        if ($null -eq $previousGate) {
            Remove-Item Env:V2_M8_ROOT_REHEARSAL -ErrorAction SilentlyContinue
        }
        else {
            $env:V2_M8_ROOT_REHEARSAL = $previousGate
        }
    }
}

function Set-Edge {
    param([Parameter(Mandatory)][string]$Upstream)

    $env:M8_FRONTEND_UPSTREAM = $Upstream
    Invoke-Compose -Command @('up', '-d', '--force-recreate', '--no-deps', 'edge')
    Wait-HttpOk -Uri "$baseUrl/healthz" | Out-Null
    $health = Wait-HttpOk -Uri "$baseUrl/api/actuator/health"
    $healthBody = if ($health.Content -is [byte[]]) {
        [System.Text.Encoding]::UTF8.GetString($health.Content)
    }
    else {
        [string]$health.Content
    }
    if (($healthBody | ConvertFrom-Json).status -ne 'UP') {
        throw "backend health was not UP through edge"
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker command not found'
}
if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "compose file not found: $composeFile"
}
& docker network inspect $network *> $null
if ($LASTEXITCODE -ne 0) {
    throw "required local dev Docker network not found: $network"
}

$env:M8_EDGE_PORT = [string]$Port

try {
    Wait-HttpOk -Uri 'http://127.0.0.1:8080/api/actuator/health' | Out-Null
    Wait-HttpOk -Uri 'http://127.0.0.1:5173/' | Out-Null

    Invoke-Compose -Command @('build', 'frontend-v2-root', 'frontend-legacy-rollback')
    Invoke-Compose -Command @('up', '-d', '--no-deps', 'frontend-v2-root', 'frontend-legacy-rollback')

    Set-Edge -Upstream 'frontend-v2-root:80'
    $v2Root = Assert-Frontend -Name 'V2_ROOT' -ExpectedTitle 'CGC-PMS V2' `
        -Paths @('/dashboard', '/project/list', '/approval/todo', '/system/users')
    Assert-V2Browser
    $v2Root['browser'] = '1/1'
    $phases.Add($v2Root)

    Set-Edge -Upstream 'frontend-legacy-rollback:80'
    $phases.Add((Assert-Frontend -Name 'LEGACY_ROLLBACK' `
        -ExpectedTitle '建筑工程总包项目管理系统' -Paths @('/dashboard')))

    Set-Edge -Upstream 'frontend-v2-root:80'
    $v2Restore = Assert-Frontend -Name 'V2_RESTORE' -ExpectedTitle 'CGC-PMS V2' `
        -Paths @('/dashboard', '/project/list', '/approval/todo', '/system/users')
    Assert-V2Browser
    $v2Restore['browser'] = '1/1'
    $phases.Add($v2Restore)

    [ordered]@{
        passed = $true
        environment = 'local-dev-with-demo-data'
        endpoint = $baseUrl
        artifact = 'same frontend-v2-root image'
        phases = $phases
    } | ConvertTo-Json -Depth 6
}
finally {
    Invoke-Compose -Command @('down', '--remove-orphans')
    Remove-Item Env:M8_FRONTEND_UPSTREAM -ErrorAction SilentlyContinue
    Remove-Item Env:M8_EDGE_PORT -ErrorAction SilentlyContinue
}
