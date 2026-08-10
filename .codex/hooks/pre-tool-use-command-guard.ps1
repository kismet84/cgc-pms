param()

$ErrorActionPreference = 'Stop'

function Deny-Command([string]$Reason) {
  @{
    hookSpecificOutput = @{
      hookEventName = 'PreToolUse'
      permissionDecision = 'deny'
      permissionDecisionReason = $Reason
    }
  } | ConvertTo-Json -Depth 4 -Compress
  exit 0
}

$payload = [Console]::In.ReadToEnd() | ConvertFrom-Json
if ($payload.tool_name -ne 'Bash' -or !$payload.tool_input.command) { exit 0 }

$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseInput(
  [string]$payload.tool_input.command,
  [ref]$tokens,
  [ref]$parseErrors
)
if ($parseErrors.Count -gt 0) {
  Deny-Command 'COMMAND_GUARD_POWERSHELL_PARSE_ERROR: fix PowerShell syntax before execution.'
}

$commands = @($ast.FindAll({ param($node) $node -is [System.Management.Automation.Language.CommandAst] }, $true))
$hasSearch = $false
foreach ($command in $commands) {
  $name = $command.GetCommandName()
  $elements = @($command.CommandElements)
  $isSearch = $name -ieq 'rg' -or (
    $name -ieq 'git' -and
    $elements.Count -gt 1 -and
    [string]$elements[1].SafeGetValue() -ieq 'grep'
  )
  if ($isSearch) {
    $hasSearch = $true
    if ($elements | Where-Object {
      $_ -is [System.Management.Automation.Language.StringConstantExpressionAst] -and
      $_.Extent.Text.StartsWith('"') -and
      $_.Extent.Text.Contains('`')
    }) {
      Deny-Command 'COMMAND_GUARD_SEARCH_LITERAL_REQUIRES_SINGLE_QUOTES: pass backticks as single-quoted literals.'
    }
  }

  if ($name -ine 'Get-Content') { continue }
  for ($index = 0; $index -lt $elements.Count - 1; $index++) {
    $element = $elements[$index]
    if ($element -isnot [System.Management.Automation.Language.CommandParameterAst] -or $element.ParameterName -ine 'LiteralPath') { continue }
    $pathElement = $elements[$index + 1]
    if ($pathElement -isnot [System.Management.Automation.Language.StringConstantExpressionAst]) { continue }
    $literalPath = [string]$pathElement.Value
    $basePath = if ($payload.cwd) { [string]$payload.cwd } else { (Get-Location).Path }
    $resolvedPath = if ([IO.Path]::IsPathRooted($literalPath)) { $literalPath } else { Join-Path $basePath $literalPath }
    if (!(Test-Path -LiteralPath $resolvedPath)) {
      Deny-Command 'COMMAND_GUARD_LITERAL_PATH_NOT_FOUND: resolve tracked files first and require one exact match.'
    }
  }
}

if ($hasSearch -and $ast.EndBlock.Statements.Count -gt 1) {
  Deny-Command 'COMMAND_GUARD_SEARCH_MUST_BE_ISOLATED: run optional searches independently and inspect their exit code.'
}
