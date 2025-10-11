# PowerShell startup script with full observability
# Includes OpenTelemetry distributed tracing and Prometheus monitoring

# Set console encoding to UTF-8 (fixes Chinese character display issues)
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 | Out-Null

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  LegalAssistant - Observability Mode" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Set Java encoding for both Agent and Application
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

# OpenTelemetry configuration
$env:OTEL_SERVICE_NAME = "legal-assistant"
$env:OTEL_TRACES_EXPORTER = "logging"
$env:OTEL_METRICS_EXPORTER = "logging"
$env:OTEL_LOGS_EXPORTER = "logging"
$env:OTEL_TRACES_SAMPLER = "parentbased_traceidratio"
$env:OTEL_TRACES_SAMPLER_ARG = "1.0"
$env:OTEL_RESOURCE_ATTRIBUTES = "deployment.environment=development,service.version=1.0.0"

# Check if OpenTelemetry Java Agent exists
$otelAgentPath = "opentelemetry-javaagent.jar"

if (-Not (Test-Path $otelAgentPath)) {
    Write-Host ""
    Write-Host "[WARNING] OpenTelemetry Java Agent not found" -ForegroundColor Yellow
    Write-Host "Please download and place it in project root:" -ForegroundColor Yellow
    Write-Host "  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Starting in normal mode (no distributed tracing)..." -ForegroundColor Yellow
    Write-Host ""
    Start-Sleep -Seconds 3
    mvn spring-boot:run
} else {
    Write-Host ""
    Write-Host "[OK] OpenTelemetry Java Agent ready" -ForegroundColor Green
    Write-Host "[OK] Service name: $env:OTEL_SERVICE_NAME" -ForegroundColor Green
    Write-Host "[OK] Trace exporter: console logging (no external collector needed)" -ForegroundColor Green
    Write-Host "[OK] Sampling rate: 100%" -ForegroundColor Green
    Write-Host ""
    Write-Host "Observability endpoints:" -ForegroundColor Cyan
    Write-Host "  - Prometheus: http://localhost:8080/api/v1/actuator/prometheus" -ForegroundColor White
    Write-Host "  - Health: http://localhost:8080/api/v1/actuator/health" -ForegroundColor White
    Write-Host "  - Metrics: http://localhost:8080/api/v1/actuator/metrics" -ForegroundColor White
    Write-Host ""
    Write-Host "Building project..." -ForegroundColor Yellow
    Write-Host ""
    
    # Build project first
    mvn clean package -DskipTests
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "[ERROR] Build failed. Please check the errors above." -ForegroundColor Red
        exit 1
    }
    
    Write-Host ""
    Write-Host "Starting application..." -ForegroundColor Green
    Write-Host ""
    
    # Build Java arguments as an array (avoids parsing issues)
    $javaArgs = @(
        "-javaagent:$otelAgentPath",
        "-Dfile.encoding=UTF-8",
        "-jar",
        "target\LegalAssistant-0.0.1-SNAPSHOT.jar"
    )
    
    # Start application with properly formatted arguments
    & java $javaArgs
}
