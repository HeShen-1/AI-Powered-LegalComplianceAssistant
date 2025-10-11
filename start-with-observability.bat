@echo off
REM Windows Batch startup script with full observability
REM Includes OpenTelemetry distributed tracing and Prometheus monitoring

REM Set console encoding to UTF-8 (fixes Chinese character display issues)
chcp 65001 > nul

echo ============================================
echo   LegalAssistant - Observability Mode
echo ============================================
echo.

REM Set Java encoding for both Agent and Application
SET JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8

REM OpenTelemetry configuration
SET OTEL_SERVICE_NAME=legal-assistant
SET OTEL_TRACES_EXPORTER=logging
SET OTEL_METRICS_EXPORTER=logging
SET OTEL_LOGS_EXPORTER=logging
SET OTEL_TRACES_SAMPLER=parentbased_traceidratio
SET OTEL_TRACES_SAMPLER_ARG=1.0
SET OTEL_RESOURCE_ATTRIBUTES=deployment.environment=development,service.version=1.0.0

REM Check if OpenTelemetry Java Agent exists
SET OTEL_AGENT_PATH=opentelemetry-javaagent.jar
IF NOT EXIST %OTEL_AGENT_PATH% (
    echo.
    echo [WARNING] OpenTelemetry Java Agent not found
    echo Please download and place it in project root:
    echo   https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest
    echo.
    echo Starting in normal mode (no distributed tracing)...
    echo.
    timeout /t 3 /nobreak > nul
    mvn spring-boot:run
) ELSE (
    echo.
    echo [OK] OpenTelemetry Java Agent ready
    echo [OK] Service name: %OTEL_SERVICE_NAME%
    echo [OK] Trace exporter: console logging (no external collector needed)
    echo [OK] Sampling rate: 100%%
    echo.
    echo Observability endpoints:
    echo   - Prometheus: http://localhost:8080/api/v1/actuator/prometheus
    echo   - Health: http://localhost:8080/api/v1/actuator/health
    echo   - Metrics: http://localhost:8080/api/v1/actuator/metrics
    echo.
    echo Building project...
    echo.
    
    REM Build project first
    call mvn clean package -DskipTests
    
    IF ERRORLEVEL 1 (
        echo.
        echo [ERROR] Build failed. Please check the errors above.
        exit /b 1
    )
    
    echo.
    echo Starting application...
    echo.
    
    REM Start application with properly formatted arguments
    java -javaagent:%OTEL_AGENT_PATH% -Dfile.encoding=UTF-8 -jar target\LegalAssistant-0.0.1-SNAPSHOT.jar
)
