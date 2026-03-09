chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

function Set-DotEnvValue {
    param(
        [AllowEmptyString()]
        [string]$Line
    )

    $trimmed = $Line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) {
        return
    }

    if ($trimmed.StartsWith('export ')) {
        $trimmed = $trimmed.Substring(7).Trim()
    }

    $parts = $trimmed -split '=', 2
    if ($parts.Count -ne 2) {
        return
    }

    $key = $parts[0].Trim()
    if (-not $key) {
        return
    }

    $value = $parts[1].Trim()
    if ($value.Length -ge 2) {
        $hasDoubleQuotes = $value.StartsWith('"') -and $value.EndsWith('"')
        $hasSingleQuotes = $value.StartsWith("'") -and $value.EndsWith("'")
        if ($hasDoubleQuotes -or $hasSingleQuotes) {
            $value = $value.Substring(1, $value.Length - 2)
        }
    }

    Set-Item -Path ("Env:{0}" -f $key) -Value $value
    Write-Host ("  set {0}" -f $key) -ForegroundColor DarkGray
}

$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Write-Host "Loading environment variables from .env..." -ForegroundColor Cyan
    Get-Content $envFile -Encoding UTF8 | ForEach-Object {
        Set-DotEnvValue -Line $_
    }
    Write-Host "Environment variables loaded." -ForegroundColor Green
}
else {
    Write-Host "Warning: .env file not found. Copy .env.example to .env if you need local overrides." -ForegroundColor Yellow
}

if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_CHAT_ENABLED)) {
    if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
        $env:DEEPSEEK_CHAT_ENABLED = "false"
    }
    else {
        $env:DEEPSEEK_CHAT_ENABLED = "true"
    }
}

Write-Host ("DeepSeek chat enabled: {0}" -f $env:DEEPSEEK_CHAT_ENABLED) -ForegroundColor Cyan
Write-Host "Starting LegalAssistant..." -ForegroundColor Green

& mvn "spring-boot:run"
