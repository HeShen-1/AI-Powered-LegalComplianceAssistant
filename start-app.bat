@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM Set console encoding to UTF-8
chcp 65001 > nul

REM Set Java encoding options
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

set "ENV_FILE=%~dp0.env"
if exist "%ENV_FILE%" (
    echo Loading environment variables from .env...
    for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
        if not "%%~a"=="" (
            set "%%~a=%%~b"
            echo   set %%~a
        )
    )
    echo Environment variables loaded.
) else (
    echo Warning: .env file not found. Copy .env.example to .env if you need local overrides.
)

if not defined DEEPSEEK_CHAT_ENABLED (
    if defined DEEPSEEK_API_KEY (
        set "DEEPSEEK_CHAT_ENABLED=true"
    ) else (
        set "DEEPSEEK_CHAT_ENABLED=false"
    )
)

echo DeepSeek chat enabled: %DEEPSEEK_CHAT_ENABLED%
echo Starting LegalAssistant...
call mvn spring-boot:run
