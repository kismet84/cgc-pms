param(
    [ValidateSet('dev', 'test', 'demo')]
    [string]$Environment = 'demo',

    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$Database = 'cgc_pms_demo_v2',

    [string]$MySqlContainer = 'cgc-pms-mysql-dev',
    [string]$BackendContainer = 'cgc-pms-backend-dev',
    [string]$FrontendUrl = 'http://127.0.0.1:4173',
    [string]$BackendUrl = 'http://127.0.0.1:8080/api',
    [long]$TenantId = 0,
    [string]$ProbeUsername = 'admin',
    [ValidateRange(1, 8)]
    [int]$Workers = 1,
    [string]$EvidenceDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-LoopbackUri([string]$Value, [string]$Name) {
    $uri = $null
    if (-not [uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -notin @('http', 'https') -or
        $uri.Host -notin @('127.0.0.1', 'localhost', '::1')) {
        throw "LIVE_EVIDENCE_LOOPBACK_REQUIRED:$Name"
    }
}

function Assert-DemoDatabase([string]$Value) {
    if ($Value -ne 'cgc_pms_demo_v2') {
        throw 'LIVE_EVIDENCE_DEDICATED_DATABASE_REQUIRED'
    }
}

function Get-BackendProxyTarget([string]$BackendBaseUrl) {
    Assert-LoopbackUri $BackendBaseUrl 'backend'
    $uri = [uri]$BackendBaseUrl
    if ($uri.Scheme -ne 'http' -or $uri.AbsolutePath.TrimEnd('/') -ne '/api' -or
        -not [string]::IsNullOrEmpty($uri.Query) -or -not [string]::IsNullOrEmpty($uri.Fragment) -or
        -not [string]::IsNullOrEmpty($uri.UserInfo)) {
        throw 'LIVE_EVIDENCE_BACKEND_URL_INVALID'
    }
    return $uri.GetLeftPart([UriPartial]::Authority)
}

function Assert-CleanWorkingTree([string]$RepoRoot) {
    $status = @(git -C $RepoRoot status --porcelain --untracked-files=all)
    if ($LASTEXITCODE -ne 0) { throw 'LIVE_EVIDENCE_GIT_STATUS_UNAVAILABLE' }
    if ($status.Count -gt 0) { throw 'LIVE_EVIDENCE_CLEAN_WORKTREE_REQUIRED' }
}

function Get-DatabaseNameFromJdbcUrl([string]$JdbcUrl) {
    $match = [regex]::Match($JdbcUrl, '^jdbc:mysql://[^/]+/(?<database>[A-Za-z0-9_]+)(?:\?|$)')
    if (-not $match.Success) { throw 'LIVE_EVIDENCE_BACKEND_DATASOURCE_INVALID' }
    return $match.Groups['database'].Value
}

function Assert-BackendContainerBinding(
    [string]$Container,
    [string]$BackendBaseUrl,
    [string]$ExpectedDatabase
) {
    $uri = [uri]$BackendBaseUrl
    $portBinding = @(docker port $Container 8080/tcp 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not ($portBinding -contains "127.0.0.1:$($uri.Port)")) {
        throw 'LIVE_EVIDENCE_BACKEND_LOOPBACK_BINDING_REQUIRED'
    }
    $environmentJson = docker inspect --format '{{json .Config.Env}}' $Container 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($environmentJson)) {
        throw 'LIVE_EVIDENCE_BACKEND_CONTAINER_UNAVAILABLE'
    }
    $environmentValues = @($environmentJson | ConvertFrom-Json)
    $datasource = @($environmentValues | Where-Object { $_.StartsWith('SPRING_DATASOURCE_URL=', [StringComparison]::Ordinal) })
    if ($datasource.Count -ne 1) { throw 'LIVE_EVIDENCE_BACKEND_DATASOURCE_UNAVAILABLE' }
    $actualDatabase = Get-DatabaseNameFromJdbcUrl $datasource[0].Substring('SPRING_DATASOURCE_URL='.Length)
    if ($actualDatabase -ne $ExpectedDatabase) { throw 'LIVE_EVIDENCE_BACKEND_DATABASE_MISMATCH' }
}

function Start-LiveFrontend([string]$FrontendRoot, [string]$Url, [string]$BackendProxyTarget) {
    Assert-LoopbackUri $Url 'frontend'
    Assert-LoopbackUri $BackendProxyTarget 'backend-proxy'
    $uri = [uri]$Url
    if ($uri.Scheme -ne 'http' -or $uri.AbsolutePath -ne '/' -or $uri.Port -le 0) {
        throw 'LIVE_EVIDENCE_FRONTEND_URL_INVALID'
    }
    if (Get-NetTCPConnection -State Listen -LocalPort $uri.Port -ErrorAction SilentlyContinue) {
        throw "LIVE_EVIDENCE_FRONTEND_PORT_IN_USE:$($uri.Port)"
    }

    $viteEntry = Join-Path $FrontendRoot 'node_modules/vite/bin/vite.js'
    if (-not (Test-Path -LiteralPath $viteEntry -PathType Leaf)) {
        throw 'LIVE_EVIDENCE_VITE_UNAVAILABLE'
    }
    $stdout = Join-Path ([IO.Path]::GetTempPath()) "cgc-live-frontend-$([guid]::NewGuid().ToString('N')).out.log"
    $stderr = Join-Path ([IO.Path]::GetTempPath()) "cgc-live-frontend-$([guid]::NewGuid().ToString('N')).err.log"
    $process = $null
    $previousApiTarget = [Environment]::GetEnvironmentVariable('VITE_API_TARGET', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('VITE_API_TARGET', $BackendProxyTarget, 'Process')
        $process = Start-Process -FilePath (Get-Command node).Source `
            -ArgumentList @($viteEntry, '--host', $uri.Host, '--port', [string]$uri.Port, '--strictPort') `
            -WorkingDirectory $FrontendRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $deadline = [DateTimeOffset]::UtcNow.AddSeconds(30)
        while ([DateTimeOffset]::UtcNow -lt $deadline) {
            if ($process.HasExited) {
                $detail = @(
                    Get-Content -LiteralPath $stdout -Tail 3 -ErrorAction SilentlyContinue
                    Get-Content -LiteralPath $stderr -Tail 3 -ErrorAction SilentlyContinue
                ) -join ' '
                throw "LIVE_EVIDENCE_FRONTEND_START_FAILED:$($process.ExitCode):$detail"
            }
            try {
                $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
                if ($response.StatusCode -eq 200) {
                    return [pscustomobject]@{ Process = $process; Stdout = $stdout; Stderr = $stderr }
                }
            } catch {
                Start-Sleep -Milliseconds 250
            }
        }
        throw 'LIVE_EVIDENCE_FRONTEND_START_TIMEOUT'
    } catch {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
        foreach ($path in @($stdout, $stderr)) {
            if (Test-Path -LiteralPath $path) {
                Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
            }
        }
        throw
    } finally {
        [Environment]::SetEnvironmentVariable('VITE_API_TARGET', $previousApiTarget, 'Process')
    }
}

function Stop-LiveFrontend([object]$Frontend) {
    if ($null -eq $Frontend) { return }
    try {
        if (-not $Frontend.Process.HasExited) {
            Stop-Process -Id $Frontend.Process.Id -Force -ErrorAction SilentlyContinue
            Wait-Process -Id $Frontend.Process.Id -Timeout 10 -ErrorAction SilentlyContinue
        }
    } finally {
        $Frontend.Process.Dispose()
        foreach ($path in @($Frontend.Stdout, $Frontend.Stderr)) {
            if (Test-Path -LiteralPath $path) {
                Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

function Assert-ResetMarker([string]$RepoRoot) {
    $marker = Join-Path $RepoRoot '.codex-autopilot/ALLOW_TEST_DATA_RESET'
    if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) {
        throw 'LIVE_EVIDENCE_RESET_MARKER_REQUIRED'
    }
}

function Assert-RequiredUsersCovered([string[]]$RequiredUsers, [string]$PackageText) {
    foreach ($username in $RequiredUsers) {
        if (-not $PackageText.Contains($username, [StringComparison]::Ordinal)) {
            throw "LIVE_EVIDENCE_USER_FIXTURE_MISSING:$username"
        }
    }
}

function Invoke-DockerMySqlRead(
    [string]$Container,
    [string]$DatabaseName,
    [string]$Sql
) {
    $tempFile = Join-Path ([IO.Path]::GetTempPath()) ("cgc-live-read-{0}.sql" -f [guid]::NewGuid().ToString('N'))
    $containerFile = "/tmp/cgc-live-read-$([guid]::NewGuid().ToString('N')).sql"
    try {
        [IO.File]::WriteAllText($tempFile, $Sql, [Text.UTF8Encoding]::new($false))
        docker cp $tempFile "${Container}:$containerFile" 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'LIVE_EVIDENCE_DOCKER_COPY_FAILED' }
        $result = docker exec $Container sh -lc "MYSQL_PWD=`"`$MYSQL_ROOT_PASSWORD`" mysql --no-defaults -uroot --default-character-set=utf8mb4 -N $DatabaseName < $containerFile" 2>&1
        if ($LASTEXITCODE -ne 0) {
            $summary = (($result | Select-Object -Last 4) -join ' ') -replace '[\r\n]+', ' '
            throw "LIVE_EVIDENCE_MYSQL_READ_FAILED:$summary"
        }
        return @($result)
    } finally {
        docker exec $Container rm -f $containerFile 2>$null | Out-Null
        if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile -Force }
    }
}

function Get-LiveSpecs([string]$FrontendRoot) {
    $modulePath = (Join-Path $FrontendRoot 'scripts/e2e-spec-groups.mjs').Replace('\', '/')
    $json = node --input-type=module -e "import { liveSpecs } from 'file:///$modulePath'; console.log(JSON.stringify(liveSpecs))"
    if ($LASTEXITCODE -ne 0) { throw 'LIVE_EVIDENCE_SPEC_LIST_UNAVAILABLE' }
    $specs = @($json | ConvertFrom-Json)
    if ($specs.Count -ne 9 -or ($specs | Select-Object -Unique).Count -ne 9) {
        throw "LIVE_EVIDENCE_SPEC_COUNT_INVALID:$($specs.Count)"
    }
    return $specs
}

function Get-RequiredUsers([string]$FrontendRoot, [string[]]$LiveSpecs) {
    $users = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($spec in $LiveSpecs) {
        $source = [IO.File]::ReadAllText((Join-Path $FrontendRoot "e2e/$spec"))
        foreach ($match in [regex]::Matches($source, "['`"](?<user>admin|demo\.[a-z0-9.-]+)['`"]")) {
            [void]$users.Add($match.Groups['user'].Value)
        }
    }
    return @($users | Sort-Object)
}

function Assert-UserRows(
    [string]$Container,
    [string]$DatabaseName,
    [long]$ExpectedTenantId,
    [string[]]$RequiredUsers
) {
    $quotedUsers = $RequiredUsers | ForEach-Object { "'$($_.Replace("'", "''"))'" }
    $sql = "SELECT username FROM sys_user WHERE tenant_id=$ExpectedTenantId AND deleted_flag=0 AND status IN ('ACTIVE','ENABLE') AND username IN ($($quotedUsers -join ',')) ORDER BY username;"
    $actual = @(Invoke-DockerMySqlRead -Container $Container -DatabaseName $DatabaseName -Sql $sql)
    $missing = @($RequiredUsers | Where-Object { $_ -notin $actual })
    if ($missing.Count -gt 0) {
        throw "LIVE_EVIDENCE_USERS_UNAVAILABLE:$($missing -join ',')"
    }
}

function Assert-LivePreflight(
    [string]$RepoRoot,
    [string]$FrontendRoot,
    [string]$EnvironmentName,
    [string]$DatabaseName,
    [string]$MySqlContainerName,
    [string]$BackendContainerName,
    [string]$FrontendBaseUrl,
    [string]$BackendBaseUrl,
    [long]$ExpectedTenantId,
    [string[]]$RequiredUsers
) {
    if ($EnvironmentName -notin @('dev', 'test', 'demo')) { throw 'LIVE_EVIDENCE_ENVIRONMENT_REQUIRED' }
    Assert-DemoDatabase $DatabaseName
    Assert-LoopbackUri $FrontendBaseUrl 'frontend'
    Assert-LoopbackUri $BackendBaseUrl 'backend'
    Assert-ResetMarker $RepoRoot
    if ($ExpectedTenantId -ne 0) { throw 'LIVE_EVIDENCE_DEDICATED_TENANT_REQUIRED' }

    $portBinding = docker port $MySqlContainerName 3306/tcp 2>$null
    if ($LASTEXITCODE -ne 0 -or -not (($portBinding -join "`n") -match '127\.0\.0\.1:')) {
        throw 'LIVE_EVIDENCE_MYSQL_LOOPBACK_REQUIRED'
    }
    $selectedDatabase = Invoke-DockerMySqlRead -Container $MySqlContainerName -DatabaseName $DatabaseName -Sql 'SELECT DATABASE();'
    if (($selectedDatabase | Select-Object -First 1) -ne $DatabaseName) {
        throw 'LIVE_EVIDENCE_MYSQL_DATABASE_MISMATCH'
    }
    Assert-BackendContainerBinding -Container $BackendContainerName -BackendBaseUrl $BackendBaseUrl `
        -ExpectedDatabase $DatabaseName

    $packageText = (Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'scripts/demo/complete-project-v2/sql') -Filter '*.sql' -File |
        Sort-Object FullName | ForEach-Object { [IO.File]::ReadAllText($_.FullName) }) -join "`n"
    Assert-RequiredUsersCovered -RequiredUsers $RequiredUsers -PackageText $packageText

    $frontendResponse = Invoke-WebRequest -Uri ($FrontendBaseUrl.TrimEnd('/') + '/') -UseBasicParsing
    if ($frontendResponse.StatusCode -ne 200) { throw 'LIVE_EVIDENCE_FRONTEND_UNHEALTHY' }
    $health = Invoke-RestMethod -Uri ($BackendBaseUrl.TrimEnd('/') + '/actuator/health')
    if ($health.status -ne 'UP') { throw 'LIVE_EVIDENCE_BACKEND_UNHEALTHY' }
}

function Assert-LiveIdentity(
    [string]$FrontendBaseUrl,
    [string]$DatabaseName,
    [long]$ExpectedTenantId,
    [string]$Username
) {
    $loginUri = $FrontendBaseUrl.TrimEnd('/') + '/api/auth/dev-login?username=' + [Uri]::EscapeDataString($Username)
    $loginResponse = Invoke-WebRequest -Uri $loginUri -SessionVariable liveWebSession -UseBasicParsing
    $login = $loginResponse.Content | ConvertFrom-Json
    if ($login.code -ne '0' -or $login.data.userInfo.username -ne $Username -or
        [long]$login.data.userInfo.tenantId -ne $ExpectedTenantId) {
        throw 'LIVE_EVIDENCE_PROBE_IDENTITY_MISMATCH'
    }
    $preview = Invoke-RestMethod -Uri ($FrontendBaseUrl.TrimEnd('/') + '/api/system/data-maintenance/preview') -WebSession $liveWebSession
    if ($preview.code -ne '0' -or $preview.data.database -ne $DatabaseName) {
        throw 'LIVE_EVIDENCE_BACKEND_DATABASE_MISMATCH'
    }

    return [pscustomobject]@{
        Username = [string]$login.data.userInfo.username
        TenantId = [long]$login.data.userInfo.tenantId
        Database = [string]$preview.data.database
    }
}

function Add-ReportedSpecFiles([object[]]$Suites, [Collections.Generic.HashSet[string]]$Files) {
    foreach ($suite in $Suites) {
        $specsProperty = $suite.PSObject.Properties['specs']
        if ($null -ne $specsProperty) {
            foreach ($spec in @($specsProperty.Value)) {
                if ($null -ne $spec.file) { [void]$Files.Add([IO.Path]::GetFileName([string]$spec.file)) }
            }
        }
        $suitesProperty = $suite.PSObject.Properties['suites']
        if ($null -ne $suitesProperty) {
            Add-ReportedSpecFiles -Suites @($suitesProperty.Value) -Files $Files
        }
    }
}

function Assert-PlaywrightEvidence([object]$Report, [string[]]$ExpectedSpecs, [int]$ExitCode) {
    $reportedSpecs = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    Add-ReportedSpecFiles -Suites @($Report.suites) -Files $reportedSpecs
    $missing = @($ExpectedSpecs | Where-Object { $_ -notin $reportedSpecs })
    $unexpectedFiles = @($reportedSpecs | Where-Object { $_ -notin $ExpectedSpecs })
    if ($missing.Count -gt 0 -or $unexpectedFiles.Count -gt 0) {
        throw "LIVE_EVIDENCE_SPEC_SELECTION_MISMATCH:missing=$($missing -join ',');unexpected=$($unexpectedFiles -join ',')"
    }
    if ($ExitCode -ne 0 -or [int]$Report.stats.expected -le 0 -or
        [int]$Report.stats.skipped -ne 0 -or [int]$Report.stats.unexpected -ne 0 -or
        [int]$Report.stats.flaky -ne 0) {
        throw "LIVE_EVIDENCE_PLAYWRIGHT_FAILED:exit=$ExitCode;expected=$($Report.stats.expected);skipped=$($Report.stats.skipped);unexpected=$($Report.stats.unexpected);flaky=$($Report.stats.flaky)"
    }
}

function Invoke-LiveEvidence {
    $packageRoot = $PSScriptRoot
    $repoRoot = [IO.Path]::GetFullPath((Join-Path $packageRoot '../../..'))
    $frontendRoot = Join-Path $repoRoot 'frontend-admin-v2'
    Assert-CleanWorkingTree $repoRoot
    $headSha = (git -C $repoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($headSha)) {
        throw 'LIVE_EVIDENCE_HEAD_SHA_UNAVAILABLE'
    }
    $branch = (git -C $repoRoot branch --show-current | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($branch)) { $branch = 'DETACHED' }
    $liveSpecs = @(Get-LiveSpecs $frontendRoot)
    $requiredUsers = @(Get-RequiredUsers -FrontendRoot $frontendRoot -LiveSpecs $liveSpecs)
    $backendProxyTarget = Get-BackendProxyTarget $BackendUrl
    $frontend = Start-LiveFrontend -FrontendRoot $frontendRoot -Url $FrontendUrl `
        -BackendProxyTarget $backendProxyTarget

    try {
    $identity = Assert-LivePreflight -RepoRoot $repoRoot -FrontendRoot $frontendRoot `
        -EnvironmentName $Environment -DatabaseName $Database -MySqlContainerName $MySqlContainer `
        -BackendContainerName $BackendContainer `
        -FrontendBaseUrl $FrontendUrl -BackendBaseUrl $BackendUrl -ExpectedTenantId $TenantId `
        -RequiredUsers $requiredUsers

    $loadScript = Join-Path $packageRoot 'load.ps1'
    & pwsh -NoProfile -File $loadScript -Environment $Environment -Database $Database -MySqlContainer $MySqlContainer
    if ($LASTEXITCODE -ne 0) { throw "LIVE_EVIDENCE_DEMO_LOAD_FAILED:$LASTEXITCODE" }
    Assert-UserRows -Container $MySqlContainer -DatabaseName $Database `
        -ExpectedTenantId $TenantId -RequiredUsers $requiredUsers
    $identity = Assert-LiveIdentity -FrontendBaseUrl $FrontendUrl -DatabaseName $Database `
        -ExpectedTenantId $TenantId -Username $ProbeUsername

    $startedAt = [DateTimeOffset]::UtcNow
    if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        $stamp = $startedAt.ToString('yyyyMMddTHHmmssZ')
        $EvidenceDirectory = Join-Path $repoRoot "local-evidence/live-e2e/live-evidence-$($headSha.Substring(0, 12))-$stamp"
    }
    $evidenceRoot = [IO.Path]::GetFullPath($EvidenceDirectory)
    $repoPrefix = $repoRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $evidenceRoot.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'LIVE_EVIDENCE_OUTPUT_MUST_BE_INSIDE_REPOSITORY'
    }
    New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
    $jsonReport = Join-Path $evidenceRoot 'playwright.json'
    $playwrightOutput = Join-Path $evidenceRoot 'artifacts'
    $visualOutput = Join-Path $evidenceRoot 'visual'
    $playwrightCli = Join-Path $frontendRoot 'node_modules/@playwright/test/cli.js'
    if (-not (Test-Path -LiteralPath $playwrightCli -PathType Leaf)) {
        throw 'LIVE_EVIDENCE_PLAYWRIGHT_UNAVAILABLE'
    }

    $environmentValues = [ordered]@{
        V2_LIVE_AUTH = '1'
        V2_LIVE_APPROVAL = '1'
        V2_LIVE_DASHBOARD = '1'
        V2_LIVE_PROJECTS = '1'
        V2_LIVE_DELIVERY = '1'
        V2_LIVE_QUALITY = '1'
        V2_LIVE_TECHNICAL = '1'
        V2_LIVE_CLOSEOUT = '1'
        V2_LIVE_SHELL = '1'
        V2_DELIVERY_PROJECT_ID = '520000000000009002'
        V2_SCHEDULE_PROJECT_ID = '520000000000000001'
        V2_SCHEDULE_READONLY_USER = 'demo.schedule.query'
        V2_QUALITY_PROJECT_ID = '520000000000000001'
        V2_TECHNICAL_PROJECT_ID = '520000000000000001'
        V2_CLOSEOUT_PROJECT_ID = '520000000000000001'
        V2_VISUAL_OUTPUT = $visualOutput
        PLAYWRIGHT_BASE_URL = $FrontendUrl
        PLAYWRIGHT_OUTPUT_DIR = $playwrightOutput
        PLAYWRIGHT_JSON_OUTPUT_NAME = $jsonReport
    }
    $previousEnvironment = @{}
    foreach ($name in $environmentValues.Keys) {
        $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $environmentValues[$name], 'Process')
    }

    $exitCode = 1
    Push-Location $frontendRoot
    try {
        $specArguments = @($liveSpecs | ForEach-Object { "e2e/$_" })
        & node $playwrightCli test --workers $Workers --max-failures 1 --reporter=json @specArguments
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
        foreach ($name in $environmentValues.Keys) {
            [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
        }
    }
    if (-not (Test-Path -LiteralPath $jsonReport -PathType Leaf)) {
        throw 'LIVE_EVIDENCE_JSON_REPORT_MISSING'
    }
    $report = Get-Content -LiteralPath $jsonReport -Raw -Encoding UTF8 | ConvertFrom-Json
    if ((git -C $repoRoot rev-parse HEAD).Trim() -ne $headSha) {
        throw 'LIVE_EVIDENCE_HEAD_SHA_CHANGED'
    }
    Assert-CleanWorkingTree $repoRoot
    $finishedAt = [DateTimeOffset]::UtcNow
    $summary = [ordered]@{
        schemaVersion = 1
        headSha = $headSha
        branch = $branch
        workingTreeDirty = $false
        environment = $Environment
        frontendUrl = $FrontendUrl
        backendUrl = $BackendUrl
        database = $identity.Database
        tenantId = $identity.TenantId
        probeUsername = $identity.Username
        requiredUsers = $requiredUsers
        liveSpecs = $liveSpecs
        expected = [int]$report.stats.expected
        skipped = [int]$report.stats.skipped
        unexpected = [int]$report.stats.unexpected
        flaky = [int]$report.stats.flaky
        exitCode = $exitCode
        startedAt = $startedAt.ToString('o')
        finishedAt = $finishedAt.ToString('o')
        durationMs = [long]($finishedAt - $startedAt).TotalMilliseconds
        playwrightReport = [IO.Path]::GetRelativePath($repoRoot, $jsonReport).Replace('\', '/')
        artifactDirectory = [IO.Path]::GetRelativePath($repoRoot, $playwrightOutput).Replace('\', '/')
    }
    $summaryPath = Join-Path $evidenceRoot 'summary.json'
    [IO.File]::WriteAllText($summaryPath, (($summary | ConvertTo-Json -Depth 8) + "`n"), [Text.UTF8Encoding]::new($false))
    Assert-PlaywrightEvidence -Report $report -ExpectedSpecs $liveSpecs -ExitCode $exitCode
    Write-Output ($summary | ConvertTo-Json -Depth 8)
    } finally {
        Stop-LiveFrontend $frontend
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    Invoke-LiveEvidence
}
