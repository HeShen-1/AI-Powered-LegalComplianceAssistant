@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 > nul

set "SPRING_PROFILES_ACTIVE=demo"
set "ENV_FILE=%~dp0.env"

if exist "%ENV_FILE%" (
    echo Loading environment variables from .env...
    for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
        if not "%%~a"=="" (
            set "%%~a=%%~b"
        )
    )
)

if not defined DEEPSEEK_API_KEY (
    echo Warning: DEEPSEEK_API_KEY is empty. Demo mode is designed to prefer DeepSeek.
)

echo Starting LegalAssistant in demo profile...
echo SPRING_PROFILES_ACTIVE=%SPRING_PROFILES_ACTIVE%
call mvn spring-boot:run

