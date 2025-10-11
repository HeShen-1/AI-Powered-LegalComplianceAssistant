#!/bin/bash
# Linux/macOS startup script with full observability
# Includes OpenTelemetry distributed tracing and Prometheus monitoring

set -e  # Exit immediately on error

# Set UTF-8 encoding for Java (fixes Chinese character display issues)
export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

echo "============================================"
echo "  LegalAssistant - Observability Mode"
echo "============================================"
echo ""

# OpenTelemetry configuration
export OTEL_SERVICE_NAME=legal-assistant
export OTEL_TRACES_EXPORTER=logging
export OTEL_METRICS_EXPORTER=logging
export OTEL_LOGS_EXPORTER=logging
export OTEL_TRACES_SAMPLER=parentbased_traceidratio
export OTEL_TRACES_SAMPLER_ARG=1.0
export OTEL_RESOURCE_ATTRIBUTES=deployment.environment=development,service.version=1.0.0

# Check if OpenTelemetry Java Agent exists
OTEL_AGENT_PATH="opentelemetry-javaagent.jar"

if [ ! -f "$OTEL_AGENT_PATH" ]; then
    echo ""
    echo "[WARNING] OpenTelemetry Java Agent not found"
    echo "Please download and place it in project root:"
    echo "  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest"
    echo ""
    echo "Starting in normal mode (no distributed tracing)..."
    echo ""
    sleep 3
    mvn spring-boot:run
else
    echo ""
    echo "[OK] OpenTelemetry Java Agent ready"
    echo "[OK] Service name: $OTEL_SERVICE_NAME"
    echo "[OK] Trace exporter: console logging (no external collector needed)"
    echo "[OK] Sampling rate: 100%"
    echo ""
    echo "Observability endpoints:"
    echo "  - Prometheus: http://localhost:8080/api/v1/actuator/prometheus"
    echo "  - Health: http://localhost:8080/api/v1/actuator/health"
    echo "  - Metrics: http://localhost:8080/api/v1/actuator/metrics"
    echo ""
    echo "Building project..."
    echo ""
    
    # Build project first
    mvn clean package -DskipTests
    
    if [ $? -ne 0 ]; then
        echo ""
        echo "[ERROR] Build failed. Please check the errors above."
        exit 1
    fi
    
    echo ""
    echo "Starting application..."
    echo ""
    
    # Start application with properly formatted arguments
    java -javaagent:"$OTEL_AGENT_PATH" -Dfile.encoding=UTF-8 -jar target/LegalAssistant-0.0.1-SNAPSHOT.jar
fi
