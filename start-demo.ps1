chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:SPRING_PROFILES_ACTIVE = "demo"

$envFile = Join-Path $scriptRoot ".env"
if (Test-Path $envFile) {
    Write-Host "Loading environment variables from .env..." -ForegroundColor Cyan
    Get-Content $envFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) {
            return
        }

        $parts = $line -split '=', 2
        if ($parts.Count -ne 2) {
            return
        }

        $key = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($key) {
            Set-Item -Path ("Env:{0}" -f $key) -Value $value
        }
    }
}

if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
    Write-Host "Warning: DEEPSEEK_API_KEY is empty. Demo mode is meant to prefer DeepSeek." -ForegroundColor Yellow
}

Write-Host "Starting LegalAssistant in demo profile..." -ForegroundColor Green
Write-Host ("SPRING_PROFILES_ACTIVE={0}" -f $env:SPRING_PROFILES_ACTIVE) -ForegroundColor Cyan

& mvn spring-boot:run

