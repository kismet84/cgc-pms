# T8 helper: API calls using cookie-based auth
$cookieFile = "$env:TEMP\cgc-pms-cookies.txt"
$baseUrl = "http://localhost:8080/api"

function api-get {
    param([string]$path)
    $url = "$baseUrl$path"
    Write-Output "=== GET $url ==="
    $result = curl.exe -s -b $cookieFile $url 2>$null
    Write-Output $result
    return $result
}

function api-post {
    param([string]$path, [string]$body)
    $url = "$baseUrl$path"
    Write-Output "=== POST $url ==="
    Write-Output "BODY: $body"
    $result = curl.exe -s -b $cookieFile -X POST $url -H "Content-Type: application/json" -d $body 2>$null
    Write-Output $result
    return $result
}

function api-put {
    param([string]$path, [string]$body)
    $url = "$baseUrl$path"
    Write-Output "=== PUT $url ==="
    Write-Output "BODY: $body"
    $result = curl.exe -s -b $cookieFile -X PUT $url -H "Content-Type: application/json" -d $body 2>$null
    Write-Output $result
    return $result
}

function api-delete {
    param([string]$path)
    $url = "$baseUrl$path"
    Write-Output "=== DELETE $url ==="
    $result = curl.exe -s -b $cookieFile -X DELETE $url 2>$null
    Write-Output $result
    return $result
}

Write-Output "Helper functions loaded: api-get, api-post, api-put, api-delete"
